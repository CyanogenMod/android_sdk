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

package com.android.ide.eclipse.adt.internal.editors.xml;

import com.android.ide.eclipse.adt.AdtConstants;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.editors.AndroidXmlCommonEditor;
import com.android.ide.eclipse.adt.internal.editors.XmlEditorDelegate;
import com.android.ide.eclipse.adt.internal.editors.descriptors.DocumentDescriptor;
import com.android.ide.eclipse.adt.internal.sdk.AndroidTargetData;
import com.android.resources.ResourceFolderType;

import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.PartInitException;
import org.w3c.dom.Document;

/**
 * Multi-page form editor for /res/xml XML files.
 */
public class OtherXmlEditorDelegate extends XmlEditorDelegate {

    public static class Creator implements IXmlEditorCreator {
        @Override
        @SuppressWarnings("unchecked")
        public OtherXmlEditorDelegate createForFile(
                AndroidXmlCommonEditor delegator,
                IFileEditorInput input,
                ResourceFolderType type) {
            if (ResourceFolderType.XML == type) {
                return new OtherXmlEditorDelegate(delegator);
            }

            return null;
        }
    }

    /**
     * Old standalone-editor ID.
     * Use {@link AndroidXmlCommonEditor#ID} instead.
     */
    public static final String LEGACY_EDITOR_ID =
        AdtConstants.EDITORS_NAMESPACE + ".xml.XmlEditor"; //$NON-NLS-1$

    /**
     * Creates the form editor for resources XML files.
     */
    public OtherXmlEditorDelegate(AndroidXmlCommonEditor editor) {
        super(editor);
    }

    // ---- Base Class Overrides ----

    /**
     * Create the various form pages.
     */
    @Override
    public void createFormPages() {
        try {
            getEditor().addPage(new OtherXmlTreePage(getEditor()));
        } catch (PartInitException e) {
            AdtPlugin.log(e, "Error creating nested page"); //$NON-NLS-1$
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

        getUiRootNode().loadFromXmlNode(xml_doc);
    }

    /**
     * Creates the initial UI Root Node, including the known mandatory elements.
     * @param force if true, a new UiRootNode is recreated even if it already exists.
     */
    @Override
    public void initUiRootNode(boolean force) {
        // The root UI node is always created, even if there's no corresponding XML node.
        if (getUiRootNode() == null || force) {
            Document doc = null;
            if (getUiRootNode() != null) {
                doc = getUiRootNode().getXmlDocument();
            }

            // get the target data from the opened file (and its project)
            AndroidTargetData data = getEditor().getTargetData();

            DocumentDescriptor desc;
            if (data == null) {
                desc = new DocumentDescriptor("temp", null /*children*/);
            } else {
                desc = data.getXmlDescriptors().getDescriptor();
            }

            setUiRootNode(desc.createUiNode());
            getUiRootNode().setEditor(getEditor());

            onDescriptorsChanged(doc);
        }
    }

    // ---- Local Methods ----

    /**
     * Reloads the UI manifest node from the XML, and calls the pages to update.
     */
    private void onDescriptorsChanged(Document document) {
        if (document != null) {
            getUiRootNode().loadFromXmlNode(document);
        } else {
            getUiRootNode().reloadFromXmlNode(getUiRootNode().getXmlNode());
        }
    }
}
