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
import com.android.tools.lint.detector.api.Project;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@SuppressWarnings("javadoc")
public class IconDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new IconDetector();
    }

    private Set<Issue> mEnabled = new HashSet<Issue>();
    private static Set<Issue> ALL = new HashSet<Issue>();
    static {
        ALL.add(IconDetector.DUPLICATES_CONFIGURATIONS);
        ALL.add(IconDetector.DUPLICATES_NAMES);
        ALL.add(IconDetector.GIF_USAGE);
        ALL.add(IconDetector.ICON_DENSITIES);
        ALL.add(IconDetector.ICON_DIP_SIZE);
        ALL.add(IconDetector.ICON_EXPECTED_SIZE);
        ALL.add(IconDetector.ICON_EXTENSION);
        ALL.add(IconDetector.ICON_LOCATION);
        ALL.add(IconDetector.ICON_MISSING_FOLDER);
        ALL.add(IconDetector.ICON_NODPI);
    }

    @Override
    protected TestConfiguration getConfiguration(Project project) {
        return new TestConfiguration() {
            @Override
            public boolean isEnabled(Issue issue) {
                return super.isEnabled(issue) && mEnabled.contains(issue);
            }
        };
    }

    public void test() throws Exception {
        mEnabled = ALL;
        assertEquals(
            "res/drawable-mdpi/sample_icon.gif: Warning: Using the .gif format for bitmaps is discouraged [GifUsage]\n" +
            "res/drawable/ic_launcher.png: Warning: The ic_launcher.png icon has identical contents in the following configuration folders: drawable-mdpi, drawable [IconDuplicatesConfig]\n" +
            "    res/drawable-mdpi/ic_launcher.png: <No location-specific message\n" +
            "res/drawable/ic_launcher.png: Warning: Found bitmap drawable res/drawable/ic_launcher.png in densityless folder [IconLocation]\n" +
            "res/drawable-hdpi: Warning: Missing the following drawables in drawable-hdpi: sample_icon.gif (found in drawable-mdpi) [IconDensities]\n" +
            "res: Warning: Missing density variation folders in res: drawable-xhdpi [IconMissingDensityFolder]\n" +
            "0 errors, 5 warnings\n" +
            "",

            lintProject(
                    // Use minSDK4 to ensure that we get warnings about missing drawables
                    "apicheck/minsdk4.xml=>AndroidManifest.xml",
                    "res/drawable/ic_launcher.png",
                    "res/drawable/ic_launcher.png=>res/drawable-mdpi/ic_launcher.png",
                    "res/drawable-mdpi/sample_icon.gif",
                    // Make a dummy file named .svn to make sure it doesn't get seen as
                    // an icon name
                    "res/drawable-mdpi/sample_icon.gif=>res/drawable-hdpi/.svn",
                    "res/drawable-hdpi/ic_launcher.png"));
    }

    public void testApi1() throws Exception {
        mEnabled = ALL;
        assertEquals(
            "No warnings.",

            lintProject(
                    // manifest file which specifies uses sdk = 2
                    "apicheck/minsdk2.xml=>AndroidManifest.xml",
                    "res/drawable/ic_launcher.png"));
    }

    public void test2() throws Exception {
        mEnabled = ALL;
        assertEquals(
            "res/drawable-hdpi/other.9.png: Warning: The following unrelated icon files have identical contents: appwidget_bg.9.png, other.9.png [IconDuplicates]\n" +
            "    res/drawable-hdpi/appwidget_bg.9.png: <No location-specific message\n" +
            "res/drawable-hdpi/unrelated.png: Warning: The following unrelated icon files have identical contents: ic_launcher.png, unrelated.png [IconDuplicates]\n" +
            "    res/drawable-hdpi/ic_launcher.png: <No location-specific message\n" +
            "res: Warning: Missing density variation folders in res: drawable-mdpi, drawable-xhdpi [IconMissingDensityFolder]\n" +
            "0 errors, 3 warnings\n" +
            "",

            lintProject(
                    "res/drawable-hdpi/unrelated.png",
                    "res/drawable-hdpi/appwidget_bg.9.png",
                    "res/drawable-hdpi/appwidget_bg_focus.9.png",
                    "res/drawable-hdpi/other.9.png",
                    "res/drawable-hdpi/ic_launcher.png"
                    ));
    }

    public void testNoDpi() throws Exception {
        mEnabled = ALL;
        assertEquals(
            "res/drawable-mdpi/frame.png: Warning: The following images appear in both -nodpi and in a density folder: frame.png [IconNoDpi]\n" +
            "res/drawable-xlarge-nodpi-v11/frame.png: Warning: The frame.png icon has identical contents in the following configuration folders: drawable-mdpi, drawable-nodpi, drawable-xlarge-nodpi-v11 [IconDuplicatesConfig]\n" +
            "    res/drawable-nodpi/frame.png: <No location-specific message\n" +
            "    res/drawable-mdpi/frame.png: <No location-specific message\n" +
            "res: Warning: Missing density variation folders in res: drawable-hdpi, drawable-xhdpi [IconMissingDensityFolder]\n" +
            "0 errors, 3 warnings\n" +
            "",

            lintProject(
                "res/drawable-mdpi/frame.png",
                "res/drawable-nodpi/frame.png",
                "res/drawable-xlarge-nodpi-v11/frame.png"));
    }

    public void testNoDpi2() throws Exception {
        mEnabled = ALL;
        // Having additional icon names in the no-dpi folder should not cause any complaints
        assertEquals(
            "res/drawable-xhdpi/frame.png: Warning: The image frame.png varies significantly in its density-independent (dip) size across the various density versions: drawable-ldpi/frame.png: 629x387 dp (472x290 px), drawable-mdpi/frame.png: 472x290 dp (472x290 px), drawable-hdpi/frame.png: 315x193 dp (472x290 px), drawable-xhdpi/frame.png: 236x145 dp (472x290 px) [IconDipSize]\n" +
            "    res/drawable-hdpi/frame.png: <No location-specific message\n" +
            "    res/drawable-mdpi/frame.png: <No location-specific message\n" +
            "    res/drawable-ldpi/frame.png: <No location-specific message\n" +
            "res/drawable-xhdpi/frame.png: Warning: The following unrelated icon files have identical contents: frame.png, frame.png, frame.png, file1.png, file2.png, frame.png [IconDuplicates]\n" +
            "    res/drawable-nodpi/file2.png: <No location-specific message\n" +
            "    res/drawable-nodpi/file1.png: <No location-specific message\n" +
            "    res/drawable-mdpi/frame.png: <No location-specific message\n" +
            "    res/drawable-ldpi/frame.png: <No location-specific message\n" +
            "    res/drawable-hdpi/frame.png: <No location-specific message\n" +
            "0 errors, 2 warnings\n" +
            "",

            lintProject(
                    "res/drawable-mdpi/frame.png=>res/drawable-mdpi/frame.png",
                    "res/drawable-mdpi/frame.png=>res/drawable-hdpi/frame.png",
                    "res/drawable-mdpi/frame.png=>res/drawable-ldpi/frame.png",
                    "res/drawable-mdpi/frame.png=>res/drawable-xhdpi/frame.png",
                    "res/drawable-mdpi/frame.png=>res/drawable-nodpi/file1.png",
                    "res/drawable-mdpi/frame.png=>res/drawable-nodpi/file2.png"));
    }

    public void testNoDpiMix() throws Exception {
        mEnabled = ALL;
        assertEquals(
            "res/drawable-mdpi/frame.xml: Warning: The following images appear in both -nodpi and in a density folder: frame.png, frame.xml [IconNoDpi]\n" +
            "    res/drawable-mdpi/frame.png: <No location-specific message\n" +
            "res: Warning: Missing density variation folders in res: drawable-hdpi, drawable-xhdpi [IconMissingDensityFolder]\n" +
            "0 errors, 2 warnings\n" +
            "",

            lintProject(
                "res/drawable-mdpi/frame.png",
                "res/drawable/states.xml=>res/drawable-nodpi/frame.xml"));
    }


    public void testMixedFormat() throws Exception {
        mEnabled = ALL;
        // Test having a mixture of .xml and .png resources for the same name
        // Make sure we don't get:
        // drawable-hdpi: Warning: Missing the following drawables in drawable-hdpi: f.png (found in drawable-mdpi)
        // drawable-xhdpi: Warning: Missing the following drawables in drawable-xhdpi: f.png (found in drawable-mdpi)
        assertEquals(
            "No warnings.",

            lintProject(
                    "res/drawable-mdpi/frame.png=>res/drawable-mdpi/f.png",
                    "res/drawable/states.xml=>res/drawable-hdpi/f.xml",
                    "res/drawable/states.xml=>res/drawable-xhdpi/f.xml"));
    }

    public void testMisleadingFileName() throws Exception {
        mEnabled = Collections.singleton(IconDetector.ICON_EXTENSION);
        assertEquals(
            "res/drawable-mdpi/frame.gif: Warning: Misleading file extension; named .gif but the file format is png [IconExtension]\n" +
            "res/drawable-mdpi/frame.jpg: Warning: Misleading file extension; named .jpg but the file format is png [IconExtension]\n" +
            "res/drawable-mdpi/myjpg.png: Warning: Misleading file extension; named .png but the file format is JPEG [IconExtension]\n" +
            "res/drawable-mdpi/sample_icon.jpeg: Warning: Misleading file extension; named .jpeg but the file format is gif [IconExtension]\n" +
            "res/drawable-mdpi/sample_icon.jpg: Warning: Misleading file extension; named .jpg but the file format is gif [IconExtension]\n" +
            "res/drawable-mdpi/sample_icon.png: Warning: Misleading file extension; named .png but the file format is gif [IconExtension]\n" +
            "0 errors, 6 warnings\n",

            lintProject(
                "res/drawable-mdpi/sample_icon.jpg=>res/drawable-mdpi/myjpg.jpg", // VALID
                "res/drawable-mdpi/sample_icon.jpg=>res/drawable-mdpi/myjpg.jpeg", // VALID
                "res/drawable-mdpi/frame.png=>res/drawable-mdpi/frame.gif",
                "res/drawable-mdpi/frame.png=>res/drawable-mdpi/frame.jpg",
                "res/drawable-mdpi/sample_icon.jpg=>res/drawable-mdpi/myjpg.png",
                "res/drawable-mdpi/sample_icon.gif=>res/drawable-mdpi/sample_icon.jpg",
                "res/drawable-mdpi/sample_icon.gif=>res/drawable-mdpi/sample_icon.jpeg",
                "res/drawable-mdpi/sample_icon.gif=>res/drawable-mdpi/sample_icon.png"));
    }
}