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

import com.android.SdkConstants;
import com.android.ide.eclipse.adt.AdtConstants;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.editors.manifest.ManifestInfo;
import com.android.ide.eclipse.adt.internal.sdk.ProjectState;
import com.android.ide.eclipse.adt.internal.sdk.ProjectState.LibraryState;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.ide.eclipse.adt.io.IFolderWrapper;
import com.android.io.IAbstractFile;
import com.android.sdklib.io.FileOp;
import com.android.utils.Pair;
import com.android.xml.AndroidManifest;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Creates build.gradle and settings.gradle files for a set of projects.
 * <p>
 * Based on {@link org.eclipse.ant.internal.ui.datatransfer.BuildFileCreator}
 */
public class BuildFileCreator {
    private static final String BUILD_FILE = "build.gradle"; //$NON-NLS-1$
    private static final String SETTINGS_FILE = "settings.gradle"; //$NON-NLS-1$
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
    private final IPath commonRoot;

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

            Set<IJavaProject> fullProjectSet = Sets.newHashSet();

            // build a list of all projects that must be included. This is in case
            // some dependencies have not been included in the selected projects. We also include
            // parent projects so that the full multi-project setup is correct.
            // Note that if two projects are selected that are not related, both will be added
            // in the same multi-project anyway.
            for (IJavaProject javaProject : projects) {
                fullProjectSet.add(javaProject);
                ProjectState projectState = Sdk.getProjectState(javaProject.getProject());

                // add dependencies
                List<IProject> dependencies = projectState.getFullLibraryProjects();
                for (IProject dependency : dependencies) {
                    fullProjectSet.add(JavaCore.create(dependency));
                }

                Collection<ProjectState> parents = projectState.getFullParentProjects();
                for (ProjectState parent : parents) {
                    fullProjectSet.add(JavaCore.create(parent.getProject()));
                }
            }

            // determine files to create/change
            List<IFile> files = new ArrayList<IFile>();

            // add the build.gradle file for all projects.
            for (IJavaProject project : fullProjectSet) {
                // build.gradle file
                IFile file = project.getProject().getFile(BuildFileCreator.BUILD_FILE);
                files.add(file);
            }

            // get the commonRoot for all projects. If only one project, this returns the path
            // of the project.
            IPath commonRoot = determineCommonRoot(fullProjectSet);

            IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
            IPath workspaceLocation = workspaceRoot.getLocation();

            IPath relativePath = commonRoot.makeRelativeTo(workspaceLocation);
            boolean rootInWorkspace = !relativePath.equals(commonRoot);

            File settingsFile = new File(commonRoot.toFile(), SETTINGS_FILE);

            // more than one project -> generate settings.gradle
            if (fullProjectSet.size() > 1 && rootInWorkspace) {
                // Locate the settings.gradle file and add it to the changed files list
                IPath settingsGradle = Path.fromOSString(settingsFile.getAbsolutePath());

                // different path, means commonRoot is inside the workspace, which means we have
                // to add settings.gradle and wrapper files to the list of files to add.
                IFile iFile = workspaceRoot.getFile(settingsGradle);
                if (iFile != null) {
                    files.add(iFile);
                }
            }

            // Gradle wrapper files
            if (hasGradleWrapper && rootInWorkspace) {
                // See if there already wrapper files there and only mark nonexistent ones for
                // creation.
                for (File wrapperFile : getGradleWrapperFiles(commonRoot.toFile())) {
                    if (!wrapperFile.exists()) {
                        IPath path = Path.fromOSString(wrapperFile.getAbsolutePath());
                        IFile file = workspaceRoot.getFile(path);
                        files.add(file);
                    }
                }
            }

            // Trigger checkout of changed files
            Set<IFile> confirmedFiles = validateEdit(shell, files);

            // Now iterate over all the projects and generate the build files.
            localmonitor = SubMonitor.convert(pm, ExportMessages.PageTitle,
                    confirmedFiles.size());
            List<String> projectSettingsPath = Lists.newArrayList();
            for (IJavaProject currentProject : fullProjectSet) {
                IFile file = currentProject.getProject().getFile(BuildFileCreator.BUILD_FILE);
                if (!confirmedFiles.contains(file)) {
                    continue;
                }

                localmonitor.setTaskName(NLS.bind(ExportMessages.FileStatusMessage,
                        currentProject.getProject().getName()));

                ProjectState projectState = Sdk.getProjectState(currentProject.getProject());
                BuildFileCreator instance = new BuildFileCreator(currentProject, commonRoot, shell);
                if (projectState != null) {
                    // This is an Android project
                    instance.appendHeader(projectState.isLibrary());
                    instance.appendDependencies();
                    instance.startAndroidTask(projectState);
                    //instance.appendDefaultConfig();
                    instance.createAndroidSourceSets();
                    instance.finishAndroidTask();
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

                // get the project path to add it to the settings.gradle.
                projectSettingsPath.add(instance.getRelativeGradleProjectPath());
            }

            // write the settings file.
            if (fullProjectSet.size() > 1) {
                writeGradleSettingsFile(settingsFile, projectSettingsPath);
                writeRootBuildGradle(new File(commonRoot.toFile(), BUILD_FILE));
            }

            // finally write the wrapper
            // TODO check we can based on where it is
            if (hasGradleWrapper) {
                copyGradleWrapper(gradleLocation, commonRoot.toFile());
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
    private BuildFileCreator(IJavaProject project, IPath commonRoot, Shell shell) {
        this.project = project;
        this.projectName = project.getProject().getName();
        this.buildfile = new StringBuilder();
        this.projectRoot = project.getResource().getLocation().toString();
        this.commonRoot = commonRoot;
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
     * If there's only one project, returns the single project's folder.
     */
    private static IPath determineCommonRoot(Collection<IJavaProject> projects) {
        IPath commonRoot = null;
        for (IJavaProject javaProject : projects) {
            if (commonRoot == null) {
                commonRoot = javaProject.getProject().getLocation();
            } else {
                commonRoot = findCommonRoot(commonRoot, javaProject.getProject().getLocation());
            }
        }

        return commonRoot;
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

        // first the local jars.
        buildfile.append("    compile fileTree(dir: 'libs', include: '*.jar')\n");

        // then the project dependencies.
        // First the Android libraries.
        ProjectState state = Sdk.getProjectState(project.getProject());

        for (LibraryState libState : state.getLibraries()) {
            String path = getRelativeGradleProjectPath(
                    libState.getProjectState().getProject().getLocation(), commonRoot);

            buildfile.append("    compile project('" + path + "')\n"); //$NON-NLS-1$
        }

        // Next, the other non-Android projects.
        for (IClasspathEntry entry : project.readRawClasspath()) {
            if (entry.getEntryKind() == IClasspathEntry.CPE_PROJECT) {
                IProject cpProject = getNonAndroidProjectByName(entry.getPath().toString());
                if (cpProject != null) {
                    String path = getRelativeGradleProjectPath(
                            cpProject.getLocation(), commonRoot);

                    buildfile.append("    compile project('" + path + "')\n"); //$NON-NLS-1$
                }
            }
        }

        buildfile.append("}\n"); //$NON-NLS-1$
        buildfile.append("\n"); //$NON-NLS-1$
    }

    /**
     * Given two IPaths, finds the parent directory of both of them.
     */
    private static IPath findCommonRoot(IPath path1, IPath path2) {
        // TODO: detect paths on different disk drive on Windows!
        if (path1 != null && path1.getDevice() != null) {
            assert path1.getDevice().equals(path2.getDevice());
        }
        IPath result = path1.uptoSegment(0);

        final int count = Math.min(path1.segmentCount(), path2.segmentCount());
        for (int i = 0; i < count; i++) {
            if (path1.segment(i).equals(path2.segment(i))) {
                result = result.append(Path.SEPARATOR + path2.segment(i));
            }
        }
        return result;
    }

    private String getRelativeGradleProjectPath() {
        return getRelativeGradleProjectPath(project.getProject().getLocation(), commonRoot);
    }

    /**
     * Converts the given path to be relative to the given root path, and converts it to
     * Gradle project notation, such as is used in the settings.gradle file.
     */
    private String getRelativeGradleProjectPath(IPath path, IPath root) {
        IPath relativePath = path.makeRelativeTo(root);
        String relativeString = relativePath.toOSString();
        return ":" + relativeString.replaceAll(Pattern.quote(File.separator), ":"); //$NON-NLS-1$
    }

    /**
     * Finds the workspace project with the given name
     */
    private static IProject getNonAndroidProjectByName(String name) {
        try {
            IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(name);
            if (project.exists() && !project.hasNature(AdtConstants.NATURE_DEFAULT)) {
                return project;
            }
        } catch (IllegalArgumentException iae) {
        } catch (CoreException e) {
        }

        return null;
    }

    /**
     * Outputs the beginning of an Android task in the build file.
     */
    private void startAndroidTask(ProjectState projectState) {
        int buildApi = projectState.getTarget().getVersion().getApiLevel();
        String toolsVersion = projectState.getTarget().getBuildToolInfo().getRevision().toString();
        buildfile.append("android {\n"); //$NON-NLS-1$
        buildfile.append("    compileSdkVersion " + buildApi + "\n"); //$NON-NLS-1$
        buildfile.append("    buildToolsVersion \"" + toolsVersion + "\"\n"); //$NON-NLS-1$
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

        String srcPaths = Joiner.on(",").join(srcDirs);

        buildfile.append("    sourceSets {\n"); //$NON-NLS-1$
        buildfile.append("        main {\n"); //$NON-NLS-1$
        buildfile.append("            manifest.srcFile '" + SdkConstants.FN_ANDROID_MANIFEST_XML + "'\n"); //$NON-NLS-1$
        buildfile.append("            java.srcDirs = [" + srcPaths + "]\n"); //$NON-NLS-1$
        buildfile.append("            resources.srcDirs = [" + srcPaths + "]\n"); //$NON-NLS-1$
        buildfile.append("            aidl.srcDirs = [" + srcPaths + "]\n"); //$NON-NLS-1$
        buildfile.append("            renderscript.srcDirs = [" + srcPaths + "]\n"); //$NON-NLS-1$
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

        String srcPaths = Joiner.on(",").join(dirs);

        buildfile.append("sourceSets {\n"); //$NON-NLS-1$
        buildfile.append("    main.java.srcDirs = [" + srcPaths + "]\n"); //$NON-NLS-1$
        buildfile.append("    main.resources.srcDirs = [" + srcPaths + "]\n"); //$NON-NLS-1$
        buildfile.append("    test.java.srcDirs = ['tests/java']\n"); //$NON-NLS-1$
        buildfile.append("    test.resources.srcDirs = ['tests/resources']\n"); //$NON-NLS-1$
        buildfile.append("}\n"); //$NON-NLS-1$
    }

    /**
     * Merges the new subproject dependencies into the settings.gradle file if it already exists,
     * and creates one if it does not.
     */
    private static void writeGradleSettingsFile(File settingsFile, List<String> projectPaths) {
        StringBuilder contents = new StringBuilder();
        for (String path : projectPaths) {
            contents.append("include '").append(path).append("'\n"); //$NON-NLS-1$ //$NON-NLS-2$
        }

        AdtPlugin.writeFile(settingsFile, contents.toString());
    }

    private static void writeRootBuildGradle(File buildFile) {
        AdtPlugin.writeFile(buildFile,
                "// Top-level build file where you can add configuration options common to all sub-projects/modules.\n");
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
