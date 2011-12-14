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

import static com.android.tools.lint.detector.api.LintConstants.ANDROID_LIBRARY;
import static com.android.tools.lint.detector.api.LintConstants.ANDROID_LIBRARY_REFERENCE_FORMAT;
import static com.android.tools.lint.detector.api.LintConstants.ANDROID_URI;
import static com.android.tools.lint.detector.api.LintConstants.ATTR_MIN_SDK_VERSION;
import static com.android.tools.lint.detector.api.LintConstants.ATTR_PACKAGE;
import static com.android.tools.lint.detector.api.LintConstants.ATTR_TARGET_SDK_VERSION;
import static com.android.tools.lint.detector.api.LintConstants.PROJECT_PROPERTIES;
import static com.android.tools.lint.detector.api.LintConstants.TAG_USES_SDK;
import static com.android.tools.lint.detector.api.LintConstants.VALUE_TRUE;

import com.android.tools.lint.client.api.Configuration;
import com.android.tools.lint.client.api.LintClient;
import com.google.common.annotations.Beta;
import com.google.common.io.Closeables;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * A project contains information about an Android project being scanned for
 * Lint errors.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
@Beta
public class Project {
    private final LintClient mClient;
    private final File mDir;
    private final File mReferenceDir;
    private Configuration mConfiguration;
    private String mPackage;
    private int mMinSdk = -1;
    private int mTargetSdk = -1;
    private boolean mLibrary;

    /**
     * If non null, specifies a non-empty list of specific files under this
     * project which should be checked.
     */
    private List<File> mFiles;
    private List<File> mJavaSourceFolders;
    private List<File> mJavaClassFolders;
    private List<Project> mDirectLibraries;
    private List<Project> mAllLibraries;

    /**
     * Creates a new {@link Project} for the given directory.
     *
     * @param client the tool running the lint check
     * @param dir the root directory of the project
     * @param referenceDir See {@link #getReferenceDir()}.
     * @return a new {@link Project}
     */
    public static Project create(LintClient client, File dir, File referenceDir) {
        return new Project(client, dir, referenceDir);
    }

    /** Creates a new Project. Use one of the factory methods to create. */
    private Project(LintClient client, File dir, File referenceDir) {
        mClient = client;
        mDir = dir;
        mReferenceDir = referenceDir;

        try {
            // Read properties file and initialize library state
            Properties properties = new Properties();
            File propFile = new File(dir, PROJECT_PROPERTIES);
            if (propFile.exists()) {
                BufferedInputStream is = new BufferedInputStream(new FileInputStream(propFile));
                try {
                    properties.load(is);
                    String value = properties.getProperty(ANDROID_LIBRARY);
                    mLibrary = VALUE_TRUE.equals(value);

                    for (int i = 1; i < 1000; i++) {
                        String key = String.format(ANDROID_LIBRARY_REFERENCE_FORMAT, i);
                        String library = properties.getProperty(key);
                        if (library == null || library.length() == 0) {
                            // No holes in the numbering sequence is allowed
                            break;
                        }

                        File libraryDir = new File(dir, library).getCanonicalFile();

                        if (mDirectLibraries == null) {
                            mDirectLibraries = new ArrayList<Project>();
                        }

                        // Adjust the reference dir to be a proper prefix path of the
                        // library dir
                        File libraryReferenceDir = referenceDir;
                        if (!libraryDir.getPath().startsWith(referenceDir.getPath())) {
                            File f = libraryReferenceDir;
                            while (f != null && f.getPath().length() > 0) {
                                if (libraryDir.getPath().startsWith(f.getPath())) {
                                    libraryReferenceDir = f;
                                    break;
                                }
                                f = f.getParentFile();
                            }
                        }

                        mDirectLibraries.add(client.getProject(libraryDir, libraryReferenceDir));
                    }
                } finally {
                    Closeables.closeQuietly(is);
                }
            }
        } catch (IOException ioe) {
            client.log(ioe, "Initializing project state");
        }

        if (mDirectLibraries != null) {
            mDirectLibraries = Collections.unmodifiableList(mDirectLibraries);
        } else {
            mDirectLibraries = Collections.emptyList();
        }
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
            mJavaSourceFolders = mClient.getJavaSourceFolders(this);
        }

        return mJavaSourceFolders;
    }

    /**
     * Returns the list of output folders for class files
     * @return a list of output folders to search for .class files
     */
    public List<File> getJavaClassFolders() {
        if (mJavaClassFolders == null) {
            mJavaClassFolders = mClient.getJavaClassFolders(this);
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
        if (mConfiguration == null) {
            mConfiguration = mClient.getConfiguration(this);
        }
        return mConfiguration;
    }

    /**
     * Returns the application package specified by the manifest
     *
     * @return the application package, or null if unknown
     */
    public String getPackage() {
        //assert !mLibrary; // Should call getPackage on the master project, not the library
        // Assertion disabled because you might be running lint on a standalone library project.

        return mPackage;
    }

    /**
     * Returns the minimum API level requested by the manifest, or -1 if not
     * specified
     *
     * @return the minimum API level or -1 if unknown
     */
    public int getMinSdk() {
        //assert !mLibrary; // Should call getMinSdk on the master project, not the library
        // Assertion disabled because you might be running lint on a standalone library project.

        return mMinSdk;
    }

    /**
     * Returns the target API level specified by the manifest, or -1 if not
     * specified
     *
     * @return the target API level or -1 if unknown
     */
    public int getTargetSdk() {
        //assert !mLibrary; // Should call getTargetSdk on the master project, not the library
        // Assertion disabled because you might be running lint on a standalone library project.

        return mTargetSdk;
    }

    /**
     * Initialized the manifest state from the given manifest model
     *
     * @param document the DOM document for the manifest XML document
     */
    public void readManifest(Document document) {
        assert !mLibrary; // Should call readManifest on the master project, not the library
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        mPackage = root.getAttribute(ATTR_PACKAGE);

        // Initialize minSdk and targetSdk
        NodeList usesSdks = root.getElementsByTagName(TAG_USES_SDK);
        if (usesSdks.getLength() > 0) {
            Element element = (Element) usesSdks.item(0);

            String minSdk = null;
            if (element.hasAttributeNS(ANDROID_URI, ATTR_MIN_SDK_VERSION)) {
                minSdk = element.getAttributeNS(ANDROID_URI, ATTR_MIN_SDK_VERSION);
            }
            if (minSdk != null) {
                try {
                    mMinSdk = Integer.valueOf(minSdk);
                } catch (NumberFormatException e) {
                    mMinSdk = -1;
                }
            }

            String targetSdk = null;
            if (element.hasAttributeNS(ANDROID_URI, ATTR_TARGET_SDK_VERSION)) {
                targetSdk = element.getAttributeNS(ANDROID_URI, ATTR_TARGET_SDK_VERSION);
            } else if (minSdk != null) {
                targetSdk = minSdk;
            }
            if (targetSdk != null) {
                try {
                    mTargetSdk = Integer.valueOf(targetSdk);
                } catch (NumberFormatException e) {
                    // TODO: Handle codenames?
                    mTargetSdk = -1;
                }
            }
        }
    }

    /**
     * Returns true if this project is an Android library project
     *
     * @return true if this project is an Android library project
     */
    public boolean isLibrary() {
        return mLibrary;
    }

    /**
     * Returns the list of library projects referenced by this project
     *
     * @return the list of library projects referenced by this project, never
     *         null
     */
    public List<Project> getDirectLibraries() {
        return mDirectLibraries;
    }

    /**
     * Returns the transitive closure of the library projects for this project
     *
     * @return the transitive closure of the library projects for this project
     */
    public List<Project> getAllLibraries() {
        if (mAllLibraries == null) {
            if (mDirectLibraries.size() == 0) {
                return mDirectLibraries;
            }

            List<Project> all = new ArrayList<Project>();
            addLibraryProjects(all);
            mAllLibraries = all;
        }

        return mAllLibraries;
    }

    /**
     * Adds this project's library project and their library projects
     * recursively into the given collection of projects
     *
     * @param collection the collection to add the projects into
     */
    private void addLibraryProjects(Collection<Project> collection) {
        for (Project library : mDirectLibraries) {
            collection.add(library);
            // Recurse
            library.addLibraryProjects(collection);
        }
    }
}
