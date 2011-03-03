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

import static com.android.ide.common.layout.LayoutConstants.ANDROID_WIDGET_PREFIX;
import static com.android.ide.eclipse.adt.AdtConstants.DOT_XML;

import com.android.ide.common.rendering.api.ViewInfo;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.CanvasViewInfo;
import com.android.ide.eclipse.adt.internal.editors.layout.uimodel.UiViewElementNode;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiElementNode;
import com.android.sdklib.SdkConstants;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.wst.sse.core.StructuredModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.sse.core.internal.provisional.IndexedRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocument;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMModel;
import org.w3c.dom.Element;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("restriction")
public class RefactoringTest extends AdtProjectTest {
    protected static Element findElementById(Element root, String id) {
        if (id.equals(VisualRefactoring.getId(root))) {
            return root;
        }

        for (Element child : RelativeLayoutConversionHelper.getChildren(root)) {
            Element result = findElementById(child, id);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    protected static List<Element> getElements(Element root, String... ids) {
        List<Element> selectedElements = new ArrayList<Element>();
        for (String id : ids) {
            Element element = findElementById(root, id);
            assertNotNull(element);
            selectedElements.add(element);
        }
        return selectedElements;
    }

    protected void checkEdits(String basename, List<Change> changes) throws BadLocationException,
            IOException {
        IDocument document = new Document();

        String xml = readTestFile(basename, false);
        if (xml == null) { // New file
            xml = ""; //$NON-NLS-1$
        }
        document.set(xml);

        for (Change change : changes) {
            if (change instanceof TextFileChange) {
                TextFileChange tf = (TextFileChange) change;
                TextEdit edit = tf.getEdit();
                if (edit instanceof MultiTextEdit) {
                    MultiTextEdit edits = (MultiTextEdit) edit;
                    edits.apply(document);
                } else {
                    edit.apply(document);
                }
            } else {
                System.out.println("Ignoring non-textfilechange in refactoring result");
            }
        }

        String actual = document.get();
        assertEqualsGolden(basename, actual);
    }

    protected void assertEqualsGolden(String basename, String actual) {
        String testName = getName();
        if (testName.startsWith("test")) {
            testName = testName.substring(4);
            if (Character.isUpperCase(testName.charAt(0))) {
                testName = Character.toLowerCase(testName.charAt(0)) + testName.substring(1);
            }
        }
        String expectedName;
        if (basename.endsWith(DOT_XML)) {
            expectedName = basename.substring(0, basename.length() - DOT_XML.length())
                    + "-expected-" + testName + DOT_XML;
        } else {
            expectedName = basename + ".expected";
        }
        String expected = readTestFile(expectedName, false);
        if (expected == null) {
            File expectedPath = new File(getTempDir(), expectedName);
            AdtPlugin.writeFile(expectedPath, actual);
            System.out.println("Expected - written to " + expectedPath + ":\n");
            System.out.println(actual);
            fail("Did not find golden file (" + expectedName + "): Wrote contents as "
                    + expectedPath);
        } else {
            if (!expected.equals(actual)) {
                File expectedPath = new File(getTempDir(), expectedName);
                File actualPath = new File(getTempDir(),
                        expectedName.replace("expected", "actual"));
               AdtPlugin.writeFile(expectedPath, expected);
                AdtPlugin.writeFile(actualPath, actual);
                System.out.println("The files differ - see " + expectedPath + " versus "
                        + actualPath);
                assertEquals("The files differ - see " + expectedPath + " versus " + actualPath,
                        expected, actual);
            }
        }
    }

    protected UiViewElementNode createModel(UiViewElementNode parent, Element element) {
        List<Element> children = RelativeLayoutConversionHelper.getChildren(element);
        String fqcn = ANDROID_WIDGET_PREFIX + element.getTagName();
        boolean hasChildren = children.size() > 0;
        UiViewElementNode node = createNode(parent, fqcn, hasChildren);
        node.setXmlNode(element);
        for (Element child : children) {
            createModel(node, child);
        }

        return node;
    }

    /**
     * Builds up a ViewInfo hierarchy for the given model. This is done by
     * reading .info dump files which record the exact pixel sizes of each
     * ViewInfo object. These files are assumed to match up exactly with the
     * model objects. This is done rather than rendering an actual layout
     * hierarchy to insulate the test from pixel difference (in say font size)
     * among platforms, as well as tying the test to particulars about relative
     * sizes of things which may change with theme adjustments etc.
     * <p>
     * Each file can be generated by the dump method in the ViewHierarchy.
     */
    protected ViewInfo createInfos(UiElementNode model, String relativePath) {
        String basename = relativePath.substring(0, relativePath.lastIndexOf('.') + 1);
        String relative = basename + "info"; //$NON-NLS-1$
        String info = readTestFile(relative, true);
        // Parse the info file and build up a model from it
        // Each line contains a new info.
        // If indented it is a child of the parent.
        String[] lines = info.split("\n"); //$NON-NLS-1$

        // Iteration order for the info file should match exactly the UI model so
        // we can just advance the line index sequentially as we traverse

        return create(model, Arrays.asList(lines).iterator());
    }

    protected ViewInfo create(UiElementNode node, Iterator<String> lineIterator) {
        // android.widget.LinearLayout [0,36,240,320]
        Pattern pattern = Pattern.compile("(\\s*)(\\S+) \\[(\\d+),(\\d+),(\\d+),(\\d+)\\].*");
        assertTrue(lineIterator.hasNext());
        String description = lineIterator.next();
        Matcher matcher = pattern.matcher(description);
        assertTrue(matcher.matches());
        //String indent = matcher.group(1);
        //String fqcn = matcher.group(2);
        String left = matcher.group(3);
        String top = matcher.group(4);
        String right = matcher.group(5);
        String bottom = matcher.group(6);

        ViewInfo view = new ViewInfo(node.getXmlNode().getLocalName(), node,
                Integer.parseInt(left), Integer.parseInt(top),
                Integer.parseInt(right), Integer.parseInt(bottom));

        List<UiElementNode> childNodes = node.getUiChildren();
        if (childNodes.size() > 0) {
            List<ViewInfo> children = new ArrayList<ViewInfo>();
            for (UiElementNode child : childNodes) {
                children.add(create(child, lineIterator));
            }
            view.setChildren(children);
        }

        return view;
    }

    protected File getTempDir() {
        if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_DARWIN) {
            return new File("/tmp"); //$NON-NLS-1$
        }
        return new File(System.getProperty("java.io.tmpdir")); //$NON-NLS-1$
    }

    protected TestContext setupTestContext(IFile file, String relativePath) throws Exception {
        IStructuredModel structuredModel = null;
        org.w3c.dom.Document domDocument = null;
        IStructuredDocument structuredDocument = null;
        Element element = null;

        try {
            IModelManager modelManager = StructuredModelManager.getModelManager();
            structuredModel = modelManager.getModelForRead(file);
            if (structuredModel instanceof IDOMModel) {
                IDOMModel domModel = (IDOMModel) structuredModel;
                domDocument = domModel.getDocument();
                element = domDocument.getDocumentElement();
                structuredDocument = structuredModel.getStructuredDocument();
            }
        } finally {
            if (structuredModel != null) {
                structuredModel.releaseFromRead();
            }
        }

        assertNotNull(structuredModel);
        assertNotNull(domDocument);
        assertNotNull(element);
        assertNotNull(structuredDocument);
        assertTrue(element instanceof IndexedRegion);

        UiViewElementNode model = createModel(null, element);
        ViewInfo info = createInfos(model, relativePath);
        CanvasViewInfo rootView = CanvasViewInfo.create(info).getFirst();
        TestLayoutEditor layoutEditor = new TestLayoutEditor(file, structuredDocument, null);

        TestContext testInfo = createTestContext();
        testInfo.mFile = file;
        testInfo.mStructuredModel = structuredModel;
        testInfo.mStructuredDocument = structuredDocument;
        testInfo.mElement = element;
        testInfo.mDomDocument = domDocument;
        testInfo.mUiModel = model;
        testInfo.mViewInfo = info;
        testInfo.mRootView = rootView;
        testInfo.mLayoutEditor = layoutEditor;

        return testInfo;
    }

    protected TestContext createTestContext() {
        return new TestContext();
    }

    protected static class TestContext {
        protected IFile mFile;
        protected IStructuredModel mStructuredModel;
        protected IStructuredDocument mStructuredDocument;
        protected org.w3c.dom.Document mDomDocument;
        protected Element mElement;
        protected UiViewElementNode mUiModel;
        protected ViewInfo mViewInfo;
        protected CanvasViewInfo mRootView;
        protected TestLayoutEditor mLayoutEditor;
    }

    @Override
    public void testDummy() {
        // To avoid JUnit warning that this class contains no tests, even though
        // this is an abstract class and JUnit shouldn't try
    }
}
