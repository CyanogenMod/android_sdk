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
package com.android.ide.eclipse.adt.internal.editors.formatting;

import com.android.ide.eclipse.adt.internal.preferences.AdtPrefs;

import org.eclipse.jface.preference.PreferenceStore;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import junit.framework.TestCase;

public class XmlPrettyPrinterTest extends TestCase {
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        PreferenceStore store = new PreferenceStore();
        AdtPrefs.init(store);
        AdtPrefs prefs = AdtPrefs.getPrefs();
        prefs.initializeStoreWithDefaults(store);
        prefs.loadValues(null);
        XmlFormatPreferences formatPrefs = XmlFormatPreferences.create();
        assertTrue(formatPrefs.oneAttributeOnFirstLine);
    }

    private void checkFormat(XmlFormatPreferences prefs, XmlFormatStyle style,
            String xml, String expected, String delimiter,
            String startNodeName, boolean openTagOnly, String endNodeName) throws Exception {

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        InputSource is = new InputSource(new StringReader(xml));
        factory.setIgnoringComments(false);
        factory.setIgnoringElementContentWhitespace(false);
        factory.setCoalescing(false);
        factory.setNamespaceAware(true);
        factory.setValidating(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new ErrorHandler() {
            public void error(SAXParseException arg0) throws SAXException {
            }
            public void fatalError(SAXParseException arg0) throws SAXException {
            }
            public void warning(SAXParseException arg0) throws SAXException {
            }
        });
        Document document = builder.parse(is);

        XmlPrettyPrinter printer = new XmlPrettyPrinter(prefs, style, delimiter);

        StringBuilder sb = new StringBuilder(1000);
        Node startNode = document;
        Node endNode = document;
        if (startNodeName != null) {
            startNode = findNode(document.getDocumentElement(), startNodeName);
        }
        if (endNodeName != null) {
            endNode = findNode(document.getDocumentElement(), endNodeName);
        }

        printer.prettyPrint(-1, document, startNode, endNode, sb, false/*openTagOnly*/);
        String formatted = sb.toString();
        if (!expected.equals(formatted)) {
            System.out.println(formatted);
        }
        assertEquals(expected, formatted);
    }

    private Node findNode(Node node, String nodeName) {
        if (node.getNodeName().equals(nodeName)) {
            return node;
        }

        NodeList children = node.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            Node result = findNode(child, nodeName);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    protected int getCaretOffset(String fileContent, String caretLocation) {
        int caretDelta = caretLocation.indexOf("^"); //$NON-NLS-1$
        assertTrue(caretLocation, caretDelta != -1);

        String caretContext = caretLocation.substring(0, caretDelta)
                + caretLocation.substring(caretDelta + 1); // +1: skip "^"
        int caretContextIndex = fileContent.indexOf(caretContext);
        assertTrue("Caret content " + caretContext + " not found in file",
                caretContextIndex != -1);
        return caretContextIndex + caretDelta;
    }

    private void checkFormat(XmlFormatPreferences prefs, XmlFormatStyle style,
            String xml, String expected, String delimiter) throws Exception {
        checkFormat(prefs, style, xml, expected, delimiter, null, false, null);
    }

    private void checkFormat(XmlFormatPreferences prefs, XmlFormatStyle style,
            String xml, String expected) throws Exception {
        checkFormat(prefs, style, xml, expected, "\n"); //$NON-NLS-1$
    }
    private void checkFormat(XmlFormatStyle style, String xml, String expected)
            throws Exception {
        XmlFormatPreferences prefs = XmlFormatPreferences.create();
        checkFormat(prefs, style, xml, expected);
    }

    private void checkFormat(String xml, String expected) throws Exception {
        checkFormat(XmlFormatStyle.LAYOUT, xml, expected);
    }

    public void testLayout1() throws Exception {
        checkFormat(
                "<LinearLayout><Button></Button></LinearLayout>",
                "<LinearLayout >\n" +
                "\n" +
                "    <Button >\n" +
                "    </Button>\n" +
                "\n" +
                "</LinearLayout>");
    }

    public void testLayout2() throws Exception {
        checkFormat(
                "<LinearLayout><Button foo=\"bar\"></Button></LinearLayout>",
                "<LinearLayout >\n" +
                "\n" +
                "    <Button foo=\"bar\" >\n" +
                "    </Button>\n" +
                "\n" +
                "</LinearLayout>");
    }

    public void testLayout3() throws Exception {
        XmlFormatPreferences prefs = XmlFormatPreferences.create();
        prefs.oneAttributeOnFirstLine = true;
        checkFormat(
                prefs, XmlFormatStyle.LAYOUT,
                "<LinearLayout><Button foo=\"bar\"></Button></LinearLayout>",
                "<LinearLayout >\n" +
                "\n" +
                "    <Button foo=\"bar\" >\n" +
                "    </Button>\n" +
                "\n" +
                "</LinearLayout>");
    }

    /*
    // TODO: This test will only work with the Eclipse DOM because our parser doesn't
    // handle the ElementImpl case which records empty elements
    public void testClosedElements() throws Exception {
        checkFormat(
                XmlPrettyPrinter.Style.RESOURCE,
                "<resources>\n" +
                "<item   name=\"title_container\"  type=\"id\"   />\n" +
                "<item name=\"title_logo\" type=\"id\"/>\n" +
                "</resources>\n",
                "<resources>\n" +
                "    <item name=\"title_container\" type=\"id\" />\n" +
                "    <item name=\"title_logo\" type=\"id\" />\n" +
                "</resources>");
    }
     */

    public void testResources() throws Exception {
        checkFormat(
                XmlFormatStyle.RESOURCE,
                "<resources><item name=\"foo\">Text value here </item></resources>",
                "<resources>\n\n" +
                "    <item name=\"foo\">Text value here </item>\n" +
                "\n</resources>");
    }

    public void testNodeTypes() throws Exception {
        // Ensures that a document with all kinds of node types is serialized correctly
        checkFormat(
                XmlFormatStyle.LAYOUT,
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<!--\n" +
                "/**\n" +
                " * Licensed under the Apache License, Version 2.0 (the \"License\");\n" +
                " */\n" +
                "-->\n" +
                "<!DOCTYPE metadata [\n" +
                "<!ELEMENT metadata (category)*>\n" +
                "<!ENTITY % ISOLat2\n" +
                "         SYSTEM \"http://www.xml.com/iso/isolat2-xml.entities\" >\n" +
                "]>\n" +
                "<LinearLayout\n" +
                "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    android:orientation=\"vertical\">\n" +
                "<![CDATA[\n" +
                "This is character data!\n" +
                "<!-- This is not a comment! -->\n" +
                "and <this is not an element>\n" +
                "]]>         \n" +
                "This is text: &lt; and &amp;\n" +
                "<!-- comment 1 \"test\"... -->\n" +
                "<!-- ... comment2 -->\n" +
                "%ISOLat2;        \n" +
                "<!-- \n" +
                "Type <key>less-than</key> (&#x3C;)\n" +
                "-->        \n" +
                "</LinearLayout>",

                /* For some reason the SAX document builder does not include the processing
                 * instruction node (the Eclipse XML parser luckily does; try to change
                 * unit test to use it since we want to make sure the pretty printer
                 * handles it properly)
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                */
                "<!--\n" +
                "/**\n" +
                " * Licensed under the Apache License, Version 2.0 (the \"License\");\n" +
                " */\n" +
                "-->\n" +
                /* For some reason the SAX document builder does not include the
                 * doc type node (the Eclipse XML parser luckily does; try to change
                 * unit test to use it since we want to make sure the pretty printer
                 * handles it properly)
                "<!DOCTYPE metadata [\n" +
                "<!ELEMENT metadata (category)*>\n" +
                "<!ENTITY % ISOLat2\n" +
                "         SYSTEM \"http://www.xml.com/iso/isolat2-xml.entities\" >\n" +
                "]>\n" +
                 */
                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    android:orientation=\"vertical\" >\n" +
                "    <![CDATA[\n" +
                "This is character data!\n" +
                "<!-- This is not a comment! -->\n" +
                "and <this is not an element>\n" +
                "]]>\n" +
                "         \n" +
                "This is text: &lt; and &amp;\n" +
                "\n" +
                "    <!-- comment 1 \"test\"... -->\n" +
                "    <!-- ... comment2 -->\n" +
                "\n" +
                "%ISOLat2;        \n" +
                "\n" +
                "    <!--\n" +
                "Type <key>less-than</key> (&#x3C;)\n" +
                "    -->\n" +
                "\n" +
                "</LinearLayout>");
    }

    public void testWindowsDelimiters() throws Exception {
        checkFormat(
                XmlFormatPreferences.create(), XmlFormatStyle.LAYOUT,
                "<LinearLayout><Button foo=\"bar\"></Button></LinearLayout>",
                "<LinearLayout >\r\n" +
                "\r\n" +
                "    <Button foo=\"bar\" >\r\n" +
                "    </Button>\r\n" +
                "\r\n" +
                "</LinearLayout>",
                "\r\n");
    }

    public void testRemoveBlanklines() throws Exception {
        XmlFormatPreferences prefs = XmlFormatPreferences.create();
        prefs.removeEmptyLines = true;
        checkFormat(
                prefs, XmlFormatStyle.LAYOUT,
                "<foo><bar><baz1></baz1><baz2></baz2></bar><bar2></bar2><bar3><baz12></baz12></bar3></foo>",
                "<foo >\n" +
                "    <bar >\n" +
                "        <baz1 >\n" +
                "        </baz1>\n" +
                "        <baz2 >\n" +
                "        </baz2>\n" +
                "    </bar>\n" +
                "    <bar2 >\n" +
                "    </bar2>\n" +
                "    <bar3 >\n" +
                "        <baz12 >\n" +
                "        </baz12>\n" +
                "    </bar3>\n" +
                "</foo>");
    }

    public void testRange() throws Exception {
        checkFormat(
                XmlFormatPreferences.create(), XmlFormatStyle.LAYOUT,
                "<LinearLayout><Button foo=\"bar\"></Button><CheckBox/></LinearLayout>",
                "\n" +
                "    <Button foo=\"bar\" >\n" +
                "    </Button>\n" +
                "\n" +
                "    <CheckBox >\n" +
                "    </CheckBox>\n",
                "\n", "Button", false, "CheckBox");
    }

    public void testOpenTagOnly() throws Exception {
        checkFormat(
                XmlFormatPreferences.create(), XmlFormatStyle.LAYOUT,
                "<LinearLayout><Button foo=\"bar\"></Button><CheckBox/></LinearLayout>",
                "\n" +
                "    <Button foo=\"bar\" >\n" +
                "    </Button>\n",

                "\n", "Button", true, "Button");
    }

    public void testRange2() throws Exception {
        XmlFormatPreferences prefs = XmlFormatPreferences.create();
        prefs.removeEmptyLines = true;
        checkFormat(
                prefs, XmlFormatStyle.LAYOUT,
                "<foo><bar><baz1></baz1><baz2></baz2></bar><bar2></bar2><bar3><baz12></baz12></bar3></foo>",
                "        <baz1 >\n" +
                "        </baz1>\n" +
                "        <baz2 >\n" +
                "        </baz2>\n" +
                "    </bar>\n" +
                "    <bar2 >\n" +
                "    </bar2>\n" +
                "    <bar3 >\n" +
                "        <baz12 >\n" +
                "        </baz12>\n",
                "\n", "baz1", false, "baz12");
    }

    public void testEOLcomments() throws Exception {
        checkFormat(
                XmlFormatStyle.LAYOUT,
                "<selector xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                "    <item android:state_pressed=\"true\"\n" +
                "          android:color=\"#ffff0000\"></item> <!-- pressed -->\n" +
                "    <item android:state_focused=\"true\"\n" +
                "          android:color=\"#ff0000ff\"></item> <!-- focused -->\n" +
                "    <item android:color=\"#ff000000\"></item> <!-- default -->\n" +
                "</selector>",

                "<selector xmlns:android=\"http://schemas.android.com/apk/res/android\" >\n" +
                "\n" +
                "    <item\n" +
                "        android:color=\"#ffff0000\"\n" +
                "        android:state_pressed=\"true\"></item> <!-- pressed -->\n" +
                "\n" +
                "    <item\n" +
                "        android:color=\"#ff0000ff\"\n" +
                "        android:state_focused=\"true\"></item> <!-- focused -->\n" +
                "\n" +
                "    <item android:color=\"#ff000000\"></item> <!-- default -->\n" +
                "\n" +
                "</selector>");
    }

    public void testPreserveNewlineAfterComment() throws Exception {
        checkFormat(
                XmlFormatStyle.RESOURCE,
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<resources><dimen name=\"colorstrip_height\">6dip</dimen>\n" +
                "    <!-- comment1 --><dimen name=\"title_height\">45dip</dimen>\n" +
                "\n" +
                "    <!-- comment2: newline above --><dimen name=\"now_playing_height\">90dip</dimen>\n" +
                "    <dimen name=\"text_size_small\">14sp</dimen>\n" +
                "\n" +
                "\n" +
                "    <!-- comment3: newline above and below -->\n" +
                "\n" +
                "\n" +
                "\n" +
                "    <dimen name=\"text_size_medium\">18sp</dimen><dimen name=\"text_size_large\">22sp</dimen>\n" +
                "</resources>",

                "<resources>\n" +
                "\n" +
                "    <dimen name=\"colorstrip_height\">6dip</dimen>\n" +
                "\n" +
                "    <!-- comment1 -->\n" +
                "    <dimen name=\"title_height\">45dip</dimen>\n" +
                "\n" +
                "    <!-- comment2: newline above -->\n" +
                "    <dimen name=\"now_playing_height\">90dip</dimen>\n" +
                "    <dimen name=\"text_size_small\">14sp</dimen>\n" +
                "\n" +
                "    <!-- comment3: newline above and below -->\n" +
                "\n" +
                "    <dimen name=\"text_size_medium\">18sp</dimen>\n" +
                "    <dimen name=\"text_size_large\">22sp</dimen>\n" +
                "\n" +
                "</resources>");
    }

    public void testPlurals() throws Exception {
        checkFormat(
                XmlFormatStyle.RESOURCE,
                "<resources xmlns:xliff=\"urn:oasis:names:tc:xliff:document:1.2\">\n" +
                "<string name=\"toast_sync_error\">Sync error: <xliff:g id=\"error\">%1$s</xliff:g></string>\n" +
                "<string name=\"session_subtitle\"><xliff:g id=\"time\">%1$s</xliff:g> in <xliff:g id=\"room\">%2$s</xliff:g></string>\n" +
                "<plurals name=\"now_playing_countdown\">\n" +
                "<item quantity=\"zero\"><xliff:g id=\"remaining_time\">%2$s</xliff:g></item>\n" +
                "<item quantity=\"one\"><xliff:g id=\"number_of_days\">%1$s</xliff:g> day, <xliff:g id=\"remaining_time\">%2$s</xliff:g></item>\n" +
                "<item quantity=\"other\"><xliff:g id=\"number_of_days\">%1$s</xliff:g> days, <xliff:g id=\"remaining_time\">%2$s</xliff:g></item>\n" +
                "</plurals>\n" +
                "</resources>",

                "<resources xmlns:xliff=\"urn:oasis:names:tc:xliff:document:1.2\">\n" +
                "\n" +
                "    <string name=\"toast_sync_error\">Sync error: <xliff:g id=\"error\">%1$s</xliff:g></string>\n" +
                "    <string name=\"session_subtitle\"><xliff:g id=\"time\">%1$s</xliff:g> in <xliff:g id=\"room\">%2$s</xliff:g></string>\n" +
                "\n" +
                "    <plurals name=\"now_playing_countdown\">\n" +
                "        <item quantity=\"zero\"><xliff:g id=\"remaining_time\">%2$s</xliff:g></item>\n" +
                "        <item quantity=\"one\"><xliff:g id=\"number_of_days\">%1$s</xliff:g> day, <xliff:g id=\"remaining_time\">%2$s</xliff:g></item>\n" +
                "        <item quantity=\"other\"><xliff:g id=\"number_of_days\">%1$s</xliff:g> days, <xliff:g id=\"remaining_time\">%2$s</xliff:g></item>\n" +
                "    </plurals>\n" +
                "\n" +
                "</resources>");
    }


}
