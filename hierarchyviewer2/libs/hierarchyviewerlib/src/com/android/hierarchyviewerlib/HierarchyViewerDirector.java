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

package com.android.hierarchyviewerlib;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.hierarchyviewerlib.device.DeviceBridge;
import com.android.hierarchyviewerlib.device.HvDeviceFactory;
import com.android.hierarchyviewerlib.device.IHvDevice;
import com.android.hierarchyviewerlib.device.WindowUpdater;
import com.android.hierarchyviewerlib.device.WindowUpdater.IWindowChangeListener;
import com.android.hierarchyviewerlib.models.DeviceSelectionModel;
import com.android.hierarchyviewerlib.models.PixelPerfectModel;
import com.android.hierarchyviewerlib.models.TreeViewModel;
import com.android.hierarchyviewerlib.models.ViewNode;
import com.android.hierarchyviewerlib.models.Window;
import com.android.hierarchyviewerlib.ui.CaptureDisplay;
import com.android.hierarchyviewerlib.ui.TreeView;
import com.android.hierarchyviewerlib.ui.util.DrawableViewNode;
import com.android.hierarchyviewerlib.ui.util.PsdFile;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * This is the class where most of the logic resides.
 */
public abstract class HierarchyViewerDirector implements IDeviceChangeListener,
        IWindowChangeListener {

    protected static HierarchyViewerDirector sDirector;

    public static final String TAG = "hierarchyviewer";

    private int mPixelPerfectRefreshesInProgress = 0;

    private Timer mPixelPerfectRefreshTimer = new Timer();

    private boolean mAutoRefresh = false;

    public static final int DEFAULT_PIXEL_PERFECT_AUTOREFRESH_INTERVAL = 5;

    private int mPixelPerfectAutoRefreshInterval = DEFAULT_PIXEL_PERFECT_AUTOREFRESH_INTERVAL;

    private PixelPerfectAutoRefreshTask mCurrentAutoRefreshTask;

    private String mFilterText = ""; //$NON-NLS-1$

    private static final Object mDevicesLock = new Object();
    private Map<IDevice, IHvDevice> mDevices = new HashMap<IDevice, IHvDevice>(10);

    public void terminate() {
        WindowUpdater.terminate();
        mPixelPerfectRefreshTimer.cancel();
    }

    public abstract String getAdbLocation();

    public static HierarchyViewerDirector getDirector() {
        return sDirector;
    }

    /**
     * Init the DeviceBridge with an existing {@link AndroidDebugBridge}.
     * @param bridge the bridge object to use
     */
    public void acquireBridge(AndroidDebugBridge bridge) {
        DeviceBridge.acquireBridge(bridge);
    }

    /**
     * Creates an {@link AndroidDebugBridge} connected to adb at the given location.
     *
     * If a bridge is already running, this disconnects it and creates a new one.
     *
     * @param adbLocation the location to adb.
     */
    public void initDebugBridge() {
        DeviceBridge.initDebugBridge(getAdbLocation());
    }

    public void stopDebugBridge() {
        DeviceBridge.terminate();
    }

    public void populateDeviceSelectionModel() {
        IDevice[] devices = DeviceBridge.getDevices();
        for (IDevice device : devices) {
            deviceConnected(device);
        }
    }

    public void startListenForDevices() {
        DeviceBridge.startListenForDevices(this);
    }

    public void stopListenForDevices() {
        DeviceBridge.stopListenForDevices(this);
    }

    public abstract void executeInBackground(String taskName, Runnable task);

    @Override
    public void deviceConnected(final IDevice device) {
        executeInBackground("Connecting device", new Runnable() {
            @Override
            public void run() {
                if (!device.isOnline()) {
                    return;
                }

                IHvDevice hvDevice;
                synchronized (mDevicesLock) {
                    hvDevice = mDevices.get(device);
                    if (hvDevice == null) {
                        hvDevice = HvDeviceFactory.create(device);
                        hvDevice.initializeViewDebug();
                        hvDevice.addWindowChangeListener(getDirector());
                        mDevices.put(device, hvDevice);
                    } else {
                        // attempt re-initializing view server if device state has changed
                        hvDevice.initializeViewDebug();
                    }
                }

                DeviceSelectionModel.getModel().addDevice(hvDevice);
                focusChanged(device);
            }
        });
    }

    @Override
    public void deviceDisconnected(final IDevice device) {
        executeInBackground("Disconnecting device", new Runnable() {
            @Override
            public void run() {
                IHvDevice hvDevice;
                synchronized (mDevicesLock) {
                    hvDevice = mDevices.get(device);
                    if (hvDevice != null) {
                        mDevices.remove(device);
                    }
                }

                if (hvDevice == null) {
                    return;
                }

                hvDevice.terminateViewDebug();
                hvDevice.removeWindowChangeListener(getDirector());
                DeviceSelectionModel.getModel().removeDevice(hvDevice);
                if (PixelPerfectModel.getModel().getDevice() == device) {
                    PixelPerfectModel.getModel().setData(null, null, null);
                }
                Window treeViewWindow = TreeViewModel.getModel().getWindow();
                if (treeViewWindow != null && treeViewWindow.getDevice() == device) {
                    TreeViewModel.getModel().setData(null, null);
                    mFilterText = ""; //$NON-NLS-1$
                }
            }
        });
    }

    @Override
    public void windowsChanged(final IDevice device) {
        executeInBackground("Refreshing windows", new Runnable() {
            @Override
            public void run() {
                IHvDevice hvDevice = getHvDevice(device);
                hvDevice.reloadWindows();
                DeviceSelectionModel.getModel().updateDevice(hvDevice);
            }
        });
    }

    @Override
    public void focusChanged(final IDevice device) {
        executeInBackground("Updating focus", new Runnable() {
            @Override
            public void run() {
                IHvDevice hvDevice = getHvDevice(device);
                int focusedWindow = hvDevice.getFocusedWindow();
                DeviceSelectionModel.getModel().updateFocusedWindow(hvDevice, focusedWindow);
            }
        });
    }

    @Override
    public void deviceChanged(IDevice device, int changeMask) {
        if ((changeMask & IDevice.CHANGE_STATE) != 0 && device.isOnline()) {
            deviceConnected(device);
        }
    }

    public void refreshPixelPerfect() {
        final IDevice device = PixelPerfectModel.getModel().getDevice();
        if (device != null) {
            // Some interesting logic here. We don't want to refresh the pixel
            // perfect view 1000 times in a row if the focus keeps changing. We
            // just
            // want it to refresh following the last focus change.
            boolean proceed = false;
            synchronized (this) {
                if (mPixelPerfectRefreshesInProgress <= 1) {
                    proceed = true;
                    mPixelPerfectRefreshesInProgress++;
                }
            }
            if (proceed) {
                executeInBackground("Refreshing pixel perfect screenshot", new Runnable() {
                    @Override
                    public void run() {
                        Image screenshotImage = getScreenshotImage(getHvDevice(device));
                        if (screenshotImage != null) {
                            PixelPerfectModel.getModel().setImage(screenshotImage);
                        }
                        synchronized (HierarchyViewerDirector.this) {
                            mPixelPerfectRefreshesInProgress--;
                        }
                    }

                });
            }
        }
    }

    public void refreshPixelPerfectTree() {
        final IDevice device = PixelPerfectModel.getModel().getDevice();
        if (device != null) {
            executeInBackground("Refreshing pixel perfect tree", new Runnable() {
                @Override
                public void run() {
                    IHvDevice hvDevice = getHvDevice(device);
                    ViewNode viewNode =
                            hvDevice.loadWindowData(Window.getFocusedWindow(hvDevice));
                    if (viewNode != null) {
                        PixelPerfectModel.getModel().setTree(viewNode);
                    }
                }

            });
        }
    }

    public void loadPixelPerfectData(final IHvDevice hvDevice) {
        executeInBackground("Loading pixel perfect data", new Runnable() {
            @Override
            public void run() {
                Image screenshotImage = getScreenshotImage(hvDevice);
                if (screenshotImage != null) {
                    ViewNode viewNode =
                            hvDevice.loadWindowData(Window.getFocusedWindow(hvDevice));
                    if (viewNode != null) {
                        PixelPerfectModel.getModel().setData(hvDevice.getDevice(),
                                screenshotImage, viewNode);
                    }
                }
            }
        });
    }

    private IHvDevice getHvDevice(IDevice device) {
        synchronized (mDevicesLock) {
            return mDevices.get(device);
        }
    }

    private Image getScreenshotImage(IHvDevice hvDevice) {
        return (hvDevice == null) ? null : hvDevice.getScreenshotImage();
    }

    public void loadViewTreeData(final Window window) {
        executeInBackground("Loading view hierarchy", new Runnable() {
            @Override
            public void run() {
                mFilterText = ""; //$NON-NLS-1$

                IHvDevice hvDevice = window.getHvDevice();
                ViewNode viewNode = hvDevice.loadWindowData(window);
                if (viewNode != null) {
                    viewNode.setViewCount();
                    TreeViewModel.getModel().setData(window, viewNode);
                }
            }
        });
    }

    public void loadOverlay(final Shell shell) {
        Display.getDefault().syncExec(new Runnable() {
            @Override
            public void run() {
                FileDialog fileDialog = new FileDialog(shell, SWT.OPEN);
                fileDialog.setFilterExtensions(new String[] {
                    "*.jpg;*.jpeg;*.png;*.gif;*.bmp" //$NON-NLS-1$
                });
                fileDialog.setFilterNames(new String[] {
                    "Image (*.jpg, *.jpeg, *.png, *.gif, *.bmp)"
                });
                fileDialog.setText("Choose an overlay image");
                String fileName = fileDialog.open();
                if (fileName != null) {
                    try {
                        Image image = new Image(Display.getDefault(), fileName);
                        PixelPerfectModel.getModel().setOverlayImage(image);
                    } catch (SWTException e) {
                        Log.e(TAG, "Unable to load image from " + fileName);
                    }
                }
            }
        });
    }

    public void showCapture(final Shell shell, final ViewNode viewNode) {
        executeInBackground("Capturing node", new Runnable() {
            @Override
            public void run() {
                final Image image = loadCapture(viewNode);
                if (image != null) {

                    Display.getDefault().syncExec(new Runnable() {
                        @Override
                        public void run() {
                            CaptureDisplay.show(shell, viewNode, image);
                        }
                    });
                }
            }
        });
    }

    public Image loadCapture(ViewNode viewNode) {
        IHvDevice hvDevice = viewNode.window.getHvDevice();
        final Image image = hvDevice.loadCapture(viewNode.window, viewNode);
        if (image != null) {
            viewNode.image = image;

            // Force the layout viewer to redraw.
            TreeViewModel.getModel().notifySelectionChanged();
        }
        return image;
    }

    public void loadCaptureInBackground(final ViewNode viewNode) {
        executeInBackground("Capturing node", new Runnable() {
            @Override
            public void run() {
                loadCapture(viewNode);
            }
        });
    }

    public void showCapture(Shell shell) {
        DrawableViewNode viewNode = TreeViewModel.getModel().getSelection();
        if (viewNode != null) {
            showCapture(shell, viewNode.viewNode);
        }
    }

    public void refreshWindows() {
        executeInBackground("Refreshing windows", new Runnable() {
            @Override
            public void run() {
                IHvDevice[] hvDevicesA = DeviceSelectionModel.getModel().getDevices();
                IDevice[] devicesA = new IDevice[hvDevicesA.length];
                for (int i = 0; i < hvDevicesA.length; i++) {
                    devicesA[i] = hvDevicesA[i].getDevice();
                }
                IDevice[] devicesB = DeviceBridge.getDevices();
                HashSet<IDevice> deviceSet = new HashSet<IDevice>();
                for (int i = 0; i < devicesB.length; i++) {
                    deviceSet.add(devicesB[i]);
                }
                for (int i = 0; i < devicesA.length; i++) {
                    if (deviceSet.contains(devicesA[i])) {
                        windowsChanged(devicesA[i]);
                        deviceSet.remove(devicesA[i]);
                    } else {
                        deviceDisconnected(devicesA[i]);
                    }
                }
                for (IDevice device : deviceSet) {
                    deviceConnected(device);
                }
            }
        });
    }

    public void loadViewHierarchy() {
        Window window = DeviceSelectionModel.getModel().getSelectedWindow();
        if (window != null) {
            loadViewTreeData(window);
        }
    }

    public void inspectScreenshot() {
        IHvDevice device = DeviceSelectionModel.getModel().getSelectedDevice();
        if (device != null) {
            loadPixelPerfectData(device);
        }
    }

    public void saveTreeView(final Shell shell) {
        Display.getDefault().syncExec(new Runnable() {
            @Override
            public void run() {
                final DrawableViewNode viewNode = TreeViewModel.getModel().getTree();
                if (viewNode != null) {
                    FileDialog fileDialog = new FileDialog(shell, SWT.SAVE);
                    fileDialog.setFilterExtensions(new String[] {
                        "*.png" //$NON-NLS-1$
                    });
                    fileDialog.setFilterNames(new String[] {
                        "Portable Network Graphics File (*.png)"
                    });
                    fileDialog.setText("Choose where to save the tree image");
                    final String fileName = fileDialog.open();
                    if (fileName != null) {
                        executeInBackground("Saving tree view", new Runnable() {
                            @Override
                            public void run() {
                                Image image = TreeView.paintToImage(viewNode);
                                ImageLoader imageLoader = new ImageLoader();
                                imageLoader.data = new ImageData[] {
                                    image.getImageData()
                                };
                                String extensionedFileName = fileName;
                                if (!extensionedFileName.toLowerCase().endsWith(".png")) { //$NON-NLS-1$
                                    extensionedFileName += ".png"; //$NON-NLS-1$
                                }
                                try {
                                    imageLoader.save(extensionedFileName, SWT.IMAGE_PNG);
                                } catch (SWTException e) {
                                    Log.e(TAG, "Unable to save tree view as a PNG image at "
                                            + fileName);
                                }
                                image.dispose();
                            }
                        });
                    }
                }
            }
        });
    }

    public void savePixelPerfect(final Shell shell) {
        Display.getDefault().syncExec(new Runnable() {
            @Override
            public void run() {
                Image untouchableImage = PixelPerfectModel.getModel().getImage();
                if (untouchableImage != null) {
                    final ImageData imageData = untouchableImage.getImageData();
                    FileDialog fileDialog = new FileDialog(shell, SWT.SAVE);
                    fileDialog.setFilterExtensions(new String[] {
                        "*.png" //$NON-NLS-1$
                    });
                    fileDialog.setFilterNames(new String[] {
                        "Portable Network Graphics File (*.png)"
                    });
                    fileDialog.setText("Choose where to save the screenshot");
                    final String fileName = fileDialog.open();
                    if (fileName != null) {
                        executeInBackground("Saving pixel perfect", new Runnable() {
                            @Override
                            public void run() {
                                ImageLoader imageLoader = new ImageLoader();
                                imageLoader.data = new ImageData[] {
                                    imageData
                                };
                                String extensionedFileName = fileName;
                                if (!extensionedFileName.toLowerCase().endsWith(".png")) { //$NON-NLS-1$
                                    extensionedFileName += ".png"; //$NON-NLS-1$
                                }
                                try {
                                    imageLoader.save(extensionedFileName, SWT.IMAGE_PNG);
                                } catch (SWTException e) {
                                    Log.e(TAG, "Unable to save tree view as a PNG image at "
                                            + fileName);
                                }
                            }
                        });
                    }
                }
            }
        });
    }

    public void capturePSD(final Shell shell) {
        Display.getDefault().syncExec(new Runnable() {
            @Override
            public void run() {
                final Window window = TreeViewModel.getModel().getWindow();
                if (window != null) {
                    FileDialog fileDialog = new FileDialog(shell, SWT.SAVE);
                    fileDialog.setFilterExtensions(new String[] {
                        "*.psd" //$NON-NLS-1$
                    });
                    fileDialog.setFilterNames(new String[] {
                        "Photoshop Document (*.psd)"
                    });
                    fileDialog.setText("Choose where to save the window layers");
                    final String fileName = fileDialog.open();
                    if (fileName != null) {
                        executeInBackground("Saving window layers", new Runnable() {
                            @Override
                            public void run() {
                                IHvDevice hvDevice = getHvDevice(window.getDevice());
                                PsdFile psdFile = hvDevice.captureLayers(window);
                                if (psdFile != null) {
                                    String extensionedFileName = fileName;
                                    if (!extensionedFileName.toLowerCase().endsWith(".psd")) { //$NON-NLS-1$
                                        extensionedFileName += ".psd"; //$NON-NLS-1$
                                    }
                                    try {
                                        psdFile.write(new FileOutputStream(extensionedFileName));
                                    } catch (FileNotFoundException e) {
                                        Log.e(TAG, "Unable to write to file " + fileName);
                                    }
                                }
                            }
                        });
                    }
                }
            }
        });
    }

    public void reloadViewHierarchy() {
        Window window = TreeViewModel.getModel().getWindow();
        if (window != null) {
            loadViewTreeData(window);
        }
    }

    public void invalidateCurrentNode() {
        final DrawableViewNode selectedNode = TreeViewModel.getModel().getSelection();
        if (selectedNode != null) {
            executeInBackground("Invalidating view", new Runnable() {
                @Override
                public void run() {
                    IHvDevice hvDevice = getHvDevice(selectedNode.viewNode.window.getDevice());
                    hvDevice.invalidateView(selectedNode.viewNode);
                }
            });
        }
    }

    public void relayoutCurrentNode() {
        final DrawableViewNode selectedNode = TreeViewModel.getModel().getSelection();
        if (selectedNode != null) {
            executeInBackground("Request layout", new Runnable() {
                @Override
                public void run() {
                    IHvDevice hvDevice = getHvDevice(selectedNode.viewNode.window.getDevice());
                    hvDevice.requestLayout(selectedNode.viewNode);
                }
            });
        }
    }

    public void dumpDisplayListForCurrentNode() {
        final DrawableViewNode selectedNode = TreeViewModel.getModel().getSelection();
        if (selectedNode != null) {
            executeInBackground("Dump displaylist", new Runnable() {
                @Override
                public void run() {
                    IHvDevice hvDevice = getHvDevice(selectedNode.viewNode.window.getDevice());
                    hvDevice.outputDisplayList(selectedNode.viewNode);
                }
            });
        }
    }

    public void profileCurrentNode() {
        final DrawableViewNode selectedNode = TreeViewModel.getModel().getSelection();
        if (selectedNode != null) {
            executeInBackground("Profile Node", new Runnable() {
                @Override
                public void run() {
                    IHvDevice hvDevice = getHvDevice(selectedNode.viewNode.window.getDevice());
                    hvDevice.loadProfileData(selectedNode.viewNode.window, selectedNode.viewNode);
                    // Force the layout viewer to redraw.
                    TreeViewModel.getModel().notifySelectionChanged();
                }
            });
        }
    }

    public void invokeMethodOnSelectedView(final String method, final List<Object> args) {
        final DrawableViewNode selectedNode = TreeViewModel.getModel().getSelection();
        if (selectedNode != null) {
            executeInBackground("Invoke View Method", new Runnable() {
                @Override
                public void run() {
                    IHvDevice hvDevice = getHvDevice(selectedNode.viewNode.window.getDevice());
                    hvDevice.invokeViewMethod(selectedNode.viewNode.window, selectedNode.viewNode,
                            method, args);
                }
            });
        }
    }

    public void loadAllViews() {
        executeInBackground("Loading all views", new Runnable() {
            @Override
            public void run() {
                DrawableViewNode tree = TreeViewModel.getModel().getTree();
                if (tree != null) {
                    loadViewRecursive(tree.viewNode);
                    // Force the layout viewer to redraw.
                    TreeViewModel.getModel().notifySelectionChanged();
                }
            }
        });
    }

    private void loadViewRecursive(ViewNode viewNode) {
        IHvDevice hvDevice = getHvDevice(viewNode.window.getDevice());
        Image image = hvDevice.loadCapture(viewNode.window, viewNode);
        if (image == null) {
            return;
        }
        viewNode.image = image;
        final int N = viewNode.children.size();
        for (int i = 0; i < N; i++) {
            loadViewRecursive(viewNode.children.get(i));
        }
    }

    public void filterNodes(String filterText) {
        this.mFilterText = filterText;
        DrawableViewNode tree = TreeViewModel.getModel().getTree();
        if (tree != null) {
            tree.viewNode.filter(filterText);
            // Force redraw
            TreeViewModel.getModel().notifySelectionChanged();
        }
    }

    public String getFilterText() {
        return mFilterText;
    }

    private static class PixelPerfectAutoRefreshTask extends TimerTask {
        @Override
        public void run() {
            HierarchyViewerDirector.getDirector().refreshPixelPerfect();
        }
    };

    public void setPixelPerfectAutoRefresh(boolean value) {
        synchronized (mPixelPerfectRefreshTimer) {
            if (value == mAutoRefresh) {
                return;
            }
            mAutoRefresh = value;
            if (mAutoRefresh) {
                mCurrentAutoRefreshTask = new PixelPerfectAutoRefreshTask();
                mPixelPerfectRefreshTimer.schedule(mCurrentAutoRefreshTask,
                        mPixelPerfectAutoRefreshInterval * 1000,
                        mPixelPerfectAutoRefreshInterval * 1000);
            } else {
                mCurrentAutoRefreshTask.cancel();
                mCurrentAutoRefreshTask = null;
            }
        }
    }

    public void setPixelPerfectAutoRefreshInterval(int value) {
        synchronized (mPixelPerfectRefreshTimer) {
            if (mPixelPerfectAutoRefreshInterval == value) {
                return;
            }
            mPixelPerfectAutoRefreshInterval = value;
            if (mAutoRefresh) {
                mCurrentAutoRefreshTask.cancel();
                long timeLeft =
                        Math.max(0, mPixelPerfectAutoRefreshInterval
                                * 1000
                                - (System.currentTimeMillis() - mCurrentAutoRefreshTask
                                        .scheduledExecutionTime()));
                mCurrentAutoRefreshTask = new PixelPerfectAutoRefreshTask();
                mPixelPerfectRefreshTimer.schedule(mCurrentAutoRefreshTask, timeLeft,
                        mPixelPerfectAutoRefreshInterval * 1000);
            }
        }
    }

    public int getPixelPerfectAutoRefreshInverval() {
        return mPixelPerfectAutoRefreshInterval;
    }
}
