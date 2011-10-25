/*
 * Copyright (C) 2011 The Android Open Source Project
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
public class UnusedResourceDetectorTest  extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new UnusedResourceDetector();
    }

    public void testUnused() throws Exception {
        assertEquals(
           "Warning: The resource R.id.imageView1 appears to be unused\n" +
           "Warning: The resource R.id.include1 appears to be unused\n" +
           "Warning: The resource R.id.linearLayout2 appears to be unused\n" +
           "Warning: The resource R.layout.main appears to be unused\n" +
           "Warning: The resource R.layout.other appears to be unused\n" +
           "Warning: The resource R.string.hello appears to be unused\n" +
           "accessibility.xml:3: Warning: The resource R.id.newlinear appears to be unused\n" +
           "accessibility.xml:4: Warning: The resource R.id.button1 appears to be unused\n" +
           "accessibility.xml:5: Warning: The resource R.id.android_logo appears to be unused\n" +
           "accessibility.xml:6: Warning: The resource R.id.android_logo2 appears to be unused",

            lintProject(
                // Rename .txt files to .java
                "src/my/pkg/Test.java.txt=>src/my/pkg/Test.java",
                "gen/my/pkg/R.java.txt=>gen/my/pkg/R.java",
                "AndroidManifest.xml",
                "res/layout/accessibility.xml"));
    }
}
