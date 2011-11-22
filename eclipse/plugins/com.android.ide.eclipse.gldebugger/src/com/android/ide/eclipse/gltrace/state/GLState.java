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

public class GLState {
    private static GLState sGLState = new GLState();

    private IGLProperty createBufferBindings() {
        IGLProperty array, eArray, vArray;

        array      = new GLIntegerProperty(GLStateType.ARRAY_BUFFER_BINDING, 0);
        eArray     = new GLIntegerProperty(GLStateType.ELEMENT_ARRAY_BUFFER_BINDING, 0);
        vArray     = new GLIntegerProperty(
                GLStateType.VERTEX_ATTRIB_ARRAY_BUFFER_BINDING_PER_INDEX, 0);

        IGLProperty vArray8 = new GLListProperty(GLStateType.VERTEX_ATTRIB_ARRAY_BUFFER_BINDINGS,
                vArray, 8);

        return new GLCompositeProperty(
                GLStateType.BUFFER_BINDINGS,
                array,
                eArray,
                vArray8);
    }

    private IGLProperty createVertexAttribArrays() {
        IGLProperty enabled, size, stride, type, normalized, pointer;

        enabled    = new GLBooleanProperty(GLStateType.VERTEX_ATTRIB_ARRAY_ENABLED, false);
        size       = new GLIntegerProperty(GLStateType.VERTEX_ATTRIB_ARRAY_SIZE, 4);
        stride     = new GLIntegerProperty(GLStateType.VERTEX_ATTRIB_ARRAY_STRIDE, 0);
        type       = new GLEnumProperty(GLStateType.VERTEX_ATTRIB_ARRAY_TYPE, GLEnum.GL_FLOAT);
        normalized = new GLBooleanProperty(GLStateType.VERTEX_ATTRIB_ARRAY_NORMALIZED, false);
        pointer    = new GLIntegerProperty(GLStateType.VERTEX_ATTRIB_ARRAY_POINTER, 0);

        IGLProperty perVertexAttribArrayState = new GLCompositeProperty(
                GLStateType.VERTEX_ATTRIB_ARRAY_COMPOSITE,
                enabled,
                size,
                stride,
                type,
                normalized,
                pointer);

        return new GLListProperty(
                GLStateType.VERTEX_ATTRIB_ARRAY,
                perVertexAttribArrayState,
                8);
    }

    private IGLProperty createVertexArrayData() {
        IGLProperty vertexAttribArrays = createVertexAttribArrays();
        IGLProperty bufferBindings = createBufferBindings();

        GLCompositeProperty vertexArrayData = new GLCompositeProperty(GLStateType.VERTEX_ARRAY_DATA,
                vertexAttribArrays,
                bufferBindings);

        return vertexArrayData;
    }

    /** Construct the default OpenGL State hierarchy. */
    public static IGLProperty createDefaultState() {
        // TODO: Currently, this only models a tiny subset of the OpenGL state: the properties for
        // vertex array data.
        GLCompositeProperty glState = new GLCompositeProperty(GLStateType.GL_STATE,
                sGLState.createVertexArrayData());
        return glState;
    }
}
