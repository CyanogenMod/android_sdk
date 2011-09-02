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

import org.apache.tools.ant.BuildException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *  This class takes care of dependency tracking for all targets and prerequisites listed in
 *  a single dependency file. A dependency graph always has a dependency file associated with it
 *  for the duration of its lifetime
 */
public class DependencyGraph {

    private static enum DependencyStatus {
        NONE, NEW_FILE, UPDATED_FILE, MISSING_FILE, ERROR;
    }

    // Files that we know about from the dependency file
    private Set<File> mTargets = Collections.emptySet();
    private Set<File> mPrereqs = mTargets;
    private File mFirstPrereq = null;
    private boolean mMissingDepFile = false;
    private long mDepFileLastModified;
    private final List<InputPath> mNewInputs;

    public static class InputPath {
        File mFile;
        /**
         * A set of extensions. Only files with an extension in this set will
         * be considered for a modification check. All deleted/created files will still be
         * checked. If this is null, all files will be checked for modification date
         */
        Set<String> mExtensionsToCheck;

        public InputPath(File file, Set<String> extensions) {
            mFile = file;
            mExtensionsToCheck = extensions;
        }
    }


    public DependencyGraph(String dependencyFilePath, List<InputPath> newInputPaths) {
        mNewInputs = newInputPaths;
        parseDependencyFile(dependencyFilePath);
    }

    /**
     * Check all the dependencies to see if anything has changed.
     *
     * @param printStatus will print to {@link System#out} the dependencies status.
     * @return true if new prerequisites have appeared, target files are missing or if
     *         prerequisite files have been modified since the last target generation.
     */
    public boolean dependenciesHaveChanged(boolean printStatus) {
        // If no dependency file has been set up, then we'll just return true
        // if we have a dependency file, we'll check to see what's been changed
        if (mMissingDepFile) {
            System.out.println("No Dependency File Found");
            return true;
        }

        // check for missing output first
        if (missingTargetFile()) {
            if (printStatus) {
                System.out.println("Found Deleted Target File");
            }
            return true;
        }

        // get the time stamp of the oldest target.
        long oldestTarget = getOutputLastModified();

        // first look through the input folders and look for new files or modified files.
        DependencyStatus status = checkInputs(oldestTarget);

        // this can't find missing files. This is done later.
        switch (status) {
            case ERROR:
                throw new BuildException();
            case NEW_FILE:
                if (printStatus) {
                    System.out.println("Found new input file");
                }
                return true;
            case UPDATED_FILE:
                if (printStatus) {
                    System.out.println("Found modified input file");
                }
                return true;
        }

        // now do a full check on the remaining files.
        status = checkPrereqFiles(oldestTarget);
        // this can't find new input files. This is done above.
        switch (status) {
            case ERROR:
                throw new BuildException();
            case MISSING_FILE:
                if (printStatus) {
                    System.out.println("Found deleted input file");
                }
                return true;
            case UPDATED_FILE:
                if (printStatus) {
                    System.out.println("Found modified input file");
                }
                return true;
        }

        return false;
    }

    public Set<File> getTargets() {
        return Collections.unmodifiableSet(mTargets);
    }

    public File getFirstPrereq() {
        return mFirstPrereq;
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

        mTargets = new HashSet<File>(targets.length);
        for (String path : targets) {
            if (path.length() > 0) {
                mTargets.add(new File(path));
            }
        }

        mPrereqs = new HashSet<File>(prereqs.length);
        for (String path : prereqs) {
            if (path.length() > 0) {
                File f = new File(path);
                if (mFirstPrereq == null) {
                    mFirstPrereq = f;
                }
                mPrereqs.add(f);
            }
        }
    }

    /**
     * Check all the input files and folders to see if there have been new
     * files added to them or if any of the existing files have been modified.
     *
     * This looks at the input paths, not at the list of known prereq. Therefore this
     * will not find missing files. It will however remove processed files from the
     * prereq file list so that we can process those in a 2nd step.
     *
     * This should be followed by a call to {@link #checkPrereqFiles(long)} which
     * will process the remaining files in the prereq list.
     *
     * If a change is found, this will return immediatly with either
     * {@link DependencyStatus#NEW_FILE} or {@link DependencyStatus#UPDATED_FILE}.
     *
     * @param oldestTarget the timestamp of the oldest output file to compare against.
     *
     * @return the status of the file in the watched folders.
     *
     */
    private DependencyStatus checkInputs(long oldestTarget) {
        if (mNewInputs != null) {
            for (InputPath input : mNewInputs) {
                if (input.mFile.isDirectory()) {
                    DependencyStatus status = checkInputFolder(input.mFile,
                            input.mExtensionsToCheck, oldestTarget);
                    if (status != DependencyStatus.NONE) {
                        return status;
                    }
                } else if (input.mFile.isFile()) {
                    DependencyStatus status = checkInputFile(input.mFile,
                            input.mExtensionsToCheck, oldestTarget);
                    if (status != DependencyStatus.NONE) {
                        return status;
                    }
                }
            }
        }

        // If we make it all the way through our directories we're good.
        return DependencyStatus.NONE;
    }

    /**
     * Check all the files in the tree under root and check to see if the files are
     * listed under the dependencies, or if they have been modified. Recurses into subdirs.
     *
     * @param rootFolder the folder to search through.
     * @param extensionsToCheck a set of extensions. Only files with an extension in this set will
     *        be considered for a modification check. All deleted/created files will still be
     *        checked. If this is null, all files will be checked for modification date
     * @param oldestTarget the time stamp of the oldest output file to compare against.
     *
     * @return the status of the file in the folder.
     */
    private DependencyStatus checkInputFolder(File rootFolder, Set<String> extensionsToCheck,
            long oldestTarget) {
        File[] files = rootFolder.listFiles();
        if (files == null) {
            System.err.println("ERROR " + rootFolder.toString() + " is not a dir or can't be read");
            return DependencyStatus.ERROR;
        }
        // Loop through files in this folder
        for (File file : files) {
            // If this is a directory, recurse into it
            if (file.isDirectory()) {
                DependencyStatus status = checkInputFolder(file, extensionsToCheck, oldestTarget);
                if (status != DependencyStatus.NONE) {
                    return status;
                }
            } else if (file.isFile()) {
                DependencyStatus status = checkInputFile(file, extensionsToCheck, oldestTarget);
                if (status != DependencyStatus.NONE) {
                    return status;
                }
            }
        }
        // If we got to here then we didn't find anything interesting
        return DependencyStatus.NONE;
    }

    private DependencyStatus checkInputFile(File file, Set<String> extensionsToCheck,
            long oldestTarget) {
        // if it's a file, remove it from the list of prereqs.
        // This way if files in this folder don't trigger a build we'll have less
        // files to go through manually
        if (mPrereqs.remove(file) == false) {
            // turns out this is a new file!
            return DependencyStatus.NEW_FILE;
        } else {
            // check the time stamp on this file if it's a file we care about based on the
            // list of extensions to check.
            if (extensionsToCheck == null || extensionsToCheck.contains(getExtension(file))) {
                if (file.lastModified() > oldestTarget) {
                    return DependencyStatus.UPDATED_FILE;
                }
            }
        }

        return DependencyStatus.NONE;
    }

    /**
     * Check all the prereq files we know about to make sure they're still there, or that they
     * haven't been modified since the last build.
     *
     * @param oldestTarget the time stamp of the oldest output file to compare against.
     *
     * @return the status of the files
     */
    private DependencyStatus checkPrereqFiles(long oldestTarget) {
        // Loop through our prereq files and make sure they still exist
        for (File prereq : mPrereqs) {
            if (prereq.exists() == false) {
                return DependencyStatus.MISSING_FILE;
            }

            // check the time stamp on this file if it's a file we care about based on the
            // list of extensions to check.
            // to get the extensions to check, we look in which input folder this file is.
            Set<String> extensionsToCheck = null;
            if (mNewInputs != null) {
                String filePath = prereq.getAbsolutePath();
                for (InputPath input : mNewInputs) {
                    if (filePath.startsWith(input.mFile.getAbsolutePath())) {
                        extensionsToCheck = input.mExtensionsToCheck;
                        break;
                    }
                }
            }

            if (extensionsToCheck == null || extensionsToCheck.contains(getExtension(prereq))) {
                if (prereq.lastModified() > oldestTarget) {
                    return DependencyStatus.UPDATED_FILE;
                }
            }
        }

        // If we get this far, then all our prereq are okay
        return DependencyStatus.NONE;
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
     * Returns the earliest modification time stamp from all the output targets. If there
     * are no known target, the dependency file time stamp is returned.
     */
    private long getOutputLastModified() {
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

        return oldestTarget;
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
