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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;

public class CollectTraceAction implements IWorkbenchWindowActionDelegate {
    @Override
    public void run(IAction action) {
        connectToDevice();
    }

    @Override
    public void selectionChanged(IAction action, ISelection selection) {
    }

    @Override
    public void dispose() {
    }

    @Override
    public void init(IWorkbenchWindow window) {
    }

    private void connectToDevice() {
        int port = 5039;

        Shell shell = Display.getDefault().getActiveShell();
        GLTraceOptionsDialog dlg = new GLTraceOptionsDialog(shell);
        if (dlg.open() != Window.OK) {
            return;
        }

        TraceOptions traceOptions = dlg.getTraceOptions();

        IDevice device = getDevice(traceOptions.device);
        String appName = traceOptions.activityToTrace.split("/")[0]; //$NON-NLS-1$
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
            startActivity(device, traceOptions.activityToTrace);
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
        startTracing(shell, traceOptions, port);
    }

    private void startTracing(Shell shell, TraceOptions traceOptions, int port) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(traceOptions.traceDestination, false);
        } catch (FileNotFoundException e) {
            // input path is valid, so this cannot occur
        }

        Socket socket = new Socket();
        DataInputStream traceDataStream = null;
        DataOutputStream traceCommandsStream = null;
        try {
            socket.connect(new java.net.InetSocketAddress("127.0.0.1", port)); //$NON-NLS-1$
            socket.setTcpNoDelay(true);
            traceDataStream = new DataInputStream(socket.getInputStream());
            traceCommandsStream = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            MessageDialog.openError(shell,
                    "OpenGL Trace",
                    "Unable to connect to remote GL Trace Server: " + e.getMessage());
            return;
        }

        // create channel to send trace commands to device
        TraceCommandWriter traceCommandWriter = new TraceCommandWriter(traceCommandsStream);
        try {
            traceCommandWriter.setTraceOptions(traceOptions.collectFbOnEglSwap,
                    traceOptions.collectFbOnGlDraw,
                    traceOptions.collectTextureData);
        } catch (IOException e) {
            MessageDialog.openError(shell,
                    "OpenGL Trace",
                    "Unexpected error while setting trace options: " + e.getMessage());
            closeSocket(socket);
            return;
        }

        // create trace writer that writes to a trace file
        TraceFileWriter traceFileWriter = new TraceFileWriter(fos, traceDataStream);
        traceFileWriter.start();

        GLTraceCollectorDialog dlg = new GLTraceCollectorDialog(shell,
                traceFileWriter,
                traceCommandWriter,
                traceOptions);
        dlg.open();

        traceFileWriter.stopTracing();
        traceCommandWriter.close();
        closeSocket(socket);
    }

    private void closeSocket(Socket socket) {
        try {
            socket.close();
        } catch (IOException e) {
            // ignore error while closing socket
        }
    }

    private void startActivity(IDevice device, String appName)
            throws TimeoutException, AdbCommandRejectedException,
            ShellCommandUnresponsiveException, IOException {
        String startAppCmd = String.format(
                "am start %s -a android.intent.action.MAIN -c android.intent.category.LAUNCHER", //$NON-NLS-1$
                appName);
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
        @Override
        public void addOutput(byte[] arg0, int arg1, int arg2) {
        }

        @Override
        public void flush() {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }
}
