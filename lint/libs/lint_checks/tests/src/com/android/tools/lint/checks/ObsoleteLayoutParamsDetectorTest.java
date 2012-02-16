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
public class ObsoleteLayoutParamsDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new ObsoleteLayoutParamsDetector();
    }

    public void test() throws Exception {
        assertEquals(
            "wrongparams.xml:11: Warning: Invalid layout param in a FrameLayout: layout_weight\n" +
            "wrongparams.xml:23: Warning: Invalid layout param in a LinearLayout: layout_alignParentLeft\n" +
            "wrongparams.xml:24: Warning: Invalid layout param in a LinearLayout: layout_alignParentTop\n" +
            "wrongparams.xml:33: Warning: Invalid layout param in a LinearLayout: layout_alignBottom\n" +
            "wrongparams.xml:34: Warning: Invalid layout param in a LinearLayout: layout_toRightOf\n" +
            "wrongparams.xml:42: Warning: Invalid layout param in a LinearLayout: layout_alignLeft\n" +
            "wrongparams.xml:43: Warning: Invalid layout param in a LinearLayout: layout_below",

            lintProject("res/layout/wrongparams.xml"));
    }

    public void test2() throws Exception {
        // Test <merge> and custom view handling

        assertEquals(
            "No warnings.",

            lintProject("res/layout/wrongparams2.xml"));
    }

    public void test3() throws Exception {
        // Test includes across files (wrong layout param on root element)
        assertEquals(
            "wrongparams3.xml:5: Warning: Invalid layout param 'layout_alignParentTop' (included from within a LinearLayout in layout/wrongparams4.xml)",

            lintProject("res/layout/wrongparams4.xml", "res/layout/wrongparams3.xml"));
    }

    public void test4() throws Exception {
        // Test includes with a <merge> (wrong layout param on child of root merge element)
        assertEquals(
            "wrongparams5.xml:15: Warning: Invalid layout param 'layout_alignParentLeft' (included from within a LinearLayout in layout/wrongparams6.xml)\n" +
            "wrongparams5.xml:8: Warning: Invalid layout param 'layout_alignParentTop' (included from within a LinearLayout in layout/wrongparams6.xml)",

            lintProject("res/layout/wrongparams5.xml", "res/layout/wrongparams6.xml"));
    }

    public void testIgnore() throws Exception {
        assertEquals(
             // Ignoring all but one of the warnings
            "wrongparams.xml:12: Warning: Invalid layout param in a FrameLayout: layout_weight",

            lintProject("res/layout/wrongparams_ignore.xml=>res/layout/wrongparams.xml"));
    }
}
