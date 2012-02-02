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

import static com.android.ide.eclipse.adt.AdtConstants.DOT_XML;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.AdtUtils;
import com.android.ide.eclipse.adt.internal.editors.IconFactory;
import com.android.ide.eclipse.adt.internal.project.BaseProjectHelper;
import com.android.tools.lint.detector.api.LintUtils;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.ui.JavaElementLabelProvider;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuCreator;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowPulldownDelegate;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Action which runs Lint on the currently projects (and also provides a
 * pulldown menu in the toolbar for selecting specifically which projects to
 * check)
 */
public class RunLintAction implements IObjectActionDelegate, IMenuCreator,
        IWorkbenchWindowPulldownDelegate {

    private ISelection mSelection;
    private Menu mMenu;

    @Override
    public void selectionChanged(IAction action, ISelection selection) {
        mSelection = selection;
    }

    @Override
    public void run(IAction action) {
        List<IProject> projects = getProjects(mSelection, true /* warn */);

        if (!projects.isEmpty()) {
            EclipseLintRunner.startLint(projects, null, false /*fatalOnly*/, true /*show*/);
        } else {
            MessageDialog.openWarning(AdtPlugin.getDisplay().getActiveShell(), "Lint",
                    "Could not run Lint: Select a project first.");
        }
    }

    /** Returns the Android project(s) to apply a lint run to. */
    static List<IProject> getProjects(ISelection selection, boolean warn) {
        List<IProject> projects = new ArrayList<IProject>();

        if (selection instanceof IStructuredSelection) {
            IStructuredSelection structuredSelection = (IStructuredSelection) selection;
            // get the unique selected item.
            Iterator<?> iterator = structuredSelection.iterator();
            while (iterator.hasNext()) {
                Object element = iterator.next();

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

                if (project != null && !projects.contains(project)) {
                    projects.add(project);
                }
            }
        }

        if (projects.isEmpty()) {
            // Try to look at the active editor instead
            IFile file = AdtUtils.getActiveFile();
            if (file != null) {
                projects.add(file.getProject());
            }
        }

        if (projects.isEmpty()) {
            // If we didn't find a default project based on the selection, check how many
            // open Android projects we can find in the current workspace. If there's only
            // one, we'll just select it by default.
            IJavaProject[] open = AdtUtils.getOpenAndroidProjects();
            for (IJavaProject project : open) {
                projects.add(project.getProject());
            }
        } else {
            // Make sure all the projects are Android projects
            for (IProject project : projects) {
                if (!BaseProjectHelper.isAndroidProject(project)) {
                    if (warn) {
                        MessageDialog.openWarning(AdtPlugin.getDisplay().getActiveShell(),
                                "Lint", "Select Android projects.");
                    }
                    return Collections.emptyList();
                }
            }
        }

        if (projects.isEmpty() && warn) {
            MessageDialog.openWarning(AdtPlugin.getDisplay().getActiveShell(), "Lint",
                    "Could not run Lint: Select a project first.");
        }

        return projects;
    }

    @Override
    public void setActivePart(IAction action, IWorkbenchPart targetPart) {
    }

    @Override
    public void dispose() {
        if (mMenu != null) {
            mMenu.dispose();
        }
    }

    @Override
    public void init(IWorkbenchWindow window) {
    }

    // ---- IMenuCreator ----

    @Override
    public Menu getMenu(Control parent) {
        mMenu = new Menu(parent);

        IconFactory iconFactory = IconFactory.getInstance();
        ImageDescriptor allIcon = iconFactory.getImageDescriptor("lintrun"); //$NON-NLS-1$
        LintMenuAction allAction = new LintMenuAction("Check All Projects", allIcon, false, null);

        addAction(allAction);
        addSeparator();
        IJavaProject[] projects = AdtUtils.getOpenAndroidProjects();
        ILabelProvider provider = new JavaElementLabelProvider(
                JavaElementLabelProvider.SHOW_DEFAULT);
        for (IJavaProject project : projects) {
            IProject p = project.getProject();
            ImageDescriptor icon = ImageDescriptor.createFromImage(provider.getImage(p));
            String label = String.format("Check %1$s", p.getName());
            LintMenuAction projectAction = new LintMenuAction(label, icon, false, p);
            addAction(projectAction);
        }

        ITextEditor textEditor = AdtUtils.getActiveTextEditor();
        if (textEditor != null) {
            IFile file = AdtUtils.getActiveFile();
            // Currently only supported for XML files
            if (file != null && LintUtils.endsWith(file.getName(), DOT_XML)) {
                ImageDescriptor icon = ImageDescriptor.createFromImage(provider.getImage(file));
                IAction fileAction = new LintMenuAction("Check Current File", icon, false, file);

                addSeparator();
                addAction(fileAction);
            }
        }

        ISharedImages images = PlatformUI.getWorkbench().getSharedImages();
        ImageDescriptor clear = images.getImageDescriptor(ISharedImages.IMG_ELCL_REMOVEALL);
        LintMenuAction clearAction = new LintMenuAction("Clear Lint Markers", clear, true, null);
        addSeparator();
        addAction(clearAction);

        return mMenu;
    }

    private void addAction(IAction action) {
        ActionContributionItem item = new ActionContributionItem(action);
        item.fill(mMenu, -1);
    }

    private void addSeparator() {
        new Separator().fill(mMenu, -1);
    }

    @Override
    public Menu getMenu(Menu parent) {
        return null;
    }

    /**
     * Actions in the pulldown context menu: run lint or clear lint markers on
     * the given resource
     */
    private static class LintMenuAction extends Action {
        private final boolean mClear;
        private final IResource mResource;

        /**
         * Creates a new context menu action
         *
         * @param text the label
         * @param descriptor the icon
         * @param clear if true, clear lint markers otherwise check the resource
         * @param resource the resource to check or clear markers for, where
         *            null means all projects
         */
        private LintMenuAction(String text, ImageDescriptor descriptor, boolean clear,
                IResource resource) {
            super(text, descriptor);
            mClear = clear;
            mResource = resource;
        }

        @Override
        public void run() {
            List<IResource> resources = new ArrayList<IResource>();
            if (mResource == null) {
                // All projects
                IJavaProject[] open = AdtUtils.getOpenAndroidProjects();
                for (IJavaProject project : open) {
                    resources.add(project.getProject());
                }
            } else {
                resources.add(mResource);
            }
            EclipseLintRunner.cancelCurrentJobs(false);
            if (mClear) {
                EclipseLintClient.clearMarkers(resources);
            } else {
                EclipseLintRunner.startLint(resources, null, false /*fatalOnly*/, true /*show*/);
            }
        }
    }
}
