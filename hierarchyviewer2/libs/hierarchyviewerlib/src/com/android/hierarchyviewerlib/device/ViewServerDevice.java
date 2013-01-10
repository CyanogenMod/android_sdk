/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.hierarchyviewerlib.device;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.hierarchyviewerlib.device.DeviceBridge.ViewServerInfo;
import com.android.hierarchyviewerlib.device.WindowUpdater.IWindowChangeListener;
import com.android.hierarchyviewerlib.models.ViewNode;
import com.android.hierarchyviewerlib.models.Window;
import com.android.hierarchyviewerlib.ui.util.PsdFile;

import org.eclipse.swt.graphics.Image;

public class ViewServerDevice extends AbstractHvDevice {
    static final String TAG = "ViewServerDevice";

    final IDevice mDevice;
    private ViewServerInfo mViewServerInfo;
    private Window[] mWindows;

    public ViewServerDevice(IDevice device) {
        mDevice = device;
    }

    @Override
    public boolean initializeViewDebug() {
        if (!mDevice.isOnline()) {
            return false;
        }

        DeviceBridge.setupDeviceForward(mDevice);

        return reloadWindows();
    }

    @Override
    public boolean reloadWindows() {
        if (!DeviceBridge.isViewServerRunning(mDevice)) {
            if (!DeviceBridge.startViewServer(mDevice)) {
                Log.e(TAG, "Unable to debug device: " + mDevice.getName());
                DeviceBridge.removeDeviceForward(mDevice);
                return false;
            }
        }

        mViewServerInfo = DeviceBridge.loadViewServerInfo(mDevice);
        if (mViewServerInfo == null) {
            return false;
        }

        mWindows = DeviceBridge.loadWindows(this, mDevice);
        return true;
    }

    @Override
    public boolean supportsDisplayListDump() {
        return mViewServerInfo != null && mViewServerInfo.protocolVersion >= 4;
    }

    @Override
    public void terminateViewDebug() {
        DeviceBridge.removeDeviceForward(mDevice);
        DeviceBridge.removeViewServerInfo(mDevice);
    }

    @Override
    public boolean isViewDebugEnabled() {
        return mViewServerInfo != null;
    }

    @Override
    public Window[] getWindows() {
        return mWindows;
    }

    @Override
    public int getFocusedWindow() {
        return DeviceBridge.getFocusedWindow(mDevice);
    }

    @Override
    public IDevice getDevice() {
        return mDevice;
    }

    @Override
    public ViewNode loadWindowData(Window window) {
        return DeviceBridge.loadWindowData(window);
    }

    @Override
    public void loadProfileData(Window window, ViewNode viewNode) {
        DeviceBridge.loadProfileData(window, viewNode);
    }

    @Override
    public Image loadCapture(Window window, ViewNode viewNode) {
        return DeviceBridge.loadCapture(window, viewNode);
    }

    @Override
    public PsdFile captureLayers(Window window) {
        return DeviceBridge.captureLayers(window);
    }

    @Override
    public void invalidateView(ViewNode viewNode) {
        DeviceBridge.invalidateView(viewNode);
    }

    @Override
    public void requestLayout(ViewNode viewNode) {
        DeviceBridge.requestLayout(viewNode);
    }

    @Override
    public void outputDisplayList(ViewNode viewNode) {
        DeviceBridge.outputDisplayList(viewNode);
    }

    @Override
    public void addWindowChangeListener(IWindowChangeListener l) {
        if (mViewServerInfo.protocolVersion >= 3) {
            WindowUpdater.startListenForWindowChanges(l, mDevice);
        }
    }

    @Override
    public void removeWindowChangeListener(IWindowChangeListener l) {
        if (mViewServerInfo.protocolVersion >= 3) {
            WindowUpdater.stopListenForWindowChanges(l, mDevice);
        }
    }
}
