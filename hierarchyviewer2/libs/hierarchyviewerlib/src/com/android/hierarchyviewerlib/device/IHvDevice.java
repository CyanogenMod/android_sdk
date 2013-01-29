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
import com.android.hierarchyviewerlib.device.WindowUpdater.IWindowChangeListener;
import com.android.hierarchyviewerlib.models.ViewNode;
import com.android.hierarchyviewerlib.models.Window;
import com.android.hierarchyviewerlib.ui.util.PsdFile;

import org.eclipse.swt.graphics.Image;

import java.util.List;

/** Represents a device that can perform view debug operations. */
public interface IHvDevice {
    /**
     * Initializes view debugging on the device.
     * @return true if the on device component was successfully initialized
     */
    boolean initializeViewDebug();
    boolean reloadWindows();

    void terminateViewDebug();
    boolean isViewDebugEnabled();
    boolean supportsDisplayListDump();

    Window[] getWindows();
    int getFocusedWindow();

    IDevice getDevice();

    Image getScreenshotImage();
    ViewNode loadWindowData(Window window);
    void loadProfileData(Window window, ViewNode viewNode);
    Image loadCapture(Window window, ViewNode viewNode);
    PsdFile captureLayers(Window window);
    void invalidateView(ViewNode viewNode);
    void requestLayout(ViewNode viewNode);
    void outputDisplayList(ViewNode viewNode);

    boolean isViewUpdateEnabled();
    void invokeViewMethod(Window window, ViewNode viewNode, String method, List<?> args);
    boolean setLayoutParameter(Window window, ViewNode viewNode, String property, int value);

    void addWindowChangeListener(IWindowChangeListener l);
    void removeWindowChangeListener(IWindowChangeListener l);
}
