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

import com.android.ide.eclipse.gltrace.GLProtoBuf.GLMessage;
import com.android.ide.eclipse.gltrace.GLProtoBuf.GLMessage.Function;
import com.android.ide.eclipse.gltrace.model.GLCall;
import com.android.ide.eclipse.gltrace.model.GLFrame;
import com.android.ide.eclipse.gltrace.model.GLTrace;
import com.android.ide.eclipse.gltrace.state.GLStateTransform;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

public class TraceFileParserTask implements IRunnableWithProgress {
    private static final TraceFileReader sReader = new TraceFileReader();

    private final Display mDisplay;
    private final int mThumbHeight;
    private final int mThumbWidth;

    private String mTraceFilePath;
    private RandomAccessFile mFile;

    private List<GLCall> mGLCalls;
    private List<List<GLStateTransform>> mStateTransformsPerCall;
    private List<GLFrame> mGLFrames;

    private int mFrameCount;
    private int mCurrentFrameStartIndex, mCurrentFrameEndIndex;
    private GLTrace mTrace;

    /**
     * Construct a GL Trace file parser.
     * @param path path to trace file
     * @param thumbDisplay display to use to create thumbnail images
     * @param thumbWidth width of thumbnail images
     * @param thumbHeight height of thumbnail images
     */
    public TraceFileParserTask(String path, Display thumbDisplay, int thumbWidth,
            int thumbHeight) {
        try {
            mFile = new RandomAccessFile(path, "r"); //$NON-NLS-1$
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException(e);
        }

        mDisplay = thumbDisplay;
        mThumbWidth = thumbWidth;
        mThumbHeight = thumbHeight;

        mTraceFilePath = path;
        mGLCalls = new ArrayList<GLCall>();
        mStateTransformsPerCall = new ArrayList<List<GLStateTransform>>();
        mFrameCount = 0;
        mCurrentFrameStartIndex = 0;
        mCurrentFrameEndIndex = 0;
        mGLFrames = new ArrayList<GLFrame>();
    }

    private void addMessage(int index, long traceFileOffset, GLMessage msg) {
        Image previewImage = null;
        if (mDisplay != null) {
            previewImage = ProtoBufUtils.getScaledImage(mDisplay, msg, mThumbWidth, mThumbHeight);
        }

        GLCall c = new GLCall(index, traceFileOffset, msg, previewImage);

        mGLCalls.add(c);
        mStateTransformsPerCall.add(GLStateTransform.getTransformsFor(c));
        mCurrentFrameEndIndex++;

        if (c.getFunction() == Function.eglSwapBuffers) {
            mGLFrames.add(new GLFrame(mFrameCount,
                    mCurrentFrameStartIndex,
                    mCurrentFrameEndIndex));

            mFrameCount++;
            mCurrentFrameStartIndex = mCurrentFrameEndIndex;
        }
    }

    /**
     * Parse the entire file and create a {@link GLTrace} object that can be retrieved
     * using {@link #getTrace()}.
     */
    public void run(IProgressMonitor monitor) throws InvocationTargetException,
            InterruptedException {
        monitor.beginTask("Parsing OpenGL Trace File", IProgressMonitor.UNKNOWN);

        try {
            GLMessage msg = null;
            int msgCount = 0;
            long filePointer = mFile.getFilePointer();

            while ((msg = sReader.getMessageAtOffset(mFile, 0)) != null) {
                addMessage(msgCount, filePointer, msg);

                filePointer = mFile.getFilePointer();
                msgCount++;

                if (monitor.isCanceled()) {
                    throw new InterruptedException();
                }
            }
        } catch (Exception e) {
            throw new InvocationTargetException(e);
        } finally {
            try {
                mFile.close();
            } catch (IOException e) {
                // ignore exception while closing file
            }
            monitor.done();
        }

        File f = new File(mTraceFilePath);
        TraceFileInfo fileInfo = new TraceFileInfo(mTraceFilePath, f.length(), f.lastModified());
        mTrace = new GLTrace(fileInfo, mGLFrames, mGLCalls, mStateTransformsPerCall);
    }

    /**
     * Retrieve the trace object constructed from messages in the trace file.
     */
    public GLTrace getTrace() {
        return mTrace;
    }
}
