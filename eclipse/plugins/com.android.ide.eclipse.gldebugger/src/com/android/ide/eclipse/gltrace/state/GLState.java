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
import com.android.ide.eclipse.gltrace.state.GLIntegerProperty.DisplayRadix;

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

        return new GLCompositeProperty(GLStateType.VERTEX_ARRAY_DATA,
                vertexAttribArrays,
                bufferBindings);
    }

    private IGLProperty createTransformationState() {
        IGLProperty viewPortX = new GLIntegerProperty(GLStateType.VIEWPORT_X, 0);
        IGLProperty viewPortY = new GLIntegerProperty(GLStateType.VIEWPORT_Y, 0);
        IGLProperty viewPortW = new GLIntegerProperty(GLStateType.VIEWPORT_WIDTH, 0);
        IGLProperty viewPortH = new GLIntegerProperty(GLStateType.VIEWPORT_HEIGHT, 0);
        IGLProperty viewPort = new GLCompositeProperty(GLStateType.VIEWPORT,
                viewPortX, viewPortY, viewPortW, viewPortH);

        IGLProperty clampNear = new GLFloatProperty(GLStateType.DEPTH_RANGE_NEAR,
                Float.valueOf(0.0f));
        IGLProperty clampFar = new GLFloatProperty(GLStateType.DEPTH_RANGE_FAR,
                Float.valueOf(1.0f));
        IGLProperty depthRange = new GLCompositeProperty(GLStateType.DEPTH_RANGE,
                clampNear,
                clampFar);

        IGLProperty transformationState = new GLCompositeProperty(GLStateType.TRANSFORMATION_STATE,
                viewPort,
                depthRange);
        return transformationState;
    }

    private IGLProperty createRasterizationState() {
        IGLProperty lineWidth = new GLFloatProperty(GLStateType.LINE_WIDTH, Float.valueOf(1.0f));
        IGLProperty cullFace = new GLBooleanProperty(GLStateType.CULL_FACE, Boolean.FALSE);
        IGLProperty cullFaceMode = new GLEnumProperty(GLStateType.CULL_FACE_MODE, GLEnum.GL_BACK);
        IGLProperty frontFace = new GLEnumProperty(GLStateType.FRONT_FACE, GLEnum.GL_CCW);
        IGLProperty polyOffsetFactor = new GLFloatProperty(GLStateType.POLYGON_OFFSET_FACTOR,
                Float.valueOf(0f));
        IGLProperty polyOffsetUnits = new GLFloatProperty(GLStateType.POLYGON_OFFSET_UNITS,
                Float.valueOf(0f));
        IGLProperty polyOffsetFill = new GLBooleanProperty(GLStateType.POLYGON_OFFSET_FILL,
                Boolean.FALSE);

        return new GLCompositeProperty(GLStateType.RASTERIZATION_STATE,
                lineWidth,
                cullFace,
                cullFaceMode,
                frontFace,
                polyOffsetFactor,
                polyOffsetUnits,
                polyOffsetFill);
    }

    private IGLProperty createPixelOperationsState() {
        IGLProperty scissorTest = new GLBooleanProperty(GLStateType.SCISSOR_TEST, Boolean.FALSE);
        IGLProperty scissorBoxX = new GLIntegerProperty(GLStateType.SCISSOR_BOX_X, 0);
        IGLProperty scissorBoxY = new GLIntegerProperty(GLStateType.SCISSOR_BOX_Y, 0);
        IGLProperty scissorBoxW = new GLIntegerProperty(GLStateType.SCISSOR_BOX_WIDTH, 0);
        IGLProperty scissorBoxH = new GLIntegerProperty(GLStateType.SCISSOR_BOX_HEIGHT, 0);
        IGLProperty scissorBox = new GLCompositeProperty(GLStateType.SCISSOR_BOX,
                scissorBoxX, scissorBoxY, scissorBoxW, scissorBoxH);

        IGLProperty stencilTest = new GLBooleanProperty(GLStateType.STENCIL_TEST, Boolean.FALSE);
        IGLProperty stencilFunc = new GLEnumProperty(GLStateType.STENCIL_FUNC, GLEnum.GL_ALWAYS);
        IGLProperty stencilMask = new GLIntegerProperty(GLStateType.STENCIL_VALUE_MASK,
                Integer.valueOf(0xffffffff), DisplayRadix.HEX);
        IGLProperty stencilRef = new GLIntegerProperty(GLStateType.STENCIL_REF,
                Integer.valueOf(0));
        IGLProperty stencilFail = new GLEnumProperty(GLStateType.STENCIL_FAIL, GLEnum.GL_KEEP);
        IGLProperty stencilPassDepthFail = new GLEnumProperty(GLStateType.STENCIL_PASS_DEPTH_FAIL,
                GLEnum.GL_KEEP);
        IGLProperty stencilPassDepthPass = new GLEnumProperty(GLStateType.STENCIL_PASS_DEPTH_PASS,
                GLEnum.GL_KEEP);
        IGLProperty stencilBackFunc = new GLEnumProperty(GLStateType.STENCIL_BACK_FUNC,
                GLEnum.GL_ALWAYS);
        IGLProperty stencilBackValueMask = new GLIntegerProperty(
                GLStateType.STENCIL_BACK_VALUE_MASK, Integer.valueOf(0xffffffff), DisplayRadix.HEX);
        IGLProperty stencilBackRef = new GLIntegerProperty(GLStateType.STENCIL_BACK_REF, 0);
        IGLProperty stencilBackFail = new GLEnumProperty(GLStateType.STENCIL_BACK_FAIL,
                GLEnum.GL_KEEP);
        IGLProperty stencilBackPassDepthFail = new GLEnumProperty(
                GLStateType.STENCIL_BACK_PASS_DEPTH_FAIL, GLEnum.GL_KEEP);
        IGLProperty stencilBackPassDepthPass = new GLEnumProperty(
                GLStateType.STENCIL_BACK_PASS_DEPTH_PASS, GLEnum.GL_KEEP);
        IGLProperty stencil = new GLCompositeProperty(GLStateType.STENCIL,
                stencilTest, stencilFunc,
                stencilMask, stencilRef, stencilFail,
                stencilPassDepthFail, stencilPassDepthPass,
                stencilBackFunc, stencilBackValueMask,
                stencilBackRef, stencilBackFail,
                stencilBackPassDepthFail, stencilBackPassDepthPass);

        IGLProperty depthTest = new GLBooleanProperty(GLStateType.DEPTH_TEST, Boolean.FALSE);
        IGLProperty depthFunc = new GLEnumProperty(GLStateType.DEPTH_FUNC, GLEnum.GL_LESS);

        IGLProperty blendEnabled = new GLBooleanProperty(GLStateType.BLEND_ENABLED, Boolean.FALSE);
        // FIXME: BLEND_SRC_RGB should be set to GL_ONE, but GL_LINES is already 0x1.
        IGLProperty blendSrcRgb = new GLEnumProperty(GLStateType.BLEND_SRC_RGB, GLEnum.GL_LINES);
        IGLProperty blendSrcAlpha = new GLEnumProperty(GLStateType.BLEND_SRC_ALPHA,
                GLEnum.GL_LINES);
        IGLProperty blendDstRgb = new GLEnumProperty(GLStateType.BLEND_DST_RGB, GLEnum.GL_POINTS);
        IGLProperty blendDstAlpha = new GLEnumProperty(GLStateType.BLEND_DST_ALPHA,
                GLEnum.GL_POINTS);
        IGLProperty blendEquationRgb = new GLEnumProperty(GLStateType.BLEND_EQUATION_RGB,
                GLEnum.GL_FUNC_ADD);
        IGLProperty blendEquationAlpha = new GLEnumProperty(GLStateType.BLEND_EQUATION_ALPHA,
                GLEnum.GL_FUNC_ADD);
        IGLProperty blend = new GLCompositeProperty(GLStateType.BLEND,
                blendEnabled, blendSrcRgb, blendSrcAlpha, blendDstRgb, blendDstAlpha,
                blendEquationRgb, blendEquationAlpha);

        IGLProperty dither = new GLBooleanProperty(GLStateType.DITHER, Boolean.TRUE);

        return new GLCompositeProperty(GLStateType.PIXEL_OPERATIONS,
                scissorTest, scissorBox, stencil,
                depthTest, depthFunc, blend, dither);
    }

    private IGLProperty createPixelPackState() {
        IGLProperty packAlignment = new GLIntegerProperty(GLStateType.PACK_ALIGNMENT,
                Integer.valueOf(4));
        IGLProperty unpackAlignment = new GLIntegerProperty(GLStateType.UNPACK_ALIGNMENT,
                Integer.valueOf(4));
        IGLProperty pixelPack = new GLCompositeProperty(GLStateType.PIXEL_PACKING,
                packAlignment, unpackAlignment);
        return pixelPack;
    }

    private IGLProperty createFramebufferState() {
        IGLProperty binding = new GLIntegerProperty(GLStateType.FRAMEBUFFER_BINDING, 0);
        GLCompositeProperty framebufferState = new GLCompositeProperty(
                GLStateType.FRAMEBUFFER_STATE,
                binding);
        return framebufferState;
    }

    static IGLProperty createDefaultES2State() {
        GLCompositeProperty glState = new GLCompositeProperty(GLStateType.GL_STATE_ES2,
                sGLState.createVertexArrayData(),
                sGLState.createFramebufferState(),
                sGLState.createTransformationState(),
                sGLState.createRasterizationState(),
                sGLState.createPixelOperationsState(),
                sGLState.createPixelPackState());
        return glState;
    }

    static IGLProperty createDefaultES1State() {
        GLCompositeProperty glState = new GLCompositeProperty(GLStateType.GL_STATE_ES1,
                sGLState.createFramebufferState(),
                sGLState.createTransformationState(),
                sGLState.createRasterizationState(),
                sGLState.createPixelOperationsState(),
                sGLState.createPixelPackState());
        return glState;
    }

    public static IGLProperty createDefaultState() {
        return new GLListProperty(GLStateType.GL_STATE, null, 0);
    }
}
