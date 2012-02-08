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
import com.android.ide.eclipse.gltrace.editors.GLCallGroups.GLCallNode;
import com.android.ide.eclipse.gltrace.model.GLCall;
import com.android.ide.eclipse.gltrace.model.GLTrace;
import com.android.ide.eclipse.gltrace.widgets.ImageCanvas;

import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.part.IPageSite;
import org.eclipse.ui.part.Page;

public class FrameBufferViewPage extends Page implements ISelectionListener {
    private ImageCanvas mImageCanvas;

    private final GLTrace mTrace;

    public FrameBufferViewPage(GLTrace trace) {
        mTrace = trace;
    }

    @Override
    public void createControl(Composite parent) {
        mImageCanvas = new ImageCanvas(parent);

        IToolBarManager toolbarManager = getSite().getActionBars().getToolBarManager();
        toolbarManager.add(new FitToCanvasAction(true, mImageCanvas));
    }

    @Override
    public Control getControl() {
        return mImageCanvas;
    }

    @Override
    public void init(IPageSite pageSite) {
        super.init(pageSite);
        pageSite.getPage().addSelectionListener(this);
    }

    @Override
    public void dispose() {
        getSite().getPage().removeSelectionListener(this);
        super.dispose();
    }

    @Override
    public void setFocus() {
    }

    @Override
    public void selectionChanged(IWorkbenchPart part, ISelection selection) {
        if (!(part instanceof GLFunctionTraceViewer)) {
            return;
        }

        if (((GLFunctionTraceViewer) part).getTrace() != mTrace) {
            return;
        }

        if (!(selection instanceof TreeSelection)) {
            return;
        }

        GLCall selectedCall = null;

        Object data = ((TreeSelection) selection).getFirstElement();
        if (data instanceof GLCallNode) {
            selectedCall = ((GLCallNode) data).getCall();
        }

        if (selectedCall == null) {
            return;
        }

        setImage(mTrace.getImage(selectedCall));
    }

    private void setImage(final Image image) {
        Display.getDefault().asyncExec(new Runnable() {
            @Override
            public void run() {
                mImageCanvas.setImage(image);
            }
        });
    };

    public void setSelectedFrame(int frame) {
        int lastCallIndex = mTrace.getFrame(frame).getEndIndex() - 1;
        if (lastCallIndex >= 0 && lastCallIndex < mTrace.getGLCalls().size()) {
            GLCall call = mTrace.getGLCalls().get(lastCallIndex);
            setImage(mTrace.getImage(call));
        }
    }
}
