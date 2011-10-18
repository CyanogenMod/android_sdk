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
public class ProguardDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new ProguardDetector();
    }

    public void testProguard() throws Exception {
        assertEquals(
                "proguard.cfg: Error: Obsolete proguard file; use -keepclasseswithmembers " +
                    "instead of -keepclasseswithmembernames",
                lint("proguard.cfg"));
    }
}
