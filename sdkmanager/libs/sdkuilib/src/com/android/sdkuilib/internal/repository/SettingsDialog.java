/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.sdkuilib.internal.repository;

import com.android.sdkuilib.ui.GridDataBuilder;
import com.android.sdkuilib.ui.GridLayoutBuilder;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import java.util.Properties;


public class SettingsDialog extends UpdaterBaseDialog implements ISettingsPage {

    // data members
    private final SettingsController mSettingsController;
    private SettingsChangedCallback mSettingsChangedCallback;

    // UI widgets
    private Group mProxySettingsGroup;
    private Group mMiscGroup;
    private Label mProxyServerLabel;
    private Label mProxyPortLabel;
    private Text mProxyServerText;
    private Text mProxyPortText;
    private Button mForceHttpCheck;
    private Button mAskAdbRestartCheck;

    private SelectionAdapter mApplyOnSelected = new SelectionAdapter() {
        @Override
        public void widgetSelected(SelectionEvent e) {
            applyNewSettings(); //$hide$
        }
    };

    private ModifyListener mApplyOnModified = new ModifyListener() {
        @Override
        public void modifyText(ModifyEvent e) {
            applyNewSettings(); //$hide$
        }
    };

    public SettingsDialog(Shell parentShell, UpdaterData updaterData) {
        super(parentShell, updaterData, "Settings" /*title*/);
        assert updaterData != null;
        mSettingsController = updaterData.getSettingsController();
    }

    @Override
    protected void createContents() {
        super.createContents();
        Shell shell = getShell();

        mProxySettingsGroup = new Group(shell, SWT.NONE);
        mProxySettingsGroup.setText("Proxy Settings");
        GridDataBuilder.create(mProxySettingsGroup).fill().grab().hSpan(2);
        GridLayoutBuilder.create(mProxySettingsGroup).columns(2);

        mProxyServerLabel = new Label(mProxySettingsGroup, SWT.NONE);
        GridDataBuilder.create(mProxyServerLabel).hRight().vCenter();
        mProxyServerLabel.setText("HTTP Proxy Server");
        String tooltip = "The DNS name or IP of the HTTP proxy server to use. " +
                         "When empty, no HTTP proxy is used.";
        mProxyServerLabel.setToolTipText(tooltip);

        mProxyServerText = new Text(mProxySettingsGroup, SWT.BORDER);
        GridDataBuilder.create(mProxyServerText).hFill().hGrab().vCenter();
        mProxyServerText.addModifyListener(mApplyOnModified);
        mProxyServerText.setToolTipText(tooltip);

        mProxyPortLabel = new Label(mProxySettingsGroup, SWT.NONE);
        GridDataBuilder.create(mProxyPortLabel).hRight().vCenter();
        mProxyPortLabel.setText("HTTP Proxy Port");
        tooltip = "The port of the HTTP proxy server to use. " +
                  "When empty, the default for HTTP or HTTPS is used.";
        mProxyPortLabel.setToolTipText(tooltip);

        mProxyPortText = new Text(mProxySettingsGroup, SWT.BORDER);
        GridDataBuilder.create(mProxyPortText).hFill().hGrab().vCenter();
        mProxyPortText.addModifyListener(mApplyOnModified);
        mProxyPortText.setToolTipText(tooltip);

        mMiscGroup = new Group(shell, SWT.NONE);
        mMiscGroup.setText("Misc");
        GridDataBuilder.create(mMiscGroup).fill().grab().hSpan(2);
        GridLayoutBuilder.create(mMiscGroup).columns(2);

        mForceHttpCheck = new Button(mMiscGroup, SWT.CHECK);
        GridDataBuilder.create(mForceHttpCheck).hFill().hGrab().vCenter().hSpan(2);
        mForceHttpCheck.setText("Force https://... sources to be fetched using http://...");
        mForceHttpCheck.setToolTipText("If you are not able to connect to the official Android repository " +
                "using HTTPS, enable this setting to force accessing it via HTTP.");
        mForceHttpCheck.addSelectionListener(mApplyOnSelected);

        mAskAdbRestartCheck = new Button(mMiscGroup, SWT.CHECK);
        GridDataBuilder.create(mAskAdbRestartCheck).hFill().hGrab().vCenter().hSpan(2);
        mAskAdbRestartCheck.setText("Ask before restarting ADB");
        mAskAdbRestartCheck.setToolTipText("When checked, the user will be asked for permission " +
                "to restart ADB after updating an addon-on package or a tool package.");
        mAskAdbRestartCheck.addSelectionListener(mApplyOnSelected);

        Label filler = new Label(shell, SWT.NONE);
        GridDataBuilder.create(filler).hFill().hGrab();

        createCloseButton();
    }

    @Override
    protected void postCreate() {
        super.postCreate();
        // This tells the controller to load the settings into the page UI.
        mSettingsController.setSettingsPage(this);
    }

    @Override
    protected void close() {
        // Dissociate this page from the controller
        mSettingsController.setSettingsPage(null);
        super.close();
    }


    // -- Start of internal part ----------
    // Hide everything down-below from SWT designer
    //$hide>>$

    /** Loads settings from the given {@link Properties} container and update the page UI. */
    @Override
    public void loadSettings(Properties in_settings) {
        mProxyServerText.setText(in_settings.getProperty(KEY_HTTP_PROXY_HOST, ""));  //$NON-NLS-1$
        mProxyPortText.setText(  in_settings.getProperty(KEY_HTTP_PROXY_PORT, ""));  //$NON-NLS-1$
        mForceHttpCheck.setSelection(Boolean.parseBoolean(in_settings.getProperty(KEY_FORCE_HTTP)));
        mAskAdbRestartCheck.setSelection(Boolean.parseBoolean(in_settings.getProperty(KEY_ASK_ADB_RESTART)));
    }

    /** Called by the application to retrieve settings from the UI and store them in
     * the given {@link Properties} container. */
    @Override
    public void retrieveSettings(Properties out_settings) {
        out_settings.setProperty(KEY_HTTP_PROXY_HOST, mProxyServerText.getText());
        out_settings.setProperty(KEY_HTTP_PROXY_PORT, mProxyPortText.getText());
        out_settings.setProperty(KEY_FORCE_HTTP,
                Boolean.toString(mForceHttpCheck.getSelection()));
        out_settings.setProperty(KEY_ASK_ADB_RESTART,
                Boolean.toString(mAskAdbRestartCheck.getSelection()));
    }

    /**
     * Called by the application to give a callback that the page should invoke when
     * settings must be applied. The page does not apply the settings itself, instead
     * it notifies the application.
     */
    @Override
    public void setOnSettingsChanged(SettingsChangedCallback settingsChangedCallback) {
        mSettingsChangedCallback = settingsChangedCallback;
    }

    /**
     * Callback invoked when user touches one of the settings.
     * There is no "Apply" button, settings are applied immediately as they are changed.
     * Notify the application that settings have changed.
     */
    private void applyNewSettings() {
        if (mSettingsChangedCallback != null) {
            mSettingsChangedCallback.onSettingsChanged(this);
        }
    }

    // End of hiding from SWT Designer
    //$hide<<$
}
