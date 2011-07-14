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
import com.android.sdklib.SdkConstants;

import java.util.Map;
import java.util.Properties;

/**
 * A mock {@link PlatformPackage} for testing.
 *
 * By design, this package contains one and only one archive.
 */
public class MockPlatformPackage extends PlatformPackage {

    private final IAndroidTarget mTarget;

    /**
     * Creates a {@link MockPlatformTarget} with the requested API and revision
     * and then a {@link MockPlatformPackage} wrapping it.
     *
     * By design, this package contains one and only one archive.
     */
    public MockPlatformPackage(int apiLevel, int revision) {
        this(null /*source*/, new MockPlatformTarget(apiLevel, revision), null /*props*/);
    }

    /**
     * Creates a {@link MockPlatformTarget} with the requested API and revision
     * and then a {@link MockPlatformPackage} wrapping it.
     *
     * Also sets the min-tools-rev of the platform.
     *
     * By design, this package contains one and only one archive.
     */
    public MockPlatformPackage(int apiLevel, int revision, int min_tools_rev) {
        this(null /*source*/,
             new MockPlatformTarget(apiLevel, revision),
             createProps(min_tools_rev));
    }

    public MockPlatformPackage(SdkSource source, int apiLevel, int revision, int min_tools_rev) {
        this(source, new MockPlatformTarget(apiLevel, revision), createProps(min_tools_rev));
    }

    /** A little trick to be able to capture the target new after passing it to the super. */
    private MockPlatformPackage(SdkSource source, IAndroidTarget target, Properties props) {
        super(source, target, props);
        mTarget = target;
    }

    private static Properties createProps(int min_tools_rev) {
        Properties props = new Properties();
        props.setProperty(PlatformPackage.PROP_MIN_TOOLS_REV, Integer.toString((min_tools_rev)));
        return props;
    }

    public IAndroidTarget getTarget() {
        return mTarget;
    }

    /**
     * A mock PlatformTarget.
     * This reimplements the minimum needed from the interface for our limited testing needs.
     */
    static class MockPlatformTarget implements IAndroidTarget {

        private final int mApiLevel;
        private final int mRevision;

        public MockPlatformTarget(int apiLevel, int revision) {
            mApiLevel = apiLevel;
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

        public String[] getAbiList() {
            return new String[] { SdkConstants.ABI_ARMEABI };
        }

        public String getImagePath(String abiType) {
            return SdkConstants.OS_IMAGES_FOLDER;
        }

        public String getLocation() {
            return "";
        }

        public IOptionalLibrary[] getOptionalLibraries() {
            return null;
        }

        public IAndroidTarget getParent() {
            return null;
        }

        public String getPath(int pathId) {
            return null;
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

        /**
         * Returns a vendor that depends on the parent *platform* API.
         * This works well in Unit Tests where we'll typically have different
         * platforms as unique identifiers.
         */
        public String getVendor() {
            return "vendor " + Integer.toString(mApiLevel);
        }

        /**
         * Create a synthetic name using the target API level.
         */
        public String getName() {
            return "platform r" + Integer.toString(mApiLevel);
        }

        public AndroidVersion getVersion() {
            return new AndroidVersion(mApiLevel, null /*codename*/);
        }

        public String getVersionName() {
            return String.format("android-%1$d", mApiLevel);
        }

        public String hashString() {
            return getVersionName();
        }

        /** Returns true for a platform. */
        public boolean isPlatform() {
            return true;
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
