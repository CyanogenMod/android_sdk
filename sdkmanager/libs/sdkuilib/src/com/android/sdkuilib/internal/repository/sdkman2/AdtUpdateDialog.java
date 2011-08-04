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

package com.android.sdkuilib.internal.repository.sdkman2;


import com.android.sdklib.AndroidVersion;
import com.android.sdklib.ISdkLog;
import com.android.sdklib.internal.repository.ExtraPackage;
import com.android.sdklib.internal.repository.Package;
import com.android.sdklib.internal.repository.PlatformPackage;
import com.android.sdkuilib.internal.repository.SettingsController;
import com.android.sdkuilib.internal.repository.UpdaterData;
import com.android.sdkuilib.internal.repository.sdkman2.PackageLoader.IAutoInstallTask;
import com.android.sdkuilib.internal.tasks.ProgressView;
import com.android.sdkuilib.internal.tasks.ProgressViewFactory;
import com.android.sdkuilib.ui.GridDataBuilder;
import com.android.sdkuilib.ui.GridLayoutBuilder;
import com.android.sdkuilib.ui.SwtBaseDialog;
import com.android.util.Pair;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;

import java.io.File;

/**
 * This is a private implementation of UpdateWindow for ADT,
 * designed to install a very specific package.
 * <p/>
 * Example of usage:
 * <pre>
 * AdtUpdateDialog dialog = new AdtUpdateDialog(
 *     AdtPlugin.getDisplay().getActiveShell(),
 *     new AdtConsoleSdkLog(),
 *     sdk.getSdkLocation());
 *
 * Pair<Boolean, File> result = dialog.installExtraPackage(
 *     "android", "compatibility");  //$NON-NLS-1$ //$NON-NLS-2$
 * or
 * Pair<Boolean, File> result = dialog.installPlatformPackage(11);
 * </pre>
 */
public class AdtUpdateDialog extends SwtBaseDialog {

    private static final String APP_NAME = "Android SDK Manager";
    private final UpdaterData mUpdaterData;

    private Boolean mResultCode = Boolean.FALSE;
    private File    mResultPath = null;
    private SettingsController mSettingsController;
    private PackageFilter mPackageFilter;
    private PackageLoader mPackageMananger;

    private ProgressBar mProgressBar;
    private Label mStatusText;

    /**
     * Creates a new {@link AdtUpdateDialog}.
     * Callers will want to call {@link #installExtraPackage} or
     * {@link #installPlatformPackage} after this.
     *
     * @param parentShell The existing parent shell. Must not be null.
     * @param sdkLog An SDK logger. Must not be null.
     * @param osSdkRoot The current SDK root OS path. Must not be null or empty.
     */
    public AdtUpdateDialog(
            Shell parentShell,
            ISdkLog sdkLog,
            String osSdkRoot) {
        super(parentShell, SWT.NONE, APP_NAME);
        mUpdaterData = new UpdaterData(osSdkRoot, sdkLog);
    }

    /**
     * Displays the update dialog and triggers installation of the requested {@code extra}
     * package with the specified vendor and path attributes.
     * <p/>
     * Callers must not try to reuse this dialog after this call.
     *
     * @param vendor The extra package vendor string to match.
     * @param path   The extra package path   string to match.
     * @return A boolean indicating whether the installation was successful (meaning the package
     *   was either already present, or got installed or updated properly) and a {@link File}
     *   with the path to the root folder of the package. The file is null when the boolean
     *   is false, otherwise it should point to an existing valid folder.
     * @wbp.parser.entryPoint
     */
    public Pair<Boolean, File> installExtraPackage(String vendor, String path) {
        mPackageFilter = PackageFilter.createExtraFilter(vendor, path);
        open();
        return Pair.of(mResultCode, mResultPath);
    }

    /**
     * Displays the update dialog and triggers installation of the requested {@code platform}
     * package with the specified api level attributes.
     * <p/>
     * Callers must not try to reuse this dialog after this call.
     *
     * @param apiLevel The platform api level to match.
     * @return A boolean indicating whether the installation was successful (meaning the package
     *   was either already present, or got installed or updated properly) and a {@link File}
     *   with the path to the root folder of the package. The file is null when the boolean
     *   is false, otherwise it should point to an existing valid folder.
     */
    public Pair<Boolean, File> installPlatformPackage(int apiLevel) {
        mPackageFilter = PackageFilter.createPlatformFilter(apiLevel);
        open();
        return Pair.of(mResultCode, mResultPath);
    }

    @Override
    protected void createContents() {
        Shell shell = getShell();
        shell.setMinimumSize(new Point(450, 100));
        shell.setSize(450, 100);

        mUpdaterData.setWindowShell(shell);

        GridLayoutBuilder.create(shell).columns(1);

        Composite composite1 = new Composite(shell, SWT.NONE);
        composite1.setLayout(new GridLayout(1, false));
        GridDataBuilder.create(composite1).fill().grab();

        mProgressBar = new ProgressBar(composite1, SWT.NONE);
        GridDataBuilder.create(mProgressBar).hFill().hGrab();

        mStatusText = new Label(composite1, SWT.NONE);
        mStatusText.setText("Status Placeholder");  //$NON-NLS-1$ placeholder
        GridDataBuilder.create(mStatusText).hFill().hGrab();
    }

    @Override
    protected void postCreate() {
        ProgressViewFactory factory = new ProgressViewFactory();
        factory.setProgressView(new ProgressView(
                mStatusText,
                mProgressBar,
                null /*buttonStop*/,
                mUpdaterData.getSdkLog()));
        mUpdaterData.setTaskFactory(factory);

        setupSources();
        initializeSettings();

        if (mUpdaterData.checkIfInitFailed()) {
            close();
            return;
        }

        mUpdaterData.broadcastOnSdkLoaded();

        mPackageMananger = new PackageLoader(mUpdaterData);
    }

    @Override
    protected void eventLoop() {
        mPackageMananger.loadPackagesWithInstallTask(new IAutoInstallTask() {
            public boolean acceptPackage(Package pkg) {
                // Is this the package we want to install?
                return mPackageFilter.accept(pkg);
            }

          public void setResult(Package pkg, boolean success, File installPath) {
              // Capture the result from the installation.
              mResultCode = Boolean.valueOf(success);
              mResultPath = installPath;
          }

          public void taskCompleted() {
              // We can close that window now.
              close();
          }
        });

        super.eventLoop();
    }

    // -- Start of internal part ----------
    // Hide everything down-below from SWT designer
    //$hide>>$

    // --- Public API -----------


    // --- Internals & UI Callbacks -----------

    /**
     * Used to initialize the sources.
     */
    private void setupSources() {
        mUpdaterData.setupDefaultSources();
    }

    /**
     * Initializes settings.
     */
    private void initializeSettings() {
        mSettingsController = mUpdaterData.getSettingsController();
        mSettingsController.loadSettings();
        mSettingsController.applySettings();
    }

    // ----

    private static abstract class PackageFilter {

        abstract boolean accept(Package pkg);

        public static PackageFilter createExtraFilter(
                final String vendor,
                final String path) {
            return new PackageFilter() {
                String mVendor = vendor;
                String mPath = path;
                @Override
                boolean accept(Package pkg) {
                    if (pkg instanceof ExtraPackage) {
                        ExtraPackage ep = (ExtraPackage) pkg;
                        return ep.getVendor().equals(mVendor) &&
                               ep.getPath().equals(mPath);
                    }
                    return false;
                }
            };
        }

        public static PackageFilter createPlatformFilter(final int apiLevel) {
            return new PackageFilter() {
                int mApiLevel = apiLevel;
                @Override
                boolean accept(Package pkg) {
                    if (pkg instanceof PlatformPackage) {
                        PlatformPackage pp = (PlatformPackage) pkg;
                        AndroidVersion v = pp.getVersion();
                        return !v.isPreview() && v.getApiLevel() == mApiLevel;
                    }
                    return false;
                }
            };
        }
    }



    // End of hiding from SWT Designer
    //$hide<<$

    // -----

}
