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
package com.android.ide.eclipse.adt.internal.assetstudio;

import com.android.ide.eclipse.adt.AdtConstants;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.editors.AndroidXmlEditor;
import com.android.ide.eclipse.adt.internal.project.BaseProjectHelper;
import com.android.ide.eclipse.adt.internal.project.BaseProjectHelper.IProjectFilter;
import com.android.ide.eclipse.adt.internal.wizards.newxmlfile.NewXmlFileWizard;
import com.android.util.Pair;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.swt.SWT;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.FileEditorInput;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * Wizard for creating a new icon set
 */
public class CreateAssetSetWizard extends Wizard implements INewWizard {
    private ChooseAssetTypePage mChooseAssetPage;
    private ConfigureAssetSetPage mConfigureAssetPage;
    private IProject mInitialProject;
    private List<IResource> mCreatedFiles;

    /** Creates a new asset set wizard */
    public CreateAssetSetWizard() {
        setWindowTitle("Create Asset Set");
    }

    @Override
    public void addPages() {
        mChooseAssetPage = new ChooseAssetTypePage();
        mChooseAssetPage.setProject(mInitialProject);
        mConfigureAssetPage = new ConfigureAssetSetPage();

        addPage(mChooseAssetPage);
        addPage(mConfigureAssetPage);
    }

    String getBaseName() {
        return mChooseAssetPage.getOutputName();
    }

    @Override
    public boolean performFinish() {
        Map<String, Map<String, BufferedImage>> categories =
                mConfigureAssetPage.generateImages(false);

        IProject project = getProject();

        // Write out the images into the project
        boolean yesToAll = false;
        mCreatedFiles = new ArrayList<IResource>();

        for (Map<String, BufferedImage> previews : categories.values()) {
            for (Map.Entry<String, BufferedImage> entry : previews.entrySet()) {
                String relativePath = entry.getKey();
                IPath dest = new Path(relativePath);
                IFile file = project.getFile(dest);
                if (file.exists()) {
                    // Warn that the file already exists and ask the user what to do
                    if (!yesToAll) {
                        MessageDialog dialog = new MessageDialog(null, "File Already Exists", null,
                                String.format(
                                        "%1$s already exists.\nWould you like to replace it?",
                                        file.getProjectRelativePath().toOSString()),
                                MessageDialog.QUESTION, new String[] {
                                        // Yes will be moved to the end because it's the default
                                        "Yes", "No", "Cancel", "Yes to All"
                                }, 0);
                        int result = dialog.open();
                        switch (result) {
                            case 0:
                                // Yes
                                break;
                            case 3:
                                // Yes to all
                                yesToAll = true;
                                break;
                            case 1:
                                // No
                                continue;
                            case SWT.DEFAULT:
                            case 2:
                                // Cancel
                                return false;
                        }
                    }

                    try {
                        file.delete(true, new NullProgressMonitor());
                    } catch (CoreException e) {
                        AdtPlugin.log(e, null);
                    }
                }

                NewXmlFileWizard.createWsParentDirectory(file.getParent());
                BufferedImage image = entry.getValue();

                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                try {
                    ImageIO.write(image, "PNG", stream); //$NON-NLS-1$
                    byte[] bytes = stream.toByteArray();
                    InputStream is = new ByteArrayInputStream(bytes);
                    file.create(is, true /*force*/, null /*progress*/);
                    mCreatedFiles.add(file);
                } catch (IOException e) {
                    AdtPlugin.log(e, null);
                } catch (CoreException e) {
                    AdtPlugin.log(e, null);
                }

                try {
                    file.getParent().refreshLocal(1, new NullProgressMonitor());
                } catch (CoreException e) {
                    AdtPlugin.log(e, null);
                }
            }
        }

        // Finally select the files themselves
        selectFiles(project, mCreatedFiles);

        return true;
    }

    private void selectFiles(IProject project, List<? extends IResource> createdFiles) {
        // Attempt to select the newly created files in the Package Explorer
        IWorkbench workbench = AdtPlugin.getDefault().getWorkbench();
        IWorkbenchPage page = workbench.getActiveWorkbenchWindow().getActivePage();
        IViewPart viewPart = page.findView(JavaUI.ID_PACKAGES);
        if (viewPart != null) {
            IWorkbenchPartSite site = viewPart.getSite();
            IJavaProject javaProject = null;
            try {
                javaProject = BaseProjectHelper.getJavaProject(project);
            } catch (CoreException e) {
                AdtPlugin.log(e, null);
            }
            final ISelectionProvider provider = site.getSelectionProvider();
            if (provider != null) {
                List<TreePath> pathList = new ArrayList<TreePath>();
                for (IResource file : createdFiles) {
                    // Create a TreePath for the given file,
                    // which should be the JavaProject, followed by the folders down to
                    // the final file.
                    List<Object> segments = new ArrayList<Object>();
                    segments.add(file);
                    IContainer folder = file.getParent();
                    if (folder != null && !(folder instanceof IProject)) {
                        segments.add(folder);
                        // res folder
                        folder = folder.getParent();
                        if (folder != null && !(folder instanceof IProject)) {
                            segments.add(folder);
                        }
                    }
                    // project
                    segments.add(javaProject);

                    Collections.reverse(segments);
                    TreePath path = new TreePath(segments.toArray());
                    pathList.add(path);

                    // IDEA: Maybe normalize the files backwards (IFile objects aren't unique)
                    // by maybe using the package explorer icons instead
                }

                TreePath[] paths = pathList.toArray(new TreePath[pathList.size()]);
                final TreeSelection selection = new TreeSelection(paths);

                provider.setSelection(selection);

                // Workaround: The above doesn't always work; it will frequently select
                // some siblings of the real files. I've tried a number of workarounds:
                // normalizing the IFile objects by looking up the canonical ones via
                // their relative paths from the project; deferring execution with
                // Display.asyncRun; first calling select on the parents, etc.
                // However, it turns out a simple workaround works best: Calling this
                // method TWICE. The first call seems to expand all the necessary parents,
                // and the second call ensures that the correct children are selected!
                provider.setSelection(selection);

                viewPart.setFocus();
            }
        }
    }

    /**
     * Returns the project to be used by the wizard (which may differ from the
     * project initialized by {@link #init(IWorkbench, IStructuredSelection)} or
     * set by {@link #setProject(IProject)} if the user changes the project
     * in the first page of the wizard.
     */
    IProject getProject() {
        if (mChooseAssetPage != null) {
            return mChooseAssetPage.getProject();
        } else {
            return mInitialProject;
        }
    }

    /** Sets the initial project to be used by the wizard */
    void setProject(IProject project) {
        mInitialProject = project;
    }

    /** Returns the {@link AssetType} to create */
    AssetType getAssetType() {
        return mChooseAssetPage.getAssetType();
    }

    public void init(IWorkbench workbench, IStructuredSelection selection) {
        setHelpAvailable(false);

        mInitialProject = guessProject(selection);
        if (mChooseAssetPage != null) {
            mChooseAssetPage.setProject(mInitialProject);
        }
    }

    private IProject guessProject(IStructuredSelection selection) {
        if (selection == null) {
            return null;
        }

        for (Object element : selection.toList()) {
            if (element instanceof IAdaptable) {
                IResource res = (IResource) ((IAdaptable) element).getAdapter(IResource.class);
                IProject project = res != null ? res.getProject() : null;

                // Is this an Android project?
                try {
                    if (project == null || !project.hasNature(AdtConstants.NATURE_DEFAULT)) {
                        continue;
                    }
                } catch (CoreException e) {
                    // checking the nature failed, ignore this resource
                    continue;
                }

                return project;
            } else if (element instanceof Pair<?, ?>) {
                // Pair of Project/String
                @SuppressWarnings("unchecked")
                Pair<IProject, String> pair = (Pair<IProject, String>) element;
                return pair.getFirst();
            }
        }

        // Try to figure out the project from the active editor
        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (window != null) {
            IWorkbenchPage page = window.getActivePage();
            if (page != null) {
                IEditorPart activeEditor = page.getActiveEditor();
                if (activeEditor instanceof AndroidXmlEditor) {
                    Object input = ((AndroidXmlEditor) activeEditor).getEditorInput();
                    if (input instanceof FileEditorInput) {
                        FileEditorInput fileInput = (FileEditorInput) input;
                        return fileInput.getFile().getProject();
                    }
                }
            }
        }

        IJavaProject[] projects = BaseProjectHelper.getAndroidProjects(new IProjectFilter() {
            public boolean accept(IProject project) {
                return project.isAccessible();
            }
        });

        if (projects != null && projects.length == 1) {
            return projects[0].getProject();
        }

        return null;
    }

    /**
     * Returns the list of files created by the wizard. This method will return
     * null if {@link #performFinish()} has not yet been called.
     *
     * @return a list of files created by the wizard, or null
     */
    List<IResource> getCreatedFiles() {
        return mCreatedFiles;
    }
}
