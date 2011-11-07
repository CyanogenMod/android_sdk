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
    }

    public void testIsXmlFile() throws Exception {
        assertTrue(LintUtils.isXmlFile(new File("foo.xml")));
        assertTrue(LintUtils.isXmlFile(new File("foo.Xml")));
        assertTrue(LintUtils.isXmlFile(new File("foo.XML")));

        assertFalse(LintUtils.isXmlFile(new File("foo.png")));
        assertFalse(LintUtils.isXmlFile(new File("xml")));
        assertFalse(LintUtils.isXmlFile(new File("xml.png")));
    }
}