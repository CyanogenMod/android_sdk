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

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/** Dialog displayed while the trace is being streamed from device to host. */
public class GLTraceCollectorDialog extends Dialog {
    private Text mFramesCollectedText;
    private Text mTraceFileSizeText;
    private GLTraceWriter mWriter;
    private int mFramesCollected;
    private int mTraceFileSize;

    protected GLTraceCollectorDialog(Shell parentShell) {
        super(parentShell);
    }

    @Override
    protected Control createButtonBar(Composite parent) {
        Composite c = new Composite(parent, SWT.NONE);
        c.setLayout(new GridLayout(0, false));
        GridData gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.horizontalAlignment = GridData.CENTER;
        c.setLayoutData(gd);

        createButton(c, IDialogConstants.OK_ID, "Stop Tracing", true);
        return c;
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite c = new Composite(parent, SWT.NONE);
        c.setLayout(new GridLayout(2, false));
        c.setLayoutData(new GridData(GridData.FILL_BOTH));

        createLabel(c, "Frames Collected:");
        mFramesCollectedText = createText(c);

        createLabel(c, "Trace File Size:");
        mTraceFileSizeText = createText(c);

        ProgressBar pb = new ProgressBar(c, SWT.INDETERMINATE);
        GridData gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.horizontalSpan = 2;
        pb.setLayoutData(gd);

        return super.createDialogArea(parent);
    }

    private Text createText(Composite c) {
        Text t = new Text(c, SWT.BORDER);

        GridData gd = new GridData();
        gd.widthHint = 100;
        t.setLayoutData(gd);

        return t;
    }

    private void createLabel(Composite parent, String text) {
        Label l = new Label(parent, SWT.NONE);
        l.setText(text);
        GridData gd = new GridData();
        gd.horizontalAlignment = SWT.RIGHT;
        gd.verticalAlignment = SWT.CENTER;
        l.setLayoutData(gd);
    }

    @Override
    protected void okPressed() {
        if (mWriter != null) {
            mWriter.stopTracing();
        }

        super.okPressed();
    }

    public void setFrameCount(final int n) {
        mFramesCollected = n;
        scheduleDisplayUpdate();
    }

    public void setTraceFileSize(final int n) {
        mTraceFileSize = n;
        scheduleDisplayUpdate();
    }

    private Runnable mRefreshTask = null;

    /** Schedule a refresh UI task if one is not already pending. */
    private void scheduleDisplayUpdate() {
        if (mRefreshTask == null) {
            mRefreshTask = new Runnable() {
                public void run() {
                    mRefreshTask = null;

                    if (mFramesCollectedText.isDisposed()) {
                        return;
                    }

                    mFramesCollectedText.setText(Integer.toString(mFramesCollected));
                    mTraceFileSizeText.setText(Integer.toString(mTraceFileSize) + " bytes");
                }
            };
            Display.getDefault().asyncExec(mRefreshTask);
        }
    }

    public void setTraceWriter(GLTraceWriter writer) {
        mWriter = writer;
    }
}
