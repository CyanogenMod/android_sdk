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
public class IconDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new IconDetector();
    }

    public void test() throws Exception {
        assertEquals(
            "Warning: Missing density variation folders in res: drawable-xhdpi\n" +
            "drawable-hdpi: Warning: Missing the following drawables in drawable-hdpi: " +
                    "sample_icon.gif (found in drawable-mdpi)\n" +
            "ic_launcher.png: Warning: Found bitmap drawable res/drawable/ic_launcher.png " +
                    "in densityless folder\n" +
            "sample_icon.gif: Warning: Using the .gif format for bitmaps is discouraged",
            lintProject(
                    "res/drawable/ic_launcher.png",
                    "res/drawable/ic_launcher.png=>res/drawable-mdpi/ic_launcher.png",
                    "res/drawable-mdpi/sample_icon.gif",
                    "res/drawable-hdpi/ic_launcher.png"));
    }
}
