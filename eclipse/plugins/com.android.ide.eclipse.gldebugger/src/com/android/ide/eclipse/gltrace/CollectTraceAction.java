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
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IDevice.DeviceUnixSocketNamespace;
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
import java.util.concurrent.Semaphore;

public class CollectTraceAction implements IWorkbenchWindowActionDelegate {
    /** Abstract Unix Domain Socket Name used by the gltrace device code. */
    private static final String GLTRACE_UDS = "gltrace";

    /** Local port that is forwarded to the device's {@link #GLTRACE_UDS} socket. */
    private static final int LOCAL_FORWARDED_PORT = 6039;

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
        Shell shell = Display.getDefault().getActiveShell();
        GLTraceOptionsDialog dlg = new GLTraceOptionsDialog(shell);
        if (dlg.open() != Window.OK) {
            return;
        }

        TraceOptions traceOptions = dlg.getTraceOptions();

        IDevice device = getDevice(traceOptions.device);
        try {
            setupForwarding(device, LOCAL_FORWARDED_PORT);
        } catch (Exception e) {
            MessageDialog.openError(shell, "Setup GL Trace",
                    "Error while setting up port forwarding: " + e.getMessage());
        }

        try {
            startActivity(device, traceOptions.activityToTrace);
        } catch (Exception e) {
            MessageDialog.openError(shell, "Setup GL Trace",
                    "Error while launching application: " + e.getMessage());
            return;
        }

        try {
            // wait a couple of seconds for the application to launch on the device
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            // can't be interrupted
        }

        // if everything went well, the app should now be waiting for the gl debugger
        // to connect
        startTracing(shell, traceOptions, LOCAL_FORWARDED_PORT);

        // once tracing is complete, remove port forwarding
        disablePortForwarding(device, LOCAL_FORWARDED_PORT);
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
            ShellCommandUnresponsiveException, IOException, InterruptedException {
        String startAppCmd = String.format(
                "am start -S --opengl-trace %s -a android.intent.action.MAIN -c android.intent.category.LAUNCHER", //$NON-NLS-1$
                appName);

        Semaphore launchCompletionSempahore = new Semaphore(0);
        StartActivityOutputReceiver receiver = new StartActivityOutputReceiver(
                launchCompletionSempahore);
        device.executeShellCommand(startAppCmd, receiver);

        // wait until shell finishes launch
        launchCompletionSempahore.acquire();

        // throw exception if there was an error during launch
        String output = receiver.getOutput();
        if (output.contains("Error")) {             //$NON-NLS-1$
            throw new RuntimeException(output);
        }
    }

    private void setupForwarding(IDevice device, int i)
            throws TimeoutException, AdbCommandRejectedException, IOException {
        device.createForward(i, GLTRACE_UDS, DeviceUnixSocketNamespace.ABSTRACT);
    }

    private void disablePortForwarding(IDevice device, int port) {
        try {
            device.removeForward(port, GLTRACE_UDS, DeviceUnixSocketNamespace.ABSTRACT);
        } catch (Exception e) {
            // ignore exceptions;
        }
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

    private static class StartActivityOutputReceiver implements IShellOutputReceiver {
        private Semaphore mSemaphore;
        private StringBuffer sb = new StringBuffer(300);

        public StartActivityOutputReceiver(Semaphore s) {
            mSemaphore = s;
        }

        @Override
        public void addOutput(byte[] data, int offset, int length) {
            String d = new String(data, offset, length);
            sb.append(d);
        }

        @Override
        public void flush() {
            mSemaphore.release();
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        public String getOutput() {
            return sb.toString();
        }
    }
}
