/*
 * Copyright (C) 2008 The Android Open Source Project
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



package com.android.ide.eclipse.adt.internal.wizards.newxmlfile;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.editors.IconFactory;
import com.android.ide.eclipse.adt.internal.wizards.newxmlfile.NewXmlFileCreationPage.TypeInfo;
import com.android.resources.ResourceFolderType;
import com.android.util.Pair;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PartInitException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

/**
 * The "New Android XML File Wizard" provides the ability to create skeleton XML
 * resources files for Android projects.
 * <p/>
 * The wizard has one page, {@link NewXmlFileCreationPage}, used to select the project,
 * the resource folder, resource type and file name. It then creates the XML file.
 */
public class NewXmlFileWizard extends Wizard implements INewWizard {
    public static final String XML_HEADER_LINE = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"; //$NON-NLS-1$

    private static final String PROJECT_LOGO_LARGE = "android-64"; //$NON-NLS-1$

    protected static final String MAIN_PAGE_NAME = "newAndroidXmlFilePage"; //$NON-NLS-1$

    private NewXmlFileCreationPage mMainPage;

    public void init(IWorkbench workbench, IStructuredSelection selection) {
        setHelpAvailable(false); // TODO have help
        setWindowTitle("New Android XML File");
        setImageDescriptor();

        mMainPage = createMainPage();
        mMainPage.setTitle("New Android XML File");
        mMainPage.setDescription("Creates a new Android XML file.");
        mMainPage.setInitialSelection(selection);
    }

    /**
     * Creates the wizard page.
     * <p/>
     * Please do NOT override this method.
     * <p/>
     * This is protected so that it can be overridden by unit tests.
     * However the contract of this class is private and NO ATTEMPT will be made
     * to maintain compatibility between different versions of the plugin.
     */
    protected NewXmlFileCreationPage createMainPage() {
        return new NewXmlFileCreationPage(MAIN_PAGE_NAME);
    }

    // -- Methods inherited from org.eclipse.jface.wizard.Wizard --
    //
    // The Wizard class implements most defaults and boilerplate code needed by
    // IWizard

    /**
     * Adds pages to this wizard.
     */
    @Override
    public void addPages() {
        addPage(mMainPage);
    }

    /**
     * Performs any actions appropriate in response to the user having pressed
     * the Finish button, or refuse if finishing now is not permitted: here, it
     * actually creates the workspace project and then switch to the Java
     * perspective.
     *
     * @return True
     */
    @Override
    public boolean performFinish() {
        Pair<IFile, IRegion> created = createXmlFile();
        if (created == null) {
            return false;
        } else {
            IFile file = created.getFirst();

            // Open the file
            try {
                AdtPlugin.openFile(file, null, false /* showEditorTab */);
            } catch (PartInitException e) {
                AdtPlugin.log(e, "Failed to create %1$s: missing type",  //$NON-NLS-1$
                        file.getFullPath().toString());
            }
            return true;
        }
    }

    // -- Custom Methods --

    private Pair<IFile, IRegion> createXmlFile() {
        IFile file = mMainPage.getDestinationFile();
        TypeInfo type = mMainPage.getSelectedType();
        if (type == null) {
            // this is not expected to happen
            String name = file.getFullPath().toString();
            AdtPlugin.log(IStatus.ERROR, "Failed to create %1$s: missing type", name);  //$NON-NLS-1$
            return null;
        }
        String xmlns = type.getXmlns();
        String root = mMainPage.getRootElement();
        if (root == null) {
            // this is not expected to happen
            AdtPlugin.log(IStatus.ERROR, "Failed to create %1$s: missing root element", //$NON-NLS-1$
                    file.toString());
            return null;
        }

        String attrs = type.getDefaultAttrs(mMainPage.getProject(), root);

        String child = type.getChild(mMainPage.getProject(), root);
        return createXmlFile(file, xmlns, root, attrs, child);
    }

    /** Creates a new file using the given root element, namespace and root attributes */
    private static Pair<IFile, IRegion> createXmlFile(IFile file, String xmlns,
            String root, String rootAttributes, String child) {
        String name = file.getFullPath().toString();
        boolean need_delete = false;

        if (file.exists()) {
            if (!AdtPlugin.displayPrompt("New Android XML File",
                String.format("Do you want to overwrite the file %1$s ?", name))) {
                // abort if user selects cancel.
                return null;
            }
            need_delete = true;
        } else {
            createWsParentDirectory(file.getParent());
        }

        StringBuilder sb = new StringBuilder(XML_HEADER_LINE);

        sb.append('<').append(root);
        if (xmlns != null) {
            sb.append('\n').append("  xmlns:android=\"").append(xmlns).append("\"");  //$NON-NLS-1$ //$NON-NLS-2$
        }

        if (rootAttributes != null) {
            sb.append("\n  ");                       //$NON-NLS-1$
            sb.append(rootAttributes.replace("\n", "\n  "));  //$NON-NLS-1$ //$NON-NLS-2$
        }

        sb.append(">\n");                            //$NON-NLS-1$

        if (child != null) {
            sb.append(child);
        }

        // The insertion line
        sb.append("    ");                           //$NON-NLS-1$
        int caretOffset = sb.length();
        sb.append("\n");                             //$NON-NLS-1$

        sb.append("</").append(root).append(">\n");  //$NON-NLS-1$ //$NON-NLS-2$

        String result = sb.toString();
        String error = null;
        try {
            byte[] buf = result.getBytes("UTF8");    //$NON-NLS-1$
            InputStream stream = new ByteArrayInputStream(buf);
            if (need_delete) {
                file.delete(IResource.KEEP_HISTORY | IResource.FORCE, null /*monitor*/);
            }
            file.create(stream, true /*force*/, null /*progress*/);
            IRegion region = new Region(caretOffset, 0);
            return Pair.of(file, region);
        } catch (UnsupportedEncodingException e) {
            error = e.getMessage();
        } catch (CoreException e) {
            error = e.getMessage();
        }

        error = String.format("Failed to generate %1$s: %2$s", name, error);
        AdtPlugin.displayError("New Android XML File", error);
        return null;
    }

    /**
     * Returns true if the New XML Wizard can create new files of the given
     * {@link ResourceFolderType}
     *
     * @param folderType the folder type to create a file for
     * @return true if this wizard can create new files for the given folder type
     */
    public static boolean canCreateXmlFile(ResourceFolderType folderType) {
        TypeInfo typeInfo = NewXmlFileCreationPage.getTypeInfo(folderType);
        return typeInfo != null && (typeInfo.getDefaultRoot(null /*project*/) != null ||
                typeInfo.getRootSeed() instanceof String);
    }

    /**
     * Creates a new XML file using the template according to the given folder type
     *
     * @param project the project to create the file in
     * @param file the file to be created
     * @param folderType the type of folder to look up a template for
     * @return the created file
     */
    public static Pair<IFile, IRegion> createXmlFile(IProject project, IFile file,
            ResourceFolderType folderType) {
        TypeInfo type = NewXmlFileCreationPage.getTypeInfo(folderType);
        String xmlns = type.getXmlns();
        String root = type.getDefaultRoot(project);
        if (root == null) {
            root = type.getRootSeed().toString();
        }
        String attrs = type.getDefaultAttrs(project, root);
        return createXmlFile(file, xmlns, root, attrs, null);
    }

    private static boolean createWsParentDirectory(IContainer wsPath) {
        if (wsPath.getType() == IResource.FOLDER) {
            if (wsPath.exists()) {
                return true;
            }

            IFolder folder = (IFolder) wsPath;
            try {
                if (createWsParentDirectory(wsPath.getParent())) {
                    folder.create(true /* force */, true /* local */, null /* monitor */);
                    return true;
                }
            } catch (CoreException e) {
                e.printStackTrace();
            }
        }

        return false;
    }

    /**
     * Returns an image descriptor for the wizard logo.
     */
    private void setImageDescriptor() {
        ImageDescriptor desc = IconFactory.getInstance().getImageDescriptor(PROJECT_LOGO_LARGE);
        setDefaultPageImageDescriptor(desc);
    }

}
