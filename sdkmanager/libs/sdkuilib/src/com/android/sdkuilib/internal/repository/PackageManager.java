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

package com.android.sdkuilib.internal.repository;

import com.android.sdklib.internal.repository.Archive;
import com.android.sdklib.internal.repository.IPackageVersion;
import com.android.sdklib.internal.repository.ITask;
import com.android.sdklib.internal.repository.ITaskMonitor;
import com.android.sdklib.internal.repository.Package;
import com.android.sdklib.internal.repository.SdkSource;
import com.android.sdklib.internal.repository.Package.UpdateInfo;

import org.eclipse.swt.widgets.Display;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages packages fetched from the remote SDK Repository and keeps track
 * of their state compared with the current local SDK installation.
 * <p/>
 * This is currently just an embryonic manager which should evolve over time.
 * There's a lot of overlap with the functionality of {@link UpdaterData} so
 * merging them together might just be the way to go.
 * <p/>
 * Right now all it does is provide the ability to load packages using a task
 * and perform some semi-automatic installation of a given package if available.
 */
abstract class PackageManager {

    private final List<PkgItem> mPackages = new ArrayList<PkgItem>();
    private final UpdaterData mUpdaterData;

    /**
     * Interface describing the task of installing a specific package.
     * For details on the operation,
     * see {@link PackageManager#performAutoInstallTask(IAutoInstallTask)}.
     *
     * @see PackageManager#performAutoInstallTask(IAutoInstallTask)
     */
    public interface IAutoInstallTask {
        /**
         * Called by the install task for every package available (new ones, updates as well
         * as existing ones that don't have a potential update.)
         * The method should return true if this is the package that should be installed.
         */
        public boolean acceptPackage(Package pkg);

        /**
         * Called when the accepted package has been installed, successfully or not.
         * If an already installed (aka existing) package has been accepted, this will
         * be called with a 'true' success and the actual install path.
         */
        public void setResult(Package pkg, boolean success, File installPath);

        /**
         * Called when the task is done iterating and completed.
         */
        public void taskCompleted();
    }

    /**
     * Creates a new PackageManager associated with the given {@link UpdaterData}.
     *
     * @param updaterData The {@link UpdaterData}. Must not be null.
     */
    public PackageManager(UpdaterData updaterData) {
        mUpdaterData = updaterData;
    }

    /**
     * Derived classes sohuld implement that to update their display of the current
     * package list. Called by {@link #loadPackages()} when the package list has
     * significantly changed.
     * <p/>
     * The package list is retrieved using {@link #getPackages()} which is guaranteed
     * not to change during this call.
     * <p/>
     * This is called from a non-UI thread. An UI interaction done here <em>must</em>
     * be wrapped in a {@link Display#syncExec(Runnable)}.
     */
    public abstract void updatePackageTable();

    /**
     * Returns the current package list.
     */
    public List<PkgItem> getPackages() {
        return mPackages;
    }

    /**
     * Load all packages from the remote repository and
     * updates the {@link PkgItem} package list.
     *
     * This runs in an {@link ITask}. The call is blocking.
     */
    public void loadPackages() {
        if (mUpdaterData == null) {
            return;
        }

        mPackages.clear();

        // get local packages
        for (Package pkg : mUpdaterData.getInstalledPackages()) {
            PkgItem pi = new PkgItem(pkg, PkgState.INSTALLED);
            mPackages.add(pi);
        }

        // get remote packages
        final boolean forceHttp = mUpdaterData.getSettingsController().getForceHttp();
        mUpdaterData.loadRemoteAddonsList();
        mUpdaterData.getTaskFactory().start("Loading Sources", new ITask() {
            public void run(ITaskMonitor monitor) {
                SdkSource[] sources = mUpdaterData.getSources().getAllSources();
                for (SdkSource source : sources) {
                    Package[] pkgs = source.getPackages();
                    if (pkgs == null) {
                        source.load(monitor, forceHttp);
                        pkgs = source.getPackages();
                    }
                    if (pkgs == null) {
                        continue;
                    }

                    nextPkg: for(Package pkg : pkgs) {
                        boolean isUpdate = false;
                        for (PkgItem pi: mPackages) {
                            if (pi.isSameAs(pkg)) {
                                continue nextPkg;
                            }
                            if (pi.isUpdatedBy(pkg)) {
                                isUpdate = true;
                                break;
                            }
                        }

                        if (!isUpdate) {
                            PkgItem pi = new PkgItem(pkg, PkgState.NEW);
                            mPackages.add(pi);
                        }
                    }

                    // Dynamically update the table while we load after each source.
                    // Since the official Android source gets loaded first, it makes the
                    // window look non-empty a lot sooner.
                    updatePackageTable();
                }

                monitor.setDescription("Done loading %1$d packages from %2$d sources",
                        mPackages.size(),
                        sources.length);
            }
        });
    }

    /**
     * Executes the given {@link IAutoInstallTask} on the current package list.
     * That is for each package known, the install task is queried to find if
     * the package is the one to be installed or updated.
     * <p/>
     * - If an already installed package is accepted by the task, it is returned. <br/>
     * - If a new package (remotely available but not installed locally) is accepted,
     * the user will be <em>prompted</em> for permission to install it. <br/>
     * - If an existing package has updates, the install task will be accept if it
     * accepts one of the updating packages, and if yes the the user will be
     * <em>prompted</em> for permission to install it. <br/>
     * <p/>
     * Only one package can be accepted, after which the task is completed.
     * There is no direct return value, {@link IAutoInstallTask#setResult} is called on the
     * result of the accepted package.
     * When the task is completed, {@link IAutoInstallTask#taskCompleted()} is called.
     * <p/>
     * The call is blocking. Although the name says "Task", this is not an {@link ITask}
     * running in its own thread but merely a synchronous call.
     *
     * @param installTask The task to perform.
     */
    public void performAutoInstallTask(IAutoInstallTask installTask) {
        try {
            for (PkgItem item : mPackages) {
                Package pkg = null;
                switch(item.getState()) {
                case NEW:
                    if (installTask.acceptPackage(item.getPackage())) {
                        pkg = item.getPackage();
                    }
                    break;
                case HAS_UPDATE:
                    for (Package upd : item.getUpdatePkgs()) {
                        if (installTask.acceptPackage(upd)) {
                            pkg = upd;
                            break;
                        }
                    }
                    break;
                case INSTALLED:
                    if (installTask.acceptPackage(item.getPackage())) {
                        // If the caller is accepting an installed package,
                        // return a success and give the package's install path
                        pkg = item.getPackage();
                        Archive[] a = pkg.getArchives();
                        // an installed package should have one local compatible archive
                        if (a.length == 1 && a[0].isCompatible()) {
                            installTask.setResult(
                                    pkg,
                                    true /*success*/,
                                    new File(a[0].getLocalOsPath()));
                            return;
                        }
                    }
                }

                if (pkg != null) {
                    // Try to install this package if it has one compatible archive.
                    Archive a = null;

                    for (Archive a2 : pkg.getArchives()) {
                        if (a2.isCompatible()) {
                            a = a2;
                            break;
                        }
                    }

                    if (a != null) {
                        ArrayList<Archive> archives = new ArrayList<Archive>();
                        archives.add(a);

                        mUpdaterData.updateOrInstallAll_WithGUI(
                                archives,
                                true /* includeObsoletes */);

                        // loadPackages will also re-enable the UI
                        loadPackages();

                        // Try to locate the installed package in the new package list
                        for (PkgItem item2 : mPackages) {
                            if (item2.getState() == PkgState.INSTALLED) {
                                Package pkg2 = item2.getPackage();
                                if (pkg2.canBeUpdatedBy(pkg) == UpdateInfo.NOT_UPDATE) {
                                    Archive[] a2 = pkg.getArchives();
                                    if (a2.length == 1 && a2[0].isCompatible()) {
                                        installTask.setResult(
                                                pkg,
                                                true /*success*/,
                                                new File(a2[0].getLocalOsPath()));
                                        return;
                                    }
                                }
                            }
                        }

                        // We failed to find the installed package.
                        // We'll assume we failed to install it.
                        installTask.setResult(pkg, false /*success*/, null);
                        return;
                    }
                }

            }
        } finally {
            installTask.taskCompleted();
        }
    }

    /**
     * The state of the a given {@link PkgItem}, that is the relationship between
     * a given remote package and the local repository.
     */
    public enum PkgState {
        /**
         * Package is locally installed and has no update available on remote sites.
         */
        INSTALLED,

        /**
         * Package is installed and has an update available.
         * In this case, {@link PkgItem#getUpdatePkgs()} provides the list of 1 or more
         * packages that can update this {@link PkgItem}.
         */
        HAS_UPDATE,

        /**
         * There's a new package available on the remote site that isn't
         * installed locally.
         */
        NEW
    }

    /**
     * A {@link PkgItem} represents one {@link Package} available for the manager.
     * It can be either a locally installed package, or a remotely available package.
     * If the later, it can be either a new package or an update for a locally installed one.
     * <p/>
     * In the case of an update, the {@link PkgItem#getPackage()} represents the currently
     * installed package and there's a separate list of {@link PkgItem#getUpdatePkgs()} that
     * links to the updating packages. Note that in a typical repository there should only
     * one update for a given installed package however the system is designed to be more
     * generic and allow many.
     */
    public static class PkgItem implements Comparable<PkgItem> {
        private final Package mPkg;
        private PkgState mState;
        private List<Package> mUpdatePkgs;

        public PkgItem(Package pkg, PkgState state) {
            mPkg = pkg;
            mState = state;
            assert mPkg != null;
        }

        public boolean isObsolete() {
            return mPkg.isObsolete();
        }

        public boolean isSameAs(Package pkg) {
            return mPkg.canBeUpdatedBy(pkg) == UpdateInfo.NOT_UPDATE;
        }

        /**
         * Check whether the 'pkg' argument updates this package.
         * If it does, record it as a sub-package.
         * Returns true if it was recorded as an update, false otherwise.
         */
        public boolean isUpdatedBy(Package pkg) {
            if (mPkg.canBeUpdatedBy(pkg) == UpdateInfo.UPDATE) {
                if (mUpdatePkgs == null) {
                    mUpdatePkgs = new ArrayList<Package>();
                }
                mUpdatePkgs.add(pkg);
                mState = PkgState.HAS_UPDATE;
                return true;
            }

            return false;
        }

        public String getName() {
            return mPkg.getListDescription();
        }

        public int getRevision() {
            return mPkg.getRevision();
        }

        public String getDescription() {
            return mPkg.getDescription();
        }

        public Package getPackage() {
            return mPkg;
        }

        public PkgState getState() {
            return mState;
        }

        public SdkSource getSource() {
            if (mState == PkgState.NEW) {
                return mPkg.getParentSource();
            } else {
                return null;
            }
        }

        public int getApi() {
            return mPkg instanceof IPackageVersion ?
                    ((IPackageVersion) mPkg).getVersion().getApiLevel() :
                        -1;
        }

        public List<Package> getUpdatePkgs() {
            return mUpdatePkgs;
        }

        public Archive[] getArchives() {
            return mPkg.getArchives();
        }

        public int compareTo(PkgItem pkg) {
            return getPackage().compareTo(pkg.getPackage());
        }
    }
}
