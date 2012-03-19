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
import com.android.tools.lint.detector.api.Issue;

@SuppressWarnings("javadoc")
public class UnusedResourceDetectorTest extends AbstractCheckTest {
    private boolean mEnableIds = false;

    @Override
    protected Detector getDetector() {
        return new UnusedResourceDetector();
    }

    @Override
    protected boolean isEnabled(Issue issue) {
        if (issue == UnusedResourceDetector.ISSUE_IDS) {
            return mEnableIds;
        } else {
            return true;
        }
    }

    public void testUnused() throws Exception {
        mEnableIds = false;
        assertEquals(
           "accessibility.xml: Warning: The resource R.layout.accessibility appears to be unused\n" +
           "main.xml: Warning: The resource R.layout.main appears to be unused\n" +
           "other.xml: Warning: The resource R.layout.other appears to be unused\n" +
           "strings2.xml:3: Warning: The resource R.string.hello appears to be unused",

            lintProject(
                "res/values/strings2.xml",
                "res/layout/layout1.xml=>res/layout/main.xml",
                "res/layout/layout1.xml=>res/layout/other.xml",

                // Rename .txt files to .java
                "src/my/pkg/Test.java.txt=>src/my/pkg/Test.java",
                "gen/my/pkg/R.java.txt=>gen/my/pkg/R.java",
                "AndroidManifest.xml",
                "res/layout/accessibility.xml"));
    }

    public void testUnusedIds() throws Exception {
        mEnableIds = true;

        assertEquals(
           "Warning: The resource R.id.imageView1 appears to be unused\n" +
           "Warning: The resource R.id.include1 appears to be unused\n" +
           "Warning: The resource R.id.linearLayout2 appears to be unused\n" +
           "Warning: The resource R.layout.main appears to be unused\n" +
           "Warning: The resource R.layout.other appears to be unused\n" +
           "Warning: The resource R.string.hello appears to be unused\n" +
           "accessibility.xml: Warning: The resource R.layout.accessibility appears to be unused\n" +
           "accessibility.xml:2: Warning: The resource R.id.newlinear appears to be unused\n" +
           "accessibility.xml:3: Warning: The resource R.id.button1 appears to be unused\n" +
           "accessibility.xml:4: Warning: The resource R.id.android_logo appears to be unused\n" +
           "accessibility.xml:5: Warning: The resource R.id.android_logo2 appears to be unused",

            lintProject(
                // Rename .txt files to .java
                "src/my/pkg/Test.java.txt=>src/my/pkg/Test.java",
                "gen/my/pkg/R.java.txt=>gen/my/pkg/R.java",
                "AndroidManifest.xml",
                "res/layout/accessibility.xml"));
    }

    public void testArrayReference() throws Exception {
        assertEquals(
           "arrayusage.xml:3: Warning: The resource R.array.my_array appears to be unused",

            lintProject(
                "AndroidManifest.xml",
                "res/values/arrayusage.xml"));
    }

    public void testAttrs() throws Exception {
        assertEquals(
           "customattrlayout.xml: Warning: The resource R.layout.customattrlayout appears to be unused",

            lintProject(
                "res/values/customattr.xml",
                "res/layout/customattrlayout.xml",
                "unusedR.java.txt=>gen/my/pkg/R.java",
                "AndroidManifest.xml"));
    }

    public void testMultiProject() throws Exception {
        assertEquals(
           // string1 is defined and used in the library project
           // string2 is defined in the library project and used in the master project
           // string3 is defined in the library project and not used anywhere
           "strings.xml:7: Warning: The resource R.string.string3 appears to be unused",

            lintProject(
                // Master project
                "multiproject/main-manifest.xml=>AndroidManifest.xml",
                "multiproject/main.properties=>project.properties",
                "multiproject/MainCode.java.txt=>src/foo/main/MainCode.java",

                // Library project
                "multiproject/library-manifest.xml=>../LibraryProject/AndroidManifest.xml",
                "multiproject/library.properties=>../LibraryProject/project.properties",
                "multiproject/LibraryCode.java.txt=>../LibraryProject/src/foo/library/LibraryCode.java",
                "multiproject/strings.xml=>../LibraryProject/res/values/strings.xml"
            ));
    }

    public void testFqcnReference() throws Exception {
        assertEquals(
           "No warnings.",

            lintProject(
                "res/layout/layout1.xml=>res/layout/main.xml",
                "src/test/pkg/UnusedReference.java.txt=>src/test/pkg/UnusedReference.java",
                "AndroidManifest.xml"));
    }

    public void testIgnoreXmlDrawable() throws Exception {
        assertEquals(
           "No warnings.",

            lintProject(
                    "res/drawable/ic_menu_help.xml",
                    "gen/my/pkg/R2.java.txt=>gen/my/pkg/R.java"
            ));
    }
}
