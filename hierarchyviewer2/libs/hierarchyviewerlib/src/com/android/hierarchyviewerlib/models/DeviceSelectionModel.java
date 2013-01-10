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

package com.android.hierarchyviewerlib.models;

import com.android.hierarchyviewerlib.device.IHvDevice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * This class stores the list of windows for each connected device. It notifies
 * listeners of any changes as well as knows which window is currently selected
 * in the device selector.
 */
public class DeviceSelectionModel {
    private final Map<IHvDevice, DeviceInfo> mDeviceMap = new HashMap<IHvDevice, DeviceInfo>(10);
    private final Map<IHvDevice, Integer> mFocusedWindowHashes =
            new HashMap<IHvDevice, Integer>(20);

    private final ArrayList<IWindowChangeListener> mWindowChangeListeners =
            new ArrayList<IWindowChangeListener>();

    private IHvDevice mSelectedDevice;

    private Window mSelectedWindow;

    private static DeviceSelectionModel sModel;

    private static class DeviceInfo {
        Window[] windows;

        private DeviceInfo(Window[] windows) {
            this.windows = windows;
        }
    }
    public static DeviceSelectionModel getModel() {
        if (sModel == null) {
            sModel = new DeviceSelectionModel();
        }
        return sModel;
    }

    public void addDevice(IHvDevice hvDevice) {
        synchronized (mDeviceMap) {
            DeviceInfo info = new DeviceInfo(hvDevice.getWindows());
            mDeviceMap.put(hvDevice, info);
        }

        notifyDeviceConnected(hvDevice);
    }

    public void removeDevice(IHvDevice hvDevice) {
        boolean selectionChanged = false;
        synchronized (mDeviceMap) {
            mDeviceMap.remove(hvDevice);
            mFocusedWindowHashes.remove(hvDevice);
            if (mSelectedDevice == hvDevice) {
                mSelectedDevice = null;
                mSelectedWindow = null;
                selectionChanged = true;
            }
        }
        notifyDeviceDisconnected(hvDevice);
        if (selectionChanged) {
            notifySelectionChanged(mSelectedDevice, mSelectedWindow);
        }
    }

    public void updateDevice(IHvDevice hvDevice) {
        boolean selectionChanged = false;
        synchronized (mDeviceMap) {
            Window[] windows = hvDevice.getWindows();
            mDeviceMap.put(hvDevice, new DeviceInfo(windows));

            // If the selected window no longer exists, we clear the selection.
            if (mSelectedDevice == hvDevice && mSelectedWindow != null) {
                boolean windowStillExists = false;
                for (int i = 0; i < windows.length && !windowStillExists; i++) {
                    if (windows[i].equals(mSelectedWindow)) {
                        windowStillExists = true;
                    }
                }
                if (!windowStillExists) {
                    mSelectedDevice = null;
                    mSelectedWindow = null;
                    selectionChanged = true;
                }
            }
        }

        notifyDeviceChanged(hvDevice);
        if (selectionChanged) {
            notifySelectionChanged(mSelectedDevice, mSelectedWindow);
        }
    }

    /*
     * Change which window has focus and notify the listeners.
     */
    public void updateFocusedWindow(IHvDevice device, int focusedWindow) {
        Integer oldValue = null;
        synchronized (mDeviceMap) {
            oldValue = mFocusedWindowHashes.put(device, new Integer(focusedWindow));
        }
        // Only notify if the values are different. It would be cool if Java
        // containers accepted basic types like int.
        if (oldValue == null || (oldValue != null && oldValue.intValue() != focusedWindow)) {
            notifyFocusChanged(device);
        }
    }

    public static interface IWindowChangeListener {
        public void deviceConnected(IHvDevice device);

        public void deviceChanged(IHvDevice device);

        public void deviceDisconnected(IHvDevice device);

        public void focusChanged(IHvDevice device);

        public void selectionChanged(IHvDevice device, Window window);
    }

    private IWindowChangeListener[] getWindowChangeListenerList() {
        IWindowChangeListener[] listeners = null;
        synchronized (mWindowChangeListeners) {
            if (mWindowChangeListeners.size() == 0) {
                return null;
            }
            listeners =
                    mWindowChangeListeners.toArray(new IWindowChangeListener[mWindowChangeListeners
                            .size()]);
        }
        return listeners;
    }

    private void notifyDeviceConnected(IHvDevice device) {
        IWindowChangeListener[] listeners = getWindowChangeListenerList();
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                listeners[i].deviceConnected(device);
            }
        }
    }

    private void notifyDeviceChanged(IHvDevice device) {
        IWindowChangeListener[] listeners = getWindowChangeListenerList();
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                listeners[i].deviceChanged(device);
            }
        }
    }

    private void notifyDeviceDisconnected(IHvDevice device) {
        IWindowChangeListener[] listeners = getWindowChangeListenerList();
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                listeners[i].deviceDisconnected(device);
            }
        }
    }

    private void notifyFocusChanged(IHvDevice device) {
        IWindowChangeListener[] listeners = getWindowChangeListenerList();
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                listeners[i].focusChanged(device);
            }
        }
    }

    private void notifySelectionChanged(IHvDevice device, Window window) {
        IWindowChangeListener[] listeners = getWindowChangeListenerList();
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                listeners[i].selectionChanged(device, window);
            }
        }
    }

    public void addWindowChangeListener(IWindowChangeListener listener) {
        synchronized (mWindowChangeListeners) {
            mWindowChangeListeners.add(listener);
        }
    }

    public void removeWindowChangeListener(IWindowChangeListener listener) {
        synchronized (mWindowChangeListeners) {
            mWindowChangeListeners.remove(listener);
        }
    }

    public IHvDevice[] getDevices() {
        synchronized (mDeviceMap) {
            Set<IHvDevice> devices = mDeviceMap.keySet();
            return devices.toArray(new IHvDevice[devices.size()]);
        }
    }

    public Window[] getWindows(IHvDevice device) {
        synchronized (mDeviceMap) {
            DeviceInfo info = mDeviceMap.get(device);
            if (info != null) {
                return info.windows;
            }
        }

        return null;
    }

    // Returns the window that currently has focus or -1. Note that this means
    // that a window with hashcode -1 gets highlighted. If you remember, this is
    // the infamous <Focused Window>
    public int getFocusedWindow(IHvDevice device) {
        synchronized (mDeviceMap) {
            Integer focusedWindow = mFocusedWindowHashes.get(device);
            if (focusedWindow == null) {
                return -1;
            }
            return focusedWindow.intValue();
        }
    }

    public void setSelection(IHvDevice device, Window window) {
        synchronized (mDeviceMap) {
            mSelectedDevice = device;
            mSelectedWindow = window;
        }
        notifySelectionChanged(device, window);
    }

    public IHvDevice getSelectedDevice() {
        synchronized (mDeviceMap) {
            return mSelectedDevice;
        }
    }

    public Window getSelectedWindow() {
        synchronized (mDeviceMap) {
            return mSelectedWindow;
        }
    }
}
