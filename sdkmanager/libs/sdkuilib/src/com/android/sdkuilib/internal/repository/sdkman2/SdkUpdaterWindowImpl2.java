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


import com.android.sdklib.ISdkLog;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.internal.repository.ITaskFactory;
import com.android.sdkuilib.internal.repository.ISdkUpdaterWindow;
import com.android.sdkuilib.internal.repository.ISettingsPage;
import com.android.sdkuilib.internal.repository.MenuBarWrapper;
import com.android.sdkuilib.internal.repository.SettingsController;
import com.android.sdkuilib.internal.repository.UpdaterData;
import com.android.sdkuilib.internal.repository.UpdaterPage;
import com.android.sdkuilib.internal.repository.UpdaterPage.Purpose;
import com.android.sdkuilib.internal.repository.icons.ImageFactory;
import com.android.sdkuilib.internal.repository.sdkman2.PackagesPage.MenuAction;
import com.android.sdkuilib.internal.tasks.ILogUiProvider;
import com.android.sdkuilib.internal.tasks.ProgressView;
import com.android.sdkuilib.internal.tasks.ProgressViewFactory;
import com.android.sdkuilib.internal.widgets.ImgDisabledButton;
import com.android.sdkuilib.internal.widgets.ToggleButton;
import com.android.sdkuilib.repository.ISdkChangeListener;
import com.android.sdkuilib.repository.AvdManagerWindow.AvdInvocationContext;
import com.android.sdkuilib.repository.SdkUpdaterWindow.SdkInvocationContext;
import com.android.sdkuilib.ui.GridDataBuilder;
import com.android.sdkuilib.ui.GridLayoutBuilder;
import com.android.sdkuilib.ui.SwtBaseDialog;
import com.android.util.Pair;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;

import java.util.ArrayList;

/**
 * This is the private implementation of the UpdateWindow
 * for the second version of the SDK Manager.
 * <p/>
 * This window features only one embedded page, the combined installed+available package list.
 */
public class SdkUpdaterWindowImpl2 implements ISdkUpdaterWindow {

    private static final String APP_NAME = "Android SDK Manager";
    private static final String SIZE_POS_PREFIX = "sdkman2"; //$NON-NLS-1$

    private final Shell mParentShell;
    private final SdkInvocationContext mContext;
    /** Internal data shared between the window and its pages. */
    private final UpdaterData mUpdaterData;
    /** A list of extra pages to instantiate. Each entry is an object array with 2 elements:
     *  the string title and the Composite class to instantiate to create the page. */
    private ArrayList<Pair<Class<? extends UpdaterPage>, Purpose>> mExtraPages;
    /** Sets whether the auto-update wizard will be shown when opening the window. */
    private boolean mRequestAutoUpdate;

    // --- UI members ---

    protected Shell mShell;
    private PackagesPage mPkgPage;
    private ProgressBar mProgressBar;
    private Label mStatusText;
    private ImgDisabledButton mButtonStop;
    private ToggleButton mButtonShowLog;
    private SettingsController mSettingsController;
    private LogWindow mLogWindow;

    /**
     * Creates a new window. Caller must call open(), which will block.
     *
     * @param parentShell Parent shell.
     * @param sdkLog Logger. Cannot be null.
     * @param osSdkRoot The OS path to the SDK root.
     * @param context The {@link SdkInvocationContext} to change the behavior depending on who's
     *  opening the SDK Manager.
     */
    public SdkUpdaterWindowImpl2(
            Shell parentShell,
            ISdkLog sdkLog,
            String osSdkRoot,
            SdkInvocationContext context) {
        mParentShell = parentShell;
        mContext = context;
        mUpdaterData = new UpdaterData(osSdkRoot, sdkLog);
    }

    /**
     * Creates a new window. Caller must call open(), which will block.
     * <p/>
     * This is to be used when the window is opened from {@link AvdManagerWindowImpl1}
     * to share the same {@link UpdaterData} structure.
     *
     * @param parentShell Parent shell.
     * @param updaterData The parent's updater data.
     * @param context The {@link SdkInvocationContext} to change the behavior depending on who's
     *  opening the SDK Manager.
     */
    public SdkUpdaterWindowImpl2(
            Shell parentShell,
            UpdaterData updaterData,
            SdkInvocationContext context) {
        mParentShell = parentShell;
        mContext = context;
        mUpdaterData = updaterData;
    }

    /**
     * Opens the window.
     * @wbp.parser.entryPoint
     */
    public void open() {
        if (mParentShell == null) {
            Display.setAppName(APP_NAME); //$hide$ (hide from SWT designer)
        }

        createShell();
        preCreateContent();
        createContents();
        createMenuBar();
        createLogWindow();
        mShell.open();
        mShell.layout();

        if (postCreateContent()) {    //$hide$ (hide from SWT designer)
            Display display = Display.getDefault();
            while (!mShell.isDisposed()) {
                if (!display.readAndDispatch()) {
                    display.sleep();
                }
            }
        }

        dispose();  //$hide$
    }

    private void createShell() {
        mShell = new Shell(mParentShell, SWT.SHELL_TRIM | SWT.APPLICATION_MODAL);
        mShell.addDisposeListener(new DisposeListener() {
            public void widgetDisposed(DisposeEvent e) {
                ShellSizeAndPos.saveSizeAndPos(mShell, SIZE_POS_PREFIX);
                onAndroidSdkUpdaterDispose();    //$hide$ (hide from SWT designer)
            }
        });

        GridLayout glShell = new GridLayout(2, false);
        glShell.verticalSpacing = 0;
        glShell.horizontalSpacing = 0;
        glShell.marginWidth = 0;
        glShell.marginHeight = 0;
        mShell.setLayout(glShell);

        mShell.setMinimumSize(new Point(500, 300));
        mShell.setSize(700, 500);
        mShell.setText(APP_NAME);

        ShellSizeAndPos.loadSizeAndPos(mShell, SIZE_POS_PREFIX);
    }

    private void createContents() {

        mPkgPage = new PackagesPage(mShell, SWT.NONE, mUpdaterData, mContext);
        mPkgPage.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));

        Composite composite1 = new Composite(mShell, SWT.NONE);
        composite1.setLayout(new GridLayout(1, false));
        composite1.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

        mProgressBar = new ProgressBar(composite1, SWT.NONE);
        mProgressBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

        mStatusText = new Label(composite1, SWT.NONE);
        mStatusText.setText("Status Placeholder");  //$NON-NLS-1$ placeholder
        mStatusText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

        Composite composite2 = new Composite(mShell, SWT.NONE);
        composite2.setLayout(new GridLayout(2, false));

        mButtonStop = new ImgDisabledButton(composite2, SWT.NONE,
                getImage("stop_enabled_16.png"),    //$NON-NLS-1$
                getImage("stop_disabled_16.png"),   //$NON-NLS-1$
                "Click to abort the current task",
                "");                                //$NON-NLS-1$ nothing to abort
        mButtonStop.addListener(SWT.Selection, new Listener() {
            public void handleEvent(Event event) {
                onStopSelected();
            }
        });

        mButtonShowLog = new ToggleButton(composite2, SWT.NONE,
                getImage("log_off_16.png"),         //$NON-NLS-1$
                getImage("log_on_16.png"),          //$NON-NLS-1$
                "Click to show the log window",     // tooltip for state hidden=>shown
                "Click to hide the log window");    // tooltip for state shown=>hidden
        mButtonShowLog.addListener(SWT.Selection, new Listener() {
            public void handleEvent(Event event) {
                onToggleLogWindow();
            }
        });
    }

    private void createMenuBar() {

        Menu menuBar = new Menu(mShell, SWT.BAR);
        mShell.setMenuBar(menuBar);

        MenuItem menuBarPackages = new MenuItem(menuBar, SWT.CASCADE);
        menuBarPackages.setText("Packages");

        Menu menuPkgs = new Menu(menuBarPackages);
        menuBarPackages.setMenu(menuPkgs);

        MenuItem showUpdatesNew = new MenuItem(menuPkgs,
                MenuAction.TOGGLE_SHOW_UPDATE_NEW_PKG.getMenuStyle());
        showUpdatesNew.setText(
                MenuAction.TOGGLE_SHOW_UPDATE_NEW_PKG.getMenuTitle());
        mPkgPage.registerMenuAction(
                MenuAction.TOGGLE_SHOW_UPDATE_NEW_PKG, showUpdatesNew);

        MenuItem showInstalled = new MenuItem(menuPkgs,
                MenuAction.TOGGLE_SHOW_INSTALLED_PKG.getMenuStyle());
        showInstalled.setText(
                MenuAction.TOGGLE_SHOW_INSTALLED_PKG.getMenuTitle());
        mPkgPage.registerMenuAction(
                MenuAction.TOGGLE_SHOW_INSTALLED_PKG, showInstalled);

        MenuItem showObsoletePackages = new MenuItem(menuPkgs,
                MenuAction.TOGGLE_SHOW_OBSOLETE_PKG.getMenuStyle());
        showObsoletePackages.setText(
                MenuAction.TOGGLE_SHOW_OBSOLETE_PKG.getMenuTitle());
        mPkgPage.registerMenuAction(
                MenuAction.TOGGLE_SHOW_OBSOLETE_PKG, showObsoletePackages);

        MenuItem showArchives = new MenuItem(menuPkgs,
                MenuAction.TOGGLE_SHOW_ARCHIVES.getMenuStyle());
        showArchives.setText(
                MenuAction.TOGGLE_SHOW_ARCHIVES.getMenuTitle());
        mPkgPage.registerMenuAction(
                MenuAction.TOGGLE_SHOW_ARCHIVES, showArchives);

        new MenuItem(menuPkgs, SWT.SEPARATOR);

        MenuItem sortByApi = new MenuItem(menuPkgs,
                MenuAction.SORT_API_LEVEL.getMenuStyle());
        sortByApi.setText(
                MenuAction.SORT_API_LEVEL.getMenuTitle());
        mPkgPage.registerMenuAction(
                MenuAction.SORT_API_LEVEL, sortByApi);

        MenuItem sortBySource = new MenuItem(menuPkgs,
                MenuAction.SORT_SOURCE.getMenuStyle());
        sortBySource.setText(
                MenuAction.SORT_SOURCE.getMenuTitle());
        mPkgPage.registerMenuAction(
                MenuAction.SORT_SOURCE, sortBySource);

        new MenuItem(menuPkgs, SWT.SEPARATOR);

        MenuItem reload = new MenuItem(menuPkgs,
                MenuAction.RELOAD.getMenuStyle());
        reload.setText(
                MenuAction.RELOAD.getMenuTitle());
        mPkgPage.registerMenuAction(
                MenuAction.RELOAD, reload);

        MenuItem menuBarTools = new MenuItem(menuBar, SWT.CASCADE);
        menuBarTools.setText("Tools");

        Menu menuTools = new Menu(menuBarTools);
        menuBarTools.setMenu(menuTools);

        if (mContext == SdkInvocationContext.STANDALONE) {
            MenuItem manageAvds = new MenuItem(menuTools, SWT.NONE);
            manageAvds.setText("Manage AVDs...");
            manageAvds.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent event) {
                    onAvdManager();
                }
            });
        }

        MenuItem manageSources = new MenuItem(menuTools,
                MenuAction.SHOW_ADDON_SITES.getMenuStyle());
        manageSources.setText(
                MenuAction.SHOW_ADDON_SITES.getMenuTitle());
        mPkgPage.registerMenuAction(
                MenuAction.SHOW_ADDON_SITES, manageSources);

        if (mContext == SdkInvocationContext.STANDALONE) {
            // Note: when invoked from an IDE, the SwtMenuBar library isn't
            // available. This means this source should not directly import
            // any of SwtMenuBar classes, otherwise the whole window class
            // would fail to load. The MenuBarWrapper below helps to make
            // that indirection.

            try {
                new MenuBarWrapper(APP_NAME, menuTools) {
                    @Override
                    public void onPreferencesMenuSelected() {
                        showRegisteredPage(Purpose.SETTINGS);
                    }

                    @Override
                    public void onAboutMenuSelected() {
                        showRegisteredPage(Purpose.ABOUT_BOX);
                    }

                    @Override
                    public void printError(String format, Object... args) {
                        if (mUpdaterData != null) {
                            mUpdaterData.getSdkLog().error(null, format, args);
                        }
                    }
                };
            } catch (Exception e) {
                mUpdaterData.getSdkLog().error(e, "Failed to setup menu bar");
                e.printStackTrace();
            }
        }
    }

    private Image getImage(String filename) {
        if (mUpdaterData != null) {
            ImageFactory imgFactory = mUpdaterData.getImageFactory();
            if (imgFactory != null) {
                return imgFactory.getImageByName(filename);
            }
        }
        return null;
    }

    /**
     * Creates the log window.
     * <p/>
     * If this is invoked from an IDE, we also define a secondary logger so that all
     * messages flow to the IDE log. This may or may not be what we want in the end
     * (e.g. a middle ground would be to repeat error, and ignore normal/verbose)
     */
    private void createLogWindow() {
        mLogWindow = new LogWindow(mShell,
                mContext == SdkInvocationContext.IDE ? mUpdaterData.getSdkLog() : null);
        mLogWindow.open();
    }


    // -- Start of internal part ----------
    // Hide everything down-below from SWT designer
    //$hide>>$

    // --- Public API -----------


    /**
     * Registers an extra page for the updater window.
     * <p/>
     * Pages must derive from {@link Composite} and implement a constructor that takes
     * a single parent {@link Composite} argument.
     * <p/>
     * All pages must be registered before the call to {@link #open()}.
     *
     * @param pageClass The {@link Composite}-derived class that will implement the page.
     * @param purpose The purpose of this page, e.g. an about box, settings page or generic.
     */
    @SuppressWarnings("unchecked")
    public void registerPage(Class<? extends UpdaterPage> pageClass,
            Purpose purpose) {
        if (mExtraPages == null) {
            mExtraPages = new ArrayList<Pair<Class<? extends UpdaterPage>, Purpose>>();
        }
        Pair<?, Purpose> value = Pair.of(pageClass, purpose);
        mExtraPages.add((Pair<Class<? extends UpdaterPage>, Purpose>) value);
    }

    /**
     * Indicate the initial page that should be selected when the window opens.
     * This must be called before the call to {@link #open()}.
     * If null or if the page class is not found, the first page will be selected.
     */
    public void setInitialPage(Class<? extends Composite> pageClass) {
        // Unused in this case. This window display only one page.
    }

    /**
     * Sets whether the auto-update wizard will be shown when opening the window.
     * <p/>
     * This must be called before the call to {@link #open()}.
     */
    public void setRequestAutoUpdate(boolean requestAutoUpdate) {
        mRequestAutoUpdate = requestAutoUpdate;
    }

    /**
     * Adds a new listener to be notified when a change is made to the content of the SDK.
     */
    public void addListener(ISdkChangeListener listener) {
        mUpdaterData.addListeners(listener);
    }

    /**
     * Removes a new listener to be notified anymore when a change is made to the content of
     * the SDK.
     */
    public void removeListener(ISdkChangeListener listener) {
        mUpdaterData.removeListener(listener);
    }

    // --- Internals & UI Callbacks -----------

    /**
     * Called before the UI is created.
     */
    private void preCreateContent() {
        mUpdaterData.setWindowShell(mShell);
        // We need the UI factory to create the UI
        mUpdaterData.setImageFactory(new ImageFactory(mShell.getDisplay()));
        // Note: we can't create the TaskFactory yet because we need the UI
        // to be created first, so this is done in postCreateContent().
    }

    /**
     * Once the UI has been created, initializes the content.
     * This creates the pages, selects the first one, setups sources and scans for local folders.
     *
     * Returns true if we should show the window.
     */
    private boolean postCreateContent() {
        ProgressViewFactory factory = new ProgressViewFactory();

        // This class delegates all logging to the mLogWindow window
        // and filters errors to make sure the window is visible when
        // an error is logged.
        ILogUiProvider logAdapter = new ILogUiProvider() {
            public void setDescription(String description) {
                mLogWindow.setDescription(description);
            }

            public void log(String log) {
                mLogWindow.log(log);
            }

            public void logVerbose(String log) {
                mLogWindow.logVerbose(log);
            }

            public void logError(String log) {
                mLogWindow.logError(log);

                // Run the window visibility check/toggle on the UI thread.
                // Note: at least on Windows, it seems ok to check for the window visibility
                // on a sub-thread but that doesn't seem cross-platform safe. We shouldn't
                // have a lot of error logging, so this should be acceptable. If not, we could
                // cache the visibility state.
                mShell.getDisplay().syncExec(new Runnable() {
                    public void run() {
                        if (!mLogWindow.isVisible()) {
                            // Don't toggle the window visibility directly.
                            // Instead use the same action as the log-toggle button
                            // so that the button's state be kept in sync.
                            onToggleLogWindow();
                        }
                    }
                });
            }
        };

        factory.setProgressView(
                new ProgressView(mStatusText, mProgressBar, mButtonStop, logAdapter));
        mUpdaterData.setTaskFactory(factory);

        setWindowImage(mShell);

        setupSources();
        initializeSettings();

        if (mUpdaterData.checkIfInitFailed()) {
            return false;
        }

        mUpdaterData.broadcastOnSdkLoaded();

        if (mRequestAutoUpdate) {
            mUpdaterData.updateOrInstallAll_WithGUI(
                    null /*selectedArchives*/,
                    false /* includeObsoletes */,
                    mContext == SdkInvocationContext.IDE ?
                            UpdaterData.TOOLS_MSG_UPDATED_FROM_ADT :
                                UpdaterData.TOOLS_MSG_UPDATED_FROM_SDKMAN);
        }

        // Tell the one page its the selected one
        mPkgPage.onPageSelected();

        return true;
    }

    /**
     * Creates the icon of the window shell.
     *
     * @param shell The shell on which to put the icon
     */
    private void setWindowImage(Shell shell) {
        String imageName = "android_icon_16.png"; //$NON-NLS-1$
        if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_DARWIN) {
            imageName = "android_icon_128.png"; //$NON-NLS-1$
        }

        if (mUpdaterData != null) {
            ImageFactory imgFactory = mUpdaterData.getImageFactory();
            if (imgFactory != null) {
                shell.setImage(imgFactory.getImageByName(imageName));
            }
        }
    }

    /**
     * Called by the main loop when the window has been disposed.
     */
    private void dispose() {
        mLogWindow.close();
        mUpdaterData.getSources().saveUserAddons(mUpdaterData.getSdkLog());
    }

    /**
     * Callback called when the window shell is disposed.
     */
    private void onAndroidSdkUpdaterDispose() {
        if (mUpdaterData != null) {
            ImageFactory imgFactory = mUpdaterData.getImageFactory();
            if (imgFactory != null) {
                imgFactory.dispose();
            }
        }
    }

    /**
     * Used to initialize the sources.
     */
    private void setupSources() {
        mUpdaterData.setupDefaultSources();
    }

    /**
     * Initializes settings.
     * This must be called after addExtraPages(), which created a settings page.
     * Iterate through all the pages to find the first (and supposedly unique) setting page,
     * and use it to load and apply these settings.
     */
    private void initializeSettings() {
        mSettingsController = mUpdaterData.getSettingsController();
        mSettingsController.loadSettings();
        mSettingsController.applySettings();
    }

    private void onToggleLogWindow() {
        // toggle visibility
        mLogWindow.setVisible(!mLogWindow.isVisible());
        mButtonShowLog.setState(mLogWindow.isVisible() ? 1 : 0);
    }

    private void onStopSelected() {
        // TODO
    }

    private void showRegisteredPage(Purpose purpose) {
        if (mExtraPages == null) {
            return;
        }

        Class<? extends UpdaterPage> clazz = null;

        for (Pair<Class<? extends UpdaterPage>, Purpose> extraPage : mExtraPages) {
            if (extraPage.getSecond() == purpose) {
                clazz = extraPage.getFirst();
                break;
            }
        }

        if (clazz != null) {
            PageDialog d = new PageDialog(mShell, clazz, purpose == Purpose.SETTINGS);
            d.open();
        }
    }

    private void onAvdManager() {
        ITaskFactory oldFactory = mUpdaterData.getTaskFactory();

        try {
            AvdManagerWindowImpl1 win = new AvdManagerWindowImpl1(
                    mShell,
                    mUpdaterData,
                    AvdInvocationContext.SDK_MANAGER);

            for (Pair<Class<? extends UpdaterPage>, Purpose> page : mExtraPages) {
                win.registerPage(page.getFirst(), page.getSecond());
            }

            win.open();
        } catch (Exception e) {
            mUpdaterData.getSdkLog().error(e, "AVD Manager window error");
        } finally {
            mUpdaterData.setTaskFactory(oldFactory);
        }
    }

    // End of hiding from SWT Designer
    //$hide<<$

    // -----

    /**
     * Dialog used to display either the About page or the Settings (aka Options) page
     * with a "close" button.
     */
    private class PageDialog extends SwtBaseDialog {

        private final Class<? extends UpdaterPage> mPageClass;
        private final boolean mIsSettingsPage;

        protected PageDialog(
                Shell parentShell,
                Class<? extends UpdaterPage> pageClass,
                boolean isSettingsPage) {
            super(parentShell, SWT.APPLICATION_MODAL, null /*title*/);
            mPageClass = pageClass;
            mIsSettingsPage = isSettingsPage;
        }

        @Override
        protected void createContents() {
            Shell shell = getShell();
            setWindowImage(shell);

            GridLayoutBuilder.create(shell).columns(2);

            UpdaterPage content = UpdaterPage.newInstance(
                    mPageClass,
                    shell,
                    SWT.NONE,
                    mUpdaterData.getSdkLog());
            GridDataBuilder.create(content).fill().grab().hSpan(2);
            if (content.getLayout() instanceof GridLayout) {
                GridLayout gl = (GridLayout) content.getLayout();
                gl.marginHeight = gl.marginWidth = 0;
            }

            if (mIsSettingsPage && content instanceof ISettingsPage) {
                mSettingsController.setSettingsPage((ISettingsPage) content);
            }

            getShell().setText(
                    String.format("%1$s - %2$s", APP_NAME, content.getPageTitle()));

            Label filler = new Label(shell, SWT.NONE);
            GridDataBuilder.create(filler).hFill().hGrab();

            Button close = new Button(shell, SWT.PUSH);
            close.setText("Close");
            GridDataBuilder.create(close);
            close.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    close();
                }
            });
        }

        @Override
        protected void postCreate() {
            // pass
        }

        @Override
        protected void close() {
            if (mIsSettingsPage) {
                mSettingsController.setSettingsPage(null);
            }
            super.close();
        }
    }
}
