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

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;


/**
 * On Mac, {@link MenuBarEnhancer#setupMenu} plugs a listener on the About and the
 * Preferences menu items of the standard "application" menu in the menu bar.
 * On Windows or Linux, it adds relevant items to a given {@link Menu} linked to
 * the same listeners.
 */
public final class MenuBarEnhancer {

    private MenuBarEnhancer() {
    }

    /**
     * Creates an instance of {@link IMenuBarEnhancer} specific to the current platform
     * and invoke its {@link IMenuBarEnhancer#setupMenu} to updates the menu bar.
     * <p/>
     * Depending on the platform, this will either hook into the existing About menu item
     * and a Preferences or Options menu item or add new ones to the given {@code swtMenu}.
     * Depending on the platform, the menu items might be decorated with the
     * given {@code appName}.
     * <p/>
     * Potential errors are reported through {@link IMenuBarCallback}.
     *
     * @param appName Name used for the About menu item and similar. Must not be null.
     * @param swtMenu For non-mac platform this is the menu where the "About" and
     *          the "Options" menu items are created. Typically the menu might be
     *          called "Tools". Must not be null.
     * @param callbacks Callbacks called when "About" and "Preferences" menu items are invoked.
     *          Must not be null.
     * @return A actual {@link IMenuBarEnhancer} implementation. Never null.
     *          This is currently not of any use for the caller but is left in case
     *          we want to expand the functionality later.
     */
    public static IMenuBarEnhancer setupMenu(
            String appName,
            Menu swtMenu,
            IMenuBarCallback callbacks) {

        IMenuBarEnhancer enhancer = null;
        String p = SWT.getPlatform();
        String className = null;
        if ("carbon".equals(p)) {                                                 //$NON-NLS-1$
            className = "com.android.menubar.internal.MenuBarEnhancerCarbon";     //$NON-NLS-1$
        } else if ("cocoa".equals(p)) {                                           //$NON-NLS-1$
            // Note: we have a Cocoa implementation that is currently disabled
            // since the SWT.jar that we use only contain Carbon implementations.
            //
            // className = "com.android.menubar.internal.MenuBarEnhancerCocoa";   //$NON-NLS-1$
        }

        if (className != null) {
            try {
                Class<?> clazz = p.getClass().forName(className);
                enhancer = (IMenuBarEnhancer) clazz.newInstance();
            } catch (Exception e) {
                // Log an error and fallback on the default implementation.
                callbacks.printError(
                        "Failed to instantiate %1$s: %2$s",                       //$NON-NLS-1$
                        className,
                        e.toString());
            }
        }

        // Default implementation for other platforms
        if (enhancer == null) {
            enhancer = new IMenuBarEnhancer() {
                public void setupMenu(
                        String appName,
                        Menu menu,
                        final IMenuBarCallback callbacks) {
                    new MenuItem(menu, SWT.SEPARATOR);

                    // Note: we use "Preferences" on Mac and "Options" on Windows/Linux.
                    final MenuItem pref = new MenuItem(menu, SWT.NONE);
                    pref.setText("Options...");

                    final MenuItem about = new MenuItem(menu, SWT.NONE);
                    about.setText("About...");

                    pref.addSelectionListener(new SelectionAdapter() {
                        @Override
                        public void widgetSelected(SelectionEvent e) {
                            try {
                                pref.setEnabled(false);
                                callbacks.onPreferencesMenuSelected();
                                super.widgetSelected(e);
                            } finally {
                                pref.setEnabled(true);
                            }
                        }
                    });

                    about.addSelectionListener(new SelectionAdapter() {
                        @Override
                        public void widgetSelected(SelectionEvent e) {
                            try {
                                about.setEnabled(false);
                                callbacks.onAboutMenuSelected();
                                super.widgetSelected(e);
                            } finally {
                                about.setEnabled(true);
                            }
                        }
                    });
                }
            };
        }

        enhancer.setupMenu(appName, swtMenu, callbacks);
        return enhancer;
    }

}
