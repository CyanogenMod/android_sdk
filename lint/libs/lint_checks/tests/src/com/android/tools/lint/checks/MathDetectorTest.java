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
public class MathDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new MathDetector();
    }

    public void test() throws Exception {
        assertEquals(
                "MathTest.java:12: Warning: Use android.util.FloatMath#sqrt() instead of java.lang.Math#sqrt to avoid argument float to double conversion\n" +
                "MathTest.java:18: Warning: Use android.util.FloatMath#sqrt() instead of java.lang.Math#sqrt to avoid double to float return value conversion\n" +
                "MathTest.java:23: Warning: Use android.util.FloatMath#sqrt() instead of java.lang.Math#sqrt to avoid argument float to double conversion",

                lintProject(
                        "bytecode/.classpath=>.classpath",
                        "bytecode/AndroidManifest.xml=>AndroidManifest.xml",
                        "bytecode/MathTest.java.txt=>src/test/bytecode/MathTest.java",
                        "bytecode/MathTest.class.data=>bin/classes/test/bytecode/MathTest.class"
                        ));
    }
}
