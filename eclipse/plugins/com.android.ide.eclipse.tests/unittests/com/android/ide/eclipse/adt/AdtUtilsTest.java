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
package com.android.ide.eclipse.adt;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public class AdtUtilsTest extends TestCase {
    public void testEndsWithIgnoreCase() {
        assertTrue(AdtUtils.endsWithIgnoreCase("foo", "foo"));
        assertTrue(AdtUtils.endsWithIgnoreCase("foo", "Foo"));
        assertTrue(AdtUtils.endsWithIgnoreCase("foo", "foo"));
        assertTrue(AdtUtils.endsWithIgnoreCase("Barfoo", "foo"));
        assertTrue(AdtUtils.endsWithIgnoreCase("BarFoo", "foo"));
        assertTrue(AdtUtils.endsWithIgnoreCase("BarFoo", "foO"));

        assertFalse(AdtUtils.endsWithIgnoreCase("foob", "foo"));
        assertFalse(AdtUtils.endsWithIgnoreCase("foo", "fo"));
    }

    public void testStripWhitespace() {
        assertEquals("foo", AdtUtils.stripWhitespace("foo"));
        assertEquals("foobar", AdtUtils.stripWhitespace("foo bar"));
        assertEquals("foobar", AdtUtils.stripWhitespace("  foo bar  \n\t"));
    }

    public void testStripAllExtensions() {
        assertEquals("", AdtUtils.stripAllExtensions(""));
        assertEquals("foobar", AdtUtils.stripAllExtensions("foobar"));
        assertEquals("foobar", AdtUtils.stripAllExtensions("foobar.png"));
        assertEquals("foobar", AdtUtils.stripAllExtensions("foobar.9.png"));
        assertEquals(".profile", AdtUtils.stripAllExtensions(".profile"));
    }

    public void testStripLastExtension() {
        assertEquals("", AdtUtils.stripLastExtension(""));
        assertEquals("foobar", AdtUtils.stripLastExtension("foobar"));
        assertEquals("foobar", AdtUtils.stripLastExtension("foobar.png"));
        assertEquals("foobar.9", AdtUtils.stripLastExtension("foobar.9.png"));
        assertEquals(".profile", AdtUtils.stripLastExtension(".profile"));
    }

    public void testCapitalize() {
        assertEquals("UPPER", AdtUtils.capitalize("UPPER"));
        assertEquals("Lower", AdtUtils.capitalize("lower"));
        assertEquals("Capital", AdtUtils.capitalize("Capital"));
        assertEquals("CamelCase", AdtUtils.capitalize("camelCase"));
        assertEquals("", AdtUtils.capitalize(""));
        assertSame("Foo", AdtUtils.capitalize("Foo"));
        assertNull(null, AdtUtils.capitalize(null));
    }

    public void testEditDistance() {
        // editing kitten to sitting has edit distance 3:
        //   replace k with s
        //   replace e with i
        //   append g
        assertEquals(3, AdtUtils.editDistance("kitten", "sitting"));

        assertEquals(3, AdtUtils.editDistance("saturday", "sunday"));
        assertEquals(1, AdtUtils.editDistance("button", "bitton"));
        assertEquals(6, AdtUtils.editDistance("radiobutton", "bitton"));
    }
}
