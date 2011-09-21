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

package com.android.ide.eclipse.adt.internal.welcome;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.preferences.AdtPrefs;
import com.android.ide.eclipse.adt.internal.sdk.AdtConsoleSdkLog;
import com.android.sdkstats.DdmsPreferenceStore;
import com.android.sdkuilib.internal.repository.sdkman2.AdtUpdateDialog;
import com.android.util.Pair;

import org.eclipse.jface.wizard.Wizard;

import java.io.File;

/**
 * Wizard shown on first start for new users: configure SDK location, accept or
 * reject usage data collection, etc
 */
public class WelcomeWizard extends Wizard {
    private final DdmsPreferenceStore mStore;
    private WelcomeWizardPage mWelcomePage;
    private UsagePermissionPage mUsagePage;

    /**
     * Creates a new {@link WelcomeWizard}
     *
     * @param store preferences for usage statistics collection etc
     */
    public WelcomeWizard(DdmsPreferenceStore store) {
        mStore = store;

        setWindowTitle("Welcome to Android Development");
    }

    @Override
    public void addPages() {
        mWelcomePage = new WelcomeWizardPage();
        addPage(mWelcomePage);

        // It's possible that the user has already run the command line tools
        // such as ddms and has agreed to usage statistics collection, but has never
        // run ADT which is why the wizard was opened. No need to ask again.
        if (!mStore.isPingOptIn()) {
            mUsagePage = new UsagePermissionPage();
            addPage(mUsagePage);
        }
    }

    @Override
    public boolean performFinish() {
        if (mUsagePage != null) {
            boolean isUsageCollectionApproved = mUsagePage.isUsageCollectionApproved();
            DdmsPreferenceStore store = new DdmsPreferenceStore();
            store.setPingOptIn(isUsageCollectionApproved);
        }

        // Read out wizard settings immediately; we will perform the actual work
        // after the wizard window has been taken down and it's too late to read the
        // settings then
        final File path = mWelcomePage.getPath();
        final boolean installCommon = mWelcomePage.isInstallCommon();
        final boolean installLatest = mWelcomePage.isInstallLatest();
        final boolean createNew = mWelcomePage.isCreateNew();

        // Perform installation asynchronously since it takes a while.
        getShell().getDisplay().asyncExec(new Runnable() {
            public void run() {
                if (createNew) {
                    try {
                        if (installCommon) {
                            installSdk(path, 7);
                        }
                        if (installLatest) {
                            installSdk(path, AdtUpdateDialog.USE_MAX_REMOTE_API_LEVEL);
                        }
                    } catch (Exception e) {
                        AdtPlugin.logAndPrintError(e, "ADT Welcome Wizard", "Installation failed");
                    }
                }

                // Set SDK path after installation since this will trigger a SDK refresh.
                AdtPrefs.getPrefs().setSdkLocation(path);
            }
        });

        // The wizard always succeeds, even if installation fails or is aborted
        return true;
    }

    /**
     * Trigger the install window. It will connect to the repository, display
     * a confirmation window showing which packages are selected for install
     * and display a progress dialog during installation.
     */
    private boolean installSdk(File path, int apiLevel) {
        if (!path.isDirectory()) {
            if (!path.mkdirs()) {
                AdtPlugin.logAndPrintError(null, "ADT Welcome Wizard",
                        "Failed to create directory %1$s",
                        path.getAbsolutePath());
                return false;
            }
        }

        AdtUpdateDialog updater = new AdtUpdateDialog(
                AdtPlugin.getDisplay().getActiveShell(),
                new AdtConsoleSdkLog(),
                path.getAbsolutePath());
        // Note: we don't have to specify tools & platform-tools since they
        // are required dependencies of any platform.
        Pair<Boolean, File> result = updater.installNewSdk(apiLevel);

        if (!result.getFirst().booleanValue()) {
            AdtPlugin.printErrorToConsole("Failed to install Android SDK.");
            return false;
        }

        return true;
    }
}
