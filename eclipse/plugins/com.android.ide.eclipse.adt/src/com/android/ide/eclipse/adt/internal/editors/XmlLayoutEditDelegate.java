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

import com.android.ide.eclipse.adt.internal.editors.XmlEditorDelegate.IXmlEditorCreator;
import com.android.ide.eclipse.adt.internal.editors.layout.MatchingStrategy;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.GraphicalEditorPart;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.RulesEngine;

import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.ui.IEditorInput;

/**
 * Implementation of form editor for /res XML files.
 * <p/>
 * All delegates must have one {@link IXmlEditorCreator} instance
 * registered in the {@code DELEGATES[]} array of {@link AndroidXmlCommonEditor}.
 */
public abstract class XmlLayoutEditDelegate extends XmlEditorDelegate {

    public XmlLayoutEditDelegate(AndroidXmlCommonEditor editor) {
        super(editor);
    }

    public abstract void setInputWithNotify(IEditorInput input);

    /**
     * Called to replace the current {@link IEditorInput} with another one.
     * <p/>This is used when {@link MatchingStrategy} returned <code>true</code> which means we're
     * opening a different configuration of the same layout.
     */
    public abstract void showEditorInput(IEditorInput editorInput);

    /**
     * Performs a complete refresh of the XML model.
     */
    public abstract void refreshXmlModel();

    /**
     * Returns the {@link RulesEngine} associated with this editor
     *
     * @return the {@link RulesEngine} associated with this editor.
     */
    public abstract RulesEngine getRulesEngine();

    /**
     * Returns the {@link GraphicalEditorPart} associated with this editor
     *
     * @return the {@link GraphicalEditorPart} associated with this editor
     */
    public abstract GraphicalEditorPart getGraphicalEditor();


    public abstract void setNewFileOnConfigChange(boolean state);

    /**
     * Tells the graphical editor to recompute its layout.
     */
    public abstract void recomputeLayout();

    public abstract boolean supportsFormatOnGuiEdit();

    public abstract void postRunLintJob(Job runLintJob);
}
