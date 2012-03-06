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

package com.android.tools.lint.client.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Severity;
import com.google.common.annotations.Beta;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Information about the tool embedding the lint analyzer. IDEs and other tools
 * implementing lint support will extend this to integrate logging, displaying errors,
 * etc.
 * <p/>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
@Beta
public abstract class LintClient {

    private static final String PROP_BIN_DIR  = "com.android.tools.lint.bindir";  //$NON-NLS-1$

    /**
     * Returns a configuration for use by the given project. The configuration
     * provides information about which issues are enabled, any customizations
     * to the severity of an issue, etc.
     * <p>
     * By default this method returns a {@link DefaultConfiguration}.
     *
     * @param project the project to obtain a configuration for
     * @return a configuration, never null.
     */
    public Configuration getConfiguration(@NonNull Project project) {
        return DefaultConfiguration.create(this, project, null);
    }

    /**
     * Report the given issue. This method will only be called if the configuration
     * provided by {@link #getConfiguration(Project)} has reported the corresponding
     * issue as enabled and has not filtered out the issue with its
     * {@link Configuration#ignore(Context, Issue, Location, String, Object)} method.
     * <p>
     *
     * @param context the context used by the detector when the issue was found
     * @param issue the issue that was found
     * @param severity the severity of the issue
     * @param location the location of the issue
     * @param message the associated user message
     * @param data optional extra data for a discovered issue, or null. The
     *            content depends on the specific issue. Detectors can pass
     *            extra info here which automatic fix tools etc can use to
     *            extract relevant information instead of relying on parsing the
     *            error message text. See each detector for details on which
     *            data if any is supplied for a given issue.
     */
    public abstract void report(
            @NonNull Context context,
            @NonNull Issue issue,
            @NonNull Severity severity,
            @Nullable Location location,
            @NonNull String message,
            @Nullable Object data);

    /**
     * Send an exception or error message (with warning severity) to the log
     *
     * @param exception the exception, possibly null
     * @param format the error message using {@link String#format} syntax, possibly null
     *    (though in that case the exception should not be null)
     * @param args any arguments for the format string
     */
    public void log(
            @Nullable Throwable exception,
            @Nullable String format,
            @Nullable Object... args) {
        log(Severity.WARNING, exception, format, args);
    }

    /**
     * Send an exception or error message to the log
     *
     * @param severity the severity of the warning
     * @param exception the exception, possibly null
     * @param format the error message using {@link String#format} syntax, possibly null
     *    (though in that case the exception should not be null)
     * @param args any arguments for the format string
     */
    public abstract void log(
            @NonNull Severity severity,
            @Nullable Throwable exception,
            @Nullable String format,
            @Nullable Object... args);

    /**
     * Returns a {@link IDomParser} to use to parse XML
     *
     * @return a new {@link IDomParser}, or null if this client does not support
     *         XML analysis
     */
    @Nullable
    public abstract IDomParser getDomParser();

    /**
     * Returns a {@link IJavaParser} to use to parse Java
     *
     * @return a new {@link IJavaParser}, or null if this client does not
     *         support Java analysis
     */
    @Nullable
    public abstract IJavaParser getJavaParser();

    /**
     * Returns an optimal detector, if applicable. By default, just returns the
     * original detector, but tools can replace detectors using this hook with a version
     * that takes advantage of native capabilities of the tool.
     *
     * @param detectorClass the class of the detector to be replaced
     * @return the new detector class, or just the original detector (not null)
     */
    @NonNull
    public Class<? extends Detector> replaceDetector(
            @NonNull Class<? extends Detector> detectorClass) {
        return detectorClass;
    }

    /**
     * Reads the given text file and returns the content as a string
     *
     * @param file the file to read
     * @return the string to return, never null (will be empty if there is an
     *         I/O error)
     */
    @NonNull
    public abstract String readFile(@NonNull File file);

    /**
     * Returns the list of source folders for Java source files
     *
     * @param project the project to look up Java source file locations for
     * @return a list of source folders to search for .java files
     */
    @NonNull
    public List<File> getJavaSourceFolders(@NonNull Project project) {
        return getEclipseClasspath(project, "src", "src", "gen"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Returns the list of output folders for class files
     *
     * @param project the project to look up class file locations for
     * @return a list of output folders to search for .class files
     */
    @NonNull
    public List<File> getJavaClassFolders(@NonNull Project project) {
        return getEclipseClasspath(project, "output", "bin"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Returns the list of Java libraries
     *
     * @param project the project to look up jar dependencies for
     * @return a list of jar dependencies containing .class files
     */
    @NonNull
    public List<File> getJavaLibraries(@NonNull Project project) {
        return getEclipseClasspath(project, "lib"); //$NON-NLS-1$
    }

    /**
     * Returns the {@link SdkInfo} to use for the given project.
     *
     * @param project the project to look up an {@link SdkInfo} for
     * @return an {@link SdkInfo} for the project
     */
    @NonNull
    public SdkInfo getSdkInfo(@NonNull Project project) {
        // By default no per-platform SDK info
        return new DefaultSdkInfo();
    }

    /**
     * Returns a suitable location for storing cache files. Note that the
     * directory may not exist.
     *
     * @param create if true, attempt to create the cache dir if it does not
     *            exist
     * @return a suitable location for storing cache files, which may be null if
     *         the create flag was false, or if for some reason the directory
     *         could not be created
     */
    @NonNull
    public File getCacheDir(boolean create) {
        String home = System.getProperty("user.home");
        String relative = ".android" + File.separator + "cache"; //$NON-NLS-1$ //$NON-NLS-2$
        File dir = new File(home, relative);
        if (create && !dir.exists()) {
            if (!dir.mkdirs()) {
                return null;
            }
        }
        return dir;
    }

    /**
     * Returns the File corresponding to the system property or the environment variable
     * for {@link #PROP_BIN_DIR}.
     * This property is typically set by the SDK/tools/lint[.bat] wrapper.
     * It denotes the path of the wrapper on disk.
     *
     * @return A new File corresponding to {@link LintClient#PROP_BIN_DIR} or null.
     */
    @Nullable
    private File getLintBinDir() {
        // First check the Java properties (e.g. set using "java -jar ... -Dname=value")
        String path = System.getProperty(PROP_BIN_DIR);
        if (path == null || path.length() == 0) {
            // If not found, check environment variables.
            path = System.getenv(PROP_BIN_DIR);
        }
        if (path != null && path.length() > 0) {
            return new File(path);
        }
        return null;
    }

    /**
     * Locates an SDK resource (relative to the SDK root directory).
     * <p>
     * TODO: Consider switching to a {@link URL} return type instead.
     *
     * @param relativePath A relative path (using {@link File#separator} to
     *            separate path components) to the given resource
     * @return a {@link File} pointing to the resource, or null if it does not
     *         exist
     */
    @Nullable
    public File findResource(@NonNull String relativePath) {
        File dir = getLintBinDir();
        if (dir == null) {
            throw new IllegalArgumentException("Lint must be invoked with the System property "
                    + PROP_BIN_DIR + " pointing to the ANDROID_SDK tools directory");
        }

        File top = dir.getParentFile();
        File file = new File(top, relativePath);
        if (file.exists()) {
            return file;
        } else {
            return null;
        }
    }

    /**
     * Considers the given directory as an Eclipse project and returns either
     * its source or its output folders depending on the {@code attribute} parameter.
     */
    @NonNull
    private List<File> getEclipseClasspath(@NonNull Project project, @NonNull String attribute,
            @NonNull String... fallbackPaths) {
        List<File> folders = new ArrayList<File>();
        File projectDir = project.getDir();
        File classpathFile = new File(projectDir, ".classpath"); //$NON-NLS-1$
        if (classpathFile.exists()) {
            String classpathXml = readFile(classpathFile);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            InputSource is = new InputSource(new StringReader(classpathXml));
            factory.setNamespaceAware(false);
            factory.setValidating(false);
            try {
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document document = builder.parse(is);
                NodeList tags = document.getElementsByTagName("classpathentry"); //$NON-NLS-1$
                for (int i = 0, n = tags.getLength(); i < n; i++) {
                    Element element = (Element) tags.item(i);
                    String kind = element.getAttribute("kind"); //$NON-NLS-1$
                    if (kind.equals(attribute)) {
                        String path = element.getAttribute("path"); //$NON-NLS-1$
                        File sourceFolder = new File(projectDir, path);
                        if (sourceFolder.exists()) {
                            folders.add(sourceFolder);
                        }
                    }
                }
            } catch (Exception e) {
                log(null, null);
            }
        }

        // Fallback?
        if (folders.size() == 0) {
            for (String fallbackPath : fallbackPaths) {
                File folder = new File(projectDir, fallbackPath);
                if (folder.exists()) {
                    folders.add(folder);
                }
            }
        }

        return folders;
    }

    /**
     * A map from directory to existing projects, or null. Used to ensure that
     * projects are unique for a directory (in case we process a library project
     * before its including project for example)
     */
    private Map<File, Project> mDirToProject;

    /**
     * Returns a project for the given directory. This should return the same
     * project for the same directory if called repeatedly.
     *
     * @param dir the directory containing the project
     * @param referenceDir See {@link Project#getReferenceDir()}.
     * @return a project, never null
     */
    @NonNull
    public Project getProject(@NonNull File dir, @NonNull File referenceDir) {
        if (mDirToProject == null) {
            mDirToProject = new HashMap<File, Project>();
        }

        File canonicalDir = dir;
        try {
            // Attempt to use the canonical handle for the file, in case there
            // are symlinks etc present (since when handling library projects,
            // we also call getCanonicalFile to compute the result of appending
            // relative paths, which can then resolve symlinks and end up with
            // a different prefix)
            canonicalDir = dir.getCanonicalFile();
        } catch (IOException ioe) {
            // pass
        }

        Project project = mDirToProject.get(canonicalDir);
        if (project != null) {
            return project;
        }


        project = Project.create(this, dir, referenceDir);
        mDirToProject.put(canonicalDir, project);
        return project;
    }
}
