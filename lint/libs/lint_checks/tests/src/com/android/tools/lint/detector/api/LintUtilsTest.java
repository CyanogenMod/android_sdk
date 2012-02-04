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

package com.android.tools.lint.detector.api;

import java.io.File;
import java.util.Arrays;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public class LintUtilsTest extends TestCase {
    public void testPrintList() throws Exception {
        assertEquals("foo, bar, baz",
                LintUtils.formatList(Arrays.asList("foo", "bar", "baz"), 3));
        assertEquals("foo, bar, baz",
                LintUtils.formatList(Arrays.asList("foo", "bar", "baz"), 5));

        assertEquals("foo, bar, baz... (3 more)",
                LintUtils.formatList(
                        Arrays.asList("foo", "bar", "baz", "4", "5", "6"), 3));
        assertEquals("foo... (5 more)",
                LintUtils.formatList(
                        Arrays.asList("foo", "bar", "baz", "4", "5", "6"), 1));
        assertEquals("foo, bar, baz",
                LintUtils.formatList(Arrays.asList("foo", "bar", "baz"), 0));
    }

    public void testEndsWith() throws Exception {
        assertTrue(LintUtils.endsWith("Foo", ""));
        assertTrue(LintUtils.endsWith("Foo", "o"));
        assertTrue(LintUtils.endsWith("Foo", "oo"));
        assertTrue(LintUtils.endsWith("Foo", "Foo"));
        assertTrue(LintUtils.endsWith("Foo", "FOO"));
        assertTrue(LintUtils.endsWith("Foo", "fOO"));

        assertFalse(LintUtils.endsWith("Foo", "f"));
    }

    public void testStartsWith() throws Exception {
        assertTrue(LintUtils.startsWith("FooBar", "Bar", 3));
        assertTrue(LintUtils.startsWith("FooBar", "BAR", 3));
        assertTrue(LintUtils.startsWith("FooBar", "Foo", 0));
        assertFalse(LintUtils.startsWith("FooBar", "Foo", 2));
    }

    public void testIsXmlFile() throws Exception {
        assertTrue(LintUtils.isXmlFile(new File("foo.xml")));
        assertTrue(LintUtils.isXmlFile(new File("foo.Xml")));
        assertTrue(LintUtils.isXmlFile(new File("foo.XML")));

        assertFalse(LintUtils.isXmlFile(new File("foo.png")));
        assertFalse(LintUtils.isXmlFile(new File("xml")));
        assertFalse(LintUtils.isXmlFile(new File("xml.png")));
    }

    public void testGetBasename() throws Exception {
        assertEquals("foo", LintUtils.getBaseName("foo.png"));
        assertEquals("foo", LintUtils.getBaseName("foo.9.png"));
        assertEquals(".foo", LintUtils.getBaseName(".foo"));
    }

    public void testEditDistance() {
        assertEquals(0, LintUtils.editDistance("kitten", "kitten"));

        // editing kitten to sitting has edit distance 3:
        //   replace k with s
        //   replace e with i
        //   append g
        assertEquals(3, LintUtils.editDistance("kitten", "sitting"));

        assertEquals(3, LintUtils.editDistance("saturday", "sunday"));
        assertEquals(1, LintUtils.editDistance("button", "bitton"));
        assertEquals(6, LintUtils.editDistance("radiobutton", "bitton"));
    }

    public void testCommonParen1() {
        assertEquals(new File("/a"), (LintUtils.getCommonParent(
                new File("/a/b/c/d/e"), new File("/a/c"))));
        assertEquals(new File("/a"), (LintUtils.getCommonParent(
                new File("/a/c"), new File("/a/b/c/d/e"))));

        assertEquals(new File("/"), LintUtils.getCommonParent(
                new File("/foo/bar"), new File("/bar/baz")));
        assertEquals(new File("/"), LintUtils.getCommonParent(
                new File("/foo/bar"), new File("/")));
        assertNull(LintUtils.getCommonParent(
               new File("C:\\Program Files"), new File("F:\\")));
        assertNull(LintUtils.getCommonParent(
                new File("C:/Program Files"), new File("F:/")));

        assertEquals(new File("/foo/bar/baz"), LintUtils.getCommonParent(
                new File("/foo/bar/baz"), new File("/foo/bar/baz")));
        assertEquals(new File("/foo/bar"), LintUtils.getCommonParent(
                new File("/foo/bar/baz"), new File("/foo/bar")));
        assertEquals(new File("/foo/bar"), LintUtils.getCommonParent(
                new File("/foo/bar/baz"), new File("/foo/bar/foo")));
        assertEquals(new File("/foo"), LintUtils.getCommonParent(
                new File("/foo/bar"), new File("/foo/baz")));
        assertEquals(new File("/foo"), LintUtils.getCommonParent(
                new File("/foo/bar"), new File("/foo/baz")));
        assertEquals(new File("/foo/bar"), LintUtils.getCommonParent(
                new File("/foo/bar"), new File("/foo/bar/baz")));
    }

    public void testCommonParent2() {
        assertEquals(new File("/"), LintUtils.getCommonParent(
                Arrays.asList(new File("/foo/bar"), new File("/bar/baz"))));
        assertEquals(new File("/"), LintUtils.getCommonParent(
                Arrays.asList(new File("/foo/bar"), new File("/"))));
        assertNull(LintUtils.getCommonParent(
                Arrays.asList(new File("C:\\Program Files"), new File("F:\\"))));
        assertNull(LintUtils.getCommonParent(
                Arrays.asList(new File("C:/Program Files"), new File("F:/"))));

        assertEquals(new File("/foo"), LintUtils.getCommonParent(
                Arrays.asList(new File("/foo/bar"), new File("/foo/baz"))));
        assertEquals(new File("/foo"), LintUtils.getCommonParent(
                Arrays.asList(new File("/foo/bar"), new File("/foo/baz"),
                        new File("/foo/baz/f"))));
        assertEquals(new File("/foo/bar"), LintUtils.getCommonParent(
                Arrays.asList(new File("/foo/bar"), new File("/foo/bar/baz"),
                        new File("/foo/bar/foo2/foo3"))));
    }

    public void testStripIdPrefix() throws Exception {
        assertEquals("foo", LintUtils.stripIdPrefix("@+id/foo"));
        assertEquals("foo", LintUtils.stripIdPrefix("@id/foo"));
        assertEquals("foo", LintUtils.stripIdPrefix("foo"));
    }

    public void testIdReferencesMatch() throws Exception {
        assertTrue(LintUtils.idReferencesMatch("@+id/foo", "@+id/foo"));
        assertTrue(LintUtils.idReferencesMatch("@id/foo", "@id/foo"));
        assertTrue(LintUtils.idReferencesMatch("@id/foo", "@+id/foo"));
        assertTrue(LintUtils.idReferencesMatch("@+id/foo", "@id/foo"));

        assertFalse(LintUtils.idReferencesMatch("@+id/foo", "@+id/bar"));
        assertFalse(LintUtils.idReferencesMatch("@id/foo", "@+id/bar"));
        assertFalse(LintUtils.idReferencesMatch("@+id/foo", "@id/bar"));
        assertFalse(LintUtils.idReferencesMatch("@+id/foo", "@+id/bar"));

        assertFalse(LintUtils.idReferencesMatch("@+id/foo", "@+id/foo1"));
        assertFalse(LintUtils.idReferencesMatch("@id/foo", "@id/foo1"));
        assertFalse(LintUtils.idReferencesMatch("@id/foo", "@+id/foo1"));
        assertFalse(LintUtils.idReferencesMatch("@+id/foo", "@id/foo1"));

        assertFalse(LintUtils.idReferencesMatch("@+id/foo1", "@+id/foo"));
        assertFalse(LintUtils.idReferencesMatch("@id/foo1", "@id/foo"));
        assertFalse(LintUtils.idReferencesMatch("@id/foo1", "@+id/foo"));
        assertFalse(LintUtils.idReferencesMatch("@+id/foo1", "@id/foo"));
    }
}