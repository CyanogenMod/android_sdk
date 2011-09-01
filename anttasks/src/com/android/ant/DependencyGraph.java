/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ant;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 *  This class takes care of dependency tracking for all targets and prerequisites listed in
 *  a single dependency file. A dependency graph always has a dependency file associated with it
 *  for the duration of its lifetime
 */
public class DependencyGraph {

    // Files that we know about from the dependency file
    private List<File> mTargets = Collections.emptyList();
    private List<File> mPrereqs = mTargets;
    private boolean mMissingDepFile = false;
    private long mDepFileLastModified;
    private final ArrayList<File> mWatchPaths;

    public DependencyGraph(String dependencyFilePath, ArrayList<File> watchPaths) {
        mWatchPaths = watchPaths;
        parseDependencyFile(dependencyFilePath);
    }

    /**
     * Check all the dependencies to see if anything has changed.
     * @param extensionsToCheck a set of extensions. Only files with an extension in this set will
     *        be considered for a modification check. All deleted/created files will still be
     *        checked. If this is null, all files will be checked for modification date
     * @param printStatus will print to {@link System#out} the dependencies status.
     * @return true if new prerequisites have appeared, target files are missing or if
     *         prerequisite files have been modified since the last target generation.
     */
    public boolean dependenciesHaveChanged(Set<String> extensionsToCheck, boolean printStatus) {
        boolean missingPrereq = missingPrereqFile();
        boolean newPrereq = newPrereqFile();
        boolean missingTarget = missingTargetFile();
        boolean modPrereq = modifiedPrereq(extensionsToCheck);

        if (printStatus) {
            if (mMissingDepFile) {
                System.out.println("No Dependency File Found");
            }
            if (missingPrereq) {
                System.out.println("Found Deleted Prereq File");
            }
            if (newPrereq) {
                System.out.println("Found New Prereq File");
            }
            if (missingTarget) {
                System.out.println("Found Deleted Target File");
            }
            if (modPrereq) {
                System.out.println("Found Modified Prereq File");
            }
        }

        // If no dependency file has been set up, then we'll just return true
        // if we have a dependency file, we'll check to see what's been changed
        return mMissingDepFile || missingPrereq || newPrereq || missingTarget || modPrereq;
    }

    public List<File> getTargets() {
        return mTargets;
    }

    public List<File> getPrereqs() {
        return mPrereqs;
    }

    /**
     * Parses the given dependency file and stores the file paths
     *
     * @param dependencyFilePath the dependency file
     */
    private void parseDependencyFile(String dependencyFilePath) {
        // first check if the dependency file is here.
        File depFile = new File(dependencyFilePath);
        if (depFile.isFile() == false) {
            mMissingDepFile = true;
            return;
        }

        // get the modification time of the dep file as we may need it later
        mDepFileLastModified = depFile.lastModified();

        // Read in our dependency file
        String content = readFile(dependencyFilePath);
        if (content == null) {
            System.err.println("ERROR: Couldn't read " + dependencyFilePath);
            return;
        }

        // The format is something like:
        // output1 output2 [...]: dep1 dep2 [...]
        // expect it's likely split on several lines. So let's move it back on a single line
        // first
        String[] lines = content.toString().split("\n");
        StringBuilder sb = new StringBuilder(content.length());
        for (String line : lines) {
            line = line.trim();
            if (line.endsWith("\\")) {
                line = line.substring(0, line.length() - 1);
            }
            sb.append(line);
        }

        // split the left and right part
        String[] files = sb.toString().split(":");

        // get the target files:
        String[] targets = files[0].trim().split(" ");

        String[] prereqs = {};
        // Check to make sure our dependency file is okay
        if (files.length < 1) {
            System.err.println(
                    "Warning! Dependency file does not list any prerequisites after ':' ");
        } else {
            // and the prerequisite files:
            prereqs = files[1].trim().split(" ");
        }

        mTargets = new ArrayList<File>(targets.length);
        for (String path : targets) {
            if (path.length() > 0) {
                mTargets.add(new File(path));
            }
        }
        mTargets = Collections.unmodifiableList(mTargets);
        mPrereqs = new ArrayList<File>(prereqs.length);
        for (String path : prereqs) {
            if (path.length() > 0) {
                mPrereqs.add(new File(path));
            }
        }
        mPrereqs = Collections.unmodifiableList(mPrereqs);
    }

    /**
     * Check all the folders we know about to see if there have been new
     * files added to them.
     * @return true if a new file is encountered in the dependency folders
     */
    private boolean newPrereqFile() {
        if (mWatchPaths != null) {
            for (File dir : mWatchPaths) {
                if (newFileInTree(dir)) {
                    return true;
                }
            }
        }
        // If we make it all the way through our directories we're good.
        return false;
    }

    /**
     * Check all the files in the tree under root and check to see if the files are
     * listed under the dependencies. Recurses into subdirs.
     * @param root the root of the file tree to search through
     * @return true if a file is encountered in the tree that is not in our list of prereqs
     */
    private boolean newFileInTree(File root) {
        File[] files = root.listFiles();
        if (files == null) {
            System.err.println("ERROR " + root.toString() + " is not a dir or can't be read");
            return false;
        }
        // Loop through files in this folder
        for (File file : files) {
            // If this is a directory, recurse into it
            if (file.isDirectory()) {
                if (newFileInTree(file)) {
                    return true;
                }
            } else if (file.isFile() && mPrereqs.contains(file) == false) {
                return true;
            }
        }
        // If we got to here then we didn't find anything interesting
        return false;
    }

    /**
     * Check all the prereq files we know about to make sure they're still there
     * @return true if any of the prereq files are missing.
     */
    private boolean missingPrereqFile() {
        // Loop through our prereq files and make sure they still exist
        for (File prereq : mPrereqs) {
            if (prereq.exists() == false) {
                return true;
            }
        }
        // If we get this far, then all our targets are okay
        return false;
    }

    /**
     * Check all the target files we know about to make sure they're still there
     * @return true if any of the target files are missing.
     */
    private boolean missingTargetFile() {
        // Loop through our target files and make sure they still exist
        for (File target : mTargets) {
            if (target.exists() == false) {
                return true;
            }
        }
        // If we get this far, then all our targets are okay
        return false;
    }

    /**
     * Check to see if any of the prerequisite files have been modified since
     * the targets were last updated.
     * @return true if the latest prerequisite modification is after the oldest
     *         target modification.
     */
    private boolean modifiedPrereq(Set<String> extensionsToCheck) {
        // Find the oldest target
        long oldestTarget = Long.MAX_VALUE;
        // if there's no output, then compare to the time of the dependency file.
        if (mTargets.size() == 0) {
            oldestTarget = mDepFileLastModified;
        } else {
            for (File target : mTargets) {
                if (target.lastModified() < oldestTarget) {
                    oldestTarget = target.lastModified();
                }
            }
        }

        // Find the newest prerequisite
        long newestPrereq = 0;
        for (File prereq : mPrereqs) {
            // If we have a list of extensions that we need to restrict ourselves to, only
            // consider this file if it has that extension.
            if (extensionsToCheck == null || extensionsToCheck.contains(getExtension(prereq))) {
                if (prereq.lastModified() > newestPrereq) {
                    newestPrereq = prereq.lastModified();
                }
            }
        }

        // And return the comparison
        return newestPrereq > oldestTarget;
    }

    /**
     * Reads and returns the content of a text file.
     * @param filepath the file path to the text file
     * @return null if the file could not be read
     */
    private static String readFile(String filepath) {
        try {
            FileInputStream fStream = new FileInputStream(filepath);
            if (fStream != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(fStream));

                String line;
                StringBuilder total = new StringBuilder(reader.readLine());
                while ((line = reader.readLine()) != null) {
                    total.append('\n');
                    total.append(line);
                }
                return total.toString();
            }
        } catch (IOException e) {
            // we'll just return null
        }
        return null;
    }

    /**
     *  Gets the extension (if present) on a file by looking at the filename
     *  @param file the file to get the extension of
     *  @return the extension if present, or the empty string if the filename doesn't have
     *          and extension.
     */
    private static String getExtension(File file) {
        String filename = file.getName();
        if (filename.lastIndexOf('.') == -1) {
            return "";
        }
        // Don't include the leading '.' in the extension
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

}
