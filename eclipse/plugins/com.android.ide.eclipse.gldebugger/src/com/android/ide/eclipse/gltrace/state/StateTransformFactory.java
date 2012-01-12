/*
 * Copyright (C) 2012 The Android Open Source Project
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
import com.android.ide.eclipse.gltrace.GLProtoBuf.GLMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StateTransformFactory {
    /** Construct a list of transformations to be applied for the provided OpenGL call. */
    public static List<IStateTransform> getTransformsFor(GLMessage msg) {
        switch (msg.getFunction()) {
            case glVertexAttribPointer:
                return transformsForGlVertexAttribPointer(msg);
            case glBindFramebuffer:
                return transformsForGlBindFramebuffer(msg);
            case eglCreateContext:
                return transformsForEglCreateContext(msg);
            default:
                return Collections.emptyList();
        }
    }

    private static List<IStateTransform> transformsForGlVertexAttribPointer(GLMessage msg) {
        int index = msg.getArgs(0).getIntValue(0);

        int size = msg.getArgs(1).getIntValue(0);
        int type = msg.getArgs(2).getIntValue(0);
        boolean normalized = msg.getArgs(3).getBoolValue(0);
        int stride = msg.getArgs(4).getIntValue(0);
        int pointer = msg.getArgs(5).getIntValue(0);

        List<IStateTransform> transforms = new ArrayList<IStateTransform>();
        transforms.add(new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(msg.getContextId(),
                                                GLStateType.VERTEX_ARRAY_DATA,
                                                GLStateType.VERTEX_ATTRIB_ARRAY,
                                                Integer.valueOf(index),
                                                GLStateType.VERTEX_ATTRIB_ARRAY_SIZE),
                Integer.valueOf(size)));
        transforms.add(new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(msg.getContextId(),
                                                GLStateType.VERTEX_ARRAY_DATA,
                                                GLStateType.VERTEX_ATTRIB_ARRAY,
                                                Integer.valueOf(index),
                                                GLStateType.VERTEX_ATTRIB_ARRAY_TYPE),
                GLEnum.valueOf(type)));
        transforms.add(new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(msg.getContextId(),
                                                GLStateType.VERTEX_ARRAY_DATA,
                                                GLStateType.VERTEX_ATTRIB_ARRAY,
                                                Integer.valueOf(index),
                                                GLStateType.VERTEX_ATTRIB_ARRAY_NORMALIZED),
                Boolean.valueOf(normalized)));
        transforms.add(new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(msg.getContextId(),
                                                GLStateType.VERTEX_ARRAY_DATA,
                                                GLStateType.VERTEX_ATTRIB_ARRAY,
                                                Integer.valueOf(index),
                                                GLStateType.VERTEX_ATTRIB_ARRAY_STRIDE),
                Integer.valueOf(stride)));
        transforms.add(new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(msg.getContextId(),
                                                GLStateType.VERTEX_ARRAY_DATA,
                                                GLStateType.VERTEX_ATTRIB_ARRAY,
                                                Integer.valueOf(index),
                                                GLStateType.VERTEX_ATTRIB_ARRAY_POINTER),
                Integer.valueOf(pointer)));
        return transforms;
    }

    private static List<IStateTransform> transformsForGlBindFramebuffer(GLMessage msg) {
        // void glBindFramebuffer(GLenum target, GLuint framebuffer);
        int fb = msg.getArgs(1).getIntValue(0);
        IStateTransform transform = new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(msg.getContextId(),
                        GLStateType.FRAMEBUFFER_STATE,
                        GLStateType.FRAMEBUFFER_BINDING),
                fb);
        return Collections.singletonList(transform);
    }

    private static List<IStateTransform> transformsForEglCreateContext(GLMessage msg) {
        // void eglCreateContext(int version, int context);
        int version = msg.getArgs(0).getIntValue(0);
        IGLProperty glState = null;
        if (version == 0) {
            glState = GLState.createDefaultES1State();
        } else {
            glState = GLState.createDefaultES2State();
        }
        IStateTransform transform = new ListElementAddTransform(null, glState);
        return Collections.singletonList(transform);
    }
}
