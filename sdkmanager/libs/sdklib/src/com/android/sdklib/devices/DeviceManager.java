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

import com.android.prefs.AndroidLocation;
import com.android.prefs.AndroidLocation.AndroidLocationException;
import com.android.resources.Keyboard;
import com.android.resources.KeyboardState;
import com.android.resources.Navigation;
import com.android.sdklib.ISdkLog;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.devices.Storage.Unit;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.internal.avd.HardwareProperties;
import com.android.sdklib.repository.PkgProps;

import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactoryConfigurationError;

/**
 * Manager class for interacting with {@link Device}s within the SDK
 */
public class DeviceManager {

    private final static String sDeviceProfilesProp = "DeviceProfiles";
    private final static Pattern sPathPropertyPattern = Pattern.compile("^" + PkgProps.EXTRA_PATH
            + "=" + sDeviceProfilesProp + "$");
    private ISdkLog mLog;
    // Vendor devices can't be a static list since they change based on the SDK
    // Location
    private List<Device> mVendorDevices;
    // Keeps track of where the currently loaded vendor devices were loaded from
    private String mVendorDevicesLocation = "";
    private static List<Device> mUserDevices;
    private static List<Device> mDefaultDevices;
    private static final Object lock = new Object();
    private static final List<DevicesChangeListener> listeners =
        Collections.synchronizedList(new ArrayList<DevicesChangeListener>());

    // TODO: Refactor this to look more like AvdManager so that we don't have
    // multiple instances
    // in the same application, which forces us to parse the XML multiple times
    // when we don't
    // to.
    public DeviceManager(ISdkLog log) {
        mLog = log;
    }

    /**
     * Interface implemented by objects which want to know when changes occur to the {@link Device}
     * lists.
     */
    public static interface DevicesChangeListener {
        /**
         * Called after one of the {@link Device} lists has been updated.
         */
        public void onDevicesChange();
    }

    /**
     * Register a listener to be notified when the device lists are modified.
     * @param listener The listener to add
     */
    public void registerListener(DevicesChangeListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a listener from the notification list such that it will no longer receive
     * notifications when modifications to the {@link Device} list occur.
     * @param listener The listener to remove.
     */
    public void unregisterListener(DevicesChangeListener listener) {
        listeners.remove(listener);
    }

    /**
     * Returns both vendor provided and user created {@link Device}s.
     * 
     * @param sdkLocation Location of the Android SDK
     * @return A list of both vendor and user provided {@link Device}s
     */
    public List<Device> getDevices(String sdkLocation) {
        List<Device> devices = new ArrayList<Device>(getVendorDevices(sdkLocation));
        devices.addAll(getDefaultDevices());
        devices.addAll(getUserDevices());
        return Collections.unmodifiableList(devices);
    }

    /**
     * Gets the {@link List} of {@link Device}s packaged with the SDK.
     * 
     * @return The {@link List} of default {@link Device}s
     */
    public List<Device> getDefaultDevices() {
        synchronized (lock) {
            if (mDefaultDevices == null) {
                try {
                    mDefaultDevices = DeviceParser.parse(
                            DeviceManager.class.getResourceAsStream(SdkConstants.FN_DEVICES_XML));
                } catch (IllegalStateException e) {
                    // The device builders can throw IllegalStateExceptions if
                    // build gets called before everything is properly setup
                    mLog.error(e, null);
                    mDefaultDevices = new ArrayList<Device>();
                } catch (Exception e) {
                    mLog.error(null, "Error reading default devices");
                    mDefaultDevices = new ArrayList<Device>();
                }
                notifyListeners();
            }
        }
        return Collections.unmodifiableList(mDefaultDevices);
    }

    /**
     * Returns all vendor provided {@link Device}s
     * 
     * @param sdkLocation Location of the Android SDK
     * @return A list of vendor provided {@link Device}s
     */
    public List<Device> getVendorDevices(String sdkLocation) {
        synchronized (lock) {
            if (mVendorDevices == null || !mVendorDevicesLocation.equals(sdkLocation)) {
                mVendorDevicesLocation = sdkLocation;
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
                notifyListeners();
            }
        }
        return Collections.unmodifiableList(mVendorDevices);
    }

    /**
     * Returns all user created {@link Device}s
     * 
     * @return All user created {@link Device}s
     */
    public List<Device> getUserDevices() {
        synchronized (lock) {
            if (mUserDevices == null) {
                // User devices should be saved out to
                // $HOME/.android/devices.xml
                mUserDevices = new ArrayList<Device>();
                File userDevicesFile = null;
                try {
                    userDevicesFile = new File(AndroidLocation.getFolder(),
                            SdkConstants.FN_DEVICES_XML);
                    mUserDevices.addAll(DeviceParser.parse(userDevicesFile));
                    notifyListeners();
                } catch (AndroidLocationException e) {
                    mLog.warning("Couldn't load user devices: %1$s", e.getMessage());
                } catch (SAXException e) {
                    // Probably an old config file which we don't want to overwrite.
                    String base = userDevicesFile.getAbsoluteFile()+".old";
                    File renamedConfig = new File(base);
                    int i = 0;
                    while (renamedConfig.exists()) {
                        renamedConfig = new File(base+"."+i);
                    }
                    mLog.error(null, "Error parsing %1$s, backing up to %2$s",
                            userDevicesFile.getAbsolutePath(), renamedConfig.getAbsolutePath());
                    userDevicesFile.renameTo(renamedConfig);
                } catch (ParserConfigurationException e) {
                    mLog.error(null, "Error parsing %1$s", userDevicesFile.getAbsolutePath());
                } catch (IOException e) {
                    mLog.error(null, "Error parsing %1$s", userDevicesFile.getAbsolutePath());
                }
            }
        }
        return Collections.unmodifiableList(mUserDevices);
    }

    public void addUserDevice(Device d) {
        synchronized (lock) {
            if (mUserDevices == null) {
                getUserDevices();
            }
            mUserDevices.add(d);
        }
        notifyListeners();
    }

    public void removeUserDevice(Device d) {
        synchronized (lock) {
            if (mUserDevices == null) {
                getUserDevices();
            }
            Iterator<Device> it = mUserDevices.iterator();
            while (it.hasNext()) {
                Device userDevice = it.next();
                if (userDevice.getName().equals(d.getName())
                        && userDevice.getManufacturer().equals(d.getManufacturer())) {
                    it.remove();
                    notifyListeners();
                    break;
                }

            }
        }
    }

    public void replaceUserDevice(Device d) {
        synchronized (lock) {
            if (mUserDevices == null) {
                getUserDevices();
            }
            removeUserDevice(d);
            addUserDevice(d);
        }
    }

    /**
     * Saves out the user devices to {@link SdkConstants#FN_DEVICES_XML} in
     * {@link AndroidLocation#getFolder()}.
     */
    public void saveUserDevices() {
        synchronized (lock) {
            if (mUserDevices != null && mUserDevices.size() != 0) {
                File userDevicesFile;
                try {
                    userDevicesFile = new File(AndroidLocation.getFolder(),
                            SdkConstants.FN_DEVICES_XML);
                    DeviceWriter.writeToXml(new FileOutputStream(userDevicesFile), mUserDevices);
                } catch (AndroidLocationException e) {
                    mLog.warning("Couldn't find user directory: %1$s", e.getMessage());
                } catch (FileNotFoundException e) {
                    mLog.warning("Couldn't open file: %1$s", e.getMessage());
                } catch (ParserConfigurationException e) {
                    mLog.warning("Error writing file: %1$s", e.getMessage());
                } catch (TransformerFactoryConfigurationError e) {
                    mLog.warning("Error writing file: %1$s", e.getMessage());
                } catch (TransformerException e) {
                    mLog.warning("Error writing file: %1$s", e.getMessage());
                }
            }
        }
    }

    /**
     * Returns hardware properties (defined in hardware.ini) as a {@link Map}.
     * 
     * @param The {@link State} from which to derive the hardware properties.
     * @return A {@link Map} of hardware properties.
     */
    public static Map<String, String> getHardwareProperties(State s) {
        Hardware hw = s.getHardware();
        Map<String, String> props = new HashMap<String, String>();
        props.put("hw.ramSize", Long.toString(hw.getRam().getSizeAsUnit(Unit.MiB)));
        props.put("hw.mainKeys", getBooleanVal(hw.getButtonType().equals(ButtonType.HARD)));
        props.put("hw.trackBall", getBooleanVal(hw.getNav().equals(Navigation.TRACKBALL)));
        props.put("hw.keyboard", getBooleanVal(hw.getKeyboard().equals(Keyboard.QWERTY)));
        props.put("hw.dPad", getBooleanVal(hw.getNav().equals(Navigation.DPAD)));
        Set<Sensor> sensors = hw.getSensors();
        props.put("hw.gps", getBooleanVal(sensors.contains(Sensor.GPS)));
        props.put("hw.battery", getBooleanVal(hw.getChargeType().equals(PowerType.BATTERY)));
        props.put("hw.accelerometer", getBooleanVal(sensors.contains(Sensor.ACCELEROMETER)));
        props.put("hw.sensors.orientation", getBooleanVal(sensors.contains(Sensor.GYROSCOPE)));
        props.put("hw.audioInput", getBooleanVal(hw.hasMic()));
        props.put("hw.sdCard", getBooleanVal(hw.getRemovableStorage().size() > 0));
        props.put("hw.sdCard", getBooleanVal(hw.getRemovableStorage().size() > 0));
        props.put("hw.lcd.density",
                Integer.toString(hw.getScreen().getPixelDensity().getDpiValue()));
        props.put("hw.sensors.proximity",
                getBooleanVal(sensors.contains(Sensor.PROXIMITY_SENSOR)));
        return props;
    }

    /**
     * Returns the hardware properties defined in
     * {@link AvdManager.HARDWARE_INI} as a {@link Map}.
     * 
     * @param The {@link Device} from which to derive the hardware properties.
     * @return A {@link Map} of hardware properties.
     */
    public static Map<String, String> getHardwareProperties(Device d) {
        Map<String, String> props = getHardwareProperties(d.getDefaultState());
        for (State s : d.getAllStates()) {
            if (s.getKeyState().equals(KeyboardState.HIDDEN)) {
                props.put("hw.keyboard.lid", getBooleanVal(true));
            }
        }
        return props;
    }

    /**
     * Takes a boolean and returns the appropriate value for
     * {@link HardwareProperties}
     * 
     * @param bool The boolean value to turn into the appropriate
     *            {@link HardwareProperties} value.
     * @return {@value HardwareProperties#BOOLEAN_VALUES[0]} if true,
     *         {@value HardwareProperties#BOOLEAN_VALUES[1]} otherwise.
     */
    private static String getBooleanVal(boolean bool) {
        if (bool) {
            return HardwareProperties.BOOLEAN_VALUES[0];
        }
        return HardwareProperties.BOOLEAN_VALUES[1];
    }

    private Collection<Device> loadDevices(File deviceXml) {
        try {
            return DeviceParser.parse(deviceXml);
        } catch (SAXException e) {
            mLog.error(null, "Error parsing %1$s", deviceXml.getAbsolutePath());
        } catch (ParserConfigurationException e) {
            mLog.error(null, "Error parsing %1$s", deviceXml.getAbsolutePath());
        } catch (IOException e) {
            mLog.error(null, "Error reading %1$s", deviceXml.getAbsolutePath());
        } catch (IllegalStateException e) {
            // The device builders can throw IllegalStateExceptions if
            // build gets called before everything is properly setup
            mLog.error(e, null);
        }
        return new ArrayList<Device>();
    }

    private void notifyListeners() {
        synchronized (listeners) {
            for (DevicesChangeListener listener : listeners) {
                listener.onDevicesChange();
            }
        }
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
            try {
                String line;
                while ((line = propertiesReader.readLine()) != null) {
                    Matcher m = sPathPropertyPattern.matcher(line);
                    if (m.matches()) {
                        return true;
                    }
                }
            } finally {
                propertiesReader.close();
            }
        } catch (IOException ignore) {
        }
        return false;
    }
}
