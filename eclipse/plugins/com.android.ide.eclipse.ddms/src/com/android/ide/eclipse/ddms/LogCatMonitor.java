/*
 * Copyright (C) 2011 The Android Open Source Project
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
package com.android.ide.eclipse.ddms;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log.LogLevel;
import com.android.ddmuilib.logcat.ILogCatMessageEventListener;
import com.android.ddmuilib.logcat.LogCatMessage;
import com.android.ddmuilib.logcat.LogCatReceiver;
import com.android.ddmuilib.logcat.LogCatReceiverFactory;
import com.android.ide.eclipse.ddms.views.LogCatView;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LogCatMonitor helps in monitoring the logcat output from a set of devices.
 * It scans through the received logcat messages, and activates the logcat view
 * if any message is deemed important.
 */
public class LogCatMonitor {
    public static final String AUTO_MONITOR_PREFKEY = "ddms.logcat.automonitor";

    private IPreferenceStore mPrefStore;
    private Map<String, DeviceData> mMonitoredDevices;
    private IDebuggerConnector[] mConnectors;

    public LogCatMonitor(IDebuggerConnector[] debuggerConnectors, IPreferenceStore prefStore) {
        mConnectors = debuggerConnectors;
        mPrefStore = prefStore;

        mMonitoredDevices = new HashMap<String, DeviceData>();

        AndroidDebugBridge.addDeviceChangeListener(new IDeviceChangeListener() {
            public void deviceDisconnected(IDevice device) {
                unmonitorDevice(device.getSerialNumber());
                mMonitoredDevices.remove(device.getSerialNumber());
            }

            public void deviceConnected(IDevice device) {
            }

            public void deviceChanged(IDevice device, int changeMask) {
            }
        });

        mPrefStore.addPropertyChangeListener(new IPropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent event) {
                if (AUTO_MONITOR_PREFKEY.equals(event.getProperty())
                        && event.getNewValue().equals(false)) {
                    unmonitorAllDevices();
                }
            }
        });
    }

    private void unmonitorAllDevices() {
        for (String device : mMonitoredDevices.keySet()) {
            unmonitorDevice(device);
        }

        mMonitoredDevices.clear();
    }

    private void unmonitorDevice(String deviceSerial) {
        DeviceData data = mMonitoredDevices.get(deviceSerial);
        if (data == null) {
            return;
        }

        data.receiver.removeMessageReceivedEventListener(data.messageEventListener);
    }

    public void monitorDevice(final IDevice device) {
        if (!mPrefStore.getBoolean(AUTO_MONITOR_PREFKEY)) {
            // do not monitor device if auto monitoring is off
            return;
        }

        if (mMonitoredDevices.keySet().contains(device.getSerialNumber())) {
            // the device is already monitored
            return;
        }

        LogCatReceiver r = LogCatReceiverFactory.INSTANCE.newReceiver(device, mPrefStore);
        ILogCatMessageEventListener l = new ILogCatMessageEventListener() {
            public void messageReceived(List<LogCatMessage> receivedMessages) {
                checkMessages(receivedMessages, device);
            }
        };
        r.addMessageReceivedEventListener(l);

        mMonitoredDevices.put(device.getSerialNumber(), new DeviceData(r, l));
    }

    private void checkMessages(List<LogCatMessage> receivedMessages, IDevice device) {
        // check the received list of messages to see if any of them are
        // significant enough to be seen by the user. If so, activate the logcat view
        // to display those messages
        for (LogCatMessage m : receivedMessages) {
            if (isImportantMessage(m)) {
                focusLogCatView(device, m.getAppName());
                break;
            }
        }
    }

    /**
     * Check whether a message is "important". Currently, we assume that a message is important if
     * it is of severity level error or higher, and it belongs to an app currently in the workspace.
     */
    private boolean isImportantMessage(LogCatMessage m) {
        if (m.getLogLevel().getPriority() < LogLevel.ERROR.getPriority()) {
            return false;
        }

        String app = m.getAppName();
        for (IDebuggerConnector c : mConnectors) {
            if (c.isWorkspaceApp(app)) {
                return true;
            }
        }

        return false;
    }

    private void focusLogCatView(final IDevice device, final String appName) {
        Display.getDefault().asyncExec(new Runnable() {
            public void run() {
                IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
                if (window == null) {
                    return;
                }

                IWorkbenchPage page = window.getActivePage();
                if (page == null) {
                    return;
                }

                // display view
                final LogCatView v = displayLogCatView(page);
                if (v == null) {
                    return;
                }

                // select correct device
                v.selectionChanged(device);

                // select appropriate filter
                v.selectTransientAppFilter(appName);
            }

            private LogCatView displayLogCatView(IWorkbenchPage page) {
                // if the view is already in the page, just bring it to the front
                // without giving it focus.
                IViewPart view = page.findView(LogCatView.ID);
                if (view != null) {
                    page.bringToTop(view);
                    if (view instanceof LogCatView) {
                        return (LogCatView)view;
                    }
                }

                // if the view is not in the page, then create and show it.
                try {
                    return (LogCatView) page.showView(LogCatView.ID);
                } catch (PartInitException e) {
                    return null;
                }
            }
        });
    }

    private static class DeviceData {
        public final LogCatReceiver receiver;
        public final ILogCatMessageEventListener messageEventListener;

        public DeviceData(LogCatReceiver r, ILogCatMessageEventListener l) {
            receiver = r;
            messageEventListener = l;
        }
    }
}
