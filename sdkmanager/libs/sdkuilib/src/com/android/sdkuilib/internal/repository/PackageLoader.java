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
import com.android.sdklib.internal.repository.LocalSdkParser;
import com.android.sdklib.internal.repository.Package;
import com.android.sdklib.internal.repository.SdkSource;
import com.android.sdklib.internal.repository.Package.UpdateInfo;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
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
     * After processing each source, the package loader calls {@link #onSouceLoaded(List)}
     * with the list of package items found in that source. The client should process that
     * list as it want, typically by accumulating the package items in a list of its own.
     * By returning true from {@link #onSouceLoaded(List)}, the client tells the loader to
     * continue and process the next source. By returning false, it tells to stop loading.
     * <p/>
     * The {@link #onLoadCompleted()} method is guaranteed to be called at the end, no
     * matter how the loader stopped, so that the client can clean up or perform any
     * final action.
     */
    public interface ISourceLoadedCallback {
        /**
         * After processing each source, the package loader calls this method with the
         * list of package items found in that source. The client should process that
         * list as it want, typically by accumulating the package items in a list of its own.
         * By returning true from {@link #onSouceLoaded(List)}, the client tells the loader to
         * continue and process the next source. By returning false, it tells to stop loading.
         * <p/>
         * <em>Important</em>: This method is called from a sub-thread, so clients who try
         * to access any UI widgets must wrap their calls into {@link Display#syncExec(Runnable)}
         * or {@link Display#asyncExec(Runnable)}.
         *
         * @param pkgItems All the package items loaded from the last processed source.
         *  This is a copy and the client can hold to this list or modify it in any way.
         * @return True if the load operation should continue, false if it should stop.
         */
        public boolean onSouceLoaded(List<PkgItem> pkgItems);

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
            final List<PkgItem> allPkgItems =  loadLocalPackages();
            if (!allPkgItems.isEmpty()) {
                // Notify the callback by giving it a copy of the current list.
                // (in case the callback holds to the list... we still need this list of
                // ourselves below).
                if (!sourceLoadedCallback.onSouceLoaded(new ArrayList<PkgItem>(allPkgItems))) {
                    return;
                }
            }

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

                            List<PkgItem> sourcePkgItems = new ArrayList<PkgItem>();

                            nextPkg: for(Package pkg : pkgs) {
                                boolean isUpdate = false;
                                for (PkgItem pi: allPkgItems) {
                                    if (pi.isSamePackageAs(pkg)) {
                                        continue nextPkg;
                                    }
                                    if (pi.isUpdatedBy(pkg)) {
                                        isUpdate = true;
                                        break;
                                    }
                                }

                                if (!isUpdate) {
                                    PkgItem pi = new PkgItem(pkg, PkgState.NEW);
                                    sourcePkgItems.add(pi);
                                    allPkgItems.add(pi);
                                }
                            }

                            // Notify the callback a new source has finished loading.
                            // If the callback requests so, stop right away.
                            if (!sourceLoadedCallback.onSouceLoaded(sourcePkgItems)) {
                                return;
                            }
                        }
                    } catch(Exception e) {
                        monitor.logError("Loading source failed: %1$s", e.toString());
                    } finally {
                        monitor.setDescription("Done loading %1$d packages from %2$d sources",
                                allPkgItems.size(),
                                sources.length);
                    }
                }
            });
        } finally {
            sourceLoadedCallback.onLoadCompleted();
        }
    }

    /**
     * Internal method that returns all installed packages from the {@link LocalSdkParser}
     * associated with the {@link UpdaterData}.
     * <p/>
     * Note that the {@link LocalSdkParser} maintains a cache, so callers need to clear
     * it if they know they changed the local installation.
     *
     * @return A new list of {@link PkgItem}. May be empty but never null.
     */
    private List<PkgItem> loadLocalPackages() {
        List<PkgItem> pkgItems = new ArrayList<PkgItem>();

        for (Package pkg : mUpdaterData.getInstalledPackages()) {
            PkgItem pi = new PkgItem(pkg, PkgState.INSTALLED);
            pkgItems.add(pi);
        }

        return pkgItems;
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
            public boolean onSouceLoaded(List<PkgItem> pkgItems) {
                for (PkgItem item : pkgItems) {
                    Package acceptedPkg = null;
                    switch(item.getState()) {
                    case NEW:
                        if (installTask.acceptPackage(item.getPackage())) {
                            acceptedPkg = item.getPackage();
                        }
                        break;
                    case HAS_UPDATE:
                        for (Package upd : item.getUpdatePkgs()) {
                            if (installTask.acceptPackage(upd)) {
                                acceptedPkg = upd;
                                break;
                            }
                        }
                        break;
                    case INSTALLED:
                        if (installTask.acceptPackage(item.getPackage())) {
                            // If the caller is accepting an installed package,
                            // return a success and give the package's install path
                            acceptedPkg = item.getPackage();
                            Archive[] a = acceptedPkg.getArchives();
                            // an installed package should have one local compatible archive
                            if (a.length == 1 && a[0].isCompatible()) {
                                installTask.setResult(
                                        acceptedPkg,
                                        true /*success*/,
                                        new File(a[0].getLocalOsPath()));

                                // return false to tell loadPackages() that we don't
                                // need to continue processing any more sources.
                                return false;
                            }
                        }
                    }

                    if (acceptedPkg != null) {
                        // Try to install this package if it has one compatible archive.
                        Archive archiveToInstall = null;

                        for (Archive a2 : acceptedPkg.getArchives()) {
                            if (a2.isCompatible()) {
                                archiveToInstall = a2;
                                break;
                            }
                        }

                        if (archiveToInstall != null) {
                            installArchive(archiveToInstall);
                        }

                        // return false to tell loadPackages() that we don't
                        // need to continue processing any more sources.
                        return false;
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
                    final List<PkgItem> localPkgItems = loadLocalPackages();

                    // Try to locate the installed package in the new package list
                    for (PkgItem localItem : localPkgItems) {
                        Package localPkg = localItem.getPackage();
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
         * Package is locally installed and has no update available on remote sites.
         */
        INSTALLED,

        /**
         * Package is installed and has an update available.
         * In this case, {@link PkgItem#getUpdatePkgs()} provides the list of 1 or more
         * packages that can update this {@link PkgItem}.
         * <p/>
         * Although not structurally enforced, it can be reasonably expected that
         * the original package and the updating packages all come from the same source.
         */
        HAS_UPDATE,

        /**
         * There's a new package available on the remote site that isn't
         * installed locally.
         */
        NEW
    }

    /**
     * A {@link PkgItem} represents one {@link Package} combined with its state.
     * <p/>
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

        public boolean isSameItemAs(PkgItem item) {
            boolean same = this.mState == item.mState;
            if (same) {
                same = isSamePackageAs(item.getPackage());
            }
            // check updating packages are the same
            if (same) {
                List<Package> u1 = getUpdatePkgs();
                List<Package> u2 = item.getUpdatePkgs();
                same = (u1 == null && u2 == null) ||
                       (u1 != null && u2 != null);
                if (same && u1 != null && u2 != null) {
                    int n = u1.size();
                    same = n == u2.size();
                    if (same) {
                        for (int i = 0; same && i < n; i++) {
                            Package p1 = u1.get(i);
                            Package p2 = u2.get(i);
                            same = p1.canBeUpdatedBy(p2) == UpdateInfo.NOT_UPDATE;
                        }
                    }
                }
            }

            return same;
        }

        public boolean isSamePackageAs(Package pkg) {
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

        /** Returns a string representation of this item, useful when debugging. */
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(mState.toString());

            if (mPkg != null) {
                sb.append(", pkg:"); //$NON-NLS-1$
                sb.append(mPkg.toString());
            }

            if (mUpdatePkgs != null && !mUpdatePkgs.isEmpty()) {
                sb.append(", updated by:"); //$NON-NLS-1$
                sb.append(Arrays.toString(mUpdatePkgs.toArray()));
            }

            return sb.toString();
        }
    }
}
