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

import com.android.ide.eclipse.gltrace.Glcall.GLCall;
import com.android.ide.eclipse.gltrace.editors.GLFunctionTraceViewer;

import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.part.ViewPart;
import org.liblzf.CLZF;

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

    public void displayFB(final GLCall glCall) {
        final int imageArg;

        switch (glCall.getFunction()) {
            case eglSwapBuffers:
                imageArg = 0;
                break;
            case glDrawArrays:
                imageArg = 3;
                break;
            case glDrawElements:
                imageArg = 4;
                break;
            default:
                return;
        }

        Display.getDefault().asyncExec(new Runnable() {
            public void run() {
                if (mImage != null) {
                    mImage.dispose();
                }

                mImage = getImage(glCall, imageArg);
                mCanvas.setBackgroundImage(mImage);
            }
        });
    }

    /**
     * Obtain the image stored in provided GL Method call
     * @param glCall glCall with image data
     * @param imageArg argument that contains the image
     * @return
     */
    private Image getImage(GLCall glCall, int imageArg) {
        int width = glCall.getArgs(imageArg).getIntValue(0);
        int height = glCall.getArgs(imageArg).getIntValue(1);

        byte []compressed = glCall.getArgs(imageArg).getRawBytesList().get(0).toByteArray();
        byte []uncompressed = new byte[width * height * 4];

        int size = CLZF.lzf_decompress(compressed, compressed.length,
                                uncompressed, uncompressed.length);
        assert size == width * height * 4 : "Unexpected image size after decompression.";

        int redMask   = 0xff000000;
        int greenMask = 0x00ff0000;
        int blueMask  = 0x0000ff00;
        PaletteData palette = new PaletteData(redMask, greenMask, blueMask);
        ImageData imageData = new ImageData(
                width,
                height,
                32,         // depth
                palette,
                1,
                uncompressed);
        imageData = imageData.scaledTo(imageData.width, -imageData.height);

        return new Image(Display.getCurrent(), imageData);
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
                displayFB((GLCall) data);
            }
        }
    }
}
