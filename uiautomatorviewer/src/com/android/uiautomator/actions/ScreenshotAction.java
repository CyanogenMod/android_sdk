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

package com.android.uiautomator.actions;

import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.RawImage;
import com.android.ddmlib.SyncService;
import com.android.uiautomator.DebugBridge;
import com.android.uiautomator.UiAutomatorModel;
import com.android.uiautomator.UiAutomatorViewer;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ScreenshotAction extends Action {
    private static final String UIAUTOMATOR = "/system/bin/uiautomator";    //$NON-NLS-1$
    private static final String UIAUTOMATOR_DUMP_COMMAND = "dump";          //$NON-NLS-1$
    private static final String UIDUMP_DEVICE_PATH = "/sdcard/uidump.xml";  //$NON-NLS-1$

    private static final int MIN_API_LEVEL = 16;

    UiAutomatorViewer mViewer;

    public ScreenshotAction(UiAutomatorViewer viewer) {
        super("&Device Screenshot");
        mViewer = viewer;
    }

    @Override
    public ImageDescriptor getImageDescriptor() {
        return ImageHelper.loadImageDescriptorFromResource("images/screenshot.png");
    }

    @Override
    public void run() {
        if (!DebugBridge.isInitialized()) {
            MessageDialog.openError(mViewer.getShell(),
                    "Error obtaining Device Screenshot",
                    "Unable to connect to adb. Check if adb is installed correctly.");
            return;
        }

        final IDevice device = pickDevice();
        if (device == null) {
            return;
        }

        ProgressMonitorDialog dialog = new ProgressMonitorDialog(mViewer.getShell());
        try {
            dialog.run(true, false, new IRunnableWithProgress() {
                private void showError(final String msg, final Throwable t,
                        IProgressMonitor monitor) {
                    monitor.done();
                    mViewer.getShell().getDisplay().syncExec(new Runnable() {
                        @Override
                        public void run() {
                            Status s = new Status(IStatus.ERROR, "Screenshot", msg, t);
                            ErrorDialog.openError(
                                    mViewer.getShell(), "Error", "Error obtaining UI hierarchy", s);
                        }
                    });
                }

                @Override
                public void run(IProgressMonitor monitor) throws InvocationTargetException,
                InterruptedException {
                    File tmpDir = null;
                    File xmlDumpFile = null;
                    File screenshotFile = null;
                    try {
                        tmpDir = File.createTempFile("uiautomatorviewer_", "");
                        tmpDir.delete();
                        if (!tmpDir.mkdirs())
                            throw new IOException("Failed to mkdir");
                        xmlDumpFile = File.createTempFile("dump_", ".xml", tmpDir);
                        screenshotFile = File.createTempFile("screenshot_", ".png", tmpDir);
                    } catch (IOException e) {
                        e.printStackTrace();
                        showError("Cannot get temp directory", e, monitor);
                        return;
                    }

                    tmpDir.deleteOnExit();
                    xmlDumpFile.deleteOnExit();
                    screenshotFile.deleteOnExit();

                    String apiLevelString = device.getProperty(IDevice.PROP_BUILD_API_LEVEL);
                    int apiLevel;
                    try {
                        apiLevel = Integer.parseInt(apiLevelString);
                    } catch (NumberFormatException e) {
                        apiLevel = MIN_API_LEVEL;
                    }
                    if (apiLevel < MIN_API_LEVEL) {
                        showError("uiautomator requires a device with API Level " + MIN_API_LEVEL,
                                null, monitor);
                        return;
                    }

                    monitor.subTask("Deleting old UI XML snapshot ...");
                    String command = "rm " + UIDUMP_DEVICE_PATH;
                    try {
                        CountDownLatch commandCompleteLatch = new CountDownLatch(1);
                        device.executeShellCommand(command,
                                new CollectingOutputReceiver(commandCompleteLatch));
                        commandCompleteLatch.await(5, TimeUnit.SECONDS);
                    } catch (Exception e1) {
                        // ignore exceptions while deleting stale files
                    }

                    monitor.subTask("Taking UI XML snapshot...");
                    command = String.format("%s %s %s", UIAUTOMATOR,
                                                        UIAUTOMATOR_DUMP_COMMAND,
                                                        UIDUMP_DEVICE_PATH);
                    try {
                        CountDownLatch commandCompleteLatch = new CountDownLatch(1);
                        device.executeShellCommand(command,
                                new CollectingOutputReceiver(commandCompleteLatch));
                        commandCompleteLatch.await(5, TimeUnit.SECONDS);
                    } catch (Exception e1) {
                        showError("", e1, monitor);
                        return;
                    }

                    monitor.subTask("Pull UI XML snapshot from device...");
                    try {
                        device.getSyncService().pullFile(UIDUMP_DEVICE_PATH,
                                xmlDumpFile.getAbsolutePath(), SyncService.getNullProgressMonitor());
                    } catch (Exception e1) {
                        showError("Error copying UI XML file from device", e1, monitor);
                        return;
                    }

                    monitor.subTask("Taking screenshot...");
                    RawImage rawImage;
                    try {
                        rawImage = device.getScreenshot();
                    } catch (Exception e1) {
                        showError("Error taking device screenshot", e1, monitor);
                        return;
                    }

                    UiAutomatorModel model;
                    try {
                        model = new UiAutomatorModel(xmlDumpFile);
                    } catch (Exception e) {
                        showError("Error while parsing UI hierarchy XML file", e, monitor);
                        return;
                    }

                    PaletteData palette = new PaletteData(
                            rawImage.getRedMask(),
                            rawImage.getGreenMask(),
                            rawImage.getBlueMask());
                    ImageData imageData = new ImageData(rawImage.width, rawImage.height,
                            rawImage.bpp, palette, 1, rawImage.data);
                    ImageLoader loader = new ImageLoader();
                    loader.data = new ImageData[] { imageData };
                    loader.save(screenshotFile.getAbsolutePath(), SWT.IMAGE_PNG);
                    Image screenshot = new Image(Display.getDefault(), imageData);

                    mViewer.setModel(model, screenshot);
                    monitor.done();
                }
            });
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private IDevice pickDevice() {
        List<IDevice> devices = DebugBridge.getDevices();
        if (devices.size() == 0) {
            MessageDialog.openError(mViewer.getShell(),
                    "Error obtaining Device Screenshot",
                    "No Android devices were detected by adb.");
            return null;
        } else if (devices.size() == 1) {
            return devices.get(0);
        } else {
            DevicePickerDialog dlg = new DevicePickerDialog(mViewer.getShell(), devices);
            if (dlg.open() != Window.OK) {
                return null;
            }
            return dlg.getSelectedDevice();
        }
    }

    private static class DevicePickerDialog extends Dialog {
        private final List<IDevice> mDevices;
        private final String[] mDeviceNames;
        private static int sSelectedDeviceIndex;

        public DevicePickerDialog(Shell parentShell, List<IDevice> devices) {
            super(parentShell);

            mDevices = devices;
            mDeviceNames = new String[mDevices.size()];
            for (int i = 0; i < devices.size(); i++) {
                mDeviceNames[i] = devices.get(i).getName();
            }
        }

        @Override
        protected Control createDialogArea(Composite parentShell) {
            Composite parent = (Composite) super.createDialogArea(parentShell);
            Composite c = new Composite(parent, SWT.NONE);

            c.setLayout(new GridLayout(2, false));

            Label l = new Label(c, SWT.NONE);
            l.setText("Select device: ");

            final Combo combo = new Combo(c, SWT.BORDER | SWT.READ_ONLY);
            combo.setItems(mDeviceNames);
            int defaultSelection =
                    sSelectedDeviceIndex < mDevices.size() ? sSelectedDeviceIndex : 0;
            combo.select(defaultSelection);
            sSelectedDeviceIndex = defaultSelection;

            combo.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent arg0) {
                    sSelectedDeviceIndex = combo.getSelectionIndex();
                }
            });

            return parent;
        }

        public IDevice getSelectedDevice() {
            return mDevices.get(sSelectedDeviceIndex);
        }
    }
}
