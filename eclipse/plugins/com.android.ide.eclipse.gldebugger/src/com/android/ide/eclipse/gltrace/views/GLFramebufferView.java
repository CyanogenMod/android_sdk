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

import com.android.ide.eclipse.gldebugger.Activator;
import com.android.ide.eclipse.gltrace.editors.GLFunctionTraceViewer;
import com.android.ide.eclipse.gltrace.model.GLCall;
import com.android.ide.eclipse.gltrace.model.GLTrace;
import com.android.ide.eclipse.gltrace.widgets.ImageCanvas;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.part.ViewPart;

import java.util.List;

public class GLFramebufferView extends ViewPart implements ISelectionListener {
    public static final String ID = "com.android.ide.eclipse.gltrace.views.GLFrameBuffer"; //$NON-NLS-1$
    private static final boolean FIT_TO_CANVAS_DEFAULT = true;
    private ImageCanvas mImageCanvas;

    public GLFramebufferView() {
    }

    @Override
    public void createPartControl(Composite parent) {
        createImageCanvas(parent);
        createToolbar();

        ISelectionService selectionService = getSite().getWorkbenchWindow().getSelectionService();
        selectionService.addPostSelectionListener(this);
    }

    @Override
    public void dispose() {
        if (mImageCanvas != null) {
            mImageCanvas.dispose();
            mImageCanvas = null;
        }

        ISelectionService selectionService = getSite().getWorkbenchWindow().getSelectionService();
        selectionService.removePostSelectionListener(this);
        super.dispose();
    }

    @Override
    public void setFocus() {
        if (mImageCanvas != null) {
            mImageCanvas.setFocus();
        }
    }

    private class FitToCanvasAction extends Action {
        public FitToCanvasAction() {
            super("Fit to Canvas", Activator.getImageDescriptor("/icons/zoomfit.png")); //$NON-NLS-2$
            setToolTipText("Fit Image to Canvas");
            setChecked(FIT_TO_CANVAS_DEFAULT);
        }

        @Override
        public void run() {
            mImageCanvas.setFitToCanvas(isChecked());
        }
    }

    private void createImageCanvas(Composite parent) {
        mImageCanvas = new ImageCanvas(parent);
        mImageCanvas.setFitToCanvas(FIT_TO_CANVAS_DEFAULT);
    }

    private void createToolbar() {
        getViewSite().getActionBars().getToolBarManager().add(new FitToCanvasAction());
    }

    public void displayFB(final Image image) {
        Display.getDefault().asyncExec(new Runnable() {
            @Override
            public void run() {
                mImageCanvas.setImage(image);
            }
        });
    }

    @Override
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
