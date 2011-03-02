/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ide.eclipse.adt.internal.editors.ui;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.ToolBar;

/**
 * Describes the content of a {@link IDecorContent}.
 */
public interface IDecorContent {
    /**
     * Creates the control that will be displayed in the {@link IDecorContent}.
     * The control must be available from {@link #getControl()}.
     * @param parent The {@link IDecorContent} parent. Never null.
     */
    public void createControl(Composite parent);

    /**
     * Creates the toolbar items that will be displayed in the {@link IDecorContent}. This
     * method will always be called <b>after</b> {@link #createControl}, so the
     * implementation can assume that it can call {@link #getControl()} to obtain the
     * corresponding control that the toolbar items will operate on.
     *
     * @param toolbar the toolbar to add the toolbar items to
     */
    public void createToolbarItems(ToolBar toolbar);

    /**
     * Returns the control previously created by {@link #createControl(Composite)}.
     * @return A control to display in the {@link IDecorContent}. Must not be null.
     */
    public Control getControl();

    /**
     * Returns an optional title for the {@link IDecorContent}'s header.
     * @return A string to display in the header or null.
     */
    public String getTitle();

    /**
     * Returns an optional image for the {@link IDecorContent}'s header.
     * @return An image to display in the header or null.
     */
    public Image getImage();
}
