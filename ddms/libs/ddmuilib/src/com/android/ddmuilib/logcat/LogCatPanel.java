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

package com.android.ddmuilib.logcat;

import com.android.ddmuilib.SelectionDependentPanel;
import com.android.ddmuilib.TableHelper;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Table;

/**
 * LogCatPanel displays a table listing the logcat messages.
 */
public final class LogCatPanel extends SelectionDependentPanel
                        implements ILogCatMessageEventListener {
    /** Width (in characters) at which to wrap messages. SWT Tables do not
     * auto wrap long text - they simply clip the text.
     * FIXME: this should be a preference. */
    private static final int MSG_WRAP_WIDTH = 150;

    private TableViewer mViewer;
    private LogCatReceiver mReceiver;
    private IPreferenceStore mPrefStore;

    /**
     * Construct a logcat panel.
     * @param r source of logcat messages.
     * @param prefStore preference store where UI preferences will be saved
     */
    public LogCatPanel(LogCatReceiver r, IPreferenceStore prefStore) {
        mReceiver = r;
        mPrefStore = prefStore;
        mReceiver.addMessageReceivedEventListener(this);
    }

    @Override
    public void deviceSelected() {
        mReceiver.stop();
        mReceiver.start(getCurrentDevice());
        mViewer.setInput(mReceiver.getMessages());
    }

    @Override
    public void clientSelected() {
    }

    @Override
    protected void postCreation() {
    }

    @Override
    protected Control createControl(Composite parent) {
        GridLayout layout = new GridLayout(2, false);
        parent.setLayout(layout);

        createLogcatViewTable(parent);

        return null;
    }

    private void createLogcatViewTable(Composite parent) {
        /* SWT.VIRTUAL style will make the table render faster, but all rows will be
         * of equal heights which causes wrapped messages to just be clipped. */
        final Table table = new Table(parent, SWT.FULL_SELECTION);
        mViewer = new TableViewer(table);

        GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
        gd.horizontalSpan = 2;
        mViewer.getTable().setLayoutData(gd);
        table.getHorizontalBar().setVisible(true);

        /** Columns to show in the table. */
        String[] properties = {
                "Level",
                "Time",
                "PID",
                "Tag",
                "Text",
        };

        /** The sampleText for each column is used to determine the default widths
         * for each column. The contents do not matter, only their lengths are needed. */
        String[] sampleText = {
                "    II",
                "    00-00 00:00:00.0000 ",
                "    0000",
                "    SampleTagText",
                "    Log Message field should be pretty long by default.",
        };

        for (int i = 0; i < properties.length; i++) {
            TableHelper.createTableColumn(mViewer.getTable(),
                    properties[i],                      /* Column title */
                    SWT.LEFT,                           /* Column Style */
                    sampleText[i],                      /* String to compute default col width */
                    getPreferenceKey(properties[i]),    /* Preference Store key for this column */
                    mPrefStore);
        }

        mViewer.getTable().setLinesVisible(true); /* zebra stripe the table */
        mViewer.getTable().setHeaderVisible(true);

        mViewer.setLabelProvider(new LogCatMessageLabelProvider(MSG_WRAP_WIDTH));
        mViewer.setContentProvider(new LogCatMessageContentProvider());
        mViewer.setInput(mReceiver.getMessages());
    }

    private String getPreferenceKey(String field) {
        return "logcat.view.colsize." + field;
    }

    @Override
    public void setFocus() {
    }

    /**
     * Update view whenever a message is received.
     * Implements {@link ILogCatMessageEventListener#messageReceived()}.
     */
    public void messageReceived() {
        Display.getDefault().asyncExec(new Runnable() {
            public void run() {
                if (mViewer.getTable().isDisposed()) {
                    return;
                }
                mViewer.refresh();

                /* if an item has been selected, then don't scroll the table,
                 * otherwise, always display the latest output.
                 * FIXME: this behavior should be controlled via a "scroll lock" button in UI. */
                if (mViewer.getTable().getSelectionCount() == 0) {
                    mViewer.getTable().setTopIndex(mViewer.getTable().getItemCount() - 1);
                }
            }
        });
    }
}
