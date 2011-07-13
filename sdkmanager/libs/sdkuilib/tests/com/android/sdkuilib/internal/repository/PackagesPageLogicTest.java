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

    public void testSortByApi_Empty() {
        assertTrue(m.mAllPkgItems.isEmpty());
        m.sortByApiLevel();
        assertSame(m.mCurrentCategories, m.mApiCategories);
        assertTrue(m.mApiCategories.isEmpty());
    }

    public void testSortByApi_AddPackages() {
        assertTrue(m.mAllPkgItems.isEmpty());
        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage("1_new"), PkgState.NEW));
        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage("2_installed"), PkgState.INSTALLED));

        m.sortByApiLevel();

        assertEquals(
                "PkgApiCategory <API=EXTRAS, label=Extras, #items=2>\n" +
                "-- <NEW, pkg:MockEmptyPackage>\n" +
                "-- <INSTALLED, pkg:MockEmptyPackage>\n",
               getTree(m));
    }

    public void testSortByApi_Update1() {
        assertTrue(m.mAllPkgItems.isEmpty());

        // Typical case: user has a locally installed package in revision 1
        // The display list after sort should show that instaled package.
        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage("type1", 1), PkgState.INSTALLED));

        m.sortByApiLevel();

        assertEquals(
                "PkgApiCategory <API=EXTRAS, label=Extras, #items=1>\n" +
                "-- <INSTALLED, pkg:MockEmptyPackage rev=1>\n",
               getTree(m));

        // Then loading sources reveals an update in revision 4
        // Edge case: another source reveals an update in revision 2.
        // The display list after sort should show an update as available with rev 4
        // and rev 2 should be ignored since we have a better one.
        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage("type1", 4), PkgState.NEW));
        m.mAllPkgItems.add(new PkgItem(new MockEmptyPackage("type1", 2), PkgState.NEW));

        m.sortByApiLevel();

        assertEquals(
                "PkgApiCategory <API=EXTRAS, label=Extras, #items=1>\n" +
                "-- <INSTALLED, pkg:MockEmptyPackage rev=1, updated by:MockEmptyPackage rev=4>\n",
               getTree(m));
    }

    public void testSortByApi_CompleteUpdate() {
        assertTrue(m.mAllPkgItems.isEmpty());

        // Resulting categories are sorted by Tools, descending platform API and finally Extras.
        // Addons are sorted by name within their API.
        // Extras are sorted by vendor name.
        // The order packages are added to the mAllPkgItems list is purposedly different from
        // the final order we get.

        // Typical case is to have these 2 tools, which should get sorted in their own category
        m.mAllPkgItems.add(new PkgItem(new MockToolPackage(10, 3), PkgState.INSTALLED));
        m.mAllPkgItems.add(new PkgItem(new MockPlatformToolPackage(3), PkgState.INSTALLED));

        // Load a few extra packages
        m.mAllPkgItems.add(new PkgItem(new MockExtraPackage("carrier", "custom_rom", 1, 0), PkgState.NEW));

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

        m.mAllPkgItems.add(new PkgItem(new MockExtraPackage("android", "usb_driver", 4, 3), PkgState.INSTALLED));
        m.mAllPkgItems.add(new PkgItem(new MockExtraPackage("android", "usb_driver", 5, 3), PkgState.NEW));

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
        m.mAllPkgItems.add(new PkgItem(p1 = new MockPlatformPackage(1, 2, 3), PkgState.INSTALLED));
        m.mAllPkgItems.add(new PkgItem(p2 = new MockPlatformPackage(2, 4, 3), PkgState.NEW));
        m.mAllPkgItems.add(new PkgItem(     new MockPlatformPackage(3, 6, 3), PkgState.INSTALLED));

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

        m.mAllPkgItems.add(new PkgItem(new MockAddonPackage("addon C",p2, 9), PkgState.NEW));
        m.mAllPkgItems.add(new PkgItem(new MockAddonPackage("addon A", p1, 5), PkgState.INSTALLED));
        m.mAllPkgItems.add(new PkgItem(new MockAddonPackage("addon A", p1, 6), PkgState.NEW));
        m.mAllPkgItems.add(new PkgItem(new MockAddonPackage("addon B",p2, 7), PkgState.NEW));
        m.mAllPkgItems.add(new PkgItem(new MockAddonPackage("addon B",p2, 8), PkgState.NEW)); // this update is ignored
        m.mAllPkgItems.add(new PkgItem(new MockAddonPackage("addon B",p2, 9), PkgState.NEW)); // this updates new addon B in rev 7

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

        // The only requirement is that updates MUST appear AFTER the packages they will update.
        // The reverse order is not supported by the sorting algorithm and both will be shown.

        m.mAllPkgItems.add(new PkgItem(new MockAddonPackage("addon D",p2, 15), PkgState.NEW));
        m.mAllPkgItems.add(new PkgItem(new MockAddonPackage("addon D",p2, 14), PkgState.INSTALLED));

        m.sortByApiLevel();

        assertEquals(
                "PkgApiCategory <API=TOOLS, label=Tools, #items=2>\n" +
                "-- <INSTALLED, pkg:Android SDK Tools, revision 10>\n" +
                "-- <INSTALLED, pkg:Android SDK Platform-tools, revision 3>\n" +
                "PkgApiCategory <API=API 3, label=Android android-3 (API 3), #items=1>\n" +
                "-- <INSTALLED, pkg:SDK Platform Android android-3, API 3, revision 6>\n" +
                "PkgApiCategory <API=API 2, label=Android android-2 (API 2), #items=5>\n" +
                "-- <NEW, pkg:SDK Platform Android android-2, API 2, revision 4>\n" +
                "-- <NEW, pkg:addon B by vendor 2, Android API 2, revision 7, updated by:addon B by vendor 2, Android API 2, revision 9>\n" +
                "-- <NEW, pkg:addon C by vendor 2, Android API 2, revision 9>\n" +
                "-- <INSTALLED, pkg:addon D by vendor 2, Android API 2, revision 14>\n" +
                "-- <NEW, pkg:addon D by vendor 2, Android API 2, revision 15>\n" +
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
