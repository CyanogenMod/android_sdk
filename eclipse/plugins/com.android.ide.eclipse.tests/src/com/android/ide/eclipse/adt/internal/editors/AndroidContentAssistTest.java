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
package com.android.ide.eclipse.adt.internal.editors;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutContentAssist;
import com.android.ide.eclipse.adt.internal.editors.layout.refactoring.AdtProjectTest;
import com.android.ide.eclipse.adt.internal.editors.manifest.ManifestContentAssist;
import com.android.ide.eclipse.adt.internal.editors.resources.ResourcesContentAssist;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.swt.graphics.Point;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;

public class AndroidContentAssistTest extends AdtProjectTest {
    private static final String CARET = "^"; //$NON-NLS-1$

    public void testStartsWith() {
        assertTrue(AndroidContentAssist.startsWith("", ""));
        assertTrue(AndroidContentAssist.startsWith("a", ""));
        assertTrue(AndroidContentAssist.startsWith("A", ""));
        assertTrue(AndroidContentAssist.startsWith("A", "a"));
        assertTrue(AndroidContentAssist.startsWith("A", "A"));
        assertTrue(AndroidContentAssist.startsWith("Ab", "a"));
        assertTrue(AndroidContentAssist.startsWith("ab", "A"));
        assertTrue(AndroidContentAssist.startsWith("ab", "AB"));
        assertFalse(AndroidContentAssist.startsWith("ab", "ABc"));
        assertFalse(AndroidContentAssist.startsWith("", "ABc"));
    }

    public void testCompletion1() throws Exception {
        // Change attribute name completion
        checkLayoutCompletion("completion1.xml", "layout_w^idth=\"fill_parent\"");
    }

    public void testCompletion2() throws Exception {
        // Check attribute value completion for enum
        checkLayoutCompletion("completion1.xml", "layout_width=\"^fill_parent\"");
    }

    public void testCompletion3() throws Exception {
        // Check attribute value completion for enum with a prefix
        checkLayoutCompletion("completion1.xml", "layout_width=\"fi^ll_parent\"");
    }

    public void testCompletion4() throws Exception {
        // Check attribute value completion on units
        checkLayoutCompletion("completion1.xml", "marginBottom=\"50^\"");
    }

    public void testCompletion5() throws Exception {
        // Check attribute value completion on units with prefix
        checkLayoutCompletion("completion1.xml", "layout_marginLeft=\"50d^p\"");
    }

    public void testCompletion6() throws Exception {
        // Check resource sorting - "style" should bubble to the top for a style attribute
        checkLayoutCompletion("completion1.xml", "style=\"@android:^style/Widget.Button\"");
    }

    public void testCompletion7a() throws Exception {
        // Check flags (multiple values inside a single XML value, separated by | - where
        // the prefix is reset as soon as you pass each | )
        checkLayoutCompletion("completion1.xml", "android:gravity=\"l^eft|bottom\"");
    }

    public void testCompletion7b() throws Exception {
        checkLayoutCompletion("completion1.xml", "android:gravity=\"left|b^ottom\"");
    }

    public void testCompletion8() throws Exception {
        // Test completion right at the "=" sign; this will be taken to be the last
        // character of the attribute name (the caret is between the last char and before
        // the = characters), so it should match a single attribute
        checkLayoutCompletion("completion1.xml", "layout_width^=\"fill_parent\"");
    }

    public void testCompletion9() throws Exception {
        // Test completion right after the "=" sign; this will be taken to be the beginning
        // of the attribute value, but all values will also include a leading quote
        checkLayoutCompletion("completion1.xml", "layout_width=^\"fill_parent\"");
    }

    public void testCompletion10() throws Exception {
        // Test completion of element names
        checkLayoutCompletion("completion1.xml", "<T^extView");
    }

    public void testCompletion11() throws Exception {
        // Test completion of element names at the outside of the <. This should include
        // all the elements too (along with the leading <).
        checkLayoutCompletion("completion1.xml", "^<TextView");
    }

    public void testCompletion12() throws Exception {
        // Test completion of element names inside a nested XML; ensure that this will
        // correctly compute element names, not previous attribute
        checkLayoutCompletion("completion1.xml", "btn_default\">^</FrameLayout>");
    }

    public void testCompletion13a() throws Exception {
        checkLayoutCompletion("completion2.xml", "gravity=\"left|bottom|^cen");
    }

    public void testCompletion13b() throws Exception {
        checkLayoutCompletion("completion2.xml", "gravity=\"left|bottom|cen^");
    }

    public void testCompletion13c() throws Exception {
        checkLayoutCompletion("completion2.xml", "gravity=\"left|bottom^|cen");
    }

    public void testCompletion14() throws Exception {
        // Test completion of permissions
        checkManifestCompletion("manifest.xml", "android.permission.ACC^ESS_NETWORK_STATE");
    }

    public void testCompletion15() throws Exception {
        // Test completion of intents
        checkManifestCompletion("manifest.xml", "android.intent.category.L^AUNCHER");
    }

    public void testCompletion16() throws Exception {
        // Test completion of top level elements
        checkManifestCompletion("manifest.xml", "<^application android:i");
    }

    public void testCompletion17() throws Exception {
        // Test completion of attributes on the manifest element
        checkManifestCompletion("manifest.xml", "^android:versionCode=\"1\"");
    }

    public void testCompletion18() throws Exception {
        // Test completion of attributes on the manifest element
        checkManifestCompletion("manifest.xml",
                "<activity android:^name=\".TestActivity\"");
    }

    public void testCompletion19() throws Exception {
        // Test special case where completing on a new element in an otherwise blank line
        // does not add in full completion (with closing tags)
        checkLayoutCompletion("broken3.xml", "<EditT^");
    }

    public void testCompletion20() throws Exception {
        checkLayoutCompletion("broken1.xml", "android:textColorHigh^");
    }

    public void testCompletion21() throws Exception {
        checkLayoutCompletion("broken2.xml", "style=^");
    }

    public void testCompletion22() throws Exception {
        // Test completion where the cursor is inside an element (e.g. the next
        // char is NOT a <) - should not complete with end tags
        checkLayoutCompletion("completion4.xml", "<Button^");
    }

    // Test completion in style files

    public void testCompletion23() throws Exception {
        checkResourceCompletion("completionvalues1.xml", "android:textS^ize");
    }

    public void testCompletion24() throws Exception {
        checkResourceCompletion("completionvalues1.xml", "17^sp");
    }

    public void testCompletion25() throws Exception {
        checkResourceCompletion("completionvalues1.xml", "textColor\">^@color/title_color</item>");
    }

    public void testCompletion26() throws Exception {
        checkResourceCompletion("completionvalues1.xml",
                "<item name=\"android:shadowColor\">@an^</item>");
    }

    public void testCompletion27() throws Exception {
        checkResourceCompletion("completionvalues1.xml",
                "<item name=\"android:gravity\">^  </item>");
    }

    public void testCompletion28() throws Exception {
        checkResourceCompletion("completionvalues1.xml",
                "<item name=\"android:gravity\">  ^</item>");
    }

    public void testCompletion29() throws Exception {
        checkResourceCompletion("completionvalues1.xml", "<item name=\"gr^\">");
    }

    public void testCompletion30() throws Exception {
        checkResourceCompletion("completionvalues1.xml", "<item name=\"an^\">");
    }

    public void testCompletion31() throws Exception {
        checkResourceCompletion("completionvalues1.xml", "<item ^></item>");
    }

    public void testCompletion32() throws Exception {
        checkResourceCompletion("completionvalues1.xml", "<item name=\"^\"></item>");
    }

    public void testCompletion33() throws Exception {
        checkResourceCompletion("completionvalues1.xml",
                "<item name=\"android:allowSingleTap\">^</item>");
    }

    public void testCompletion34() throws Exception {
        checkResourceCompletion("completionvalues1.xml",
                "<item name=\"android:alwaysDrawnWithCache\">^  false  </item>");
    }

    public void testCompletion35() throws Exception {
        checkResourceCompletion("completionvalues1.xml",
                "<item name=\"android:alwaysDrawnWithCache\">  ^false  </item>");
    }

    public void testCompletion36() throws Exception {
        checkResourceCompletion("completionvalues1.xml",
                "<item name=\"android:alwaysDrawnWithCache\">  f^alse  </item>");
    }

    public void testCompletion37() throws Exception {
        checkResourceCompletion("completionvalues1.xml",
                "<item name=\"android:orientation\">h^</item>");
    }

    public void testCompletion38() throws Exception {
        checkResourceCompletion("completionvalues1.xml",
                "           c^");
    }

    public void testCompletion39() throws Exception {
        // If you are at the end of a closing quote (but with no space), completion should
        // include a separating space.
        checkLayoutCompletion("completion1.xml", "marginBottom=\"50\"^");
    }

    public void testCompletion40() throws Exception {
        // Same as test 39 but with single quote
        checkLayoutCompletion("completion5.xml",  "android:id='@+id/button2'^");
    }


    // ---- Test *applying* code completion ----

    // The following tests check -applying- a specific code completion
    // match - this verifies that the document is updated correctly, the
    // caret is moved appropriately, etc.

    public void testApplyCompletion1() throws Exception {
        // Change attribute name completion
        checkApplyLayoutCompletion("completion1.xml", "layout_w^idth=\"fill_parent\"",
                "android:layout_weight");
    }

    public void testApplyCompletion2() throws Exception {
        // Check attribute value completion for enum
        checkApplyLayoutCompletion("completion1.xml", "layout_width=\"^fill_parent\"",
                "match_parent");
    }

    public void testApplyCompletion3() throws Exception {
        // Check attribute value completion for enum with a prefix
        checkApplyLayoutCompletion("completion1.xml", "layout_width=\"fi^ll_parent\"",
                "fill_parent");
    }

    public void testApplyCompletion4() throws Exception {
        // Check attribute value completion on units
        checkApplyLayoutCompletion("completion1.xml", "marginBottom=\"50^\"", "50mm");
    }

    public void testApplyCompletion5() throws Exception {
        // Check attribute value completion on units with prefix
        checkApplyLayoutCompletion("completion1.xml", "layout_marginLeft=\"50d^p\"", "50dp");
    }

    public void testApplyCompletion6() throws Exception {
        // Check resource sorting - "style" should bubble to the top for a style attribute
        checkApplyLayoutCompletion("completion1.xml", "style=\"@android:^style/Widget.Button\"",
                "@android:drawable/");
    }

    public void testApplyCompletion7a() throws Exception {
        // Check flags (multiple values inside a single XML value, separated by | - where
        // the prefix is reset as soon as you pass each | )
        checkApplyLayoutCompletion("completion1.xml", "android:gravity=\"l^eft|bottom\"",
                "left");
        // NOTE - this will replace all flag values with the newly selected value.
        // That may not be the best behavior - perhaps we should only replace one portion
        // of the value.
    }

    public void testApplyCompletion7b() throws Exception {
        checkApplyLayoutCompletion("completion1.xml", "android:gravity=\"left|b^ottom\"",
                "bottom");
        // NOTE - this will replace all flag values with the newly selected value.
        // That may not be the best behavior - perhaps we should only replace one portion
        // of the value.
    }

    public void testApplyCompletion8() throws Exception {
        // Test completion right at the "=" sign; this will be taken to be the last
        // character of the attribute name (the caret is between the last char and before
        // the = characters), so it should match a single attribute
        checkApplyLayoutCompletion("completion1.xml", "layout_width^=\"fill_parent\"",
                "android:layout_width");
    }

    public void testApplyCompletion9() throws Exception {
        // Test completion right after the "=" sign; this will be taken to be the beginning
        // of the attribute value, but all values will also include a leading quote
        checkApplyLayoutCompletion("completion1.xml", "layout_width=^\"fill_parent\"",
                "\"wrap_content\"");
    }

    public void testApplyCompletion10() throws Exception {
        // Test completion of element names
        checkApplyLayoutCompletion("completion1.xml", "<T^extView", "TableLayout");
    }

    public void testApplyCompletion11a() throws Exception {
        // Test completion of element names at the outside of the <. This should include
        // all the elements too (along with the leading <).
        checkApplyLayoutCompletion("completion1.xml", "^<TextView", "<RadioGroup ></RadioGroup>");
    }

    public void testApplyCompletion11b() throws Exception {
        // Similar to testApplyCompletion11a, but replacing with an element that does not
        // have children (to test the closing tag insertion code)
        checkApplyLayoutCompletion("completion1.xml", "^<TextView", "<CheckBox />");
    }

    public void testApplyCompletion12() throws Exception {
        // Test completion of element names inside a nested XML; ensure that this will
        // correctly compute element names, not previous attribute
        checkApplyLayoutCompletion("completion1.xml", "btn_default\">^</FrameLayout>",
                "<FrameLayout ></FrameLayout>");
    }

    public void testApplyCompletion13a() throws Exception {
        checkApplyLayoutCompletion("completion2.xml", "gravity=\"left|bottom|^cen",
                "fill_vertical");
    }

    public void testApplyCompletion13b() throws Exception {
        checkApplyLayoutCompletion("completion2.xml", "gravity=\"left|bottom|cen^",
                "center_horizontal");
    }

    public void testApplyCompletion13c() throws Exception {
        checkApplyLayoutCompletion("completion2.xml", "gravity=\"left|bottom^|cen",
                "bottom|fill_horizontal");
    }

    public void testApplyCompletion14() throws Exception {
        // Test special case where completing on a new element in an otherwise blank line
        // does not add in full completion (with closing tags)
        checkApplyLayoutCompletion("broken3.xml", "<EditT^", "EditText />");
    }

    public void testApplyCompletion15() throws Exception {
        checkApplyLayoutCompletion("broken1.xml", "android:textColorHigh^",
                "android:textColorHighlight");
    }

    public void testApplyCompletion16() throws Exception {
        checkApplyLayoutCompletion("broken2.xml", "style=^",
                "\"@android:\"");
    }

    public void testApplyCompletion17() throws Exception {
        // Make sure that completion right before a / inside an element still
        // inserts the ="" part (e.g. handles it as "insertNew)
        checkApplyLayoutCompletion("completion3.xml", "<EditText ^/>",
                "android:textColorHighlight");
    }

    public void testApplyCompletion18() throws Exception {
        // Make sure that completion right before a > inside an element still
        // inserts the ="" part (e.g. handles it as "insertNew)
        checkApplyLayoutCompletion("completion3.xml", "<Button ^></Button>",
                "android:paddingRight");
    }

    public void testApplyCompletion19() throws Exception {
        // Test completion with single quotes (apostrophe)
        checkApplyLayoutCompletion("completion5.xml", "android:orientation='^'", "horizontal");
    }

    public void testApplyCompletion20() throws Exception {
        // Test completion with single quotes (apostrophe)
        checkApplyLayoutCompletion("completion5.xml", "android:layout_marginTop='50^dp'", "50pt");
    }

    public void testApplyCompletion21() throws Exception {
        // Test completion with single quotes (apostrophe)
        checkApplyLayoutCompletion("completion5.xml", "android:layout_width='^wrap_content'",
                "match_parent");
        // Still broken - but not a common case
        //checkApplyLayoutCompletion("completion5.xml", "android:layout_width=^'wrap_content'",
        //     "\"match_parent\"");
    }

    public void testApplyCompletion22() throws Exception {
        // Test completion in an empty string
        checkApplyLayoutCompletion("completion6.xml", "android:orientation=\"^\"", "horizontal");
    }

    public void testApplyCompletion23() throws Exception {
        // Test completion in an empty string
        checkApplyLayoutCompletion("completion7.xml", "android:orientation=\"^", "horizontal");
    }

    // Test completion in style files

    public void testApplyCompletion24a() throws Exception {
        checkApplyResourceCompletion("completionvalues1.xml", "android:textS^ize",
                "android:textSelectHandleLeft");
    }

    public void testApplyCompletion24b() throws Exception {
        checkApplyResourceCompletion("completionvalues1.xml", "17^sp", "17mm");
    }

    public void testApplyCompletion25() throws Exception {
        checkApplyResourceCompletion("completionvalues1.xml",
                "textColor\">^@color/title_color</item>", "@android:");
    }

    public void testApplyCompletion26() throws Exception {
        checkApplyResourceCompletion("completionvalues1.xml",
                "<item name=\"android:shadowColor\">@an^</item>", "@android:");
    }

    public void testApplyCompletion27() throws Exception {
        checkApplyResourceCompletion("completionvalues1.xml",
                "<item name=\"android:gravity\">^  </item>", "center_vertical");
    }

    public void testApplyCompletion28() throws Exception {
        checkApplyResourceCompletion("completionvalues1.xml",
                "<item name=\"android:gravity\">  ^</item>", "left");
    }

    public void testApplyCompletion29() throws Exception {
        checkApplyResourceCompletion("completionvalues1.xml", "<item name=\"gr^\">",
                "android:gravity");
    }

    public void testApplyCompletion30() throws Exception {
        checkApplyResourceCompletion("completionvalues1.xml", "<item name=\"an^\">",
                "android:animateOnClick");
    }

    public void testApplyCompletion31() throws Exception {
        checkApplyResourceCompletion("completionvalues1.xml", "<item ^></item>", "name");
    }

    public void testApplyCompletion32() throws Exception {
        checkApplyResourceCompletion("completionvalues1.xml", "<item name=\"^\"></item>",
                "android:background");
    }

    public void testApplyCompletion33() throws Exception {
        checkApplyResourceCompletion("completionvalues1.xml",
                "<item name=\"android:allowSingleTap\">^</item>", "true");
    }

    public void testApplyCompletion34() throws Exception {
        checkApplyResourceCompletion("completionvalues1.xml",
                "<item name=\"android:alwaysDrawnWithCache\">^  false  </item>", "true");
    }

    public void testApplyCompletion35() throws Exception {
        checkApplyResourceCompletion("completionvalues1.xml",
                "<item name=\"android:alwaysDrawnWithCache\">  ^false  </item>", "true");
    }

    public void testApplyCompletion36() throws Exception {
        checkApplyResourceCompletion("completionvalues1.xml",
                "<item name=\"android:alwaysDrawnWithCache\">  f^alse  </item>", "false");
    }

    public void testApplyCompletion37() throws Exception {
        checkApplyResourceCompletion("completionvalues1.xml",
                "<item name=\"android:orientation\">h^</item>", "horizontal");
    }

    public void testApplyCompletion38() throws Exception {
        checkApplyResourceCompletion("completionvalues1.xml",
                "           c^", "center");
    }

    public void testApplyCompletion39() throws Exception {
        // If you are at the end of a closing quote (but with no space), completion should
        // include a separating space.
        checkApplyLayoutCompletion("completion1.xml", "marginBottom=\"50\"^", " android:maxEms");
    }

    public void testApplyCompletion40() throws Exception {
        // If you are at the end of a closing quote (but with no space), completion should
        // include a separating space.
        checkApplyLayoutCompletion("completion5.xml",  "android:id='@+id/button2'^",
                " android:maxWidth");
    }


    // --- Code Completion test infrastructure ----

    private void checkLayoutCompletion(String name, String caretLocation) throws Exception {
        checkCompletion(name, getLayoutFile(getProject(), name), caretLocation,
                new LayoutContentAssist());
    }

    private void checkManifestCompletion(String name, String caretLocation) throws Exception {
        // Manifest files must be named AndroidManifest.xml. Must overwrite to replace
        // the default manifest created in the test project.
        IFile file = getTestDataFile(getProject(), name, "AndroidManifest.xml", true);

        checkCompletion(name, file, caretLocation,
                new ManifestContentAssist());
    }

    private void checkApplyLayoutCompletion(String name, String caretLocation,
            String match) throws Exception {
        checkApplyCompletion(name, getLayoutFile(getProject(), name), caretLocation,
                new LayoutContentAssist(), match);
    }

    private void checkResourceCompletion(String name, String caretLocation) throws Exception {
        checkCompletion(name, getValueFile(getProject(), name), caretLocation,
                new ResourcesContentAssist());
    }

    private void checkApplyResourceCompletion(String name, String caretLocation,
            String match) throws Exception {
        checkApplyCompletion(name, getValueFile(getProject(), name), caretLocation,
                new ResourcesContentAssist(), match);
    }

    private ICompletionProposal[] complete(IFile file, String caretLocation,
            AndroidContentAssist assist) throws Exception {

        // Determine the offset
        int offset = getCaretOffset(file, caretLocation);

        // Open file
        IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
        assertNotNull(page);
        IEditorPart editor = IDE.openEditor(page, file);
        assertTrue(editor instanceof AndroidXmlEditor);
        AndroidXmlEditor layoutEditor = (AndroidXmlEditor) editor;
        ISourceViewer viewer = layoutEditor.getStructuredSourceViewer();

        // Run code completion
        ICompletionProposal[] proposals = assist.computeCompletionProposals(viewer, offset);
        if (proposals == null) {
            proposals = new ICompletionProposal[0];
        }

        return proposals;
    }

    private void checkApplyCompletion(String basename, IFile file, String caretLocation,
            AndroidContentAssist assist, String match) throws Exception {
        ICompletionProposal[] proposals = complete(file, caretLocation, assist);
        ICompletionProposal chosen = null;
        for (ICompletionProposal proposal : proposals) {
            if (proposal.getDisplayString().equals(match)) {
                chosen = proposal;
                break;
            }
        }
        assertNotNull(chosen);
        assert chosen != null; // Eclipse null pointer analysis doesn't believe the JUnit assertion

        String fileContent = AdtPlugin.readFile(file);
        IDocument document = new Document();
        document.set(fileContent);

        // Apply code completion
        chosen.apply(document);

        // Insert caret location as well
        Point location = chosen.getSelection(document);
        document.replace(location.x, 0, CARET);

        String actual = document.get();

        int offset = getCaretOffset(fileContent, caretLocation);
        String beforeWithCaret = fileContent.substring(0, offset) + CARET
                + fileContent.substring(offset);

        String diff = getDiff(beforeWithCaret, actual);
        assertTrue(diff + " versus " + actual, diff.length() > 0 || beforeWithCaret.equals(actual));

        StringBuilder summary = new StringBuilder();
        summary.append("Code completion in " + basename + " for " + caretLocation + " selecting " + match + ":\n");
        if (diff.length() == 0) {
            diff = "No changes";
        }
        summary.append(diff);

        //assertEqualsGolden(basename, actual);
        assertEqualsGolden(basename, summary.toString(), "diff");
    }

    private void checkCompletion(String basename, IFile file, String caretLocation,
                AndroidContentAssist assist) throws Exception {
        ICompletionProposal[] proposals = complete(file, caretLocation, assist);
        StringBuilder sb = new StringBuilder(1000);
        sb.append("Code completion in " + basename + " for " + caretLocation + ":\n");
        for (ICompletionProposal proposal : proposals) {
            // TODO: assertNotNull(proposal.getImage());
            sb.append(proposal.getDisplayString());
            String help = proposal.getAdditionalProposalInfo();
            if (help != null && help.trim().length() > 0) {
                sb.append(" : ");
                sb.append(help.replace('\n', ' '));
            }
            sb.append('\n');
        }
        assertEqualsGolden(basename, sb.toString(), "txt");
    }
}
