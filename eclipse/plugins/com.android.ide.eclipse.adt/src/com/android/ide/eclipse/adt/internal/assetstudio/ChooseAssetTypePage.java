/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.ide.eclipse.adt.internal.assetstudio;

import com.android.ide.eclipse.adt.internal.project.ProjectChooserHelper;
import com.android.ide.eclipse.adt.internal.resources.ResourceNameValidator;
import com.android.resources.ResourceFolderType;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

public class ChooseAssetTypePage extends WizardPage implements SelectionListener, ModifyListener {
    private Text mProjectText;
    private Button mBrowseButton;
    private ProjectChooserHelper mProjectChooserHelper;
    private IProject mProject;
    private Text mNameText;

    /**
     * Create the wizard.
     */
    public ChooseAssetTypePage() {
        super("chooseAssetTypePage");
        setTitle("Choose Asset Set Type");
        setDescription("Select the type of asset set to create:");
    }

    /**
     * Create contents of the wizard.
     *
     * @param parent the parent composite
     */
    @SuppressWarnings("unused")
    public void createControl(Composite parent) {
        Composite container = new Composite(parent, SWT.NULL);

        setControl(container);
        container.setLayout(new GridLayout(3, false));

        Button launcherIcons = new Button(container, SWT.RADIO);
        launcherIcons.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 3, 1));
        launcherIcons.setSelection(true);
        launcherIcons.setText("Launcher Icons");

        Button menuIcons = new Button(container, SWT.RADIO);
        menuIcons.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 3, 1));
        menuIcons.setEnabled(false);
        menuIcons.setText("Menu Icons");

        Button actionBarIcons = new Button(container, SWT.RADIO);
        actionBarIcons.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 3, 1));
        actionBarIcons.setEnabled(false);
        actionBarIcons.setText("Action Bar Icons (Android 3.0+)");

        Button tabIcons = new Button(container, SWT.RADIO);
        tabIcons.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 3, 1));
        tabIcons.setEnabled(false);
        tabIcons.setText("Tab Icons");

        Button notificationIcons = new Button(container, SWT.RADIO);
        notificationIcons.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 3, 1));
        notificationIcons.setEnabled(false);
        notificationIcons.setText("Notification Icons");

        mProjectChooserHelper = new ProjectChooserHelper(parent.getShell(), null /*filter*/);

        Label separator = new Label(container, SWT.SEPARATOR | SWT.HORIZONTAL);
        GridData gdSeparator = new GridData(SWT.FILL, SWT.CENTER, false, false, 3, 1);
        gdSeparator.heightHint = 20;
        separator.setLayoutData(gdSeparator);

        Label projectLabel = new Label(container, SWT.NONE);
        projectLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        projectLabel.setText("Project:");

        mProjectText = new Text(container, SWT.BORDER);
        mProjectText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        if (mProject != null) {
            mProjectText.setText(mProject.getName());
        }
        mProjectText.addModifyListener(this);

        mBrowseButton = new Button(container, SWT.FLAT);
        mBrowseButton.setText("Browse...");
        mBrowseButton.setToolTipText("Allows you to select the Android project to modify.");

        Label assetLabel = new Label(container, SWT.NONE);
        assetLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        assetLabel.setText("Asset Name:");

        mNameText = new Text(container, SWT.BORDER);
        mNameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        mNameText.setText("ic_launcher");
        mNameText.addModifyListener(this);
        new Label(container, SWT.NONE);
        mBrowseButton.addSelectionListener(this);

        validatePage();
        parent.getDisplay().asyncExec(new Runnable() {
            public void run() {
                mNameText.setFocus();
            }
        });
    }

    void setProject(IProject project) {
        mProject = project;
    }

    IProject getProject() {
        return mProject;
    }

    @Override
    public boolean canFlipToNextPage() {
        return mProject != null;
    }

    public void widgetSelected(SelectionEvent e) {
        if (e.getSource() == mBrowseButton) {
            IJavaProject p = mProjectChooserHelper.chooseJavaProject(mProjectText.getText(),
                    "Please select the target project");
            if (p != null) {
                changeProject(p.getProject());
                mProjectText.setText(mProject.getName());
            }

        }
    }

    public void widgetDefaultSelected(SelectionEvent e) {
    }

    public void modifyText(ModifyEvent e) {
        String project = mProjectText.getText();

        // Is this a valid project?
        IJavaProject[] projects = mProjectChooserHelper.getAndroidProjects(null /*javaModel*/);
        IProject found = null;
        for (IJavaProject p : projects) {
            if (p.getProject().getName().equals(project)) {
                found = p.getProject();
                break;
            }
        }

        if (found != mProject) {
            changeProject(found);
        }

        validatePage();
    }

    private void changeProject(IProject newProject) {
        mProject = newProject;
        validatePage();
    }

    String getOutputName() {
        return mNameText.getText().trim();
    }

    private void validatePage() {
        String error = null;
        //String warning = null;

        if (getProject() == null) {
            error = "Please select an Android project.";
        }

        String outputName = getOutputName();
        if (outputName == null || outputName.length() == 0) {
            error = "Please enter a name";
        } else {
            ResourceNameValidator validator =
                    ResourceNameValidator.create(true, ResourceFolderType.DRAWABLE);
            error = validator.isValid(outputName);
        }

        setPageComplete(error == null);
        if (error != null) {
            setMessage(error, IMessageProvider.ERROR);
        //} else if (warning != null) {
        //    setMessage(warning, IMessageProvider.WARNING);
        } else {
            setErrorMessage(null);
            setMessage(null);
        }
    }
}
