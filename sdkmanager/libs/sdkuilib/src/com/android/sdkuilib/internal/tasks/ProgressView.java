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

import com.android.sdklib.ISdkLog;
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

    /**
     * Accumulated log text. This is intended to be displayed in a scrollable
     * text area. The various methods that append to the log might not be called
     * from the UI thread, so accesses should be synchronized on the builder.
     */
    private final StringBuilder mLogText = new StringBuilder();

    /** Logger object. Can be null. */
    private final ISdkLog mLog;

    private String mLastLogMsg = null;

    /**
     * Creates a new {@link ProgressView} object, a simple "holder" for the various
     * widgets used to display and update a progress + status bar.
     *
     * @param label The label to display titles of status updates (e.g. task titles and
     *      calls to {@link #setDescription(String)}.) Must not be null.
     * @param progressBar The progress bar to update during a task. Must not be null.
     * @param stopButton The stop button. It will be disabled when there's no task that can
     *      be interrupted. A selection listener will be attached to it. Optional. Can be null.
     * @param log An <em>optional</em> logger object that will be used to report all the log.
     *      If null, all logging will be collected here with a little UI to display it.
     */
    public ProgressView(
            Label label,
            ProgressBar progressBar,
            Control stopButton,
            ISdkLog log) {
        mLabel = label;
        mProgressBar = progressBar;
        mLog = log;
        mProgressBar.setEnabled(false);

        mStopButton = stopButton;
        if (mStopButton != null) {
            mStopButton.addListener(SWT.Selection, new Listener() {
                public void handleEvent(Event event) {
                    if (mState == State.ACTIVE) {
                        changeState(State.STOP_PENDING);
                    }
                }
            });
        }
    }

    /**
     * Starts the task and block till it's either finished or canceled.
     * This can be called from a non-UI thread safely.
     */
    public void startTask(
            final String title,
            final ITaskMonitor parentMonitor,
            final ITask task) {
        if (task != null) {
            try {
                if (parentMonitor == null && !mProgressBar.isDisposed()) {
                    mLabel.setText(title);
                    mProgressBar.setSelection(0);
                    mProgressBar.setEnabled(true);
                    changeState(ProgressView.State.ACTIVE);
                }

                Runnable r = new Runnable() {
                    public void run() {
                        if (parentMonitor == null) {
                            task.run(new TaskMonitorImpl(ProgressView.this));

                        } else {
                            // Use all the reminder of the parent monitor.
                            if (parentMonitor.getProgressMax() == 0) {
                                parentMonitor.setProgressMax(1);
                            }
                            ITaskMonitor sub = parentMonitor.createSubMonitor(
                                    parentMonitor.getProgressMax() - parentMonitor.getProgress());
                            try {
                                task.run(sub);
                            } finally {
                                int delta =
                                    sub.getProgressMax() - sub.getProgress();
                                if (delta > 0) {
                                    sub.incProgress(delta);
                                }
                            }
                        }
                    }
                };

                final Thread t = new Thread(r, title);
                t.start();

                // If for some reason the UI has been disposed, just abort the thread.
                if (mProgressBar.isDisposed()) {
                    return;
                }

                if (TaskMonitorImpl.isTaskMonitorImpl(parentMonitor)) {
                    // If there's a parent monitor and it's our own class, we know this parent
                    // monitor is already running an event loop, so don't run a second one,
                    // and instead just wait for the thread.
                    t.join();
                } else {
                    // Process the app's event loop whilst we wait for the thread to finish
                    while (!mProgressBar.isDisposed() && t.isAlive()) {
                        if (!mProgressBar.getDisplay().readAndDispatch()) {
                            mProgressBar.getDisplay().sleep();
                        }
                    }
                }
            } catch (Exception e) {
                // TODO log

            } finally {
                if (parentMonitor == null && !mProgressBar.isDisposed()) {
                    changeState(ProgressView.State.IDLE);
                    mProgressBar.setSelection(0);
                    mProgressBar.setEnabled(false);
                }
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

        if (acceptLog(description)) {
            if (mLog != null) {
                mLog.printf("%1$s", description);
            } else {
                synchronized (mLogText) {
                    mLogText.append(description);
                }
            }
        }
    }

    /**
     * Logs a "normal" information line.
     * This method can be invoked from a non-UI thread.
     */
    public void log(String log) {
        if (acceptLog(log)) {
            if (mLog != null) {
                mLog.printf("  %1$s", log);
            } else {
                synchronized (mLogText) {
                    mLogText.append(" ").append(log);
                }
            }
        }
    }

    /**
     * Logs an "error" information line.
     * This method can be invoked from a non-UI thread.
     */
    public void logError(String log) {
        if (acceptLog(log)) {
            if (mLog != null) {
                mLog.error(null, "  %1$s", log);
            } else {
                synchronized (mLogText) {
                    mLogText.append("ERROR: ").append(log);
                }
            }
        }
    }

    /**
     * Logs a "verbose" information line, that is extra details which are typically
     * not that useful for the end-user and might be hidden until explicitly shown.
     * This method can be invoked from a non-UI thread.
     */
    public void logVerbose(String log) {
        if (acceptLog(log)) {
            if (mLog != null) {
                mLog.printf("    %1$s", log);
            } else {
                synchronized (mLogText) {
                    mLogText.append("  ").append(log);
                }
            }
        }
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

    // ----

    /**
     * Filter messages displayed in the log: <br/>
     * - Messages with a % are typical part of a progress update and shouldn't be in the log. <br/>
     * - Messages that are the same as the same output message should be output a second time.
     *
     * @param msg The potential log line to print.
     * @return True if the log line should be printed, false otherwise.
     */
    private boolean acceptLog(String msg) {
        if (msg == null) {
            return false;
        }

        msg = msg.trim();
        if (msg.indexOf('%') != -1) {
            return false;
        }

        if (msg.equals(mLastLogMsg)) {
            return false;
        }

        mLastLogMsg = msg;
        return true;
    }
}
