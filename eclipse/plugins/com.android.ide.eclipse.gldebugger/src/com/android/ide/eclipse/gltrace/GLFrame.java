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
import com.android.ide.eclipse.gltrace.state.GLState;
import com.android.ide.eclipse.gltrace.state.GLStateTransform;
import com.android.ide.eclipse.gltrace.state.IGLProperty;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A GLFrame is composed of a set of GL API Calls, ending with eglSwapBuffer. */
public class GLFrame {
    private List<GLCall> mGLCalls;

    /** List of state transforms to be applied for each GLCall */
    private List<List<GLStateTransform>> mStateTransformsPerCall;

    /** OpenGL State as of call {@link #mCurrentStateIndex}. */
    private IGLProperty mState;
    private int mCurrentStateIndex;

    public GLFrame() {
        mGLCalls = new ArrayList<GLCall>();
        mStateTransformsPerCall = new ArrayList<List<GLStateTransform>>();

        mState = GLState.createDefaultState();
        mCurrentStateIndex = -1;
    }

    public void addGLCall(GLCall c) {
        mGLCalls.add(c);
        mStateTransformsPerCall.add(GLStateTransform.getTransformFor(c));
    }

    public List<GLCall> getGLCalls() {
        return mGLCalls;
    }

    public IGLProperty getStateAt(GLCall call) {
        int callIndex = mGLCalls.indexOf(call);

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
        int fromIndex = mGLCalls.indexOf(from);
        int toIndex = mGLCalls.indexOf(to);

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
}
