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

import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.internal.repository.Archive;
import com.android.sdklib.internal.repository.IDescription;
import com.android.sdklib.internal.repository.ITask;
import com.android.sdklib.internal.repository.ITaskMonitor;
import com.android.sdklib.internal.repository.Package;
import com.android.sdklib.internal.repository.PlatformPackage;
import com.android.sdklib.internal.repository.PlatformToolPackage;
import com.android.sdklib.internal.repository.SdkSource;
import com.android.sdklib.internal.repository.ToolPackage;
import com.android.sdkuilib.internal.repository.PackageLoader.ISourceLoadedCallback;
import com.android.sdkuilib.internal.repository.PackageLoader.PkgItem;
import com.android.sdkuilib.internal.repository.PackageLoader.PkgState;
import com.android.sdkuilib.internal.repository.icons.ImageFactory;
import com.android.sdkuilib.repository.ISdkChangeListener;
import com.android.sdkuilib.ui.GridDataBuilder;
import com.android.sdkuilib.ui.GridLayoutBuilder;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.ITableFontProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.TreeColumnViewerLabelProvider;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map.Entry;

/**
 * Page that displays both locally installed packages as well as all known
 * remote available packages. This gives an overview of what is installed
 * vs what is available and allows the user to update or install packages.
 */
public class PackagesPage extends UpdaterPage
        implements ISdkChangeListener, IPageListener {

    private static final String ICON_CAT_OTHER      = "pkgcat_other_16.png";    //$NON-NLS-1$
    private static final String ICON_CAT_PLATFORM   = "pkgcat_16.png";          //$NON-NLS-1$
    private static final String ICON_SORT_BY_SOURCE = "source_icon16.png";      //$NON-NLS-1$
    private static final String ICON_SORT_BY_API    = "platform_pkg_16.png";    //$NON-NLS-1$
    private static final String ICON_PKG_NEW        = "pkg_new_16.png";         //$NON-NLS-1$
    private static final String ICON_PKG_UPDATE     = "pkg_update_16.png";      //$NON-NLS-1$
    private static final String ICON_PKG_INSTALLED  = "pkg_installed_16.png";   //$NON-NLS-1$

    enum MenuAction {
        RELOAD                      (SWT.NONE,  "Reload"),
        SHOW_ADDON_SITES            (SWT.NONE,  "Manage Add-on Sites..."),
        TOGGLE_SHOW_ARCHIVES        (SWT.CHECK, "Show Archives Details"),
        TOGGLE_SHOW_INSTALLED_PKG   (SWT.CHECK, "Show Installed Packages"),
        TOGGLE_SHOW_OBSOLETE_PKG    (SWT.CHECK, "Show Obsolete Packages"),
        TOGGLE_SHOW_UPDATE_NEW_PKG  (SWT.CHECK, "Show Updates/New Packages"),
        SORT_API_LEVEL              (SWT.RADIO, "Sort by API Level"),
        SORT_SOURCE                 (SWT.RADIO, "Sort by Repository")
        ;

        private final int mMenuStyle;
        private final String mMenuTitle;

        MenuAction(int menuStyle, String menuTitle) {
            mMenuStyle = menuStyle;
            mMenuTitle = menuTitle;
        }

        public int getMenuStyle() {
            return mMenuStyle;
        }

        public String getMenuTitle() {
            return mMenuTitle;
        }
    };

    private final Map<MenuAction, MenuItem> mMenuActions = new HashMap<MenuAction, MenuItem>();

    private final PackagesPageLogic mLogic;
    private boolean mDisplayArchives = false;

    private Text mTextSdkOsPath;
    private Button mCheckSortSource;
    private Button mCheckSortApi;
    private Button mCheckFilterObsolete;
    private Button mCheckFilterInstalled;
    private Button mCheckFilterNew;
    private Composite mGroupOptions;
    private Composite mGroupSdk;
    private Group mGroupPackages;
    private Button mButtonDelete;
    private Button mButtonInstall;
    private Tree mTree;
    private CheckboxTreeViewer mTreeViewer;
    private TreeViewerColumn mColumnName;
    private TreeViewerColumn mColumnApi;
    private TreeViewerColumn mColumnRevision;
    private TreeViewerColumn mColumnStatus;
    private Font mTreeFontItalic;
    private TreeColumn mTreeColumnName;
    private boolean mOperationPending;

    public PackagesPage(Composite parent, int swtStyle, UpdaterData updaterData) {
        super(parent, swtStyle);

        mLogic = new PackagesPageLogic(updaterData) {
            @Override
            boolean keepItem(PkgItem item) {
                return PackagesPage.this.keepItem(item);
            }
        };

        createContents(this);
        postCreate();  //$hide$
    }

    public void onPageSelected() {
        if (mLogic.mAllPkgItems.isEmpty()) {
            // Initialize the package list the first time the page is shown.
            loadPackages();
        }
    }

    private void createContents(Composite parent) {
        GridLayoutBuilder.create(parent).noMargins().columns(2);

        mGroupSdk = new Composite(parent, SWT.NONE);
        GridDataBuilder.create(mGroupSdk).hFill().vCenter().hGrab().hSpan(2);
        GridLayoutBuilder.create(mGroupSdk).columns(2);

        Label label1 = new Label(mGroupSdk, SWT.NONE);
        label1.setText("SDK Path:");

        mTextSdkOsPath = new Text(mGroupSdk, SWT.NONE);
        GridDataBuilder.create(mTextSdkOsPath).hFill().vCenter().hGrab();
        mTextSdkOsPath.setEnabled(false);

        mGroupPackages = new Group(parent, SWT.NONE);
        GridDataBuilder.create(mGroupPackages).fill().grab().hSpan(2);
        mGroupPackages.setText("Packages");
        GridLayoutBuilder.create(mGroupPackages).columns(1);

        mTreeViewer = new CheckboxTreeViewer(mGroupPackages, SWT.BORDER);

        mTreeViewer.addCheckStateListener(new ICheckStateListener() {
            public void checkStateChanged(CheckStateChangedEvent event) {
                onTreeCheckStateChanged(event); //$hide$
            }
        });

        mTree = mTreeViewer.getTree();
        mTree.setLinesVisible(true);
        mTree.setHeaderVisible(true);
        GridDataBuilder.create(mTree).fill().grab();

        // column name icon is set in sortPackages() depending on the current filter type
        // (e.g. API level or source)
        mColumnName = new TreeViewerColumn(mTreeViewer, SWT.NONE);
        mTreeColumnName = mColumnName.getColumn();
        mTreeColumnName.setText("Name");
        mTreeColumnName.setWidth(340);

        mColumnApi = new TreeViewerColumn(mTreeViewer, SWT.NONE);
        TreeColumn treeColumn2 = mColumnApi.getColumn();
        treeColumn2.setText("API");
        treeColumn2.setAlignment(SWT.CENTER);
        treeColumn2.setWidth(50);

        mColumnRevision = new TreeViewerColumn(mTreeViewer, SWT.NONE);
        TreeColumn treeColumn3 = mColumnRevision.getColumn();
        treeColumn3.setText("Rev.");
        treeColumn3.setToolTipText("Revision currently installed");
        treeColumn3.setAlignment(SWT.CENTER);
        treeColumn3.setWidth(50);


        mColumnStatus = new TreeViewerColumn(mTreeViewer, SWT.NONE);
        TreeColumn treeColumn4 = mColumnStatus.getColumn();
        treeColumn4.setText("Status");
        treeColumn4.setAlignment(SWT.LEAD);
        treeColumn4.setWidth(190);

        mGroupOptions = new Composite(mGroupPackages, SWT.NONE);
        GridDataBuilder.create(mGroupOptions).hFill().vCenter().hGrab();
        GridLayoutBuilder.create(mGroupOptions).columns(6).noMargins();

        // Options line 1, 6 columns

        Label label3 = new Label(mGroupOptions, SWT.NONE);
        label3.setText("Show:");

        mCheckFilterNew = new Button(mGroupOptions, SWT.CHECK);
        mCheckFilterNew.setText("Updates/New");
        mCheckFilterNew.setToolTipText("Show Updates and New");
        mCheckFilterNew.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                sortPackages(true /*updateButtons*/);
            }
        });
        mCheckFilterNew.setSelection(true);

        mCheckFilterInstalled = new Button(mGroupOptions, SWT.CHECK);
        mCheckFilterInstalled.setToolTipText("Show Installed");
        mCheckFilterInstalled.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                sortPackages(true /*updateButtons*/);
            }
        });
        mCheckFilterInstalled.setSelection(true);
        mCheckFilterInstalled.setText("Installed");

        mCheckFilterObsolete = new Button(mGroupOptions, SWT.CHECK);
        mCheckFilterObsolete.setText("Obsolete");
        mCheckFilterObsolete.setToolTipText("Also show obsolete packages");
        mCheckFilterObsolete.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                sortPackages(true /*updateButtons*/);
            }
        });
        mCheckFilterObsolete.setSelection(false);

        Link linkSelectNew = new Link(mGroupOptions, SWT.NONE);
        linkSelectNew.setText("<a>Select New/Updates</a>");
        linkSelectNew.setToolTipText("Selects all items that are either new or updates.");
        GridDataBuilder.create(linkSelectNew).hFill().hGrab();
        linkSelectNew.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                super.widgetSelected(e);
                onSelectNewUpdates();
            }
        });

        mButtonInstall = new Button(mGroupOptions, SWT.NONE);
        mButtonInstall.setText("Install Selected...");
        mButtonInstall.setToolTipText("Install all the selected packages");
        GridDataBuilder.create(mButtonInstall).hFill().vCenter().hGrab();
        mButtonInstall.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onButtonInstall();  //$hide$
            }
        });

        // Options line 2, 6 columns

        Label label2 = new Label(mGroupOptions, SWT.NONE);
        label2.setText("Sort by:");

        mCheckSortApi = new Button(mGroupOptions, SWT.RADIO);
        mCheckSortApi.setToolTipText("Sort by API level");
        mCheckSortApi.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                sortPackages(true /*updateButtons*/);
                // Reset the expanded state when changing sort algorithm
                expandInitial(mLogic.mCurrentCategories);
            }
        });
        mCheckSortApi.setText("API level");
        mCheckSortApi.setSelection(true);

        mCheckSortSource = new Button(mGroupOptions, SWT.RADIO);
        mCheckSortSource.setText("Repository");
        mCheckSortSource.setToolTipText("Sort by Repository");
        mCheckSortSource.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                sortPackages(true /*updateButtons*/);
                // Reset the expanded state when changing sort algorithm
                expandInitial(mLogic.mCurrentCategories);
            }
        });

        new Label(mGroupOptions, SWT.NONE);

        Link linkDeselect = new Link(mGroupOptions, SWT.NONE);
        linkDeselect.setText("<a>Deselect All</a>");
        linkDeselect.setToolTipText("Deselects all the currently selected items");
        GridDataBuilder.create(linkDeselect).hFill().hGrab();
        linkDeselect.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                super.widgetSelected(e);
                onDeselectAll();
            }
        });

        mButtonDelete = new Button(mGroupOptions, SWT.NONE);
        mButtonDelete.setText("Delete...");
        mButtonDelete.setToolTipText("Delete an installed package");
        GridDataBuilder.create(mButtonDelete).hFill().vCenter().hGrab();
        mButtonDelete.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onButtonDelete();  //$hide$
            }
        });
    }

    private Image getImage(String filename) {
        if (mLogic.mUpdaterData != null) {
            ImageFactory imgFactory = mLogic.mUpdaterData.getImageFactory();
            if (imgFactory != null) {
                return imgFactory.getImageByName(filename);
            }
        }
        return null;
    }


    // -- Start of internal part ----------
    // Hide everything down-below from SWT designer
    //$hide>>$


    // --- menu interactions ---

    public void registerMenuAction(final MenuAction action, MenuItem item) {
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                Button button = null;

                switch (action) {
                case RELOAD:
                    loadPackages();
                    break;
                case SHOW_ADDON_SITES:
                    AddonSitesDialog d = new AddonSitesDialog(getShell(), mLogic.mUpdaterData);
                    if (d.open()) {
                        loadPackages();
                    }
                    break;
                case TOGGLE_SHOW_ARCHIVES:
                    mDisplayArchives = !mDisplayArchives;
                    sortPackages(true /*updateButtons*/);
                    break;
                case TOGGLE_SHOW_INSTALLED_PKG:
                    button = mCheckFilterInstalled;
                    break;
                case TOGGLE_SHOW_OBSOLETE_PKG:
                    button = mCheckFilterObsolete;
                    break;
                case TOGGLE_SHOW_UPDATE_NEW_PKG:
                    button = mCheckFilterNew;
                    break;
                case SORT_API_LEVEL:
                    button = mCheckSortApi;
                    break;
                case SORT_SOURCE:
                    button = mCheckSortSource;
                    break;
                }

                if (button != null && !button.isDisposed()) {
                    // Toggle this button (radio or checkbox)

                    boolean value = button.getSelection();

                    // SWT doesn't automatically switch radio buttons when using the
                    // Widget#setSelection method, so we'll do it here manually.
                    if (!value && (button.getStyle() & SWT.RADIO) != 0) {
                        // we'll be selecting this radio button, so deselect all ther other ones
                        // in the parent group.
                        for (Control child : button.getParent().getChildren()) {
                            if (child instanceof Button &&
                                    child != button &&
                                    (child.getStyle() & SWT.RADIO) != 0) {
                                ((Button) child).setSelection(value);
                            }
                        }
                    }

                    button.setSelection(!value);

                    // SWT doesn't actually invoke the listeners when using Widget#setSelection
                    // so let's run the actual action.
                    button.notifyListeners(SWT.Selection, new Event());
                }

                updateMenuCheckmarks();
            }
        });

        mMenuActions.put(action, item);
    }

    // --- internal methods ---

    private void updateMenuCheckmarks() {

        for (Entry<MenuAction, MenuItem> entry : mMenuActions.entrySet()) {
            MenuAction action = entry.getKey();
            MenuItem item = entry.getValue();

            if (action.getMenuStyle() == SWT.NONE) {
                continue;
            }

            boolean value = false;
            Button button = null;

            switch (action) {
            case TOGGLE_SHOW_ARCHIVES:
                value = mDisplayArchives;
                break;
            case TOGGLE_SHOW_INSTALLED_PKG:
                button = mCheckFilterInstalled;
                break;
            case TOGGLE_SHOW_OBSOLETE_PKG:
                button = mCheckFilterObsolete;
                break;
            case TOGGLE_SHOW_UPDATE_NEW_PKG:
                button = mCheckFilterNew;
                break;
            case SORT_API_LEVEL:
                button = mCheckSortApi;
                break;
            case SORT_SOURCE:
                button = mCheckSortSource;
                break;
            }

            if (button != null && !button.isDisposed()) {
                value = button.getSelection();
            }

            item.setSelection(value);
        }

    }

    private void postCreate() {
        if (mLogic.mUpdaterData != null) {
            mTextSdkOsPath.setText(mLogic.mUpdaterData.getOsSdkRoot());
        }

        mTreeViewer.setContentProvider(new PkgContentProvider());

        mColumnApi.setLabelProvider(
                new TreeColumnViewerLabelProvider(new PkgCellLabelProvider(mColumnApi)));
        mColumnName.setLabelProvider(
                new TreeColumnViewerLabelProvider(new PkgCellLabelProvider(mColumnName)));
        mColumnStatus.setLabelProvider(
                new TreeColumnViewerLabelProvider(new PkgCellLabelProvider(mColumnStatus)));
        mColumnRevision.setLabelProvider(
                new TreeColumnViewerLabelProvider(new PkgCellLabelProvider(mColumnRevision)));

        FontData fontData = mTree.getFont().getFontData()[0];
        fontData.setStyle(SWT.ITALIC);
        mTreeFontItalic = new Font(mTree.getDisplay(), fontData);

        mTree.addDisposeListener(new DisposeListener() {
            public void widgetDisposed(DisposeEvent e) {
                mTreeFontItalic.dispose();
                mTreeFontItalic = null;
            }
        });
    }

    private void loadPackages() {
        if (mLogic.mUpdaterData == null) {
            return;
        }

        final boolean firstLoad = mLogic.mAllPkgItems.isEmpty();

        // LoadPackage is synchronous but does not block the UI.
        // Consequently it's entirely possible for the user
        // to request the app to close whilst the packages are loading. Any
        // action done after loadPackages must check the UI hasn't been
        // disposed yet. Otherwise hilarity ensues.

        mLogic.mPackageLoader.loadPackages(new ISourceLoadedCallback() {
            public boolean onSourceLoaded(List<PkgItem> newPkgItems) {
                boolean somethingNew = false;

                synchronized(mLogic.mAllPkgItems) {
                    nextNewItem: for (PkgItem newItem : newPkgItems) {
                        for (PkgItem existingItem : mLogic.mAllPkgItems) {
                            if (existingItem.isSameItemAs(newItem)) {
                                // This isn't a new package, we already have it.
                                continue nextNewItem;
                            }
                        }
                        mLogic.mAllPkgItems.add(newItem);
                        somethingNew = true;
                    }
                }

                if (somethingNew) {
                    // Dynamically update the table while we load after each source.
                    // Since the official Android source gets loaded first, it makes the
                    // window look non-empty a lot sooner.
                    if (!mGroupPackages.isDisposed()) {
                        mGroupPackages.getDisplay().syncExec(new Runnable() {
                            public void run() {
                                sortPackages(true /* updateButtons */);

                                if (!mGroupPackages.isDisposed()) {
                                    if (firstLoad) {
                                        // set the initial expanded state
                                        expandInitial(mLogic.mCurrentCategories);
                                    }
                                    updateButtonsState();
                                    updateMenuCheckmarks();
                                }
                            }
                        });
                    }
                }

                // Return true to tell the loader to continue with the next source.
                // Return false to stop the loader if any UI has been disposed, which can
                // happen if the user is trying to close the window during the load operation.
                return !mGroupPackages.isDisposed();
            }

            public void onLoadCompleted() {
                if (firstLoad && !mGroupPackages.isDisposed()) {
                    updateButtonsState();
                    updateMenuCheckmarks();
                }
            }
        });
    }

    private void sortPackages(boolean updateButtons) {
        if (isSortByApi()) {
            sortByApiLevel();
        } else {
            sortBySource();
        }
        if (updateButtons) {
            updateButtonsState();
            updateMenuCheckmarks();
        }
    }

    private boolean isSortByApi() {
        return mCheckSortApi != null && !mCheckSortApi.isDisposed() && mCheckSortApi.getSelection();
    }

    /**
     * Recompute the tree by sorting all the packages by API.
     * This does an update in-place of the mApiCategories list so that the table
     * can preserve its state (checked / expanded / selected) properly.
     */
    private void sortByApiLevel() {

        if (!mTreeColumnName.isDisposed()) {
            mTreeColumnName.setImage(getImage(ICON_SORT_BY_API));
        }

        mLogic.sortByApiLevel();

        if (mTreeViewer.getInput() != mLogic.mCurrentCategories) {
            // set initial input
            mTreeViewer.setInput(mLogic.mCurrentCategories);
        } else {
            // refresh existing, which preserves the expanded state, the selection
            // and the checked state.
            mTreeViewer.refresh();
        }
    }

    /**
     * Recompute the tree by sorting all packages by source.
     */
    private void sortBySource() {

        if (!mTreeColumnName.isDisposed()) {
            mTreeColumnName.setImage(getImage(ICON_SORT_BY_SOURCE));
        }

        mLogic.sortBySource();

        // We don't support in-place incremental updates so the table gets reset
        // each time we load when sorted by source.
        if (mTreeViewer.getInput() != mLogic.mCurrentCategories) {
            mTreeViewer.setInput(mLogic.mCurrentCategories);
        } else {
            // refresh existing, which preserves the expanded state, the selection
            // and the checked state.
            mTreeViewer.refresh();
        }
    }

    /**
     * Decide whether to keep an item in the current tree based on user-chosen filter options.
     */
    private boolean keepItem(PkgItem item) {
        if (!mCheckFilterObsolete.getSelection()) {
            if (item.isObsolete()) {
                return false;
            }
        }

        if (!mCheckFilterInstalled.getSelection()) {
            if (item.getState() == PkgState.INSTALLED) {
                return false;
            }
        }

        if (!mCheckFilterNew.getSelection()) {
            if (item.getState() == PkgState.NEW || item.hasUpdatePkg()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Performs the initial expansion of the tree. This expands categories that contain
     * at least one installed item and collapses the ones with nothing installed.
     *
     * TODO: change this to only change the expanded state on categories that have not
     * been touched by the user yet. Once we do that, call this every time a new source
     * is added or the list is reloaded.
     */
    private void expandInitial(Object elem) {
        mTreeViewer.setExpandedState(elem, true);
        for (Object pkg :
                ((ITreeContentProvider) mTreeViewer.getContentProvider()).getChildren(elem)) {
            if (pkg instanceof PkgCategory) {
                PkgCategory cat = (PkgCategory) pkg;
                for (PkgItem item : cat.getItems()) {
                    if (item.getState() == PkgState.INSTALLED) {
                        expandInitial(pkg);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Handle checking and unchecking of the tree items.
     *
     * When unchecking, all sub-tree items checkboxes are cleared too.
     * When checking a source, all of its packages are checked too.
     * When checking a package, only its compatible archives are checked.
     */
    private void onTreeCheckStateChanged(CheckStateChangedEvent event) {
        boolean b = event.getChecked();
        Object elem = event.getElement();

        assert event.getSource() == mTreeViewer;

        // when deselecting, we just deselect all children too
        if (b == false) {
            mTreeViewer.setSubtreeChecked(elem, b);
            updateButtonsState();
            return;
        }

        ITreeContentProvider provider = (ITreeContentProvider) mTreeViewer.getContentProvider();

        // When selecting, we want to only select compatible archives and expand the super nodes.
        checkExpandItem(elem, provider);

        updateButtonsState();
    }

    private void checkExpandItem(Object elem, ITreeContentProvider provider) {
        if (elem instanceof PkgCategory || elem instanceof PkgItem) {
            mTreeViewer.setExpandedState(elem, true);
            for (Object pkg : provider.getChildren(elem)) {
                mTreeViewer.setChecked(pkg, true);
                checkExpandItem(pkg, provider);
            }
        } else if (elem instanceof Package) {
            selectCompatibleArchives(elem, provider);
        }
    }

    private void selectCompatibleArchives(Object pkg, ITreeContentProvider provider) {
        for (Object archive : provider.getChildren(pkg)) {
            if (archive instanceof Archive) {
                mTreeViewer.setChecked(archive, ((Archive) archive).isCompatible());
            }
        }
    }

    /**
     * Indicate an install/delete operation is pending.
     * This disable the install/delete buttons.
     * Use {@link #endOperationPending()} to revert.
     */
    private void beginOperationPending() {
        mOperationPending = true;
        mButtonInstall.setEnabled(false);
        mButtonDelete.setEnabled(false);
    }

    private void endOperationPending() {
        mOperationPending = false;
        updateButtonsState();
    }

    private void updateButtonsState() {
        if (mOperationPending) {
            return;
        }

        boolean canInstall = false;

        if (mDisplayArchives) {
            // In detail mode, we display archives so we can install if at
            // least one archive is selected.

            Object[] checked = mTreeViewer.getCheckedElements();
            if (checked != null) {
                for (Object c : checked) {
                    if (c instanceof Archive) {
                        if (((Archive) c).isCompatible()) {
                            canInstall = true;
                            break;
                        }
                    }
                }
            }
        } else {
            // In non-detail mode, we need to check if there are any packages
            // or pkgitems selected with at least one compatible archive to be
            // installed.

            Object[] checked = mTreeViewer.getCheckedElements();
            if (checked != null) {
                for (Object c : checked) {
                    if (c instanceof Package) {
                        // This is an update package
                        if (((Package) c).hasCompatibleArchive()) {
                            canInstall = true;
                            break;
                        }
                    } else if (c instanceof PkgItem) {
                        if (((PkgItem) c).getMainPackage().hasCompatibleArchive()) {
                            canInstall = true;
                            break;
                        }
                    }
                }
            }
        }

        mButtonInstall.setEnabled(canInstall);

        // We can only delete local archives
        boolean canDelete = false;
        Object[] checked = mTreeViewer.getCheckedElements();
        if (checked != null) {
            for (Object c : checked) {
                if (c instanceof PkgItem) {
                    PkgState state = ((PkgItem) c).getState();
                    if (state == PkgState.INSTALLED) {
                        canDelete = true;
                        break;
                    }
                }
            }
        }

        mButtonDelete.setEnabled(canDelete);
    }

    private void onSelectNewUpdates() {
        ITreeContentProvider provider = (ITreeContentProvider) mTreeViewer.getContentProvider();
        synchronized(mLogic.mAllPkgItems) {
            for (PkgCategory cat : mLogic.mCurrentCategories) {
                boolean selected = false;
                for (PkgItem item : cat.getItems()) {
                    if (item.getState() == PkgState.NEW || item.hasUpdatePkg()) {
                        mTreeViewer.setChecked(item, true);
                        checkExpandItem(item, provider);
                        selected = true;
                    }
                }
                if (selected) {
                    mTreeViewer.setExpandedState(cat, true);
                }
            }
        }
        updateButtonsState();
    }

    private void onDeselectAll() {
        mTreeViewer.setCheckedElements(new Object[0]);
        updateButtonsState();
    }

    private void onButtonInstall() {
        ArrayList<Archive> archives = new ArrayList<Archive>();

        if (mDisplayArchives) {
            // In detail mode, we display archives so we can install only the
            // archives that are actually selected.

            Object[] checked = mTreeViewer.getCheckedElements();
            if (checked != null) {
                for (Object c : checked) {
                    if (c instanceof Archive) {
                        if (((Archive) c).isCompatible()) {
                            archives.add((Archive) c);
                        }
                    }
                }
            }
        } else {
            // In non-detail mode, we install all the compatible archives
            // found in the selected pkg items. We also automatically
            // select update packages rather than the root package if any.

            Object[] checked = mTreeViewer.getCheckedElements();
            if (checked != null) {
                for (Object c : checked) {
                    Package p = null;
                    if (c instanceof Package) {
                        // This is an update package
                        p = (Package) c;
                    } else if (c instanceof PkgItem) {
                        p = ((PkgItem) c).getMainPackage();

                        PkgItem pi = (PkgItem) c;
                        if (pi.getState() == PkgState.INSTALLED) {
                            Package updPkg = pi.getUpdatePkg();
                            if (updPkg != null) {
                            // If there's one and only one update, auto-select it instead.
                                p = updPkg;
                            }
                        }
                    }
                    if (p != null) {
                        for (Archive a : p.getArchives()) {
                            if (a.isCompatible()) {
                                archives.add(a);
                            }
                        }
                    }
                }
            }
        }

        if (mLogic.mUpdaterData != null) {
            try {
                beginOperationPending();

                mLogic.mUpdaterData.updateOrInstallAll_WithGUI(
                    archives,
                    mCheckFilterObsolete.getSelection() /* includeObsoletes */);
            } finally {
                endOperationPending();

                // Remove any pkg item matching anything we potentially installed
                // then request the package list to be updated. This will prevent
                // from having stale entries.
                synchronized(mLogic.mAllPkgItems) {
                    for (Archive a : archives) {
                        for (Iterator<PkgItem> it = mLogic.mAllPkgItems.iterator(); it.hasNext(); ) {
                            PkgItem pi = it.next();
                            if (pi.hasArchive(a)) {
                                it.remove();
                                break;
                            }
                        }
                    }
                }

                // The local package list has changed, make sure to refresh it
                mLogic.mUpdaterData.getLocalSdkParser().clearPackages();
                loadPackages();
            }
        }
    }

    private void onButtonDelete() {
        // Find selected local packages to be delete
        Object[] checked = mTreeViewer.getCheckedElements();
        if (checked == null) {
            // This should not happen since the button should be disabled
            return;
        }

        final String title = "Delete SDK Package";
        String msg = "Are you sure you want to delete:";

        // A map of archives to deleted versus their internal PkgItem representation
        final Map<Archive, PkgItem> archives = new TreeMap<Archive, PkgItem>();

        for (Object c : checked) {
            if (c instanceof PkgItem) {
                PkgItem pi = (PkgItem) c;
                PkgState state = pi.getState();
                if (state == PkgState.INSTALLED) {
                    Package p = pi.getMainPackage();

                    Archive[] as = p.getArchives();
                    if (as.length == 1 && as[0] != null && as[0].isLocal()) {
                        Archive archive = as[0];
                        String osPath = archive.getLocalOsPath();

                        File dir = new File(osPath);
                        if (dir.isDirectory()) {
                            msg += "\n - " + p.getShortDescription();
                            archives.put(archive, pi);
                        }
                    }
                }
            }
        }

        if (!archives.isEmpty()) {
            msg += "\n" + "This cannot be undone.";
            if (MessageDialog.openQuestion(getShell(), title, msg)) {
                try {
                    beginOperationPending();

                    mLogic.mUpdaterData.getTaskFactory().start("Delete Package", new ITask() {
                        public void run(ITaskMonitor monitor) {
                            monitor.setProgressMax(archives.size() + 1);
                            for (Entry<Archive, PkgItem> entry : archives.entrySet()) {
                                Archive a = entry.getKey();

                                monitor.setDescription("Deleting '%1$s' (%2$s)",
                                        a.getParentPackage().getShortDescription(),
                                        a.getLocalOsPath());

                                // Delete the actual package and its internal representation
                                a.deleteLocal();

                                synchronized(mLogic.mAllPkgItems) {
                                    mLogic.mAllPkgItems.remove(entry.getValue());
                                }

                                monitor.incProgress(1);
                                if (monitor.isCancelRequested()) {
                                    break;
                                }
                            }

                            monitor.incProgress(1);
                            monitor.setDescription("Done");
                        }
                    });
                } finally {
                    endOperationPending();

                    // The local package list has changed, make sure to refresh it
                    mLogic.mUpdaterData.getLocalSdkParser().clearPackages();
                    loadPackages();
                }
            }
        }
    }

    // ----------------------

    public class PkgCellLabelProvider extends ColumnLabelProvider implements ITableFontProvider {

        private final TreeViewerColumn mColumn;

        public PkgCellLabelProvider(TreeViewerColumn column) {
            super();
            mColumn = column;
        }

        @Override
        public String getText(Object element) {

            if (mColumn == mColumnName) {

                if (element instanceof PkgCategory) {
                    return ((PkgCategory) element).getLabel();
                } else if (element instanceof PkgItem) {
                    return getPkgItemname((PkgItem) element);
                } else if (element instanceof IDescription) {
                    return ((IDescription) element).getShortDescription();
                }

            } else if (mColumn == mColumnApi) {

                int api = -1;
                if (element instanceof PkgItem) {
                    api = ((PkgItem) element).getApi();
                }
                if (api >= 1) {
                    return Integer.toString(api);
                }

            } else if (mColumn == mColumnRevision) {

                if (element instanceof PkgItem) {
                    PkgItem pkg = (PkgItem) element;

                    if (pkg.getState() == PkgState.INSTALLED) {
                        return Integer.toString(pkg.getRevision());
                    }
                }

            } else if (mColumn == mColumnStatus) {

                if (element instanceof PkgItem) {
                    PkgItem pkg = (PkgItem) element;

                    switch(pkg.getState()) {
                    case INSTALLED:
                        Package update = pkg.getUpdatePkg();
                        if (update != null) {
                            return String.format(
                                    "Update available: rev. %1$s",
                                    update.getRevision());
                        }
                        return "Installed";

                    case NEW:
                        return "Not installed";
                    }
                    return pkg.getState().toString();

                } else if (element instanceof Package) {
                    // This is an update package.
                    return "New revision " + Integer.toString(((Package) element).getRevision());
                }
            }

            return "";
        }

        private String getPkgItemname(PkgItem item) {
            String name = item.getName().trim();

            if (isSortByApi()) {
                // When sorting by API, the package name might contains the API number
                // or the platform name at the end. If we find it, cut it out since it's
                // redundant.

                PkgApiCategory cat = (PkgApiCategory) findCategoryForItem(item);
                String apiLabel = cat.getApiLabel();
                String platLabel = cat.getPlatformName();

                if (platLabel != null && name.endsWith(platLabel)) {
                    return name.substring(0, name.length() - platLabel.length());

                } else if (apiLabel != null && name.endsWith(apiLabel)) {
                    return name.substring(0, name.length() - apiLabel.length());
                }
            }

            return name;
        }

        private PkgCategory findCategoryForItem(PkgItem item) {
            for (PkgCategory cat : mLogic.mCurrentCategories) {
                for (PkgItem i : cat.getItems()) {
                    if (i == item) {
                        return cat;
                    }
                }
            }

            return null;
        }

        @Override
        public Image getImage(Object element) {
            ImageFactory imgFactory = mLogic.mUpdaterData.getImageFactory();

            if (imgFactory != null) {
                if (mColumn == mColumnName) {
                    if (element instanceof PkgCategory) {
                        return imgFactory.getImageForObject(((PkgCategory) element).getIconRef());
                    } else if (element instanceof PkgItem) {
                        return imgFactory.getImageForObject(((PkgItem) element).getMainPackage());
                    }
                    return imgFactory.getImageForObject(element);

                } else if (mColumn == mColumnStatus && element instanceof PkgItem) {
                    PkgItem pi = (PkgItem) element;
                    switch(pi.getState()) {
                    case INSTALLED:
                        if (pi.hasUpdatePkg()) {
                            return imgFactory.getImageByName(ICON_PKG_UPDATE);
                        } else {
                            return imgFactory.getImageByName(ICON_PKG_INSTALLED);
                        }
                    case NEW:
                        return imgFactory.getImageByName(ICON_PKG_NEW);
                    }
                }
            }
            return super.getImage(element);
        }

        // -- ITableFontProvider

        public Font getFont(Object element, int columnIndex) {
            if (element instanceof PkgItem) {
                if (((PkgItem) element).getState() == PkgState.NEW) {
                    return mTreeFontItalic;
                }
            } else if (element instanceof Package) {
                // update package
                return mTreeFontItalic;
            }
            return super.getFont(element);
        }
    }

    private class PkgContentProvider implements ITreeContentProvider {

        public Object[] getChildren(Object parentElement) {

            if (parentElement instanceof ArrayList<?>) {
                return ((ArrayList<?>) parentElement).toArray();

            } else if (parentElement instanceof PkgCategory) {
                return ((PkgCategory) parentElement).getItems().toArray();

            } else if (parentElement instanceof PkgItem) {
                if (mDisplayArchives) {

                    Package pkg = ((PkgItem) parentElement).getUpdatePkg();

                    // Display update packages as sub-items if the details mode is activated.
                    if (pkg != null) {
                        return new Object[] { pkg };
                    }

                    return ((PkgItem) parentElement).getArchives();
                }

            } else if (parentElement instanceof Package) {
                if (mDisplayArchives) {
                    return ((Package) parentElement).getArchives();
                }

            }

            return new Object[0];
        }

        public Object getParent(Object element) {
            // Pass. We don't try to compute the parent element and so far
            // that doesn't seem to affect the behavior of the tree widget.
            return null;
        }

        public boolean hasChildren(Object parentElement) {
            if (parentElement instanceof ArrayList<?>) {
                return true;

            } else if (parentElement instanceof PkgCategory) {
                return true;

            } else if (parentElement instanceof PkgItem) {
                if (mDisplayArchives) {
                    Package pkg = ((PkgItem) parentElement).getUpdatePkg();

                    // Display update packages as sub-items if the details mode is activated.
                    if (pkg != null) {
                        return true;
                    }

                    Archive[] archives = ((PkgItem) parentElement).getArchives();
                    return archives.length > 0;
                }
            } else if (parentElement instanceof Package) {
                if (mDisplayArchives) {
                    return ((Package) parentElement).getArchives().length > 0;
                }
            }

            return false;
        }

        public Object[] getElements(Object inputElement) {
            return getChildren(inputElement);
        }

        public void dispose() {
            // unused

        }

        public void inputChanged(Viewer arg0, Object arg1, Object arg2) {
            // unused
        }
    }

    static class PkgCategory {
        private final int mKey;
        private final Object mIconRef;
        private final List<PkgItem> mItems = new ArrayList<PkgItem>();
        private String mLabel;

        // When sorting by Source, key is the hash of the source's name.
        // When storing by API, key is the API level (>=1). Tools and extra have the
        // special values.

        public PkgCategory(int key, String label, Object iconRef) {
            mKey = key;
            mLabel = label;
            mIconRef = iconRef;
        }

        public int getKey() {
            return mKey;
        }

        public String getLabel() {
            return mLabel;
        }

        public void setLabel(String label) {
            mLabel = label;
        }

        public Object getIconRef() {
            return mIconRef;
        }

        public List<PkgItem> getItems() {
            return mItems;
        }

        @Override
        public String toString() {
            return String.format("%s <key=%08x, label=%s, #items=%d>",
                    this.getClass().getSimpleName(),
                    mKey,
                    mLabel,
                    mItems.size());
        }

        /** {@link PkgCategory} are equal if their internal key is the same. */
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + mKey;
            return result;
        }

        /** {@link PkgCategory} are equal if their internal key is the same. */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            PkgCategory other = (PkgCategory) obj;
            if (mKey != other.mKey) return false;
            return true;
        }
    }

    private static class PkgApiCategory extends PkgCategory {

        /** Platform name, in the form "Android 1.2". Can be null if we don't have the name. */
        private String mPlatformName;

        // When sorting by Source, key is the hash of the source's name.
        // When storing by API, key is the API level (>=1). Tools and extra have the
        // special values so they get naturally sorted the way we want them.
        // (Note: don't use max to avoid integers wrapping in comparisons. We can
        // revisit the day we get 2^30 platforms.)
        public final static int KEY_TOOLS = Integer.MAX_VALUE / 2;
        public final static int KEY_EXTRA = -1;

        public PkgApiCategory(int apiKey, String platformName, Object iconRef) {
            super(apiKey, null /*label*/, iconRef);
            setPlatformName(platformName);
        }

        public String getPlatformName() {
            return mPlatformName;
        }

        public void setPlatformName(String platformName) {
            if (platformName != null) {
                // Normal case for actual platform categories
                mPlatformName = String.format("Android %1$s", platformName);
                super.setLabel(null);
            }
        }

        public String getApiLabel() {
            int api = getKey();
            if (api == KEY_TOOLS) {
                return "TOOLS"; //$NON-NLS-1$ for internal use only
            } else if (api == KEY_EXTRA) {
                return "EXTRAS"; //$NON-NLS-1$ for internal use only
            } else {
                return String.format("API %1$d", getKey());
            }
        }

        @Override
        public String getLabel() {
            String label = super.getLabel();
            if (label == null) {
                int key = getKey();

                if (key == KEY_TOOLS) {
                    label = "Tools";
                } else if (key == KEY_EXTRA) {
                    label = "Extras";
                } else {
                    if (mPlatformName != null) {
                        label = String.format("%1$s (%2$s)", mPlatformName, getApiLabel());
                    } else {
                        label = getApiLabel();
                    }
                }
                super.setLabel(label);
            }
            return label;
        }

        @Override
        public void setLabel(String label) {
            throw new UnsupportedOperationException("Use setPlatformName() instead.");
        }

        @Override
        public String toString() {
            return String.format("%s <API=%s, label=%s, #items=%d>",
                    this.getClass().getSimpleName(),
                    getApiLabel(),
                    getLabel(),
                    getItems().size());
        }
    }


    // --- Implementation of ISdkChangeListener ---

    public void onSdkLoaded() {
        onSdkReload();
    }

    public void onSdkReload() {
        loadPackages();
    }

    public void preInstallHook() {
        // nothing to be done for now.
    }

    public void postInstallHook() {
        // nothing to be done for now.
    }


    /**
     * Helper class that separate the logic of package management from the UI
     * so that we can test it using head-less unit tests.
     */
    static abstract class PackagesPageLogic {
        final PackageLoader mPackageLoader;
        final UpdaterData mUpdaterData;

        final List<PkgCategory> mApiCategories = new ArrayList<PkgCategory>();
        final List<PkgCategory> mSourceCategories = new ArrayList<PkgCategory>();
        List<PkgCategory> mCurrentCategories = mApiCategories;
        /** Access to this list must be synchronized on {@link #mAllPkgItems}. */
        final List<PkgItem> mAllPkgItems = new ArrayList<PkgItem>();

        public PackagesPageLogic(UpdaterData updaterData) {
            mUpdaterData = updaterData;
            mPackageLoader = new PackageLoader(updaterData);
        }

        /**
         * Recompute the tree by sorting all the packages by API.
         * This does an update in-place of the mApiCategories list so that the table
         * can preserve its state (checked / expanded / selected) properly.
         */
        void sortByApiLevel() {
            ImageFactory imgFactory = mUpdaterData.getImageFactory();

            mCurrentCategories = mApiCategories;

            // We'll do an in-place update: first make a map of existing categories and
            // whatever pkg items they contain. Then prepare the new categories we want
            // which is all the existing categories + tools & extra (creating them the first time).
            // We mark all the existing items as "unused" then remove from the unused set the
            // items that we want to keep. At the end, whatever is left in the unused maps
            // are obsolete items we remove from the tree.

            // Keep a map of the initial state so that we can detect which items or categories are
            // no longer being used, so that we can remove them at the end of the in-place update.

            final Map<Integer, PkgApiCategory> categoryKeyMap = new HashMap<Integer, PkgApiCategory>();
            final Set<Integer> unusedCategoryKey = new HashSet<Integer>();
            final Set<Package> unusedPackages = new HashSet<Package>();

            // Get existing categories and packages
            for (PkgCategory cat : mApiCategories) {
                if (cat instanceof PkgApiCategory) {
                    PkgApiCategory acat = (PkgApiCategory) cat;
                    categoryKeyMap.put(acat.getKey(), acat);
                    unusedCategoryKey.add(acat.getKey());

                    for (PkgItem pi : cat.getItems()) {
                        unusedPackages.add(pi.getMainPackage());
                        if (pi.hasUpdatePkg()) {
                            unusedPackages.add(pi.getUpdatePkg());
                        }
                    }
                }
            }

            // Always add the tools & extras categories, even if empty (unlikely anyway)
            if (!unusedCategoryKey.contains(PkgApiCategory.KEY_TOOLS)) {
                PkgApiCategory acat = new PkgApiCategory(
                        PkgApiCategory.KEY_TOOLS,
                        null,
                        imgFactory.getImageByName(ICON_CAT_OTHER));
                mApiCategories.add(acat);
                categoryKeyMap.put(acat.getKey(), acat);
                unusedCategoryKey.add(acat.getKey());
            }

            if (!unusedCategoryKey.contains(PkgApiCategory.KEY_EXTRA)) {
                PkgApiCategory acat = new PkgApiCategory(
                        PkgApiCategory.KEY_EXTRA,
                        null,
                        imgFactory.getImageByName(ICON_CAT_OTHER));
                mApiCategories.add(acat);
                categoryKeyMap.put(acat.getKey(), acat);
                unusedCategoryKey.add(acat.getKey());
            }

            // Go through the new package item list
            synchronized (mAllPkgItems) {
                for (PkgItem newItem : mAllPkgItems) {
                    // Is this a package we want to display? That may change depending on the
                    // display filter (obsolete, new/updates, etc.)
                    if (!keepItem(newItem)) {
                        continue;
                    }

                    // Get the category for this item.
                    int apiKey = newItem.getApi();

                    if (apiKey < 1) {
                        Package p = newItem.getMainPackage();
                        if (p instanceof ToolPackage || p instanceof PlatformToolPackage) {
                            apiKey = PkgApiCategory.KEY_TOOLS;
                        } else {
                            apiKey = PkgApiCategory.KEY_EXTRA;
                        }
                    }

                    PkgApiCategory cat = categoryKeyMap.get(apiKey);

                    if (cat == null) {
                        // This is a new category. Create it and add it to the map.

                        // We need a label for the category.
                        // If we have an API level, try to get the info from the SDK Manager.
                        // If we don't (e.g. when installing a new platform that isn't yet available
                        // locally in the SDK Manager), it's OK we'll try to find the first platform
                        // package available.
                        String platformName = null;
                        if (apiKey != -1) {
                            for (IAndroidTarget target : mUpdaterData.getSdkManager().getTargets()) {
                                if (target.isPlatform() && target.getVersion().getApiLevel() == apiKey) {
                                    platformName = target.getVersionName();
                                    break;
                                }
                            }
                        }

                        cat = new PkgApiCategory(
                                apiKey,
                                platformName,
                                imgFactory.getImageByName(ICON_CAT_PLATFORM));
                        mApiCategories.add(0, cat);
                        categoryKeyMap.put(cat.getKey(), cat);
                    } else {
                        // Remove the category key from the unused list.
                        unusedCategoryKey.remove(apiKey);
                    }

                    // Add the item to the category or merge as an update of an existing package
                    boolean found = false;
                    if (newItem.getState() == PkgState.NEW) {
                        for (PkgItem pi : cat.getItems()) {
                            Package p = newItem.getMainPackage();
                            if (pi.isSameItemAs(newItem) ||
                                    pi.isSameMainPackageAs(p)) {
                                // It's the same item or
                                // it's not exactly the same item but the main package is the same.
                                unusedPackages.remove(pi.getMainPackage());
                                found = true;

                            } else if (pi.mergeUpdate(p)) {
                                // The new package is an update for the existing package.
                                unusedPackages.remove(pi.getMainPackage());
                                unusedPackages.remove(pi.getUpdatePkg());
                                found = true;
                            }
                            if (found) {
                                break;
                            }
                        }
                    } else {
                        // We do not try to merge installed packages. This prevents a bug in
                        // the edge case where a new update might be present in the package
                        // list before the installed item it would update. If we were trying
                        // to merge the installed item into the new package, the installed item
                        // would most likely be hidden because it would have a lesser revision.
                        // This case is not supposed to happen but if it does, we 'd better have
                        // a dup than a missing displayed package.

                        for (PkgItem pi : cat.getItems()) {
                            if (pi.isSameItemAs(newItem)) {
                                unusedPackages.remove(newItem.getMainPackage());
                                found = true;
                                break;
                            }
                        }
                    }

                    if (!found) {
                        cat.getItems().add(newItem);
                        unusedPackages.remove(newItem.getMainPackage());
                        unusedPackages.remove(newItem.getUpdatePkg());
                    }

                    if (apiKey != -1 && cat.getPlatformName() == null) {
                        // Check whether we can get the actual platform version name (e.g. "1.5")
                        // from the first Platform package we find in this category.
                        Package p = newItem.getMainPackage();
                        if (p instanceof PlatformPackage) {
                            String platformName = ((PlatformPackage) p).getVersionName();
                            cat.setPlatformName(platformName);
                        }
                    }
                }
            }

            // Now go through all the remaining categories used for the tree and clear unused items.
            for (Iterator<PkgCategory> iterCat = mApiCategories.iterator(); iterCat.hasNext(); ) {
                PkgCategory cat = iterCat.next();

                // Remove any unused categories.
                if (unusedCategoryKey.contains(cat.getKey())) {
                    iterCat.remove();
                    continue;
                }

                // Remove any unused items in the category.
                for (Iterator<PkgItem> iterItem = cat.getItems().iterator(); iterItem.hasNext(); ) {
                    PkgItem item = iterItem.next();

                    if (unusedPackages.contains(item.getMainPackage())) {
                        iterItem.remove();
                    } else if (item.hasUpdatePkg() && unusedPackages.contains(item.getUpdatePkg())) {
                        item.removeUpdate();
                    }
                }

                // Sort the items
                Collections.sort(cat.getItems());
            }

            // Sort the categories list.
            Collections.sort(mApiCategories, new Comparator<PkgCategory>() {
                public int compare(PkgCategory cat1, PkgCategory cat2) {
                    // We always want categories in order tools..platforms..extras.
                    // For platform, we compare in descending order (o2-o1).
                    return cat2.getKey() - cat1.getKey();
                }
            });
        }

        /**
         * Recompute the tree by sorting all packages by source.
         */
        void sortBySource() {

            mCurrentCategories = mSourceCategories;

            Map<SdkSource, List<PkgItem>> sourceMap = new HashMap<SdkSource, List<PkgItem>>();

            synchronized(mAllPkgItems) {
                for (PkgItem item : mAllPkgItems) {
                    if (keepItem(item)) {
                        SdkSource source = item.getSource();
                        List<PkgItem> list = sourceMap.get(source);
                        if (list == null) {
                            list = new ArrayList<PkgItem>();
                            sourceMap.put(source, list);
                        }
                        list.add(item);
                    }
                }
            }

            // Sort the sources so that we can create categories sorted the same way
            // (the categories don't link to the sources, so we can't just sort the categories.)
            Set<SdkSource> sources = new TreeSet<SdkSource>(new Comparator<SdkSource>() {
                public int compare(SdkSource o1, SdkSource o2) {
                    if (o1 == o2) {
                        return 0;
                    } else if (o1 == null && o2 != null) {
                        return -1;
                    } else if (o1 != null && o2 == null) {
                        return 1;
                    }
                    assert o1 != null;
                    return o1.toString().compareTo(o2.toString());
                }
            });
            sources.addAll(sourceMap.keySet());

            for (SdkSource source : sources) {
                Object key = source != null ? source : "Locally Installed Packages";
                Object iconRef = source != null ? source :
                            mUpdaterData.getImageFactory().getImageByName(ICON_PKG_INSTALLED);

                PkgCategory cat = new PkgCategory(
                        key.hashCode(),
                        key.toString(),
                        iconRef);

                for (PkgItem item : sourceMap.get(source)) {
                    if (item.getSource() == source) {
                        cat.getItems().add(item);
                    }
                }

                mSourceCategories.add(cat);
            }
        }

        abstract boolean keepItem(PkgItem item);
    }


    // --- End of hiding from SWT Designer ---
    //$hide<<$
}
