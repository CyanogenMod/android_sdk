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

/**
 * A mock PlatformTarget.
 * This reimplements the minimum needed from the interface for our limited testing needs.
 */
class MockPlatformTarget implements IAndroidTarget {

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
