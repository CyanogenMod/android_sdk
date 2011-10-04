/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.sdkmanager;


import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.SdkManagerTestCase;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.repository.SdkAddonConstants;
import com.android.sdklib.repository.SdkRepoConstants;
import com.android.util.Pair;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

public class MainTest extends SdkManagerTestCase {

    private IAndroidTarget mTarget;
    private File mAvdFolder;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mTarget = getSdkManager().getTargets()[0];
        mAvdFolder = AvdInfo.getDefaultAvdFolder(getAvdManager(), getName());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    public void testDisplayEmptyAvdList() {
        Main main = new Main();
        main.setLogger(getLog());
        getLog().clear();
        main.displayAvdList(getAvdManager());
        assertEquals("[P Available Android Virtual Devices:\n]", getLog().toString());
    }

    public void testDisplayAvdListOfOneNonSnapshot() {
        Main main = new Main();
        main.setLogger(getLog());
        getAvdManager().createAvd(
                mAvdFolder,
                this.getName(),
                mTarget,
                SdkConstants.ABI_ARMEABI,
                null,   // skinName
                null,   // sdName
                null,   // properties
                false,  // createSnapshot
                false,  // removePrevious
                false,  // editExisting
                getLog());

        getLog().clear();
        main.displayAvdList(getAvdManager());
        assertEquals(
                "[P Available Android Virtual Devices:\n"
                + ", P     Name: " + this.getName() + "\n"
                + ", P     Path: " + mAvdFolder + "\n"
                + ", P   Target: Android 0.0 (API level 0)\n"
                + ", P      ABI: armeabi\n"
                + ", P     Skin: HVGA\n"
                + "]",
                getLog().toString());
    }

    public void testDisplayAvdListOfOneSnapshot() {
        Main main = new Main();
        main.setLogger(getLog());

        getAvdManager().createAvd(
                mAvdFolder,
                this.getName(),
                mTarget,
                SdkConstants.ABI_ARMEABI,
                null,   // skinName
                null,   // sdName
                null,   // properties
                true,  // createSnapshot
                false,  // removePrevious
                false,  // editExisting
                getLog());

        getLog().clear();
        main.displayAvdList(getAvdManager());
        assertEquals(
                "[P Available Android Virtual Devices:\n"
                + ", P     Name: " + this.getName() + "\n"
                + ", P     Path: " + mAvdFolder + "\n"
                + ", P   Target: Android 0.0 (API level 0)\n"
                + ", P      ABI: armeabi\n"
                + ", P     Skin: HVGA\n"
                + ", P Snapshot: true\n"
                + "]",
                getLog().toString());
    }

    public void testDisplayTargetList() {
        Main main = new Main();
        main.setLogger(getLog());
        main.setSdkManager(getSdkManager());
        getLog().clear();
        main.displayTargetList();
        assertEquals(
                "[P Available Android targets:\n" +
                ", P ----------\n" +
                ", P id: 1 or \"android-0\"\n" +
                ", P      Name: Android 0.0\n" +
                ", P      Type: Platform\n" +
                ", P      API level: 0\n" +
                ", P      Revision: 1\n" +
                ", P      Skins: , P \n" +
                ", P      ABIs : , P armeabi, P \n" +
                "]",
                getLog().toString());
    }

    public void testDisplayAbiList() {
        Main main = new Main();
        main.setLogger(getLog());
        main.setSdkManager(getSdkManager());
        getLog().clear();
        main.displayAbiList(mTarget, "message");
        assertEquals(
                "[P message, P armeabi, P \n" +
                "]",
                getLog().toString());
    }

    public void testDisplaySkinList() {
        Main main = new Main();
        main.setLogger(getLog());
        main.setSdkManager(getSdkManager());
        getLog().clear();
        main.displaySkinList(mTarget, "message");
        assertEquals(
                "[P message, P \n" +
                "]",
                getLog().toString());
    }

    public void testCheckFilterValues() {
        // These are the values we expect checkFilterValues() to match.
        String[] expectedValues = {
                "platform",
                "system-image",
                "tool",
                "platform-tool",
                "doc",
                "sample",
                "add-on",
                "extra",
                "source"
        };

        Set<String> expectedSet = new TreeSet<String>(Arrays.asList(expectedValues));

        // First check the values are actually defined in the proper arrays
        // in the Sdk*Constants.NODES
        for (String node : SdkRepoConstants.NODES) {
            assertTrue(
                String.format(
                    "Error: value '%1$s' from SdkRepoConstants.NODES should be used in unit-test",
                    node),
                expectedSet.contains(node));
        }
        for (String node : SdkAddonConstants.NODES) {
            assertTrue(
                String.format(
                    "Error: value '%1$s' from SdkAddonConstants.NODES should be used in unit-test",
                    node),
                expectedSet.contains(node));
        }

        // Now check none of these values are NOT present in the NODES arrays
        for (String node : SdkRepoConstants.NODES) {
            expectedSet.remove(node);
        }
        for (String node : SdkAddonConstants.NODES) {
            expectedSet.remove(node);
        }
        assertTrue(
            String.format(
                    "Error: values %1$s are missing from Sdk[Repo|Addons]Constants.NODES",
                    Arrays.toString(expectedSet.toArray())),
            expectedSet.isEmpty());

        // We're done with expectedSet now
        expectedSet = null;

        // Finally check that checkFilterValues accepts all these values, one by one.
        Main main = new Main();
        main.setLogger(getLog());

        for (int step = 0; step < 3; step++) {
            for (String value : expectedValues) {
                switch(step) {
                // step 0: use value as-is
                case 1:
                    // add some whitespace before and after
                    value = "  " + value + "   ";
                    break;
                case 2:
                    // same with some empty arguments that should get ignored
                    value = "  ," + value + " ,  ";
                    break;
                    }

                Pair<String, ArrayList<String>> result = main.checkFilterValues(value);
                assertNull(
                        String.format("Expected error to be null for value '%1$s', got: %2$s",
                                value, result.getFirst()),
                        result.getFirst());
                assertEquals(
                        String.format("[%1$s]", value.replace(',', ' ').trim()),
                        Arrays.toString(result.getSecond().toArray()));
            }
        }
    }
}
