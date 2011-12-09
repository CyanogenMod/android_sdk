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

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.ScrollBar;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.part.ViewPart;

import java.util.List;

public class GLFramebufferView extends ViewPart implements ISelectionListener {
    public static final String ID = "com.android.ide.eclipse.gltrace.GLFrameBuffer";
    private static final boolean FIT_TO_CANVAS_DEFAULT = true;
    private static final int SCROLLBAR_INCREMENT = 20;
    private Point mOrigin;
    private Canvas mCanvas;
    private ScrollBar mHorizontalScrollBar;
    private ScrollBar mVerticalScrollBar;
    private Image mImage;
    private boolean mFitToCanvas = FIT_TO_CANVAS_DEFAULT;

    public GLFramebufferView() {
    }

    @Override
    public void createPartControl(Composite parent) {
        createCanvas(parent);
        createToolbar();

        ISelectionService selectionService = getSite().getWorkbenchWindow().getSelectionService();
        selectionService.addPostSelectionListener(this);
    }

    @Override
    public void dispose() {
        if (mImage != null && !mImage.isDisposed()) {
            mImage.dispose();
        }

        ISelectionService selectionService = getSite().getWorkbenchWindow().getSelectionService();
        selectionService.removePostSelectionListener(this);
        super.dispose();
    }

    @Override
    public void setFocus() {
        if (mCanvas != null) {
            mCanvas.setFocus();
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
            setFitToCanvas(isChecked());
        }
    }

    private void setFitToCanvas(boolean checked) {
        mFitToCanvas = checked;
        updateScrollBars();
        mCanvas.redraw();
    }

    private void createToolbar() {
        getViewSite().getActionBars().getToolBarManager().add(new FitToCanvasAction());
    }

    private void createCanvas(Composite parent) {
        mCanvas = new Canvas(parent, SWT.NO_BACKGROUND | SWT.V_SCROLL | SWT.H_SCROLL);
        mOrigin = new Point(0, 0);

        mHorizontalScrollBar = mCanvas.getHorizontalBar();
        mVerticalScrollBar = mCanvas.getVerticalBar();

        setScrollBarIncrements();
        setScrollBarPageIncrements(mCanvas.getClientArea());

        updateScrollBars();

        SelectionListener scrollBarSelectionListener = new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (e.getSource() == mHorizontalScrollBar) {
                    scrollHorizontally();
                } else {
                    scrollVertically();
                }
            }
        };

        mHorizontalScrollBar.addSelectionListener(scrollBarSelectionListener);
        mVerticalScrollBar.addSelectionListener(scrollBarSelectionListener);

        mCanvas.addListener(SWT.Resize,  new Listener() {
            public void handleEvent(Event e) {
                setScrollBarPageIncrements(mCanvas.getClientArea());
                updateScrollBars();
            }
        });

        mCanvas.addListener(SWT.Paint, new Listener() {
            public void handleEvent(Event e) {
                paintCanvas(e.gc);
            }
        });
    }

    private void setScrollBarPageIncrements(Rectangle clientArea) {
        mHorizontalScrollBar.setPageIncrement(clientArea.width);
        mVerticalScrollBar.setPageIncrement(clientArea.height);
    }

    private void setScrollBarIncrements() {
        // The default increment is 1 pixel. Assign a saner default.
        mHorizontalScrollBar.setIncrement(SCROLLBAR_INCREMENT);
        mVerticalScrollBar.setIncrement(SCROLLBAR_INCREMENT);
    }

    private void scrollHorizontally() {
        if (mImage == null) {
            return;
        }

        int selection = mHorizontalScrollBar.getSelection();
        int destX = -selection - mOrigin.x;
        Rectangle imageBounds = mImage.getBounds();
        mCanvas.scroll(destX, 0, 0, 0, imageBounds.width, imageBounds.height, false);
        mOrigin.x = -selection;
    }

    private void scrollVertically() {
        if (mImage == null) {
            return;
        }

        int selection = mVerticalScrollBar.getSelection();
        int destY = -selection - mOrigin.y;
        Rectangle imageBounds = mImage.getBounds();
        mCanvas.scroll(0, destY, 0, 0, imageBounds.width, imageBounds.height, false);
        mOrigin.y = -selection;
    }

    private void updateScrollBars() {
        Rectangle client = mCanvas.getClientArea();

        int imageWidth, imageHeight;
        if (mImage != null & !mFitToCanvas) {
            imageWidth = mImage.getBounds().width;
            imageHeight = mImage.getBounds().height;
        } else {
            imageWidth = client.width;
            imageHeight = client.height;
        }

        mHorizontalScrollBar.setMaximum(imageWidth);
        mVerticalScrollBar.setMaximum(imageHeight);
        mHorizontalScrollBar.setThumb(Math.min(imageWidth, client.width));
        mVerticalScrollBar.setThumb(Math.min(imageHeight, client.height));

        int hPage = imageWidth - client.width;
        int vPage = imageHeight - client.height;
        int hSelection = mHorizontalScrollBar.getSelection();
        int vSelection = mVerticalScrollBar.getSelection();
        if (hSelection >= hPage) {
            if (hPage <= 0) {
                hSelection = 0;
            }
            mOrigin.x = -hSelection;
        }

        if (vSelection >= vPage) {
            if (vPage <= 0) {
                vSelection = 0;
            }
            mOrigin.y = -vSelection;
        }

        mCanvas.redraw();
    }

    private void paintCanvas(GC gc) {
        gc.fillRectangle(mCanvas.getClientArea());
        if (mImage == null) {
            return;
        }

        Rectangle rect = mImage.getBounds();
        Rectangle client = mCanvas.getClientArea();

        if (mFitToCanvas && rect.width > 0 && rect.height > 0) {
            double sx = (double) client.width / (double) rect.width;
            double sy = (double) client.height / (double) rect.height;

            if (sx < sy) {
                // if we need to scale more horizontally, then reduce the client height
                // appropriately so that aspect ratios are maintained
                gc.drawImage(mImage,
                        0, 0, rect.width, rect.height,
                        0, 0, client.width, (int)(rect.height * sx));
            } else {
                // scale client width to maintain aspect ratio
                gc.drawImage(mImage,
                        0, 0, rect.width, rect.height,
                        0, 0, (int)(rect.width * sy), client.height);
            }
        } else {
            gc.drawImage(mImage, mOrigin.x, mOrigin.y);
        }
    }

    public void displayFB(final Image image) {
        Display.getDefault().asyncExec(new Runnable() {
            public void run() {
                if (mImage != null) {
                    mImage.dispose();
                }

                mImage = image;
                mOrigin = new Point(0, 0);
                updateScrollBars();
                mCanvas.redraw();
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
