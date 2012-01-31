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

package com.android.ide.eclipse.gltrace.state.transforms;

import com.android.ide.eclipse.gldebugger.GLEnum;
import com.android.ide.eclipse.gltrace.FileUtils;
import com.android.ide.eclipse.gltrace.GLProtoBuf.GLMessage;
import com.android.ide.eclipse.gltrace.state.GLState;
import com.android.ide.eclipse.gltrace.state.GLStateType;
import com.android.ide.eclipse.gltrace.state.IGLProperty;
import com.google.common.io.Files;
import com.google.protobuf.ByteString;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class StateTransformFactory {
    private static final String TEXTURE_DATA_FILE_PREFIX = "tex";   //$NON-NLS-1$
    private static final String TEXTURE_DATA_FILE_SUFFIX = ".dat";  //$NON-NLS-1$

    /** Construct a list of transformations to be applied for the provided OpenGL call. */
    public static List<IStateTransform> getTransformsFor(GLMessage msg) {
        switch (msg.getFunction()) {
            case eglCreateContext:
                return transformsForEglCreateContext(msg);
            case glVertexAttribPointer:
                return transformsForGlVertexAttribPointer(msg);
            case glBindFramebuffer:
                return transformsForGlBindFramebuffer(msg);
            case glViewport:
                return transformsForGlViewport(msg);
            case glDepthRangef:
                return transformsForGlDepthRangef(msg);
            case glLineWidth:
                return transformsForGlLineWidth(msg);
            case glCullFace:
                return transformsForGlCullFace(msg);
            case glFrontFace:
                return transformsForGlFrontFace(msg);
            case glPolygonOffset:
                return transformsForGlPolygonOffset(msg);
            case glScissor:
                return transformsForGlScissor(msg);
            case glStencilFunc:
                return transformsForGlStencilFunc(msg);
            case glStencilFuncSeparate:
                return transformsForGlStencilFuncSeparate(msg);
            case glStencilOp:
                return transformsForGlStencilOp(msg);
            case glStencilOpSeparate:
                return transformsForGlStencilOpSeparate(msg);
            case glDepthFunc:
                return transformsForGlDepthFunc(msg);
            case glBlendEquation:
                return transformsForGlBlendEquation(msg);
            case glBlendEquationSeparate:
                return transformsForGlBlendEquationSeparate(msg);
            case glBlendFunc:
                return transformsForGlBlendFunc(msg);
            case glBlendFuncSeparate:
                return transformsForGlBlendFuncSeparate(msg);
            case glPixelStorei:
                return transformsForGlPixelStorei(msg);

            // Texture State Transformations
            case glGenTextures:
                return transformsForGlGenTextures(msg);
            case glDeleteTextures:
                return transformsForGlDeleteTextures(msg);
            case glActiveTexture:
                return transformsForGlActiveTexture(msg);
            case glBindTexture:
                return transformsForGlBindTexture(msg);
            case glTexImage2D:
                return transformsForGlTexImage2D(msg);
            case glTexSubImage2D:
                return transformsForGlTexSubImage2D(msg);
            case glTexParameteri:
                return transformsForGlTexParameter(msg);

            // Program State Transformations
            case glCreateProgram:
                return transformsForGlCreateProgram(msg);
            case glUseProgram:
                return transformsForGlUseProgram(msg);
            case glAttachShader:
                return transformsForGlAttachShader(msg);
            case glDetachShader:
                return transformsForGlDetachShader(msg);

            // Shader State Transformations
            case glCreateShader:
                return transformsForGlCreateShader(msg);
            case glDeleteShader:
                return transformsForGlDeleteShader(msg);
            case glShaderSource:
                return transformsForGlShaderSource(msg);
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

    private static List<IStateTransform> transformsForGlLineWidth(GLMessage msg) {
        // void glLineWidth(GLfloat width);
        float width = msg.getArgs(0).getFloatValue(0);
        IStateTransform transform = new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(msg.getContextId(),
                        GLStateType.RASTERIZATION_STATE,
                        GLStateType.LINE_WIDTH),
                width);
        return Collections.singletonList(transform);
    }

    private static List<IStateTransform> transformsForGlCullFace(GLMessage msg) {
        // void glCullFace(GLenum mode);
        int mode = msg.getArgs(0).getIntValue(0);
        IStateTransform transform = new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(msg.getContextId(),
                        GLStateType.RASTERIZATION_STATE,
                        GLStateType.CULL_FACE_MODE),
                GLEnum.valueOf(mode));
        return Collections.singletonList(transform);
    }

    private static List<IStateTransform> transformsForGlFrontFace(GLMessage msg) {
        // void glFrontFace(GLenum mode);
        int mode = msg.getArgs(0).getIntValue(0);
        IStateTransform transform = new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(msg.getContextId(),
                        GLStateType.RASTERIZATION_STATE,
                        GLStateType.FRONT_FACE),
                GLEnum.valueOf(mode));
        return Collections.singletonList(transform);
    }

    private static List<IStateTransform> transformsForGlPolygonOffset(GLMessage msg) {
        // void glPolygonOffset(GLfloat factor, GLfloat units)
        float factor = msg.getArgs(0).getFloatValue(0);
        float units = msg.getArgs(1).getFloatValue(0);

        List<IStateTransform> transforms = new ArrayList<IStateTransform>();
        transforms.add(new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(msg.getContextId(),
                        GLStateType.RASTERIZATION_STATE,
                        GLStateType.POLYGON_OFFSET_FACTOR),
                Float.valueOf(factor)));
        transforms.add(new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(msg.getContextId(),
                        GLStateType.RASTERIZATION_STATE,
                        GLStateType.POLYGON_OFFSET_UNITS),
                Float.valueOf(units)));
        return transforms;
    }

    private static List<IStateTransform> transformsForGlScissor(GLMessage msg) {
        // void glScissor(GLint x, GLint y, GLsizei width, GLsizei height);
        int x = msg.getArgs(0).getIntValue(0);
        int y = msg.getArgs(1).getIntValue(0);
        int w = msg.getArgs(2).getIntValue(0);
        int h = msg.getArgs(3).getIntValue(0);

        List<IStateTransform> transforms = new ArrayList<IStateTransform>();
        transforms.add(new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(msg.getContextId(),
                        GLStateType.PIXEL_OPERATIONS,
                        GLStateType.SCISSOR_BOX,
                        GLStateType.SCISSOR_BOX_X),
                Integer.valueOf(x)));
        transforms.add(new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(msg.getContextId(),
                        GLStateType.PIXEL_OPERATIONS,
                        GLStateType.SCISSOR_BOX,
                        GLStateType.SCISSOR_BOX_Y),
                Integer.valueOf(y)));
        transforms.add(new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(msg.getContextId(),
                        GLStateType.PIXEL_OPERATIONS,
                        GLStateType.SCISSOR_BOX,
                        GLStateType.SCISSOR_BOX_WIDTH),
                Integer.valueOf(w)));
        transforms.add(new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(msg.getContextId(),
                        GLStateType.PIXEL_OPERATIONS,
                        GLStateType.SCISSOR_BOX,
                        GLStateType.SCISSOR_BOX_HEIGHT),
                Integer.valueOf(h)));
        return transforms;
    }

    private static List<IStateTransform> transformsForGlStencilFuncFront(int contextId,
            GLEnum func, int ref, int mask) {
        List<IStateTransform> transforms = new ArrayList<IStateTransform>();
        transforms.add(new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(contextId,
                        GLStateType.PIXEL_OPERATIONS,
                        GLStateType.STENCIL,
                        GLStateType.STENCIL_FUNC),
                func));
        transforms.add(new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(contextId,
                        GLStateType.PIXEL_OPERATIONS,
                        GLStateType.STENCIL,
                        GLStateType.STENCIL_REF),
                Integer.valueOf(ref)));
        transforms.add(new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(contextId,
                        GLStateType.PIXEL_OPERATIONS,
                        GLStateType.STENCIL,
                        GLStateType.STENCIL_VALUE_MASK),
                Integer.valueOf(mask)));
        return transforms;
    }

    private static List<IStateTransform> transformsForGlStencilFuncBack(int contextId,
            GLEnum func, int ref, int mask) {
        List<IStateTransform> transforms = new ArrayList<IStateTransform>();
        transforms.add(new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(contextId,
                        GLStateType.PIXEL_OPERATIONS,
                        GLStateType.STENCIL,
                        GLStateType.STENCIL_BACK_FUNC),
                func));
        transforms.add(new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(contextId,
                        GLStateType.PIXEL_OPERATIONS,
                        GLStateType.STENCIL,
                        GLStateType.STENCIL_BACK_REF),
                Integer.valueOf(ref)));
        transforms.add(new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(contextId,
                        GLStateType.PIXEL_OPERATIONS,
                        GLStateType.STENCIL,
                        GLStateType.STENCIL_BACK_VALUE_MASK),
                Integer.valueOf(mask)));
        return transforms;
    }

    private static List<IStateTransform> transformsForGlStencilFunc(GLMessage msg) {
        // void glStencilFunc(GLenum func, GLint ref, GLuint mask);
        GLEnum func = GLEnum.valueOf(msg.getArgs(0).getIntValue(0));
        int ref = msg.getArgs(1).getIntValue(0);
        int mask = msg.getArgs(2).getIntValue(0);

        List<IStateTransform> transforms = new ArrayList<IStateTransform>();
        transforms.addAll(transformsForGlStencilFuncFront(msg.getContextId(), func, ref, mask));
        transforms.addAll(transformsForGlStencilFuncBack(msg.getContextId(), func, ref, mask));
        return transforms;
    }

    private static List<IStateTransform> transformsForGlStencilFuncSeparate(GLMessage msg) {
        // void glStencilFuncSeparate(GLenum face, GLenum func, GLint ref, GLuint mask);
        GLEnum face = GLEnum.valueOf(msg.getArgs(0).getIntValue(0));
        GLEnum func = GLEnum.valueOf(msg.getArgs(1).getIntValue(0));
        int ref = msg.getArgs(2).getIntValue(0);
        int mask = msg.getArgs(3).getIntValue(0);

        List<IStateTransform> transforms = new ArrayList<IStateTransform>();
        if (face == GLEnum.GL_FRONT || face == GLEnum.GL_FRONT_AND_BACK) {
            transforms.addAll(
                    transformsForGlStencilFuncFront(msg.getContextId(), func, ref, mask));
        }
        if (face == GLEnum.GL_BACK || face == GLEnum.GL_FRONT_AND_BACK) {
            transforms.addAll(
                    transformsForGlStencilFuncBack(msg.getContextId(), func, ref, mask));
        }

        return transforms;
    }

    private static List<IStateTransform> transformsForGlStencilOpFront(int contextId,
            GLEnum sfail, GLEnum dpfail, GLEnum dppass) {
        List<IStateTransform> transforms = new ArrayList<IStateTransform>();
        transforms.add(new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(contextId,
                        GLStateType.PIXEL_OPERATIONS,
                        GLStateType.STENCIL,
                        GLStateType.STENCIL_FAIL),
                sfail));
        transforms.add(new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(contextId,
                        GLStateType.PIXEL_OPERATIONS,
                        GLStateType.STENCIL,
                        GLStateType.STENCIL_PASS_DEPTH_FAIL),
                dpfail));
        transforms.add(new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(contextId,
                        GLStateType.PIXEL_OPERATIONS,
                        GLStateType.STENCIL,
                        GLStateType.STENCIL_PASS_DEPTH_PASS),
                dppass));
        return transforms;
    }

    private static List<IStateTransform> transformsForGlStencilOpBack(int contextId,
            GLEnum sfail, GLEnum dpfail, GLEnum dppass) {
        List<IStateTransform> transforms = new ArrayList<IStateTransform>();
        transforms.add(new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(contextId,
                        GLStateType.PIXEL_OPERATIONS,
                        GLStateType.STENCIL,
                        GLStateType.STENCIL_BACK_FAIL),
                sfail));
        transforms.add(new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(contextId,
                        GLStateType.PIXEL_OPERATIONS,
                        GLStateType.STENCIL,
                        GLStateType.STENCIL_BACK_PASS_DEPTH_FAIL),
                dpfail));
        transforms.add(new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(contextId,
                        GLStateType.PIXEL_OPERATIONS,
                        GLStateType.STENCIL,
                        GLStateType.STENCIL_BACK_PASS_DEPTH_PASS),
                dppass));
        return transforms;
    }

    private static List<IStateTransform> transformsForGlStencilOp(GLMessage msg) {
        // void glStencilOp(GLenum sfail, GLenum dpfail, GLenum dppass);
        GLEnum sfail = GLEnum.valueOf(msg.getArgs(0).getIntValue(0));
        GLEnum dpfail = GLEnum.valueOf(msg.getArgs(1).getIntValue(0));
        GLEnum dppass = GLEnum.valueOf(msg.getArgs(2).getIntValue(0));
        List<IStateTransform> transforms = new ArrayList<IStateTransform>();
        transforms.addAll(
                transformsForGlStencilOpFront(msg.getContextId(), sfail, dpfail, dppass));
        transforms.addAll(
                transformsForGlStencilOpBack(msg.getContextId(), sfail, dpfail, dppass));
        return transforms;
    }

    private static List<IStateTransform> transformsForGlStencilOpSeparate(GLMessage msg) {
        // void glStencilOp(GLenum face, GLenum sfail, GLenum dpfail, GLenum dppass);
        GLEnum face = GLEnum.valueOf(msg.getArgs(0).getIntValue(0));
        GLEnum sfail = GLEnum.valueOf(msg.getArgs(1).getIntValue(0));
        GLEnum dpfail = GLEnum.valueOf(msg.getArgs(2).getIntValue(0));
        GLEnum dppass = GLEnum.valueOf(msg.getArgs(3).getIntValue(0));
        List<IStateTransform> transforms = new ArrayList<IStateTransform>();

        if (face == GLEnum.GL_FRONT || face == GLEnum.GL_FRONT_AND_BACK) {
            transforms.addAll(
                    transformsForGlStencilOpFront(msg.getContextId(), sfail, dpfail, dppass));
        }

        if (face == GLEnum.GL_BACK || face == GLEnum.GL_FRONT_AND_BACK) {
            transforms.addAll(
                    transformsForGlStencilOpBack(msg.getContextId(), sfail, dpfail, dppass));
        }

        return transforms;
    }

    private static List<IStateTransform> transformsForGlDepthFunc(GLMessage msg) {
        // void glDepthFunc(GLenum func);
        GLEnum func = GLEnum.valueOf(msg.getArgs(0).getIntValue(0));

        IStateTransform transform = new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(msg.getContextId(),
                        GLStateType.PIXEL_OPERATIONS,
                        GLStateType.DEPTH_FUNC),
                func);
        return Collections.singletonList(transform);
    }

    private static IStateTransform transformForGlEquationRGB(int contextId, GLEnum mode) {
        return new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(contextId,
                        GLStateType.PIXEL_OPERATIONS,
                        GLStateType.BLEND,
                        GLStateType.BLEND_EQUATION_RGB),
                mode);
    }

    private static IStateTransform transformForGlEquationAlpha(int contextId, GLEnum mode) {
        return new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(contextId,
                        GLStateType.PIXEL_OPERATIONS,
                        GLStateType.BLEND,
                        GLStateType.BLEND_EQUATION_ALPHA),
                mode);
    }

    private static List<IStateTransform> transformsForGlBlendEquationSeparate(GLMessage msg) {
        // void glBlendEquationSeparate(GLenum modeRGB, GLenum modeAlpha);
        GLEnum rgb = GLEnum.valueOf(msg.getArgs(0).getIntValue(0));
        GLEnum alpha = GLEnum.valueOf(msg.getArgs(1).getIntValue(0));

        List<IStateTransform> transforms = new ArrayList<IStateTransform>();
        transforms.add(transformForGlEquationRGB(msg.getContextId(), rgb));
        transforms.add(transformForGlEquationAlpha(msg.getContextId(), alpha));
        return transforms;
    }

    private static List<IStateTransform> transformsForGlBlendEquation(GLMessage msg) {
        // void glBlendEquation(GLenum mode);
        GLEnum mode = GLEnum.valueOf(msg.getArgs(0).getIntValue(0));

        List<IStateTransform> transforms = new ArrayList<IStateTransform>();
        transforms.add(transformForGlEquationRGB(msg.getContextId(), mode));
        transforms.add(transformForGlEquationAlpha(msg.getContextId(), mode));
        return transforms;
    }

    private static List<IStateTransform> transformsForGlBlendFuncSrcDst(boolean src,
            int contextId, GLEnum rgb, GLEnum alpha) {
        List<IStateTransform> transforms = new ArrayList<IStateTransform>();

        GLStateType rgbAccessor = GLStateType.BLEND_DST_RGB;
        GLStateType alphaAccessor = GLStateType.BLEND_DST_ALPHA;
        if (src) {
            rgbAccessor = GLStateType.BLEND_SRC_RGB;
            alphaAccessor = GLStateType.BLEND_SRC_ALPHA;
        }

        transforms.add(new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(contextId,
                        GLStateType.PIXEL_OPERATIONS,
                        GLStateType.BLEND,
                        rgbAccessor),
                rgb));
        transforms.add(new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(contextId,
                        GLStateType.PIXEL_OPERATIONS,
                        GLStateType.BLEND,
                        alphaAccessor),
                alpha));
        return transforms;
    }

    private static List<IStateTransform> transformsForGlBlendFuncSeparate(GLMessage msg) {
        // void glBlendFuncSeparate(GLenum srcRGB, GLenum dstRGB, GLenum srcAlpha, GLenum dstAlpha)
        GLEnum srcRgb = GLEnum.valueOf(msg.getArgs(0).getIntValue(0));
        GLEnum dstRgb = GLEnum.valueOf(msg.getArgs(1).getIntValue(0));
        GLEnum srcAlpha = GLEnum.valueOf(msg.getArgs(2).getIntValue(0));
        GLEnum dstAlpha = GLEnum.valueOf(msg.getArgs(3).getIntValue(0));

        List<IStateTransform> transforms = new ArrayList<IStateTransform>();
        transforms.addAll(transformsForGlBlendFuncSrcDst(true,
                msg.getContextId(), srcRgb, srcAlpha));
        transforms.addAll(transformsForGlBlendFuncSrcDst(false,
                msg.getContextId(), dstRgb, dstAlpha));
        return transforms;
    }

    private static List<IStateTransform> transformsForGlBlendFunc(GLMessage msg) {
        // void glBlendFunc(GLenum sfactor, GLenum dfactor);
        GLEnum sfactor = GLEnum.valueOf(msg.getArgs(0).getIntValue(0));
        GLEnum dfactor = GLEnum.valueOf(msg.getArgs(1).getIntValue(0));

        List<IStateTransform> transforms = new ArrayList<IStateTransform>();
        transforms.addAll(transformsForGlBlendFuncSrcDst(true,
                msg.getContextId(), sfactor, sfactor));
        transforms.addAll(transformsForGlBlendFuncSrcDst(false,
                msg.getContextId(), dfactor, dfactor));
        return transforms;
    }

    private static List<IStateTransform> transformsForGlPixelStorei(GLMessage msg) {
        // void glPixelStorei(GLenum pname, GLint param);
        GLEnum pname = GLEnum.valueOf(msg.getArgs(0).getIntValue(0));
        Integer param = Integer.valueOf(msg.getArgs(1).getIntValue(0));

        IStateTransform transform;
        if (pname == GLEnum.GL_PACK_ALIGNMENT) {
            transform = new PropertyChangeTransform(
                    GLPropertyAccessor.makeAccessor(msg.getContextId(),
                            GLStateType.PIXEL_PACKING,
                            GLStateType.PACK_ALIGNMENT),
                    param);
        } else {
            transform = new PropertyChangeTransform(
                    GLPropertyAccessor.makeAccessor(msg.getContextId(),
                            GLStateType.PIXEL_PACKING,
                            GLStateType.UNPACK_ALIGNMENT),
                    param);
        }

        return Collections.singletonList(transform);
    }

    private static List<IStateTransform> transformsForGlViewport(GLMessage msg) {
        // void glViewport( GLint x, GLint y, GLsizei width, GLsizei height);
        int x = msg.getArgs(0).getIntValue(0);
        int y = msg.getArgs(1).getIntValue(0);
        int w = msg.getArgs(2).getIntValue(0);
        int h = msg.getArgs(3).getIntValue(0);

        List<IStateTransform> transforms = new ArrayList<IStateTransform>();
        transforms.add(new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(msg.getContextId(),
                                                GLStateType.TRANSFORMATION_STATE,
                                                GLStateType.VIEWPORT,
                                                GLStateType.VIEWPORT_X),
                Integer.valueOf(x)));
        transforms.add(new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(msg.getContextId(),
                                                GLStateType.TRANSFORMATION_STATE,
                                                GLStateType.VIEWPORT,
                                                GLStateType.VIEWPORT_Y),
                Integer.valueOf(y)));
        transforms.add(new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(msg.getContextId(),
                                                GLStateType.TRANSFORMATION_STATE,
                                                GLStateType.VIEWPORT,
                                                GLStateType.VIEWPORT_WIDTH),
                Integer.valueOf(w)));
        transforms.add(new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(msg.getContextId(),
                                                GLStateType.TRANSFORMATION_STATE,
                                                GLStateType.VIEWPORT,
                                                GLStateType.VIEWPORT_HEIGHT),
                Integer.valueOf(h)));
        return transforms;
    }

    private static List<IStateTransform> transformsForGlDepthRangef(GLMessage msg) {
        // void glDepthRangef(GLclampf nearVal, GLclampf farVal);
        float near = msg.getArgs(0).getFloatValue(0);
        float far = msg.getArgs(1).getFloatValue(0);

        List<IStateTransform> transforms = new ArrayList<IStateTransform>();
        transforms.add(new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(msg.getContextId(),
                                                GLStateType.TRANSFORMATION_STATE,
                                                GLStateType.DEPTH_RANGE,
                                                GLStateType.DEPTH_RANGE_NEAR),
                Float.valueOf(near)));
        transforms.add(new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(msg.getContextId(),
                                                GLStateType.TRANSFORMATION_STATE,
                                                GLStateType.DEPTH_RANGE,
                                                GLStateType.DEPTH_RANGE_FAR),
                Float.valueOf(far)));
        return transforms;
    }

    private static List<IStateTransform> transformsForGlGenTextures(GLMessage msg) {
        // void glGenTextures(GLsizei n, GLuint *textures);
        int n = msg.getArgs(0).getIntValue(0);

        List<IStateTransform> transforms = new ArrayList<IStateTransform>();
        for (int i = 0; i < n; i++) {
            int texture = msg.getArgs(1).getIntValue(i);
            transforms.add(new SparseArrayElementAddTransform(
                    GLPropertyAccessor.makeAccessor(msg.getContextId(),
                                                    GLStateType.TEXTURE_STATE,
                                                    GLStateType.TEXTURES),
                    texture));
        }

        return transforms;
    }

    /**
     * Obtain a list of transforms that will reset any existing texture units
     * that are bound to provided texture.
     * @param contextId context to operate on
     * @param texture texture that should be unbound
     */
    private static List<IStateTransform> transformsToResetBoundTextureUnits(int contextId,
            int texture) {
        List<IStateTransform> transforms = new ArrayList<IStateTransform>(
                GLState.TEXTURE_UNIT_COUNT);

        for (int i = 0; i < GLState.TEXTURE_UNIT_COUNT; i++) {
            transforms.add(new PropertyChangeTransform(
                    GLPropertyAccessor.makeAccessor(contextId,
                                                    GLStateType.TEXTURE_STATE,
                                                    GLStateType.TEXTURE_UNITS,
                                                    Integer.valueOf(i),
                                                    GLStateType.TEXTURE_BINDING_2D),
                    Integer.valueOf(0), /* reset binding to texture 0 */
                    Predicates.matchesInteger(texture) /* only if currently bound to @texture */ ));
        }
        return transforms;
    }

    private static List<IStateTransform> transformsForGlDeleteTextures(GLMessage msg) {
        // void glDeleteTextures(GLsizei n, const GLuint * textures);
        int n = msg.getArgs(0).getIntValue(0);

        List<IStateTransform> transforms = new ArrayList<IStateTransform>(n);
        for (int i = 0; i < n; i++) {
            int texture = msg.getArgs(1).getIntValue(i);
            transforms.add(new SparseArrayElementRemoveTransform(
                    GLPropertyAccessor.makeAccessor(msg.getContextId(),
                                                    GLStateType.TEXTURE_STATE,
                                                    GLStateType.TEXTURES),
                    texture));
            transforms.addAll(transformsToResetBoundTextureUnits(msg.getContextId(), texture));
        }

        return transforms;
    }

    private static List<IStateTransform> transformsForGlActiveTexture(GLMessage msg) {
        // void glActiveTexture(GLenum texture);
        GLEnum texture = GLEnum.valueOf(msg.getArgs(0).getIntValue(0));
        Integer textureIndex = Integer.valueOf(texture.value - GLEnum.GL_TEXTURE0.value);
        IStateTransform transform = new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(msg.getContextId(),
                                                GLStateType.TEXTURE_STATE,
                                                GLStateType.ACTIVE_TEXTURE_UNIT),
                textureIndex);
        return Collections.singletonList(transform);
    }

    private static GLStateType getTextureUnitTargetName(GLEnum target) {
        if (target == GLEnum.GL_TEXTURE_BINDING_CUBE_MAP) {
            return GLStateType.TEXTURE_BINDING_CUBE_MAP;
        } else {
            return GLStateType.TEXTURE_BINDING_2D;
        }
    }

    private static GLStateType getTextureTargetName(GLEnum pname) {
        switch (pname) {
            case GL_TEXTURE_MIN_FILTER:
                return GLStateType.TEXTURE_MIN_FILTER;
            case GL_TEXTURE_MAG_FILTER:
                return GLStateType.TEXTURE_MAG_FILTER;
            case GL_TEXTURE_WRAP_S:
                return GLStateType.TEXTURE_WRAP_S;
            case GL_TEXTURE_WRAP_T:
                return GLStateType.TEXTURE_WRAP_T;
        }

        assert false : "glTexParameter's pname argument does not support provided value.";
        return GLStateType.TEXTURE_MIN_FILTER;
    }

    private static List<IStateTransform> transformsForGlBindTexture(GLMessage msg) {
        // void glBindTexture(GLenum target, GLuint texture);
        GLEnum target = GLEnum.valueOf(msg.getArgs(0).getIntValue(0));
        Integer texture = Integer.valueOf(msg.getArgs(1).getIntValue(0));

        IStateTransform transform = new PropertyChangeTransform(
                new TextureUnitPropertyAccessor(msg.getContextId(),
                                                getTextureUnitTargetName(target)),
                texture);
        return Collections.singletonList(transform);
    }

    /**
     * Utility function used by both {@link #transformsForGlTexImage2D(GLMessage) and
     * {@link #transformsForGlTexSubImage2D(GLMessage)}.
     */
    private static List<IStateTransform> transformsForGlTexImage(GLMessage msg, int widthArgIndex,
            int heightArgIndex, int xOffsetIndex, int yOffsetIndex) {
        GLEnum target = GLEnum.valueOf(msg.getArgs(0).getIntValue(0));
        Integer width = Integer.valueOf(msg.getArgs(widthArgIndex).getIntValue(0));
        Integer height = Integer.valueOf(msg.getArgs(heightArgIndex).getIntValue(0));
        GLEnum format = GLEnum.valueOf(msg.getArgs(6).getIntValue(0));
        GLEnum type = GLEnum.valueOf(msg.getArgs(7).getIntValue(0));

        List<IStateTransform> transforms = new ArrayList<IStateTransform>();
        transforms.add(new PropertyChangeTransform(
                new TexturePropertyAccessor(msg.getContextId(),
                                            getTextureUnitTargetName(target),
                                            GLStateType.TEXTURE_WIDTH),
                width));
        transforms.add(new PropertyChangeTransform(
                new TexturePropertyAccessor(msg.getContextId(),
                                            getTextureUnitTargetName(target),
                                            GLStateType.TEXTURE_HEIGHT),
                height));
        transforms.add(new PropertyChangeTransform(
                new TexturePropertyAccessor(msg.getContextId(),
                                            getTextureUnitTargetName(target),
                                            GLStateType.TEXTURE_FORMAT),
                format));
        transforms.add(new PropertyChangeTransform(
                new TexturePropertyAccessor(msg.getContextId(),
                                            getTextureUnitTargetName(target),
                                            GLStateType.TEXTURE_IMAGE_TYPE),
                type));

        // if texture data is available, extract and store it in the cache folder
        File f = null;
        if (msg.getArgs(8).getIsArray()) {
            ByteString data = msg.getArgs(8).getRawBytes(0);
            f = FileUtils.createTempFile(TEXTURE_DATA_FILE_PREFIX, TEXTURE_DATA_FILE_SUFFIX);
            try {
                Files.write(data.toByteArray(), f);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        int xOffset = 0;
        int yOffset = 0;

        if (xOffsetIndex >= 0) {
            xOffset = msg.getArgs(xOffsetIndex).getIntValue(0);
        }

        if (yOffsetIndex >= 0) {
            yOffset = msg.getArgs(yOffsetIndex).getIntValue(0);
        }

        transforms.add(new TexImageTransform(
                new TexturePropertyAccessor(msg.getContextId(),
                        getTextureUnitTargetName(target),
                        GLStateType.TEXTURE_IMAGE),
                f, format, xOffset, yOffset, width, height));

        return transforms;
    }

    private static List<IStateTransform> transformsForGlTexImage2D(GLMessage msg) {
        // void glTexImage2D(GLenum target, GLint level, GLint internalformat, GLsizei width,
        //          GLsizei height, GLint border, GLenum format, GLenum type, const GLvoid *data);
        return transformsForGlTexImage(msg, 3, 4, -1, -1);
    }

    private static List<IStateTransform> transformsForGlTexSubImage2D(GLMessage msg) {
        // void glTexSubImage2D(GLenum target, GLint level, GLint xoffset, GLint yoffset,
        //          GLsizei width, GLsizei height, GLenum format, GLenum type, const GLvoid *data);
        return transformsForGlTexImage(msg, 4, 5, 2, 3);
    }

    private static List<IStateTransform> transformsForGlTexParameter(GLMessage msg) {
        // void glTexParameteri(GLenum target, GLenum pname, GLint param);
        GLEnum target = GLEnum.valueOf(msg.getArgs(0).getIntValue(0));
        GLEnum pname = GLEnum.valueOf(msg.getArgs(1).getIntValue(0));
        GLEnum pvalue = GLEnum.valueOf(msg.getArgs(2).getIntValue(0));

        IStateTransform transform = new PropertyChangeTransform(
                new TexturePropertyAccessor(msg.getContextId(),
                                            getTextureUnitTargetName(target),
                                            getTextureTargetName(pname)),
                pvalue);
        return Collections.singletonList(transform);
    }

    private static List<IStateTransform> transformsForGlCreateProgram(GLMessage msg) {
        // GLuint glCreateProgram(void);
        int program = msg.getReturnValue().getIntValue(0);

        IStateTransform transform = new SparseArrayElementAddTransform(
                GLPropertyAccessor.makeAccessor(msg.getContextId(),
                                                GLStateType.PROGRAM_STATE,
                                                GLStateType.PROGRAMS),
                program);
        return Collections.singletonList(transform);
    }

    private static List<IStateTransform> transformsForGlUseProgram(GLMessage msg) {
        // void glUseProgram(GLuint program);
        Integer program = Integer.valueOf(msg.getArgs(0).getIntValue(0));

        IStateTransform transform = new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(msg.getContextId(),
                                                GLStateType.PROGRAM_STATE,
                                                GLStateType.CURRENT_PROGRAM),
                program);
        return Collections.singletonList(transform);
    }

    private static List<IStateTransform> transformsForGlAttachShader(GLMessage msg) {
        // void glAttachShader(GLuint program, GLuint shader);
        int program = msg.getArgs(0).getIntValue(0);
        int shader = msg.getArgs(1).getIntValue(0);

        IStateTransform transform = new SparseArrayElementAddTransform(
                GLPropertyAccessor.makeAccessor(msg.getContextId(),
                                                GLStateType.PROGRAM_STATE,
                                                GLStateType.PROGRAMS,
                                                Integer.valueOf(program),
                                                GLStateType.ATTACHED_SHADERS),
                Integer.valueOf(shader));
        return Collections.singletonList(transform);
    }

    private static List<IStateTransform> transformsForGlDetachShader(GLMessage msg) {
        // void glDetachShader(GLuint program, GLuint shader);
        int program = msg.getArgs(0).getIntValue(0);
        int shader = msg.getArgs(1).getIntValue(0);

        IStateTransform transform = new SparseArrayElementRemoveTransform(
                GLPropertyAccessor.makeAccessor(msg.getContextId(),
                                                GLStateType.PROGRAM_STATE,
                                                GLStateType.PROGRAMS,
                                                Integer.valueOf(program),
                                                GLStateType.ATTACHED_SHADERS),
                Integer.valueOf(shader));
        return Collections.singletonList(transform);
    }

    private static List<IStateTransform> transformsForGlCreateShader(GLMessage msg) {
        // GLuint glCreateShader(GLenum shaderType);
        GLEnum shaderType = GLEnum.valueOf(msg.getArgs(0).getIntValue(0));
        int shader = msg.getReturnValue().getIntValue(0);

        IStateTransform addShader = new SparseArrayElementAddTransform(
                GLPropertyAccessor.makeAccessor(msg.getContextId(),
                                                GLStateType.SHADERS),
                shader);
        IStateTransform setShaderType = new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(msg.getContextId(),
                                                GLStateType.SHADERS,
                                                Integer.valueOf(shader),
                                                GLStateType.SHADER_TYPE),
                shaderType);
        return Arrays.asList(addShader, setShaderType);
    }

    private static List<IStateTransform> transformsForGlDeleteShader(GLMessage msg) {
        // void glDeleteShader(GLuint shader);
        int shader = msg.getArgs(0).getIntValue(0);

        IStateTransform transform = new SparseArrayElementRemoveTransform(
                GLPropertyAccessor.makeAccessor(msg.getContextId(),
                        GLStateType.SHADERS),
                shader);
        return Collections.singletonList(transform);
    }

    private static List<IStateTransform> transformsForGlShaderSource(GLMessage msg) {
        // void glShaderSource(GLuint shader, GLsizei count, const GLchar **string,
        //                                                          const GLint *length);
        // This message is patched up on the device to return a single string as opposed to a
        // list of strings
        int shader = msg.getArgs(0).getIntValue(0);
        String src = msg.getArgs(2).getCharValue(0).toStringUtf8();

        IStateTransform transform = new PropertyChangeTransform(
                GLPropertyAccessor.makeAccessor(msg.getContextId(),
                                                GLStateType.SHADERS,
                                                Integer.valueOf(shader),
                                                GLStateType.SHADER_SOURCE),
                src);
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
