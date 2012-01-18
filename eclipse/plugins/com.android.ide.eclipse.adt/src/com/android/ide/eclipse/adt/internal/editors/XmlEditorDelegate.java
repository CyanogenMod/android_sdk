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

package com.android.ide.eclipse.adt.internal.editors;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiElementNode;
import com.android.resources.ResourceFolderType;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.part.EditorActionBarContributor;
import org.eclipse.ui.part.FileEditorInput;
import org.w3c.dom.Document;

/**
 * Implementation of form editor for /res XML files.
 * <p/>
 * All delegates must have one {@link IXmlEditorCreator} instance
 * registered in the {@code DELEGATES[]} array of {@link AndroidXmlCommonEditor}.
 */
public abstract class XmlEditorDelegate {

    /** The editor that created the delegate. Never null. */
    private final AndroidXmlCommonEditor mEditor;

    /** Root node of the UI element hierarchy. Can be null. */
    private UiElementNode mUiRootNode;

    /**
     * Static creator for {@link XmlEditorDelegate}s. Delegates implement a method
     * that will decide whether this delegate can be created for the given file input.
     */
    public interface IXmlEditorCreator {
        /**
         * Determines whether this delegate can handle the given file, typically
         * based on its resource path (e.g. ResourceManager#getResourceFolder).
         *
         * @param delegator The non-null instance of {@link AndroidXmlCommonEditor}.
         * @param input A non-null input file.
         * @param type The {@link ResourceFolderType} of the folder containing the file,
         *   if it can be determined. Null otherwise.
         * @return A new delegate that can handle that file or null.
         */
        public @Nullable <T extends XmlEditorDelegate> T createForFile(
                            @NonNull AndroidXmlCommonEditor delegator,
                            @NonNull IFileEditorInput input,
                            @Nullable ResourceFolderType type);
    }

    /** Implemented by delegates that need to support {@link EditorActionBarContributor} */
    public interface IActionContributorDelegate {
        /** Called from {@link EditorActionBarContributor#setActiveEditor(IEditorPart)}. */
        public void setActiveEditor(IEditorPart part, IActionBars bars);
    }

    public XmlEditorDelegate(AndroidXmlCommonEditor editor) {
        mEditor = editor;
    }

    public void dispose() {
    }

    /**
     * Returns the editor that created this delegate.
     *
     * @return the editor that created this delegate. Never null.
     */
    public @NonNull AndroidXmlCommonEditor getEditor() {
        return mEditor;
    }

    /**
     * @return The root node of the UI element hierarchy
     */
    public UiElementNode getUiRootNode() {
        return mUiRootNode;
    }

    protected void setUiRootNode(UiElementNode uiRootNode) {
        mUiRootNode = uiRootNode;
    }

    /** Called to compute the initial {@code UiRootNode}. */
    public abstract void initUiRootNode(boolean force);

    /**
     * Returns true, indicating the "save as" operation is supported by this editor.
     */
    public boolean isSaveAsAllowed() {
        return true;
    }

    /**
     * Create the various form pages.
     */
    public abstract void createFormPages();

    public void postCreatePages() {
        // pass
    }

    /**
     * Changes the tab/title name to include the project name.
     */
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
    public abstract void xmlModelChanged(Document xml_doc);

    public void pageChange(int newPageIndex) {
        // pass
    }

    public void postPageChange(int newPageIndex) {
        // pass
    }
    /**
     * Save the XML.
     * <p/>
     * The actual save operation is done in the super class by committing
     * all data to the XML model and then having the Structured XML Editor
     * save the XML.
     * <p/>
     * Here we just need to tell the graphical editor that the model has
     * been saved.
     */
    public void doSave(IProgressMonitor monitor) {
        // pass
    }

    /**
     * Tells the editor to start a Lint check.
     * It's up to the caller to check whether this should be done depending on preferences.
     */
    public Job runLint() {
        return getEditor().startLintJob();
    }


    /**
     * Returns the custom IContentOutlinePage or IPropertySheetPage when asked for it.
     */
    public Object getAdapter(Class<?> adapter) {
        return null;
    }


}
