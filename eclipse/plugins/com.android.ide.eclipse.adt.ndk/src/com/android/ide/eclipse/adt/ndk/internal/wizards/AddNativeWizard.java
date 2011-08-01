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

package com.android.ide.eclipse.adt.ndk.internal.wizards;

import com.android.ide.eclipse.adt.ndk.internal.Activator;
import com.android.ide.eclipse.adt.ndk.internal.NdkManager;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.make.core.MakeCorePlugin;
import org.eclipse.cdt.ui.CUIPlugin;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.WorkbenchException;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

public class AddNativeWizard extends Wizard {

    private final IProject mProject;
    private final IWorkbenchWindow mWindow;

    private AddNativeWizardPage mAddNativeWizardPage;
    private Map<String, String> mTemplateArgs = new HashMap<String, String>();

    public AddNativeWizard(IProject project, IWorkbenchWindow window) {
        mProject = project;
        mWindow = window;
        mTemplateArgs.put(NdkManager.LIBRARY_NAME, project.getName());
    }

    @Override
    public void addPages() {
        mAddNativeWizardPage = new AddNativeWizardPage(mTemplateArgs);
        addPage(mAddNativeWizardPage);
    }

    @Override
    public boolean performFinish() {
        // Switch to C/C++ Perspective
        try {
            mWindow.getWorkbench().showPerspective(CUIPlugin.ID_CPERSPECTIVE, mWindow);
        } catch (WorkbenchException e1) {
            Activator.log(e1);
        }

        mAddNativeWizardPage.updateArgs(mTemplateArgs);

        IRunnableWithProgress op = new IRunnableWithProgress() {
            public void run(IProgressMonitor monitor) throws InvocationTargetException,
                    InterruptedException {
                IWorkspaceRunnable op1 = new IWorkspaceRunnable() {
                    public void run(IProgressMonitor monitor1) throws CoreException {
                        // Convert to CDT project
                        CCorePlugin.getDefault().convertProjectToCC(mProject, monitor1,
                                MakeCorePlugin.MAKE_PROJECT_ID);
                        // Set up build information
                        new NdkWizardHandler().convertProject(mProject, monitor1);
                        // Run the template
                        NdkManager.addNativeSupport(mProject, mTemplateArgs, monitor1);
                    }
                };
                // TODO run from a job
                IWorkspace workspace = ResourcesPlugin.getWorkspace();
                try {
                    workspace.run(op1, workspace.getRoot(), 0, new NullProgressMonitor());
                } catch (CoreException e) {
                    throw new InvocationTargetException(e);
                }
            }
        };
        try {
            getContainer().run(false, true, op);
            return true;
        } catch (InterruptedException e) {
            Activator.log(e);
            return false;
        } catch (InvocationTargetException e) {
            Activator.log(e);
            return false;
        }
    }

}
