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
import com.android.ide.eclipse.gltrace.Glcall.GLCall.Function;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Read a file containing encoded GL Trace information and create the in memory model in
 * {@link GLTrace}.
 */
public class GLTraceReader {
    DataInputStream fs;

    public GLTraceReader(String fname) {
        try {
            fs = new DataInputStream(new FileInputStream(fname));
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private GLCall getNextMessage() {
        int len;
        byte[] b;
        try {
            len = fs.readInt();
            b = new byte[len];
            fs.read(b, 0, len);
        } catch (EOFException e) {
            return null;
        } catch (IOException e) {
            return null;
        }

        try {
            return GLCall.parseFrom(b);
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
            return null;
        }
    }

    public GLTrace parseTrace() {
        GLTrace trace = new GLTrace();

        GLCall c = null;
        GLFrame frame = null;

        while ((c = getNextMessage()) != null) {
            if (frame == null) {
                frame = new GLFrame();
            }

            frame.addGLCall(c);

            if (c.getFunction() == Function.eglSwapBuffers) {
                trace.addGLFrame(frame);
                frame = null;
            }
        }

        return trace;
    }
}
