/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ide.eclipse.adt.internal.wizards.exportgradle;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Shell;

import com.android.SdkConstants;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.editors.manifest.ManifestInfo;
import com.android.ide.eclipse.adt.internal.sdk.ProjectState;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.ide.eclipse.adt.io.IFolderWrapper;
import com.android.io.IAbstractFile;
import com.android.sdklib.io.FileOp;
import com.android.utils.Pair;
import com.android.xml.AndroidManifest;
import com.google.common.base.Joiner;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Creates build.gradle and settings.gradle files for a set of projects.
 * <p>
 * Based on {@link org.eclipse.ant.internal.ui.datatransfer.BuildFileCreator}
 */
public class BuildFileCreator {
    //Finds include ':module_name_1', ':module_name_2',... statements in settings.gradle files
    private static final Pattern INCLUDE_PATTERN =
            Pattern.compile("include +(':[^']+', *)*':[^']+'"); //$NON-NLS-1$
    private static final String BUILD_FILE = "build.gradle"; //$NON-NLS-1$
    private static final String SETTINGS_FILE = "settings.gradle"; //$NON-NLS-1$
    private static final String ANDROID_SUPPORT_JAR = "android-support-v4.jar"; //$NON-NLS-1$
    private static final String NEWLINE = System.getProperty("line.separator"); //$NON-NLS-1$
    private static final String GRADLE_WRAPPER_LOCATION =
            "tools/templates/gradle/wrapper"; //$NON-NLS-1$
    static final String PLUGIN_CLASSPATH =
            "classpath 'com.android.tools.build:gradle:0.4'"; //$NON-NLS-1$
    static final String MAVEN_REPOSITORY = "mavenCentral()"; //$NON-NLS-1$

    private static final String[] GRADLE_WRAPPER_FILES = new String[] {
        "gradlew", //$NON-NLS-1$
        "gradlew.bat", //$NON-NLS-1$
        "gradle/wrapper/gradle-wrapper.jar", //$NON-NLS-1$
        "gradle/wrapper/gradle-wrapper.properties" //$NON-NLS-1$
    };

    private static final Comparator<IFile> FILE_COMPARATOR = new Comparator<IFile>() {
        @Override
        public int compare(IFile o1, IFile o2) {
            return o1.toString().compareTo(o2.toString());
        }
    };

    private StringBuilder buildfile;
    private IJavaProject project;
    private String projectName;
    private String projectRoot;
    private Set<String> settingsFileEntries = new HashSet<String>();
    private boolean needsSettingsFile = false;
    private String commonRoot = null;

    /**
     * Create buildfile for the given projects.
     *
     * @param projects create buildfiles for these <code>IJavaProject</code>
     *            objects
     * @param shell parent instance for dialogs
     * @return project names for which buildfiles were created
     * @throws InterruptedException thrown when user cancels task
     */
    public static List<String> createBuildFiles(Set<IJavaProject> projects, Shell shell,
            IProgressMonitor pm)
            throws JavaModelException, IOException, CoreException, InterruptedException {
        File gradleLocation = new File(Sdk.getCurrent().getSdkLocation(), GRADLE_WRAPPER_LOCATION);
        List<String> res = new ArrayList<String>();
        SubMonitor localmonitor = null;
        try {
            // See if we have a Gradle wrapper in the SDK templates directory. If so, we can copy
            // it over.
            boolean hasGradleWrapper = true;
            for (File wrapperFile : getGradleWrapperFiles(gradleLocation)) {
                if (!wrapperFile.exists()) {
                    hasGradleWrapper = false;
                }
            }
            // determine files to create/change
            List<IFile> files = new ArrayList<IFile>();
            for (IJavaProject project : projects) {
                // build.gradle file
                IFile file = project.getProject().getFile(BuildFileCreator.BUILD_FILE);
                files.add(file);
            }

            // Locate the settings.gradle file and add it to the changed files list
            for (IJavaProject project : projects) {
                BuildFileCreator instance = new BuildFileCreator(project, shell);
                instance.determineCommonRoot();
                IPath path = Path.fromOSString(instance.getGradleSettingsFile().getAbsolutePath());
                IFile file = project.getProject().getWorkspace().getRoot().getFile(path);
                if (file != null) {
                    files.add(file);
                }
                // Gradle wrapper files
                if (hasGradleWrapper) {
                    // See if there already wrapper files there and only mark nonexistent ones for
                    // creation.
                    for (File wrapperFile : getGradleWrapperFiles(
                            new File(project.getResource().getLocation().toString()))) {
                        if (!wrapperFile.exists()) {
                            path = Path.fromOSString(wrapperFile.getAbsolutePath());
                            file = project.getProject().getWorkspace().getRoot().getFile(path);
                            files.add(file);
                        }
                    }
                }
            }

            // Trigger checkout of changed files
            Set<IFile> confirmedFiles = validateEdit(shell, files);

            // Now iterate over all the projects and generate the build files.
            localmonitor = SubMonitor.convert(pm, ExportMessages.PageTitle,
                    confirmedFiles.size());
            for (IJavaProject currentProject : projects) {
                IFile file = currentProject.getProject().getFile(BuildFileCreator.BUILD_FILE);
                if (!confirmedFiles.contains(file)) {
                    continue;
                }

                localmonitor.setTaskName(NLS.bind(ExportMessages.FileStatusMessage,
                        currentProject.getProject().getName()));

                ProjectState projectState = Sdk.getProjectState(currentProject.getProject());
                BuildFileCreator instance = new BuildFileCreator(currentProject, shell);
                instance.determineCommonRoot();
                if (projectState != null) {
                    // This is an Android project
                    instance.appendHeader(projectState.isLibrary());
                    instance.appendDependencies();
                    instance.startAndroidTask(projectState);
                    instance.appendDefaultConfig();
                    instance.createAndroidSourceSets();
                    instance.finishAndroidTask();
                    if (instance.needsSettingsFile) {
                        instance.mergeGradleSettingsFile();
                        if (hasGradleWrapper) {
                            copyGradleWrapper(gradleLocation, new File(instance.commonRoot));
                        }
                    }
                } else {
                    // This is a plain Java project
                    instance.appendJavaHeader();
                    instance.createJavaSourceSets();
                }

                // Write the build file
                String buildfile = instance.buildfile.toString();
                InputStream is =
                        new ByteArrayInputStream(buildfile.getBytes("UTF-8")); //$NON-NLS-1$
                if (file.exists()) {
                    file.setContents(is, true, true, null);
                } else {
                    file.create(is, true, null);
                }
                if (localmonitor.isCanceled()) {
                    return res;
                }
                localmonitor.worked(1);
                res.add(instance.projectName);
            }
        } finally {
            if (localmonitor != null && !localmonitor.isCanceled()) {
                localmonitor.done();
            }
            if (pm != null) {
                pm.done();
            }
        }
        return res;
    }

    /**
     * @param project create buildfile for this project
     * @param shell parent instance for dialogs
     */
    private BuildFileCreator(IJavaProject project, Shell shell) {
        this.project = project;
        this.projectName = project.getProject().getName();
        this.buildfile = new StringBuilder();
        this.projectRoot = project.getResource().getLocation().toString();
    }

    /**
     * Return the files that comprise the Gradle wrapper as a collection of {@link File} instances.
     * @param root
     * @return
     */
    private static List<File> getGradleWrapperFiles(File root) {
        List<File> files = new ArrayList<File>(GRADLE_WRAPPER_FILES.length);
        for (String file : GRADLE_WRAPPER_FILES) {
            files.add(new File(root, file));
        }
        return files;
    }

    /**
     * Copy the Gradle wrapper files from one directory to another.
     */
    private static void copyGradleWrapper(File from, File to) throws IOException {
        for (String file : GRADLE_WRAPPER_FILES) {
            File dest = new File(to, file);
            if (dest.exists()) {
                // Don't clobber an existing file. The user may have modified it already.
                continue;
            }
            File src = new File(from, file);
            dest.getParentFile().mkdirs();
            new FileOp().copyFile(src, dest);
            dest.setExecutable(src.canExecute());
        }
    }

    /**
     * Finds the common parent directory shared by this project and all its dependencies.
     */
    private void determineCommonRoot() {
        String currentProjectRoot = project.getResource().getLocation().toString();

        if (commonRoot == null) {
            commonRoot = new Path(currentProjectRoot).removeLastSegments(1).toString();
        }

        for (IClasspathEntry entry : project.readRawClasspath()) {
            if (entry.getEntryKind() != IClasspathEntry.CPE_PROJECT) {
                continue;
            }
            IJavaProject referencedProject = getJavaProjectByName(entry.getPath().toString());
            String referencedProjectRoot = referencedProject.getResource().getLocation().toString();
            commonRoot = findCommonRoot(commonRoot, referencedProjectRoot);
        }
    }

    /**
     * Outputs boilerplate header information common to all Gradle build files.
     */
    private void appendHeader(boolean isLibrary) {
        buildfile.append("buildscript {\n"); //$NON-NLS-1$
        buildfile.append("    repositories {\n"); //$NON-NLS-1$
        buildfile.append("        " + MAVEN_REPOSITORY + "\n"); //$NON-NLS-1$
        buildfile.append("    }\n"); //$NON-NLS-1$
        buildfile.append("    dependencies {\n"); //$NON-NLS-1$
        buildfile.append("        " + PLUGIN_CLASSPATH + "\n"); //$NON-NLS-1$
        buildfile.append("    }\n"); //$NON-NLS-1$
        buildfile.append("}\n"); //$NON-NLS-1$
        if (isLibrary) {
            buildfile.append("apply plugin: 'android-library'\n"); //$NON-NLS-1$
        } else {
            buildfile.append("apply plugin: 'android'\n"); //$NON-NLS-1$
        }
        buildfile.append("\n"); //$NON-NLS-1$
    }

    /**
     * Outputs a block which sets up library and project dependencies.
     */
    private void appendDependencies() {
        if (project == null) {
            AdtPlugin.log(IStatus.WARNING, "project is not loaded in workspace"); //$NON-NLS-1$
            return;
        }
        buildfile.append("dependencies {\n"); //$NON-NLS-1$

        // We'll have to do a preliminary pass and pull out any projects that have or are
        // dependencies. Then we need to identify the common parent of all of those projects,
        // and merge all those projects into a settings.gradle file there. Then we need to build
        // relative gradle paths to each library and populate those into the compile
        // project(':a:b:library') entries output below.

        String currentProjectRoot = project.getResource().getLocation().toString();
        String path = getRelativeGradleProjectPath(currentProjectRoot, commonRoot);
        settingsFileEntries.add("'" + path + "'"); //$NON-NLS-1$

        for (IClasspathEntry entry : project.readRawClasspath()) {
            String entryPath = entry.getPath().makeAbsolute().toString();
            switch(entry.getEntryKind()) {
                case IClasspathEntry.CPE_PROJECT:
                    IJavaProject cpProject = getJavaProjectByName(entry.getPath().toString());
                    String cpProjectRoot = cpProject.getResource().getLocation().toString();
                    path = getRelativeGradleProjectPath(cpProjectRoot, commonRoot);
                    settingsFileEntries.add("'" + path + "'"); //$NON-NLS-1$
                    needsSettingsFile = true;
                    buildfile.append("    compile project('" + path + "')\n"); //$NON-NLS-1$
                    break;
                case IClasspathEntry.CPE_LIBRARY:
                    if (entry.getPath().lastSegment().equals(ANDROID_SUPPORT_JAR)) {
                        // This jar gets added automatically by the Android Gradle plugin
                        continue;
                    }
                    path = getRelativePath(entryPath, projectRoot);
                    buildfile.append("    compile files('" + path + "')\n"); //$NON-NLS-1$
                    break;
                default:
                    break;
            }
        }
        buildfile.append("}\n"); //$NON-NLS-1$
        buildfile.append("\n"); //$NON-NLS-1$
    }

    /**
     * Given two filesystem paths, finds the parent directory of both of them.
     */
    private String findCommonRoot(String path1, String path2) {
        IPath f1 = new Path(path1);
        IPath f2 = new Path(path2);
        IPath result = (IPath) Path.ROOT.clone();
        for (int i = 0; i < Math.min(f1.segmentCount(), f2.segmentCount()); i++) {
            if (f1.segment(i).equals(f2.segment(i))) {
                result = result.append(Path.SEPARATOR + f1.segment(i));
            }
        }
        return result.toString();
    }

    /**
     * Converts the given path to be relative to the given root path, and converts it to
     * Gradle project notation, such as is used in the settings.gradle file.
     */
    private String getRelativeGradleProjectPath(String path, String root) {
        String relativePath = getRelativePath(path, root);
        return ":" + relativePath.replaceAll("\\" + Path.SEPARATOR, ":"); //$NON-NLS-1$
    }

    /**
     * Returns a path which is equivalent to the given location relative to the
     * specified base path.
     */
    private static String getRelativePath(String otherLocation, String basePath) {

        IPath location = new Path(otherLocation);
        IPath base = new Path(basePath);
        if ((location.getDevice() != null && !location.getDevice()
                .equalsIgnoreCase(base.getDevice())) || !location.isAbsolute()) {
            return otherLocation;
        }
        int baseCount = base.segmentCount();
        int count = base.matchingFirstSegments(location);
        String temp = ""; //$NON-NLS-1$
        for (int j = 0; j < baseCount - count; j++) {
            temp += "../"; //$NON-NLS-1$
        }
        String relative = new Path(temp).append(
                location.removeFirstSegments(count)).toString();
        if (relative.length() == 0) {
            relative = "."; //$NON-NLS-1$
        }

        return relative;
    }

    /**
     * Finds the workspace project with the given name
     */
    private static IJavaProject getJavaProjectByName(String name) {
        try {
            IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(name);
            if (project.exists()) {
                return JavaCore.create(project);
            }
        } catch (IllegalArgumentException iae) {
        }
        return null;
    }

    /**
     * Outputs the beginning of an Android task in the build file.
     */
    private void startAndroidTask(ProjectState projectState) {
        int buildApi = projectState.getTarget().getVersion().getApiLevel();
        buildfile.append("android {\n"); //$NON-NLS-1$
        buildfile.append("    compileSdkVersion " + buildApi + "\n"); //$NON-NLS-1$
        buildfile.append("    buildToolsVersion \"" + buildApi + "\"\n"); //$NON-NLS-1$
        buildfile.append("\n"); //$NON-NLS-1$
    }

    /**
     * Outputs the defaultConfig block in the Android task.
     */
    private void appendDefaultConfig() {
        Pair<Integer, Integer> v = ManifestInfo.computeSdkVersions(project.getProject());
        int minApi = v.getFirst();
        int targetApi = v.getSecond();

        buildfile.append("    defaultConfig {\n"); //$NON-NLS-1$
        buildfile.append("        minSdkVersion " + minApi + "\n"); //$NON-NLS-1$
        buildfile.append("        targetSdkVersion " + targetApi + "\n"); //$NON-NLS-1$
        buildfile.append("    }\n"); //$NON-NLS-1$
    }

    /**
     * Outputs a sourceSets block to the Android task that locates all of the various source
     * subdirectories in the project.
     */
    private void createAndroidSourceSets() {
        IFolderWrapper projectFolder = new IFolderWrapper(project.getProject());
        IAbstractFile mManifestFile = AndroidManifest.getManifest(projectFolder);
        if (mManifestFile == null) {
            return;
        }
        List<String> srcDirs = new ArrayList<String>();
        for (IClasspathEntry entry : project.readRawClasspath()) {
            if (entry.getEntryKind() != IClasspathEntry.CPE_SOURCE ||
                    SdkConstants.FD_GEN_SOURCES.equals(entry.getPath().lastSegment())) {
                continue;
            }
            IPath path = entry.getPath().removeFirstSegments(1);
            srcDirs.add("'" + path.toOSString() + "'"); //$NON-NLS-1$
        }

        buildfile.append("    sourceSets {\n"); //$NON-NLS-1$
        buildfile.append("        main {\n"); //$NON-NLS-1$
        buildfile.append("            manifest.srcFile '"
                + getRelativePath(mManifestFile.getOsLocation(), projectRoot)
                + "'\n"); //$NON-NLS-1$
        buildfile.append("            java.srcDirs = ["
                + Joiner.on(",").join(srcDirs)
                + "]\n"); //$NON-NLS-1$
        buildfile.append("            resources.srcDirs = ['src']\n"); //$NON-NLS-1$
        buildfile.append("            aidl.srcDirs = ['src']\n"); //$NON-NLS-1$
        buildfile.append("            renderscript.srcDirs = ['src']\n"); //$NON-NLS-1$
        buildfile.append("            res.srcDirs = ['res']\n"); //$NON-NLS-1$
        buildfile.append("            assets.srcDirs = ['assets']\n"); //$NON-NLS-1$
        buildfile.append("        }\n"); //$NON-NLS-1$
        buildfile.append("\n"); //$NON-NLS-1$
        buildfile.append("        instrumentTest.setRoot('tests')\n"); //$NON-NLS-1$
        buildfile.append("    }\n"); //$NON-NLS-1$
    }

    /**
     * Outputs the completion of the Android task in the build file.
     */
    private void finishAndroidTask() {
        buildfile.append("}\n"); //$NON-NLS-1$
    }

    /**
     * Outputs a boilerplate header for non-Android projects
     */
    private void appendJavaHeader() {
        buildfile.append("apply plugin: 'java'\n"); //$NON-NLS-1$
    }

    /**
     * Outputs a sourceSets block for non-Android projects to locate the source directories.
     */
    private void createJavaSourceSets() {
        List<String> dirs = new ArrayList<String>();
        for (IClasspathEntry entry : project.readRawClasspath()) {
            if (entry.getEntryKind() != IClasspathEntry.CPE_SOURCE) {
                continue;
            }
            IPath path = entry.getPath().removeFirstSegments(1);
            dirs.add("'" + path.toOSString() + "'"); //$NON-NLS-1$
        }
        buildfile.append("sourceSets {\n"); //$NON-NLS-1$
        buildfile.append("    main.java.srcDirs = ["); //$NON-NLS-1$
        buildfile.append(Joiner.on(",").join(dirs)); //$NON-NLS-1$
        buildfile.append("]\n"); //$NON-NLS-1$
        buildfile.append("}\n"); //$NON-NLS-1$
    }

    /**
     * Merges the new subproject dependencies into the settings.gradle file if it already exists,
     * and creates one if it does not.
     */
    private void mergeGradleSettingsFile() {
        File file = getGradleSettingsFile();
        StringBuilder contents = new StringBuilder();
        if (file.exists()) {
            contents.append(AdtPlugin.readFile(file));

            for (String entry : settingsFileEntries) {
                if (contents.indexOf(entry) != -1) {
                    continue;
                }
                Matcher matcher = INCLUDE_PATTERN.matcher(contents);
                if (matcher.find()) {
                    contents.insert(matcher.end(), ", " + entry); //$NON-NLS-1$
                } else {
                    contents.insert(0, "include " + entry + "\n"); //$NON-NLS-1$
                }
            }
        } else {
            contents.append("include ");
            contents.append(Joiner.on(",").join(settingsFileEntries));
            contents.append("\n"); //$NON-NLS-1$
        }

        AdtPlugin.writeFile(file, contents.toString());
    }

    /**
     * Returns the settings.gradle file (which may not exist yet).
     */
    private File getGradleSettingsFile() {
        return new File(commonRoot, SETTINGS_FILE);
    }

    /**
     * Request write access to given files. Depending on the version control
     * plug-in opens a confirm checkout dialog.
     *
     * @param shell
     *            parent instance for dialogs
     * @return <code>IFile</code> objects for which user confirmed checkout
     * @throws CoreException
     *             thrown if project is under version control, but not connected
     */
    static Set<IFile> validateEdit(Shell shell, List<IFile> files) throws CoreException {
        Set<IFile> confirmedFiles = new TreeSet<IFile>(FILE_COMPARATOR);
        if (files.size() == 0) {
            return confirmedFiles;
        }
        IStatus status = (files.get(0)).getWorkspace().validateEdit(
                files.toArray(new IFile[files.size()]), shell);
        if (status.isMultiStatus() && status.getChildren().length > 0) {
            for (int i = 0; i < status.getChildren().length; i++) {
                IStatus statusChild = status.getChildren()[i];
                if (statusChild.isOK()) {
                    confirmedFiles.add(files.get(i));
                }
            }
        } else if (status.isOK()) {
            confirmedFiles.addAll(files);
        }
        if (status.getSeverity() == IStatus.ERROR) {
            // not possible to checkout files: not connected to version
            // control plugin or hijacked files and made read-only, so
            // collect error messages provided by validator and re-throw
            StringBuffer message = new StringBuffer(status.getPlugin() + ": " //$NON-NLS-1$
                    + status.getMessage() + NEWLINE);
            if (status.isMultiStatus()) {
                for (int i = 0; i < status.getChildren().length; i++) {
                    IStatus statusChild = status.getChildren()[i];
                    message.append(statusChild.getMessage() + NEWLINE);
                }
            }
            throw new CoreException(new Status(IStatus.ERROR,
                    AdtPlugin.PLUGIN_ID, 0, message.toString(), null));
        }

        return confirmedFiles;
    }
}
