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

import com.android.annotations.NonNull;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.AdtUtils;
import com.android.ide.eclipse.adt.internal.editors.IconFactory;
import com.android.ide.eclipse.adt.internal.editors.manifest.ManifestInfo;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.wizards.newresource.BasicNewResourceWizard;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Template wizard which creates parameterized templates
 */
public class NewTemplateWizard extends Wizard implements INewWizard {
    /** Template name and location under $sdk/templates for the default activity */
    static final String BLANK_ACTIVITY = "activities/BlankActivity";           //$NON-NLS-1$
    /** Template name and location under $sdk/templates for the custom view template */
    static final String CUSTOM_VIEW = "other/CustomView";                      //$NON-NLS-1$
    private static final String PROJECT_LOGO_LARGE = "android-64";             //$NON-NLS-1$

    protected IWorkbench mWorkbench;
    protected NewTemplatePage mMainPage;
    protected UpdateToolsPage mUpdatePage;
    protected InstallDependencyPage mDependencyPage;
    protected NewTemplateWizardState mValues;
    private final String mTemplateName;

    NewTemplateWizard(String templateName) {
        mTemplateName = templateName;
    }

    @Override
    public void init(IWorkbench workbench, IStructuredSelection selection) {
        mWorkbench = workbench;

        setHelpAvailable(false);
        setImageDescriptor();

        mValues = new NewTemplateWizardState();

        File template = TemplateHandler.getTemplateLocation(mTemplateName);
        mValues.setTemplateLocation(template);
        hideBuiltinParameters();

        if (!UpdateToolsPage.isUpToDate()) {
            mUpdatePage = new UpdateToolsPage();
        }

        List<IProject> projects = AdtUtils.getSelectedProjects(selection);
        if (projects.size() == 1) {
            mValues.project = projects.get(0);
        }

        mMainPage = new NewTemplatePage(mValues, true);
    }

    /**
     * Hide those parameters that the template requires but that we don't want
     * to ask the users about, since we can derive it from the target project
     * the template is written into.
     */
    protected void hideBuiltinParameters() {
        Set<String> hidden = mValues.hidden;
        hidden.add(ATTR_PACKAGE_NAME);
        hidden.add(ATTR_MIN_API);
        hidden.add(ATTR_MIN_API_LEVEL);
        hidden.add(ATTR_TARGET_API);
    }

    @Override
    public void addPages() {
        if (mUpdatePage != null) {
            addPage(mUpdatePage);
        }

        addPage(mMainPage);
    }

    @Override
    public IWizardPage getNextPage(IWizardPage page) {
        if (page == mMainPage) {
            TemplateMetadata template = mValues.getTemplateHandler().getTemplate();
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
    public IWizardPage getStartingPage() {
        if (mUpdatePage != null && mUpdatePage.isPageComplete()) {
            return mMainPage;
        }
        return super.getStartingPage();
    }

    @Override
    public boolean performFinish() {
        try {
            Map<String, Object> parameters = mValues.parameters;
            IProject project = mValues.project;

            ManifestInfo manifest = ManifestInfo.get(project);
            parameters.put(ATTR_PACKAGE_NAME, manifest.getPackage());
            parameters.put(ATTR_MIN_API, manifest.getMinSdkVersion());
            parameters.put(ATTR_MIN_API_LEVEL, manifest.getMinSdkName());
            parameters.put(ATTR_TARGET_API, manifest.getTargetSdkVersion());

            File outputPath = AdtUtils.getAbsolutePath(project).toFile();
            TemplateHandler handler = mValues.getTemplateHandler();
            handler.render(outputPath, parameters);

            try {
                project.refreshLocal(DEPTH_INFINITE, new NullProgressMonitor());
            } catch (CoreException e) {
                AdtPlugin.log(e, null);
            }

            List<String> filesToOpen = handler.getFilesToOpen();
            NewTemplateWizard.openFiles(project, filesToOpen, mWorkbench);

            return true;
        } catch (Exception ioe) {
            AdtPlugin.log(ioe, null);
            return false;
        }
    }

    /**
     * Returns an image descriptor for the wizard logo.
     */
    private void setImageDescriptor() {
        ImageDescriptor desc = IconFactory.getInstance().getImageDescriptor(PROJECT_LOGO_LARGE);
        setDefaultPageImageDescriptor(desc);
    }

    /**
     * Opens the given set of files (as relative paths within a given project
     *
     * @param project the project containing the paths
     * @param relativePaths the paths to files to open
     * @param mWorkbench the workbench to open the files in
     */
    public static void openFiles(
            @NonNull final IProject project,
            @NonNull final List<String> relativePaths,
            @NonNull final IWorkbench mWorkbench) {
        if (!relativePaths.isEmpty()) {
            // This has to be delayed in order for focus handling to work correctly
            AdtPlugin.getDisplay().asyncExec(new Runnable() {
                @Override
                public void run() {
                    for (String path : relativePaths) {
                        IResource resource = project.findMember(path);
                        if (resource != null) {
                            if (resource instanceof IFile) {
                                try {
                                    AdtPlugin.openFile((IFile) resource, null, false);
                                } catch (PartInitException e) {
                                    AdtPlugin.log(e, "Failed to open %1$s", //$NON-NLS-1$
                                            resource.getFullPath().toString());
                                }
                            }
                            boolean isLast = relativePaths.size() == 1 ||
                                    path.equals(relativePaths.get(relativePaths.size() - 1));
                            if (isLast) {
                                BasicNewResourceWizard.selectAndReveal(resource,
                                        mWorkbench.getActiveWorkbenchWindow());
                            }
                        }
                    }
                }
            });
        }
    }

    /**
     * Specific New Custom View wizard
     */
    public static class NewCustomViewWizard extends NewTemplateWizard {
        /** Creates a new {@link NewCustomViewWizard} */
        public NewCustomViewWizard() {
            super(CUSTOM_VIEW);
        }

        @Override
        public void init(IWorkbench workbench, IStructuredSelection selection) {
            super.init(workbench, selection);
            setWindowTitle("New Custom View");
            super.mMainPage.setTitle("New Custom View");
            super.mMainPage.setDescription("Creates a new custom view");
        }
    }
}
