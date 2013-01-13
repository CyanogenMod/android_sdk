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

package com.android.hierarchyviewerlib.actions;

import com.android.ddmuilib.ImageLoader;
import com.android.hierarchyviewerlib.HierarchyViewerDirector;
import com.android.hierarchyviewerlib.device.IHvDevice;
import com.android.hierarchyviewerlib.models.DeviceSelectionModel;
import com.android.hierarchyviewerlib.models.DeviceSelectionModel.IWindowChangeListener;
import com.android.hierarchyviewerlib.models.Window;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;

public class InspectScreenshotAction extends Action implements ImageAction, IWindowChangeListener {

    private static InspectScreenshotAction sAction;

    private Image mImage;

    private InspectScreenshotAction() {
        super("Inspect &Screenshot");
        setAccelerator(SWT.MOD1 + 'S');
        ImageLoader imageLoader = ImageLoader.getLoader(HierarchyViewerDirector.class);
        mImage = imageLoader.loadImage("inspect-screenshot.png", Display.getDefault()); //$NON-NLS-1$
        setImageDescriptor(ImageDescriptor.createFromImage(mImage));
        setToolTipText("Inspect a screenshot in the pixel perfect view");
        setEnabled(
                DeviceSelectionModel.getModel().getSelectedDevice() != null);
        DeviceSelectionModel.getModel().addWindowChangeListener(this);
    }

    public static InspectScreenshotAction getAction() {
        if (sAction == null) {
            sAction = new InspectScreenshotAction();
        }
        return sAction;
    }

    @Override
    public void run() {
        HierarchyViewerDirector.getDirector().inspectScreenshot();
    }

    @Override
    public Image getImage() {
        return mImage;
    }

    @Override
    public void deviceChanged(IHvDevice device) {
        // pass
    }

    @Override
    public void deviceConnected(IHvDevice device) {
        // pass
    }

    @Override
    public void deviceDisconnected(IHvDevice device) {
        // pass
    }

    @Override
    public void focusChanged(IHvDevice device) {
        // pass
    }

    @Override
    public void selectionChanged(final IHvDevice device, final Window window) {
        Display.getDefault().syncExec(new Runnable() {
            @Override
            public void run() {
                InspectScreenshotAction.getAction().setEnabled(device != null);
            }
        });
    }
}
