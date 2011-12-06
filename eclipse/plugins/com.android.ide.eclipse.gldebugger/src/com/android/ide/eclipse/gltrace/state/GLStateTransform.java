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

package com.android.ide.eclipse.gltrace.state;

import com.android.ide.eclipse.gldebugger.GLEnum;
import com.android.ide.eclipse.gltrace.model.GLCall;

import java.util.ArrayList;
import java.util.List;

/**
 * A GLStateTransform object encapsulates a change to an {@link IGLProperty}. Each OpenGL
 * call might create a list of this GLStateTransform's to effect changes to multiple properties in
 * the OpenGL state.
 */
public class GLStateTransform {
    private final GLPropertyAccessor mAccessor;
    private final Object mNewValue;
    private Object mOldValue;

    /**
     * Construct a state transform that will extract the property using the accessor,
     * and modify its value to the provided value.
     */
    private GLStateTransform(GLPropertyAccessor accessor, Object newValue) {
        mAccessor = accessor;
        mNewValue = newValue;
        mOldValue = null;
    }

    /** Apply the state transformation on the given OpenGL state. */
    public void apply(IGLProperty state) {
        IGLProperty property = mAccessor.getProperty(state);

        assert mOldValue == null : "Transform cannot be applied multiple times";
        mOldValue = property.getValue();
        property.setValue(mNewValue);
    }

    /**
     * Reverses the effect of this state transform. It restores the property's value to the same
     * state as it was before this transformation was applied. If this transform was never
     * {@link #apply(IGLProperty)}'ed, then performing a revert has no effect.
     */
    public void revert(IGLProperty state) {
        if (mOldValue != null) {
            IGLProperty property = mAccessor.getProperty(state);
            property.setValue(mOldValue);
            mOldValue = null;
        }
    }

    /** Gets the property that will be affected by applying this transformation. */
    public IGLProperty getChangedProperty(IGLProperty state) {
        return mAccessor.getProperty(state);
    }

    /** Construct a list of transformations to be applied for the provided OpenGL call. */
    public static List<GLStateTransform> getTransformsFor(GLCall call) {
        // TODO: currently we only modify state for glVertexAttribPointer() call.
        // Obviously, this will grow to be a long list.
        switch (call.getFunction()) {
            case glVertexAttribPointer:
                return transformsForGlVertexAttribPointer(call);
            default:
                return new ArrayList<GLStateTransform>();
        }
    }

    private static List<GLStateTransform> transformsForGlVertexAttribPointer(GLCall call) {
        int index = call.getArg(0).getIntValue(0);

        int size = call.getArg(1).getIntValue(0);
        int type = call.getArg(2).getIntValue(0);
        boolean normalized = call.getArg(3).getBoolValue(0);
        int stride = call.getArg(4).getIntValue(0);
        int pointer = call.getArg(5).getIntValue(0);

        List<GLStateTransform> transforms = new ArrayList<GLStateTransform>();
        transforms.add(new GLStateTransform(
                GLPropertyAccessor.makeAccessor(GLStateType.VERTEX_ARRAY_DATA,
                                                GLStateType.VERTEX_ATTRIB_ARRAY,
                                                Integer.valueOf(index),
                                                GLStateType.VERTEX_ATTRIB_ARRAY_SIZE),
                Integer.valueOf(size)));
        transforms.add(new GLStateTransform(
                GLPropertyAccessor.makeAccessor(GLStateType.VERTEX_ARRAY_DATA,
                                                GLStateType.VERTEX_ATTRIB_ARRAY,
                                                Integer.valueOf(index),
                                                GLStateType.VERTEX_ATTRIB_ARRAY_TYPE),
                GLEnum.valueOf(type)));
        transforms.add(new GLStateTransform(
                GLPropertyAccessor.makeAccessor(GLStateType.VERTEX_ARRAY_DATA,
                                                GLStateType.VERTEX_ATTRIB_ARRAY,
                                                Integer.valueOf(index),
                                                GLStateType.VERTEX_ATTRIB_ARRAY_NORMALIZED),
                Boolean.valueOf(normalized)));
        transforms.add(new GLStateTransform(
                GLPropertyAccessor.makeAccessor(GLStateType.VERTEX_ARRAY_DATA,
                                                GLStateType.VERTEX_ATTRIB_ARRAY,
                                                Integer.valueOf(index),
                                                GLStateType.VERTEX_ATTRIB_ARRAY_STRIDE),
                Integer.valueOf(stride)));
        transforms.add(new GLStateTransform(
                GLPropertyAccessor.makeAccessor(GLStateType.VERTEX_ARRAY_DATA,
                                                GLStateType.VERTEX_ATTRIB_ARRAY,
                                                Integer.valueOf(index),
                                                GLStateType.VERTEX_ATTRIB_ARRAY_POINTER),
                Integer.valueOf(pointer)));
        return transforms;
    }
}
