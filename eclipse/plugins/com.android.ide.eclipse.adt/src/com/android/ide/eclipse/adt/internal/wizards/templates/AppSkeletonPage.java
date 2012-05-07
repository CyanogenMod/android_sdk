/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.ide.eclipse.adt.internal.wizards.templates;


import com.android.ide.eclipse.adt.AdtPlugin;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

class AppSkeletonPage extends WizardPage implements SelectionListener {
    private final NewProjectWizardState mValues;

    private Button mActionBarToggle;
    private Button mAboutToggle;
    private Button mSettingsToggle;
    private Button mPhoneToggle;
    private Button mTabletToggle;

    private boolean mIgnore;
    private Button mCreateIconToggle;

    AppSkeletonPage(NewProjectWizardState values) {
        super("appSkeletonPage");
        mValues = values;
        setTitle("Configure Application Skeleton");
        setDescription("Select which platforms to target and what to include in the app");
    }

    @SuppressWarnings("unused") // SWT constructors have side effects and aren't unused
    @Override
    public void createControl(Composite parent) {
        Composite container = new Composite(parent, SWT.NULL);
        setControl(container);
        GridLayout gl_container = new GridLayout(2, false);
        gl_container.horizontalSpacing = 10;
        container.setLayout(gl_container);
        Label targetLabel = new Label(container, SWT.NONE);
        targetLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        targetLabel.setText("Target:");
        mPhoneToggle = new Button(container, SWT.CHECK);
        mPhoneToggle.setSelection(true);
        mPhoneToggle.setText("Phones");
        mPhoneToggle.setEnabled(false);
        mPhoneToggle.addSelectionListener(this);
        new Label(container, SWT.NONE);
        mTabletToggle = new Button(container, SWT.CHECK);
        mTabletToggle.setSelection(true);
        mTabletToggle.setText("Tablets");
        mTabletToggle.setEnabled(false);
        mTabletToggle.addSelectionListener(this);
        new Label(container, SWT.NONE);
        new Label(container, SWT.NONE);
        Label uiLabel = new Label(container, SWT.NONE);
        uiLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        uiLabel.setText("User Interface:");
        mActionBarToggle = new Button(container, SWT.CHECK);
        mActionBarToggle.setSelection(true);
        mActionBarToggle.setText("Action Bar");
        mActionBarToggle.setEnabled(false);
        mActionBarToggle.addSelectionListener(this);
        new Label(container, SWT.NONE);
        mSettingsToggle = new Button(container, SWT.CHECK);
        mSettingsToggle.setSelection(true);
        mSettingsToggle.setText("Settings");
        mSettingsToggle.setEnabled(false);
        mSettingsToggle.addSelectionListener(this);
        new Label(container, SWT.NONE);
        mAboutToggle = new Button(container, SWT.CHECK);
        mAboutToggle.setSelection(true);
        mAboutToggle.setText("About");
        mAboutToggle.setEnabled(false);
        mAboutToggle.addSelectionListener(this);

        new Label(container, SWT.NONE);
        mCreateIconToggle = new Button(container, SWT.CHECK);
        mCreateIconToggle.setSelection(mValues.createIcon);
        mCreateIconToggle.setText("Create custom launcher icon");
        mCreateIconToggle.addSelectionListener(this);
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);

        validatePage();
    }

    private void validatePage() {
        IStatus status = null;

        if (status == null || status.getSeverity() != IStatus.ERROR) {
            // Ensure that you're choosing at least one UI target
            if (!mValues.phone && !mValues.tablet) {
                status = new Status(IStatus.ERROR, AdtPlugin.PLUGIN_ID,
                        "You must choose at least one target (tablet or phone)");
            }
        }

        setPageComplete(status == null || status.getSeverity() != IStatus.ERROR);
        if (status != null) {
            setMessage(status.getMessage(),
                    status.getSeverity() == IStatus.ERROR
                        ? IMessageProvider.ERROR : IMessageProvider.WARNING);
        } else {
            setErrorMessage(null);
            setMessage(null);
        }
    }

    // ---- Implements SelectionListener ----

    @Override
    public void widgetSelected(SelectionEvent e) {
        if (mIgnore) {
            return;
        }

        Object source = e.getSource();
        if (source == mPhoneToggle) {
            mValues.phone = mPhoneToggle.getSelection();
        } else if (source == mTabletToggle) {
            mValues.tablet = mTabletToggle.getSelection();
        } else if (source == mCreateIconToggle) {
            mValues.createIcon = mCreateIconToggle.getSelection();
        }

        validatePage();
    }

    @Override
    public void widgetDefaultSelected(SelectionEvent e) {
    }
}
