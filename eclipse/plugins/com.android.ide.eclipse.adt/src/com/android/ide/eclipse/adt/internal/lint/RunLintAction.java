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

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IActionDelegate;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

/** Action which runs Lint on the current project */
public class RunLintAction implements IActionDelegate {

     private ISelection mSelection;

    public void selectionChanged(IAction action, ISelection selection) {
        mSelection = selection;
    }

    public void run(IAction action) {
        final IProject project = RunLintAction.getSelectedProject(mSelection);
        if (project != null) {
            Job job = LintRunner.startLint(project, null);
            if (job != null) {
                job.addJobChangeListener(new FinishListener(project));
            }

            // Show problems view since that's where the results are listed
            IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
            if (window != null) {
                IWorkbenchPage page = window.getActivePage();
                if (page != null) {
                    try {
                        String id = "org.eclipse.ui.views.ProblemView"; //$NON-NLS-1$
                        page.showView(id);
                    } catch (PartInitException e) {
                        AdtPlugin.log(e, "Cannot open Problems View");
                    }
                }
            }
        }
    }

    /** Returns the selected project, if there is exactly one selected project */
    static IProject getSelectedProject(ISelection selection) {
        if (selection instanceof IStructuredSelection) {
            IStructuredSelection structuredSelection = (IStructuredSelection) selection;
            // get the unique selected item.
            if (structuredSelection.size() == 1) {
                Object element = structuredSelection.getFirstElement();

                // get the project object from it.
                IProject project = null;
                if (element instanceof IProject) {
                    project = (IProject) element;
                } else if (element instanceof IAdaptable) {
                    project = (IProject) ((IAdaptable) element).getAdapter(IProject.class);
                }

                return project;
            }
        }

        return null;
    }

    private final class FinishListener extends JobChangeAdapter implements Runnable {
        private final IProject mProject;

        private FinishListener(IProject project) {
            this.mProject = project;
        }

        @Override
        public void done(IJobChangeEvent event) {
            Display display = AdtPlugin.getDisplay();
            if (display.getThread() != Thread.currentThread()) {
                display.asyncExec(this);
            } else {
                run();
            }
        }

        // ---- Implements Runnable ----

        public void run() {
            IMarker[] markers = LintEclipseContext.getMarkers(mProject);
            int warningCount = 0;
            int errorCount = 0;
            for (IMarker marker : markers) {
                int severity = marker.getAttribute(IMarker.SEVERITY, -1);
                if (severity == IMarker.SEVERITY_ERROR) {
                    errorCount++;
                } else if (severity == IMarker.SEVERITY_WARNING) {
                    warningCount++;
                }
            }
            String message = null;
            if (errorCount == 0 && warningCount == 0) {
                message = "Finished running lint, no problems found.";
            } else {
                message = String.format(
                "Finished running lint, found %1$d errors and %2$d warnings.\n" +
                "Results are shown in the Problems View.\n" +
                "Warnings can be configured in the \"Lint Error Checking\" preferences page.",
                errorCount, warningCount);
            }
            MessageDialog.openInformation(AdtPlugin.getDisplay().getActiveShell(),
                    "Finished Checking", message);
        }
    }
}
