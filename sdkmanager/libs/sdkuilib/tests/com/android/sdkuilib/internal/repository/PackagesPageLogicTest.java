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

package com.android.sdkuilib.internal.repository;

import com.android.sdklib.internal.repository.MockAddonPackage;
import com.android.sdklib.internal.repository.MockExtraPackage;
import com.android.sdklib.internal.repository.MockPlatformPackage;
import com.android.sdklib.internal.repository.MockPlatformToolPackage;
import com.android.sdklib.internal.repository.MockToolPackage;
import com.android.sdklib.internal.repository.SdkRepoSource;
import com.android.sdklib.internal.repository.SdkSource;
import com.android.sdkuilib.internal.repository.PackageLoader.PkgItem;
import com.android.sdkuilib.internal.repository.PackageLoader.PkgState;
import com.android.sdkuilib.internal.repository.PackagesPage.PackagesPageLogic;
import com.android.sdkuilib.internal.repository.PackagesPage.PkgCategory;

import junit.framework.TestCase;

public class PackagesPageLogicTest extends TestCase {

    private PackagesPageLogic m;
    private MockUpdaterData u;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        u = new MockUpdaterData();
        m = new PackagesPageLogic(u) {
            @Override
            boolean keepItem(PkgItem item) {
                return true;
            }
        };
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    // ----
    //
    // Test Details Note: the way load is implemented in PackageLoader, the
    // loader processes each source and then for each source the packages are added
    // to a list and the sorting algorithm is called with that list. Thus for
    // one load, many calls to the sortByX/Y happen, with the list progressively
    // being populated.
    // However when the user switches sorting algorithm, the package list is not
    // reloaded and is processed at once.

    public void testSortByApi_Empty() {
        assertTrue(m.mAllPkgItems.isEmpty());
        m.sortByApiLevel();
        assertSame(m.mCurrentCategories, m.mApiCategories);
        assertTrue(m.mApiCategories.isEmpty());
    }

    public void testSortByApi_SamePackage() {
        assertTrue(m.mAllPkgItems.isEmpty());
        SdkSource src1 = new SdkRepoSource("http://repo.com/url", "repo1");

        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage(src1, "some pkg", 1), PkgState.INSTALLED));
        m.sortByApiLevel();

        assertEquals(
                "PkgApiCategory <API=EXTRAS, label=Extras, #items=1>\n" +
                "-- <INSTALLED, pkg:MockEmptyPackage 'some pkg' rev=1>\n",
               getTree(m));

        // Same package as the one installed, so we don't display it
        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage(src1, "some pkg", 1), PkgState.NEW));
        m.sortByApiLevel();

        assertEquals(
                "PkgApiCategory <API=EXTRAS, label=Extras, #items=1>\n" +
                "-- <INSTALLED, pkg:MockEmptyPackage 'some pkg' rev=1>\n",
               getTree(m));
    }

    public void testSortByApi_AddPackages() {
        assertTrue(m.mAllPkgItems.isEmpty());
        SdkSource src1 = new SdkRepoSource("http://repo.com/url", "repo1");
        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage(src1, "that pkg", 1), PkgState.INSTALLED));
        m.sortByApiLevel();
        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage(src1, "this pkg", 1), PkgState.NEW));
        m.sortByApiLevel();

        assertEquals(
                "PkgApiCategory <API=EXTRAS, label=Extras, #items=2>\n" +
                "-- <INSTALLED, pkg:MockEmptyPackage 'that pkg' rev=1>\n" +
                "-- <NEW, pkg:MockEmptyPackage 'this pkg' rev=1>\n",
               getTree(m));
    }

    public void testSortByApi_Update1() {
        assertTrue(m.mAllPkgItems.isEmpty());
        SdkSource src1 = new SdkRepoSource("http://repo.com/url", "repo1");

        // Typical case: user has a locally installed package in revision 1
        // The display list after sort should show that instaled package.
        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage(src1, "type1", 1), PkgState.INSTALLED));
        m.sortByApiLevel();

        assertEquals(
                "PkgApiCategory <API=EXTRAS, label=Extras, #items=1>\n" +
                "-- <INSTALLED, pkg:MockEmptyPackage 'type1' rev=1>\n",
               getTree(m));

        // Then loading sources reveals an update in revision 4
        // Edge case: another source reveals an update in revision 2.
        // The display list after sort should show an update as available with rev 4
        // and rev 2 should be ignored since we have a better one.
        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage(src1, "type1", 4), PkgState.NEW));
        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage(src1, "type1", 2), PkgState.NEW));
        m.sortByApiLevel();

        assertEquals(
                "PkgApiCategory <API=EXTRAS, label=Extras, #items=1>\n" +
                "-- <INSTALLED, pkg:MockEmptyPackage 'type1' rev=1, updated by:MockEmptyPackage 'type1' rev=4>\n",
               getTree(m));
    }

    public void testSortByApi_Reload() {
        assertTrue(m.mAllPkgItems.isEmpty());
        SdkSource src1 = new SdkRepoSource("http://repo.com/url", "repo1");

        // First load reveals a package local package and its update
        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage(src1, "type1", 1), PkgState.INSTALLED));
        m.sortByApiLevel();
        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage(src1, "type1", 2), PkgState.NEW));
        m.sortByApiLevel();

        assertEquals(
                "PkgApiCategory <API=EXTRAS, label=Extras, #items=1>\n" +
                "-- <INSTALLED, pkg:MockEmptyPackage 'type1' rev=1, updated by:MockEmptyPackage 'type1' rev=2>\n",
               getTree(m));

        // Now simulate a reload that clears the package list and create similar
        // objects but not the same references.
        m.mAllPkgItems.clear();
        assertTrue(m.mAllPkgItems.isEmpty());
        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage(src1, "type1", 1), PkgState.INSTALLED));
        m.sortByApiLevel();
        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage(src1, "type1", 2), PkgState.NEW));
        m.sortByApiLevel();

        assertEquals(
                "PkgApiCategory <API=EXTRAS, label=Extras, #items=1>\n" +
                "-- <INSTALLED, pkg:MockEmptyPackage 'type1' rev=1, updated by:MockEmptyPackage 'type1' rev=2>\n",
               getTree(m));
    }

    public void testSortByApi_InstallAfterNew() {
        assertTrue(m.mAllPkgItems.isEmpty());
        SdkSource src1 = new SdkRepoSource("http://repo.com/url", "repo1");

        // We expect updates to appear AFTER the packages the installed items will update.
        // (This is pretty much guaranteed since local packages are processed first.)
        // The reverse order is not supported by the sorting algorithm and both will be shown.

        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage(src1, "type1", 2), PkgState.NEW));
        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage(src1, "type1", 1), PkgState.INSTALLED));
        m.sortByApiLevel();

        assertEquals(
                "PkgApiCategory <API=EXTRAS, label=Extras, #items=2>\n" +
                "-- <INSTALLED, pkg:MockEmptyPackage 'type1' rev=1>\n" +
                "-- <NEW, pkg:MockEmptyPackage 'type1' rev=2>\n",
               getTree(m));
    }

    public void testSortByApi_InstallPackage() {
        assertTrue(m.mAllPkgItems.isEmpty());
        SdkSource src1 = new SdkRepoSource("http://repo.com/url", "repo1");

        // First load reveals a new package
        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage(src1, "type1", 1), PkgState.NEW));
        m.sortByApiLevel();

        assertEquals(
                "PkgApiCategory <API=EXTRAS, label=Extras, #items=1>\n" +
                "-- <NEW, pkg:MockEmptyPackage 'type1' rev=1>\n",
               getTree(m));

        // Install it. Load reveals a package local package and its update
        m.mAllPkgItems.clear();
        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage(src1, "type1", 1), PkgState.INSTALLED));
        m.sortByApiLevel();
        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage(src1, "type1", 1), PkgState.NEW));
        m.sortByApiLevel();

        assertEquals(
                "PkgApiCategory <API=EXTRAS, label=Extras, #items=1>\n" +
                "-- <INSTALLED, pkg:MockEmptyPackage 'type1' rev=1>\n",
               getTree(m));

        // Now we have an update
        m.mAllPkgItems.clear();
        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage(src1, "type1", 1), PkgState.INSTALLED));
        m.sortByApiLevel();
        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage(src1, "type1", 2), PkgState.NEW));
        m.sortByApiLevel();

        assertEquals(
                "PkgApiCategory <API=EXTRAS, label=Extras, #items=1>\n" +
                "-- <INSTALLED, pkg:MockEmptyPackage 'type1' rev=1, updated by:MockEmptyPackage 'type1' rev=2>\n",
               getTree(m));
    }

    public void testSortByApi_DeletePackage() {
        assertTrue(m.mAllPkgItems.isEmpty());
        SdkSource src1 = new SdkRepoSource("http://repo.com/url", "repo1");

        // We have an installed package
        m.mAllPkgItems.clear();
        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage(src1, "type1", 1), PkgState.INSTALLED));
        m.sortByApiLevel();
        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage(src1, "type1", 1), PkgState.NEW));
        m.sortByApiLevel();

        assertEquals(
                "PkgApiCategory <API=EXTRAS, label=Extras, #items=1>\n" +
                "-- <INSTALLED, pkg:MockEmptyPackage 'type1' rev=1>\n",
               getTree(m));

        // User now deletes the installed package.
        m.mAllPkgItems.clear();
        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage(src1, "type1", 1), PkgState.NEW));
        m.sortByApiLevel();

        assertEquals(
                "PkgApiCategory <API=EXTRAS, label=Extras, #items=1>\n" +
                "-- <NEW, pkg:MockEmptyPackage 'type1' rev=1>\n",
               getTree(m));
    }

    public void testSortByApi_CompleteUpdate() {
        assertTrue(m.mAllPkgItems.isEmpty());
        SdkSource src1 = new SdkRepoSource("http://repo.com/url", "repo1");

        // Resulting categories are sorted by Tools, descending platform API and finally Extras.
        // Addons are sorted by name within their API.
        // Extras are sorted by vendor name.
        // The order packages are added to the mAllPkgItems list is purposedly different from
        // the final order we get.

        // Typical case is to have these 2 tools, which should get sorted in their own category
        m.mAllPkgItems.add(new PkgItem(new MockToolPackage(10, 3), PkgState.INSTALLED));
        m.mAllPkgItems.add(new PkgItem(new MockPlatformToolPackage(3), PkgState.INSTALLED));
        // We'll typically see installed items twice, first as installed then as new packages
        // coming from the source that delivered them. The new ones should be ignored.
        m.mAllPkgItems.add(new PkgItem(new MockToolPackage(10, 3), PkgState.NEW));
        m.mAllPkgItems.add(new PkgItem(new MockPlatformToolPackage(3), PkgState.NEW));

        // Load a few extra packages
        m.mAllPkgItems.add(new PkgItem(
                new MockExtraPackage(src1, "carrier", "custom_rom", 1, 0), PkgState.NEW));

        // We call sortByApiLevel() multiple times to simulate the fact it works as an
        // incremental diff. In real usage, it is called after each source is loaded so
        // that we can progressively update the display.
        m.sortByApiLevel();

        assertEquals(
                "PkgApiCategory <API=TOOLS, label=Tools, #items=2>\n" +
                "-- <INSTALLED, pkg:Android SDK Tools, revision 10>\n" +
                "-- <INSTALLED, pkg:Android SDK Platform-tools, revision 3>\n" +
                "PkgApiCategory <API=EXTRAS, label=Extras, #items=1>\n" +
                "-- <NEW, pkg:Carrier Custom Rom package, revision 1>\n",
               getTree(m));

        m.mAllPkgItems.add(new PkgItem(
                new MockExtraPackage(src1, "android", "usb_driver", 4, 3), PkgState.INSTALLED));
        m.mAllPkgItems.add(new PkgItem(
                new MockExtraPackage(src1, "android", "usb_driver", 5, 3), PkgState.NEW));

        m.sortByApiLevel();

        assertEquals(
                "PkgApiCategory <API=TOOLS, label=Tools, #items=2>\n" +
                "-- <INSTALLED, pkg:Android SDK Tools, revision 10>\n" +
                "-- <INSTALLED, pkg:Android SDK Platform-tools, revision 3>\n" +
                "PkgApiCategory <API=EXTRAS, label=Extras, #items=2>\n" +
                "-- <INSTALLED, pkg:Android USB Driver package, revision 4, updated by:Android USB Driver package, revision 5>\n" +
                "-- <NEW, pkg:Carrier Custom Rom package, revision 1>\n",
               getTree(m));

        // Platforms and addon are sorted in a category based on their API level
        MockPlatformPackage p1;
        MockPlatformPackage p2;
        m.mAllPkgItems.add(new PkgItem(p1 = new MockPlatformPackage(src1, 1, 2, 3), PkgState.INSTALLED));
        m.mAllPkgItems.add(new PkgItem(p2 = new MockPlatformPackage(src1, 2, 4, 3), PkgState.NEW));
        m.mAllPkgItems.add(new PkgItem(     new MockPlatformPackage(src1, 3, 6, 3), PkgState.INSTALLED));

        m.sortByApiLevel();

        assertEquals(
                "PkgApiCategory <API=TOOLS, label=Tools, #items=2>\n" +
                "-- <INSTALLED, pkg:Android SDK Tools, revision 10>\n" +
                "-- <INSTALLED, pkg:Android SDK Platform-tools, revision 3>\n" +
                "PkgApiCategory <API=API 3, label=Android android-3 (API 3), #items=1>\n" +
                "-- <INSTALLED, pkg:SDK Platform Android android-3, API 3, revision 6>\n" +
                "PkgApiCategory <API=API 2, label=Android android-2 (API 2), #items=1>\n" +
                "-- <NEW, pkg:SDK Platform Android android-2, API 2, revision 4>\n" +
                "PkgApiCategory <API=API 1, label=Android android-1 (API 1), #items=1>\n" +
                "-- <INSTALLED, pkg:SDK Platform Android android-1, API 1, revision 2>\n" +
                "PkgApiCategory <API=EXTRAS, label=Extras, #items=2>\n" +
                "-- <INSTALLED, pkg:Android USB Driver package, revision 4, updated by:Android USB Driver package, revision 5>\n" +
                "-- <NEW, pkg:Carrier Custom Rom package, revision 1>\n",
               getTree(m));

        m.mAllPkgItems.add(new PkgItem(new MockAddonPackage(src1, "addon C", p2, 9), PkgState.NEW));
        m.mAllPkgItems.add(new PkgItem(new MockAddonPackage(src1, "addon A", p1, 5), PkgState.INSTALLED));
        m.mAllPkgItems.add(new PkgItem(new MockAddonPackage(src1, "addon A", p1, 6), PkgState.NEW));
        m.mAllPkgItems.add(new PkgItem(new MockAddonPackage(src1, "addon B", p2, 7), PkgState.NEW));
        // the rev 8 update will be ignored since there's a rev 9 coming after
        m.mAllPkgItems.add(new PkgItem(new MockAddonPackage(src1, "addon B", p2, 8), PkgState.NEW));
        m.mAllPkgItems.add(new PkgItem(new MockAddonPackage(src1, "addon B", p2, 9), PkgState.NEW));

        m.sortByApiLevel();

        assertEquals(
                "PkgApiCategory <API=TOOLS, label=Tools, #items=2>\n" +
                "-- <INSTALLED, pkg:Android SDK Tools, revision 10>\n" +
                "-- <INSTALLED, pkg:Android SDK Platform-tools, revision 3>\n" +
                "PkgApiCategory <API=API 3, label=Android android-3 (API 3), #items=1>\n" +
                "-- <INSTALLED, pkg:SDK Platform Android android-3, API 3, revision 6>\n" +
                "PkgApiCategory <API=API 2, label=Android android-2 (API 2), #items=3>\n" +
                "-- <NEW, pkg:SDK Platform Android android-2, API 2, revision 4>\n" +
                "-- <NEW, pkg:addon B by vendor 2, Android API 2, revision 7, updated by:addon B by vendor 2, Android API 2, revision 9>\n" +
                "-- <NEW, pkg:addon C by vendor 2, Android API 2, revision 9>\n" +
                "PkgApiCategory <API=API 1, label=Android android-1 (API 1), #items=2>\n" +
                "-- <INSTALLED, pkg:SDK Platform Android android-1, API 1, revision 2>\n" +
                "-- <INSTALLED, pkg:addon A by vendor 1, Android API 1, revision 5, updated by:addon A by vendor 1, Android API 1, revision 6>\n" +
                "PkgApiCategory <API=EXTRAS, label=Extras, #items=2>\n" +
                "-- <INSTALLED, pkg:Android USB Driver package, revision 4, updated by:Android USB Driver package, revision 5>\n" +
                "-- <NEW, pkg:Carrier Custom Rom package, revision 1>\n",
               getTree(m));

        // Now simulate a change of sorting algorithm: sort by source then by API again.

        m.sortBySource();
        m.sortByApiLevel();

        assertEquals(
                "PkgApiCategory <API=TOOLS, label=Tools, #items=2>\n" +
                "-- <INSTALLED, pkg:Android SDK Tools, revision 10>\n" +
                "-- <INSTALLED, pkg:Android SDK Platform-tools, revision 3>\n" +
                "PkgApiCategory <API=API 3, label=Android android-3 (API 3), #items=1>\n" +
                "-- <INSTALLED, pkg:SDK Platform Android android-3, API 3, revision 6>\n" +
                "PkgApiCategory <API=API 2, label=Android android-2 (API 2), #items=3>\n" +
                "-- <NEW, pkg:SDK Platform Android android-2, API 2, revision 4>\n" +
                "-- <NEW, pkg:addon B by vendor 2, Android API 2, revision 7, updated by:addon B by vendor 2, Android API 2, revision 9>\n" +
                "-- <NEW, pkg:addon C by vendor 2, Android API 2, revision 9>\n" +
                "PkgApiCategory <API=API 1, label=Android android-1 (API 1), #items=2>\n" +
                "-- <INSTALLED, pkg:SDK Platform Android android-1, API 1, revision 2>\n" +
                "-- <INSTALLED, pkg:addon A by vendor 1, Android API 1, revision 5, updated by:addon A by vendor 1, Android API 1, revision 6>\n" +
                "PkgApiCategory <API=EXTRAS, label=Extras, #items=2>\n" +
                "-- <INSTALLED, pkg:Android USB Driver package, revision 4, updated by:Android USB Driver package, revision 5>\n" +
                "-- <NEW, pkg:Carrier Custom Rom package, revision 1>\n",
               getTree(m));
}

    // ----

    public void testSortBySource_Empty() {
        assertTrue(m.mAllPkgItems.isEmpty());
        m.sortBySource();
        assertSame(m.mCurrentCategories, m.mSourceCategories);
        assertTrue(m.mApiCategories.isEmpty());
    }


    public void testSortBySource_AddPackages() {
        assertTrue(m.mAllPkgItems.isEmpty());

        // Since we're sorting by source, items are grouped under their source
        // even if installed. The 'local' source is only for installed items for
        // which we don't know the source.
        SdkSource src1 = new SdkRepoSource("http://repo.com/url", "repo1");

        m.mAllPkgItems.add(new PkgItem(
                new MockEmptyPackage(src1, "new", 1), PkgState.NEW));
        m.mAllPkgItems.add(new PkgItem(
                new MockEmptyPackage(src1, "known source", 2), PkgState.INSTALLED));
        m.mAllPkgItems.add(new PkgItem(
                new MockEmptyPackage(null, "unknown source", 3), PkgState.INSTALLED));

        m.sortBySource();

        assertEquals(
                "PkgSourceCategory <source=Local, #items=1>\n" +
                "-- <INSTALLED, pkg:MockEmptyPackage 'unknown source' rev=3>\n" +
                "PkgSourceCategory <source=repo1 (repo.com), #items=2>\n" +
                "-- <NEW, pkg:MockEmptyPackage 'new' rev=1>\n" +
                "-- <INSTALLED, pkg:MockEmptyPackage 'known source' rev=2>\n",
               getTree(m));
    }

    public void testSortBySource_Update1() {
        assertTrue(m.mAllPkgItems.isEmpty());

        // Typical case: user has a locally installed package in revision 1
        // The display list after sort should show that instaled package.
        SdkSource src1 = new SdkRepoSource("http://repo.com/url", "repo1");
        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage(src1, "type1", 1), PkgState.INSTALLED));

        m.sortBySource();

        assertEquals(
                "PkgSourceCategory <source=repo1 (repo.com), #items=1>\n" +
                "-- <INSTALLED, pkg:MockEmptyPackage 'type1' rev=1>\n",
               getTree(m));

        // Edge case: the source reveals an update in revision 2. It is ignored since
        // we already have a package in rev 4.

        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage(src1, "type1", 4), PkgState.NEW));
        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage(src1, "type1", 2), PkgState.NEW));

        m.sortBySource();

        assertEquals(
                "PkgSourceCategory <source=repo1 (repo.com), #items=1>\n" +
                "-- <INSTALLED, pkg:MockEmptyPackage 'type1' rev=1, updated by:MockEmptyPackage 'type1' rev=4>\n",
               getTree(m));
    }

    public void testSortBySource_Reload() {
        assertTrue(m.mAllPkgItems.isEmpty());

        // First load reveals a package local package and its update
        SdkSource src1 = new SdkRepoSource("http://repo.com/url", "repo1");
        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage(src1, "type1", 1), PkgState.INSTALLED));
        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage(src1, "type1", 2), PkgState.NEW));

        m.sortBySource();

        assertEquals(
                "PkgSourceCategory <source=repo1 (repo.com), #items=1>\n" +
                "-- <INSTALLED, pkg:MockEmptyPackage 'type1' rev=1, updated by:MockEmptyPackage 'type1' rev=2>\n",
               getTree(m));

        // Now simulate a reload that clears the package list and create similar
        // objects but not the same references.
        m.mAllPkgItems.clear();
        assertTrue(m.mAllPkgItems.isEmpty());
        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage(src1, "type1", 1), PkgState.INSTALLED));
        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage(src1, "type1", 2), PkgState.NEW));

        m.sortBySource();

        assertEquals(
                "PkgSourceCategory <source=repo1 (repo.com), #items=1>\n" +
                "-- <INSTALLED, pkg:MockEmptyPackage 'type1' rev=1, updated by:MockEmptyPackage 'type1' rev=2>\n",
               getTree(m));
    }

    public void testSortBySource_InstallAfterNew() {
        assertTrue(m.mAllPkgItems.isEmpty());

        // We expect updates to appear AFTER the packages the installed items will update.
        // (This is pretty much guaranteed since local packages are processed first.)
        // The reverse order is not supported by the sorting algorithm and both will be shown.

        SdkSource src1 = new SdkRepoSource("http://repo.com/url", "repo1");
        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage(src1, "type1", 2), PkgState.NEW));
        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage(src1, "type1", 1), PkgState.INSTALLED));

        m.sortBySource();

        assertEquals(
                "PkgSourceCategory <source=repo1 (repo.com), #items=2>\n" +
                "-- <INSTALLED, pkg:MockEmptyPackage 'type1' rev=1>\n" +
                "-- <NEW, pkg:MockEmptyPackage 'type1' rev=2>\n",
               getTree(m));
    }

    public void testSortBySource_InstallPackage() {
        assertTrue(m.mAllPkgItems.isEmpty());

        // First load reveals a new package
        SdkSource src1 = new SdkRepoSource("http://repo.com/url", "repo1");
        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage(src1, "type1", 1), PkgState.NEW));

        m.sortBySource();

        assertEquals(
                "PkgSourceCategory <source=repo1 (repo.com), #items=1>\n" +
                "-- <NEW, pkg:MockEmptyPackage 'type1' rev=1>\n",
               getTree(m));

        // Install it. The display only shows the installed one, 'hiding' the remote package
        m.mAllPkgItems.clear();
        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage(src1, "type1", 1), PkgState.INSTALLED));
        m.sortBySource();
        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage(src1, "type1", 1), PkgState.NEW));
        m.sortBySource();

        assertEquals(
                "PkgSourceCategory <source=repo1 (repo.com), #items=1>\n" +
                "-- <INSTALLED, pkg:MockEmptyPackage 'type1' rev=1>\n",
               getTree(m));

        // Now we have an update
        m.mAllPkgItems.clear();
        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage(src1, "type1", 1), PkgState.INSTALLED));
        m.sortBySource();
        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage(src1, "type1", 2), PkgState.NEW));
        m.sortBySource();

        assertEquals(
                "PkgSourceCategory <source=repo1 (repo.com), #items=1>\n" +
                "-- <INSTALLED, pkg:MockEmptyPackage 'type1' rev=1, updated by:MockEmptyPackage 'type1' rev=2>\n",
               getTree(m));
    }

    public void testSortBySource_DeletePackage() {
        assertTrue(m.mAllPkgItems.isEmpty());

        // Start with an installed package and its matching remote package
        SdkSource src1 = new SdkRepoSource("http://repo.com/url", "repo1");
        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage(src1, "type1", 1), PkgState.INSTALLED));
        m.sortBySource();
        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage(src1, "type1", 1), PkgState.NEW));
        m.sortBySource();

        assertEquals(
                "PkgSourceCategory <source=repo1 (repo.com), #items=1>\n" +
                "-- <INSTALLED, pkg:MockEmptyPackage 'type1' rev=1>\n",
               getTree(m));

        // User now deletes the installed package.
        m.mAllPkgItems.clear();
        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage(src1, "type1", 1), PkgState.NEW));
        m.sortBySource();

        assertEquals(
                "PkgSourceCategory <source=repo1 (repo.com), #items=1>\n" +
                "-- <NEW, pkg:MockEmptyPackage 'type1' rev=1>\n",
               getTree(m));
    }

    public void testSortBySource_CompleteUpdate() {
        assertTrue(m.mAllPkgItems.isEmpty());

        // Typical case is to have these 2 tools
        SdkSource src1 = new SdkRepoSource("http://repo.com/url1", "repo1");
        m.mAllPkgItems.add(new PkgItem(new MockToolPackage(src1, 10, 3),     PkgState.INSTALLED));
        m.mAllPkgItems.add(new PkgItem(new MockPlatformToolPackage(src1, 3), PkgState.INSTALLED));

        // Load a few extra packages
        m.mAllPkgItems.add(
            new PkgItem(new MockExtraPackage(src1, "carrier", "custom_rom", 1, 0), PkgState.NEW));

        // We call sortBySource() multiple times to simulate the fact it works as an
        // incremental diff. In real usage, it is called after each source is loaded so
        // that we can progressively update the display.
        m.sortBySource();

        assertEquals(
                "PkgSourceCategory <source=repo1 (repo.com), #items=3>\n" +
                "-- <INSTALLED, pkg:Android SDK Tools, revision 10>\n" +
                "-- <INSTALLED, pkg:Android SDK Platform-tools, revision 3>\n" +
                "-- <NEW, pkg:Carrier Custom Rom package, revision 1>\n",
               getTree(m));

        // Source 2 only provides the addon, which is already installed so the source
        // should be empty.
        SdkSource src2 = new SdkRepoSource("http://repo.com/url2", "repo2");
        m.mAllPkgItems.add(new PkgItem(
                new MockExtraPackage(src2, "android", "usb_driver", 4, 3), PkgState.INSTALLED));
        m.mAllPkgItems.add(new PkgItem(
                new MockExtraPackage(src2, "android", "usb_driver", 4, 3), PkgState.NEW));

        m.sortBySource();

        assertEquals(
                "PkgSourceCategory <source=repo1 (repo.com), #items=3>\n" +
                "-- <INSTALLED, pkg:Android SDK Tools, revision 10>\n" +
                "-- <INSTALLED, pkg:Android SDK Platform-tools, revision 3>\n" +
                "-- <NEW, pkg:Carrier Custom Rom package, revision 1>\n" +
                "PkgSourceCategory <source=repo2 (repo.com), #items=1>\n" +
                "-- <INSTALLED, pkg:Android USB Driver package, revision 4>\n",
               getTree(m));

        // When an update is available, it is still merged with the installed item
        m.mAllPkgItems.add(new PkgItem(
                new MockExtraPackage(src2, "android", "usb_driver", 6, 4), PkgState.NEW));

        m.sortBySource();

        assertEquals(
                "PkgSourceCategory <source=repo1 (repo.com), #items=3>\n" +
                "-- <INSTALLED, pkg:Android SDK Tools, revision 10>\n" +
                "-- <INSTALLED, pkg:Android SDK Platform-tools, revision 3>\n" +
                "-- <NEW, pkg:Carrier Custom Rom package, revision 1>\n" +
                "PkgSourceCategory <source=repo2 (repo.com), #items=1>\n" +
                "-- <INSTALLED, pkg:Android USB Driver package, revision 4, updated by:Android USB Driver package, revision 6>\n" ,
               getTree(m));


        // Now add a few Platforms

        SdkSource src3 = new SdkRepoSource("http://repo.com/url3", "repo3");
        MockPlatformPackage p1;
        MockPlatformPackage p2;
        m.mAllPkgItems.add(new PkgItem(
                p1 = new MockPlatformPackage(src2, 1, 2, 3), PkgState.INSTALLED));
        m.mAllPkgItems.add(new PkgItem(
                p2 = new MockPlatformPackage(src3, 2, 4, 3), PkgState.NEW));
        m.mAllPkgItems.add(new PkgItem(
                     new MockPlatformPackage(src2, 3, 6, 3), PkgState.INSTALLED));

        m.sortBySource();

        assertEquals(
                "PkgSourceCategory <source=repo1 (repo.com), #items=3>\n" +
                "-- <INSTALLED, pkg:Android SDK Tools, revision 10>\n" +
                "-- <INSTALLED, pkg:Android SDK Platform-tools, revision 3>\n" +
                "-- <NEW, pkg:Carrier Custom Rom package, revision 1>\n" +
                "PkgSourceCategory <source=repo2 (repo.com), #items=3>\n" +
                "-- <INSTALLED, pkg:SDK Platform Android android-3, API 3, revision 6>\n"+
                "-- <INSTALLED, pkg:SDK Platform Android android-1, API 1, revision 2>\n" +
                "-- <INSTALLED, pkg:Android USB Driver package, revision 4, updated by:Android USB Driver package, revision 6>\n" +
                "PkgSourceCategory <source=repo3 (repo.com), #items=1>\n" +
                "-- <NEW, pkg:SDK Platform Android android-2, API 2, revision 4>\n",
               getTree(m));

        // Add a bunch of add-ons and sort them.
        // Note that for source 4, the order is BCA since we order first by decreasing API
        // and then by increasing add-on name.
        SdkSource src4 = new SdkRepoSource("http://repo.com/url4", "repo4");
        m.mAllPkgItems.add(new PkgItem(
                new MockAddonPackage(src4, "addon C", p2, 9), PkgState.NEW));
        m.mAllPkgItems.add(new PkgItem(
                new MockAddonPackage(src4, "addon A", p1, 5), PkgState.INSTALLED));
        m.mAllPkgItems.add(new PkgItem(
                new MockAddonPackage(src4, "addon A", p1, 6), PkgState.NEW));
        m.mAllPkgItems.add(new PkgItem(
                new MockAddonPackage(src4, "addon B", p2, 7), PkgState.NEW));
        // the rev 8 update will be ignored since there's a rev 9 coming after
        m.mAllPkgItems.add(new PkgItem(
                new MockAddonPackage(src4, "addon B", p2, 8), PkgState.NEW));
        m.mAllPkgItems.add(new PkgItem(
                new MockAddonPackage(src4, "addon B", p2, 9), PkgState.NEW));

        m.sortBySource();

        assertEquals(
                "PkgSourceCategory <source=repo1 (repo.com), #items=3>\n" +
                "-- <INSTALLED, pkg:Android SDK Tools, revision 10>\n" +
                "-- <INSTALLED, pkg:Android SDK Platform-tools, revision 3>\n" +
                "-- <NEW, pkg:Carrier Custom Rom package, revision 1>\n" +
                "PkgSourceCategory <source=repo2 (repo.com), #items=3>\n" +
                "-- <INSTALLED, pkg:SDK Platform Android android-3, API 3, revision 6>\n"+
                "-- <INSTALLED, pkg:SDK Platform Android android-1, API 1, revision 2>\n" +
                "-- <INSTALLED, pkg:Android USB Driver package, revision 4, updated by:Android USB Driver package, revision 6>\n" +
                "PkgSourceCategory <source=repo3 (repo.com), #items=1>\n" +
                "-- <NEW, pkg:SDK Platform Android android-2, API 2, revision 4>\n" +
                "PkgSourceCategory <source=repo4 (repo.com), #items=3>\n" +
                "-- <NEW, pkg:addon B by vendor 2, Android API 2, revision 7, updated by:addon B by vendor 2, Android API 2, revision 9>\n" +
                "-- <NEW, pkg:addon C by vendor 2, Android API 2, revision 9>\n" +
                "-- <INSTALLED, pkg:addon A by vendor 1, Android API 1, revision 5, updated by:addon A by vendor 1, Android API 1, revision 6>\n",
               getTree(m));

        // Now simulate a change of sorting algorithm: sort by source then by API again.
        m.sortByApiLevel();
        m.sortBySource();

        assertEquals(
                "PkgSourceCategory <source=repo1 (repo.com), #items=3>\n" +
                "-- <INSTALLED, pkg:Android SDK Tools, revision 10>\n" +
                "-- <INSTALLED, pkg:Android SDK Platform-tools, revision 3>\n" +
                "-- <NEW, pkg:Carrier Custom Rom package, revision 1>\n" +
                "PkgSourceCategory <source=repo2 (repo.com), #items=3>\n" +
                "-- <INSTALLED, pkg:SDK Platform Android android-3, API 3, revision 6>\n"+
                "-- <INSTALLED, pkg:SDK Platform Android android-1, API 1, revision 2>\n" +
                "-- <INSTALLED, pkg:Android USB Driver package, revision 4, updated by:Android USB Driver package, revision 6>\n" +
                "PkgSourceCategory <source=repo3 (repo.com), #items=1>\n" +
                "-- <NEW, pkg:SDK Platform Android android-2, API 2, revision 4>\n" +
                "PkgSourceCategory <source=repo4 (repo.com), #items=3>\n" +
                "-- <NEW, pkg:addon B by vendor 2, Android API 2, revision 7, updated by:addon B by vendor 2, Android API 2, revision 9>\n" +
                "-- <NEW, pkg:addon C by vendor 2, Android API 2, revision 9>\n" +
                "-- <INSTALLED, pkg:addon A by vendor 1, Android API 1, revision 5, updated by:addon A by vendor 1, Android API 1, revision 6>\n",
               getTree(m));
    }

    // ----

    /**
     * Simulates the display we would have in the Packages Tree.
     * This always depends on mCurrentCategories like the tree does.
     * The display format is something like:
     * <pre>
     *   PkgCategory &lt;description&gt;
     *   -- &lt;PkgItem description&gt;
     * </pre>
     */
    public String getTree(PackagesPageLogic l) {
        StringBuilder sb = new StringBuilder();

        for (PkgCategory cat : l.mCurrentCategories) {
            sb.append(cat.toString()).append('\n');
            for (PkgItem item : cat.getItems()) {
                sb.append("-- ").append(item.toString()).append('\n');
            }
        }

        return sb.toString();
    }
}
