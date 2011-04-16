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

package com.android.sdkuilib.internal.tasks;

import com.android.sdklib.internal.repository.ITask;
import com.android.sdklib.internal.repository.ITaskMonitor;

import org.eclipse.swt.widgets.Shell;


/**
 * An {@link ITaskMonitor} that displays a {@link ProgressTaskDialog}.
 */
public final class ProgressTask extends TaskMonitorImpl {

    private final ProgressTaskDialog mDialog;
    private boolean mAutomaticallyCloseOnTaskCompletion = true;


    /**
     * Creates a new {@link ProgressTask} with the given title.
     * The given task will execute in a separate thread (not the UI thread).
     *
     * This blocks till the thread ends.
     */
    public ProgressTask(Shell parent, String title, ITask task) {
        super(new ProgressTaskDialog(parent));
        mDialog = (ProgressTaskDialog) getUiProvider();
        mDialog.setText(title);
        mDialog.open(createTaskThread(title, task));
    }

    /**
     * Creates a thread to run the task. The thread has not been started yet.
     * When the task completes, requests to close the dialog.
     *
     * @return A new thread that will run the task. The thread has not been started yet.
     */
    private Thread createTaskThread(String title, final ITask task) {
        if (task != null) {
            return new Thread(title) {
                @Override
                public void run() {
                    task.run(ProgressTask.this);
                    if (mAutomaticallyCloseOnTaskCompletion) {
                        mDialog.setAutoCloseRequested();
                    } else {
                        mDialog.setManualCloseRequested();
                    }
                }
            };
        }
        return null;
    }

    /**
     * Sets the dialog to not auto-close since we want the user to see the error.
     * {@inheritDoc}
     */
    @Override
    public void logError(String format, Object...args) {
        mAutomaticallyCloseOnTaskCompletion = false;
        super.logError(format, args);
    }
}
