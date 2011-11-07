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

package com.android.tools.lint;

import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Position;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.EnumSet;

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
        Project project = new Project(null, file.getParentFile(), file.getParentFile());
        Context context = new Context(new Main(), project, file, EnumSet.of(Scope.RESOURCE_FILE));
        Document document = parser.parse(context);
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
        Position start = parser.getStartPosition(context, attr);
        Position end = parser.getEndPosition(context, attr);
        assertEquals(2, start.getLine());
        assertEquals(xml.indexOf("android:layout_width"), start.getOffset());
        assertEquals(2, end.getLine());
        String target = "android:layout_width=\"match_parent\"";
        assertEquals(xml.indexOf(target) + target.length(), end.getOffset());

        // Check element positions
        Element button = (Element) buttons.item(0);
        start = parser.getStartPosition(context, button);
        end = parser.getEndPosition(context, button);
        assertEquals(6, start.getLine());
        assertEquals(xml.indexOf("<Button"), start.getOffset());
        assertEquals(xml.indexOf("/>") + 2, end.getOffset());
        assertEquals(10, end.getLine());
        int button1End = end.getOffset();


        Element button2 = (Element) buttons.item(1);
        start = parser.getStartPosition(context, button2);
        end = parser.getEndPosition(context, button2);
        assertEquals(12, start.getLine());
        assertEquals(xml.indexOf("<Button", button1End), start.getOffset());
        assertEquals(xml.indexOf("/>", start.getOffset()) + 2, end.getOffset());
        assertEquals(16, end.getLine());

        parser.dispose(context);

        file.delete();
    }
}
