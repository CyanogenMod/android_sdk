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
import org.eclipse.swt.widgets.Shell;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads packages fetched from the remote SDK Repository and keeps track
 * of their state compared with the current local SDK installation.
 */
class PackageLoader {

    private final UpdaterData mUpdaterData;

    /**
     * Interface for the callback called by
     * {@link PackageLoader#loadPackages(ISourceLoadedCallback)}.
     * <p/>
     * After processing each source, the package loader calls {@link #onUpdateSource}
     * with the list of packages found in that source.
     * By returning true from {@link #onUpdateSource}, the client tells the loader to
     * continue and process the next source. By returning false, it tells to stop loading.
     * <p/>
     * The {@link #onLoadCompleted()} method is guaranteed to be called at the end, no
     * matter how the loader stopped, so that the client can clean up or perform any
     * final action.
     */
    public interface ISourceLoadedCallback {
        /**
         * After processing each source, the package loader calls this method with the
         * list of packages found in that source.
         * By returning true from {@link #onUpdateSource}, the client tells
         * the loader to continue and process the next source.
         * By returning false, it tells to stop loading.
         * <p/>
         * <em>Important</em>: This method is called from a sub-thread, so clients which
         * try to access any UI widgets must wrap their calls into
         * {@link Display#syncExec(Runnable)} or {@link Display#asyncExec(Runnable)}.
         *
         * @param packages All the packages loaded from the source. Never null.
         * @return True if the load operation should continue, false if it should stop.
         */
        public boolean onUpdateSource(SdkSource source, Package[] packages);

        /**
         * This method is guaranteed to be called at the end, no matter how the
         * loader stopped, so that the client can clean up or perform any final action.
         */
        public void onLoadCompleted();
    }

    /**
     * Interface describing the task of installing a specific package.
     * For details on the operation,
     * see {@link PackageLoader#loadPackagesWithInstallTask(IAutoInstallTask)}.
     *
     * @see PackageLoader#loadPackagesWithInstallTask(IAutoInstallTask)
     */
    public interface IAutoInstallTask {
        /**
         * Called by the install task for every package available (new ones, updates as well
         * as existing ones that don't have a potential update.)
         * The method should return true if this is the package that should be installed,
         * at which point the packager loader will stop processing the next packages and sources.
         * <p/>
         * <em>Important</em>: This method is called from a sub-thread, so clients who try
         * to access any UI widgets must wrap their calls into {@link Display#syncExec(Runnable)}
         * or {@link Display#asyncExec(Runnable)}.
         */
        public boolean acceptPackage(Package pkg);

        /**
         * Called when the accepted package has been installed, successfully or not.
         * If an already installed (aka existing) package has been accepted, this will
         * be called with a 'true' success and the actual install path.
         * <p/>
         * <em>Important</em>: This method is called from a sub-thread, so clients who try
         * to access any UI widgets must wrap their calls into {@link Display#syncExec(Runnable)}
         * or {@link Display#asyncExec(Runnable)}.
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
    public PackageLoader(UpdaterData updaterData) {
        mUpdaterData = updaterData;
    }

    /**
     * Loads all packages from the remote repository.
     * This runs in an {@link ITask}. The call is blocking.
     * <p/>
     * The callback is called with each set of {@link PkgItem} found in each source.
     * The caller is responsible to accumulate the packages given to the callback
     * after each source is finished loaded. In return the callback tells the loader
     * whether to continue loading sources.
     */
    public void loadPackages(final ISourceLoadedCallback sourceLoadedCallback) {
        try {
            if (mUpdaterData == null) {
                return;
            }

            // get local packages and offer them to the callback
            Package[] localPkgs = mUpdaterData.getInstalledPackages();
            if (localPkgs == null) {
                localPkgs = new Package[0];
            }
            if (!sourceLoadedCallback.onUpdateSource(null, localPkgs)) {
                return;
            }

            final int[] numPackages = { localPkgs == null ? 0 : localPkgs.length };

            // get remote packages
            final boolean forceHttp = mUpdaterData.getSettingsController().getForceHttp();
            mUpdaterData.loadRemoteAddonsList();
            mUpdaterData.getTaskFactory().start("Loading Sources", new ITask() {
                public void run(ITaskMonitor monitor) {
                    SdkSource[] sources = mUpdaterData.getSources().getAllSources();
                    try {
                        for (SdkSource source : sources) {
                            Package[] pkgs = source.getPackages();
                            if (pkgs == null) {
                                source.load(monitor, forceHttp);
                                pkgs = source.getPackages();
                            }
                            if (pkgs == null) {
                                continue;
                            }

                            numPackages[0] += pkgs.length;

                            // Notify the callback a new source has finished loading.
                            // If the callback requests so, stop right away.
                            if (!sourceLoadedCallback.onUpdateSource(source, pkgs)) {
                                return;
                            }
                        }
                    } catch(Exception e) {
                        monitor.logError("Loading source failed: %1$s", e.toString());
                    } finally {
                        monitor.setDescription("Done loading %1$d packages from %2$d sources",
                                numPackages[0],
                                sources.length);
                    }
                }
            });
        } finally {
            sourceLoadedCallback.onLoadCompleted();
        }
    }

    /**
     * Load packages, source by source using {@link #loadPackages(ISourceLoadedCallback)},
     * and executes the given {@link IAutoInstallTask} on the current package list.
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
     * <em>Important</em>: Since some UI will be displayed to install the selected package,
     * the {@link UpdaterData} must have a window {@link Shell} associated using
     * {@link UpdaterData#setWindowShell(Shell)}.
     * <p/>
     * The call is blocking. Although the name says "Task", this is not an {@link ITask}
     * running in its own thread but merely a synchronous call.
     *
     * @param installTask The task to perform.
     */
    public void loadPackagesWithInstallTask(final IAutoInstallTask installTask) {

        loadPackages(new ISourceLoadedCallback() {
            public boolean onUpdateSource(SdkSource source, Package[] packages) {
                for (Package pkg : packages) {
                    if (pkg.isLocal()) {
                        // This is a local (aka installed) package
                        if (installTask.acceptPackage(pkg)) {
                            // If the caller is accepting an installed package,
                            // return a success and give the package's install path
                            Archive[] a = pkg.getArchives();
                            // an installed package should have one local compatible archive
                            if (a.length == 1 && a[0].isCompatible()) {
                                installTask.setResult(
                                        pkg,
                                        true /*success*/,
                                        new File(a[0].getLocalOsPath()));
                            }
                            // return false to tell loadPackages() that we don't
                            // need to continue processing any more sources.
                            return false;
                        }

                    } else {
                        // This is a remote package
                        if (installTask.acceptPackage(pkg)) {
                            // The caller is accepting this remote package. Let's try to install it.

                            for (Archive archive : pkg.getArchives()) {
                                if (archive.isCompatible()) {
                                    installArchive(archive);
                                    break;
                                }
                            }
                            // return false to tell loadPackages() that we don't
                            // need to continue processing any more sources.
                            return false;
                        }
                    }
                }

                // Tell loadPackages() to process the next source.
                return true;
            }

            /**
             * Shows the UI of the install selector.
             * If the package is then actually installed, refresh the local list and
             * notify the install task of the installation path.
             *
             * @param archiveToInstall The archive to install.
             */
            private void installArchive(Archive archiveToInstall) {
                // What we want to install
                final ArrayList<Archive> archivesToInstall = new ArrayList<Archive>();
                archivesToInstall.add(archiveToInstall);

                Package packageToInstall = archiveToInstall.getParentPackage();

                // What we'll end up installing
                final Archive[] installedArchive = new Archive[] { null };

                // Actually install the new archive that we just found.
                // This will display some UI so we need a shell's sync exec.

                mUpdaterData.getWindowShell().getDisplay().syncExec(new Runnable() {
                    public void run() {
                        List<Archive> archives =
                            mUpdaterData.updateOrInstallAll_WithGUI(
                                archivesToInstall,
                                true /* includeObsoletes */);

                        if (archives != null && !archives.isEmpty()) {
                            // We expect that at most one archive has been installed.
                            assert archives.size() == 1;
                            installedArchive[0] = archives.get(0);
                        }
                    }
                });

                // If the desired package has been installed...
                if (installedArchive[0] == archiveToInstall) {

                    // The local package list has changed, make sure to refresh it
                    mUpdaterData.getLocalSdkParser().clearPackages();
                    final Package[] localPkgs = mUpdaterData.getInstalledPackages();

                    // Try to locate the installed package in the new package list
                    for (Package localPkg : localPkgs) {
                        if (localPkg.canBeUpdatedBy(packageToInstall) == UpdateInfo.NOT_UPDATE) {
                            Archive[] localArchive = localPkg.getArchives();
                            if (localArchive.length == 1 && localArchive[0].isCompatible()) {
                                installTask.setResult(
                                        localPkg,
                                        true /*success*/,
                                        new File(localArchive[0].getLocalOsPath()));
                                return;
                            }
                        }
                    }
                }

                // We failed to install the package.
                installTask.setResult(packageToInstall, false /*success*/, null);
            }

            public void onLoadCompleted() {
                installTask.taskCompleted();
            }
        });

    }

    /**
     * The state of the a given {@link PkgItem}, that is the relationship between
     * a given remote package and the local repository.
     */
    public enum PkgState {
        /**
         * Package is locally installed and may or may not have an update.
         */
        INSTALLED,

        /**
         * There's a new package available on the remote site that isn't installed locally.
         */
        NEW
    }

    /**
     * A {@link PkgItem} represents one main {@link Package} combined with its state
     * and an optional update package.
     * <p/>
     * The main package is final and cannot change since it's what "defines" this PkgItem.
     * The state or update package can change later.
     */
    public static class PkgItem implements Comparable<PkgItem> {
        private PkgState mState;
        private final Package mMainPkg;
        private Package mUpdatePkg;

        /**
         * Create a new {@link PkgItem} for this main package.
         * The main package is final and cannot change since it's what "defines" this PkgItem.
         * The state or update package can change later.
         */
        public PkgItem(Package mainPkg, PkgState state) {
            mMainPkg = mainPkg;
            mState = state;
            assert mMainPkg != null;
        }

        public boolean isObsolete() {
            return mMainPkg.isObsolete();
        }

        public Package getUpdatePkg() {
            return mUpdatePkg;
        }

        public boolean hasUpdatePkg() {
            return mUpdatePkg != null;
        }

        public String getName() {
            return mMainPkg.getListDescription();
        }

        public int getRevision() {
            return mMainPkg.getRevision();
        }

        public String getDescription() {
            return mMainPkg.getDescription();
        }

        public Package getMainPackage() {
            return mMainPkg;
        }

        public PkgState getState() {
            return mState;
        }

        public void setState(PkgState state) {
            mState = state;
        }

        public SdkSource getSource() {
            return mMainPkg.getParentSource();
        }

        public int getApi() {
            return mMainPkg instanceof IPackageVersion ?
                    ((IPackageVersion) mMainPkg).getVersion().getApiLevel() :
                        -1;
        }

        public Archive[] getArchives() {
            return mMainPkg.getArchives();
        }

        public int compareTo(PkgItem pkg) {
            return getMainPackage().compareTo(pkg.getMainPackage());
        }

        /**
         * Returns true if this package or its updating packages contains
         * the exact given archive.
         * Important: This compares object references, not object equality.
         */
        public boolean hasArchive(Archive archive) {
            if (mMainPkg.hasArchive(archive)) {
                return true;
            }
            if (mUpdatePkg != null && mUpdatePkg.hasArchive(archive)) {
                return true;
            }
            return false;
        }

        /**
         * Checks whether the main packages are of the same type and are
         * not an update of each other.
         */
        public boolean isSameMainPackageAs(Package pkg) {
            if (mMainPkg.canBeUpdatedBy(pkg) == UpdateInfo.NOT_UPDATE) {
                // package revision numbers must match
                return mMainPkg.getRevision() == pkg.getRevision();
            }
            return false;
        }

        /**
         * Checks whether too {@link PkgItem} are the same.
         * This checks both items have the same state, both main package are similar
         * and that they have the same updating packages.
         */
        public boolean isSameItemAs(PkgItem item) {
            if (this == item) {
                return true;
            }
            boolean same = this.mState == item.mState;
            if (same) {
                same = isSameMainPackageAs(item.getMainPackage());
            }

            if (same) {
                // check updating packages are the same
                Package p1 = this.mUpdatePkg;
                Package p2 = item.getUpdatePkg();
                same = (p1 == p2) || (p1 == null && p2 == null) || (p1 != null && p2 != null);

                if (same && p1 != null) {
                    same = p1.canBeUpdatedBy(p2) == UpdateInfo.NOT_UPDATE;
                }
            }

            return same;
        }

        /**
         * Equality is defined as {@link #isSameItemAs(PkgItem)}: state, main package
         * and update package must be the similar.
         */
        @Override
        public boolean equals(Object obj) {
            return (obj instanceof PkgItem) && this.isSameItemAs((PkgItem) obj);
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((mState     == null) ? 0 : mState.hashCode());
            result = prime * result + ((mMainPkg   == null) ? 0 : mMainPkg.hashCode());
            result = prime * result + ((mUpdatePkg == null) ? 0 : mUpdatePkg.hashCode());
            return result;
        }

        /**
         * Check whether the 'pkg' argument is an update for this package.
         * If it is, record it as an updating package.
         * If there's already an updating package, only keep the most recent update.
         * Returns true if it is update (even if there was already an update and this
         * ended up not being the most recent), false if incompatible or not an update.
         *
         * This should only be used for installed packages.
         */
        public boolean mergeUpdate(Package pkg) {
            if (mUpdatePkg == pkg) {
                return true;
            }
            if (mMainPkg.canBeUpdatedBy(pkg) == UpdateInfo.UPDATE) {
                if (mUpdatePkg == null) {
                    mUpdatePkg = pkg;
                } else if (mUpdatePkg.canBeUpdatedBy(pkg) == UpdateInfo.UPDATE) {
                    // If we have more than one, keep only the most recent update
                    mUpdatePkg = pkg;
                }
                return true;
            }

            return false;
        }

        public void removeUpdate() {
            mUpdatePkg = null;
        }

        /** Returns a string representation of this item, useful when debugging. */
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append('<');
            sb.append(mState.toString());

            if (mMainPkg != null) {
                sb.append(", pkg:"); //$NON-NLS-1$
                sb.append(mMainPkg.toString());
            }

            if (mUpdatePkg != null) {
                sb.append(", updated by:"); //$NON-NLS-1$
                sb.append(mUpdatePkg.toString());
            }

            sb.append('>');
            return sb.toString();
        }

    }
}
