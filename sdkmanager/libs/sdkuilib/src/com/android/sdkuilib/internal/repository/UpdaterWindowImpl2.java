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


import com.android.menubar.IMenuBarCallback;
import com.android.menubar.MenuBarEnhancer;
import com.android.sdklib.ISdkLog;
import com.android.sdklib.SdkConstants;
import com.android.sdkuilib.internal.repository.PackagesPage.MenuAction;
import com.android.sdkuilib.internal.repository.icons.ImageFactory;
import com.android.sdkuilib.internal.tasks.ProgressView;
import com.android.sdkuilib.internal.tasks.ProgressViewFactory;
import com.android.sdkuilib.repository.ISdkChangeListener;
import com.android.sdkuilib.repository.IUpdaterWindow;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseTrackListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
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
public class UpdaterWindowImpl2 implements IUpdaterWindow {

    private static final String APP_NAME = "Android SDK Manager";
    private final Shell mParentShell;
    /** Internal data shared between the window and its pages. */
    private final UpdaterData mUpdaterData;
    /** A list of extra pages to instantiate. Each entry is an object array with 2 elements:
     *  the string title and the Composite class to instantiate to create the page. */
    private ArrayList<Object[]> mExtraPages;
    /** Sets whether the auto-update wizard will be shown when opening the window. */
    private boolean mRequestAutoUpdate;

    // --- UI members ---

    protected Shell mShell;
    private PackagesPage mPkgPage;
    private ProgressBar mProgressBar;
    private Label mStatusText;
    private ImgDisabledButton mButtonStop;
    private ToggleButton mButtonDetails;

    /**
     * Creates a new window. Caller must call open(), which will block.
     *
     * @param parentShell Parent shell.
     * @param sdkLog Logger. Cannot be null.
     * @param osSdkRoot The OS path to the SDK root.
     */
    public UpdaterWindowImpl2(Shell parentShell, ISdkLog sdkLog, String osSdkRoot) {
        mParentShell = parentShell;
        mUpdaterData = new UpdaterData(osSdkRoot, sdkLog);
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
        mShell = new Shell(mParentShell, SWT.SHELL_TRIM);
        mShell.addDisposeListener(new DisposeListener() {
            public void widgetDisposed(DisposeEvent e) {
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
    }

    private void createContents() {

        mPkgPage = new PackagesPage(mShell, mUpdaterData);
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
                getImage("stop_enabled_16.png"),   //$NON-NLS-1$
                getImage("stop_disabled_16.png"));   //$NON-NLS-1$
        mButtonStop.addListener(SWT.Selection, new Listener() {
            public void handleEvent(Event event) {
                onStopSelected();
            }
        });

        mButtonDetails = new ToggleButton(composite2, SWT.NONE,
                getImage("collapsed_16.png"),   //$NON-NLS-1$
                getImage("expanded_16.png"));   //$NON-NLS-1$
        mButtonDetails.addListener(SWT.Selection, new Listener() {
            public void handleEvent(Event event) {
                onToggleDetails();
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

        MenuItem manageAvds = new MenuItem(menuTools, SWT.NONE);
        manageAvds.setText("Manage AVDs...");

        MenuItem manageSources = new MenuItem(menuTools,
                MenuAction.SHOW_ADDON_SITES.getMenuStyle());
        manageSources.setText(
                MenuAction.SHOW_ADDON_SITES.getMenuTitle());
        mPkgPage.registerMenuAction(
                MenuAction.SHOW_ADDON_SITES, manageSources);

        MenuBarEnhancer.setupMenu(APP_NAME, menuTools, new IMenuBarCallback() {
            public void onPreferencesMenuSelected() {
                // TODO: plug settings page here
                MessageDialog.openInformation(mShell, "test", "on prefs");
            }

            public void onAboutMenuSelected() {
                // TODO: plug about page here
                MessageDialog.openInformation(mShell, "test", "on about");
            }

            public void printError(String format, Object... args) {
                if (mUpdaterData != null) {
                    // TODO: right now dump to stderr. Use sdklog later.
                    //mUpdaterData.getSdkLog().error(null, format, args);
                    System.err.printf(format, args);
                }
            }
        });
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
     * @param title The title of the page.
     * @param pageClass The {@link Composite}-derived class that will implement the page.
     */
    public void registerPage(String title, Class<? extends Composite> pageClass) {
        if (mExtraPages == null) {
            mExtraPages = new ArrayList<Object[]>();
        }
        mExtraPages.add(new Object[]{ title, pageClass });
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
     * This creates the pages, selects the first one, setup sources and scan for local folders.
     *
     * Returns true if we should show the window.
     */
    private boolean postCreateContent() {
        ProgressViewFactory factory = new ProgressViewFactory();
        factory.setProgressView(new ProgressView(
                mStatusText, mProgressBar, mButtonStop));
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
                    false /* includeObsoletes */);
        }

        // Tell the one page its the selected one
        mPkgPage.onPageSelected();

        return true;
    }

    /**
     * Creates the icon of the window shell.
     */
    private void setWindowImage(Shell androidSdkUpdater) {
        String imageName = "android_icon_16.png"; //$NON-NLS-1$
        if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_DARWIN) {
            imageName = "android_icon_128.png"; //$NON-NLS-1$
        }

        if (mUpdaterData != null) {
            ImageFactory imgFactory = mUpdaterData.getImageFactory();
            if (imgFactory != null) {
                mShell.setImage(imgFactory.getImageByName(imageName));
            }
        }
    }

    /**
     * Called by the main loop when the window has been disposed.
     */
    private void dispose() {
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
        SettingsController c = mUpdaterData.getSettingsController();
        c.loadSettings();
        c.applySettings();

        // TODO give access to a settings dialog somehow (+about dialog)
        // TODO c.setSettingsPage(settingsPage);
    }

    private void onToggleDetails() {
        mButtonDetails.setState(1 - mButtonDetails.getState());
    }

    private void onStopSelected() {
        // TODO
    }

    // End of hiding from SWT Designer
    //$hide<<$

    // -----

    /**
     * A label that can display 2 images depending on its internal state.
     * This acts as a button by firing the {@link SWT#Selection} listener.
     */
    private static class ToggleButton extends CLabel {
        private Image[] mImage = new Image[2];
        private boolean mMouseIn;
        private int mState = 0;


        public ToggleButton(Composite parent, int style, Image image1, Image image2) {
            super(parent, style);
            mImage[0] = image1;
            mImage[1] = image2;
            updateImage();

            addMouseListener(new MouseListener() {
                public void mouseDown(MouseEvent e) {
                    // pass
                }

                public void mouseUp(MouseEvent e) {
                    // We select on mouse-up, as it should be properly done since this is the
                    // only way a user can cancel a button click by moving out of the button.
                    if (mMouseIn && e.button == 1) {
                        notifyListeners(SWT.Selection, new Event());
                    }
                }

                public void mouseDoubleClick(MouseEvent e) {
                    if (mMouseIn && e.button == 1) {
                        notifyListeners(SWT.DefaultSelection, new Event());
                    }
                }
            });

            addMouseTrackListener(new MouseTrackListener() {
                public void mouseExit(MouseEvent e) {
                    if (mMouseIn) {
                        mMouseIn = false;
                        redraw();
                    }
                }

                public void mouseEnter(MouseEvent e) {
                    if (!mMouseIn) {
                        mMouseIn = true;
                        redraw();
                    }
                }

                public void mouseHover(MouseEvent e) {
                    // pass
                }
            });
        }

        @Override
        public int getStyle() {
            int style = super.getStyle();
            if (mMouseIn) {
                style |= SWT.SHADOW_IN;
            }
            return style;
        }

        /**
         * Sets current state.
         * @param state A value 0 or 1.
         */
        public void setState(int state) {
            assert state == 0 || state == 1;
            mState = state;
            updateImage();
            redraw();
        }

        /**
         * Returns the current state
         * @return Returns the current state, either 0 or 1.
         */
        public int getState() {
            return mState;
        }

        protected void updateImage() {
            setImage(mImage[getState()]);
        }
    }

    /**
     * A label that can display 2 images depending on its enabled/disabled state.
     * This acts as a button by firing the {@link SWT#Selection} listener.
     */
    private static class ImgDisabledButton extends ToggleButton {
        public ImgDisabledButton(Composite parent, int style,
                Image imageEnabled, Image imageDisabled) {
            super(parent, style, imageEnabled, imageDisabled);
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            updateImage();
            redraw();
        }

        @Override
        public void setState(int state) {
            throw new UnsupportedOperationException(); // not available for this type of button
        }

        @Override
        public int getState() {
            return (isDisposed() || !isEnabled()) ? 1 : 0;
        }
    }
}
