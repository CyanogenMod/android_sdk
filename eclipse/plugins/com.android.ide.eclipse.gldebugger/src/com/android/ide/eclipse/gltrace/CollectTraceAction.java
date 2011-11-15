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

package com.android.ide.eclipse.gltrace;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class CollectTraceAction implements IWorkbenchWindowActionDelegate {
    public void run(IAction action) {
        connectToDevice();
    }

    public void selectionChanged(IAction action, ISelection selection) {
    }

    public void dispose() {
    }

    public void init(IWorkbenchWindow window) {
    }

    private void connectToDevice() {
        int port = 5039;

        Shell shell = Display.getDefault().getActiveShell();
        GLTraceOptionsDialog dlg = new GLTraceOptionsDialog(shell);
        if (dlg.open() != Window.OK) {
            return;
        }

        String activityName = dlg.getApplicationToTrace();
        String traceDestination = dlg.getTraceDestination();
        String deviceName = dlg.getDevice();

        IDevice device = getDevice(deviceName);

        String appName = activityName.split("/")[0];
        killApp(device, appName);

        try {
            setGLTraceOn(device, appName);
        } catch (Exception e) {
            MessageDialog.openError(shell, "Setup GL Trace",
                    "Error initializing GL Trace on device: " + e.getMessage());
        }

        try {
            setupForwarding(device, port);
        } catch (Exception e) {
            MessageDialog.openError(shell, "Setup GL Trace",
                    "Error while setting up port forwarding: " + e.getMessage());
        }

        try {
            startActivity(device, activityName);
        } catch (Exception e) {
            MessageDialog.openError(shell, "Setup GL Trace",
                    "Error while launching application: " + e.getMessage());
        }

        try {
            // wait a couple of seconds for the application to launch on the device
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            // can't be interrupted
        }

        // if everything went well, the app should now be waiting for the gl debugger
        // to connect
        startTracing(shell, traceDestination, port);
    }

    private void startTracing(Shell shell, String traceDestination, int port) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(traceDestination, false);
        } catch (FileNotFoundException e) {
            // input path is valid, so this cannot occur
        }

        GLTraceWriter writer = new GLTraceWriter(fos, port, new GLTraceCollectorDialog(shell));
        writer.start();
    }

    private void startActivity(IDevice device, String appName)
            throws TimeoutException, AdbCommandRejectedException,
            ShellCommandUnresponsiveException, IOException {
        String startAppCmd = String.format(
                "am start %s -a android.intent.action.MAIN -c android.intent.category.LAUNCHER", //$NON-NLS-1$
                appName);
        System.out.println(startAppCmd);
        device.executeShellCommand(startAppCmd, new IgnoreOutputReceiver());
    }

    private void setupForwarding(IDevice device, int i)
            throws TimeoutException, AdbCommandRejectedException, IOException {
        device.createForward(i, i);
    }

    // adb shell setprop debug.egl.debug_proc <procname>
    // adb shell setprop debug.egl.debug_forceUseFile 0
    private void setGLTraceOn(IDevice device, String appName)
            throws TimeoutException, AdbCommandRejectedException,
            ShellCommandUnresponsiveException, IOException {
        String setDebugProcProperty = "setprop debug.egl.debug_proc " + appName; //$NON-NLS-1$
        String setNoFileProperty = "setprop debug.egl.debug_forceUseFile 0";     //$NON-NLS-1$

        device.executeShellCommand(setDebugProcProperty, new IgnoreOutputReceiver());
        device.executeShellCommand(setNoFileProperty, new IgnoreOutputReceiver());
    }

    private IDevice getDevice(String deviceName) {
        IDevice[] devices = AndroidDebugBridge.getBridge().getDevices();

        for (IDevice device : devices) {
            String name = device.getAvdName();
            if (name == null) {
                name = device.getSerialNumber();
            }

            if (name.equals(deviceName)) {
                return device;
            }
        }

        return null;
    }

    private void killApp(IDevice device, String appName) {
        Client client = device.getClient(appName);
        if (client != null) {
            client.kill();
        }
    }

    private static class IgnoreOutputReceiver implements IShellOutputReceiver {
        public void addOutput(byte[] arg0, int arg1, int arg2) {
        }

        public void flush() {
        }

        public boolean isCancelled() {
            return false;
        }
    }
}
