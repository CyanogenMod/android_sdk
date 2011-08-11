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

package com.android.sdkuilib.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * Dialog which collects from the user his/her login and password.
 */
public class AuthenticationDialog extends GridDialog {

    private Text mTxtLogin;

    private Text mTxtPassword;

    private String mTitle;

    private String mMessage;

    protected String mLogin;

    protected String mPassword;

    /**
     * Constructor which retrieves the parent {@link Shell} and the message to
     * be displayed in this dialog.
     *
     * @param parentShell Parent Shell
     * @param title Title of the window.
     * @param message Message the be displayed in this dialog.
     */
    public AuthenticationDialog(Shell parentShell, String title, String message) {
        super(parentShell, 1, false);
        // assign fields
        mTitle = title;
        mMessage = message;
    }

    @Override
    public void createDialogContent(Composite parent) {
        // Configure Dialog
        getShell().setText(mTitle);
        GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
        parent.setLayoutData(data);

        // Upper Composite
        Composite upperComposite = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout(2, false);
        layout.verticalSpacing = 10;
        upperComposite.setLayout(layout);
        data = new GridData(SWT.FILL, SWT.CENTER, true, true);
        upperComposite.setLayoutData(data);

        // add message label
        Label lblMessage = new Label(upperComposite, SWT.WRAP);
        lblMessage.setText(mMessage);
        data = new GridData(SWT.FILL, SWT.CENTER, true, true, 2, 1);
        data.widthHint = 500;
        lblMessage.setLayoutData(data);

        // add user name label and text field
        Label lblUserName = new Label(upperComposite, SWT.NONE);
        lblUserName.setText("Login:");
        data = new GridData(SWT.LEFT, SWT.CENTER, false, false);
        lblUserName.setLayoutData(data);

        mTxtLogin = new Text(upperComposite, SWT.SINGLE | SWT.BORDER);
        data = new GridData(SWT.FILL, SWT.CENTER, true, false);
        mTxtLogin.setLayoutData(data);
        mTxtLogin.setFocus();
        mTxtLogin.addModifyListener(new ModifyListener() {
            public void modifyText(ModifyEvent arg0) {
                mLogin = mTxtLogin.getText();
            }
        });

        // add password label and text field
        Label lblPassword = new Label(upperComposite, SWT.NONE);
        lblPassword.setText("Password:");
        data = new GridData(SWT.LEFT, SWT.CENTER, false, false);
        lblPassword.setLayoutData(data);

        mTxtPassword = new Text(upperComposite, SWT.SINGLE | SWT.PASSWORD | SWT.BORDER);
        data = new GridData(SWT.FILL, SWT.CENTER, true, false);
        mTxtPassword.setLayoutData(data);
        mTxtPassword.addModifyListener(new ModifyListener() {
            public void modifyText(ModifyEvent arg0) {
                mPassword = mTxtPassword.getText();
            }
        });
    }

    /**
     * Retrieves the Login field information
     *
     * @return Login field value or empty String. Return value is never null
     */
    public String getLogin() {
        return mLogin != null ? mLogin : ""; //$NON-NLS-1$
    }

    /**
     * Retrieves the Password field information
     *
     * @return Password field value or empty String. Return value is never null
     */
    public String getPassword() {
        return mPassword != null ? mPassword : ""; //$NON-NLS-1$
    }
}
