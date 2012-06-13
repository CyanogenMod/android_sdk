/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.sdklib.devices;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.android.annotations.Nullable;
import com.android.prefs.AndroidLocation;
import com.android.prefs.AndroidLocation.AndroidLocationException;
import com.android.sdklib.ISdkLog;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.repository.PkgProps;

/**
 * Manager class for interacting with {@link Device}s within the SDK
 */
public class DeviceManager {

    private final static String sDeviceProfilesProp = "DeviceProfiles";
    private final static Pattern sPathPropertyPattern = Pattern.compile("^" + PkgProps.EXTRA_PATH
            + "=" + sDeviceProfilesProp + "$");

    private ISdkLog mLog;
    private List<Device> mVendorDevices;
    private List<Device> mUserDevices;

    public DeviceManager(ISdkLog log) {
        mLog = log;
    }

    /**
     * Returns both vendor provided and user created {@link Device}s.
     *
     * @param sdkLocation
     *            Location of the Android SDK
     * @return A list of both vendor and user provided {@link Device}s
     */
    public List<Device> getVendorAndUserDevices(String sdkLocation) {
        List<Device> devices = new ArrayList<Device>(getVendorDevices(sdkLocation));
        devices.addAll(getUserDevices());
        return devices;
    }

    /**
     * Returns all vendor provided {@link Device}s
     *
     * @param sdkLocation
     *            Location of the Android SDK
     * @return A list of vendor provided {@link Device}s
     */
    public List<Device> getVendorDevices(String sdkLocation) {
        synchronized (mVendorDevices) {
            if (mVendorDevices == null) {
                List<Device> devices = new ArrayList<Device>();
                File extrasFolder = new File(sdkLocation, SdkConstants.FD_EXTRAS);
                List<File> deviceDirs = getExtraDirs(extrasFolder);
                for (File deviceDir : deviceDirs) {
                    File deviceXml = new File(deviceDir, SdkConstants.FN_DEVICES_XML);
                    if (deviceXml.isFile()) {
                        devices.addAll(loadDevices(deviceXml));
                    }
                }
                mVendorDevices = devices;
            }
        }
        return mVendorDevices;
    }

    /**
     * Returns all user created {@link Device}s
     *
     * @return All user created {@link Device}s
     */
    public List<Device> getUserDevices() {
        synchronized (mUserDevices) {
            if (mUserDevices == null) {
                // User devices should be saved out to
                // $HOME/.android/devices.xml
                mUserDevices = new ArrayList<Device>();
                try {
                    File userDevicesFile = new File(AndroidLocation.getFolder(),
                            SdkConstants.FN_DEVICES_XML);
                    mUserDevices.addAll(loadDevices(userDevicesFile));
                } catch (AndroidLocationException e) {
                    mLog.warning("Couldn't load user devices: %1$", e.getMessage());
                }
            }
        }
        return mUserDevices;
    }

    private Collection<Device> loadDevices(File deviceXml) {
        try {
            return DeviceParser.parse(deviceXml);
        } catch (SAXException e) {
            mLog.error(null, "Error parsing %1$", deviceXml.getAbsolutePath());
        } catch (ParserConfigurationException e) {
            mLog.error(null, "Error parsing %1$", deviceXml.getAbsolutePath());
        } catch (IOException e) {
            mLog.error(null, "Error reading %1$", deviceXml.getAbsolutePath());
        } catch (IllegalStateException e) {
            // The device builders can throw IllegalStateExceptions if
            // build gets called before everything is properly setup
            mLog.error(e, null);
        }
        return new ArrayList<Device>();
    }

    /* Returns all of DeviceProfiles in the extras/ folder */
    private List<File> getExtraDirs(File extrasFolder) {
        List<File> extraDirs = new ArrayList<File>();
        // All OEM provided device profiles are in
        // $SDK/extras/$VENDOR/$ITEM/devices.xml
        if (extrasFolder != null && extrasFolder.isDirectory()) {
            for (File vendor : extrasFolder.listFiles()) {
                if (vendor.isDirectory()) {
                    for (File item : vendor.listFiles()) {
                        if (item.isDirectory() && isDevicesExtra(item)) {
                            extraDirs.add(item);
                        }
                    }
                }
            }
        }

        return extraDirs;
    }

    /*
     * Returns whether a specific folder for a specific vendor is a
     * DeviceProfiles folder
     */
    private boolean isDevicesExtra(File item) {
        File properties = new File(item, SdkConstants.FN_SOURCE_PROP);
        try {
            BufferedReader propertiesReader = new BufferedReader(new FileReader(properties));
            String line;
            while ((line = propertiesReader.readLine()) != null) {
                Matcher m = sPathPropertyPattern.matcher(line);
                if (m.matches()) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }
}
