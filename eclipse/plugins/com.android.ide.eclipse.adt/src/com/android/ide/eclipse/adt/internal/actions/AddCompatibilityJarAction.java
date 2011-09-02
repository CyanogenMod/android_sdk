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
import com.android.ide.eclipse.adt.internal.project.ProjectHelper;
import com.android.ide.eclipse.adt.internal.sdk.AdtConsoleSdkLog;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.io.FileOp;
import com.android.sdkuilib.internal.repository.sdkman2.AdtUpdateDialog;
import com.android.util.Pair;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
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
    public void setActivePart(IAction action, IWorkbenchPart targetPart) {
    }

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
                    install(project, false /* waitForFinish */);
                }
            }
        }
    }

    public void selectionChanged(IAction action, ISelection selection) {
        mSelection = selection;
    }

    /**
     * Install the compatibility jar into the given project.
     *
     * @param project The Android project to install the compatibility jar into
     * @param waitForFinish If true, block until the task has finished
     * @return true if the installation was successful (or if <code>waitForFinish</code>
     *    is false, if the installation is likely to be successful - e.g. the user has
     *    at least agreed to all installation prompts.)
     */
    public static boolean install(final IProject project, boolean waitForFinish) {

        final IJavaProject javaProject = JavaCore.create(project);
        if (javaProject == null) {
            // Should not happen.
            AdtPlugin.log(IStatus.ERROR, "JavaProject is null for %1$s", project); //$NON-NLS-1$
        }

        final Sdk sdk = Sdk.getCurrent();
        if (sdk == null) {
            AdtPlugin.printErrorToConsole(
                    AddCompatibilityJarAction.class.getSimpleName(),   // tag
                    "Error: Android SDK is not loaded yet."); //$NON-NLS-1$
            return false;
        }

        // TODO: For the generic action, check the library isn't in the project already.

        // First call the package manager to make sure the package is installed
        // and get the installation path of the library.

        AdtUpdateDialog window = new AdtUpdateDialog(
                AdtPlugin.getDisplay().getActiveShell(),
                new AdtConsoleSdkLog(),
                sdk.getSdkLocation());

        Pair<Boolean, File> result = window.installExtraPackage(
                "android", "compatibility");    //$NON-NLS-1$ //$NON-NLS-2$

        if (!result.getFirst().booleanValue()) {
            AdtPlugin.printErrorToConsole("Failed to install Android Compatibility library");
            return false;
        }

        // TODO these "v4" values needs to be dynamic, e.g. we could try to match
        // vN/android-support-vN.jar. Eventually we'll want to rely on info from the
        // package manifest anyway so this is irrelevant.

        File path = new File(result.getSecond(), "v4");                   //$NON-NLS-1$
        final File jarPath = new File(path, "android-support-v4.jar");    //$NON-NLS-1$

        if (!jarPath.isFile()) {
            AdtPlugin.printErrorToConsole("Android Compatibility JAR not found:",
                    jarPath.getAbsolutePath());
            return false;
        }

        // Then run an Eclipse asynchronous job to update the project

        Job job = new Job("Add Compatibility Library to Project") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    monitor.beginTask("Add library to project build path", 3);

                    IResource jarRes = copyJarIntoProject(project, jarPath, monitor);

                    monitor.worked(1);

                    IClasspathEntry libEntry = JavaCore.newLibraryEntry(
                            jarRes.getFullPath(),
                            null /*sourceAttachmentPath*/,
                            null /*sourceAttachmentRootPath*/ );

                    if (!ProjectHelper.isEntryInClasspath(javaProject, libEntry)) {
                        ProjectHelper.addEntryToClasspath(javaProject, libEntry);
                    }

                    monitor.worked(1);

                    return Status.OK_STATUS;
                } catch (JavaModelException e) {
                    return e.getJavaModelStatus();
                } catch (CoreException e) {
                    return e.getStatus();
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
            File jarPath,
            IProgressMonitor monitor) throws IOException, CoreException {
        IFolder resFolder = project.getFolder(SdkConstants.FD_NATIVE_LIBS);
        if (!resFolder.exists()) {
            resFolder.create(IResource.FORCE, true /*local*/, new SubProgressMonitor(monitor, 1));
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
