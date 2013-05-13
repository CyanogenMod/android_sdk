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

import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.IExportWizard;
import org.eclipse.ui.IWorkbench;

public class GradleExportWizard extends Wizard implements IExportWizard {
    private GradleExportPage mMainPage;

    /**
     * Creates buildfile.
     */
    @Override
    public boolean performFinish() {
        return mMainPage.generateBuildfiles();
    }

    @Override
    public void addPages() {
        mMainPage = new GradleExportPage();
        addPage(mMainPage);
    }

    @Override
    public void init(IWorkbench workbench, IStructuredSelection selection) {
        setWindowTitle(ExportMessages.WindowTitle);
        setNeedsProgressMonitor(true);
    }
}
