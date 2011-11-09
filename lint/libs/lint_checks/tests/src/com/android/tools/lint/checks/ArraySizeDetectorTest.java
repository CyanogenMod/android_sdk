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
public class ArraySizeDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new ArraySizeDetector();
    }
    public void testArraySizes() throws Exception {
        assertEquals(
            "arrays.xml: Warning: Array security_questions has an inconsistent number of items " +
                    "(3 in values-nl-rNL/arrays.xml, 4 in values-cs/arrays.xml)\n" +
            "arrays.xml: Warning: Array signal_strength has an inconsistent number of items " +
                "(5 in values/arrays.xml, 6 in values-land/arrays.xml)",

            lintProject(
                 "res/values/arrays.xml",
                 "res/values-cs/arrays.xml",
                 "res/values-land/arrays.xml",
                 "res/values-nl-rNL/arrays.xml",
                 "res/values-es/strings.xml"));
    }
}
