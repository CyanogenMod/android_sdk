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

@SuppressWarnings("javadoc")
public class WakelockDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new WakelockDetector();
    }

    public void test1() throws Exception {
        assertEquals(
            "WakelockActivity1.java:15: Warning: Found a wakelock acquire() but no release() calls anywhere",

            lintProject(
                "bytecode/.classpath=>.classpath",
                "bytecode/AndroidManifest.xml=>AndroidManifest.xml",
                "res/layout/onclick.xml=>res/layout/onclick.xml",
                "bytecode/WakelockActivity1.java.txt=>src/test/pkg/WakelockActivity1.java",
                "bytecode/WakelockActivity1.class.data=>bin/classes/test/pkg/WakelockActivity1.class"
                ));
    }

    public void test2() throws Exception {
        assertEquals(
            "WakelockActivity2.java:13: Warning: Wakelocks should be released in onPause, not onDestroy",

            lintProject(
                "bytecode/.classpath=>.classpath",
                "bytecode/AndroidManifest.xml=>AndroidManifest.xml",
                "res/layout/onclick.xml=>res/layout/onclick.xml",
                "bytecode/WakelockActivity2.java.txt=>src/test/pkg/WakelockActivity2.java",
                "bytecode/WakelockActivity2.class.data=>bin/classes/test/pkg/WakelockActivity2.class"
                ));
    }
}
