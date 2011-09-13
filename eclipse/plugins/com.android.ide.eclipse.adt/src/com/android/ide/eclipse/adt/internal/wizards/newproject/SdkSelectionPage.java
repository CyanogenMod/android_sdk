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
package com.android.ide.eclipse.adt.internal.wizards.newproject;

import com.android.ide.common.sdk.LoadStatus;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.ide.eclipse.adt.internal.sdk.Sdk.ITargetChangeListener;
import com.android.ide.eclipse.adt.internal.wizards.newproject.NewProjectWizardState.Mode;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkConstants;
import com.android.sdkuilib.internal.widgets.SdkTargetSelector;

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;

import java.io.File;
import java.util.Collections;
import java.util.List;

/** A page in the New Project wizard where you select the target SDK */
class SdkSelectionPage extends WizardPage implements ITargetChangeListener {
    private final NewProjectWizardState mValues;
    private boolean mIgnore;
    private SdkTargetSelector mSdkTargetSelector;

    /**
     * Create the wizard.
     */
    SdkSelectionPage(NewProjectWizardState values) {
        super("sdkSelection"); //$NON-NLS-1$
        mValues = values;

        setTitle("Select Build Target");
        AdtPlugin.getDefault().addTargetListener(this);
    }

    @Override
    public void dispose() {
        AdtPlugin.getDefault().removeTargetListener(this);
        super.dispose();
    }

    /**
     * Create contents of the wizard.
     */
    public void createControl(Composite parent) {
        Group group = new Group(parent, SWT.SHADOW_ETCHED_IN);
        // Layout has 1 column
        group.setLayout(new GridLayout());
        group.setLayoutData(new GridData(GridData.FILL_BOTH));
        group.setFont(parent.getFont());
        group.setText("Build Target");

        // The selector is created without targets. They are added below in the change listener.
        mSdkTargetSelector = new SdkTargetSelector(group, null);

        mSdkTargetSelector.setSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (mIgnore) {
                    return;
                }

                mValues.target = mSdkTargetSelector.getSelected();
                mValues.targetModifiedByUser = true;
                onSdkTargetModified();
                validatePage();
            }
        });

        onSdkLoaded();

        setControl(group);
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (mValues.mode == Mode.SAMPLE) {
            setDescription("Choose an SDK to select a sample from");
        } else {
            setDescription("Choose an SDK to target");
        }
        try {
            mIgnore = true;
            if (mValues.target != null) {
                mSdkTargetSelector.setSelection(mValues.target);
            }
        } finally {
            mIgnore = false;
        }

        validatePage();
    }

    @Override
    public boolean isPageComplete() {
        // Ensure that the Finish button isn't enabled until
        // the user has reached and completed this page
        if (mValues.target == null) {
            return false;
        }

        return super.isPageComplete();
    }

    /**
     * Called when an SDK target is modified.
     *
     * Also changes the minSdkVersion field to reflect the sdk api level that has
     * just been selected.
     */
    private void onSdkTargetModified() {
        if (mIgnore) {
            return;
        }

        IAndroidTarget target = mValues.target;

        // Update the minimum SDK text field?
        // We do if one of two conditions are met:
        if (target != null) {
            boolean setMinSdk = false;
            AndroidVersion version = target.getVersion();
            int apiLevel = version.getApiLevel();
            // 1. Has the user not manually edited the SDK field yet? If so, keep
            //    updating it to the selected value.
            if (!mValues.minSdkModifiedByUser) {
                setMinSdk = true;
            } else {
                // 2. Is the API level set to a higher level than the newly selected
                //    target SDK? If so, change it down to the new lower value.
                String s = mValues.minSdk;
                if (s.length() > 0) {
                    try {
                        int currentApi = Integer.parseInt(s);
                        if (currentApi > apiLevel) {
                            setMinSdk = true;
                        }
                    } catch (NumberFormatException nfe) {
                        // User may have typed something invalid -- ignore
                    }
                }
            }
            if (setMinSdk) {
                String minSdk;
                if (version.isPreview()) {
                    minSdk = version.getCodename();
                } else {
                    minSdk = Integer.toString(apiLevel);
                }
                mValues.minSdk = minSdk;
            }
        }

        loadSamplesForTarget(target);
    }

    /**
     * Updates the list of all samples for the given target SDK.
     * The list is stored in mSamplesPaths as absolute directory paths.
     * The combo is recreated to match this.
     */
    private void loadSamplesForTarget(IAndroidTarget target) {
        // Keep the name of the old selection (if there were any samples)
        File previouslyChosenSample = mValues.chosenSample;

        mValues.samples.clear();
        mValues.chosenSample = null;

        if (target != null) {
            // Get the sample root path and recompute the list of samples
            String samplesRootPath = target.getPath(IAndroidTarget.SAMPLES);

            mValues.samplesDir = new File(samplesRootPath);
            findSamplesManifests(mValues.samplesDir, mValues.samples);

            if (mValues.samples.size() == 0) {
                return;
            } else {
                Collections.sort(mValues.samples);
            }

            // Recompute the description of each sample (the relative path
            // to the sample root). Also try to find the old selection.
            if (previouslyChosenSample != null) {
                String previouslyChosenName = previouslyChosenSample.getName();
                for (int i = 0, n = mValues.samples.size(); i < n; i++) {
                    File file = mValues.samples.get(i);
                    if (file.getName().equals(previouslyChosenName)) {
                        mValues.chosenSample = file;
                        break;
                    }
                }
            }
        }
    }

    /**
     * Recursively find potential sample directories under the given directory.
     * Actually lists any directory that contains an android manifest.
     * Paths found are added the samplesPaths list.
     */
    private void findSamplesManifests(File samplesDir, List<File> samplesPaths) {
        if (!samplesDir.isDirectory()) {
            return;
        }

        for (File f : samplesDir.listFiles()) {
            if (f.isDirectory()) {
                // Assume this is a sample if it contains an android manifest.
                File manifestFile = new File(f, SdkConstants.FN_ANDROID_MANIFEST_XML);
                if (manifestFile.isFile()) {
                    samplesPaths.add(f);
                }

                // Recurse in the project, to find embedded tests sub-projects
                // We can however skip this recursion for known android sub-dirs that
                // can't have projects, namely for sources, assets and resources.
                String leaf = f.getName();
                if (!SdkConstants.FD_SOURCES.equals(leaf) &&
                        !SdkConstants.FD_ASSETS.equals(leaf) &&
                        !SdkConstants.FD_RES.equals(leaf)) {
                    findSamplesManifests(f, samplesPaths);
                }
            }
        }
    }

    private void validatePage() {
        String error = null;

        if (AdtPlugin.getDefault().getSdkLoadStatus() == LoadStatus.LOADING) {
            error = "The SDK is still loading; please wait.";
        }

        if (error == null && mValues.target == null) {
            error = "An SDK Target must be specified.";
        }

        if (error == null && mValues.mode == Mode.SAMPLE) {
            // Make sure this SDK target contains samples
            if (mValues.samples == null || mValues.samples.size() == 0) {
                error = "This target has no samples. Please select another target.";
            }
        }

        // -- update UI & enable finish if there's no error
        setPageComplete(error == null);
        if (error != null) {
            setMessage(error, IMessageProvider.ERROR);
        } else {
            setErrorMessage(null);
            setMessage(null);
        }
    }

    // ---- Implements ITargetChangeListener ----
    public void onSdkLoaded() {
        if (mSdkTargetSelector == null) {
            return;
        }

        // Update the sdk target selector with the new targets

        // get the targets from the sdk
        IAndroidTarget[] targets = null;
        if (Sdk.getCurrent() != null) {
            targets = Sdk.getCurrent().getTargets();
        }
        mSdkTargetSelector.setTargets(targets);

        // If there's only one target, select it.
        // This will invoke the selection listener on the selector defined above.
        if (targets != null && targets.length == 1) {
            mValues.target = targets[0];
            mSdkTargetSelector.setSelection(mValues.target);
            onSdkTargetModified();
        } else if (targets != null) {
            // Pick the highest available platform by default (see issue #17505
            // for related discussion.)
            IAndroidTarget initialTarget = null;
            for (IAndroidTarget target : targets) {
                if (target.isPlatform()
                        && !target.getVersion().isPreview()
                        && (initialTarget == null ||
                                target.getVersion().getApiLevel() >
                                    initialTarget.getVersion().getApiLevel())) {
                    initialTarget = target;
                }
            }
            if (initialTarget != null) {
                mValues.target = initialTarget;
                try {
                    mIgnore = true;
                    mSdkTargetSelector.setSelection(mValues.target);
                } finally {
                    mIgnore = false;
                }
                onSdkTargetModified();
            }
        }

        validatePage();
    }

    public void onProjectTargetChange(IProject changedProject) {
        // Ignore
    }

    public void onTargetLoaded(IAndroidTarget target) {
        // Ignore
    }
}
