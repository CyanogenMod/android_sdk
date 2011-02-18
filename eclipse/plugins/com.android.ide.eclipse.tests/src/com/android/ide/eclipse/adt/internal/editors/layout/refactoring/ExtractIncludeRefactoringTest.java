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

import static com.android.ide.eclipse.adt.AdtConstants.DOT_XML;

import org.eclipse.core.resources.IFile;
import org.eclipse.ltk.core.refactoring.Change;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class ExtractIncludeRefactoringTest extends RefactoringTest {

    public void testExtract1() throws Exception {
        // Basic: Extract a single button
        checkRefactoring("sample3.xml", "newlayout1", true, true, "@+id/button2");
    }

    public void testExtract2() throws Exception {
        // Extract a couple of elements
        checkRefactoring("sample3.xml", "newlayout2", true, true,
                "@+id/button2", "@+id/android_logo");
    }

    private void checkRefactoring(String basename, String layoutName,
            boolean updateRefs, boolean replaceOccurrences, String... ids) throws Exception {
        assertTrue(ids.length > 0);

        IFile file = getTestFile(sProject, basename);
        TestContext info = setupTestContext(file, basename);
        TestLayoutEditor layoutEditor = info.mLayoutEditor;
        List<Element> selectedElements = getElements(info.mElement, ids);

        ExtractIncludeRefactoring refactoring = new ExtractIncludeRefactoring(selectedElements,
                layoutEditor);
        refactoring.setLayoutName(layoutName);
        refactoring.setUpdateReferences(updateRefs);
        refactoring.setReplaceOccurrences(replaceOccurrences);
        List<Change> changes = refactoring.computeChanges();

        assertEquals(3, changes.size());

        // The first change is to update the current file:
        checkEdits(basename, Collections.singletonList(changes.get(0)));

        // The second change is to create the new extracted file
        checkEdits(layoutName + DOT_XML, Collections.singletonList(changes.get(1)));
    }
}
