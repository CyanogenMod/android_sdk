/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.sdkuilib.repository;

import com.android.sdklib.ISdkLog;
import com.android.sdkuilib.internal.repository.IUpdaterWindow;
import com.android.sdkuilib.internal.repository.UpdaterPage;
import com.android.sdkuilib.internal.repository.UpdaterWindowImpl;
import com.android.sdkuilib.internal.repository.UpdaterWindowImpl2;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;

/**
 * Opens an SDK Updater Window.
 *
 * This is the public entry point for using the window.
 */
public class UpdaterWindow {

    private IUpdaterWindow mWindow;

    /**
     * Creates a new window. Caller must call open(), which will block.
     *
     * @param parentShell Parent shell.
     * @param sdkLog Logger. Cannot be null.
     * @param osSdkRoot The OS path to the SDK root.
     */
    public UpdaterWindow(Shell parentShell, ISdkLog sdkLog, String osSdkRoot) {

        // TODO right now the new PackagesPage is experimental and not enabled by default
        if (System.getenv("ANDROID_SDKMAN_EXP") != null) {  //$NON-NLS-1$
            mWindow = new UpdaterWindowImpl2(parentShell, sdkLog, osSdkRoot);
        } else {
            mWindow = new UpdaterWindowImpl(parentShell, sdkLog, osSdkRoot);
        }
    }

    /**
     * Registers an extra page for the updater window.
     * <p/>
     * Pages must derive from {@link Composite} and implement a constructor that takes
     * a single parent {@link Composite} argument.
     * <p/>
     * All pages must be registered before the call to {@link #open()}.
     *
     * @param pageClass The {@link Composite}-derived class that will implement the page.
     * @param purpose The purpose of this page, e.g. an about box, settings page or generic.
     */
    public void registerPage(Class<? extends UpdaterPage> pageClass,
            UpdaterPage.Purpose purpose) {
        mWindow.registerPage(pageClass, purpose);
    }

    /**
     * Indicate the initial page that should be selected when the window opens.
     * <p/>
     * This must be called before the call to {@link #open()}.
     * If null or if the page class is not found, the first page will be selected.
     */
    public void setInitialPage(Class<? extends Composite> pageClass) {
        mWindow.setInitialPage(pageClass);
    }

    /**
     * Sets whether the auto-update wizard will be shown when opening the window.
     * <p/>
     * This must be called before the call to {@link #open()}.
     */
    public void setRequestAutoUpdate(boolean requestAutoUpdate) {
        mWindow.setRequestAutoUpdate(requestAutoUpdate);
    }

    /**
     * Adds a new listener to be notified when a change is made to the content of the SDK.
     * This should be called before {@link #open()}.
     */
    public void addListener(ISdkChangeListener listener) {
        mWindow.addListener(listener);
    }

    /**
     * Removes a new listener to be notified anymore when a change is made to the content of
     * the SDK.
     */
    public void removeListener(ISdkChangeListener listener) {
        mWindow.removeListener(listener);
    }

    /**
     * Opens the window.
     */
    public void open() {
        mWindow.open();
    }
}
