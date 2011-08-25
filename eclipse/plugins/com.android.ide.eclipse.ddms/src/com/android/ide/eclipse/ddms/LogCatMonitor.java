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
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * LogCatMonitor helps in monitoring the logcat output from a set of devices.
 * It scans through the received logcat messages, and activates the logcat view
 * if any message is deemed important.
 */
public class LogCatMonitor {
    private IPreferenceStore mPrefStore;
    private Set<String> mMonitoredDevices;
    private IDebuggerConnector[] mConnectors;

    public LogCatMonitor(IDebuggerConnector[] debuggerConnectors, IPreferenceStore prefStore) {
        mConnectors = debuggerConnectors;
        mPrefStore = prefStore;

        mMonitoredDevices = new HashSet<String>();

        AndroidDebugBridge.addDeviceChangeListener(new IDeviceChangeListener() {
            public void deviceDisconnected(IDevice device) {
                unmonitorDevice(device);
            }

            public void deviceConnected(IDevice device) {
            }

            public void deviceChanged(IDevice device, int changeMask) {
            }
        });
    }

    private synchronized void unmonitorDevice(IDevice device) {
        mMonitoredDevices.remove(device.getSerialNumber());
    }

    public void monitorDevice(final IDevice device) {
        if (mMonitoredDevices.contains(device.getSerialNumber())) {
            // the device is already monitored
            return;
        }

        LogCatReceiver r = LogCatReceiverFactory.INSTANCE.newReceiver(device, mPrefStore);
        r.addMessageReceivedEventListener(new ILogCatMessageEventListener() {
            public void messageReceived(List<LogCatMessage> receivedMessages) {
                checkMessages(receivedMessages, device);
            }
        });

        mMonitoredDevices.add(device.getSerialNumber());
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

                // if the view is already in the page, just bring it to the front
                // without giving it focus.
                IViewPart logCatView = page.findView(LogCatView.ID);
                if (logCatView != null) {
                    page.bringToTop(logCatView);
                    return;
                }

                // if the view is not in the page, then create and show it.
                try {
                    page.showView(LogCatView.ID);
                } catch (PartInitException e) {
                    return;
                }
            }
        });
    }
}
