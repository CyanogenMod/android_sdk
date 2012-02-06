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
import com.android.tools.lint.detector.api.Issue;

@SuppressWarnings("javadoc")
public class ButtonDetectorTest extends AbstractCheckTest {
    private static Issue sTestIssue;

    @Override
    protected boolean isEnabled(Issue issue) {
        return super.isEnabled(issue) && sTestIssue == null || issue == sTestIssue;

    }

    @Override
    protected Detector getDetector() {
        return new ButtonDetector();
    }

    public void testButtonOrder() throws Exception {
        sTestIssue = ButtonDetector.ORDER;
        assertEquals(
            "buttonbar.xml:124: Warning: OK button should be on the right (was \"OK | Cancel\", should be \"Cancel | OK\")\n" +
            "buttonbar.xml:12: Warning: OK button should be on the right (was \"OK | Cancel\", should be \"Cancel | OK\")\n" +
            "buttonbar.xml:140: Warning: OK button should be on the right (was \"Ok | CANCEL\", should be \"CANCEL | Ok\")\n" +
            "buttonbar.xml:156: Warning: OK button should be on the right (was \"OK | Abort\", should be \"Abort | OK\")\n" +
            "buttonbar.xml:177: Warning: Cancel button should be on the left (was \"Send | Cancel\", should be \"Cancel | Send\")\n" +
            "buttonbar.xml:44: Warning: OK button should be on the right (was \"OK | Cancel\", should be \"Cancel | OK\")\n" +
            "buttonbar.xml:92: Warning: OK button should be on the right (was \"OK | Cancel\", should be \"Cancel | OK\")",

            lintProject("res/layout/buttonbar.xml", "res/values/buttonbar-values.xml"));
    }

    public void testButtonOrderRelativeLayout() throws Exception {
        sTestIssue = ButtonDetector.ORDER;
        assertEquals(
            "No warnings.",

            lintProject("res/layout/buttonbar2.xml", "res/values/buttonbar-values.xml"));
    }

    public void testButtonOrderRelativeLayout2() throws Exception {
        sTestIssue = ButtonDetector.ORDER;
        assertEquals(
            "buttonbar3.xml:27: Warning: Cancel button should be on the left",

            lintProject("res/layout/buttonbar3.xml", "res/values/buttonbar-values.xml"));
    }

    public void testButtonOrderRelativeLayout3() throws Exception {
        sTestIssue = ButtonDetector.ORDER;
        assertEquals(
            "No warnings.",

            lintProject("res/layout/buttonbar4.xml", "res/values/buttonbar-values.xml"));
    }


    public void testCase() throws Exception {
        sTestIssue = ButtonDetector.CASE;
        assertEquals(
            "buttonbar-values.xml:10: Warning: The standard Android way to capitalize CANCEL is \"Cancel\" (tip: use @android:string/ok instead)\n" +
            "buttonbar-values.xml:9: Warning: The standard Android way to capitalize Ok is \"OK\" (tip: use @android:string/ok instead)",

            lintProject("res/layout/buttonbar.xml", "res/values/buttonbar-values.xml"));
    }

    public void testBack() throws Exception {
        sTestIssue = ButtonDetector.BACKBUTTON;
        assertEquals(
            "buttonbar.xml:183: Warning: Back buttons are not standard on Android; see design guide's navigation section",

            lintProject("res/layout/buttonbar.xml", "res/values/buttonbar-values.xml"));
    }

    public void testOldApp() throws Exception {
        // Target SDK < 14 - no warnings on button order
        sTestIssue = ButtonDetector.ORDER;
        assertEquals(
                "No warnings.",

        lintProject(
                "minsdk5targetsdk9.xml=>AndroidManifest.xml",
                "res/layout/buttonbar.xml",
                "res/values/buttonbar-values.xml"));
    }

    public void testEnglishLocales() throws Exception {
        sTestIssue = ButtonDetector.ORDER;
        assertEquals(
                "buttonbar.xml:124: Warning: OK button should be on the right (was \"OK | Cancel\", should be \"Cancel | OK\")\n" +
                "buttonbar.xml:12: Warning: OK button should be on the right (was \"OK | Cancel\", should be \"Cancel | OK\")\n" +
                "buttonbar.xml:140: Warning: OK button should be on the right (was \"Ok | CANCEL\", should be \"CANCEL | Ok\")\n" +
                "buttonbar.xml:156: Warning: OK button should be on the right (was \"OK | Abort\", should be \"Abort | OK\")\n" +
                "buttonbar.xml:177: Warning: Cancel button should be on the left (was \"Send | Cancel\", should be \"Cancel | Send\")\n" +
                "buttonbar.xml:44: Warning: OK button should be on the right (was \"OK | Cancel\", should be \"Cancel | OK\")\n" +
                "buttonbar.xml:92: Warning: OK button should be on the right (was \"OK | Cancel\", should be \"Cancel | OK\")",

                lintProject("res/layout/buttonbar.xml=>res/layout-en-rGB/buttonbar.xml",
                        "res/values/buttonbar-values.xml=>res/values-en-rGB/buttonbar-values.xml"));
    }

    public void testOtherLocales() throws Exception {
        sTestIssue = ButtonDetector.ORDER;
        assertEquals(
                // Hardcoded values only
                "buttonbar.xml:12: Warning: OK button should be on the right (was \"OK | Cancel\", should be \"Cancel | OK\")\n" +
                "buttonbar.xml:44: Warning: OK button should be on the right (was \"OK | Cancel\", should be \"Cancel | OK\")",

                lintProject("res/layout/buttonbar.xml=>res/layout-de/buttonbar.xml",
                        "res/values/buttonbar-values.xml=>res/values-de/buttonbar-values.xml"));
    }

    public void testOtherLocales2() throws Exception {
        sTestIssue = ButtonDetector.CASE;
        assertEquals(
                "No warnings.",

                lintProject("res/layout/buttonbar.xml=>res/layout-de/buttonbar.xml",
                        "res/values/buttonbar-values.xml=>res/values-de/buttonbar-values.xml"));
    }
}
