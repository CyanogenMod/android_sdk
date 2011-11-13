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

import com.android.ide.eclipse.gltrace.Glcall.GLCall;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;

/** A class that streams data received from a socket into the trace file. */
public class GLTraceWriter {
    private DataOutputStream mOutputStream;
    private int mPort;
    private GLTraceCollectorDialog mDialog;
    private volatile boolean stopTracing = false;
    private Thread mReceiverThread;

    private int mFileSize = 0;
    private int mFrameCount = 0;

    public GLTraceWriter(FileOutputStream fos, int port,
            GLTraceCollectorDialog glTraceCollectorDialog) {
        mOutputStream = new DataOutputStream(fos);
        mPort = port;
        mDialog = glTraceCollectorDialog;
    }

    public void start() {
        // launch thread
        mReceiverThread = new Thread(new GLTraceReceiverTask());
        mReceiverThread.setName("GL Trace Receiver");
        mReceiverThread.start();

        // launch dialog
        mDialog.setTraceWriter(this);
        mDialog.open();
    }

    public void stopTracing() {
        // stop thread
        stopTracing = true;

        // wait for receiver to complete
        try {
            mReceiverThread.join();
        } catch (InterruptedException e1) {
            // ignore, this cannot be interrupted
        }

        // close stream
        try {
            mOutputStream.close();
        } catch (IOException e) {
            // ignore error while closing stream
        }
    }

    private class GLTraceReceiverTask implements Runnable {
        public void run() {
            try {
                Socket socket = new Socket();
                socket.connect(new java.net.InetSocketAddress("127.0.0.1", mPort));
                DataInputStream dis = new DataInputStream(socket.getInputStream());

                while (!stopTracing) {
                    if (dis.available() > 0) {
                        readMessage(dis);
                        mDialog.setFrameCount(mFrameCount);
                        mDialog.setTraceFileSize(mFileSize);
                    } else {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            // ignore, as this thread is not interrupted by any other thread.
                        }
                    }
                }

                socket.close();
            } catch (IOException e) {
            }
        }
    }

    private void readMessage(DataInputStream dis) throws IOException {
        int len = dis.readInt();
        len = Integer.reverseBytes(len);    // readInt is big endian, we want little endian

        byte[] buffer = new byte[len];
        int readLen = 0;
        while (readLen < len) {
            int read = dis.read(buffer, readLen, len - readLen);
            if (read < 0) {
                throw new IOException();
            } else {
                readLen += read;
            }
        }

        GLCall msg = null;
        try {
            msg = GLCall.parseFrom(buffer);
        } catch (InvalidProtocolBufferException e) {
            System.out.println("Invalid protocol buffer: " + e.getMessage());
            return;
        }
        mOutputStream.writeInt(len);
        mOutputStream.write(buffer);

        mFileSize += readLen;

        if (msg.getFunction() == GLCall.Function.eglSwapBuffers) {
            mFrameCount++;
        }
    }
}
