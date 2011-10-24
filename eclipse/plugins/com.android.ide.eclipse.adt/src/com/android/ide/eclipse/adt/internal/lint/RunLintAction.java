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

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IActionDelegate;

/** Action which runs Lint on the current project */
public class RunLintAction implements IActionDelegate {

     private ISelection mSelection;

    public void selectionChanged(IAction action, ISelection selection) {
        mSelection = selection;
    }

    public void run(IAction action) {
        final IProject project = RunLintAction.getSelectedProject(mSelection);
        if (project != null) {
            LintRunner.startLint(project, null);

            // Show lint view where the results are listed
            LintViewPart.show(project);
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
}
