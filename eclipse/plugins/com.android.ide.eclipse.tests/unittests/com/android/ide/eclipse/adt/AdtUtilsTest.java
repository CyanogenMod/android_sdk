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

import com.google.common.collect.Iterables;

import java.util.Arrays;
import java.util.Locale;

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

    public void testStartsWithIgnoreCase() {
        assertTrue(AdtUtils.startsWithIgnoreCase("foo", "foo"));
        assertTrue(AdtUtils.startsWithIgnoreCase("foo", "Foo"));
        assertTrue(AdtUtils.startsWithIgnoreCase("foo", "foo"));
        assertTrue(AdtUtils.startsWithIgnoreCase("barfoo", "bar"));
        assertTrue(AdtUtils.startsWithIgnoreCase("BarFoo", "bar"));
        assertTrue(AdtUtils.startsWithIgnoreCase("BarFoo", "bAr"));

        assertFalse(AdtUtils.startsWithIgnoreCase("bfoo", "foo"));
        assertFalse(AdtUtils.startsWithIgnoreCase("fo", "foo"));
    }

    public void testStartsWith() {
        assertTrue(AdtUtils.startsWith("foo", 0, "foo"));
        assertTrue(AdtUtils.startsWith("foo", 0, "Foo"));
        assertTrue(AdtUtils.startsWith("Foo", 0, "foo"));
        assertTrue(AdtUtils.startsWith("aFoo", 1, "foo"));

        assertFalse(AdtUtils.startsWith("aFoo", 0, "foo"));
        assertFalse(AdtUtils.startsWith("aFoo", 2, "foo"));
    }

    public void testEndsWith() {
        assertTrue(AdtUtils.endsWith("foo", "foo"));
        assertTrue(AdtUtils.endsWith("foobar", "obar"));
        assertTrue(AdtUtils.endsWith("foobar", "bar"));
        assertTrue(AdtUtils.endsWith("foobar", "ar"));
        assertTrue(AdtUtils.endsWith("foobar", "r"));
        assertTrue(AdtUtils.endsWith("foobar", ""));

        assertTrue(AdtUtils.endsWith(new StringBuilder("foobar"), "bar"));
        assertTrue(AdtUtils.endsWith(new StringBuilder("foobar"), new StringBuffer("obar")));
        assertTrue(AdtUtils.endsWith("foobar", new StringBuffer("obar")));

        assertFalse(AdtUtils.endsWith("foo", "fo"));
        assertFalse(AdtUtils.endsWith("foobar", "Bar"));
        assertFalse(AdtUtils.endsWith("foobar", "longfoobar"));
    }

    public void testEndsWith2() {
        assertTrue(AdtUtils.endsWith("foo", "foo".length(), "foo"));
        assertTrue(AdtUtils.endsWith("foo", "fo".length(), "fo"));
        assertTrue(AdtUtils.endsWith("foo", "f".length(), "f"));
    }

    public void testStripWhitespace() {
        assertEquals("foo", AdtUtils.stripWhitespace("foo"));
        assertEquals("foobar", AdtUtils.stripWhitespace("foo bar"));
        assertEquals("foobar", AdtUtils.stripWhitespace("  foo bar  \n\t"));
    }

    public void testExtractClassName() {
        assertEquals("Foo", AdtUtils.extractClassName("foo"));
        assertEquals("Foobar", AdtUtils.extractClassName("foo bar"));
        assertEquals("JavasTypeSystem", AdtUtils.extractClassName("Java's Type System"));
        assertEquals("Foo", AdtUtils.extractClassName("1foo "));
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

    public void testCamelCaseToUnderlines() {
        assertEquals("", AdtUtils.camelCaseToUnderlines(""));
        assertEquals("foo", AdtUtils.camelCaseToUnderlines("foo"));
        assertEquals("foo", AdtUtils.camelCaseToUnderlines("Foo"));
        assertEquals("foo_bar", AdtUtils.camelCaseToUnderlines("FooBar"));
        assertEquals("test_xml", AdtUtils.camelCaseToUnderlines("testXML"));
        assertEquals("test_foo", AdtUtils.camelCaseToUnderlines("testFoo"));
    }

    public void testUnderlinesToCamelCase() {
        assertEquals("", AdtUtils.underlinesToCamelCase(""));
        assertEquals("", AdtUtils.underlinesToCamelCase("_"));
        assertEquals("Foo", AdtUtils.underlinesToCamelCase("foo"));
        assertEquals("FooBar", AdtUtils.underlinesToCamelCase("foo_bar"));
        assertEquals("FooBar", AdtUtils.underlinesToCamelCase("foo__bar"));
        assertEquals("Foo", AdtUtils.underlinesToCamelCase("foo_"));
    }

    public void testStripSuffix() {
        assertEquals("Foo", AdtUtils.stripSuffix("Foo", ""));
        assertEquals("Fo", AdtUtils.stripSuffix("Foo", "o"));
        assertEquals("F", AdtUtils.stripSuffix("Fo", "o"));
        assertEquals("", AdtUtils.stripSuffix("Foo", "Foo"));
        assertEquals("LinearLayout_Layout",
                AdtUtils.stripSuffix("LinearLayout_LayoutParams", "Params"));
        assertEquals("Foo", AdtUtils.stripSuffix("Foo", "Bar"));
    }

    public void testSplitPath() throws Exception {
        assertTrue(Arrays.equals(new String[] { "/foo", "/bar", "/baz" },
                Iterables.toArray(AdtUtils.splitPath("/foo:/bar:/baz"), String.class)));

        assertTrue(Arrays.equals(new String[] { "/foo", "/bar" },
                Iterables.toArray(AdtUtils.splitPath("/foo;/bar"), String.class)));

        assertTrue(Arrays.equals(new String[] { "/foo", "/bar:baz" },
                Iterables.toArray(AdtUtils.splitPath("/foo;/bar:baz"), String.class)));

        assertTrue(Arrays.equals(new String[] { "\\foo\\bar", "\\bar\\foo" },
                Iterables.toArray(AdtUtils.splitPath("\\foo\\bar;\\bar\\foo"), String.class)));

        assertTrue(Arrays.equals(new String[] { "${sdk.dir}\\foo\\bar", "\\bar\\foo" },
                Iterables.toArray(AdtUtils.splitPath("${sdk.dir}\\foo\\bar;\\bar\\foo"),
                        String.class)));

        assertTrue(Arrays.equals(new String[] { "${sdk.dir}/foo/bar", "/bar/foo" },
                Iterables.toArray(AdtUtils.splitPath("${sdk.dir}/foo/bar:/bar/foo"),
                        String.class)));

        assertTrue(Arrays.equals(new String[] { "C:\\foo", "/bar" },
                Iterables.toArray(AdtUtils.splitPath("C:\\foo:/bar"), String.class)));
    }

    public void testFormatFloatValue() throws Exception {
        assertEquals("1", AdtUtils.formatFloatAttribute(1.0f));
        assertEquals("2", AdtUtils.formatFloatAttribute(2.0f));
        assertEquals("1.50", AdtUtils.formatFloatAttribute(1.5f));
        assertEquals("1.50", AdtUtils.formatFloatAttribute(1.50f));
        assertEquals("1.51", AdtUtils.formatFloatAttribute(1.51f));
        assertEquals("1.51", AdtUtils.formatFloatAttribute(1.514542f));
        assertEquals("1.52", AdtUtils.formatFloatAttribute(1.516542f));
        assertEquals("-1.51", AdtUtils.formatFloatAttribute(-1.51f));
        assertEquals("-1", AdtUtils.formatFloatAttribute(-1f));
    }

    public void testFormatFloatValueLocale() throws Exception {
        // Ensure that the layout float values aren't affected by
        // locale settings, like using commas instead of of periods
        Locale originalDefaultLocale = Locale.getDefault();

        try {
            Locale.setDefault(Locale.FRENCH);

            // Ensure that this is a locale which uses a comma instead of a period:
            assertEquals("5,24", String.format("%.2f", 5.236f));

            // Ensure that the formatFloatAttribute is immune
            assertEquals("1.50", AdtUtils.formatFloatAttribute(1.5f));
        } finally {
            Locale.setDefault(originalDefaultLocale);
        }
    }
}
