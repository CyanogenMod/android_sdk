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

package com.android.ide.eclipse.gltrace.views;

import com.android.ide.eclipse.gltrace.editors.GLFunctionTraceViewer;
import com.android.ide.eclipse.gltrace.model.GLCall;
import com.android.ide.eclipse.gltrace.model.GLTrace;

import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.part.ViewPart;

import java.util.List;

public class GLFramebufferView extends ViewPart implements ISelectionListener {
    public static final String ID = "com.android.ide.eclipse.gltrace.GLFrameBuffer";
    private Canvas mCanvas;
    private Image mImage;

    public GLFramebufferView() {
    }

    @Override
    public void createPartControl(Composite parent) {
        mCanvas = new Canvas(parent, SWT.NONE);

        ISelectionService selectionService = getSite().getWorkbenchWindow().getSelectionService();
        selectionService.addPostSelectionListener(this);
    }

    @Override
    public void dispose() {
        ISelectionService selectionService = getSite().getWorkbenchWindow().getSelectionService();
        selectionService.removePostSelectionListener(this);
        super.dispose();
    }

    @Override
    public void setFocus() {
    }

    public void displayFB(final Image image) {
        Display.getDefault().asyncExec(new Runnable() {
            public void run() {
                if (mImage != null) {
                    mImage.dispose();
                }

                mImage = image;
                mCanvas.setBackgroundImage(mImage);
            }
        });
    }

    public void selectionChanged(IWorkbenchPart part, ISelection selection) {
        if (!(part instanceof GLFunctionTraceViewer)) {
            return;
        }

        if (!(selection instanceof IStructuredSelection)) {
            return;
        }

        IStructuredSelection ssel = (IStructuredSelection) selection;
        @SuppressWarnings("rawtypes")
        List objects = ssel.toList();
        if (objects.size() > 0) {
            Object data = objects.get(0);
            if (data instanceof GLCall) {
                GLCall glCall = (GLCall) data;
                if (!glCall.hasFb()) {
                    return;
                }

                GLFunctionTraceViewer traceViewer = (GLFunctionTraceViewer) part;
                GLTrace glTrace = traceViewer.getTrace();
                displayFB(glTrace.getImage(glCall));
            }
        }
    }
}
