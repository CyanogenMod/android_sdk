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

import com.android.ide.eclipse.gldebugger.Activator;
import com.android.ide.eclipse.gltrace.widgets.ImageCanvas;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.part.ViewPart;

/**
 * The {@link ImageViewPart} is an abstract implementation of an Eclipse View
 * that holds an image canvas. This class registers itself to listen to the workbench
 * selection service. Subclasses must implement the
 * {@link #selectionChanged(IWorkbenchPart, ISelection)} method, and call {@link #setImage(Image)}
 * to update the image to be displayed.
 */
public abstract class ImageViewPart extends ViewPart implements ISelectionListener {
    private boolean mFitToCanvasDefault = true;
    private ImageCanvas mImageCanvas;

    public ImageViewPart(boolean fitToCanvasByDefault) {
        mFitToCanvasDefault = fitToCanvasByDefault;
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
            setChecked(mFitToCanvasDefault);
        }

        @Override
        public void run() {
            mImageCanvas.setFitToCanvas(isChecked());
        }
    }

    private void createImageCanvas(Composite parent) {
        mImageCanvas = new ImageCanvas(parent);
        mImageCanvas.setFitToCanvas(mFitToCanvasDefault);
    }

    private void createToolbar() {
        getViewSite().getActionBars().getToolBarManager().add(new FitToCanvasAction());
    }

    protected void setImage(final Image image) {
        Display.getDefault().asyncExec(new Runnable() {
            @Override
            public void run() {
                mImageCanvas.setImage(image);
            }
        });
    };

    @Override
    public abstract void selectionChanged(IWorkbenchPart part, ISelection selection);
}
