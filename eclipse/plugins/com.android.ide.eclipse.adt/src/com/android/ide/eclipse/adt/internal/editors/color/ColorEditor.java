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

package com.android.ide.eclipse.adt.internal.editors.color;

import static com.android.ide.eclipse.adt.AdtConstants.EDITORS_NAMESPACE;

import com.android.ide.eclipse.adt.internal.editors.AndroidXmlEditor;
import com.android.ide.eclipse.adt.internal.editors.descriptors.ElementDescriptor;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiElementNode;
import com.android.ide.eclipse.adt.internal.sdk.AndroidTargetData;

import org.eclipse.core.resources.IFile;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Editor for /res/color XML files.
 */
@SuppressWarnings("restriction")
public class ColorEditor extends AndroidXmlEditor {
    public static final String ID = EDITORS_NAMESPACE + ".color.ColorEditor"; //$NON-NLS-1$

    /** Root node of the UI element hierarchy */
    private UiElementNode mUiRootNode;

    public ColorEditor() {
        super();
    }

    @Override
    public UiElementNode getUiRootNode() {
        return mUiRootNode;
    }

    @Override
    public boolean isSaveAsAllowed() {
        return true;
    }

    @Override
    protected void createFormPages() {
        /* Disabled for now; doesn't work quite right
        try {
            addPage(new ColorTreePage(this));
        } catch (PartInitException e) {
            AdtPlugin.log(IStatus.ERROR, "Error creating nested page"); //$NON-NLS-1$
            AdtPlugin.getDefault().getLog().log(e.getStatus());
        }
        */
     }

    /* (non-java doc)
     * Change the tab/title name to include the project name.
     */
    @Override
    protected void setInput(IEditorInput input) {
        super.setInput(input);
        if (input instanceof FileEditorInput) {
            FileEditorInput fileInput = (FileEditorInput) input;
            IFile file = fileInput.getFile();
            setPartName(String.format("%1$s",
                    file.getName()));
        }
    }

    @Override
    protected void xmlModelChanged(Document xmlDoc) {
        // create the ui root node on demand.
        initUiRootNode(false /*force*/);

        Element rootElement = xmlDoc.getDocumentElement();
        mUiRootNode.loadFromXmlNode(rootElement);

        super.xmlModelChanged(xmlDoc);
    }

    @Override
    protected void initUiRootNode(boolean force) {
        // The manifest UI node is always created, even if there's no corresponding XML node.
        if (mUiRootNode == null || force) {
            ElementDescriptor descriptor;
            AndroidTargetData data = getTargetData();
            if (data == null) {
                descriptor = new ColorDescriptors().getDescriptor();
            } else {
                descriptor = data.getColorDescriptors().getDescriptor();
            }
            mUiRootNode = descriptor.createUiNode();
            mUiRootNode.setEditor(this);
            onDescriptorsChanged();
        }
    }

    private void onDescriptorsChanged() {
        IStructuredModel model = getModelForRead();
        if (model != null) {
            try {
                Node node = getXmlDocument(model).getDocumentElement();
                mUiRootNode.reloadFromXmlNode(node);
            } finally {
                model.releaseFromRead();
            }
        }
    }
}
