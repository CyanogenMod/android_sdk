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

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.ide.eclipse.gldebugger.Activator;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.osgi.service.prefs.BackingStoreException;

import java.util.ArrayList;
import java.util.List;

// FIXME: Not all elements in this dialog are functional. They are there for UI review,
// and once we figure out what needs to be there and what not, we'll fix this.
/** Dialog displaying all the trace options before the user initiates tracing. */
public class GLTraceOptionsDialog extends TitleAreaDialog {
    private static final String TITLE = "OpenGL ES 2.0 Trace Options";
    private static final String DEFAULT_MESSAGE = "Provide the application to be traced. The application needs to have INTERNET permission for tracing.";
    private static final String DEFAULT_NUM_FRAMES_TEXT = "10";

    private static final String PREF_APPNAME = "gl.trace.appname";
    private static final String PREF_TRACEFILE = "gl.trace.destfile";

    private static String sSaveToFolder = System.getProperty("user.home");;

    private Button mOkButton;

    private Combo mDeviceCombo;
    private Text mAppToTraceText;
    private Text mTraceFilePathText;

    private String mSelectedDevice = "";
    private String mAppToTrace = "";
    private String mTraceFilePath = "";

    public GLTraceOptionsDialog(Shell parentShell) {
        super(parentShell);
        loadPreferences();
    }

    @Override
    protected Control createDialogArea(Composite shell) {
        setTitle(TITLE);
        setMessage(DEFAULT_MESSAGE);

        Composite parent = (Composite) super.createDialogArea(shell);
        Composite c = new Composite(parent, SWT.BORDER);
        c.setLayout(new GridLayout(2, false));
        c.setLayoutData(new GridData(GridData.FILL_BOTH));

        createLabel(c, "Device:");
        createDeviceDropdown(c, AndroidDebugBridge.getBridge().getDevices());

        createLabel(c, "Activity:");
        createAppToTraceText(c, "e.g. com.example.android.apis");

        createLabel(c, "Capture Mode:");
        createCaptureModeOptions(c);

        createLabel(c, "Capture Framebuffer:");
        createCaptureFBOptions(c);

        createSeparator(c);

        createLabel(c, "Destination File: ");
        createSaveToField(c);

        return c;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        super.createButtonsForButtonBar(parent);

        mOkButton = getButton(IDialogConstants.OK_ID);
        mOkButton.setText("Trace");

        DialogStatus status = validateDialog();
        mOkButton.setEnabled(status.valid);
    }

    private void createSeparator(Composite c) {
        Label l = new Label(c, SWT.SEPARATOR | SWT.HORIZONTAL);
        GridData gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.horizontalSpan = 2;
        l.setLayoutData(gd);
    }

    private void createSaveToField(Composite parent) {
        Composite c = new Composite(parent, SWT.NONE);
        c.setLayout(new GridLayout(2, false));
        c.setLayoutData(new GridData(GridData.FILL_BOTH));

        mTraceFilePathText = new Text(c, SWT.BORDER);
        mTraceFilePathText.setEditable(false);
        mTraceFilePathText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        mTraceFilePathText.setText(mTraceFilePath);

        Button browse = new Button(c, SWT.PUSH);
        browse.setText("Browse...");
        browse.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                String fName = openBrowseDialog();
                if (fName == null) {
                    return;
                }

                mTraceFilePathText.setText(fName);
                validateAndSetMessage();
            }
        });
    }

    private String openBrowseDialog() {
        FileDialog fd = new FileDialog(Display.getDefault().getActiveShell(), SWT.SAVE);

        fd.setText("Save To");
        fd.setFileName("trace1.gltrace");

        fd.setFilterPath(sSaveToFolder);
        fd.setFilterExtensions(new String[] { "*.gltrace" });

        String fname = fd.open();
        if (fname == null || fname.trim().length() == 0) {
            return null;
        }

        sSaveToFolder = fd.getFilterPath();
        return fname;
    }

    /** Options controlling when the FB should be captured. */
    private void createCaptureFBOptions(Composite parent) {
        Composite c = new Composite(parent, SWT.NONE);
        c.setLayout(new GridLayout(1, false));
        c.setLayoutData(new GridData(GridData.FILL_BOTH));

        Button b1 = new Button(c, SWT.CHECK);
        b1.setText("On eglSwap()");
        b1.setSelection(true);

        Button b2 = new Button(c, SWT.CHECK);
        b2.setText("On glDrawElements() and glDrawArrays()");
        b2.setSelection(false);
    }

    private void createCaptureModeOptions(Composite parent) {
        Composite c = new Composite(parent, SWT.NONE);
        c.setLayout(new GridLayout(2, false));
        c.setLayoutData(new GridData(GridData.FILL_BOTH));

        Button b1 = new Button(c, SWT.RADIO);
        b1.setText("Infinite Buffer");
        GridData gd = new GridData();
        gd.horizontalSpan = 2;
        b1.setLayoutData(gd);

        Button b2 = new Button(c, SWT.RADIO);
        b2.setText("Last N frames");
        b2.setEnabled(false);

        b1.setSelection(true);

        Text t = new Text(c, SWT.BORDER);
        t.setMessage(DEFAULT_NUM_FRAMES_TEXT);
        gd = new GridData();
        gd.widthHint = 30;
        t.setLayoutData(gd);
        t.setEditable(false);
        t.setEnabled(false);
    }

    private Text createAppToTraceText(Composite parent, String defaultMessage) {
        mAppToTraceText = new Text(parent, SWT.BORDER);
        mAppToTraceText.setMessage(defaultMessage);
        mAppToTraceText.setText(mAppToTrace);

        mAppToTraceText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        mAppToTraceText.addModifyListener(new ModifyListener() {
            public void modifyText(ModifyEvent e) {
                validateAndSetMessage();
            }
        });

        return mAppToTraceText;
    }

    private void validateAndSetMessage() {
        DialogStatus status = validateDialog();
        mOkButton.setEnabled(status.valid);
        setErrorMessage(status.message);
    }

    private Combo createDeviceDropdown(Composite parent, IDevice[] devices) {
        mDeviceCombo = new Combo(parent, SWT.READ_ONLY | SWT.BORDER);

        List<String> items = new ArrayList<String>(devices.length);
        for (IDevice d : devices) {
            String name = d.getAvdName();
            if (name == null) {
                name = d.getSerialNumber();
            }
            items.add(name);
        }
        mDeviceCombo.setItems(items.toArray(new String[0]));
        mDeviceCombo.select(0);
        return mDeviceCombo;
    }

    private void createLabel(Composite parent, String text) {
        Label l = new Label(parent, SWT.NONE);
        l.setText(text);
        GridData gd = new GridData();
        gd.horizontalAlignment = SWT.RIGHT;
        gd.verticalAlignment = SWT.CENTER;
        l.setLayoutData(gd);
    }

    /**
     * A tuple that specifies whether the current state of the inputs
     * on the dialog is valid or not. If it is not valid, the message
     * field stores the reason why it isn't.
     */
    private final class DialogStatus {
        final boolean valid;
        final String message;

        private DialogStatus(boolean isValid, String errMessage) {
            valid = isValid;
            message = errMessage;
        }
    }

    private DialogStatus validateDialog() {
        if (mAppToTraceText.getText().trim().length() == 0) {
            return new DialogStatus(false, "Provide an application name");
        }

        if (mTraceFilePathText.getText().trim().length() == 0) {
            return new DialogStatus(false, "Specify the location where the trace will be saved.");
        }

        return new DialogStatus(true, null);
    }

    @Override
    protected void okPressed() {
        mAppToTrace = mAppToTraceText.getText();
        mTraceFilePath = mTraceFilePathText.getText();
        mSelectedDevice = mDeviceCombo.getText();

        savePreferences();

        super.okPressed();
    }

    private void savePreferences() {
        IEclipsePreferences prefs = new InstanceScope().getNode(Activator.PLUGIN_ID);
        prefs.put(PREF_APPNAME, mAppToTrace);
        prefs.put(PREF_TRACEFILE, mTraceFilePath);
        try {
            prefs.flush();
        } catch (BackingStoreException e) {
            // ignore issues while persisting preferences
        }
    }

    private void loadPreferences() {
        IEclipsePreferences prefs = new InstanceScope().getNode(Activator.PLUGIN_ID);
        mAppToTrace = prefs.get(PREF_APPNAME, "");
        mTraceFilePath = prefs.get(PREF_TRACEFILE, "");
    }

    public String getDevice() {
        return mSelectedDevice;
    }

    public String getApplicationToTrace() {
        return mAppToTrace.trim();
    }

    public String getTraceDestination() {
        return mTraceFilePath;
    }
}
