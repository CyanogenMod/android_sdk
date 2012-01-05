/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.util;

import com.android.util.PositionXmlParser.Position;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.io.Writer;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public class PositionXmlParserTest extends TestCase {
    public void test() throws Exception {
        String xml =
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    android:layout_width=\"match_parent\"\n" +
                "    android:layout_height=\"wrap_content\"\n" +
                "    android:orientation=\"vertical\" >\n" +
                "\n" +
                "    <Button\n" +
                "        android:id=\"@+id/button1\"\n" +
                "        android:layout_width=\"wrap_content\"\n" +
                "        android:layout_height=\"wrap_content\"\n" +
                "        android:text=\"Button\" />\n" +
                "\n" +
                "    <Button\n" +
                "        android:id=\"@+id/button2\"\n" +
                "        android:layout_width=\"wrap_content\"\n" +
                "        android:layout_height=\"wrap_content\"\n" +
                "        android:text=\"Button\" />\n" +
                "\n" +
                "</LinearLayout>\n";
        PositionXmlParser parser = new PositionXmlParser();
        File file = File.createTempFile("parsertest", ".xml");
        Writer fw = new BufferedWriter(new FileWriter(file));
        fw.write(xml);
        fw.close();
        Document document = parser.parse(new FileInputStream(file));
        assertNotNull(document);

        // Basic parsing heart beat tests
        Element linearLayout = (Element) document.getElementsByTagName("LinearLayout").item(0);
        assertNotNull(linearLayout);
        NodeList buttons = document.getElementsByTagName("Button");
        assertEquals(2, buttons.getLength());
        final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
        assertEquals("wrap_content",
                linearLayout.getAttributeNS(ANDROID_URI, "layout_height"));

        // Check attribute positions
        Attr attr = linearLayout.getAttributeNodeNS(ANDROID_URI, "layout_width");
        assertNotNull(attr);
        Position start = parser.getPosition(attr);
        Position end = start.getEnd();
        assertEquals(2, start.getLine());
        assertEquals(xml.indexOf("android:layout_width"), start.getOffset());
        assertEquals(2, end.getLine());
        String target = "android:layout_width=\"match_parent\"";
        assertEquals(xml.indexOf(target) + target.length(), end.getOffset());

        // Check element positions
        Element button = (Element) buttons.item(0);
        start = parser.getPosition(button);
        end = start.getEnd();
        assertNull(end.getEnd());
        assertEquals(6, start.getLine());
        assertEquals(xml.indexOf("<Button"), start.getOffset());
        assertEquals(xml.indexOf("/>") + 2, end.getOffset());
        assertEquals(10, end.getLine());
        int button1End = end.getOffset();

        Element button2 = (Element) buttons.item(1);
        start = parser.getPosition(button2);
        end = start.getEnd();
        assertEquals(12, start.getLine());
        assertEquals(xml.indexOf("<Button", button1End), start.getOffset());
        assertEquals(xml.indexOf("/>", start.getOffset()) + 2, end.getOffset());
        assertEquals(16, end.getLine());

        file.delete();
    }

    public void testLineEndings() throws Exception {
        // Test for http://code.google.com/p/android/issues/detail?id=22925
        String xml =
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n" +
                "<LinearLayout>\r\n" +
                "\r" +
                "<LinearLayout></LinearLayout>\r\n" +
                "</LinearLayout>\r\n";
        PositionXmlParser parser = new PositionXmlParser();
        File file = File.createTempFile("parsertest2", ".xml");
        Writer fw = new BufferedWriter(new FileWriter(file));
        fw.write(xml);
        fw.close();
        Document document = parser.parse(new FileInputStream(file));
        assertNotNull(document);

        file.delete();
    }

    private static void checkEncoding(String encoding, boolean writeBom, boolean writeEncoding)
            throws Exception {
        String value = "¾¿Œ";
        StringBuilder sb = new StringBuilder();

        sb.append("<?xml version=\"1.0\"");
        if (writeEncoding) {
            sb.append(" encoding=\"");
            sb.append(encoding);
            sb.append("\"");
        }
        sb.append("?>\n" +
                "<!-- This is a \n" +
                "     multiline comment\n" +
                "-->\n" +
                "<foo ");
        int startAttrOffset = sb.length();
        sb.append("attr=\"");
        sb.append(value);
        sb.append("\"");
        sb.append(">\n" +
                "\n" +
                "<bar></bar>\n" +
                "</foo>\n");
        PositionXmlParser parser = new PositionXmlParser();
        File file = File.createTempFile("parsertest" + encoding + writeBom + writeEncoding,
                ".xml");
        BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(file));
        OutputStreamWriter writer = new OutputStreamWriter(stream, encoding);

        if (writeBom) {
            String normalized = encoding.toLowerCase().replace("-", "_");
            if (normalized.equals("utf_8")) {
                stream.write(0xef);
                stream.write(0xbb);
                stream.write(0xbf);
            } else if (normalized.equals("utf_16")) {
                stream.write(0xfe);
                stream.write(0xff);
            } else if (normalized.equals("utf_16le")) {
                stream.write(0xff);
                stream.write(0xfe);
            } else if (normalized.equals("utf_32")) {
                stream.write(0x0);
                stream.write(0x0);
                stream.write(0xfe);
                stream.write(0xff);
            } else if (normalized.equals("utf_32le")) {
                stream.write(0xff);
                stream.write(0xfe);
                stream.write(0x0);
                stream.write(0x0);
            } else {
                fail("Can't write BOM for encoding " + encoding);
            }
        }

        writer.write(sb.toString());
        writer.close();

        Document document = parser.parse(new FileInputStream(file));
        assertNotNull(document);
        Element root = document.getDocumentElement();
        assertEquals(file.getPath(), value, root.getAttribute("attr"));
        assertEquals(4, parser.getPosition(root).getLine());

        Attr attribute = root.getAttributeNode("attr");
        assertNotNull(attribute);
        Position position = parser.getPosition(attribute);
        assertNotNull(position);
        assertEquals(4, position.getLine());
        assertEquals(startAttrOffset, position.getOffset());

        file.delete();
    }

    public void testEncoding() throws Exception {
        checkEncoding("utf-8", false /*bom*/, true /*encoding*/);
        checkEncoding("UTF-8", false /*bom*/, true /*encoding*/);
        checkEncoding("UTF_16", false /*bom*/, true /*encoding*/);
        checkEncoding("UTF-16", false /*bom*/, true /*encoding*/);
        checkEncoding("UTF_16LE", false /*bom*/, true /*encoding*/);
        checkEncoding("UTF_32", false /*bom*/, true /*encoding*/);
        checkEncoding("UTF_32LE", false /*bom*/, true /*encoding*/);
        checkEncoding("windows-1252", false /*bom*/, true /*encoding*/);
        checkEncoding("MacRoman", false /*bom*/, true /*encoding*/);
        checkEncoding("ISO-8859-1", false /*bom*/, true /*encoding*/);
        checkEncoding("iso-8859-1", false /*bom*/, true /*encoding*/);

        // Try BOM's (with no encoding specified)
        checkEncoding("utf-8", true /*bom*/, false /*encoding*/);
        checkEncoding("UTF-8", true /*bom*/, false /*encoding*/);
        checkEncoding("UTF_16", true /*bom*/, false /*encoding*/);
        checkEncoding("UTF-16", true /*bom*/, false /*encoding*/);
        checkEncoding("UTF_16LE", true /*bom*/, false /*encoding*/);
        checkEncoding("UTF_32", true /*bom*/, false /*encoding*/);
        checkEncoding("UTF_32LE", true /*bom*/, false /*encoding*/);

        // Try default encodings (only defined for utf-8 and utf-16)
        checkEncoding("utf-8", false /*bom*/, false /*encoding*/);
        checkEncoding("UTF-8", false /*bom*/, false /*encoding*/);
        checkEncoding("UTF_16", false /*bom*/, false /*encoding*/);
        checkEncoding("UTF-16", false /*bom*/, false /*encoding*/);
        checkEncoding("UTF_16LE", false /*bom*/, false /*encoding*/);

        // Try BOM's (with explicit encoding specified)
        checkEncoding("utf-8", true /*bom*/, true /*encoding*/);
        checkEncoding("UTF-8", true /*bom*/, true /*encoding*/);
        checkEncoding("UTF_16", true /*bom*/, true /*encoding*/);
        checkEncoding("UTF-16", true /*bom*/, true /*encoding*/);
        checkEncoding("UTF_16LE", true /*bom*/, true /*encoding*/);
        checkEncoding("UTF_32", true /*bom*/, true /*encoding*/);
        checkEncoding("UTF_32LE", true /*bom*/, true /*encoding*/);
    }
}
