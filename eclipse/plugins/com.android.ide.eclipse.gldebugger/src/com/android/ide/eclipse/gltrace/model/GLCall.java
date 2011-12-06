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

import com.android.ide.eclipse.gltrace.GLProtoBuf;
import com.android.ide.eclipse.gltrace.GLProtoBuf.GLMessage;
import com.android.ide.eclipse.gltrace.GLProtoBuf.GLMessage.DataType;
import com.android.ide.eclipse.gltrace.GLProtoBuf.GLMessage.Function;

import org.eclipse.swt.graphics.Image;

import java.util.List;

/**
 * A GLCall is the in memory representation of a single {@link GLProtoBuf.GLMessage}.
 *
 * Some protocol buffer messages have a large amount of image data packed in them. Rather
 * than storing all of that in memory, the GLCall stores a thumbnail image, and an offset
 * into the trace file corresponding to original protocol buffer message. If full image data
 * is required, the protocol buffer message can be recreated by reading the trace at the
 * specified offset.
 */
public class GLCall {
    /** Index of this call in the trace. */
    private final int mIndex;

    /** Offset of the protobuf message corresponding to this call in the trace file. */
    private final long mTraceFileOffset;

    /** Corresponding protobuf message with its image data stripped. */
    private final GLMessage mMessage;

    /** Flag indicating whether the original protobuf message included FB data. */
    private final boolean mHasFb;

    /** Thumbnail image of the framebuffer if available. */
    private final Image mThumbnailImage;

    public GLCall(int index, long traceFileOffset, GLMessage msg, Image thumbnailImage) {
        mIndex = index;
        mTraceFileOffset = traceFileOffset;

        if (msg.hasFb()) {
            // strip off the FB contents
            msg = msg.toBuilder().clearFb().build();
            mHasFb = true;
        } else {
            mHasFb = false;
        }

        mMessage = msg;
        mThumbnailImage = thumbnailImage;
    }

    public int getIndex() {
        return mIndex;
    }

    public long getOffsetInTraceFile() {
        return mTraceFileOffset;
    }

    public Function getFunction() {
        return mMessage.getFunction();
    }

    public List<DataType> getArgsList() {
        return mMessage.getArgsList();
    }

    public DataType getArg(int index) {
        return mMessage.getArgs(index);
    }

    public boolean hasFb() {
        return mHasFb;
    }

    public Image getThumbnailImage() {
        return mThumbnailImage;
    }
}
