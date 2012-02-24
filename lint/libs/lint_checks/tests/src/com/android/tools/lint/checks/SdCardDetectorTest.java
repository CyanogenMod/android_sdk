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
public class SdCardDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new SdCardDetector();
    }

    public void test() throws Exception {
        assertEquals(
                "SdCardTest.java:13: Warning: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead\n" +
                "SdCardTest.java:14: Warning: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead\n" +
                "SdCardTest.java:15: Warning: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead\n" +
                "SdCardTest.java:16: Warning: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead\n" +
                "SdCardTest.java:20: Warning: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead\n" +
                "SdCardTest.java:22: Warning: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead\n" +
                "SdCardTest.java:24: Warning: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead\n" +
                "SdCardTest.java:30: Warning: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead\n" +
                "SdCardTest.java:31: Warning: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead",

                lintProject("src/test/pkg/SdCardTest.java.txt=>src/test/pkg/SdCardTest.java"));
    }

    public void testSuppress() throws Exception {
        assertEquals(
            // The only reference in the file not covered by an annotation
            "SuppressTest5.java:40: Warning: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead",

            // File with lots of /sdcard references, but with @SuppressLint warnings
            // on fields, methods, variable declarations etc
            lintProject("src/test/pkg/SuppressTest5.java.txt=>src/test/pkg/SuppressTest5.java"));
    }

    public void testUtf8Bom() throws Exception {
        assertEquals(
            "Utf8BomTest.java:4: Warning: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead",

            lintProject("src/test/pkg/Utf8BomTest.java.data=>src/test/pkg/Utf8BomTest.java"));
    }
}
