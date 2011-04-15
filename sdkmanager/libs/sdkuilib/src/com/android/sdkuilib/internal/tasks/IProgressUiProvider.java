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

package com.android.sdkuilib.internal.tasks;

import com.android.sdklib.internal.repository.ITaskMonitor;

import org.eclipse.swt.widgets.ProgressBar;

/**
 * Interface for a user interface that displays both a task status
 * (e.g. via an {@link ITaskMonitor}) and the progress state of the
 * task (e.g. via a progress bar.)
 * <p/>
 * See {@link ITaskMonitor} for details on how a monitor expects to
 * be displayed.
 */
interface IProgressUiProvider {

    public abstract boolean isCancelRequested();

    /**
     * Sets the description in the current task dialog.
     * This method can be invoked from a non-UI thread.
     */
    public abstract void setDescription(String description);

    /**
     * Logs a "normal" information line.
     * This method can be invoked from a non-UI thread.
     */
    public abstract void log(String log);

    /**
     * Logs an "error" information line.
     * This method can be invoked from a non-UI thread.
     */
    public abstract void logError(String log);

    /**
     * Logs a "verbose" information line, that is extra details which are typically
     * not that useful for the end-user and might be hidden until explicitly shown.
     * This method can be invoked from a non-UI thread.
     */
    public abstract void logVerbose(String log);

    /**
     * Sets the max value of the progress bar.
     * This method can be invoked from a non-UI thread.
     *
     * @see ProgressBar#setMaximum(int)
     */
    public abstract void setProgressMax(int max);

    /**
     * Sets the current value of the progress bar.
     * This method can be invoked from a non-UI thread.
     */
    public abstract void setProgress(int value);

    /**
     * Returns the current value of the progress bar,
     * between 0 and up to {@link #setProgressMax(int)} - 1.
     * This method can be invoked from a non-UI thread.
     */
    public abstract int getProgress();

    /**
     * Display a yes/no question dialog box.
     *
     * This implementation allow this to be called from any thread, it
     * makes sure the dialog is opened synchronously in the ui thread.
     *
     * @param title The title of the dialog box
     * @param message The error message
     * @return true if YES was clicked.
     */
    public abstract boolean displayPrompt(String title, String message);

}
