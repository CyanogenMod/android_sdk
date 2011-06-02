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
package com.android.ide.eclipse.adt.internal.editors.layout.gle2;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Arrays;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import junit.framework.TestCase;

public class DomUtilitiesTest extends TestCase {

    public void testToXmlAttributeValue() throws Exception {
        assertEquals("", DomUtilities.toXmlAttributeValue(""));
        assertEquals("foo", DomUtilities.toXmlAttributeValue("foo"));
        assertEquals("foo<bar", DomUtilities.toXmlAttributeValue("foo<bar"));

        assertEquals("&quot;", DomUtilities.toXmlAttributeValue("\""));
        assertEquals("&apos;", DomUtilities.toXmlAttributeValue("'"));
        assertEquals("foo&quot;b&apos;&apos;ar",
                DomUtilities.toXmlAttributeValue("foo\"b''ar"));
    }

    public void testIsEquivalent() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document1 = builder.newDocument();
        Document document2 = builder.newDocument();
        document1.appendChild(document1.createElement("root"));
        document2.appendChild(document2.createElement("root"));

        assertFalse(DomUtilities.isEquivalent(null, null));
        Element root1 = document1.getDocumentElement();
        assertFalse(DomUtilities.isEquivalent(null, root1));
        Element root2 = document2.getDocumentElement();
        assertFalse(DomUtilities.isEquivalent(root2, null));
        assertTrue(DomUtilities.isEquivalent(root1, root2));

        root1.appendChild(document1.createTextNode("    "));
        // Differences in text are NOT significant!
        assertTrue(DomUtilities.isEquivalent(root1, root2));
        root2.appendChild(document2.createTextNode("    "));
        assertTrue(DomUtilities.isEquivalent(root1, root2));

        Element foo1 = document1.createElement("foo");
        Element foo2 = document2.createElement("foo");
        root1.appendChild(foo1);
        assertFalse(DomUtilities.isEquivalent(root1, root2));
        root2.appendChild(foo2);
        assertTrue(DomUtilities.isEquivalent(root1, root2));

        root1.appendChild(document1.createElement("bar"));
        assertFalse(DomUtilities.isEquivalent(root1, root2));
        root2.appendChild(document2.createElement("bar"));
        assertTrue(DomUtilities.isEquivalent(root1, root2));

        // Add attributes in opposite order
        foo1.setAttribute("attribute1", "value1");
        foo1.setAttribute("attribute2", "value2");
        assertFalse(DomUtilities.isEquivalent(root1, root2));
        foo2.setAttribute("attribute2", "value2");
        foo2.setAttribute("attribute1", "valueWrong");
        assertFalse(DomUtilities.isEquivalent(root1, root2));
        foo2.setAttribute("attribute1", "value1");
        assertTrue(DomUtilities.isEquivalent(root1, root2));

        // TODO - test different tag names
        // TODO - test different name spaces!
    }

    public void testIsContiguous() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();
        document.appendChild(document.createElement("root"));
        Element root = document.getDocumentElement();
        root.appendChild(document.createTextNode("    "));
        Element foo = document.createElement("foo");
        root.appendChild(foo);
        root.appendChild(document.createTextNode("    "));
        Element bar = document.createElement("bar");
        root.appendChild(bar);
        Element baz = document.createElement("baz");
        root.appendChild(baz);

        assertTrue(DomUtilities.isContiguous(Arrays.asList(foo)));
        assertTrue(DomUtilities.isContiguous(Arrays.asList(foo, bar)));
        assertTrue(DomUtilities.isContiguous(Arrays.asList(foo, bar, baz)));
        assertTrue(DomUtilities.isContiguous(Arrays.asList(foo, bar, baz)));
        assertTrue(DomUtilities.isContiguous(Arrays.asList(bar, baz, foo)));
        assertTrue(DomUtilities.isContiguous(Arrays.asList(baz, bar, foo)));
        assertTrue(DomUtilities.isContiguous(Arrays.asList(baz, foo, bar)));

        assertFalse(DomUtilities.isContiguous(Arrays.asList(foo, baz)));
        assertFalse(DomUtilities.isContiguous(Arrays.asList(root, baz)));
    }
}
