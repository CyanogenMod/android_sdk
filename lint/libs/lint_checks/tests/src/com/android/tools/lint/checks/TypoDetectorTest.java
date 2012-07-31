/*
 * Copyright (C) 2012 The Android Open Source Project
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

import java.util.Arrays;

@SuppressWarnings("javadoc")
public class TypoDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new TypoDetector();
    }

    public void testPlainValues() throws Exception {
        assertEquals(
            "strings.xml:10: Warning: \"throught\" is a common misspelling; did you mean \"thought\" or \"through\" or \"throughout\" ?\n" +
            "strings.xml:12: Warning: \"Seach\" is a common misspelling; did you mean \"Search\" ?\n" +
            "strings.xml:16: Warning: \"Tuscon\" is a common misspelling; did you mean \"Tucson\" ?\n" +
            "strings.xml:20: Warning: \"Ok\" is usually capitalized as \"OK\"\n" +
            "strings.xml:6: Warning: \"Andriod\" is a common misspelling; did you mean \"Android\" ?\n" +
            "strings.xml:6: Warning: \"activites\" is a common misspelling; did you mean \"activities\" ?\n" +
            "strings.xml:8: Warning: \"Cmoputer\" is a common misspelling; did you mean \"Computer\" ?",
            lintProject("res/values/typos.xml=>res/values/strings.xml"));
    }

    public void testEnLanguage() throws Exception {
        assertEquals(
            "strings-en.xml:10: Warning: \"throught\" is a common misspelling; did you mean \"thought\" or \"through\" or \"throughout\" ?\n" +
            "strings-en.xml:12: Warning: \"Seach\" is a common misspelling; did you mean \"Search\" ?\n" +
            "strings-en.xml:16: Warning: \"Tuscon\" is a common misspelling; did you mean \"Tucson\" ?\n" +
            "strings-en.xml:20: Warning: \"Ok\" is usually capitalized as \"OK\"\n" +
            "strings-en.xml:6: Warning: \"Andriod\" is a common misspelling; did you mean \"Android\" ?\n" +
            "strings-en.xml:6: Warning: \"activites\" is a common misspelling; did you mean \"activities\" ?\n" +
            "strings-en.xml:8: Warning: \"Cmoputer\" is a common misspelling; did you mean \"Computer\" ?",
            lintProject("res/values/typos.xml=>res/values-en-rUS/strings-en.xml"));
    }

    public void testNorwegian() throws Exception {
        // UTF-8 handling
        assertEquals(
            "typos.xml:10: Warning: \"altid\" is a common misspelling; did you mean \"alltid\" ?\n" +
            "typos.xml:12: Warning: \"Altid\" is a common misspelling; did you mean \"Alltid\" ?\n" +
            "typos.xml:18: Warning: \"karri¾re\" is a common misspelling; did you mean \"karrire\" ?\n" +
            "typos.xml:6: Warning: \"Andriod\" is a common misspelling; did you mean \"Android\" ?\n" +
            "typos.xml:6: Warning: \"morro\" is a common misspelling; did you mean \"moro\" ?\n" +
            "typos.xml:8: Warning: \"Parallel\" is a common misspelling; did you mean \"Parallell\" ?",
            lintProject("res/values-nb/typos.xml"));
    }

    public void testOk() throws Exception {
        assertEquals(
            "No warnings.",
            lintProject("res/values/typos.xml=>res/values-de/strings.xml"));
    }

    public void testGetReplacements() {
        String s = "\"throught\" is a common misspelling; did you mean \"thought\" or " +
                   "\"through\" or \"throughout\" ?\n";
        assertEquals("throught", TypoDetector.getTypo(s));
        assertEquals(Arrays.asList("thought", "through", "throughout"),
                TypoDetector.getSuggestions(s));

    }
}
