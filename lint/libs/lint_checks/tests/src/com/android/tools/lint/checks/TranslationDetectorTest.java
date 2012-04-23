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

    @Override
    protected boolean includeParentPath() {
        return true;
    }

    public void testTranslation() throws Exception {
        TranslationDetector.COMPLETE_REGIONS = false;
        assertEquals(
            // Sample files from the Home app
            "values-cs/arrays.xml:3: Warning: \"security_questions\" is translated here but not found in default locale\n" +
            "=> values-es/strings.xml:12: Also translated here\n" +
            "values-de-rDE/strings.xml:11: Warning: \"continue_skip_label\" is translated here but not found in default locale\n" +
            "values/strings.xml:20: Error: \"show_all_apps\" is not translated in nl-rNL\n" +
            "values/strings.xml:23: Error: \"menu_wallpaper\" is not translated in nl-rNL\n" +
            "values/strings.xml:25: Error: \"menu_settings\" is not translated in cs, de-rDE, es, es-rUS, nl-rNL",

            lintProject(
                 "res/values/strings.xml",
                 "res/values-cs/strings.xml",
                 "res/values-de-rDE/strings.xml",
                 "res/values-es/strings.xml",
                 "res/values-es-rUS/strings.xml",
                 "res/values-land/strings.xml",
                 "res/values-cs/arrays.xml",
                 "res/values-es/donottranslate.xml",
                 "res/values-nl-rNL/strings.xml"));
    }

    public void testTranslationWithCompleteRegions() throws Exception {
        TranslationDetector.COMPLETE_REGIONS = true;
        assertEquals(
            // Sample files from the Home app
            "values-de-rDE/strings.xml:11: Warning: \"continue_skip_label\" is translated here but not found in default locale\n" +
            "values/strings.xml:19: Error: \"home_title\" is not translated in es-rUS\n" +
            "values/strings.xml:20: Error: \"show_all_apps\" is not translated in es-rUS, nl-rNL\n" +
            "values/strings.xml:23: Error: \"menu_wallpaper\" is not translated in es-rUS, nl-rNL\n" +
            "values/strings.xml:25: Error: \"menu_settings\" is not translated in cs, de-rDE, es-rUS, nl-rNL\n" +
            "values/strings.xml:29: Error: \"wallpaper_instructions\" is not translated in es-rUS",

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

    public void testTranslatedArrays() throws Exception {
        TranslationDetector.COMPLETE_REGIONS = true;
        assertEquals(
            "No warnings.",

            lintProject(
                 "res/values/translatedarrays.xml",
                 "res/values-cs/translatedarrays.xml"));
    }

    public void testTranslationSuppresss() throws Exception {
        TranslationDetector.COMPLETE_REGIONS = false;
        assertEquals(
            "No warnings.",

            lintProject(
                    "res/values/strings_ignore.xml=>res/values/strings.xml",
                    "res/values-es/strings_ignore.xml=>res/values-es/strings.xml",
                    "res/values-nl-rNL/strings.xml=>res/values-nl-rNL/strings.xml"));
    }

    public void testMixedTranslationArrays() throws Exception {
        // See issue http://code.google.com/p/android/issues/detail?id=29263
        assertEquals(
                "No warnings.",

                lintProject(
                        "res/values/strings3.xml=>res/values/strings.xml",
                        "res/values-fr/strings.xml=>res/values-fr/strings.xml"));
    }
}
