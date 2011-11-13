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

import java.util.ArrayList;
import java.util.List;

/** A GLFrame is composed of a set of GL API Calls, ending with eglSwapBuffer. */
public class GLFrame {
    private List<GLCall> mGLCalls;

    public GLFrame() {
        mGLCalls = new ArrayList<GLCall>();
    }

    public void addGLCall(GLCall c) {
        mGLCalls.add(c);
    }

    public List<GLCall> getGLCalls() {
        return mGLCalls;
    }
}
