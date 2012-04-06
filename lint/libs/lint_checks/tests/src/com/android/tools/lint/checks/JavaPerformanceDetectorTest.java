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
public class JavaPerformanceDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new JavaPerformanceDetector();
    }

    public void test() throws Exception {
        assertEquals(
            "JavaPerformanceTest.java:103: Warning: Avoid object allocations during draw/layout operations (preallocate and reuse instead)\n" +
            "JavaPerformanceTest.java:109: Warning: Avoid object allocations during draw/layout operations (preallocate and reuse instead)\n" +
            "JavaPerformanceTest.java:112: Warning: Avoid object allocations during draw/layout operations (preallocate and reuse instead)\n" +
            "JavaPerformanceTest.java:113: Warning: Avoid object allocations during draw/layout operations (preallocate and reuse instead)\n" +
            "JavaPerformanceTest.java:114: Warning: Avoid object allocations during draw/layout operations (preallocate and reuse instead)\n" +
            "JavaPerformanceTest.java:116: Warning: Avoid object allocations during draw operations: Use Canvas.getClipBounds(Rect) instead of Canvas.getClipBounds() which allocates a temporary Rect\n" +
            "JavaPerformanceTest.java:140: Warning: Avoid object allocations during draw/layout operations (preallocate and reuse instead)\n" +
            "JavaPerformanceTest.java:145: Warning: Use Integer.valueOf(42) instead\n" +
            "JavaPerformanceTest.java:146: Warning: Use Long.valueOf(42L) instead\n" +
            "JavaPerformanceTest.java:147: Warning: Use Boolean.valueOf(true) instead\n" +
            "JavaPerformanceTest.java:148: Warning: Use Character.valueOf('c') instead\n" +
            "JavaPerformanceTest.java:149: Warning: Use Float.valueOf(1.0f) instead\n" +
            "JavaPerformanceTest.java:150: Warning: Use Double.valueOf(1.0) instead\n" +
            "JavaPerformanceTest.java:28: Warning: Avoid object allocations during draw/layout operations (preallocate and reuse instead)\n" +
            "JavaPerformanceTest.java:29: Warning: Avoid object allocations during draw/layout operations (preallocate and reuse instead)\n" +
            "JavaPerformanceTest.java:33: Warning: Use Integer.valueOf(5) instead\n" +
            "JavaPerformanceTest.java:70: Warning: Use new SparseArray<String>(...) instead for better performance\n" +
            "JavaPerformanceTest.java:72: Warning: Use new SparseBooleanArray(...) instead for better performance\n" +
            "JavaPerformanceTest.java:74: Warning: Use new SparseIntArray(...) instead for better performance",

            lintProject("src/test/pkg/JavaPerformanceTest.java.txt=>" +
                    "src/test/pkg/JavaPerformanceTest.java"));
    }
}
