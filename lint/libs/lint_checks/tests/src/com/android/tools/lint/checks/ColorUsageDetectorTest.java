/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
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
public class ColorUsageDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new ColorUsageDetector();
    }

    public void test() throws Exception {
        assertEquals(
            "WrongColor.java:11: Error: Should pass resolved color instead of resource id here: getResources().getColor(R.color.red)\n" +
            "WrongColor.java:12: Error: Should pass resolved color instead of resource id here: getResources().getColor(android.R.color.red)\n" +
            "WrongColor.java:9: Error: Should pass resolved color instead of resource id here: getResources().getColor(R.color.blue)",

            lintProject("src/test/pkg/WrongColor.java.txt=>src/test/pkg/WrongColor.java"));
    }
}
