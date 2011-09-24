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

package com.android.sdkuilib.internal.repository.sdkman1;


import com.android.sdklib.ISdkLog;
import com.android.sdklib.SdkConstants;
import com.android.sdkuilib.internal.repository.IPageListener;
import com.android.sdkuilib.internal.repository.ISdkUpdaterWindow;
import com.android.sdkuilib.internal.repository.ISettingsPage;
import com.android.sdkuilib.internal.repository.SettingsController;
import com.android.sdkuilib.internal.repository.UpdaterData;
import com.android.sdkuilib.internal.repository.UpdaterPage;
import com.android.sdkuilib.internal.repository.UpdaterPage.Purpose;
import com.android.sdkuilib.internal.repository.icons.ImageFactory;
import com.android.sdkuilib.internal.tasks.ProgressTaskFactory;
import com.android.sdkuilib.repository.ISdkChangeListener;
import com.android.sdkuilib.repository.SdkUpdaterWindow.SdkInvocationContext;
import com.android.util.Pair;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Shell;

import java.util.ArrayList;

/**
 * This is the private implementation of the UpdateWindow for the
 * first version of the SDK Manager.
 * <p/>
 * This window has a sash, with a list of available pages on the left
 * (AVD list, settings, about, installed packages, available packages)
 * and the corresponding page on the right.
 */
public class SdkUpdaterWindowImpl1 implements ISdkUpdaterWindow {

    private final Shell mParentShell;
    /** Internal data shared between the window and its pages. */
    private final UpdaterData mUpdaterData;
    /** The array of pages instances. Only one is visible at a time. */
    private ArrayList<Composite> mPages = new ArrayList<Composite>();
    /** Indicates a page change is due to an internal request. Prevents callbacks from looping. */
    private boolean mInternalPageChange;
    /** A list of extra pages to instantiate. Each entry is an object array with 2 elements:
     *  the string title and the Composite class to instantiate to create the page. */
    private ArrayList<Pair<Class<? extends UpdaterPage>, Purpose>> mExtraPages;
    /** A factory to create progress task dialogs. */
    private ProgressTaskFactory mTaskFactory;
    /** The initial page to display. If null or not a know class, the first page will be displayed.
     * Must be set before the first call to {@link #open()}. */
    private Class<? extends Composite> mInitialPage;
    /** Sets whether the auto-update wizard will be shown when opening the window. */
    private boolean mRequestAutoUpdate;

    // --- UI members ---

    protected Shell mShell;
    private List mPageList;
    private Composite mPagesRootComposite;
    private AvdManagerPage mAvdManagerPage;
    private StackLayout mStackLayout;
    private LocalPackagesPage mLocalPackagePage;
    private RemotePackagesPage mRemotePackagesPage;

    /**
     * Creates a new window. Caller must call open(), which will block.
     *
     * @param parentShell Parent shell.
     * @param sdkLog Logger. Cannot be null.
     * @param osSdkRoot The OS path to the SDK root.
     * @param context The {@link SdkInvocationContext} to change the behavior depending on who's
     *  opening the SDK Manager. Unused for SdkMan1.
     */
    public SdkUpdaterWindowImpl1(
            Shell parentShell,
            ISdkLog sdkLog,
            String osSdkRoot,
            SdkInvocationContext context/*unused*/) {
        mParentShell = parentShell;
        mUpdaterData = new UpdaterData(osSdkRoot, sdkLog);
    }

    /**
     * Opens the window.
     * @wbp.parser.entryPoint
     */
    public void open() {
        if (mParentShell == null) {
            Display.setAppName("Android"); //$hide$ (hide from SWT designer)
        }

        createShell();
        preCreateContent();
        createContents();
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
        mShell.setText("Android SDK and AVD Manager");
    }

    /**
     * Create contents of the window.
     */
    private void createContents() {
        SashForm sashForm = new SashForm(mShell, SWT.NONE);
        sashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));

        mPageList = new List(sashForm, SWT.BORDER);
        mPageList.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onPageListSelected();    //$hide$ (hide from SWT designer)
            }
        });

        createPagesRoot(sashForm);

        sashForm.setWeights(new int[] {150, 576});
    }

    private void createPagesRoot(Composite parent) {
        mPagesRootComposite = new Composite(parent, SWT.NONE);
        mStackLayout = new StackLayout();
        mPagesRootComposite.setLayout(mStackLayout);
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
        mInitialPage = pageClass;
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
     * Called by {@link #postCreateContent()} to generate the pages that can be
     * displayed in the window.
     */
    protected void createPages() {
        mAvdManagerPage = new AvdManagerPage(mPagesRootComposite, SWT.BORDER, mUpdaterData);

        mLocalPackagePage = new LocalPackagesPage(mPagesRootComposite, SWT.BORDER, mUpdaterData);
        mRemotePackagesPage = new RemotePackagesPage(mPagesRootComposite, SWT.BORDER, mUpdaterData);

        addPage(mAvdManagerPage, "Virtual devices");

        addPage(mLocalPackagePage,   "Installed packages");
        addPage(mRemotePackagesPage, "Available packages");

        addExtraPages();
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
     * Creates the icon of the window shell.
     */
    private void setWindowImage(Shell androidSdkUpdater) {
        String imageName = "android_icon_16.png"; //$NON-NLS-1$
        if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_DARWIN) {
            imageName = "android_icon_128.png";
        }

        if (mUpdaterData != null) {
            ImageFactory imgFactory = mUpdaterData.getImageFactory();
            if (imgFactory != null) {
                mShell.setImage(imgFactory.getImageByName(imageName));
            }
        }
    }

    /**
     * Called before the UI is created.
     */
    private void preCreateContent() {
        mUpdaterData.setWindowShell(mShell);
        mTaskFactory = new ProgressTaskFactory(mShell);
        mUpdaterData.setTaskFactory(mTaskFactory);
        mUpdaterData.setImageFactory(new ImageFactory(mShell.getDisplay()));
    }

    /**
     * Once the UI has been created, initializes the content.
     * This creates the pages, selects the first one, setup sources and scan for local folders.
     *
     * Returns true if we should show the window.
     */
    private boolean postCreateContent() {
        setWindowImage(mShell);
        createPages();

        setupSources();
        initializeSettings();
        selectInitialPage();

        if (mUpdaterData.checkIfInitFailed()) {
            return false;
        }

        mUpdaterData.broadcastOnSdkLoaded();

        if (mRequestAutoUpdate) {
            mUpdaterData.updateOrInstallAll_WithGUI(
                    null /*selectedArchives*/,
                    false /* includeObsoletes */,
                    0 /* flags */);
        }

        return true;
    }

    /**
     * Called by the main loop when the window has been disposed.
     */
    private void dispose() {
        mUpdaterData.getSources().saveUserAddons(mUpdaterData.getSdkLog());
    }

    // --- page switching ---

    /**
     * Adds an instance of a page to the page list.
     * <p/>
     * Each page is a {@link Composite}. The title of the page is stored in the
     * {@link Composite#getData()} field.
     */
    protected void addPage(Composite page, String title) {
        assert title != null;
        if (title == null) {
            title = "Unknown";
        }
        page.setData(title);
        mPages.add(page);
        if (mPageList != null) {
            mPageList.add(title);
        }
    }

    /**
     * Adds all extra pages. For each page, instantiates an instance of the {@link Composite}
     * using the constructor that takes a single {@link Composite} argument and then adds it
     * to the page list.
     */
    protected void addExtraPages() {
        if (mExtraPages == null) {
            return;
        }

        for (Pair<Class<? extends UpdaterPage>, Purpose> extraPage : mExtraPages) {
            Class<? extends UpdaterPage> clazz = extraPage.getFirst();
            UpdaterPage instance = UpdaterPage.newInstance(
                    clazz,
                    mPagesRootComposite,
                    SWT.BORDER,
                    mUpdaterData.getSdkLog());
            if (instance != null) {
                addPage(instance, instance.getPageTitle());
            }
        }
    }

    /**
     * Callback invoked when an item is selected in the page list.
     * If this is not an internal page change, displays the given page.
     */
    private void onPageListSelected() {
        if (mInternalPageChange == false && mPageList != null) {
            int index = mPageList.getSelectionIndex();
            if (index >= 0) {
                displayPage(index);
            }
        }
    }

    /**
     * Displays the page at the given index.
     *
     * @param index An index between 0 and {@link #mPages}'s length - 1.
     */
    private void displayPage(int index) {
        Composite page = mPages.get(index);
        if (page != null) {
            mStackLayout.topControl = page;
            mPagesRootComposite.layout(true);

            if (!mInternalPageChange && mPageList != null) {
                mInternalPageChange = true;
                mPageList.setSelection(index);
                mInternalPageChange = false;
            }

            if (page instanceof IPageListener) {
                ((IPageListener) page).onPageSelected();
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

        for (Object page : mPages) {
            if (page instanceof ISettingsPage) {
                ISettingsPage settingsPage = (ISettingsPage) page;

                c.setSettingsPage(settingsPage);
                break;
            }
        }
    }

    /**
     * Select and show the initial page.
     * This will be either the page which class matches {@link #mInitialPage} or the
     * first one in the list.
     */
    private void selectInitialPage() {
        int pageIndex = 0;
        int i = 0;
        for (Composite p : mPages) {
            if (p.getClass().equals(mInitialPage)) {
                pageIndex = i;
                break;
            }
            i++;
        }

        displayPage(pageIndex);
        if (mPageList != null) {
            mPageList.setSelection(pageIndex);
        }
    }

    // End of hiding from SWT Designer
    //$hide<<$
}
