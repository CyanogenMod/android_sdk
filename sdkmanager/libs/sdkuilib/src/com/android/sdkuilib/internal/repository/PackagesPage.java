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

import com.android.annotations.VisibleForTesting;
import com.android.annotations.VisibleForTesting.Visibility;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.internal.repository.Archive;
import com.android.sdklib.internal.repository.IDescription;
import com.android.sdklib.internal.repository.IPackageVersion;
import com.android.sdklib.internal.repository.ITask;
import com.android.sdklib.internal.repository.ITaskMonitor;
import com.android.sdklib.internal.repository.Package;
import com.android.sdklib.internal.repository.PlatformPackage;
import com.android.sdklib.internal.repository.PlatformToolPackage;
import com.android.sdklib.internal.repository.SdkRepoSource;
import com.android.sdklib.internal.repository.SdkSource;
import com.android.sdklib.internal.repository.ToolPackage;
import com.android.sdkuilib.internal.repository.PackageLoader.ISourceLoadedCallback;
import com.android.sdkuilib.internal.repository.PackageLoader.PkgItem;
import com.android.sdkuilib.internal.repository.PackageLoader.PkgState;
import com.android.sdkuilib.internal.repository.PackagesPage.PackagesDiffLogic.UpdateOp;
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
import org.eclipse.jface.viewers.ViewerFilter;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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

    private final PackagesDiffLogic mDiffLogic;
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

        mDiffLogic = new PackagesDiffLogic(updaterData);

        createContents(this);
        postCreate();  //$hide$
    }

    public void onPageSelected() {
        if (mDiffLogic.mCurrentCategories == null || mDiffLogic.mCurrentCategories.isEmpty()) {
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
        mTreeViewer.addFilter(new ViewerFilter() {
            @Override
            public boolean select(Viewer viewer, Object parentElement, Object element) {
                return filterViewerItem(element);
            }
        });

        mTreeViewer.addCheckStateListener(new ICheckStateListener() {
            public void checkStateChanged(CheckStateChangedEvent event) {
                onTreeCheckStateChanged(event); //$hide$
            }
        });

        mTree = mTreeViewer.getTree();
        mTree.setLinesVisible(true);
        mTree.setHeaderVisible(true);
        GridDataBuilder.create(mTree).fill().grab();

        // column name icon is set when loading depending on the current filter type
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
                loadPackages();
            }
        });
        mCheckFilterNew.setSelection(true);

        mCheckFilterInstalled = new Button(mGroupOptions, SWT.CHECK);
        mCheckFilterInstalled.setToolTipText("Show Installed");
        mCheckFilterInstalled.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                loadPackages();
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
                loadPackages();
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
                loadPackages();
                // Reset the expanded state when changing sort algorithm
                expandInitial(mDiffLogic.mCurrentCategories);
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
                loadPackages();
                // Reset the expanded state when changing sort algorithm
                expandInitial(mDiffLogic.mCurrentCategories);
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
        if (mDiffLogic.mUpdaterData != null) {
            ImageFactory imgFactory = mDiffLogic.mUpdaterData.getImageFactory();
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
                    AddonSitesDialog d = new AddonSitesDialog(getShell(), mDiffLogic.mUpdaterData);
                    if (d.open()) {
                        loadPackages();
                    }
                    break;
                case TOGGLE_SHOW_ARCHIVES:
                    mDisplayArchives = !mDisplayArchives;
                    loadPackages();
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
        if (mDiffLogic.mUpdaterData != null) {
            mTextSdkOsPath.setText(mDiffLogic.mUpdaterData.getOsSdkRoot());
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
        if (mDiffLogic.mUpdaterData == null) {
            return;
        }

        // LoadPackage is synchronous but does not block the UI.
        // Consequently it's entirely possible for the user
        // to request the app to close whilst the packages are loading. Any
        // action done after loadPackages must check the UI hasn't been
        // disposed yet. Otherwise hilarity ensues.

        final boolean useSortByApi = isSortByApi();

        if (!mTreeColumnName.isDisposed()) {
            mTreeColumnName.setImage(
                    getImage(useSortByApi ? ICON_SORT_BY_API : ICON_SORT_BY_SOURCE));
        }

        final UpdateOp op = mDiffLogic.updateStart(useSortByApi);
        mDiffLogic.mPackageLoader.loadPackages(new ISourceLoadedCallback() {
            boolean needsRefresh = mDiffLogic.isSortByApi() == useSortByApi;

            public boolean onUpdateSource(SdkSource source, Package[] newPackages) {
                if (mDiffLogic.updateSourcePackages(op, source, newPackages) || needsRefresh) {
                    refreshViewerSync();
                    needsRefresh = false;
                }

                // Return true to tell the loader to continue with the next source.
                // Return false to stop the loader if any UI has been disposed, which can
                // happen if the user is trying to close the window during the load operation.
                return !mGroupPackages.isDisposed();
            }

            public void onLoadCompleted() {
                if (mDiffLogic.updateEnd(op) || needsRefresh) {
                    refreshViewerSync();
                    needsRefresh = false;
                }
            }
        });
    }

    private void refreshViewerSync() {
        // Dynamically update the table while we load after each source.
        // Since the official Android source gets loaded first, it makes the
        // window look non-empty a lot sooner.
        if (!mGroupPackages.isDisposed()) {
            mGroupPackages.getDisplay().syncExec(new Runnable() {
                public void run() {
                    if (!mGroupPackages.isDisposed()) {

                        if (mTreeViewer.getInput() != mDiffLogic.mCurrentCategories) {
                            // set initial input
                            mTreeViewer.setInput(mDiffLogic.mCurrentCategories);
                        } else {
                            // refresh existing, which preserves the expanded state, the selection
                            // and the checked state.
                            mTreeViewer.refresh();
                        }

                        // set the initial expanded state
                        expandInitial(mDiffLogic.mCurrentCategories);

                        updateButtonsState();
                        updateMenuCheckmarks();
                    }
                }
            });
        }
    }

    private boolean isSortByApi() {
        return mCheckSortApi != null && !mCheckSortApi.isDisposed() && mCheckSortApi.getSelection();
    }

    /**
     * Decide whether to keep an item in the current tree based on user-chosen filter options.
     */
    private boolean filterViewerItem(Object treeElement) {
        if (treeElement instanceof PkgCategory) {
            PkgCategory cat = (PkgCategory) treeElement;

            if (!cat.getItems().isEmpty()) {
                // A category is hidden if all of its content is hidden.
                // However empty categories are always visible.
                for (PkgItem item : cat.getItems()) {
                    if (filterViewerItem(item)) {
                        // We found at least one element that is visible.
                        return true;
                    }
                }
                return false;
            }
        }

        if (treeElement instanceof PkgItem) {
            PkgItem item = (PkgItem) treeElement;

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
        synchronized(mDiffLogic.mCurrentCategories) {
            for (PkgCategory cat : mDiffLogic.mCurrentCategories) {
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

        if (mDiffLogic.mUpdaterData != null) {
            try {
                beginOperationPending();

                mDiffLogic.mUpdaterData.updateOrInstallAll_WithGUI(
                    archives,
                    mCheckFilterObsolete.getSelection() /* includeObsoletes */);
            } finally {
                endOperationPending();

                // The local package list has changed, make sure to refresh it
                mDiffLogic.mUpdaterData.getLocalSdkParser().clearPackages();
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

                    mDiffLogic.mUpdaterData.getTaskFactory().start("Delete Package", new ITask() {
                        public void run(ITaskMonitor monitor) {
                            monitor.setProgressMax(archives.size() + 1);
                            for (Entry<Archive, PkgItem> entry : archives.entrySet()) {
                                Archive a = entry.getKey();

                                monitor.setDescription("Deleting '%1$s' (%2$s)",
                                        a.getParentPackage().getShortDescription(),
                                        a.getLocalOsPath());

                                // Delete the actual package
                                a.deleteLocal();

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
                    mDiffLogic.mUpdaterData.getLocalSdkParser().clearPackages();
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
                    return getPkgItemName((PkgItem) element);
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

        private String getPkgItemName(PkgItem item) {
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

                } else if (platLabel != null && item.isObsolete() && name.indexOf(platLabel) > 0) {
                    // For obsolete items, the format is "<base name> <platform name> (Obsolete)"
                    // so in this case only accept removing a platform name that is not at
                    // the end.
                    name = name.replace(platLabel, ""); //$NON-NLS-1$
                }
            }

            // Collapse potential duplicated spacing
            name = name.replaceAll(" +", " "); //$NON-NLS-1$ //$NON-NLS-2$

            return name;
        }

        private PkgCategory findCategoryForItem(PkgItem item) {
            for (PkgCategory cat : mDiffLogic.mCurrentCategories) {
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
            ImageFactory imgFactory = mDiffLogic.mUpdaterData.getImageFactory();

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

    @VisibleForTesting(visibility=Visibility.PRIVATE)
    static abstract class PkgCategory {
        private final Object mKey;
        private final Object mIconRef;
        private final List<PkgItem> mItems = new ArrayList<PkgItem>();
        private String mLabel;
        /** Transient flag used during incremental updates. */
        private boolean mUnused;

        public PkgCategory(Object key, String label, Object iconRef) {
            mKey = key;
            mLabel = label;
            mIconRef = iconRef;
        }

        public Object getKey() {
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

        public void setUnused(boolean unused) {
            mUnused = unused;
        }

        public boolean isUnused() {
            return mUnused;
        }

        @Override
        public String toString() {
            return String.format("%s <key=%08x, label=%s, #items=%d>",
                    this.getClass().getSimpleName(),
                    mKey == null ? "null" : mKey.toString(),
                    mLabel,
                    mItems.size());
        }

        /** {@link PkgCategory}s are equal if their internal keys are equal. */
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((mKey == null) ? 0 : mKey.hashCode());
            return result;
        }

        /** {@link PkgCategory}s are equal if their internal keys are equal. */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            PkgCategory other = (PkgCategory) obj;
            if (mKey == null) {
                if (other.mKey != null) return false;
            } else if (!mKey.equals(other.mKey)) return false;
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
            int api = ((Integer) getKey()).intValue();
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
                int key = ((Integer) getKey()).intValue();

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

    private static class PkgSourceCategory extends PkgCategory {

        /**
         * A special {@link SdkSource} object that represents the locally installed
         * items, or more exactly a lack of remote source.
         */
        public final static SdkSource UNKNOWN_SOURCE =
            new SdkRepoSource("http://no.source", "Local Packages");
        private final SdkSource mSource;

        public PkgSourceCategory(SdkSource source, UpdaterData updaterData) {
            super(
                source, // the source is the key and it can be null
                source == UNKNOWN_SOURCE ? "Local Packages" : source.toString(),
                source == UNKNOWN_SOURCE ?
                        updaterData.getImageFactory().getImageByName(ICON_PKG_INSTALLED) :
                            source);
            mSource = source;
        }

        @Override
        public String toString() {
            return String.format("%s <source=%s, #items=%d>",
                    this.getClass().getSimpleName(),
                    mSource.toString(),
                    getItems().size());
        }

        public SdkSource getSource() {
            return mSource;
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
     * Helper class that separates the logic of package management from the UI
     * so that we can test it using head-less unit tests.
     */
    static class PackagesDiffLogic {
        final PackageLoader mPackageLoader;
        final UpdaterData mUpdaterData;

        final List<PkgCategory> mApiCategories = new ArrayList<PkgCategory>();
        final List<PkgCategory> mSourceCategories = new ArrayList<PkgCategory>();
        List<PkgCategory> mCurrentCategories = mApiCategories;

        public PackagesDiffLogic(UpdaterData updaterData) {
            mUpdaterData = updaterData;
            mPackageLoader = new PackageLoader(updaterData);
        }

        /**
         * An update operation, customized to either sort by API or sort by source.
         */
        abstract class UpdateOp {
            public final Set<SdkSource> mVisitedSources = new HashSet<SdkSource>();

            /** Retrieve the category key for the given package, either local or remote. */
            public abstract Object getCategoryKey(Package pkg);
            /** Modified {@code currentCategories} to add default categories. */
            public abstract void addDefaultCategories(List<PkgCategory> currentCategories);
            /** Creates the category for the given key and returns it. */
            public abstract PkgCategory createCategory(Object catKey);
            /** Sorts the category list (but not the items within the categories.) */
            public abstract void sortCategoryList(List<PkgCategory> categoryList);
            /** Called after items of a given category have changed. Used to sort the
             * items and/or adjust the category name. */
            public abstract void postCategoryItemsChanged(List<PkgCategory> categoryList);
            /** Add the new package or merge it as an update or does nothing if this package
             * is already part of the category items.
             * Returns true if the category item list has changed. */
            public abstract boolean mergeNewPackage(Package newPackage, PkgCategory cat);
        }

        public boolean isSortByApi() {
            return mCurrentCategories == mApiCategories;
        }

        public UpdateOp updateStart(boolean sortByApi) {
            mCurrentCategories = sortByApi ? mApiCategories : mSourceCategories;

            UpdateOp info = sortByApi ? (new UpdateOpApi()) : (new UpdateOpSource());

            // Note that default categories are created after the unused ones so that
            // the callback can decide whether they should be marked as unused or not.
            for (PkgCategory cat : mCurrentCategories) {
                cat.setUnused(true);
            }

            info.addDefaultCategories(mCurrentCategories);

            return info;
        }

        public boolean updateSourcePackages(UpdateOp op, SdkSource source, Package[] newPackages) {
            if (newPackages.length > 0) {
                op.mVisitedSources.add(source);
            }
            if (source == null) {
                return processLocals(op, newPackages);
            } else {
                return processSource(op, source, newPackages);
            }
        }

        public boolean updateEnd(UpdateOp op) {
            boolean hasChanged = false;

            // Remove unused categories
            for (Iterator<PkgCategory> catIt = mCurrentCategories.iterator(); catIt.hasNext(); ) {
                PkgCategory cat = catIt.next();
                if (cat.isUnused()) {
                    catIt.remove();
                    hasChanged  = true;
                    continue;
                }

                // Remove all items which source we have not been visited. They are obsolete.
                for (Iterator<PkgItem> itemIt = cat.getItems().iterator(); itemIt.hasNext(); ) {
                    PkgItem item = itemIt.next();
                    if (!op.mVisitedSources.contains(item.getSource())) {
                        itemIt.remove();
                        hasChanged  = true;
                    }
                }
            }
            return hasChanged;
        }

        /** Process all local packages. Returns true if something changed.
         * @param op */
        private boolean processLocals(UpdateOp op, Package[] packages) {
            boolean hasChanged = false;
            Set<Package> newPackages = new HashSet<Package>(Arrays.asList(packages));
            Set<Package> unusedPackages = new HashSet<Package>(newPackages);

            assert newPackages.size() == packages.length;

            // Upgrade 'new' items to 'installed' for any local package we already know about
            for (PkgCategory cat : mCurrentCategories) {
                List<PkgItem> items = cat.getItems();
                for (int i = 0; i < items.size(); i++) {
                    PkgItem item = items.get(i);

                    if (item.hasUpdatePkg() && newPackages.contains(item.getUpdatePkg())) {
                        // This item has an update package that is now installed.
                        PkgItem installed = new PkgItem(item.getUpdatePkg(), PkgState.INSTALLED);
                        unusedPackages.remove(item.getUpdatePkg());
                        item.removeUpdate();
                        items.add(installed);
                        cat.setUnused(false);
                        hasChanged = true;
                    }

                    if (newPackages.contains(item.getMainPackage())) {
                        unusedPackages.remove(item.getMainPackage());
                        if (item.getState() == PkgState.NEW) {
                            // This item has a main package that is now installed.
                            item.setState(PkgState.INSTALLED);
                            cat.setUnused(false);
                            hasChanged = true;
                        }
                    }
                }
            }

            // Downgrade 'installed' items to 'new' if their package isn't listed anymore
            for (PkgCategory cat : mCurrentCategories) {
                for (PkgItem item : cat.getItems()) {
                    if (item.getState() == PkgState.INSTALLED &&
                            !newPackages.contains(item.getMainPackage())) {
                        item.setState(PkgState.NEW);
                        hasChanged = true;
                    }
                }
            }

            // Create new 'installed' items for any local package we haven't processed yet
            for (Package newPackage : unusedPackages) {
                Object catKey = op.getCategoryKey(newPackage);
                PkgCategory cat = findCurrentCategory(mCurrentCategories, catKey);

                if (cat == null) {
                    // This is a new category. Create it and add it to the list.
                    cat = op.createCategory(catKey);
                    mCurrentCategories.add(cat);
                    op.sortCategoryList(mCurrentCategories);
                }

                cat.getItems().add(new PkgItem(newPackage, PkgState.INSTALLED));
                cat.setUnused(false);
                hasChanged = true;
            }

            if (hasChanged) {
                op.postCategoryItemsChanged(mCurrentCategories);
            }

            return hasChanged;
        }

        /** Process all remote packages. Returns true if something changed.
         * @param op */
        private boolean processSource(UpdateOp op, SdkSource source, Package[] packages) {
            boolean hasChanged = false;
            // Note: unusedPackages must respect the original packages order. It can't be a set.
            List<Package> unusedPackages = new ArrayList<Package>(Arrays.asList(packages));
            Set<Package> newPackages = new HashSet<Package>(unusedPackages);

            assert newPackages.size() == packages.length;

            // Remove any items or updates that are no longer in the source's packages
            for (PkgCategory cat : mCurrentCategories) {
                List<PkgItem> items = cat.getItems();
                for (int i = 0; i < items.size(); i++) {
                    PkgItem item = items.get(i);
                    SdkSource itemSource = item.getSource();

                    // Only process items matching the current source
                    if (!(itemSource == source || (source != null && source.equals(itemSource)))) {
                        continue;
                    }
                    // Installed items have been dealt with the local source,
                    // so only change new items here
                    if (item.getState() == PkgState.NEW &&
                            !newPackages.contains(item.getMainPackage())) {
                        // This package is no longer part of the source.
                        items.remove(i--);
                        hasChanged = true;
                        continue;
                    }

                    cat.setUnused(false);
                    unusedPackages.remove(item.getMainPackage());

                    if (item.hasUpdatePkg()) {
                        if (newPackages.contains(item.getUpdatePkg())) {
                            unusedPackages.remove(item.getUpdatePkg());
                        } else {
                            // This update is no longer part of the source
                            item.removeUpdate();
                            hasChanged = true;
                        }
                    }
                }
            }

            // Add any new unknown packages
            for (Package newPackage : unusedPackages) {
                Object catKey = op.getCategoryKey(newPackage);
                PkgCategory cat = findCurrentCategory(mCurrentCategories, catKey);

                if (cat == null) {
                    // This is a new category. Create it and add it to the list.
                    cat = op.createCategory(catKey);
                    mCurrentCategories.add(cat);
                    op.sortCategoryList(mCurrentCategories);
                }

                // Add the new package or merge it as an update
                hasChanged |= op.mergeNewPackage(newPackage, cat);
            }

            if (hasChanged) {
                op.postCategoryItemsChanged(mCurrentCategories);
            }

            return hasChanged;
        }

        private PkgCategory findCurrentCategory(
                List<PkgCategory> currentCategories,
                Object categoryKey) {
            for (PkgCategory cat : currentCategories) {
                if (cat.getKey().equals(categoryKey)) {
                    return cat;
                }
            }
            return null;
        }

        /**
         * {@link UpdateOp} describing the Sort-by-API operation.
         */
        private class UpdateOpApi extends UpdateOp {
            @Override
            public Object getCategoryKey(Package pkg) {
                // Sort by API

                if (pkg instanceof IPackageVersion) {
                    return ((IPackageVersion) pkg).getVersion().getApiLevel();

                } else if (pkg instanceof ToolPackage || pkg instanceof PlatformToolPackage) {
                    return PkgApiCategory.KEY_TOOLS;

                } else {
                    return PkgApiCategory.KEY_EXTRA;
                }
            }

            @Override
            public void addDefaultCategories(List<PkgCategory> currentCategories) {
                boolean needTools = true;
                boolean needExtras = true;

                for (PkgCategory cat : currentCategories) {
                    if (cat.getKey().equals(PkgApiCategory.KEY_TOOLS)) {
                        // Mark them as no unused to prevent their removal in updateEnd().
                        cat.setUnused(false);
                        needTools = false;
                    } else if (cat.getKey().equals(PkgApiCategory.KEY_EXTRA)) {
                        cat.setUnused(false);
                        needExtras = false;
                    }
                }

                // Always add the tools & extras categories, even if empty (unlikely anyway)
                if (needTools) {
                    PkgApiCategory acat = new PkgApiCategory(
                            PkgApiCategory.KEY_TOOLS,
                            null,
                            mUpdaterData.getImageFactory().getImageByName(ICON_CAT_OTHER));
                    currentCategories.add(acat);
                }

                if (needExtras) {
                    PkgApiCategory acat = new PkgApiCategory(
                            PkgApiCategory.KEY_EXTRA,
                            null,
                            mUpdaterData.getImageFactory().getImageByName(ICON_CAT_OTHER));
                    currentCategories.add(acat);
                }
            }

            @Override
            public PkgCategory createCategory(Object catKey) {
                // Create API category.
                PkgCategory cat = null;

                assert catKey instanceof Integer;
                int apiKey = ((Integer) catKey).intValue();

                // We need a label for the category.
                // If we have an API level, try to get the info from the SDK Manager.
                // If we don't (e.g. when installing a new platform that isn't yet available
                // locally in the SDK Manager), it's OK we'll try to find the first platform
                // package available.
                String platformName = null;
                if (apiKey >= 1 && apiKey != PkgApiCategory.KEY_TOOLS) {
                    for (IAndroidTarget target :
                            mUpdaterData.getSdkManager().getTargets()) {
                        if (target.isPlatform() &&
                                target.getVersion().getApiLevel() == apiKey) {
                            platformName = target.getVersionName();
                            break;
                        }
                    }
                }

                cat = new PkgApiCategory(
                        apiKey,
                        platformName,
                        mUpdaterData.getImageFactory().getImageByName(ICON_CAT_PLATFORM));

                return cat;
            }

            @Override
            public boolean mergeNewPackage(Package newPackage, PkgCategory cat) {
                // First check if the new package could be an update
                // to an existing package
                for (PkgItem item : cat.getItems()) {
                    if (item.isSameMainPackageAs(newPackage)) {
                        // Seems like this isn't really a new item after all.
                        cat.setUnused(false);
                        // Return false since we're not changing anything.
                        return false;
                    } else if (item.mergeUpdate(newPackage)) {
                        // The new package is an update for the existing package
                        // and has been merged in the PkgItem as such.
                        cat.setUnused(false);
                        // Return true to indicate we changed something.
                        return true;
                    }
                }

                // This is truly a new item.
                cat.getItems().add(new PkgItem(newPackage, PkgState.NEW));
                cat.setUnused(false);
                return true; // something has changed
            }

            @Override
            public void sortCategoryList(List<PkgCategory> categoryList) {
                // Sort the categories list.
                // We always want categories in order tools..platforms..extras.
                // For platform, we compare in descending order (o2-o1).
                // This order is achieved by having the category keys ordered as
                // needed for the sort to just do what we expect.

                Collections.sort(categoryList, new Comparator<PkgCategory>() {
                    public int compare(PkgCategory cat1, PkgCategory cat2) {
                        assert cat1 instanceof PkgApiCategory;
                        assert cat2 instanceof PkgApiCategory;
                        int api1 = ((Integer) cat1.getKey()).intValue();
                        int api2 = ((Integer) cat2.getKey()).intValue();
                        return api2 - api1;
                    }
                });
            }

            @Override
            public void postCategoryItemsChanged(List<PkgCategory> categoryList) {
                // Sort the items
                for (PkgCategory cat : mCurrentCategories) {
                    Collections.sort(cat.getItems());

                    // When sorting by API, we can't always get the platform name
                    // from the package manager. In this case at the very end we
                    // look for a potential platform package we can use to extract
                    // the platform version name (e.g. '1.5') from the first suitable
                    // platform package we can find.

                    assert cat instanceof PkgApiCategory;
                    PkgApiCategory pac = (PkgApiCategory) cat;
                    if (pac.getPlatformName() == null) {
                        // Check whether we can get the actual platform version name (e.g. "1.5")
                        // from the first Platform package we find in this category.

                        for (PkgItem item : cat.getItems()) {
                            Package p = item.getMainPackage();
                            if (p instanceof PlatformPackage) {
                                String platformName = ((PlatformPackage) p).getVersionName();
                                if (platformName != null) {
                                    pac.setPlatformName(platformName);
                                    break;
                                }
                            }
                        }
                    }
                }

            }
        }

        /**
         * {@link UpdateOp} describing the Sort-by-Source operation.
         */
        private class UpdateOpSource extends UpdateOp {
            @Override
            public Object getCategoryKey(Package pkg) {
                // Sort by source
                SdkSource source = pkg.getParentSource();
                if (source == null) {
                    return PkgSourceCategory.UNKNOWN_SOURCE;
                }
                return source;
            }

            @Override
            public void addDefaultCategories(List<PkgCategory> currentCategories) {
                for (PkgCategory cat : currentCategories) {
                    if (cat.getKey().equals(PkgSourceCategory.UNKNOWN_SOURCE)) {
                        // Already present.
                        return;
                    }
                }

                // Always add the local categories, even if empty (unlikely anyway)
                PkgSourceCategory cat = new PkgSourceCategory(
                        PkgSourceCategory.UNKNOWN_SOURCE,
                        mUpdaterData);
                // Mark it as unused so that it can be cleared in updateEnd() if not used.
                cat.setUnused(true);
                currentCategories.add(cat);
            }

            @Override
            public PkgCategory createCategory(Object catKey) {
                assert catKey instanceof SdkSource;
                PkgCategory cat = new PkgSourceCategory((SdkSource) catKey, mUpdaterData);
                return cat;

            }

            @Override
            public boolean mergeNewPackage(Package newPackage, PkgCategory cat) {
                // First check if the new package could be an update
                // to an existing package
                for (PkgItem item : cat.getItems()) {
                    if (item.isSameMainPackageAs(newPackage)) {
                        // Seems like this isn't really a new item after all.
                        cat.setUnused(false);
                        // Return false since we're not changing anything.
                        return false;
                    } else if (item.mergeUpdate(newPackage)) {
                        // The new package is an update for the existing package
                        // and has been merged in the PkgItem as such.
                        cat.setUnused(false);
                        // Return true to indicate we changed something.
                        return true;
                    }
                }

                // This is truly a new item.
                cat.getItems().add(new PkgItem(newPackage, PkgState.NEW));
                cat.setUnused(false);
                return true; // something has changed
            }

            @Override
            public void sortCategoryList(List<PkgCategory> categoryList) {
                // Sort the sources in ascending source name order,
                // with the local packages always first.

                Collections.sort(categoryList, new Comparator<PkgCategory>() {
                    public int compare(PkgCategory cat1, PkgCategory cat2) {
                        assert cat1 instanceof PkgSourceCategory;
                        assert cat2 instanceof PkgSourceCategory;

                        SdkSource src1 = ((PkgSourceCategory) cat1).getSource();
                        SdkSource src2 = ((PkgSourceCategory) cat2).getSource();

                        if (src1 == src2) {
                            return 0;
                        } else if (src1 == PkgSourceCategory.UNKNOWN_SOURCE) {
                            return -1;
                        } else if (src2 == PkgSourceCategory.UNKNOWN_SOURCE) {
                            return 1;
                        }
                        assert src1 != null; // true because LOCAL_SOURCE==null
                        assert src2 != null;
                        return src1.toString().compareTo(src2.toString());
                    }
                });
            }

            @Override
            public void postCategoryItemsChanged(List<PkgCategory> categoryList) {
                // Sort the items
                for (PkgCategory cat : mCurrentCategories) {
                    Collections.sort(cat.getItems());
                }
            }
        }
    }


    // --- End of hiding from SWT Designer ---
    //$hide<<$
}
