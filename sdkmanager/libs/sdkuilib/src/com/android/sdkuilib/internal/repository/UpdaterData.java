/*
 * Copyright (C) 2009 The Android Open Source Project
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

import com.android.annotations.VisibleForTesting;
import com.android.annotations.VisibleForTesting.Visibility;
import com.android.prefs.AndroidLocation.AndroidLocationException;
import com.android.sdklib.ISdkLog;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.SdkManager;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.internal.repository.AdbWrapper;
import com.android.sdklib.internal.repository.AddonPackage;
import com.android.sdklib.internal.repository.AddonsListFetcher;
import com.android.sdklib.internal.repository.Archive;
import com.android.sdklib.internal.repository.ArchiveInstaller;
import com.android.sdklib.internal.repository.ITask;
import com.android.sdklib.internal.repository.ITaskFactory;
import com.android.sdklib.internal.repository.ITaskMonitor;
import com.android.sdklib.internal.repository.LocalSdkParser;
import com.android.sdklib.internal.repository.NullTaskMonitor;
import com.android.sdklib.internal.repository.Package;
import com.android.sdklib.internal.repository.PlatformToolPackage;
import com.android.sdklib.internal.repository.SdkAddonSource;
import com.android.sdklib.internal.repository.SdkRepoSource;
import com.android.sdklib.internal.repository.SdkSource;
import com.android.sdklib.internal.repository.SdkSourceCategory;
import com.android.sdklib.internal.repository.SdkSources;
import com.android.sdklib.internal.repository.ToolPackage;
import com.android.sdklib.internal.repository.AddonsListFetcher.Site;
import com.android.sdklib.repository.SdkAddonConstants;
import com.android.sdklib.repository.SdkAddonsListConstants;
import com.android.sdklib.repository.SdkRepoConstants;
import com.android.sdklib.util.LineUtil;
import com.android.sdklib.util.SparseIntArray;
import com.android.sdkuilib.internal.repository.icons.ImageFactory;
import com.android.sdkuilib.internal.repository.sdkman1.LocalSdkAdapter;
import com.android.sdkuilib.internal.repository.sdkman1.RemotePackagesPage;
import com.android.sdkuilib.internal.repository.sdkman1.RepoSourcesAdapter;
import com.android.sdkuilib.internal.repository.sdkman1.SdkUpdaterWindowImpl1;
import com.android.sdkuilib.repository.ISdkChangeListener;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Data shared between {@link SdkUpdaterWindowImpl1} and its pages.
 */
public class UpdaterData implements IUpdaterData {

    public static final int NO_TOOLS_MSG = 0;
    public static final int TOOLS_MSG_UPDATED_FROM_ADT = 1;
    public static final int TOOLS_MSG_UPDATED_FROM_SDKMAN = 2;

    private String mOsSdkRoot;

    private final ISdkLog mSdkLog;
    private ITaskFactory mTaskFactory;

    private SdkManager mSdkManager;
    private AvdManager mAvdManager;

    private final LocalSdkParser mLocalSdkParser = new LocalSdkParser();
    private final SdkSources mSources = new SdkSources();

    private final LocalSdkAdapter mLocalSdkAdapter = new LocalSdkAdapter(this);
    private final RepoSourcesAdapter mSourcesAdapter = new RepoSourcesAdapter(this);

    private ImageFactory mImageFactory;

    private final SettingsController mSettingsController;

    private final ArrayList<ISdkChangeListener> mListeners = new ArrayList<ISdkChangeListener>();

    private Shell mWindowShell;

    private AndroidLocationException mAvdManagerInitError;

    /**
     * 0 = need to fetch remote addons list once..
     * 1 = fetch succeeded, don't need to do it any more.
     * -1= fetch failed, do it again only if the user requests a refresh
     *     or changes the force-http setting.
     */
    private int mStateFetchRemoteAddonsList;

    /**
     * Creates a new updater data.
     *
     * @param sdkLog Logger. Cannot be null.
     * @param osSdkRoot The OS path to the SDK root.
     */
    public UpdaterData(String osSdkRoot, ISdkLog sdkLog) {
        mOsSdkRoot = osSdkRoot;
        mSdkLog = sdkLog;

        mSettingsController = new SettingsController(this);

        initSdk();
    }

    // ----- getters, setters ----

    public String getOsSdkRoot() {
        return mOsSdkRoot;
    }

    public void setTaskFactory(ITaskFactory taskFactory) {
        mTaskFactory = taskFactory;
    }

    public ITaskFactory getTaskFactory() {
        return mTaskFactory;
    }

    public SdkSources getSources() {
        return mSources;
    }

    public RepoSourcesAdapter getSourcesAdapter() {
        return mSourcesAdapter;
    }

    public LocalSdkParser getLocalSdkParser() {
        return mLocalSdkParser;
    }

    public LocalSdkAdapter getLocalSdkAdapter() {
        return mLocalSdkAdapter;
    }

    public ISdkLog getSdkLog() {
        return mSdkLog;
    }

    public void setImageFactory(ImageFactory imageFactory) {
        mImageFactory = imageFactory;
    }

    public ImageFactory getImageFactory() {
        return mImageFactory;
    }

    public SdkManager getSdkManager() {
        return mSdkManager;
    }

    public AvdManager getAvdManager() {
        return mAvdManager;
    }

    public SettingsController getSettingsController() {
        return mSettingsController;
    }

    /** Adds a listener ({@link ISdkChangeListener}) that is notified when the SDK is reloaded. */
    public void addListeners(ISdkChangeListener listener) {
        if (mListeners.contains(listener) == false) {
            mListeners.add(listener);
        }
    }

    /** Removes a listener ({@link ISdkChangeListener}) that is notified when the SDK is reloaded. */
    public void removeListener(ISdkChangeListener listener) {
        mListeners.remove(listener);
    }

    public void setWindowShell(Shell windowShell) {
        mWindowShell = windowShell;
    }

    public Shell getWindowShell() {
        return mWindowShell;
    }

    /**
     * Check if any error occurred during initialization.
     * If it did, display an error message.
     *
     * @return True if an error occurred, false if we should continue.
     */
    public boolean checkIfInitFailed() {
        if (mAvdManagerInitError != null) {
            String example;
            if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS) {
                example = "%USERPROFILE%";     //$NON-NLS-1$
            } else {
                example = "~";                 //$NON-NLS-1$
            }

            String error = String.format(
                "The AVD manager normally uses the user's profile directory to store " +
                "AVD files. However it failed to find the default profile directory. " +
                "\n" +
                "To fix this, please set the environment variable ANDROID_SDK_HOME to " +
                "a valid path such as \"%s\".",
                example);

            // We may not have any UI. Only display a dialog if there's a window shell available.
            if (mWindowShell != null) {
                MessageDialog.openError(mWindowShell,
                    "Android Virtual Devices Manager",
                    error);
            } else {
                mSdkLog.error(null /* Throwable */, "%s", error);  //$NON-NLS-1$
            }

            return true;
        }
        return false;
    }

    // -----

    /**
     * Initializes the {@link SdkManager} and the {@link AvdManager}.
     */
    @VisibleForTesting(visibility=Visibility.PRIVATE)
    protected void initSdk() {
        setSdkManager(SdkManager.createManager(mOsSdkRoot, mSdkLog));
        try {
            mAvdManager = null; // remove the old one if needed.
            mAvdManager = new AvdManager(mSdkManager, mSdkLog);
        } catch (AndroidLocationException e) {
            mSdkLog.error(e, "Unable to read AVDs: " + e.getMessage());  //$NON-NLS-1$

            // Note: we used to continue here, but the thing is that
            // mAvdManager==null so nothing is really going to work as
            // expected. Let's just display an error later in checkIfInitFailed()
            // and abort right there. This step is just too early in the SWT
            // setup process to display a message box yet.

            mAvdManagerInitError = e;
        }

        // notify listeners.
        broadcastOnSdkReload();
    }

    @VisibleForTesting(visibility=Visibility.PRIVATE)
    protected void setSdkManager(SdkManager sdkManager) {
        mSdkManager = sdkManager;
    }

    /**
     * Reloads the SDK content (targets).
     * <p/>
     * This also reloads the AVDs in case their status changed.
     * <p/>
     * This does not notify the listeners ({@link ISdkChangeListener}).
     */
    public void reloadSdk() {
        // reload SDK
        mSdkManager.reloadSdk(mSdkLog);

        // reload AVDs
        if (mAvdManager != null) {
            try {
                mAvdManager.reloadAvds(mSdkLog);
            } catch (AndroidLocationException e) {
                // FIXME
            }
        }

        mLocalSdkParser.clearPackages();

        // notify listeners
        broadcastOnSdkReload();
    }

    /**
     * Reloads the AVDs.
     * <p/>
     * This does not notify the listeners.
     */
    public void reloadAvds() {
        // reload AVDs
        if (mAvdManager != null) {
            try {
                mAvdManager.reloadAvds(mSdkLog);
            } catch (AndroidLocationException e) {
                mSdkLog.error(e, null);
            }
        }
    }

    /**
     * Sets up the default sources: <br/>
     * - the default google SDK repository, <br/>
     * - the user sources from prefs <br/>
     * - the extra repo URLs from the environment, <br/>
     * - and finally the extra user repo URLs from the environment.
     * <p/>
     * Note that the "remote add-ons" list is not loaded from here. Instead
     * it is fetched the first time the {@link RemotePackagesPage} is displayed.
     */
    public void setupDefaultSources() {
        SdkSources sources = getSources();

        // SDK_TEST_URLS is a semicolon-separated list of URLs that can be used to
        // seed the SDK Updater list for full repos and addon repositories. This is
        // only meant as a debugging and QA testing tool and not for user usage.
        //
        // To be used, the URLs must either end with the / or end with the canonical
        // filename expected for either a full repo or an add-on repo. This lets QA
        // use URLs ending with / to cover all cases.
        String testUrls = System.getenv("SDK_TEST_URLS");
        if (testUrls != null) {
            String[] urls = testUrls.split(";");
            for (String url : urls) {
                if (url != null) {
                    url = url.trim();
                    if (url.endsWith("/")
                            || url.endsWith(SdkRepoConstants.URL_DEFAULT_FILENAME)
                            || url.endsWith(SdkRepoConstants.URL_DEFAULT_FILENAME2)) {
                        String fullUrl = url;
                        if (fullUrl.endsWith("/")) {
                            fullUrl += SdkRepoConstants.URL_DEFAULT_FILENAME2;
                        }

                        SdkSource s = new SdkRepoSource(fullUrl, null/*uiName*/);
                        if (!sources.hasSourceUrl(s)) {
                            sources.add(SdkSourceCategory.GETENV_REPOS, s);
                        }
                    }

                    if (url.endsWith("/") || url.endsWith(SdkAddonConstants.URL_DEFAULT_FILENAME)) {
                        String fullUrl = url;
                        if (fullUrl.endsWith("/")) {
                            fullUrl += SdkAddonConstants.URL_DEFAULT_FILENAME;
                        }

                        SdkSource s = new SdkAddonSource(fullUrl, null/*uiName*/);
                        if (!sources.hasSourceUrl(s)) {
                            sources.add(SdkSourceCategory.GETENV_ADDONS, s);
                        }
                    }
                }
            }
        }

        // Load the conventional sources if we didn't load anything or if
        // there's an env var asking to do so anyway.
        if (sources.getAllSources().length == 0 ||
                System.getenv("SDK_MIX_WITH_TEST_URLS") != null) {
            sources.add(SdkSourceCategory.ANDROID_REPO,
                    new SdkRepoSource(SdkRepoConstants.URL_GOOGLE_SDK_SITE,
                                      SdkSourceCategory.ANDROID_REPO.getUiName()));

            // Load user sources
            sources.loadUserAddons(getSdkLog());
        }
    }

    /**
     * Returns the list of installed packages, parsing them if this has not yet been done.
     * <p/>
     * The package list is cached in the {@link LocalSdkParser} and will be reset when
     * {@link #reloadSdk()} is invoked.
     */
    public Package[] getInstalledPackages(ITaskMonitor monitor) {
        LocalSdkParser parser = getLocalSdkParser();

        Package[] packages = parser.getPackages();

        if (packages == null) {
            // load on demand the first time
            packages = parser.parseSdk(getOsSdkRoot(), getSdkManager(), monitor);
        }

        return packages;
    }
    /**
     * Install the list of given {@link Archive}s. This is invoked by the user selecting some
     * packages in the remote page and then clicking "install selected".
     *
     * @param archives The archives to install. Incompatible ones will be skipped.
     * @param flags Optional flags for the installer, such as {@link #NO_TOOLS_MSG}.
     * @return A list of archives that have been installed. Can be empty but not null.
     */
    @VisibleForTesting(visibility=Visibility.PRIVATE)
    protected List<Archive> installArchives(final List<ArchiveInfo> archives, final int flags) {
        if (mTaskFactory == null) {
            throw new IllegalArgumentException("Task Factory is null");
        }

        // this will accumulate all the packages installed.
        final List<Archive> newlyInstalledArchives = new ArrayList<Archive>();

        final boolean forceHttp = getSettingsController().getForceHttp();

        // sort all archives based on their dependency level.
        Collections.sort(archives, new InstallOrderComparator());

        mTaskFactory.start("Installing Archives", new ITask() {
            public void run(ITaskMonitor monitor) {

                final int progressPerArchive = 2 * ArchiveInstaller.NUM_MONITOR_INC;
                monitor.setProgressMax(1 + archives.size() * progressPerArchive);
                monitor.setDescription("Preparing to install archives");

                boolean installedAddon = false;
                boolean installedTools = false;
                boolean installedPlatformTools = false;
                boolean preInstallHookInvoked = false;

                // Mark all current local archives as already installed.
                HashSet<Archive> installedArchives = new HashSet<Archive>();
                for (Package p : getInstalledPackages(monitor.createSubMonitor(1))) {
                    for (Archive a : p.getArchives()) {
                        installedArchives.add(a);
                    }
                }

                int numInstalled = 0;
                nextArchive: for (ArchiveInfo ai : archives) {
                    Archive archive = ai.getNewArchive();
                    if (archive == null) {
                        // This is not supposed to happen.
                        continue nextArchive;
                    }

                    int nextProgress = monitor.getProgress() + progressPerArchive;
                    try {
                        if (monitor.isCancelRequested()) {
                            break;
                        }

                        ArchiveInfo[] adeps = ai.getDependsOn();
                        if (adeps != null) {
                            for (ArchiveInfo adep : adeps) {
                                Archive na = adep.getNewArchive();
                                if (na == null) {
                                    // This archive depends on a missing archive.
                                    // We shouldn't get here.
                                    // Skip it.
                                    monitor.log("Skipping '%1$s'; it depends on a missing package.",
                                            archive.getParentPackage().getShortDescription());
                                    continue nextArchive;
                                } else if (!installedArchives.contains(na)) {
                                    // This archive depends on another one that was not installed.
                                    // We shouldn't get here.
                                    // Skip it.
                                    monitor.logError("Skipping '%1$s'; it depends on '%2$s' which was not installed.",
                                            archive.getParentPackage().getShortDescription(),
                                            adep.getShortDescription());
                                    continue nextArchive;
                                }
                            }
                        }

                        if (!preInstallHookInvoked) {
                            preInstallHookInvoked = true;
                            broadcastPreInstallHook();
                        }

                        ArchiveInstaller installer = createArchiveInstaler();
                        if (installer.install(ai,
                                              mOsSdkRoot,
                                              forceHttp,
                                              mSdkManager,
                                              monitor)) {
                            // We installed this archive.
                            newlyInstalledArchives.add(archive);
                            installedArchives.add(archive);
                            numInstalled++;

                            // If this package was replacing an existing one, the old one
                            // is no longer installed.
                            installedArchives.remove(ai.getReplaced());

                            // Check if we successfully installed a platform-tool or add-on package.
                            if (archive.getParentPackage() instanceof AddonPackage) {
                                installedAddon = true;
                            } else if (archive.getParentPackage() instanceof ToolPackage) {
                                installedTools = true;
                            } else if (archive.getParentPackage() instanceof PlatformToolPackage) {
                                installedPlatformTools = true;
                            }
                        }

                    } catch (Throwable t) {
                        // Display anything unexpected in the monitor.
                        String msg = t.getMessage();
                        if (msg != null) {
                            msg = String.format("Unexpected Error installing '%1$s': %2$s: %3$s",
                                    archive.getParentPackage().getShortDescription(),
                                    t.getClass().getCanonicalName(), msg);
                        } else {
                            // no error info? get the stack call to display it
                            // At least that'll give us a better bug report.
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            t.printStackTrace(new PrintStream(baos));

                            msg = String.format("Unexpected Error installing '%1$s'\n%2$s",
                                    archive.getParentPackage().getShortDescription(),
                                    baos.toString());
                        }

                        monitor.log(     "%1$s", msg);      //$NON-NLS-1$
                        mSdkLog.error(t, "%1$s", msg);      //$NON-NLS-1$
                    } finally {

                        // Always move the progress bar to the desired position.
                        // This allows internal methods to not have to care in case
                        // they abort early
                        monitor.incProgress(nextProgress - monitor.getProgress());
                    }
                }

                if (installedAddon) {
                    // Update the USB vendor ids for adb
                    try {
                        mSdkManager.updateAdb();
                        monitor.log("Updated ADB to support the USB devices declared in the SDK add-ons.");
                    } catch (Exception e) {
                        mSdkLog.error(e, "Update ADB failed");
                        monitor.logError("failed to update adb to support the USB devices declared in the SDK add-ons.");
                    }
                }

                if (preInstallHookInvoked) {
                    broadcastPostInstallHook();
                }

                if (installedAddon || installedPlatformTools) {
                    // We need to restart ADB. Actually since we don't know if it's even
                    // running, maybe we should just kill it and not start it.
                    // Note: it turns out even under Windows we don't need to kill adb
                    // before updating the tools folder, as adb.exe is (surprisingly) not
                    // locked.

                    askForAdbRestart(monitor);
                }

                if (installedTools) {
                    notifyToolsNeedsToBeRestarted(flags);
                }

                if (numInstalled == 0) {
                    monitor.setDescription("Done. Nothing was installed.");
                } else {
                    monitor.setDescription("Done. %1$d %2$s installed.",
                            numInstalled,
                            numInstalled == 1 ? "package" : "packages");

                    //notify listeners something was installed, so that they can refresh
                    reloadSdk();
                }
            }
        });

        return newlyInstalledArchives;
    }

    /**
     * A comparator to sort all the {@link ArchiveInfo} based on their
     * dependency level. This forces the installer to install first all packages
     * with no dependency, then those with one level of dependency, etc.
     */
    private static class InstallOrderComparator implements Comparator<ArchiveInfo> {

        private final Map<ArchiveInfo, Integer> mOrders = new HashMap<ArchiveInfo, Integer>();

        public int compare(ArchiveInfo o1, ArchiveInfo o2) {
            int n1 = getDependencyOrder(o1);
            int n2 = getDependencyOrder(o2);

            return n1 - n2;
        }

        private int getDependencyOrder(ArchiveInfo ai) {
            if (ai == null) {
                return 0;
            }

            // reuse cached value, if any
            Integer cached = mOrders.get(ai);
            if (cached != null) {
                return cached.intValue();
            }

            ArchiveInfo[] deps = ai.getDependsOn();
            if (deps == null) {
                return 0;
            }

            // compute dependencies, recursively
            int n = deps.length;

            for (ArchiveInfo dep : deps) {
                n += getDependencyOrder(dep);
            }

            // cache it
            mOrders.put(ai, Integer.valueOf(n));

            return n;
        }

    }

    /**
     * Attempts to restart ADB.
     * <p/>
     * If the "ask before restart" setting is set (the default), prompt the user whether
     * now is a good time to restart ADB.
     *
     * @param monitor
     */
    private void askForAdbRestart(ITaskMonitor monitor) {
        final boolean[] canRestart = new boolean[] { true };

        if (getWindowShell() != null && getSettingsController().getAskBeforeAdbRestart()) {
            // need to ask for permission first
            final Shell shell = getWindowShell();
            if (shell != null && !shell.isDisposed()) {
                shell.getDisplay().syncExec(new Runnable() {
                    public void run() {
                        if (!shell.isDisposed()) {
                            canRestart[0] = MessageDialog.openQuestion(shell,
                                    "ADB Restart",
                                    "A package that depends on ADB has been updated. \n" +
                                    "Do you want to restart ADB now?");
                        }
                    }
                });
            }
        }

        if (canRestart[0]) {
            AdbWrapper adb = new AdbWrapper(getOsSdkRoot(), monitor);
            adb.stopAdb();
            adb.startAdb();
        }
    }

    private void notifyToolsNeedsToBeRestarted(int flags) {

        String msg = null;
        if ((flags & TOOLS_MSG_UPDATED_FROM_ADT) != 0) {
            msg =
            "The Android SDK and AVD Manager that you are currently using has been updated. " +
            "Please also run Eclipse > Help > Check for Updates to see if the Android " +
            "plug-in needs to be updated.";

        } else if ((flags & TOOLS_MSG_UPDATED_FROM_SDKMAN) != 0) {
            msg =
            "The Android SDK and AVD Manager that you are currently using has been updated. " +
            "It is recommended that you now close the manager window and re-open it. " +
            "If you use Eclipse, please run Help > Check for Updates to see if the Android " +
            "plug-in needs to be updated.";
        }

        final String msg2 = msg;

        final Shell shell = getWindowShell();
        if (msg2 != null && shell != null && !shell.isDisposed()) {
            shell.getDisplay().syncExec(new Runnable() {
                public void run() {
                    if (!shell.isDisposed()) {
                        MessageDialog.openInformation(shell,
                                "Android Tools Updated",
                                msg2);
                    }
                }
            });
        }
    }


    /**
     * Tries to update all the *existing* local packages.
     * This version *requires* to be run with a GUI.
     * <p/>
     * There are two modes of operation:
     * <ul>
     * <li>If selectedArchives is null, refreshes all sources, compares the available remote
     * packages with the current local ones and suggest updates to be done to the user (including
     * new platforms that the users doesn't have yet).
     * <li>If selectedArchives is not null, this represents a list of archives/packages that
     * the user wants to install or update, so just process these.
     * </ul>
     *
     * @param selectedArchives The list of remote archives to consider for the update.
     *  This can be null, in which case a list of remote archive is fetched from all
     *  available sources.
     * @param includeObsoletes True if obsolete packages should be used when resolving what
     *  to update.
     * @param flags Optional flags for the installer, such as {@link #NO_TOOLS_MSG}.
     * @return A list of archives that have been installed. Can be null if nothing was done.
     */
    public List<Archive> updateOrInstallAll_WithGUI(
            Collection<Archive> selectedArchives,
            boolean includeObsoletes,
            int flags) {

        // Note: we no longer call refreshSources(true) here. This will be done
        // automatically by computeUpdates() iif it needs to access sources to
        // resolve missing dependencies.

        SdkUpdaterLogic ul = new SdkUpdaterLogic(this);
        List<ArchiveInfo> archives = ul.computeUpdates(
                selectedArchives,
                getSources(),
                getLocalSdkParser().getPackages(),
                includeObsoletes);

        if (selectedArchives == null) {
            loadRemoteAddonsList(new NullTaskMonitor(getSdkLog()));
            ul.addNewPlatforms(
                    archives,
                    getSources(),
                    getLocalSdkParser().getPackages(),
                    includeObsoletes);
        }

        // TODO if selectedArchives is null and archives.len==0, find if there are
        // any new platform we can suggest to install instead.

        Collections.sort(archives);

        SdkUpdaterChooserDialog dialog =
            new SdkUpdaterChooserDialog(getWindowShell(), this, archives);
        dialog.open();

        ArrayList<ArchiveInfo> result = dialog.getResult();
        if (result != null && result.size() > 0) {
            return installArchives(result, flags);
        }
        return null;
    }

    /**
     * Fetches all archives available on the known remote sources.
     *
     * Used by {@link UpdaterData#listRemotePackages_NoGUI} and
     * {@link UpdaterData#updateOrInstallAll_NoGUI}.
     *
     * @param includeObsoletes True to also list obsolete packages.
     * @return A list of potential {@link ArchiveInfo} to install.
     */
    private List<ArchiveInfo> getRemoteArchives_NoGUI(boolean includeObsoletes) {
        refreshSources(true);
        loadRemoteAddonsList(new NullTaskMonitor(getSdkLog()));

        SdkUpdaterLogic ul = new SdkUpdaterLogic(this);
        List<ArchiveInfo> archives = ul.computeUpdates(
                null /*selectedArchives*/,
                getSources(),
                getLocalSdkParser().getPackages(),
                includeObsoletes);

        ul.addNewPlatforms(
                archives,
                getSources(),
                getLocalSdkParser().getPackages(),
                includeObsoletes);

        Collections.sort(archives);
        return archives;
    }

    /**
     * Lists remote packages available for install using
     * {@link UpdaterData#updateOrInstallAll_NoGUI}.
     *
     * @param includeObsoletes True to also list obsolete packages.
     * @param extendedOutput True to display more details on each package.
     */
    public void listRemotePackages_NoGUI(boolean includeObsoletes, boolean extendedOutput) {

        List<ArchiveInfo> archives = getRemoteArchives_NoGUI(includeObsoletes);

        mSdkLog.printf("Packages available for installation or update: %1$d\n", archives.size());

        int index = 1;
        for (ArchiveInfo ai : archives) {
            Archive a = ai.getNewArchive();
            if (a != null) {
                Package p = a.getParentPackage();
                if (p != null) {
                    if (extendedOutput) {
                        mSdkLog.printf("----------\n");
                        mSdkLog.printf("id: %1$d or \"%2$s\"\n", index, p.installId());
                        mSdkLog.printf("     Type: %1$s\n",
                                p.getClass().getSimpleName().replaceAll("Package", "")); //$NON-NLS-1$ //$NON-NLS-2$
                        String desc = LineUtil.reformatLine("     Desc: %s\n",
                                p.getLongDescription());
                        mSdkLog.printf("%s", desc); //$NON-NLS-1$
                    } else {
                        mSdkLog.printf("%1$ 4d- %2$s\n",
                                index,
                                p.getShortDescription());
                    }
                    index++;
                }
            }
        }
    }

    /**
     * Tries to update all the *existing* local packages.
     * This version is intended to run without a GUI and
     * only outputs to the current {@link ISdkLog}.
     *
     * @param pkgFilter A list of {@link SdkRepoConstants#NODES} or {@link Package#installId()}
     *   or package indexes to limit the packages we can update or install.
     *   A null or empty list means to update everything possible.
     * @param includeObsoletes True to also list and install obsolete packages.
     * @param dryMode True to check what would be updated/installed but do not actually
     *   download or install anything.
     * @return A list of archives that have been installed. Can be null if nothing was done.
     */
    public List<Archive> updateOrInstallAll_NoGUI(
            Collection<String> pkgFilter,
            boolean includeObsoletes,
            boolean dryMode) {

        List<ArchiveInfo> archives = getRemoteArchives_NoGUI(includeObsoletes);

        // Filter the selected archives to only keep the ones matching the filter
        if (pkgFilter != null && pkgFilter.size() > 0 && archives != null && archives.size() > 0) {
            // Map filter types to an SdkRepository Package type,
            // e.g. create a map "platform" => PlatformPackage.class
            HashMap<String, Class<? extends Package>> pkgMap =
                new HashMap<String, Class<? extends Package>>();

            mapFilterToPackageClass(pkgMap, SdkRepoConstants.NODES);
            mapFilterToPackageClass(pkgMap, SdkAddonConstants.NODES);

            // Prepare a map install-id => package instance
            HashMap<String, Package> installIdMap = new HashMap<String, Package>();
            for (ArchiveInfo ai : archives) {
                Archive a = ai.getNewArchive();
                if (a != null) {
                    Package p = a.getParentPackage();
                    if (p != null) {
                        String id = p.installId();
                        if (id != null && id.length() > 0 && !installIdMap.containsKey(id)) {
                            installIdMap.put(id, p);
                        }
                    }
                }
            }

            // Now intersect this with the pkgFilter requested by the user, in order to
            // only keep the classes that the user wants to install.
            // We also create a set with the package indices requested by the user
            // and a set of install-ids requested by the user.

            HashSet<Class<? extends Package>> userFilteredClasses =
                new HashSet<Class<? extends Package>>();
            SparseIntArray userFilteredIndices = new SparseIntArray();
            Set<String> userFilteredInstallIds = new HashSet<String>();

            for (String type : pkgFilter) {
                if (installIdMap.containsKey(type)) {
                    userFilteredInstallIds.add(type);

                } else if (type.replaceAll("[0-9]+", "").length() == 0) {//$NON-NLS-1$ //$NON-NLS-2$
                    // An all-digit number is a package index requested by the user.
                    int index = Integer.parseInt(type);
                    userFilteredIndices.put(index, index);

                } else if (pkgMap.containsKey(type)) {
                    userFilteredClasses.add(pkgMap.get(type));

                } else {
                    // This should not happen unless there's a mismatch in the package map.
                    mSdkLog.error(null, "Ignoring unknown package filter '%1$s'", type);
                }
            }

            // we don't need the maps anymore
            pkgMap = null;
            installIdMap = null;

            // Now filter the remote archives list to keep:
            // - any package which class matches userFilteredClasses
            // - any package index which matches userFilteredIndices
            // - any package install id which matches userFilteredInstallIds

            int index = 1;
            for (Iterator<ArchiveInfo> it = archives.iterator(); it.hasNext(); ) {
                boolean keep = false;
                ArchiveInfo ai = it.next();
                Archive a = ai.getNewArchive();
                if (a != null) {
                    Package p = a.getParentPackage();
                    if (p != null) {
                        if (userFilteredInstallIds.contains(p.installId()) ||
                                userFilteredClasses.contains(p.getClass()) ||
                                userFilteredIndices.get(index) > 0) {
                            keep = true;
                        }

                        index++;
                    }
                }

                if (!keep) {
                    it.remove();
                }
            }

            if (archives.size() == 0) {
                mSdkLog.printf(LineUtil.reflowLine(
                        "Warning: The package filter removed all packages. There is nothing to install.\nPlease consider trying to update again without a package filter.\n"));
                return null;
            }
        }

        if (archives != null && archives.size() > 0) {
            if (dryMode) {
                mSdkLog.printf("Packages selected for install:\n");
                for (ArchiveInfo ai : archives) {
                    Archive a = ai.getNewArchive();
                    if (a != null) {
                        Package p = a.getParentPackage();
                        if (p != null) {
                            mSdkLog.printf("- %1$s\n", p.getShortDescription());
                        }
                    }
                }
                mSdkLog.printf("\nDry mode is on so nothing is actually being installed.\n");
            } else {
                return installArchives(archives, NO_TOOLS_MSG);
            }
        } else {
            mSdkLog.printf("There is nothing to install or update.\n");
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private void mapFilterToPackageClass(
            HashMap<String, Class<? extends Package>> inOutPkgMap,
            String[] nodes) {

        // Automatically find the classes matching the node names
        ClassLoader classLoader = getClass().getClassLoader();
        String basePackage = Package.class.getPackage().getName();

        for (String node : nodes) {
            // Capitalize the name
            String name = node.substring(0, 1).toUpperCase() + node.substring(1);

            // We can have one dash at most in a name. If it's present, we'll try
            // with the dash or with the next letter capitalized.
            int dash = name.indexOf('-');
            if (dash > 0) {
                name = name.replaceFirst("-", "");
            }

            for (int alternatives = 0; alternatives < 2; alternatives++) {

                String fqcn = basePackage + '.' + name + "Package";  //$NON-NLS-1$
                try {
                    Class<? extends Package> clazz =
                        (Class<? extends Package>) classLoader.loadClass(fqcn);
                    if (clazz != null) {
                        inOutPkgMap.put(node, clazz);
                        continue;
                    }
                } catch (Throwable ignore) {
                }

                if (alternatives == 0 && dash > 0) {
                    // Try an alternative where the next letter after the dash
                    // is converted to an upper case.
                    name = name.substring(0, dash) +
                           name.substring(dash, dash + 1).toUpperCase() +
                           name.substring(dash + 1);
                } else {
                    break;
                }
            }
        }
    }

    /**
     * Refresh all sources. This is invoked either internally (reusing an existing monitor)
     * or as a UI callback on the remote page "Refresh" button (in which case the monitor is
     * null and a new task should be created.)
     *
     * @param forceFetching When true, load sources that haven't been loaded yet.
     *                      When false, only refresh sources that have been loaded yet.
     */
    public void refreshSources(final boolean forceFetching) {
        assert mTaskFactory != null;

        final boolean forceHttp = getSettingsController().getForceHttp();

        mTaskFactory.start("Refresh Sources", new ITask() {
            public void run(ITaskMonitor monitor) {

                if (mStateFetchRemoteAddonsList <= 0) {
                    loadRemoteAddonsListInTask(monitor);
                }

                SdkSource[] sources = mSources.getAllSources();
                monitor.setDescription("Refresh Sources");
                monitor.setProgressMax(monitor.getProgress() + sources.length);
                for (SdkSource source : sources) {
                    if (forceFetching ||
                            source.getPackages() != null ||
                            source.getFetchError() != null) {
                        source.load(monitor.createSubMonitor(1), forceHttp);
                    }
                    monitor.incProgress(1);
                }
            }
        });
    }

    /**
     * Loads the remote add-ons list.
     */
    public void loadRemoteAddonsList(ITaskMonitor monitor) {

        if (mStateFetchRemoteAddonsList != 0) {
            return;
        }

        mTaskFactory.start("Load Add-ons List", monitor, new ITask() {
            public void run(ITaskMonitor subMonitor) {
                loadRemoteAddonsListInTask(subMonitor);
            }
        });
    }

    private void loadRemoteAddonsListInTask(ITaskMonitor monitor) {
        mStateFetchRemoteAddonsList = -1;

        // SDK_TEST_URLS is a semicolon-separated list of URLs that can be used to
        // seed the SDK Updater list. This is only meant as a debugging and QA testing
        // tool and not for user usage.
        //
        // To be used, the URLs must either end with the / or end with the canonical
        // filename expected for an addon list. This lets QA use URLs ending with /
        // to cover all cases.
        //
        // Since SDK_TEST_URLS can contain many such URLs, we take the first one that
        // matches our criteria.
        String url = System.getenv("SDK_TEST_URLS");    //$NON-NLS-1$

        if (url == null) {
            // No override, use the canonical URL.
            url = SdkAddonsListConstants.URL_ADDON_LIST;
        } else {
            String[] urls = url.split(";");             //$NON-NLS-1$
            url = null;
            for (String u : urls) {
                u = u.trim();
                // This is an URL that comes from the env var. We expect it to either
                // end with a / or the canonical name, otherwise we don't use it.
                if (u.endsWith("/")) {                  //$NON-NLS-1$
                    url = u + SdkAddonsListConstants.URL_DEFAULT_FILENAME;
                    break;
                } else if (u.endsWith(SdkAddonsListConstants.URL_DEFAULT_FILENAME)) {
                    url = u;
                    break;
                }
            }
        }

        if (url != null) {
            if (getSettingsController().getForceHttp()) {
                url = url.replaceAll("https://", "http://");    //$NON-NLS-1$ //$NON-NLS-2$
            }

            AddonsListFetcher fetcher = new AddonsListFetcher();
            Site[] sites = fetcher.fetch(monitor, url);
            if (sites != null) {
                mSources.removeAll(SdkSourceCategory.ADDONS_3RD_PARTY);

                for (Site s : sites) {
                    mSources.add(SdkSourceCategory.ADDONS_3RD_PARTY,
                                 new SdkAddonSource(s.getUrl(), s.getUiName()));
                }

                mStateFetchRemoteAddonsList = 1;
            }
        }

        monitor.setDescription("Fetched Add-ons List successfully");
    }

    /**
     * Safely invoke all the registered {@link ISdkChangeListener#onSdkLoaded()}.
     * This can be called from any thread.
     */
    public void broadcastOnSdkLoaded() {
        if (mWindowShell != null && mListeners.size() > 0) {
            mWindowShell.getDisplay().syncExec(new Runnable() {
                public void run() {
                    for (ISdkChangeListener listener : mListeners) {
                        try {
                            listener.onSdkLoaded();
                        } catch (Throwable t) {
                            mSdkLog.error(t, null);
                        }
                    }
                }
            });
        }
    }

    /**
     * Safely invoke all the registered {@link ISdkChangeListener#onSdkReload()}.
     * This can be called from any thread.
     */
    private void broadcastOnSdkReload() {
        if (mWindowShell != null && mListeners.size() > 0) {
            mWindowShell.getDisplay().syncExec(new Runnable() {
                public void run() {
                    for (ISdkChangeListener listener : mListeners) {
                        try {
                            listener.onSdkReload();
                        } catch (Throwable t) {
                            mSdkLog.error(t, null);
                        }
                    }
                }
            });
        }
    }

    /**
     * Safely invoke all the registered {@link ISdkChangeListener#preInstallHook()}.
     * This can be called from any thread.
     */
    private void broadcastPreInstallHook() {
        if (mWindowShell != null && mListeners.size() > 0) {
            mWindowShell.getDisplay().syncExec(new Runnable() {
                public void run() {
                    for (ISdkChangeListener listener : mListeners) {
                        try {
                            listener.preInstallHook();
                        } catch (Throwable t) {
                            mSdkLog.error(t, null);
                        }
                    }
                }
            });
        }
    }

    /**
     * Safely invoke all the registered {@link ISdkChangeListener#postInstallHook()}.
     * This can be called from any thread.
     */
    private void broadcastPostInstallHook() {
        if (mWindowShell != null && mListeners.size() > 0) {
            mWindowShell.getDisplay().syncExec(new Runnable() {
                public void run() {
                    for (ISdkChangeListener listener : mListeners) {
                        try {
                            listener.postInstallHook();
                        } catch (Throwable t) {
                            mSdkLog.error(t, null);
                        }
                    }
                }
            });
        }
    }

    /**
     * Internal helper to return a new {@link ArchiveInstaller}.
     * This allows us to override the installer for unit-testing.
     */
    @VisibleForTesting(visibility=Visibility.PRIVATE)
    protected ArchiveInstaller createArchiveInstaler() {
        return new ArchiveInstaller();
    }

}
