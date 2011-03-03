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
package com.android.ide.eclipse.adt.internal.editors.layout.refactoring;

import com.android.ide.eclipse.adt.internal.editors.layout.LayoutEditor;

import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;

public abstract class VisualRefactoringWizard extends RefactoringWizard {
    protected final LayoutEditor mEditor;

    public VisualRefactoringWizard(Refactoring refactoring, LayoutEditor editor) {
        super(refactoring, DIALOG_BASED_USER_INTERFACE | PREVIEW_EXPAND_FIRST_NODE);
        mEditor = editor;
    }

    @Override
    public boolean performFinish() {
        mEditor.setIgnoreXmlUpdate(true);
        try {
            return super.performFinish();
        } finally {
            mEditor.setIgnoreXmlUpdate(false);
            mEditor.refreshXmlModel();
        }
    }
}
