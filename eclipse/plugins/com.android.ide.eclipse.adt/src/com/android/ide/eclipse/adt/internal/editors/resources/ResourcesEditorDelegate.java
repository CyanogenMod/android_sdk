/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.ide.eclipse.adt.internal.editors.resources;

import com.android.ide.common.resources.ResourceFolder;
import com.android.ide.eclipse.adt.AdtConstants;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.editors.XmlEditorDelegate;
import com.android.ide.eclipse.adt.internal.editors.AndroidXmlCommonEditor;
import com.android.ide.eclipse.adt.internal.editors.descriptors.ElementDescriptor;
import com.android.ide.eclipse.adt.internal.editors.resources.descriptors.ResourcesDescriptors;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiElementNode;
import com.android.ide.eclipse.adt.internal.resources.manager.ResourceManager;
import com.android.resources.ResourceFolderType;
import com.android.sdklib.xml.AndroidXPathFactory;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.FileEditorInput;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;

/**
 * Multi-page form editor for /res/values XML files.
 */
public class ResourcesEditorDelegate extends XmlEditorDelegate {

    public static class Creator implements IXmlEditorCreator {
        @Override
        @SuppressWarnings("unchecked")
        public ResourcesEditorDelegate createForFile(
                AndroidXmlCommonEditor delegator,
                IFileEditorInput input) {
            // get the IFile object and check it's the desired sub-resource folder
            IFile iFile = input.getFile();
            ResourceFolder resFolder = ResourceManager.getInstance().getResourceFolder(iFile);
            ResourceFolderType type = resFolder == null ? null : resFolder.getType();
            if (ResourceFolderType.VALUES.equals(type)) {
                return new ResourcesEditorDelegate(delegator);
            }

            return null;
        }
    }

    /**
     * Old standalone-editor ID.
     * @deprecated Use {@link AndroidXmlCommonEditor#ID} instead.
     */
    @Deprecated
    public static final String OLD_STANDALONE_EDITOR_ID = AdtConstants.EDITORS_NAMESPACE + ".resources.ResourcesEditor"; //$NON-NLS-1$

    /** Root node of the UI element hierarchy */
    private UiElementNode mUiResourcesNode;

    private final AndroidXmlCommonEditor mDelegator;


    /**
     * Creates the form editor for resources XML files.
     */
    public ResourcesEditorDelegate(AndroidXmlCommonEditor delegator) {
        super();
        mDelegator = delegator;
    }

    @Override
    public AndroidXmlCommonEditor getEditor() {
        return mDelegator;
    }

    @Override
    public void dispose() {
        // pass
    }

    /**
     * Returns the root node of the UI element hierarchy, which
     * here is the "resources" node.
     */
    @Override
    public UiElementNode getUiRootNode() {
        return mUiResourcesNode;
    }

    // ---- Base Class Overrides ----

    /**
     * Returns whether the "save as" operation is supported by this editor.
     * <p/>
     * Save-As is a valid operation for the ManifestEditor since it acts on a
     * single source file.
     *
     * @see IEditorPart
     */
    @Override
    public boolean isSaveAsAllowed() {
        return true;
    }

    /**
     * Create the various form pages.
     */
    @Override
    public void createFormPages() {
        try {
            getEditor().addPage(new ResourcesTreePage(getEditor()));
        } catch (PartInitException e) {
            AdtPlugin.log(IStatus.ERROR, "Error creating nested page"); //$NON-NLS-1$
            AdtPlugin.getDefault().getLog().log(e.getStatus());
        }
     }

    /* (non-java doc)
     * Change the tab/title name to include the project name.
     */
    @Override
    public void setInput(IEditorInput input) {
        if (input instanceof FileEditorInput) {
            FileEditorInput fileInput = (FileEditorInput) input;
            IFile file = fileInput.getFile();
            getEditor().setPartName(String.format("%1$s", file.getName()));
        }
    }

    /**
     * Processes the new XML Model, which XML root node is given.
     *
     * @param xml_doc The XML document, if available, or null if none exists.
     */
    @Override
    public void xmlModelChanged(Document xml_doc) {
        // init the ui root on demand
        initUiRootNode(false /*force*/);

        mUiResourcesNode.setXmlDocument(xml_doc);
        if (xml_doc != null) {
            ElementDescriptor resources_desc =
                    ResourcesDescriptors.getInstance().getElementDescriptor();
            try {
                XPath xpath = AndroidXPathFactory.newXPath();
                Node node = (Node) xpath.evaluate("/" + resources_desc.getXmlName(),  //$NON-NLS-1$
                        xml_doc,
                        XPathConstants.NODE);
                // Node can be null _or_ it must be the element we searched for.
                assert node == null || node.getNodeName().equals(resources_desc.getXmlName());

                // Refresh the manifest UI node and all its descendants
                mUiResourcesNode.loadFromXmlNode(node);
            } catch (XPathExpressionException e) {
                AdtPlugin.log(e, "XPath error when trying to find '%s' element in XML.", //$NON-NLS-1$
                        resources_desc.getXmlName());
            }
        }
    }

    /**
     * Creates the initial UI Root Node, including the known mandatory elements.
     * @param force if true, a new UiRootNode is recreated even if it already exists.
     */
    @Override
    public void initUiRootNode(boolean force) {
        // The manifest UI node is always created, even if there's no corresponding XML node.
        if (mUiResourcesNode == null || force) {
            ElementDescriptor resources_desc =
                    ResourcesDescriptors.getInstance().getElementDescriptor();
            mUiResourcesNode = resources_desc.createUiNode();
            mUiResourcesNode.setEditor(getEditor());

            onDescriptorsChanged();
        }
    }

    // ---- Local methods ----

    private void onDescriptorsChanged() {
        // nothing to be done, as the descriptor are static for now.
        // FIXME Update when the descriptors are not static
    }
}

