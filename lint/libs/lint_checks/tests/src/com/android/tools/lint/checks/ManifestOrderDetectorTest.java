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
public class ManifestOrderDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new ManifestOrderDetector();
    }

    public void testOrderOk() throws Exception {
        assertEquals(
                "No warnings.",
                lintProject(
                        "AndroidManifest.xml",
                        "res/values/strings.xml"));
    }

    public void testBrokenOrder() throws Exception {
        assertEquals(
                "AndroidManifest.xml:16: Warning: <uses-sdk> tag appears after <application> " +
                "tag\n" +
                "AndroidManifest.xml:16: Warning: <uses-sdk> tag should specify a target API " +
                "level (the highest verified version; when running on later versions, " +
                "compatibility behaviors may be enabled) with android:targetSdkVersion=\"?\"",

                lintProject(
                        "broken-manifest.xml=>AndroidManifest.xml",
                        "res/values/strings.xml"));
    }

    public void testMissingUsesSdk() throws Exception {
        assertEquals(
                "AndroidManifest.xml: Warning: Manifest should specify a minimum API level " +
                "with <uses-sdk android:minSdkVersion=\"?\" />; if it really supports all " +
                "versions of Android set it to 1.",
                lintProject(
                        "missingusessdk.xml=>AndroidManifest.xml",
                        "res/values/strings.xml"));
    }

    public void testMissingMinSdk() throws Exception {
        assertEquals(
                "AndroidManifest.xml:7: Warning: <uses-sdk> tag should specify a minimum API " +
                "level with android:minSdkVersion=\"?\"",
                lintProject(
                        "missingmin.xml=>AndroidManifest.xml",
                        "res/values/strings.xml"));
    }

    public void testMultipleSdk() throws Exception {
        assertEquals(
                "AndroidManifest.xml:7: Warning: <uses-sdk> tag should specify a target API level (the highest verified version; when running on later versions, compatibility behaviors may be enabled) with android:targetSdkVersion=\"?\"\n" +
                "AndroidManifest.xml:9: Warning: <uses-sdk> tag should specify a minimum API level with android:minSdkVersion=\"?\"\n" +
                "ManifestOrderDetectorTest_testMultipleSdk/AndroidManifest.xml:8: Error: There should only be a single <uses-sdk> element in the manifest: merge these together\n" +
                "=> ManifestOrderDetectorTest_testMultipleSdk/AndroidManifest.xml:7: Also appears here\n" +
                "=> ManifestOrderDetectorTest_testMultipleSdk/AndroidManifest.xml:9: Also appears here",

                lintProject(
                        "multiplesdk.xml=>AndroidManifest.xml",
                        "res/values/strings.xml"));
    }
}
