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

package com.android.ide.eclipse.gltrace.views.detail;

import com.android.ide.eclipse.gltrace.state.GLCompositeProperty;
import com.android.ide.eclipse.gltrace.state.GLStateType;
import com.android.ide.eclipse.gltrace.state.GLStringProperty;
import com.android.ide.eclipse.gltrace.state.IGLProperty;
import com.android.ide.eclipse.gltrace.views.FitToCanvasAction;
import com.android.ide.eclipse.gltrace.widgets.ImageCanvas;

import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;

import java.util.Collections;
import java.util.List;

public class TextureImageDetailsProvider implements IStateDetailProvider {
    private ImageCanvas mImageCanvas;
    private FitToCanvasAction mFitToCanvasAction;
    private List<IContributionItem> mToolBarItems;

    @Override
    public boolean isApplicable(IGLProperty state) {
        return getTextureImageProperty(state) != null;
    }

    @Override
    public void createControl(Composite parent) {
        mImageCanvas = new ImageCanvas(parent);
        mImageCanvas.setFitToCanvas(false);

        mFitToCanvasAction = new FitToCanvasAction(false, mImageCanvas);
        mToolBarItems = Collections.singletonList(
                (IContributionItem) new ActionContributionItem(mFitToCanvasAction));
    }

    @Override
    public void disposeControl() {
        mImageCanvas.dispose();
        mImageCanvas = null;
    }

    @Override
    public Control getControl() {
        return mImageCanvas;
    }

    @Override
    public void updateControl(IGLProperty state) {
        IGLProperty imageProperty = getTextureImageProperty(state);
        if (imageProperty == null) {
            return;
        }

        String texturePath = ((GLStringProperty) imageProperty).getStringValue();
        if (texturePath != null) {
            mImageCanvas.setImage(new Image(Display.getDefault(), texturePath));
            mImageCanvas.setFitToCanvas(false);
            return;
        }
    }

    /**
     * Get the {@link GLStateType#TEXTURE_IMAGE} property given a node in
     * the state hierarchy.
     * @param state any node in the GL state hierarchy
     * @return The {@link GLStateType#TEXTURE_IMAGE} property if a unique instance
     *         of it can be accessed from the given node, null otherwise.
     *         A unique instance can be accessed if the given node is
     *         either the requested node itself, or its parent or sibling.
     */
    private IGLProperty getTextureImageProperty(IGLProperty state) {
        if (state.getType() == GLStateType.TEXTURE_IMAGE) {
            // given node is the requested node
            return state;
        }

        if (state.getType() != GLStateType.PER_TEXTURE_STATE) {
            // if it is not the parent, then it could be a sibling, in which case
            // we go up a level to its parent
            state = state.getParent();
        }

        if (state != null && state.getType() == GLStateType.PER_TEXTURE_STATE) {
            // if it is the parent, we can access the required property
            return ((GLCompositeProperty) state).getProperty(GLStateType.TEXTURE_IMAGE);
        }

        return null;
    }

    @Override
    public List<IContributionItem> getToolBarItems() {
        return mToolBarItems;
    }
}
