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

import com.android.ide.eclipse.gltrace.views.GLFramebufferView;
import com.android.ide.eclipse.gltrace.views.StateView;

import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;

public class GLTracePerspective implements IPerspectiveFactory {
    private static final String STATE_FOLDER_ID = "right";      //$NON-NLS-1$
    private static final String FB_FOLDER_ID = "rightBottom";   //$NON-NLS-1$

    @Override
    public void createInitialLayout(IPageLayout layout) {
        // Add the OpenGL state view to the right of the editor
        IFolderLayout right = layout.createFolder(STATE_FOLDER_ID, IPageLayout.RIGHT, 0.7f,
                layout.getEditorArea());
        right.addView(StateView.ID);

        // Add the OpenGL Framebuffer view below the state view
        IFolderLayout rightBottom = layout.createFolder(FB_FOLDER_ID, IPageLayout.BOTTOM, 0.6f,
                STATE_FOLDER_ID);
        rightBottom.addView(GLFramebufferView.ID);
    }
}
