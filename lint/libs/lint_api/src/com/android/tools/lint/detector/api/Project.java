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

package com.android.tools.lint.detector.api;

import com.android.tools.lint.client.api.Configuration;
import com.android.tools.lint.client.api.LintClient;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A project contains information about an Android project being scanned for
 * Lint errors.
 * <p>
 * TODO: Accumulate more project info here, such as the project package name,
 * the minimum and target SDK API levels, etc.
 */
public class Project {
    /** The associated tool */
    private final LintClient mTool;
    private final File mDir;
    private final File mReferenceDir;
    private Configuration mConfiguration;

    /**
     * If non null, specifies a non-empty list of specific files under this
     * project which should be checked.
     */
    private List<File> mFiles;
    private List<File> mJavaSourceFolders;
    private List<File> mJavaClassFolders;

    /**
     * Creates a new Project.
     *
     * @param tool the tool running the lint check
     * @param dir the root directory of the project
     * @param referenceDir See {@link #getReferenceDir()}.
     */
    public Project(LintClient tool, File dir, File referenceDir) {
        mTool = tool;
        mDir = dir;
        mReferenceDir = referenceDir;
    }

    @Override
    public String toString() {
        return "Project [dir=" + mDir + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((mDir == null) ? 0 : mDir.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Project other = (Project) obj;
        if (mDir == null) {
            if (other.mDir != null)
                return false;
        } else if (!mDir.equals(other.mDir))
            return false;
        return true;
    }

    /**
     * Adds the given file to the list of files which should be checked in this
     * project. If no files are added, the whole project will be checked.
     *
     * @param file the file to be checked
     */
    public void addFile(File file) {
        if (mFiles == null) {
            mFiles = new ArrayList<File>();
        }
        mFiles.add(file);
    }

    /**
     * The list of files to be checked in this project. If null, the whole
     * project should be checked.
     *
     * @return the subset of files to be checked, or null for the whole project
     */
    public List<File> getSubset() {
        return mFiles;
    }

    /**
     * Returns the list of source folders for Java source files
     *
     * @return a list of source folders to search for .java files
     */
    public List<File> getJavaSourceFolders() {
        if (mJavaSourceFolders == null) {
            mJavaSourceFolders = mTool.getJavaSourceFolders(this);
        }

        return mJavaSourceFolders;
    }

    /**
     * Returns the list of output folders for class files
     * @return a list of output folders to search for .class files
     */
    public List<File> getJavaClassFolders() {
        if (mJavaClassFolders == null) {
            mJavaClassFolders = mTool.getJavaClassFolders(this);
        }
        return mJavaClassFolders;
    }

    /**
     * Returns the relative path of a given file relative to the user specified
     * directory (which is often the project directory but sometimes a higher up
     * directory when a directory tree is being scanned
     *
     * @param file the file under this project to check
     * @return the path relative to the reference directory (often the project directory)
     */
    public String getDisplayPath(File file) {
       String path = file.getPath();
       String referencePath = mReferenceDir.getPath();
       if (path.startsWith(referencePath)) {
           int length = referencePath.length();
           if (path.length() > length && path.charAt(length) == File.separatorChar) {
               length++;
           }

           return path.substring(length);
       }

       return path;
    }

    /**
     * Returns the relative path of a given file within the current project.
     *
     * @param file the file under this project to check
     * @return the path relative to the project
     */
    public String getRelativePath(File file) {
       String path = file.getPath();
       String referencePath = mDir.getPath();
       if (path.startsWith(referencePath)) {
           int length = referencePath.length();
           if (path.length() > length && path.charAt(length) == File.separatorChar) {
               length++;
           }

           return path.substring(length);
       }

       return path;
    }

    /**
     * Returns the project root directory
     *
     * @return the dir
     */
    public File getDir() {
        return mDir;
    }

    /**
     * Returns the original user supplied directory where the lint search
     * started. For example, if you run lint against {@code /tmp/foo}, and it
     * finds a project to lint in {@code /tmp/foo/dev/src/project1}, then the
     * {@code dir} is {@code /tmp/foo/dev/src/project1} and the
     * {@code referenceDir} is {@code /tmp/foo/}.
     *
     * @return the reference directory, never null
     */
    public File getReferenceDir() {
        return mReferenceDir;
    }

    /**
     * Gets the configuration associated with this project
     *
     * @return the configuration associated with this project
     */
    public Configuration getConfiguration() {
        return mConfiguration;
    }

    /**
     * Sets the configuration associated with this project
     *
     * @param configuration sets the configuration associated with this project
     */
    public void setConfiguration(Configuration configuration) {
        mConfiguration = configuration;
    }
}
