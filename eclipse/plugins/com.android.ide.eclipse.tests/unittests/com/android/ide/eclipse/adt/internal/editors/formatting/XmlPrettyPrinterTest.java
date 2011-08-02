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

import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import junit.framework.TestCase;

public class XmlPrettyPrinterTest extends TestCase {
    private void checkFormat(XmlFormatPreferences prefs, XmlFormatStyle style,
            String xml, String expected) throws Exception {

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

        XmlPrettyPrinter printer = new XmlPrettyPrinter(prefs, style);

        StringBuilder sb = new StringBuilder(1000);
        printer.prettyPrint(-1, document, document, document, sb);
        String formatted = sb.toString();
        if (!expected.equals(formatted)) {
            System.out.println(formatted);
        }
        assertEquals(expected, formatted);
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
                "<LinearLayout>\n" +
                "\n" +
                "    <Button>\n" +
                "    </Button>\n" +
                "\n" +
                "</LinearLayout>");
    }

    public void testLayout2() throws Exception {
        checkFormat(
                "<LinearLayout><Button foo=\"bar\"></Button></LinearLayout>",
                "<LinearLayout>\n" +
                "\n" +
                "    <Button\n" +
                "        foo=\"bar\">\n" +
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
                "<LinearLayout>\n" +
                "\n" +
                "    <Button foo=\"bar\">\n" +
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
                "<LinearLayout\n" +
                "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    android:orientation=\"vertical\">\n" +
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
}
