/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.menubar;

import org.eclipse.swt.widgets.Menu;


/**
 * Interface to the platform-specific MenuBarEnhancer implementation returned by
 * {@link MenuBarEnhancer#setupMenu}.
 */
public interface IMenuBarEnhancer {

    /**
     * Updates the menu bar to provide an About menu item and a Preferences menu item.
     * Depending on the platform, the menu items might be decorated with the
     * given {@code appName}.
     * <p/>
     * Users should not call this directly.
     * {@link MenuBarEnhancer#setupMenu} should be used instead.
     *
     * @param appName Name used for the About menu item and similar. Must not be null.
     * @param swtMenu For non-mac platform this is the menu where the "About" and
     *          the "Preferences" menu items are created. Must not be null.
     * @param callbacks Callbacks called when "About" and "Preferences" menu items are invoked.
     *          Must not be null.
     */
    public void setupMenu(
            String appName,
            Menu swtMenu,
            IMenuBarCallback callbacks);
}
