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

package com.android.ide.eclipse.gltrace.views;

import com.android.ide.eclipse.gltrace.editors.GLFunctionTraceViewer;

import org.eclipse.ui.IWorkbenchPart;

/**
 * The {@link FrameBufferView} displays the contents of the frame buffer for the
 * currently displayed frame.
 */
public class FrameBufferView extends GLPageBookView {
    public static final String ID = "com.android.ide.eclipse.gltrace.views.FrameBufferView"; //$NON-NLS-1$

    public FrameBufferView() {
        super("Open a GL Trace file to view the framebuffer contents.");
    }

    @Override
    protected PageRec doCreatePage(IWorkbenchPart part) {
        if (!(part instanceof GLFunctionTraceViewer)) {
            return null;
        }

        GLFunctionTraceViewer viewer = (GLFunctionTraceViewer) part;
        FrameBufferViewPage page = viewer.getFrameBufferViewPage();
        initPage(page);
        page.createControl(getPageBook());

        return new PageRec(part, page);
    }

    @Override
    protected void doDestroyPage(IWorkbenchPart part, PageRec pageRecord) {
        FrameBufferViewPage page = (FrameBufferViewPage) pageRecord.page;
        page.dispose();
        pageRecord.dispose();
    }
}
