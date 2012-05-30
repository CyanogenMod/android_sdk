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
public class HandlerDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new HandlerDetector();
    }

    public void testRegistered() throws Exception {
        assertEquals(
            "HandlerTest.java:14: Warning: This Handler class should be static or leaks might occur (test.pkg.HandlerTest.Inner)\n" +
            "HandlerTest.java:20: Warning: This Handler class should be static or leaks might occur (test.pkg.HandlerTest.1)",

            lintProject(
                "bytecode/HandlerTest.java.txt=>src/test/pkg/HandlerTest.java",
                "bytecode/HandlerTest.class.data=>bin/classes/test/pkg/HandlerTest.class",
                "bytecode/HandlerTest$Inner.class.data=>bin/classes/test/pkg/HandlerTest$Inner.class",
                "bytecode/HandlerTest$StaticInner.class.data=>bin/classes/test/pkg/HandlerTest$StaticInner.class",
                "bytecode/HandlerTest$1.class.data=>bin/classes/test/pkg/HandlerTest$1.class"));
    }
}
