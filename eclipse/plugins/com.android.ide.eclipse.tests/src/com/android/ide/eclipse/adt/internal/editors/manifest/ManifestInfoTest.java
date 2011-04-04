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
package com.android.ide.eclipse.adt.internal.editors.manifest;

import static com.android.resources.ScreenSize.LARGE;
import static com.android.resources.ScreenSize.NORMAL;
import static com.android.resources.ScreenSize.XLARGE;

import com.android.ide.eclipse.adt.internal.editors.layout.refactoring.AdtProjectTest;
import com.android.ide.eclipse.adt.internal.resources.ResourceHelper;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.NullProgressMonitor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;

public class ManifestInfoTest extends AdtProjectTest {
    @Override
    protected boolean testCaseNeedsUniqueProject() {
        return true;
    }

    public void testGetActivityThemes1() throws Exception {
        ManifestInfo info = getManifestInfo(
                "<manifest xmlns:android='http://schemas.android.com/apk/res/android'\n" +
                "    package='com.android.unittest'>\n" +
                "    <uses-sdk android:minSdkVersion='3' android:targetSdkVersion='4'/>\n" +
                "</manifest>\n");
        Map<String, String> map = info.getActivityThemes();
        assertEquals(map.toString(), 0, map.size());
        assertEquals("com.android.unittest", info.getPackage());
        assertEquals("Theme", ResourceHelper.styleToTheme(info.getDefaultTheme(NORMAL)));
        assertEquals("@android:style/Theme", info.getDefaultTheme(null));
        assertEquals("Theme", ResourceHelper.styleToTheme(info.getDefaultTheme(XLARGE)));
    }

    public void testGetActivityThemes2() throws Exception {
        ManifestInfo info = getManifestInfo(
                "<manifest xmlns:android='http://schemas.android.com/apk/res/android'\n" +
                "    package='com.android.unittest'>\n" +
                "    <uses-sdk android:minSdkVersion='3' android:targetSdkVersion='11'/>\n" +
                "</manifest>\n");
        Map<String, String> map = info.getActivityThemes();
        assertEquals(map.toString(), 0, map.size());
        assertEquals("com.android.unittest", info.getPackage());
        assertEquals("Theme.Holo", ResourceHelper.styleToTheme(info.getDefaultTheme(XLARGE)));
        assertEquals("Theme", ResourceHelper.styleToTheme(info.getDefaultTheme(LARGE)));
    }

    public void testGetActivityThemes3() throws Exception {
        ManifestInfo info = getManifestInfo(
                "<manifest xmlns:android='http://schemas.android.com/apk/res/android'\n" +
                "    package='com.android.unittest'>\n" +
                "    <uses-sdk android:minSdkVersion='11'/>\n" +
                "</manifest>\n");
        Map<String, String> map = info.getActivityThemes();
        assertEquals(map.toString(), 0, map.size());
        assertEquals("com.android.unittest", info.getPackage());
        assertEquals("Theme.Holo", ResourceHelper.styleToTheme(info.getDefaultTheme(XLARGE)));
        assertEquals("Theme", ResourceHelper.styleToTheme(info.getDefaultTheme(NORMAL)));
    }

    public void testGetActivityThemes4() throws Exception {
        ManifestInfo info = getManifestInfo(
                "<manifest xmlns:android='http://schemas.android.com/apk/res/android'\n" +
                "    package='com.android.unittest'>\n" +
                "    <application\n" +
                "        android:label='@string/app_name'\n" +
                "        android:name='.app.TestApp' android:icon='@drawable/app_icon'>\n" +
                "\n" +
                "        <activity\n" +
                "            android:name='.prefs.PrefsActivity'\n" +
                "            android:label='@string/prefs_title' />\n" +
                "\n" +
                "        <activity\n" +
                "            android:name='.app.IntroActivity'\n" +
                "            android:label='@string/intro_title'\n" +
                "            android:theme='@android:style/Theme.Dialog' />\n" +
                "    </application>\n" +
                "    <uses-sdk android:minSdkVersion='3' android:targetSdkVersion='4'/>\n" +
                "</manifest>\n" +
                ""
                );
        assertEquals("com.android.unittest", info.getPackage());
        assertEquals("Theme", ResourceHelper.styleToTheme(info.getDefaultTheme(XLARGE)));

        Map<String, String> map = info.getActivityThemes();
        assertEquals(map.toString(), 1, map.size());
        assertNull(map.get("com.android.unittest.prefs.PrefsActivity"));
        assertEquals("@android:style/Theme.Dialog",
                map.get("com.android.unittest.app.IntroActivity"));
    }

    public void testGetActivityThemes5() throws Exception {
        ManifestInfo info = getManifestInfo(
                "<manifest xmlns:android='http://schemas.android.com/apk/res/android'\n" +
                "    package='com.android.unittest'" +
                "    android:theme='@style/NoBackground'>\n" +
                "    <application\n" +
                "        android:label='@string/app_name'\n" +
                "        android:name='.app.TestApp' android:icon='@drawable/app_icon'>\n" +
                "\n" +
                "        <activity\n" +
                "            android:name='.prefs.PrefsActivity'\n" +
                "            android:label='@string/prefs_title' />\n" +
                "\n" +
                "        <activity\n" +
                "            android:name='.app.IntroActivity'\n" +
                "            android:label='@string/intro_title'\n" +
                "            android:theme='@android:style/Theme.Dialog' />\n" +
                "    </application>\n" +
                "    <uses-sdk android:minSdkVersion='3' android:targetSdkVersion='4'/>\n" +
                "</manifest>\n" +
                ""
                );

        assertEquals("@style/NoBackground", info.getDefaultTheme(XLARGE));
        assertEquals("@style/NoBackground", info.getDefaultTheme(NORMAL));
        assertEquals("NoBackground", ResourceHelper.styleToTheme(info.getDefaultTheme(NORMAL)));

        Map<String, String> map = info.getActivityThemes();
        assertEquals(map.toString(), 1, map.size());
        assertNull(map.get("com.android.unittest.prefs.PrefsActivity"));
        assertEquals("@android:style/Theme.Dialog",
                map.get("com.android.unittest.app.IntroActivity"));

    }

    private ManifestInfo getManifestInfo(String manifestContents) throws Exception {
        InputStream bstream = new ByteArrayInputStream(
                manifestContents.getBytes("UTF-8")); //$NON-NLS-1$

        IFile file = getProject().getFile("AndroidManifest.xml");
        if (file.exists()) {
            file.setContents(bstream, IFile.FORCE, new NullProgressMonitor());
        } else {
            file.create(bstream, false /* force */, new NullProgressMonitor());
        }
        return ManifestInfo.get(getProject());
    }
}
