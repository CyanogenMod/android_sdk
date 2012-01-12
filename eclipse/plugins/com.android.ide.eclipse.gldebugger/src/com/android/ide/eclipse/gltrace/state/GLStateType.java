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

/** The type for each OpenGL State Property {@link IGLProperty}. */
public enum GLStateType {
    // Note: the indentation reflects the state hierarchy.

    GL_STATE("OpenGL State Variables"),
        GL_STATE_ES1("OpenGL ES 1.1 State"),
        GL_STATE_ES2("OpenGL ES 2.0 State"),

    VERTEX_ARRAY_DATA("Vertex Array Data"),
        VERTEX_ATTRIB_ARRAY("Vertex Attrib Array Properties"),
        VERTEX_ATTRIB_ARRAY_COMPOSITE("Vertex Attrib Array #n Properties"),
            VERTEX_ATTRIB_ARRAY_ENABLED("Vertex Attrib Array Enable"),
            VERTEX_ATTRIB_ARRAY_SIZE("Vertex Attrib Array Size"),
            VERTEX_ATTRIB_ARRAY_STRIDE("Vertex Attrib Array Stride"),
            VERTEX_ATTRIB_ARRAY_TYPE("Vertex Attrib Array Type"),
            VERTEX_ATTRIB_ARRAY_NORMALIZED("Vertex Attrib Array Normalized"),
            VERTEX_ATTRIB_ARRAY_POINTER("Vertex Attrib Array Pointer"),

        BUFFER_BINDINGS("Buffer Bindings"),
            ARRAY_BUFFER_BINDING("Current Buffer Binding"),
            ELEMENT_ARRAY_BUFFER_BINDING("Element Array Buffer Binding"),
            VERTEX_ATTRIB_ARRAY_BUFFER_BINDINGS("Attribute Array Buffer Bindings"),
            VERTEX_ATTRIB_ARRAY_BUFFER_BINDING_PER_INDEX("Attribute Array Buffer Binding"),

    FRAMEBUFFER_STATE("Framebuffer State"),
        FRAMEBUFFER_BINDING("Framebuffer Binding"),
        FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE("Framebuffer object type"),
        FRAMEBUFFER_ATTACHMENT_OBJECT_NAME("Framebuffer object name"),
        FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL("Framebuffer texture level"),
        FRAMEBUFFER_ATTACHEMENT_TEXTURE_CUBE_MAP_FACE("Framebuffer texture cubemap face");


    private final String mDescription;

    GLStateType(String description) {
        mDescription = description;
    }

    public String getDescription() {
        return mDescription;
    }
}
