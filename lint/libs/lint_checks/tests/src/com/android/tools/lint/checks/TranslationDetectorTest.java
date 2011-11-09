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

@SuppressWarnings("javadoc")
public class TranslationDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new TranslationDetector();
    }

    public void testTranslation() throws Exception {
        TranslationDetector.COMPLETE_REGIONS = false;
        assertEquals(
            // Sample files from the Home app
            "values-cs: Error: Locale cs is missing translations for: menu_settings\n" +
            "values-de-rDE: Error: Locale de-rDE is missing translations for: menu_settings\n" +
            "values-de-rDE: Warning: Locale de-rDE is translating names not found in default locale: continue_skip_label\n" +
            "values-es-rUS: Error: Locale es-rUS is missing translations for: menu_settings\n" +
            "values-es-rUS: Warning: Locale es-rUS is translating names not found in default locale: security_questions\n" +
            "values-es: Error: Locale es is missing translations for: menu_settings\n" +
            "values-es: Warning: Locale es is translating names not found in default locale: security_questions\n" +
            "values-nl-rNL: Error: Locale nl-rNL is missing translations for: menu_settings, menu_wallpaper, show_all_apps",

            lintProject(
                 "res/values/strings.xml",
                 "res/values-cs/strings.xml",
                 "res/values-de-rDE/strings.xml",
                 "res/values-es/strings.xml",
                 "res/values-es-rUS/strings.xml",
                 "res/values-land/strings.xml",
                 "res/values-es/donottranslate.xml",
                 "res/values-nl-rNL/strings.xml"));
    }

    public void testTranslationWithCompleteRegions() throws Exception {
        TranslationDetector.COMPLETE_REGIONS = true;
        assertEquals(
            // Sample files from the Home app
            "values-cs: Error: Locale cs is missing translations for: menu_settings\n" +
            "values-de-rDE: Error: Locale de-rDE is missing translations for: menu_settings\n" +
            "values-de-rDE: Warning: Locale de-rDE is translating names not found in default locale: continue_skip_label\n" +
            "values-es-rUS: Error: Locale es-rUS is missing translations for: home_title, menu_settings, menu_wallpaper, show_all_apps... (1 more)\n" +
            "values-nl-rNL: Error: Locale nl-rNL is missing translations for: menu_settings, menu_wallpaper, show_all_apps",

            lintProject(
                 "res/values/strings.xml",
                 "res/values-cs/strings.xml",
                 "res/values-de-rDE/strings.xml",
                 "res/values-es-rUS/strings.xml",
                 "res/values-land/strings.xml",
                 "res/values-nl-rNL/strings.xml"));
    }

    public void testHandleBom() throws Exception {
        // This isn't really testing translation detection; it's just making sure that the
        // XML parser doesn't bomb on BOM bytes (byte order marker) at the beginning of
        // the XML document
        assertEquals(
            "No warnings.",
            lintProject(
                 "res/values-de/strings.xml"
            ));
    }
}
