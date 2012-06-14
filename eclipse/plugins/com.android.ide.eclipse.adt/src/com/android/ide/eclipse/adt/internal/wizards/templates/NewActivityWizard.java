/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.ide.eclipse.adt.internal.wizards.templates;

import static com.android.ide.eclipse.adt.internal.wizards.templates.NewProjectWizard.ATTR_MIN_API;
import static com.android.ide.eclipse.adt.internal.wizards.templates.NewProjectWizard.ATTR_MIN_API_LEVEL;
import static com.android.ide.eclipse.adt.internal.wizards.templates.NewProjectWizard.ATTR_PACKAGE_NAME;
import static com.android.ide.eclipse.adt.internal.wizards.templates.NewProjectWizard.ATTR_TARGET_API;
import static org.eclipse.core.resources.IResource.DEPTH_INFINITE;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.AdtUtils;
import com.android.ide.eclipse.adt.internal.editors.IconFactory;
import com.android.ide.eclipse.adt.internal.editors.manifest.ManifestInfo;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Wizard for creating new activities. This is a hybrid between a New Project
 * Wizard and a New Template Wizard: it has the "Activity selector" page from
 * the New Project Wizard, which is used to dynamically select a wizard for the
 * second page, but beyond that it runs the normal template wizard when it comes
 * time to create the template.
 */
public class NewActivityWizard extends Wizard implements INewWizard {
    private static final String PROJECT_LOGO_LARGE = "android-64"; //$NON-NLS-1$

    private IWorkbench mWorkbench;
    private UpdateToolsPage mUpdatePage;
    private NewTemplatePage mTemplatePage;
    private ActivityPage mActivityPage;
    private NewProjectWizardState mValues;
    protected InstallDependencyPage mDependencyPage;
    private NewTemplateWizardState mActivityValues;

    /** Creates a new {@link NewActivityWizard} */
    public NewActivityWizard() {
    }

    @Override
    public void init(IWorkbench workbench, IStructuredSelection selection) {
        mWorkbench = workbench;

        setWindowTitle("New Activity");
        setHelpAvailable(false);
        ImageDescriptor desc = IconFactory.getInstance().getImageDescriptor(PROJECT_LOGO_LARGE);
        setDefaultPageImageDescriptor(desc);

        if (!UpdateToolsPage.isUpToDate()) {
            mUpdatePage = new UpdateToolsPage();
        }

        mValues = new NewProjectWizardState();
        mActivityPage = new ActivityPage(mValues);

        mActivityValues = mValues.activityValues;
        List<IProject> projects = AdtUtils.getSelectedProjects(selection);
        if (projects.size() == 1) {
            mActivityValues.project = projects.get(0);
        }
    }

    @Override
    public void addPages() {
        if (mUpdatePage != null) {
            addPage(mUpdatePage);
        }

        addPage(mActivityPage);
    }

    @Override
    public IWizardPage getStartingPage() {
        if (mUpdatePage != null && mUpdatePage.isPageComplete()) {
            return mActivityPage;
        }
        return super.getStartingPage();
    }

    @Override
    public IWizardPage getNextPage(IWizardPage page) {
        if (page == mActivityPage) {
            if (mTemplatePage == null) {
                Set<String> hidden = mActivityValues.hidden;
                hidden.add(ATTR_PACKAGE_NAME);
                hidden.add(ATTR_MIN_API);
                hidden.add(ATTR_MIN_API_LEVEL);
                hidden.add(ATTR_TARGET_API);

                mTemplatePage = new NewTemplatePage(mActivityValues, true);
                addPage(mTemplatePage);
            }
            return mTemplatePage;
        } else if (page == mTemplatePage) {
            TemplateMetadata template = mActivityValues.getTemplateHandler().getTemplate();
            if (template != null) {
                if (InstallDependencyPage.isInstalled(template.getDependencies())) {
                    return null;
                } else {
                    if (mDependencyPage == null) {
                        mDependencyPage = new InstallDependencyPage();
                        addPage(mDependencyPage);
                    }
                    mDependencyPage.setTemplate(template);
                    return mDependencyPage;
                }
            }
        }

        return super.getNextPage(page);
    }

    @Override
    public boolean canFinish() {
        // Deal with lazy creation of some pages: these may not be in the page-list yet
        // since they are constructed lazily, so consider that option here.
        if (mTemplatePage == null || !mTemplatePage.isPageComplete()) {
            return false;
        }

        return super.canFinish();
    }

    @Override
    public boolean performFinish() {
        try {
            Shell shell = getShell();
            if (shell != null) {
                shell.setVisible(false);
            }
            IProject project = mActivityValues.project;
            File outputPath = AdtUtils.getAbsolutePath(project).toFile();
            assert mValues.createActivity;
            NewTemplateWizardState activityValues = mValues.activityValues;
            Map<String, Object> parameters = activityValues.parameters;
            ManifestInfo manifest = ManifestInfo.get(project);
            parameters.put(ATTR_PACKAGE_NAME, manifest.getPackage());
            parameters.put(ATTR_MIN_API, manifest.getMinSdkVersion());
            parameters.put(ATTR_MIN_API_LEVEL, manifest.getMinSdkName());
            parameters.put(ATTR_TARGET_API, manifest.getTargetSdkVersion());
            TemplateHandler activityTemplate = activityValues.getTemplateHandler();
            activityTemplate.setBackupMergedFiles(false);
            activityTemplate.render(outputPath, parameters);
            List<String> filesToOpen = activityTemplate.getFilesToOpen();

            try {
                project.refreshLocal(DEPTH_INFINITE, new NullProgressMonitor());
            } catch (CoreException e) {
                AdtPlugin.log(e, null);
            }

            // Open the primary file/files
            NewTemplateWizard.openFiles(project, filesToOpen, mWorkbench);

            return true;
        } catch (Exception ioe) {
            AdtPlugin.log(ioe, null);
            return false;
        }
    }
}
