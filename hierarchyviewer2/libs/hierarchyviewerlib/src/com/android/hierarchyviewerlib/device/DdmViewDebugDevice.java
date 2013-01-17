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

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.HandleViewDebug;
import com.android.ddmlib.HandleViewDebug.ViewDumpHandler;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.hierarchyviewerlib.device.WindowUpdater.IWindowChangeListener;
import com.android.hierarchyviewerlib.models.ViewNode;
import com.android.hierarchyviewerlib.models.Window;
import com.android.hierarchyviewerlib.ui.util.PsdFile;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class DdmViewDebugDevice extends AbstractHvDevice implements IDeviceChangeListener {
    private static final String TAG = "DdmViewDebugDevice";

    private final IDevice mDevice;
    private Map<Client, List<String>> mViewRootsPerClient = new HashMap<Client, List<String>>(40);

    public DdmViewDebugDevice(IDevice device) {
        mDevice = device;
    }

    @Override
    public boolean initializeViewDebug() {
        AndroidDebugBridge.addDeviceChangeListener(this);
        return reloadWindows();
    }

    private static class ListViewRootsHandler extends ViewDumpHandler {
        private List<String> mViewRoots = Collections.synchronizedList(new ArrayList<String>(10));

        public ListViewRootsHandler() {
            super(HandleViewDebug.CHUNK_VULW);
        }

        @Override
        protected void handleViewDebugResult(ByteBuffer data) {
            int nWindows = data.getInt();

            for (int i = 0; i < nWindows; i++) {
                int len = data.getInt();
                mViewRoots.add(getString(data, len));
            }
        }

        public List<String> getViewRoots(long timeout, TimeUnit unit) {
            waitForResult(timeout, unit);
            return mViewRoots;
        }
    }

    private static class CaptureByteArrayHandler extends ViewDumpHandler {
        public CaptureByteArrayHandler(int type) {
            super(type);
        }

        private AtomicReference<byte[]> mData = new AtomicReference<byte[]>();

        @Override
        protected void handleViewDebugResult(ByteBuffer data) {
            byte[] b = new byte[data.remaining()];
            data.get(b);
            mData.set(b);

        }

        public byte[] getData(long timeout, TimeUnit unit) {
            waitForResult(timeout, unit);
            return mData.get();
        }
    }

    private static class CaptureLayersHandler extends ViewDumpHandler {
        private AtomicReference<PsdFile> mPsd = new AtomicReference<PsdFile>();

        public CaptureLayersHandler() {
            super(HandleViewDebug.CHUNK_VURT);
        }

        @Override
        protected void handleViewDebugResult(ByteBuffer data) {
            byte[] b = new byte[data.remaining()];
            data.get(b);
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(b));
            try {
                mPsd.set(DeviceBridge.parsePsd(dis));
            } catch (IOException e) {
                Log.e(TAG, e);
            }
        }

        public PsdFile getPsdFile(long timeout, TimeUnit unit) {
            waitForResult(timeout, unit);
            return mPsd.get();
        }
    }

    @Override
    public boolean reloadWindows() {
        mViewRootsPerClient = new HashMap<Client, List<String>>(40);

        for (Client c : mDevice.getClients()) {
            ClientData cd = c.getClientData();
            if (cd != null && cd.hasFeature(ClientData.FEATURE_VIEW_HIERARCHY)) {
                ListViewRootsHandler handler = new ListViewRootsHandler();

                try {
                    HandleViewDebug.listViewRoots(c, handler);
                } catch (IOException e) {
                    Log.e(TAG, e);
                    continue;
                }

                List<String> viewRoots = new ArrayList<String>(
                        handler.getViewRoots(200, TimeUnit.MILLISECONDS));
                mViewRootsPerClient.put(c, viewRoots);
            }
        }

        return true;
    }

    @Override
    public void terminateViewDebug() {
        // nothing to terminate
    }

    @Override
    public boolean isViewDebugEnabled() {
        return true;
    }

    @Override
    public boolean supportsDisplayListDump() {
        return true;
    }

    @Override
    public Window[] getWindows() {
        List<Window> windows = new ArrayList<Window>(10);

        for (Client c: mViewRootsPerClient.keySet()) {
            for (String viewRoot: mViewRootsPerClient.get(c)) {
                windows.add(new Window(this, viewRoot, c));
            }
        }

        return windows.toArray(new Window[windows.size()]);
    }

    @Override
    public int getFocusedWindow() {
        // TODO: add support for identifying view in focus
        return -1;
    }

    @Override
    public IDevice getDevice() {
        return mDevice;
    }

    @Override
    public ViewNode loadWindowData(Window window) {
        Client c = window.getClient();
        if (c == null) {
            return null;
        }

        String viewRoot = window.getTitle();
        CaptureByteArrayHandler handler = new CaptureByteArrayHandler(HandleViewDebug.CHUNK_VURT);
        try {
            HandleViewDebug.dumpViewHierarchy(c, viewRoot,
                    false /* skipChildren */,
                    true  /* includeProperties */,
                    handler);
        } catch (IOException e) {
            Log.e(TAG, e);
            return null;
        }

        byte[] data = handler.getData(10, TimeUnit.SECONDS);
        String viewHierarchy = new String(data, Charset.forName("UTF-8"));
        return DeviceBridge.parseViewHierarchy(new BufferedReader(new StringReader(viewHierarchy)),
                window);
    }

    @Override
    public void loadProfileData(Window window, ViewNode viewNode) {
        Client c = window.getClient();
        if (c == null) {
            return;
        }

        String viewRoot = window.getTitle();
        CaptureByteArrayHandler handler = new CaptureByteArrayHandler(HandleViewDebug.CHUNK_VUOP);
        try {
            HandleViewDebug.profileView(c, viewRoot, viewNode.toString(), handler);
        } catch (IOException e) {
            Log.e(TAG, e);
            return;
        }

        byte[] data = handler.getData(30, TimeUnit.SECONDS);
        if (data == null) {
            Log.e(TAG, "Timed out waiting for profile data");
            return;
        }

        try {
            boolean success = DeviceBridge.loadProfileDataRecursive(viewNode,
                    new BufferedReader(new StringReader(new String(data))));
            if (success) {
                viewNode.setProfileRatings();
            }
        } catch (IOException e) {
            Log.e(TAG, e);
            return;
        }
    }

    @Override
    public Image loadCapture(Window window, ViewNode viewNode) {
        Client c = window.getClient();
        if (c == null) {
            return null;
        }

        String viewRoot = window.getTitle();
        CaptureByteArrayHandler handler = new CaptureByteArrayHandler(HandleViewDebug.CHUNK_VUOP);

        try {
            HandleViewDebug.captureView(c, viewRoot, viewNode.toString(), handler);
        } catch (IOException e) {
            Log.e(TAG, e);
            return null;
        }

        byte[] data = handler.getData(10, TimeUnit.SECONDS);
        return (data == null) ? null :
            new Image(Display.getDefault(), new ByteArrayInputStream(data));
    }

    @Override
    public PsdFile captureLayers(Window window) {
        Client c = window.getClient();
        if (c == null) {
            return null;
        }

        String viewRoot = window.getTitle();
        CaptureLayersHandler handler = new CaptureLayersHandler();
        try {
            HandleViewDebug.captureLayers(c, viewRoot, handler);
        } catch (IOException e) {
            Log.e(TAG, e);
            return null;
        }

        return handler.getPsdFile(20, TimeUnit.SECONDS);
    }

    @Override
    public void invalidateView(ViewNode viewNode) {
        Window window = viewNode.window;
        Client c = window.getClient();
        if (c == null) {
            return;
        }

        String viewRoot = window.getTitle();
        try {
            HandleViewDebug.invalidateView(c, viewRoot, viewNode.toString());
        } catch (IOException e) {
            Log.e(TAG, e);
        }
    }

    @Override
    public void requestLayout(ViewNode viewNode) {
        Window window = viewNode.window;
        Client c = window.getClient();
        if (c == null) {
            return;
        }

        String viewRoot = window.getTitle();
        try {
            HandleViewDebug.requestLayout(c, viewRoot, viewNode.toString());
        } catch (IOException e) {
            Log.e(TAG, e);
        }
    }

    @Override
    public void outputDisplayList(ViewNode viewNode) {
        Window window = viewNode.window;
        Client c = window.getClient();
        if (c == null) {
            return;
        }

        String viewRoot = window.getTitle();
        try {
            HandleViewDebug.dumpDisplayList(c, viewRoot, viewNode.toString());
        } catch (IOException e) {
            Log.e(TAG, e);
        }
    }

    @Override
    public void addWindowChangeListener(IWindowChangeListener l) {
        // TODO: add support for listening to view root changes
    }

    @Override
    public void removeWindowChangeListener(IWindowChangeListener l) {
        // TODO: add support for listening to view root changes
    }

    @Override
    public void deviceConnected(IDevice device) {
        // pass
    }

    @Override
    public void deviceDisconnected(IDevice device) {
        // pass
    }

    @Override
    public void deviceChanged(IDevice device, int changeMask) {
        if ((changeMask & IDevice.CHANGE_CLIENT_LIST) != 0) {
            reloadWindows();
        }
    }
}
