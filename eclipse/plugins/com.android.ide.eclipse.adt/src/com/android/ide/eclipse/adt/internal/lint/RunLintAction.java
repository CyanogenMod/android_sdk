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

package com.android.ide.eclipse.adt.internal.lint;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.AdtUtils;
import com.android.ide.eclipse.adt.internal.project.BaseProjectHelper;
import com.android.ide.eclipse.adt.internal.sdk.ProjectState;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;

/** Action which runs Lint on the current project */
public class RunLintAction implements IWorkbenchWindowActionDelegate, IObjectActionDelegate {

    private ISelection mSelection;

    public void selectionChanged(IAction action, ISelection selection) {
        mSelection = selection;
    }

    public void run(IAction action) {
        IProject project = RunLintAction.getSelectedProject(mSelection);

        if (project == null) {
            // Try to look at the active editor instead
            IFile file = AdtUtils.getActiveFile();
            if (file != null) {
                project = file.getProject();
            }
        }

        if (project == null || !BaseProjectHelper.isAndroidProject(project)) {
            // If we didn't find a default project based on the selection, check how many
            // open Android projects we can find in the current workspace. If there's only
            // one, we'll just select it by default.
            IJavaProject[] projects = AdtUtils.getOpenAndroidProjects();
            if (projects.length == 1) {
                project = projects[0].getProject();
            } else {
                // ...or, if there is just one non-library project, choose that one
                project = null;
                for (IJavaProject p : projects) {
                    IProject ip = p.getProject();
                    ProjectState state = Sdk.getProjectState(ip);
                    if (state != null && !state.isLibrary()) {
                        if (project != null) {
                            // More than one
                            project = null;
                            break;
                        } else {
                            project = ip;
                        }
                    }
                }
            }
        }

        if (project != null && !BaseProjectHelper.isAndroidProject(project)) {
            MessageDialog.openWarning(AdtPlugin.getDisplay().getActiveShell(), "Lint",
                    "Select an Android project.");
            return;
        }

        if (project != null) {
            LintRunner.startLint(project, null, false);

            // Show lint view where the results are listed
            LintViewPart.show(project);
        } else {
            MessageDialog.openWarning(AdtPlugin.getDisplay().getActiveShell(), "Lint",
                    "Could not run Lint: Select a project first.");
        }
    }

    /** Returns the selected project, if there is exactly one selected project */
    static IProject getSelectedProject(ISelection selection) {
        if (selection instanceof IStructuredSelection) {
            IStructuredSelection structuredSelection = (IStructuredSelection) selection;
            // get the unique selected item.
            if (structuredSelection.size() == 1) {
                Object element = structuredSelection.getFirstElement();

                // First look up the resource (since some adaptables
                // provide an IResource but not an IProject, and we can
                // always go from IResource to IProject)
                IResource resource = null;
                if (element instanceof IResource) { // may include IProject
                   resource = (IResource) element;
                } else if (element instanceof IAdaptable) {
                    IAdaptable adaptable = (IAdaptable)element;
                    Object adapter = adaptable.getAdapter(IResource.class);
                    resource = (IResource) adapter;
                }

                // get the project object from it.
                IProject project = null;
                if (resource != null) {
                    project = resource.getProject();
                } else if (element instanceof IAdaptable) {
                    project = (IProject) ((IAdaptable) element).getAdapter(IProject.class);
                }

                return project;
            }
        }

        return null;
    }

    public void setActivePart(IAction action, IWorkbenchPart targetPart) {
    }

    public void dispose() {
        // Nothing to dispose
    }

    public void init(IWorkbenchWindow window) {
    }
}
