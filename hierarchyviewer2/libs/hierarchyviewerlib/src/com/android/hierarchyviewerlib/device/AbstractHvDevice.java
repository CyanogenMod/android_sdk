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

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.ddmlib.RawImage;
import com.android.ddmlib.TimeoutException;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.widgets.Display;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

public abstract class AbstractHvDevice implements IHvDevice {
    private static final String TAG = "HierarchyViewer";

    @Override
    public Image getScreenshotImage() {
        IDevice device = getDevice();
        final AtomicReference<Image> imageRef = new AtomicReference<Image>();

        try {
            final RawImage screenshot = device.getScreenshot();
            if (screenshot == null) {
                return null;
            }
            Display.getDefault().syncExec(new Runnable() {
                @Override
                public void run() {
                    ImageData imageData =
                            new ImageData(screenshot.width, screenshot.height, screenshot.bpp,
                                    new PaletteData(screenshot.getRedMask(), screenshot
                                            .getGreenMask(), screenshot.getBlueMask()), 1,
                                    screenshot.data);
                    imageRef.set(new Image(Display.getDefault(), imageData));
                }
            });
            return imageRef.get();
        } catch (IOException e) {
            Log.e(TAG, "Unable to load screenshot from device " + device.getName());
        } catch (TimeoutException e) {
            Log.e(TAG, "Timeout loading screenshot from device " + device.getName());
        } catch (AdbCommandRejectedException e) {
            Log.e(TAG, "Adb rejected command to load screenshot from device " + device.getName());
        }
        return null;
    }
}
