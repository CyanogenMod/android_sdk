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
public class TypoDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new TypoDetector();
    }

    public void test() throws Exception {
        assertEquals(
                "wrong_namespace.xml:2: Warning: Unexpected namespace URI bound to the " +
                "\"android\" prefix, was http://schemas.android.com/apk/res/andriod, " +
                "expected http://schemas.android.com/apk/res/android",

                lintProject("res/layout/wrong_namespace.xml"));
    }

    public void test2() throws Exception {
        assertEquals(
                "wrong_namespace2.xml:2: Warning: URI is case sensitive: was " +
                "\"http://schemas.android.com/apk/res/Android\", expected " +
                "\"http://schemas.android.com/apk/res/android\"",

                lintProject("res/layout/wrong_namespace2.xml"));
    }

    public void test3() throws Exception {
        assertEquals(
                "wrong_namespace3.xml:2: Warning: Unexpected namespace URI bound to the " +
                "\"android\" prefix, was http://schemas.android.com/apk/res/androi, " +
                "expected http://schemas.android.com/apk/res/android",

                lintProject("res/layout/wrong_namespace3.xml"));
    }

    public void testOk() throws Exception {
        assertEquals(
                "No warnings.",

                lintProject("res/layout/wrong_namespace4.xml"));
    }
}
