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

package com.android.ide.eclipse.gltrace.model;

import com.android.ide.eclipse.gltrace.GLProtoBuf.GLMessage;
import com.android.ide.eclipse.gltrace.ProtoBufUtils;
import com.android.ide.eclipse.gltrace.TraceFileInfo;
import com.android.ide.eclipse.gltrace.TraceFileReader;
import com.android.ide.eclipse.gltrace.state.GLState;
import com.android.ide.eclipse.gltrace.state.GLStateTransform;
import com.android.ide.eclipse.gltrace.state.IGLProperty;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** GLTrace is the in memory model of a OpenGL trace file. */
public class GLTrace {
    private static final TraceFileReader sTraceFileReader = new TraceFileReader();

    /** Information regarding the trace file. */
    private final TraceFileInfo mTraceFileInfo;

    /** List of frames in the trace. */
    private final List<GLFrame> mGLFrames;

    /** List of GL Calls comprising the trace. */
    private final List<GLCall> mGLCalls;

    /** List of state transforms to be applied for each GLCall */
    private final List<List<GLStateTransform>> mStateTransformsPerCall;

    /** OpenGL State as of call {@link #mCurrentStateIndex}. */
    private IGLProperty mState;
    private int mCurrentStateIndex;

    public GLTrace(TraceFileInfo traceFileInfo, List<GLFrame> glFrames, List<GLCall> glCalls,
            List<List<GLStateTransform>> stateTransformsPerCall) {
        mTraceFileInfo = traceFileInfo;
        mGLFrames = glFrames;
        mGLCalls = glCalls;
        mStateTransformsPerCall = stateTransformsPerCall;

        mState = GLState.createDefaultState();
        mCurrentStateIndex = -1;
    }

    public List<GLFrame> getFrames() {
        return mGLFrames;
    }

    public List<GLCall> getGLCallsForFrame(int frameIndex) {
        if (frameIndex >= mGLFrames.size()) {
            return Collections.emptyList();
        }

        GLFrame frame = mGLFrames.get(frameIndex);
        int start = frame.getStartIndex();
        int end = frame.getEndIndex();

        return mGLCalls.subList(start, end);
    }

    public IGLProperty getStateAt(GLCall call) {
        if (call == null) {
            return null;
        }

        int callIndex = call.getIndex();
        if (callIndex == mCurrentStateIndex) {
            return mState;
        }

        if (callIndex > mCurrentStateIndex) {
            // if the state is needed for a future GLCall, then apply the transformations
            // for all the GLCall's upto and including the required GLCall
            for (int i = mCurrentStateIndex + 1; i <= callIndex; i++) {
                for (GLStateTransform f : mStateTransformsPerCall.get(i)) {
                    f.apply(mState);
                }
            }

            mCurrentStateIndex = callIndex;
            return mState;
        }

        // if the state is needed for a call that is before the current index,
        // then revert the transformations until we reach the required call
        for (int i = mCurrentStateIndex; i > callIndex; i--) {
            for (GLStateTransform f : mStateTransformsPerCall.get(i)) {
                f.revert(mState);
            }
        }

        mCurrentStateIndex = callIndex;
        return mState;
    }

    /**
     * Gets the set of properties in the provided OpenGL state that are affected by
     * changing state from one GL call to another.
     */
    public Set<IGLProperty> getChangedProperties(GLCall from, GLCall to, IGLProperty state) {
        int fromIndex = from == null ? 0 : from.getIndex();
        int toIndex = to == null ? 0 : to.getIndex();

        if (fromIndex == -1 || toIndex == -1) {
            return null;
        }

        int setSizeHint = 3 * Math.abs(fromIndex - toIndex) + 10;
        Set<IGLProperty> changedProperties = new HashSet<IGLProperty>(setSizeHint);

        for (int i = Math.min(fromIndex, toIndex); i <= Math.max(fromIndex, toIndex); i++) {
            for (GLStateTransform f : mStateTransformsPerCall.get(i)) {
                IGLProperty changedProperty = f.getChangedProperty(state);
                // add the property that is affected
                changedProperties.add(changedProperty);

                // also add its entire parent chain until we reach the root
                IGLProperty parent = changedProperty.getParent();
                while (parent != null) {
                    changedProperties.add(parent);
                    parent = parent.getParent();
                }
            }
        }

        return changedProperties;
    }

    public Image getImage(GLCall c) {
        if (!c.hasFb()) {
            return null;
        }

        if (isTraceFileModified()) {
            return c.getThumbnailImage();
        }

        RandomAccessFile file;
        try {
            file = new RandomAccessFile(mTraceFileInfo.getPath(), "r"); //$NON-NLS-1$
        } catch (FileNotFoundException e1) {
            return c.getThumbnailImage();
        }

        GLMessage m = null;
        try {
            m = sTraceFileReader.getMessageAtOffset(file, c.getOffsetInTraceFile());
        } catch (Exception e) {
            return null;
        } finally {
            try {
                file.close();
            } catch (IOException e) {
                // ignore exception while closing file
            }
        }

        return ProtoBufUtils.getImage(Display.getCurrent(), m);
    }

    private boolean isTraceFileModified() {
        File f = new File(mTraceFileInfo.getPath());
        return f.length() != mTraceFileInfo.getSize()
                || f.lastModified() != mTraceFileInfo.getLastModificationTime();
    }
}
