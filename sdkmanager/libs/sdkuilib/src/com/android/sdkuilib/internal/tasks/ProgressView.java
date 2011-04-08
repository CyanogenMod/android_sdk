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

import com.android.sdklib.internal.repository.ITask;
import com.android.sdklib.internal.repository.ITaskMonitor;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Widget;


/**
 * Implements a "view" that uses an existing progress bar, status button and
 * status text to display a {@link ITaskMonitor}.
 */
public final class ProgressView implements IProgressUiProvider {

    private static enum State {
        /** View created but there's no task running. Next state can only be ACTIVE. */
        IDLE,
        /** A task is currently running. Next state is either STOP_PENDING or IDLE. */
        ACTIVE,
        /** Stop button has been clicked. Waiting for thread to finish. Next state is IDLE. */
        STOP_PENDING,
    }

    /** The current mode of operation of the dialog. */
    private State mState = State.IDLE;

    // UI fields
    private final Label mLabel;
    private final Control mStopButton;
    private final ProgressBar mProgressBar;
    private final StringBuffer mResultText = new StringBuffer();


    /**
     * Creates a new {@link ProgressView} object, a simple "holder" for the various
     * widgets used to display and update a progress + status bar.
     */
    public ProgressView(Label label, ProgressBar progressBar, Control stopButton) {
        mLabel = label;
        mProgressBar = progressBar;
        mProgressBar.setEnabled(false);

        mStopButton = stopButton;
        mStopButton.addListener(SWT.Selection, new Listener() {
            public void handleEvent(Event event) {
                if (mState == State.ACTIVE) {
                    changeState(State.STOP_PENDING);
                }
            }
        });
    }

    /**
     * Starts the task and block till it's either finished or cancelled.
     */
    public void startTask(final String title, final ITask task) {
        if (task != null) {
            try {
                mLabel.setText(title);
                mProgressBar.setSelection(0);
                mProgressBar.setEnabled(true);
                changeState(ProgressView.State.ACTIVE);

                Runnable r = new Runnable() {
                    public void run() {
                        task.run(new TaskMonitorImpl(ProgressView.this));
                    }
                };

                Thread t = new Thread(r, title);
                t.start();

                // Process the app's event loop whilst we wait for the thread to finish
                Display display = mProgressBar.getDisplay();
                while (!mProgressBar.isDisposed() && t.isAlive()) {
                    if (!display.readAndDispatch()) {
                        display.sleep();
                    }
                }


            } catch (Exception e) {
                // TODO log

            } finally {
                changeState(ProgressView.State.IDLE);
                mProgressBar.setSelection(0);
                mProgressBar.setEnabled(false);
            }
        }
    }

    private void syncExec(final Widget widget, final Runnable runnable) {
        if (widget != null && !widget.isDisposed()) {
            widget.getDisplay().syncExec(runnable);
        }
    }

    private void changeState(State state) {
        if (mState != null ) {
            mState = state;
        }

        syncExec(mStopButton, new Runnable() {
            public void run() {
                mStopButton.setEnabled(mState == State.ACTIVE);
            }
        });

    }

    // --- Implementation of ITaskUiProvider ---

    public boolean isCancelRequested() {
        return mState != State.ACTIVE;
    }

    /**
     * Sets the description in the current task dialog.
     * This method can be invoked from a non-UI thread.
     */
    public void setDescription(final String description) {
        syncExec(mLabel, new Runnable() {
            public void run() {
                mLabel.setText(description);
            }
        });
        mResultText.append("** ").append(description);
    }

    /**
     * Sets the description in the current task dialog.
     * This method can be invoked from a non-UI thread.
     */
    public void setResult(final String result) {
        mResultText.append("=> ").append(result);
    }

    /**
     * Sets the max value of the progress bar.
     * This method can be invoked from a non-UI thread.
     *
     * @see ProgressBar#setMaximum(int)
     */
    public void setProgressMax(final int max) {
        syncExec(mProgressBar, new Runnable() {
            public void run() {
                mProgressBar.setMaximum(max);
            }
        });
    }

    /**
     * Sets the current value of the progress bar.
     * This method can be invoked from a non-UI thread.
     */
    public void setProgress(final int value) {
        syncExec(mProgressBar, new Runnable() {
            public void run() {
                mProgressBar.setSelection(value);
            }
        });
    }

    /**
     * Returns the current value of the progress bar,
     * between 0 and up to {@link #setProgressMax(int)} - 1.
     * This method can be invoked from a non-UI thread.
     */
    public int getProgress() {
        final int[] result = new int[] { 0 };

        if (!mProgressBar.isDisposed()) {
            mProgressBar.getDisplay().syncExec(new Runnable() {
                public void run() {
                    if (!mProgressBar.isDisposed()) {
                        result[0] = mProgressBar.getSelection();
                    }
                }
            });
        }

        return result[0];
    }

    public boolean displayPrompt(final String title, final String message) {
        final boolean[] result = new boolean[] { false };

        if (!mProgressBar.isDisposed()) {
            final Shell shell = mProgressBar.getShell();
            Display display = shell.getDisplay();

            display.syncExec(new Runnable() {
                public void run() {
                    result[0] = MessageDialog.openQuestion(shell, title, message);
                }
            });
        }

        return result[0];
    }
}
