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


import static com.android.ide.eclipse.adt.AdtUtils.extractClassName;
import static com.android.ide.eclipse.adt.internal.wizards.templates.NewTemplatePage.WIZARD_PAGE_WIDTH;

import com.android.annotations.Nullable;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.AdtUtils;
import com.android.ide.eclipse.adt.internal.editors.IconFactory;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.ide.eclipse.adt.internal.wizards.newproject.ApplicationInfoPage;
import com.android.ide.eclipse.adt.internal.wizards.newproject.ProjectNamePage;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.google.common.collect.Maps;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.fieldassist.ControlDecoration;
import org.eclipse.jface.fieldassist.FieldDecoration;
import org.eclipse.jface.fieldassist.FieldDecorationRegistry;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.ast.libs.org.parboiled.google.collect.Lists;

/**
 * First wizard page in the "New Project From Template" wizard
 */
public class NewProjectPage extends WizardPage
        implements ModifyListener, SelectionListener, FocusListener {
    private static final String SAMPLE_PACKAGE_PREFIX = "com.example."; //$NON-NLS-1$
    /** Suffix added by default to activity names */
    static final String ACTIVITY_NAME_SUFFIX = "Activity";              //$NON-NLS-1$
    /** Prefix added to default layout names */
    static final String LAYOUT_NAME_PREFIX = "activity_";               //$NON-NLS-1$
    private static final int INITIAL_MIN_SDK = 8;

    private final NewProjectWizardState mValues;
    private Map<String, Integer> mMinNameToApi;

    private Text mProjectText;
    private Text mPackageText;
    private Text mApplicationText;
    private Combo mMinSdkCombo;
    private Button mCompatToggle;

    private boolean mIgnore;
    private Combo mBuildSdkCombo;
    private Button mChooseSdkButton;
    private Button mCustomIconToggle;
    private Button mLibraryToggle;
    private Label mHelpIcon;
    private Label mTipLabel;

    private ControlDecoration mApplicationDec;
    private ControlDecoration mProjectDec;
    private ControlDecoration mPackageDec;
    private ControlDecoration mBuildTargetDec;
    private ControlDecoration mMinSdkDec;

    NewProjectPage(NewProjectWizardState values) {
        super("newAndroidApp"); //$NON-NLS-1$
        mValues = values;
        setTitle("New Android Application");
        setDescription("Creates a new Android Application");
    }

    @SuppressWarnings("unused") // SWT constructors have side effects and aren't unused
    @Override
    public void createControl(Composite parent) {
        Composite container = new Composite(parent, SWT.NULL);
        setControl(container);
        GridLayout gl_container = new GridLayout(4, false);
        gl_container.horizontalSpacing = 10;
        container.setLayout(gl_container);

        Label applicationLabel = new Label(container, SWT.NONE);
        applicationLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 2, 1));
        applicationLabel.setText("Application Name:");

        mApplicationText = new Text(container, SWT.BORDER);
        mApplicationText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        mApplicationText.addModifyListener(this);
        mApplicationText.addFocusListener(this);
        mApplicationDec = createFieldDecoration(mApplicationText,
                "The application name is shown in the Play Store, as well as in the " +
                "Manage Application list in Settings.");

        Label projectLabel = new Label(container, SWT.NONE);
        projectLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 2, 1));
        projectLabel.setText("Project Name:");
        mProjectText = new Text(container, SWT.BORDER);
        mProjectText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        mProjectText.addModifyListener(this);
        mProjectText.addFocusListener(this);
        mProjectDec = createFieldDecoration(mProjectText,
                "The project name is only used by Eclipse, but must be unique within the " +
                "workspace. This can typically be the same as the application name.");

        Label packageLabel = new Label(container, SWT.NONE);
        packageLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 2, 1));
        packageLabel.setText("Package Name:");

        mPackageText = new Text(container, SWT.BORDER);
        mPackageText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        mPackageText.addModifyListener(this);
        mPackageText.addFocusListener(this);
        mPackageDec = createFieldDecoration(mPackageText,
                "The package name must be a unique identifier for your application.\n" +
                "It is typically not shown to users, but it *must* stay the same " +
                "for the lifetime of your application; it is how multiple versions " +
                "of the same application are considered the \"same app\".\nThis is " +
                "typically the reverse domain name of your organization plus one or " +
                "more application identifiers, and it must be a valid Java package " +
                "name.");
        new Label(container, SWT.NONE);

        new Label(container, SWT.NONE);
        new Label(container, SWT.NONE);
        new Label(container, SWT.NONE);

        Label buildSdkLabel = new Label(container, SWT.NONE);
        buildSdkLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 2, 1));
        buildSdkLabel.setText("Build SDK:");

        mBuildSdkCombo = new Combo(container, SWT.READ_ONLY);
        mBuildSdkCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        IAndroidTarget[] targets = getCompilationTargets();
        mMinNameToApi = Maps.newHashMap();
        List<String> labels = new ArrayList<String>(targets.length);
        for (IAndroidTarget target : targets) {
            String targetLabel = String.format("%1$s (API %2$s)", target.getFullName(),
                    target.getVersion().getApiString());
            labels.add(targetLabel);
            mMinNameToApi.put(targetLabel, target.getVersion().getApiLevel());

        }
        mBuildSdkCombo.setData(targets);
        mBuildSdkCombo.setItems(labels.toArray(new String[labels.size()]));

        // Pick most recent platform
        List<String> codeNames = Lists.newArrayList();
        int selectIndex = -1;
        for (int i = 0, n = targets.length; i < n; i++) {
            IAndroidTarget target = targets[i];
            AndroidVersion version = target.getVersion();
            int apiLevel = version.getApiLevel();
            if (version.isPreview()) {
                String codeName = version.getCodename();
                String targetLabel = codeName + " Preview";
                codeNames.add(targetLabel);
                mMinNameToApi.put(targetLabel, apiLevel);
            } else if (target.isPlatform()
                    && (mValues.target == null ||
                        apiLevel > mValues.target.getVersion().getApiLevel())) {
                mValues.target = target;
                selectIndex = i;
            }
        }
        if (selectIndex != -1) {
            mBuildSdkCombo.select(selectIndex);
        }

        mBuildSdkCombo.addSelectionListener(this);
        mBuildSdkCombo.addFocusListener(this);
        mBuildTargetDec = createFieldDecoration(mBuildSdkCombo,
                "Choose a target API to compile your code against. This is typically the most " +
                "recent version, or the first version that supports all the APIs you want to " +
                "directly access");


        mChooseSdkButton = new Button(container, SWT.NONE);
        mChooseSdkButton.setText("Choose...");
        mChooseSdkButton.addSelectionListener(this);
        mChooseSdkButton.setEnabled(false);

        Label minSdkLabel = new Label(container, SWT.NONE);
        minSdkLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 2, 1));
        minSdkLabel.setText("Minimum Required SDK:");

        mMinSdkCombo = new Combo(container, SWT.READ_ONLY);
        mMinSdkCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

        labels = new ArrayList<String>(24);
        for (String label : AdtUtils.getKnownVersions()) {
            labels.add(label);
        }
        assert labels.size() >= 15; // *Known* versions to ADT, not installed/available versions
        for (String codeName : codeNames) {
            labels.add(codeName);
        }
        String[] versions = labels.toArray(new String[labels.size()]);
        mMinSdkCombo.setItems(versions);
        if (mValues.target != null && mValues.target.getVersion().isPreview()) {
            mValues.minSdk = mValues.target.getVersion().getCodename();
            mMinSdkCombo.setText(mValues.minSdk);
            mValues.iconState.minSdk = mValues.target.getVersion().getApiLevel();
        } else {
            mMinSdkCombo.select(INITIAL_MIN_SDK - 1);
            mValues.minSdk = Integer.toString(INITIAL_MIN_SDK);
            mValues.iconState.minSdk = INITIAL_MIN_SDK;
        }
        mMinSdkCombo.addSelectionListener(this);
        mMinSdkCombo.addFocusListener(this);
        mMinSdkDec = createFieldDecoration(mMinSdkCombo,
                "Choose the lowest version of Android that your application will support. Lower " +
                "API levels target more devices, but means fewer features are available. By " +
                "targeting API 8 and later, you reach approximately 93% of the market.");

        new Label(container, SWT.NONE);
        new Label(container, SWT.NONE);
        new Label(container, SWT.NONE);

        mCompatToggle = new Button(container, SWT.CHECK);
        mCompatToggle.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
        mCompatToggle.setSelection(true);
        mCompatToggle.setEnabled(false);
        mCompatToggle.setText("Include compatibility code");
        mCompatToggle.addSelectionListener(this);
        new Label(container, SWT.NONE);

        new Label(container, SWT.NONE);
        new Label(container, SWT.NONE);
        new Label(container, SWT.NONE);

        mCustomIconToggle = new Button(container, SWT.CHECK);
        mCustomIconToggle.setSelection(true);
        mCustomIconToggle.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 4, 1));
        mCustomIconToggle.setText("Create custom launcher icon");
        mCustomIconToggle.setSelection(mValues.createIcon);
        mCustomIconToggle.addSelectionListener(this);

        mLibraryToggle = new Button(container, SWT.CHECK);
        mLibraryToggle.setSelection(true);
        mLibraryToggle.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 4, 1));
        mLibraryToggle.setText("Mark this project as a library");
        mLibraryToggle.setSelection(mValues.isLibrary);
        mLibraryToggle.addSelectionListener(this);

        Label label = new Label(container, SWT.SEPARATOR | SWT.HORIZONTAL);
        label.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 4, 1));

        mHelpIcon = new Label(container, SWT.NONE);
        mHelpIcon.setLayoutData(new GridData(SWT.RIGHT, SWT.TOP, false, false, 1, 1));
        Image icon = IconFactory.getInstance().getIcon("quickfix");
        mHelpIcon.setImage(icon);
        mHelpIcon.setVisible(false);

        mTipLabel = new Label(container, SWT.WRAP);
        mTipLabel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1));

        // Reserve space for 4 lines
        mTipLabel.setText("\n\n\n\n"); //$NON-NLS-1$

        // Reserve enough width to accommodate the various wizard pages up front
        // (since they are created lazily, and we don't want the wizard to dynamically
        // resize itself for small size adjustments as each successive page is slightly
        // larger)
        Label dummy = new Label(container, SWT.NONE);
        GridData data = new GridData();
        data.horizontalSpan = 4;
        data.widthHint = WIZARD_PAGE_WIDTH;
        dummy.setLayoutData(data);

    }

    private IAndroidTarget[] getCompilationTargets() {
        IAndroidTarget[] targets = Sdk.getCurrent().getTargets();
        List<IAndroidTarget> list = new ArrayList<IAndroidTarget>();

        for (IAndroidTarget target : targets) {
            if (target.isPlatform() == false &&
                    (target.getOptionalLibraries() == null ||
                            target.getOptionalLibraries().length == 0)) {
                continue;
            }
            list.add(target);
        }

        return list.toArray(new IAndroidTarget[list.size()]);
    }

    private ControlDecoration createFieldDecoration(Control control, String description) {
        ControlDecoration dec = new ControlDecoration(control, SWT.LEFT);
        dec.setMarginWidth(2);
        FieldDecoration errorFieldIndicator = FieldDecorationRegistry.getDefault().
           getFieldDecoration(FieldDecorationRegistry.DEC_INFORMATION);
        dec.setImage(errorFieldIndicator.getImage());
        dec.setDescriptionText(description);
        control.setToolTipText(description);

        return dec;
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);

        // DURING DEVELOPMENT ONLY
        //if (assertionsEnabled()) {
        //    String uniqueProjectName = AdtUtils.getUniqueProjectName("Test", "");
        //    mProjectText.setText(uniqueProjectName);
        //    mPackageText.setText("test.pkg");
        //}

        validatePage();
    }

    // ---- Implements ModifyListener ----

    @Override
    public void modifyText(ModifyEvent e) {
        if (mIgnore) {
            return;
        }

        Object source = e.getSource();
        if (source == mProjectText) {
            mValues.projectName = mProjectText.getText();
            mValues.projectModified = true;

            try {
                mIgnore = true;
                if (!mValues.applicationModified) {
                    mValues.applicationName = mValues.projectName;
                    mApplicationText.setText(mValues.projectName);
                }
                updateActivityNames(mValues.projectName);
            } finally {
                mIgnore = false;
            }
            suggestPackage(mValues.projectName);
        } else if (source == mPackageText) {
            mValues.packageName = mPackageText.getText();
            mValues.packageModified = true;
        } else if (source == mApplicationText) {
            mValues.applicationName = mApplicationText.getText();
            mValues.applicationModified = true;

            try {
                mIgnore = true;
                if (!mValues.projectModified) {
                    mValues.projectName = mValues.applicationName;
                    mProjectText.setText(mValues.applicationName);
                }
                updateActivityNames(mValues.applicationName);
            } finally {
                mIgnore = false;
            }
            suggestPackage(mValues.applicationName);
        }

        validatePage();
    }

    private void updateActivityNames(String name) {
        try {
            mIgnore = true;
            if (!mValues.activityNameModified) {
                mValues.activityName = extractClassName(name) + ACTIVITY_NAME_SUFFIX;
            }
            if (!mValues.activityTitleModified) {
                mValues.activityTitle = name;
            }
        } finally {
            mIgnore = false;
        }
    }

    // ---- Implements SelectionListener ----

    @Override
    public void widgetSelected(SelectionEvent e) {
        if (mIgnore) {
            return;
        }

        Object source = e.getSource();
        if (source == mChooseSdkButton) {
            // TODO: Open SDK chooser
            assert false;
        } else if (source == mMinSdkCombo) {
            mValues.minSdk = getSelectedMinSdk();
            // If higher than build target, adjust build target
            // TODO: implement

            Integer minSdk = mMinNameToApi.get(mValues.minSdk);
            if (minSdk == null) {
                try {
                    minSdk = Integer.parseInt(mValues.minSdk);
                } catch (NumberFormatException nufe) {
                    minSdk = 1;
                }
            }
            mValues.iconState.minSdk = minSdk.intValue();
            mValues.minSdkLevel = minSdk.intValue();
        } else if (source == mBuildSdkCombo) {
            mValues.target = getSelectedBuildTarget();

            // If lower than min sdk target, adjust min sdk target
            if (mValues.target.getVersion().isPreview()) {
                mValues.minSdk = mValues.target.getVersion().getCodename();
                try {
                    mIgnore = true;
                    mMinSdkCombo.setText(mValues.minSdk);
                } finally {
                    mIgnore = false;
                }
            } else {
                String minSdk = mValues.minSdk;
                int buildApiLevel = mValues.target.getVersion().getApiLevel();
                if (minSdk != null && !minSdk.isEmpty()
                        && Character.isDigit(minSdk.charAt(0))
                        && buildApiLevel < Integer.parseInt(minSdk)) {
                    mValues.minSdk = Integer.toString(buildApiLevel);
                    try {
                        mIgnore = true;
                        setSelectedMinSdk(buildApiLevel);
                    } finally {
                        mIgnore = false;
                    }
                }
            }
        } else if (source == mCustomIconToggle) {
            mValues.createIcon = mCustomIconToggle.getSelection();
        } else if (source == mLibraryToggle) {
            mValues.isLibrary = mLibraryToggle.getSelection();
        }

        validatePage();
    }

    private String getSelectedMinSdk() {
        // If you're using a preview build, such as android-JellyBean, you have
        // to use the codename, e.g. JellyBean, as the minimum SDK as well.
        IAndroidTarget buildTarget = getSelectedBuildTarget();
        if (buildTarget != null && buildTarget.getVersion().isPreview()) {
            return buildTarget.getVersion().getCodename();
        }

        // +1: First API level (at index 0) is 1
        return Integer.toString(mMinSdkCombo.getSelectionIndex() + 1);
    }

    private void setSelectedMinSdk(int api) {
        mMinSdkCombo.select(api - 1); // -1: First API level (at index 0) is 1
    }

    @Nullable
    private IAndroidTarget getSelectedBuildTarget() {
        IAndroidTarget[] targets = (IAndroidTarget[]) mBuildSdkCombo.getData();
        int index = mBuildSdkCombo.getSelectionIndex();
        if (index >= 0 && index < targets.length) {
            return targets[index];
        } else {
            return null;
        }
    }

    private void suggestPackage(String original) {
        if (!mValues.packageModified) {
            // Create default package name
            StringBuilder sb = new StringBuilder();
            sb.append(SAMPLE_PACKAGE_PREFIX);
            appendPackage(sb, original);

            mValues.packageName = sb.toString();
            try {
                mIgnore = true;
                mPackageText.setText(mValues.packageName);
            } finally {
                mIgnore = false;
            }
        }
    }

    private static void appendPackage(StringBuilder sb, String string) {
        for (int i = 0, n = string.length(); i < n; i++) {
            char c = string.charAt(i);
            if (i == 0 && Character.isJavaIdentifierStart(c)
                    || i != 0 && Character.isJavaIdentifierPart(c)) {
                sb.append(Character.toLowerCase(c));
            } else if ((c == '.' || c == ' ')
                    && (sb.length() > 0 && sb.charAt(sb.length() - 1) != '.')) {
                sb.append('.');
            } else if (c == '-') {
                sb.append('_');
            }
        }
    }

    @Override
    public void widgetDefaultSelected(SelectionEvent e) {
    }

    // ---- Implements FocusListener ----

    @Override
    public void focusGained(FocusEvent e) {
        Object source = e.getSource();
        String tip = "";
        if (source == mApplicationText) {
            tip = mApplicationDec.getDescriptionText();
        } else if (source == mProjectText) {
            tip = mProjectDec.getDescriptionText();
        } else if (source == mBuildSdkCombo) {
            tip = mBuildTargetDec.getDescriptionText();
        } else if (source == mMinSdkCombo) {
            tip = mMinSdkDec.getDescriptionText();
        } else if (source == mPackageText) {
            tip = mPackageDec.getDescriptionText();
            if (mPackageText.getText().startsWith(SAMPLE_PACKAGE_PREFIX)) {
                int length = SAMPLE_PACKAGE_PREFIX.length();
                if (mPackageText.getText().length() > length
                        && SAMPLE_PACKAGE_PREFIX.endsWith(".")) { //$NON-NLS-1$
                    length--;
                }
                mPackageText.setSelection(0, length);
            }
        }
        mTipLabel.setText(tip);
        mHelpIcon.setVisible(tip.length() > 0);
    }

    @Override
    public void focusLost(FocusEvent e) {
        mTipLabel.setText("");
        mHelpIcon.setVisible(false);
    }

    // Validation

    private void validatePage() {
        IStatus status = mValues.template.validateTemplate();
        if (status != null && !status.isOK()) {
            updateDecorator(mApplicationDec, null, true);
            updateDecorator(mPackageDec, null, true);
            updateDecorator(mProjectDec, null, true);
        } else {
            IStatus appStatus = validateAppName();
            if (appStatus != null && (status == null
                    || appStatus.getSeverity() > status.getSeverity())) {
                status = appStatus;
            }

            IStatus projectStatus = validateProjectName();
            if (projectStatus != null && (status == null
                    || projectStatus.getSeverity() > status.getSeverity())) {
                status = projectStatus;
            }

            IStatus packageStatus = validatePackageName();
            if (packageStatus != null && (status == null
                    || packageStatus.getSeverity() > status.getSeverity())) {
                status = packageStatus;
            }

            if (status == null || status.getSeverity() != IStatus.ERROR) {
                if (mValues.target == null) {
                    status = new Status(IStatus.WARNING, AdtPlugin.PLUGIN_ID,
                            "Select an Android build target version");
                }
            }

            if (status == null || status.getSeverity() != IStatus.ERROR) {
                if (mValues.minSdk == null || mValues.minSdk.isEmpty()) {
                    status = new Status(IStatus.WARNING, AdtPlugin.PLUGIN_ID,
                            "Select a minimum SDK version");
                } else {
                    AndroidVersion version = mValues.target.getVersion();
                    if (version.isPreview()) {
                        if (version.getCodename().equals(mValues.minSdk) == false) {
                            status = new Status(IStatus.ERROR, AdtPlugin.PLUGIN_ID,
                            "Preview platforms require the min SDK version to match their codenames.");
                       }
                    } else if (mValues.target.getVersion().compareTo(
                            mValues.minSdkLevel, mValues.minSdk) < 0) {
                        status = new Status(IStatus.WARNING, AdtPlugin.PLUGIN_ID,
                            "The minimum SDK version is higher than the build target version");
                    }
                }
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

    private IStatus validateAppName() {
        String appName = mValues.applicationName;
        IStatus status = null;
        if (appName == null || appName.isEmpty()) {
            status = new Status(IStatus.ERROR, AdtPlugin.PLUGIN_ID,
                    "Enter an application name (shown in launcher)");
        } else if (Character.isLowerCase(mValues.applicationName.charAt(0))) {
            status = new Status(IStatus.WARNING, AdtPlugin.PLUGIN_ID,
                    "The application name for most apps begins with an uppercase letter");
        }

        updateDecorator(mApplicationDec, status, true);

        return status;
    }

    private IStatus validateProjectName() {
        IStatus status = ProjectNamePage.validateProjectName(mValues.projectName);
        updateDecorator(mProjectDec, status, true);

        return status;
    }

    private IStatus validatePackageName() {

        IStatus status;
        if (mValues.packageName == null || mValues.packageName.startsWith(SAMPLE_PACKAGE_PREFIX)) {
            if (mValues.packageName != null
                    && !mValues.packageName.equals(SAMPLE_PACKAGE_PREFIX)) {
                status = ApplicationInfoPage.validatePackage(mValues.packageName);
                if (status == null || status.isOK()) {
                    status = new Status(IStatus.WARNING, AdtPlugin.PLUGIN_ID,
                        String.format("The prefix '%1$s' is meant as a placeholder and should " +
                                      "not be used", SAMPLE_PACKAGE_PREFIX));
                }
            } else {
                status = new Status(IStatus.ERROR, AdtPlugin.PLUGIN_ID,
                        "Package name must be specified.");
            }
        } else {
            status = ApplicationInfoPage.validatePackage(mValues.packageName);
        }

        updateDecorator(mPackageDec, status, true);

        return status;
    }

    private void updateDecorator(ControlDecoration decorator, IStatus status, boolean hasInfo) {
        if (hasInfo) {
            int severity = status != null ? status.getSeverity() : IStatus.OK;
            setDecoratorType(decorator, severity);
        } else {
            if (status == null || status.isOK()) {
                decorator.hide();
            } else {
                decorator.show();
            }
        }
    }

    private void setDecoratorType(ControlDecoration decorator, int severity) {
        String id;
        if (severity == IStatus.ERROR) {
            id = FieldDecorationRegistry.DEC_ERROR;
        } else if (severity == IStatus.WARNING) {
            id = FieldDecorationRegistry.DEC_WARNING;
        } else {
            id = FieldDecorationRegistry.DEC_INFORMATION;
        }
        FieldDecoration errorFieldIndicator = FieldDecorationRegistry.getDefault().
                getFieldDecoration(id);
        decorator.setImage(errorFieldIndicator.getImage());
    }
}
