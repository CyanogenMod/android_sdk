/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.sdklib.internal.repository;

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.SystemImage;
import com.android.sdklib.ISystemImage.LocationType;
import com.android.sdklib.io.FileOp;

import java.util.Map;

/**
 * A mock {@link AddonPackage} for testing.
 *
 * By design, this package contains one and only one archive.
 */
public class MockAddonPackage extends AddonPackage {

    /**
     * Creates a {@link MockAddonTarget} with the requested base platform and addon revision
     * and then a {@link MockAddonPackage} wrapping it and a default name of "addon".
     *
     * By design, this package contains one and only one archive.
     */
    public MockAddonPackage(MockPlatformPackage basePlatform, int revision) {
        this("addon", basePlatform, revision); //$NON-NLS-1$
    }

    /**
     * Creates a {@link MockAddonTarget} with the requested base platform and addon revision
     * and then a {@link MockAddonPackage} wrapping it.
     *
     * By design, this package contains one and only one archive.
     */
    public MockAddonPackage(String name, MockPlatformPackage basePlatform, int revision) {
        super(new MockAddonTarget(name, basePlatform.getTarget(), revision), null /*props*/);
    }

    public MockAddonPackage(
            SdkSource source,
            String name,
            MockPlatformPackage basePlatform,
            int revision) {
        super(source,
              new MockAddonTarget(name, basePlatform.getTarget(), revision),
              null /*props*/);
    }

    /**
     * A mock AddonTarget.
     * This reimplements the minimum needed from the interface for our limited testing needs.
     */
    static class MockAddonTarget implements IAndroidTarget {

        private final IAndroidTarget mParentTarget;
        private final int mRevision;
        private final String mName;
        private ISystemImage[] mSystemImages;

        public MockAddonTarget(String name, IAndroidTarget parentTarget, int revision) {
            mName = name;
            mParentTarget = parentTarget;
            mRevision = revision;
        }

        public String getClasspathName() {
            return getName();
        }

        public String getShortClasspathName() {
            return getName();
        }

        public String getDefaultSkin() {
            return null;
        }

        public String getDescription() {
            return getName();
        }

        public String getFullName() {
            return getName();
        }

        public ISystemImage[] getSystemImages() {
            if (mSystemImages == null) {
                SystemImage si = new SystemImage(
                        FileOp.append(getLocation(), SdkConstants.OS_IMAGES_FOLDER),
                        LocationType.IN_PLATFORM_LEGACY,
                        SdkConstants.ABI_ARMEABI);
                mSystemImages = new SystemImage[] { si };
            }
            return mSystemImages;
        }

        public ISystemImage getSystemImage(String abiType) {
            if (SdkConstants.ABI_ARMEABI.equals(abiType)) {
                return getSystemImages()[0];
            }
            return null;
        }

        public String getLocation() {
            return "/sdk/add-ons/addon-" + mName;
        }

        public IOptionalLibrary[] getOptionalLibraries() {
            return null;
        }

        public IAndroidTarget getParent() {
            return mParentTarget;
        }

        public String getPath(int pathId) {
            throw new UnsupportedOperationException("Implement this as needed for tests");
        }

        public String[] getPlatformLibraries() {
            return null;
        }

        public String getProperty(String name) {
            return null;
        }

        public Integer getProperty(String name, Integer defaultValue) {
            return defaultValue;
        }

        public Boolean getProperty(String name, Boolean defaultValue) {
            return defaultValue;
        }

        public Map<String, String> getProperties() {
            return null;
        }

        public int getRevision() {
            return mRevision;
        }

        public String[] getSkins() {
            return null;
        }

        public int getUsbVendorId() {
            return 0;
        }

        public AndroidVersion getVersion() {
            return mParentTarget.getVersion();
        }

        public String getName() {
            return mName;
        }

        public String getVendor() {
            return mParentTarget.getVendor();
        }

        public String getVersionName() {
            return String.format("mock-addon-%1$d", getVersion().getApiLevel());
        }

        public String hashString() {
            return getVersionName();
        }

        /** Returns false for an addon. */
        public boolean isPlatform() {
            return false;
        }

        public boolean canRunOn(IAndroidTarget target) {
            throw new UnsupportedOperationException("Implement this as needed for tests");
        }

        public int compareTo(IAndroidTarget o) {
            throw new UnsupportedOperationException("Implement this as needed for tests");
        }

        public boolean hasRenderingLibrary() {
            return false;
        }
    }
}
