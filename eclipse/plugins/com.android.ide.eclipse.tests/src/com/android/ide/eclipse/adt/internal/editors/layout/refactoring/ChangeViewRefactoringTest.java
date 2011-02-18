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

import static com.android.ide.common.layout.LayoutConstants.FQCN_RADIO_BUTTON;

import org.eclipse.core.resources.IFile;
import org.eclipse.ltk.core.refactoring.Change;
import org.w3c.dom.Element;

import java.util.List;

public class ChangeViewRefactoringTest extends RefactoringTest {

    public void testChangeView1() throws Exception {
        checkRefactoring("sample1a.xml", "@+id/button1", "@+id/button6");
    }

    private void checkRefactoring(String basename, String... ids) throws Exception {
        assertTrue(ids.length > 0);

        IFile file = getTestFile(sProject, basename);
        TestContext info = setupTestContext(file, basename);
        TestLayoutEditor layoutEditor = info.mLayoutEditor;
        List<Element> selectedElements = getElements(info.mElement, ids);

        ChangeViewRefactoring refactoring = new ChangeViewRefactoring(selectedElements,
                layoutEditor);
        refactoring.setType(FQCN_RADIO_BUTTON);

        List<Change> changes = refactoring.computeChanges();
        checkEdits(basename, changes);
    }
}
