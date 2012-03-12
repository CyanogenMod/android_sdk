/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.ide.eclipse.adt.internal.actions;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.AdtUtils;
import com.android.ide.eclipse.adt.internal.sdk.AdtConsoleSdkLog;
import com.android.ide.eclipse.adt.internal.sdk.ProjectState;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.internal.project.ProjectProperties.PropertyType;
import com.android.sdklib.internal.project.ProjectPropertiesWorkingCopy;
import com.android.sdklib.io.FileOp;
import com.android.sdkuilib.internal.repository.sdkman2.AdtUpdateDialog;
import com.android.util.Pair;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.filesystem.IFileSystem;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

/**
 * An action to add the android-support-v4.jar compatibility library
 * to the selected project.
 * <p/>
 * This should be used by the GLE. The action itself is currently more
 * like an example of how to invoke the new {@link AdtUpdateDialog}.
 * <p/>
 * TODO: make this more configurable.
 */
public class AddCompatibilityJarAction implements IObjectActionDelegate {

    private ISelection mSelection;

    /**
     * @see IObjectActionDelegate#setActivePart(IAction, IWorkbenchPart)
     */
    @Override
    public void setActivePart(IAction action, IWorkbenchPart targetPart) {
    }

    @Override
    public void run(IAction action) {
        if (mSelection instanceof IStructuredSelection) {

            for (Iterator<?> it = ((IStructuredSelection) mSelection).iterator();
                    it.hasNext();) {
                Object element = it.next();
                IProject project = null;
                if (element instanceof IProject) {
                    project = (IProject) element;
                } else if (element instanceof IAdaptable) {
                    project = (IProject) ((IAdaptable) element)
                            .getAdapter(IProject.class);
                }
                if (project != null) {
                    install(project);
                }
            }
        }
    }

    @Override
    public void selectionChanged(IAction action, ISelection selection) {
        mSelection = selection;
    }

    /**
     * Install the compatibility jar into the given project.
     *
     * @param project The Android project to install the compatibility jar into
     * @return true if the installation was successful
     */
    public static boolean install(final IProject project) {
        File jarPath = installSupport();
        if (jarPath != null) {
            try {
                return copyJarIntoProject(project, jarPath) != null;
            } catch (Exception e) {
                AdtPlugin.log(e, null);
            }
        }

        return false;
    }

    private static File installSupport() {

        final Sdk sdk = Sdk.getCurrent();
        if (sdk == null) {
            AdtPlugin.printErrorToConsole(
                    AddCompatibilityJarAction.class.getSimpleName(),   // tag
                    "Error: Android SDK is not loaded yet."); //$NON-NLS-1$
            return null;
        }

        // TODO: For the generic action, check the library isn't in the project already.

        // First call the package manager to make sure the package is installed
        // and get the installation path of the library.

        AdtUpdateDialog window = new AdtUpdateDialog(
                AdtPlugin.getDisplay().getActiveShell(),
                new AdtConsoleSdkLog(),
                sdk.getSdkLocation());

        Pair<Boolean, File> result = window.installExtraPackage(
                "android", "support");    //$NON-NLS-1$ //$NON-NLS-2$

        // TODO: Make sure the version is at the required level; we know we need at least one
        // containing the v7 support

        if (!result.getFirst().booleanValue()) {
            AdtPlugin.printErrorToConsole("Failed to install Android Compatibility library");
            return null;
        }

        // TODO these "v4" values needs to be dynamic, e.g. we could try to match
        // vN/android-support-vN.jar. Eventually we'll want to rely on info from the
        // package manifest anyway so this is irrelevant.

        File path = new File(result.getSecond(), "v4");                   //$NON-NLS-1$
        final File jarPath = new File(path, "android-support-v4.jar");    //$NON-NLS-1$

        if (!jarPath.isFile()) {
            AdtPlugin.printErrorToConsole("Android Compatibility JAR not found:",
                    jarPath.getAbsolutePath());
            return null;
        }

        return jarPath;
    }

    /**
     * Similar to {@link #install}, but rather than copy a jar into the given
     * project, it creates a new library project in the workspace for the
     * compatibility library, and adds a library dependency on the newly
     * installed library from the given project.
     *
     * @param project the project to add a dependency on the library to
     * @param waitForFinish If true, block until the task has finished
     * @return true if the installation was successful (or if
     *         <code>waitForFinish</code> is false, if the installation is
     *         likely to be successful - e.g. the user has at least agreed to
     *         all installation prompts.)
     */
    public static boolean installLibrary(final IProject project, boolean waitForFinish) {
        final IJavaProject javaProject = JavaCore.create(project);
        if (javaProject != null) {

            File sdk = new File(Sdk.getCurrent().getSdkLocation());
            File supportPath = new File(sdk,
                    SdkConstants.FD_EXTRAS + File.separator
                    + "android" + File.separator   //$NON-NLS-1$
                    + "support");                  //$NON-NLS-1$
            if (!supportPath.isDirectory()) {
                File path = installSupport();
                if (path == null) {
                    return false;
                }
                assert path.equals(supportPath);
            }
            File libraryPath = new File(supportPath,
                    "v7" + File.separator   //$NON-NLS-1$
                    + "gridlayout");        //$NON-NLS-1$
            if (!libraryPath.isDirectory()) {
                // Upgrade support package: it's out of date. The SDK manager will
                // perform an upgrade to the latest version if the package is already installed.
                File path = installSupport();
                if (path == null) {
                    return false;
                }
                assert path.equals(libraryPath) : path;
            }

            // Create workspace copy of the project and add library dependency
            IProject libraryProject = createLibraryProject(libraryPath, project, waitForFinish);
            if (libraryProject != null) {
                return addLibraryDependency(libraryProject, project, waitForFinish);
            }
        }

        return false;
    }

    /**
     * Creates a library project in the Eclipse workspace out of the grid layout project
     * in the SDK tree.
     *
     * @param libraryPath the path to the directory tree containing the project contents
     * @param project the project to copy the SDK target out of
     * @param waitForFinish whether the operation should finish before this method returns
     * @return a library project, or null if it fails for some reason
     */
    private static IProject createLibraryProject(
            final File libraryPath,
            final IProject project,
            boolean waitForFinish) {

        // Install a new library into the workspace. This is a copy rather than
        // a reference to the compatibility library version such that modifications
        // do not modify the pristine copy in the SDK install area.

        final IProject newProject;
        try {
            IProgressMonitor monitor = new NullProgressMonitor();
            IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();

            String name = AdtUtils.getUniqueProjectName(
                    "gridlayout_v7", "_"); //$NON-NLS-1$ //$NON-NLS-2$
            newProject = root.getProject(name);
            newProject.create(monitor);

            // Copy in the files recursively
            IFileSystem fileSystem = EFS.getLocalFileSystem();
            IFileStore sourceDir = fileSystem.getStore(libraryPath.toURI());
            IFileStore destDir = fileSystem.getStore(newProject.getLocationURI());
            sourceDir.copy(destDir, EFS.OVERWRITE, null);

            // Make sure the src folder exists
            destDir.getChild("src").mkdir(0, null /*monitor*/);

            // Set the android platform to the same level as the calling project
            ProjectState state = Sdk.getProjectState(project);
            String target = state.getProperties().getProperty(ProjectProperties.PROPERTY_TARGET);
            if (target != null && target.length() > 0) {
                ProjectProperties properties = ProjectProperties.load(libraryPath.getPath(),
                        PropertyType.PROJECT);
                ProjectPropertiesWorkingCopy copy = properties.makeWorkingCopy();
                copy.setProperty(ProjectProperties.PROPERTY_TARGET, target);
                try {
                    copy.save();
                } catch (Exception e) {
                    AdtPlugin.log(e, null);
                }
            }

            newProject.open(monitor);

            return newProject;
        } catch (CoreException e) {
            AdtPlugin.log(e, null);
            return null;
        }
    }

    /**
     * Adds a library dependency on the given library into the given project.
     *
     * @param libraryProject the library project to depend on
     * @param dependentProject the project to write the dependency into
     * @param waitForFinish whether this method should wait for the job to
     *            finish
     * @return true if the operation succeeded
     */
    public static boolean addLibraryDependency(
            final IProject libraryProject,
            final IProject dependentProject,
            boolean waitForFinish) {

        // Now add library dependency

        // Run an Eclipse asynchronous job to update the project
        Job job = new Job("Add Compatibility Library Dependency to Project") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    monitor.beginTask("Add library dependency to project build path", 3);
                    monitor.worked(1);

                    // TODO: Add library project to the project.properties file!
                    ProjectState state = Sdk.getProjectState(dependentProject);
                    ProjectPropertiesWorkingCopy mPropertiesWorkingCopy =
                            state.getProperties().makeWorkingCopy();

                    // Get the highest version number of the libraries; there cannot be any
                    // gaps so we will assign the next library the next number
                    int nextVersion = 1;
                    for (String property : mPropertiesWorkingCopy.keySet()) {
                        if (property.startsWith(ProjectProperties.PROPERTY_LIB_REF)) {
                            String s = property.substring(
                                    ProjectProperties.PROPERTY_LIB_REF.length());
                            int version = Integer.parseInt(s);
                            if (version >= nextVersion) {
                                nextVersion = version + 1;
                            }
                        }
                    }

                    IPath relativePath = libraryProject.getLocation().makeRelativeTo(
                            dependentProject.getLocation());

                    mPropertiesWorkingCopy.setProperty(
                            ProjectProperties.PROPERTY_LIB_REF + nextVersion,
                            relativePath.toString());
                    try {
                        mPropertiesWorkingCopy.save();
                        IResource projectProp = dependentProject.findMember(
                                SdkConstants.FN_PROJECT_PROPERTIES);
                        projectProp.refreshLocal(IResource.DEPTH_ZERO, new NullProgressMonitor());
                    } catch (Exception e) {
                        String msg = String.format(
                                "Failed to save %1$s for project %2$s",
                                SdkConstants.FN_PROJECT_PROPERTIES, dependentProject.getName());
                        AdtPlugin.log(e, msg);
                    }

                    // Project fix-ups
                    Job fix = FixProjectAction.createFixProjectJob(libraryProject);
                    fix.schedule();
                    fix.join();

                    monitor.worked(1);

                    return Status.OK_STATUS;
                } catch (Exception e) {
                    return new Status(Status.ERROR, AdtPlugin.PLUGIN_ID, Status.ERROR,
                                      "Failed", e); //$NON-NLS-1$
                } finally {
                    if (monitor != null) {
                        monitor.done();
                    }
                }
            }
        };
        job.schedule();

        if (waitForFinish) {
            try {
                job.join();
                return job.getState() == IStatus.OK;
            } catch (InterruptedException e) {
                AdtPlugin.log(e, null);
            }
        }

        return true;
    }

    private static IResource copyJarIntoProject(
            IProject project,
            File jarPath) throws IOException, CoreException {
        IFolder resFolder = project.getFolder(SdkConstants.FD_NATIVE_LIBS);
        if (!resFolder.exists()) {
            resFolder.create(IResource.FORCE, true /*local*/, null);
        }

        IFile destFile = resFolder.getFile(jarPath.getName());
        IPath loc = destFile.getLocation();
        File destPath = loc.toFile();

        // Only modify the file if necessary so that we don't trigger unnecessary recompilations
        FileOp f = new FileOp();
        if (!f.isFile(destPath) || !f.isSameFile(jarPath, destPath)) {
            f.copyFile(jarPath, destPath);
            // Make sure Eclipse discovers java.io file changes
            resFolder.refreshLocal(1, new NullProgressMonitor());
        }

        return destFile;
    }

    /**
     * @see IWorkbenchWindowActionDelegate#init
     */
    public void init(IWorkbenchWindow window) {
        // pass
    }

}
