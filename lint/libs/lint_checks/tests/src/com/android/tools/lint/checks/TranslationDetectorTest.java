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

import java.util.Arrays;

@SuppressWarnings("javadoc")
public class TranslationDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new TranslationDetector();
    }

    public void testTranslation() throws Exception {
        assertEquals(
            // Sample files from the Home app
            "values-es-rUS: Error: Language es-rUS is missing translations for the " +
                "names menu_settings\n" +
            "values-nl-rNL: Error: Language nl-rNL is missing translations for the names " +
                "menu_settings, menu_wallpaper, show_all_apps\n" +
            "values-de-rDE: Error: Language de-rDE is missing translations for the names " +
                "menu_settings\n" +
            "values-cs: Error: Language cs is missing translations for the names menu_settings",

            lint("values/strings.xml",
                 "values-cs/strings.xml",
                 "values-de-rDE/strings.xml",
                 "values-es-rUS/strings.xml",
                 "values-land/strings.xml",
                 "values-nl-rNL/strings.xml"));
    }

    public void testPrintList() throws Exception {
        assertEquals("foo, bar, baz",
                TranslationDetector.formatList(Arrays.asList("foo", "bar", "baz"), 3));
        assertEquals("foo, bar, baz",
                TranslationDetector.formatList(Arrays.asList("foo", "bar", "baz"), 5));

        assertEquals("foo, bar, baz... (3 more)",
                TranslationDetector.formatList(
                        Arrays.asList("foo", "bar", "baz", "4", "5", "6"), 3));
        assertEquals("foo... (5 more)",
                TranslationDetector.formatList(
                        Arrays.asList("foo", "bar", "baz", "4", "5", "6"), 1));
    }
}
