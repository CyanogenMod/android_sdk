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
import org.eclipse.core.runtime.IPath;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.w3c.dom.Element;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExtractIncludeRefactoringTest extends RefactoringTest {

    public void testExtract1() throws Exception {
        // Basic: Extract a single button
        checkRefactoring("sample3.xml", "newlayout1", false, null, 2, "@+id/button2");
    }

    public void testExtract2() throws Exception {
        // Extract a couple of elements
        checkRefactoring("sample3.xml", "newlayout2",  false, null, 2,
                "@+id/button2", "@+id/android_logo");
    }

    public void testExtract3() throws Exception {
        // Test to make sure layout attributes are updated
        checkRefactoring("sample2.xml", "newlayout3", false, null, 2,
                "@+id/button3");
    }

    public void testExtract4() throws Exception {
        // Tests extracting from -multiple- files (as well as with custom android namespace
        // prefix)

        // Make sure the variation-files exist
        Map<IPath, String> extraFiles = new HashMap<IPath, String>();
        extraFiles.put(getTestDataFile(getProject(), "sample3-variation1.xml",
                "res/layout-land/sample3.xml").getProjectRelativePath(),
                "sample3-variation1.xml");
        extraFiles.put(getTestDataFile(getProject(), "sample3-variation2.xml",
                "res/layout-xlarge-land/sample3.xml").getProjectRelativePath(),
                "sample3-variation2.xml");

        checkRefactoring("sample3.xml", "newlayout3", true, extraFiles, 4,
                "@+id/android_logo");
    }

    public void testExtract5() throws Exception {
        // Tests extracting from multiple files with -contiguous regions-.

        // Make sure the variation-files exist
        Map<IPath, String> extraFiles = new HashMap<IPath, String>();
        extraFiles.put(getTestDataFile(getProject(), "sample3-variation1.xml",
                "res/layout-land/sample3.xml").getProjectRelativePath(),
                "sample3-variation1.xml");
        extraFiles.put(getTestDataFile(getProject(), "sample3-variation2.xml",
                "res/layout-xlarge-land/sample3.xml").getProjectRelativePath(),
                "sample3-variation2.xml");

        checkRefactoring("sample3.xml", "newlayout3", true, extraFiles, 4,
                "@+id/android_logo", "@+id/button1");
    }

    private void checkRefactoring(String basename, String layoutName,
            boolean replaceOccurrences, Map<IPath,String> extraFiles,
            int expectedModifiedFileCount, String... ids) throws Exception {
        assertTrue(ids.length > 0);

        IFile file = getLayoutFile(sProject, basename);
        TestContext info = setupTestContext(file, basename);
        TestLayoutEditor layoutEditor = info.mLayoutEditor;
        List<Element> selectedElements = getElements(info.mElement, ids);

        ExtractIncludeRefactoring refactoring = new ExtractIncludeRefactoring(selectedElements,
                layoutEditor);
        refactoring.setLayoutName(layoutName);
        refactoring.setReplaceOccurrences(replaceOccurrences);
        List<Change> changes = refactoring.computeChanges();

        assertTrue(changes.size() >= 3);

        Map<IPath,String> fileToGolden = new HashMap<IPath,String>();
        IPath sourcePath = file.getProjectRelativePath();
        fileToGolden.put(sourcePath, basename);
        IPath newPath = sourcePath.removeLastSegments(1).append(layoutName + DOT_XML);
        fileToGolden.put(newPath, layoutName + DOT_XML);
        if (extraFiles != null) {
            fileToGolden.putAll(extraFiles);
        }

        checkEdits(changes, fileToGolden);

        int modifiedFileCount = 0;
        for (Change change : changes) {
            if (change instanceof TextFileChange) {
                modifiedFileCount++;
            }
        }
        assertEquals(expectedModifiedFileCount, modifiedFileCount);
    }
}
