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

import com.android.ide.eclipse.gltrace.state.GLCompositeProperty;
import com.android.ide.eclipse.gltrace.state.GLStateType;
import com.android.ide.eclipse.gltrace.state.GLStringProperty;
import com.android.ide.eclipse.gltrace.state.IGLProperty;

import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbenchPart;

import java.util.List;

public class TextureView extends ImageViewPart {
    public TextureView() {
        super(false);
    }

    public static final String ID = "com.android.ide.eclipse.gltrace.views.Texture"; //$NON-NLS-1$

    @Override
    public void selectionChanged(IWorkbenchPart part, ISelection selection) {
        if (!(part instanceof StateView)) {
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
            if (!(data instanceof IGLProperty)) {
                return;
            }

            String textureImagePath = getTextureImage((IGLProperty) data);
            if (textureImagePath == null) {
                setImage(null);
                return;
            }

            setImage(getImage(textureImagePath));
        }
    }

    private Image getImage(String imagePath) {
        return new Image(Display.getDefault(), imagePath);
    }

    /**
     * Extract the TEXTURE_IMAGE property from the gl state hierarchy.
     * The TEXTURE_IMAGE property fits in the hierarchy like so:
     * ...
     *   |---PER_TEXTURE_STATE
     *       |--
     *       |-- TEXTURE_IMAGE
     *
     * So we can extract the TEXTURE_IMAGE if the given property is either PER_TEXTURE_STATE,
     * or a child of PER_TEXTURE_STATE.
     */
    private String getTextureImage(IGLProperty property) {
        // if the selected property is the texture image, then we just return its value
        if (property.getType() == GLStateType.TEXTURE_IMAGE) {
            return ((GLStringProperty) property).getStringValue();
        }

        if (property.getType() != GLStateType.PER_TEXTURE_STATE) {
            // See if the property is a child of PER_TEXTURE_STATE
            IGLProperty parent = property.getParent();
            if (parent == null || parent.getType() != GLStateType.PER_TEXTURE_STATE) {
                return null;
            } else {
                property = parent;
            }
        }

        // property is now the parent property of TEXTURE_IMAGE
        GLCompositeProperty perTextureState = (GLCompositeProperty) property;
        IGLProperty imageProperty = perTextureState.getProperty(GLStateType.TEXTURE_IMAGE);
        return ((GLStringProperty) imageProperty).getStringValue();
    }
}
