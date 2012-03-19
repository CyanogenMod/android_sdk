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

package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Detector;

import java.util.HashSet;
import java.util.Set;

@SuppressWarnings("javadoc")
public class StringFormatDetectorTest  extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new StringFormatDetector();
    }

    public void testAll() throws Exception {
        assertEquals(
            "formatstrings.xml:5: Warning: Formatting string 'missing' is not referencing numbered arguments [1, 2]\n" +
            "pkg/StringFormatActivity.java:13: Error: Wrong argument type for formatting argument '#1' in hello: conversion is 'd', received String\n" +
            "=> values-es/formatstrings.xml:3: Conflicting argument declaration here\n" +
            "pkg/StringFormatActivity.java:15: Error: Wrong argument count, format string hello2 requires 3 but format call supplies 2\n" +
            "=> values-es/formatstrings.xml:4: This definition requires 3 arguments\n" +
            "values-es/formatstrings.xml:3: Error: Inconsistent formatting types for argument #1 in format string hello ('%1$d'): Found both 's' and 'd' (in values/formatstrings.xml)\n" +
            "=> values/formatstrings.xml:3: Conflicting argument type here\n" +
            "values-es/formatstrings.xml:4: Warning: Inconsistent number of arguments in formatting string hello2; found both 2 and 3\n" +
            "=> values/formatstrings.xml:4: Conflicting number of arguments here",

            lintProject(
                    "res/values/formatstrings.xml",
                    "res/values-es/formatstrings.xml",
                    // Java files must be renamed in source tree
                    "src/test/pkg/StringFormatActivity.java.txt=>src/test/pkg/StringFormatActivity.java"
                ));
    }

    public void testArgCount() {
        assertEquals(3, StringFormatDetector.getFormatArgumentCount(
                "First: %1$s, Second %2$s, Third %3$s", null));
        assertEquals(11, StringFormatDetector.getFormatArgumentCount(
                "Skipping stuff: %11$s", null));
        assertEquals(1, StringFormatDetector.getFormatArgumentCount(
                "First: %1$s, Skip \\%2$s", null));
        assertEquals(1, StringFormatDetector.getFormatArgumentCount(
                "First: %s, Skip \\%s", null));

        Set<Integer> indices = new HashSet<Integer>();
        assertEquals(11, StringFormatDetector.getFormatArgumentCount(
                "Skipping stuff: %2$d %11$s", indices));
        assertEquals(2, indices.size());
        assertTrue(indices.contains(2));
        assertTrue(indices.contains(11));
    }

    public void testArgType() {
        assertEquals("s", StringFormatDetector.getFormatArgumentType(
                "First: %1$s, Second %2$s, Third %3$s", 1));
        assertEquals("d", StringFormatDetector.getFormatArgumentType(
                "First: %1$s, Second %2$-5d, Third %3$s", 2));
        assertEquals("s", StringFormatDetector.getFormatArgumentType(
                "Skipping stuff: %11$s",11));
        assertEquals("d", StringFormatDetector.getFormatArgumentType(
                "First: %1$s, Skip \\%2$s, Value=%2$d", 2));
    }

    public void testWrongSyntax() throws Exception {
        assertEquals(
            "No warnings.",

            lintProject(
                    "res/values/formatstrings2.xml"
                ));
    }

    public void testDateStrings() throws Exception {
        assertEquals(
            "No warnings.",

            lintProject(
                    "res/values/formatstrings-version1.xml=>res/values-tl/donottranslate-cldr.xml",
                    "res/values/formatstrings-version2.xml=>res/values/donottranslate-cldr.xml"
                ));
    }

    public void testUa() throws Exception {
        assertEquals(
            "No warnings.",

            lintProject(
                    "res/values/formatstrings-version1.xml=>res/values-tl/donottranslate-cldr.xml",
                    "src/test/pkg/StringFormat2.java.txt=>src/test/pkg/StringFormat2.java"
                ));
    }

    public void testSuppressed() throws Exception {
        assertEquals(
            "No warnings.",

            lintProject(
                    "res/values/formatstrings_ignore.xml=>res/values/formatstrings.xml",
                    "res/values-es/formatstrings_ignore.xml=>res/values-es/formatstrings.xml",
                    "src/test/pkg/StringFormatActivity_ignore.java.txt=>src/test/pkg/StringFormatActivity.java"
                ));
    }

    public void testIssue27108() throws Exception {
        assertEquals(
            "No warnings.",

            lintProject("res/values/formatstrings3.xml"));
    }
}
