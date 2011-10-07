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

import com.android.sdklib.internal.repository.Archive;
import com.android.sdklib.internal.repository.IDescription;
import com.android.sdklib.internal.repository.ITask;
import com.android.sdklib.internal.repository.ITaskMonitor;
import com.android.sdklib.internal.repository.Package;
import com.android.sdklib.internal.repository.SdkSource;
import com.android.sdkuilib.internal.repository.IPageListener;
import com.android.sdkuilib.internal.repository.UpdaterData;
import com.android.sdkuilib.internal.repository.UpdaterPage;
import com.android.sdkuilib.internal.repository.icons.ImageFactory;
import com.android.sdkuilib.internal.repository.sdkman2.PackageLoader.ISourceLoadedCallback;
import com.android.sdkuilib.internal.repository.sdkman2.PkgItem.PkgState;
import com.android.sdkuilib.repository.ISdkChangeListener;
import com.android.sdkuilib.repository.SdkUpdaterWindow.SdkInvocationContext;
import com.android.sdkuilib.ui.GridDataBuilder;
import com.android.sdkuilib.ui.GridLayoutBuilder;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ITableFontProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.jface.viewers.TreeColumnViewerLabelProvider;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.window.ToolTip;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

/**
 * Page that displays both locally installed packages as well as all known
 * remote available packages. This gives an overview of what is installed
 * vs what is available and allows the user to update or install packages.
 */
public class PackagesPage extends UpdaterPage
        implements ISdkChangeListener, IPageListener {

    static final String ICON_CAT_OTHER      = "pkgcat_other_16.png";    //$NON-NLS-1$
    static final String ICON_CAT_PLATFORM   = "pkgcat_16.png";          //$NON-NLS-1$
    static final String ICON_SORT_BY_SOURCE = "source_icon16.png";      //$NON-NLS-1$
    static final String ICON_SORT_BY_API    = "platform_pkg_16.png";    //$NON-NLS-1$
    static final String ICON_PKG_NEW        = "pkg_new_16.png";         //$NON-NLS-1$
    static final String ICON_PKG_UPDATE     = "pkg_update_16.png";      //$NON-NLS-1$
    static final String ICON_PKG_INSTALLED  = "pkg_installed_16.png";   //$NON-NLS-1$

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

    private final SdkInvocationContext mContext;
    private final UpdaterData mUpdaterData;
    private final PackagesDiffLogic mDiffLogic;
    private boolean mDisplayArchives = false;
    private boolean mOperationPending;

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

    public PackagesPage(
            Composite parent,
            int swtStyle,
            UpdaterData updaterData,
            SdkInvocationContext context) {
        super(parent, swtStyle);
        mUpdaterData = updaterData;
        mContext = context;

        mDiffLogic = new PackagesDiffLogic(updaterData);

        createContents(this);
        postCreate();  //$hide$
    }

    public void onPageSelected() {
        List<PkgCategory> cats = mDiffLogic.getCategories(isSortByApi());
        if (cats == null || cats.isEmpty()) {
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

        mTreeViewer.addDoubleClickListener(new IDoubleClickListener() {
            public void doubleClick(DoubleClickEvent event) {
                onTreeDoubleClick(event); //$hide$
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
                refreshViewerInput();
            }
        });
        mCheckFilterNew.setSelection(true);

        mCheckFilterInstalled = new Button(mGroupOptions, SWT.CHECK);
        mCheckFilterInstalled.setToolTipText("Show Installed");
        mCheckFilterInstalled.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                refreshViewerInput();
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
                refreshViewerInput();
            }
        });
        mCheckFilterObsolete.setSelection(false);

        Link linkSelectNew = new Link(mGroupOptions, SWT.NONE);
        // Note for i18n: we need to identify which link is used, and this is done by using the
        // text itself so for translation purposes we want to keep the <a> link strings separate.
        final String strLinkNew = "New";
        final String strLinkUpdates = "Updates";
        linkSelectNew.setText(
                String.format("Select <a>%1$s</a> or <a>%2$s</a>", strLinkNew, strLinkUpdates));
        linkSelectNew.setToolTipText("Selects all items that are either new or updates.");
        GridDataBuilder.create(linkSelectNew).hFill().hGrab();
        linkSelectNew.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                super.widgetSelected(e);
                boolean selectNew = e.text == null || e.text.equals(strLinkNew);
                onSelectNewUpdates(selectNew, !selectNew);
            }
        });

        mButtonInstall = new Button(mGroupOptions, SWT.NONE);
        mButtonInstall.setText("");  //$NON-NLS-1$  placeholder, filled in updateButtonsState()
        mButtonInstall.setToolTipText("Install one or more packages");
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
                if (mCheckSortApi.getSelection()) {
                    refreshViewerInput();
                    copySelection(true /*toApi*/);
                    syncViewerSelection();
                }
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
                if (mCheckSortSource.getSelection()) {
                    refreshViewerInput();
                    copySelection(false /*toApi*/);
                    syncViewerSelection();
                }
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
        mButtonDelete.setText("");  //$NON-NLS-1$  placeholder, filled in updateButtonsState()
        mButtonDelete.setToolTipText("Delete one ore more installed packages");
        GridDataBuilder.create(mButtonDelete).hFill().vCenter().hGrab();
        mButtonDelete.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onButtonDelete();  //$hide$
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


    // --- menu interactions ---

    public void registerMenuAction(final MenuAction action, MenuItem item) {
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                Button button = null;

                switch (action) {
                case RELOAD:
                    fullReload();
                    break;
                case SHOW_ADDON_SITES:
                    AddonSitesDialog d = new AddonSitesDialog(getShell(), mUpdaterData);
                    if (d.open()) {
                        loadPackages();
                    }
                    break;
                case TOGGLE_SHOW_ARCHIVES:
                    mDisplayArchives = !mDisplayArchives;
                    // Force the viewer to be refreshed
                    mTreeViewer.setInput(null);
                    refreshViewerInput();
                    syncViewerSelection();
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
        if (mUpdaterData != null) {
            mTextSdkOsPath.setText(mUpdaterData.getOsSdkRoot());
        }

        mTreeViewer.setContentProvider(new PkgContentProvider());
        ColumnViewerToolTipSupport.enableFor(mTreeViewer, ToolTip.NO_RECREATE);

        mColumnApi.setLabelProvider     (new PkgTreeColumnViewerLabelProvider(mColumnApi));
        mColumnName.setLabelProvider    (new PkgTreeColumnViewerLabelProvider(mColumnName));
        mColumnStatus.setLabelProvider  (new PkgTreeColumnViewerLabelProvider(mColumnStatus));
        mColumnRevision.setLabelProvider(new PkgTreeColumnViewerLabelProvider(mColumnRevision));

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

    /**
     * Performs a full reload by removing all cached packages data, including the platforms
     * and addons from the sdkmanager instance. This will perform a full local parsing
     * as well as a full reload of the remote data (by fetching all sources again.)
     */
    private void fullReload() {
        // Clear all source information, forcing them to be refreshed.
        mUpdaterData.getSources().clearAllPackages();
        // Clear and reload all local data too.
        localReload();
    }

    /**
     * Performs a full reload of all the local package information, including the platforms
     * and addons from the sdkmanager instance. This will perform a full local parsing.
     * <p/>
     * This method does NOT force a new fetch of the remote sources.
     *
     * @see #fullReload()
     */
    private void localReload() {
        // Clear all source caches, otherwise loading will use the cached data
        mUpdaterData.getLocalSdkParser().clearPackages();
        mUpdaterData.getSdkManager().reloadSdk(mUpdaterData.getSdkLog());
        loadPackages();
    }

    private void loadPackages() {
        if (mUpdaterData == null) {
            return;
        }

        // LoadPackage is synchronous but does not block the UI.
        // Consequently it's entirely possible for the user
        // to request the app to close whilst the packages are loading. Any
        // action done after loadPackages must check the UI hasn't been
        // disposed yet. Otherwise hilarity ensues.

        final boolean displaySortByApi = isSortByApi();

        if (!mTreeColumnName.isDisposed()) {
            mTreeColumnName.setImage(
                    getImage(displaySortByApi ? ICON_SORT_BY_API : ICON_SORT_BY_SOURCE));
        }

        mDiffLogic.updateStart();
        mDiffLogic.getPackageLoader().loadPackages(new ISourceLoadedCallback() {
            public boolean onUpdateSource(SdkSource source, Package[] newPackages) {
                // This runs in a thread and must not access UI directly.
                final boolean changed = mDiffLogic.updateSourcePackages(
                        displaySortByApi, source, newPackages);

                if (!mGroupPackages.isDisposed()) {
                    mGroupPackages.getDisplay().syncExec(new Runnable() {
                        public void run() {
                            if (changed ||
                                mTreeViewer.getInput() != mDiffLogic.getCategories(isSortByApi())) {
                                refreshViewerInput();
                            }
                        }
                    });
                }

                // Return true to tell the loader to continue with the next source.
                // Return false to stop the loader if any UI has been disposed, which can
                // happen if the user is trying to close the window during the load operation.
                return !mGroupPackages.isDisposed();
            }

            public void onLoadCompleted() {
                // This runs in a thread and must not access UI directly.
                final boolean changed = mDiffLogic.updateEnd(displaySortByApi);

                if (!mGroupPackages.isDisposed()) {
                    mGroupPackages.getDisplay().syncExec(new Runnable() {
                        public void run() {
                            if (changed ||
                                mTreeViewer.getInput() != mDiffLogic.getCategories(isSortByApi())) {
                                refreshViewerInput();
                            }

                            if (mDiffLogic.isFirstLoadComplete() && !mGroupPackages.isDisposed()) {
                                // At the end of the first load, if nothing is selected then
                                // automatically select all new and update packages.
                                Object[] checked = mTreeViewer.getCheckedElements();
                                if (checked == null || checked.length == 0) {
                                    onSelectNewUpdates(true, true);
                                }
                            }
                        }
                    });
                }
            }
        });
    }

    private void refreshViewerInput() {
        // Dynamically update the table while we load after each source.
        // Since the official Android source gets loaded first, it makes the
        // window look non-empty a lot sooner.
        if (!mGroupPackages.isDisposed()) {

            List<PkgCategory> cats = mDiffLogic.getCategories(isSortByApi());
            if (mTreeViewer.getInput() != cats) {
                // set initial input
                mTreeViewer.setInput(cats);
            } else {
                // refresh existing, which preserves the expanded state, the selection
                // and the checked state.
                mTreeViewer.refresh();
            }

            // set the initial expanded state
            expandInitial(mTreeViewer.getInput());

            updateButtonsState();
            updateMenuCheckmarks();
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
        if (mTreeViewer != null && !mTreeViewer.getTree().isDisposed()) {
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
    }

    /**
     * Handle checking and unchecking of the tree items.
     *
     * When unchecking, all sub-tree items checkboxes are cleared too.
     * When checking a source, all of its packages are checked too.
     * When checking a package, only its compatible archives are checked.
     */
    private void onTreeCheckStateChanged(CheckStateChangedEvent event) {
        boolean checked = event.getChecked();
        Object elem = event.getElement();

        assert event.getSource() == mTreeViewer;

        // When selecting, we want to only select compatible archives and expand the super nodes.
        checkAndExpandItem(elem, checked, true/*fixChildren*/, true/*fixParent*/);
        updateButtonsState();
    }

    private void onTreeDoubleClick(DoubleClickEvent event) {
        assert event.getSource() == mTreeViewer;
        ISelection sel = event.getSelection();
        if (sel.isEmpty() || !(sel instanceof ITreeSelection)) {
            return;
        }
        ITreeSelection tsel = (ITreeSelection) sel;
        Object elem = tsel.getFirstElement();
        if (elem == null) {
            return;
        }

        ITreeContentProvider provider = (ITreeContentProvider) mTreeViewer.getContentProvider();
        Object[] children = provider.getElements(elem);
        if (children == null) {
            return;
        }

        if (children.length > 0) {
            // If the element has children, expand/collapse it.
            if (mTreeViewer.getExpandedState(elem)) {
                mTreeViewer.collapseToLevel(elem, 1);
            } else {
                mTreeViewer.expandToLevel(elem, 1);
            }
        } else {
            // If the element is a terminal one, select/deselect it.
            checkAndExpandItem(
                    elem,
                    !mTreeViewer.getChecked(elem),
                    false /*fixChildren*/,
                    true /*fixParent*/);
            updateButtonsState();
        }
    }

    private void checkAndExpandItem(
            Object elem,
            boolean checked,
            boolean fixChildren,
            boolean fixParent) {
        ITreeContentProvider provider = (ITreeContentProvider) mTreeViewer.getContentProvider();

        // fix the item itself
        if (checked != mTreeViewer.getChecked(elem)) {
            mTreeViewer.setChecked(elem, checked);
        }
        if (elem instanceof PkgItem) {
            // update the PkgItem to reflect the selection
            ((PkgItem) elem).setChecked(checked);
        }

        if (!checked) {
            if (fixChildren) {
                // when de-selecting, we deselect all children too
                mTreeViewer.setSubtreeChecked(elem, checked);
                for (Object child : provider.getChildren(elem)) {
                    checkAndExpandItem(child, checked, fixChildren, false/*fixParent*/);
                }
            }

            // fix the parent when deselecting
            if (fixParent) {
                Object parent = provider.getParent(elem);
                if (parent != null && mTreeViewer.getChecked(parent)) {
                    mTreeViewer.setChecked(parent, false);
                }
            }
            return;
        }

        // When selecting, we also select sub-items (for a category)
        if (fixChildren) {
            if (elem instanceof PkgCategory || elem instanceof PkgItem) {
                Object[] children = provider.getChildren(elem);
                for (Object child : children) {
                    checkAndExpandItem(child, true, fixChildren, false/*fixParent*/);
                }
                // only fix the parent once the last sub-item is set
                if (elem instanceof PkgCategory) {
                    if (children.length > 0) {
                        checkAndExpandItem(
                                children[0], true, false/*fixChildren*/, true/*fixParent*/);
                    } else {
                        mTreeViewer.setChecked(elem, false);
                    }
                }
            } else if (elem instanceof Package) {
                // in details mode, we auto-select compatible packages
                selectCompatibleArchives(elem, provider);
            }
        }

        if (fixParent && checked && elem instanceof PkgItem) {
            Object parent = provider.getParent(elem);
            if (!mTreeViewer.getChecked(parent)) {
                Object[] children = provider.getChildren(parent);
                boolean allChecked = children.length > 0;
                for (Object e : children) {
                    if (!mTreeViewer.getChecked(e)) {
                        allChecked = false;
                        break;
                    }
                }
                if (allChecked) {
                    mTreeViewer.setChecked(parent, true);
                }
            }
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
     * Checks all PkgItems that are either new or have updates.
     */
    private void onSelectNewUpdates(boolean selectNew, boolean selectUpdates) {
        // This does not update the tree itself, syncViewerSelection does it below.
        mDiffLogic.checkNewUpdateItems(selectNew, selectUpdates);
        syncViewerSelection();
        updateButtonsState();
    }

    /**
     * Deselect all checked PkgItems.
     */
    private void onDeselectAll() {
        // This does not update the tree itself, syncViewerSelection does it below.
        mDiffLogic.uncheckAllItems();
        syncViewerSelection();
        updateButtonsState();
    }

    /**
     * When switching between the tree-by-api and the tree-by-source, copy the selection
     * (aka the checked items) from one list to the other.
     * This does not update the tree itself.
     */
    private void copySelection(boolean fromSourceToApi) {
        List<PkgItem> fromItems = mDiffLogic.getAllPkgItems(!fromSourceToApi, fromSourceToApi);
        List<PkgItem> toItems = mDiffLogic.getAllPkgItems(fromSourceToApi, !fromSourceToApi);

        // deselect all targets
        for (PkgItem item : toItems) {
            item.setChecked(false);
        }

        // mark new one from the source
        for (PkgItem source : fromItems) {
            if (source.isChecked()) {
                // There should typically be a corresponding item in the target side
                for (PkgItem target : toItems) {
                    if (target.isSameMainPackageAs(source.getMainPackage())) {
                        target.setChecked(true);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Synchronize the 'checked' state of PkgItems in the tree with their internal isChecked state.
     */
    private void syncViewerSelection() {
        ITreeContentProvider provider = (ITreeContentProvider) mTreeViewer.getContentProvider();

        for (Object cat : provider.getElements(mTreeViewer.getInput())) {
            Object[] children = provider.getElements(cat);
            boolean allChecked = children.length > 0;
            for (Object child : children) {
                if (child instanceof PkgItem) {
                    PkgItem item = (PkgItem) child;
                    boolean checked = item.isChecked();
                    allChecked &= checked;

                    if (checked != mTreeViewer.getChecked(item)) {
                        if (checked) {
                            if (!mTreeViewer.getExpandedState(cat)) {
                                mTreeViewer.setExpandedState(cat, true);
                            }
                        }
                        checkAndExpandItem(item, checked, true/*fixChildren*/, false/*fixParent*/);
                    }
                }
            }

            if (allChecked != mTreeViewer.getChecked(cat)) {
                mTreeViewer.setChecked(cat, allChecked);
            }
        }
    }

    /**
     * Indicate an install/delete operation is pending.
     * This disables the install/delete buttons.
     * Use {@link #endOperationPending()} to revert, typically in a {@code try..finally} block.
     */
    private void beginOperationPending() {
        mOperationPending = true;
        updateButtonsState();
    }

    private void endOperationPending() {
        mOperationPending = false;
        updateButtonsState();
    }

    private void updateButtonsState() {
        boolean canInstall = false;
        int numPackages = 0;

        if (mDisplayArchives) {
            // In detail mode, we display archives so we can install if at
            // least one archive is selected.
            // Note that in this mode we allow the user to install an archive
            // even if it's not "compatible" with the current platform.

            Object[] checked = mTreeViewer.getCheckedElements();
            if (checked != null) {
                for (Object c : checked) {
                    if (c instanceof Archive) {
                        Archive a = (Archive) c;
                        if (!a.isLocal()) {
                            canInstall = true;
                            numPackages++;
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
                    Package p = null;

                    if (c instanceof Package) {
                        p = (Package) c;
                    } else if (c instanceof PkgItem) {
                        p = ((PkgItem) c).getMainPackage();
                    }
                    if (p != null && !p.isLocal() && p.hasCompatibleArchive()) {
                        canInstall = true;
                        numPackages++;
                    }
                }
            }
        }

        mButtonInstall.setEnabled(canInstall && !mOperationPending);
        mButtonInstall.setText(
                numPackages == 0 ? "Install packages..." :
                    numPackages == 1 ? "Install 1 package..." :
                        String.format("Install %d packages...", numPackages));

        // We can only delete local archives
        boolean canDelete = false;
        numPackages = 0;

        Object[] checked = mTreeViewer.getCheckedElements();
        if (checked != null) {
            for (Object c : checked) {
                if (c instanceof PkgItem) {
                    PkgState state = ((PkgItem) c).getState();
                    if (state == PkgState.INSTALLED) {
                        canDelete = true;
                        numPackages++;
                    }
                }
            }
        }

        mButtonDelete.setEnabled(canDelete && !mOperationPending);
        mButtonDelete.setText(
                numPackages == 0 ? "Delete packages..." :
                    numPackages == 1 ? "Delete 1 package..." :
                        String.format("Delete %d packages...", numPackages));
    }

    private void onButtonInstall() {
        ArrayList<Archive> archives = new ArrayList<Archive>();

        if (mDisplayArchives) {
            // In detail mode, we display archives so we can install only the
            // archives that are actually selected.
            // Note that in this mode we allow the user to install an archive
            // even if it's not "compatible" with the current platform.

            Object[] checked = mTreeViewer.getCheckedElements();
            if (checked != null) {
                for (Object c : checked) {
                    if (c instanceof Archive) {
                        Archive a = (Archive) c;
                        if (!a.isLocal()) {
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
                            if (!a.isLocal() && a.isCompatible()) {
                                archives.add(a);
                            }
                        }
                    }
                }
            }
        }

        if (mUpdaterData != null) {
            boolean needsRefresh = false;
            try {
                beginOperationPending();

                List<Archive> installed = mUpdaterData.updateOrInstallAll_WithGUI(
                    archives,
                    mCheckFilterObsolete.getSelection() /* includeObsoletes */,
                    mContext == SdkInvocationContext.IDE ?
                            UpdaterData.TOOLS_MSG_UPDATED_FROM_ADT :
                                UpdaterData.TOOLS_MSG_UPDATED_FROM_SDKMAN);
                needsRefresh = installed != null && !installed.isEmpty();
            } finally {
                endOperationPending();

                if (needsRefresh) {
                    // The local package list has changed, make sure to refresh it
                    localReload();
                }
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

                    mUpdaterData.getTaskFactory().start("Delete Package", new ITask() {
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
                    localReload();
                }
            }
        }
    }

    // ----------------------

    /**
     * A custom version of {@link TreeColumnViewerLabelProvider} which
     * handles {@link TreePath}s and delegates content to a base
     * {@link PkgCellLabelProvider} for the given {@link TreeViewerColumn}.
     * <p/>
     * The implementation handles a variety of providers (table label, table
     * color, table font) but does not implement a tooltip provider, so we
     * delegate the calls here to the appropriate {@link PkgCellLabelProvider}.
     * <p/>
     * Only {@link #getToolTipText(Object)} is really useful for us but we
     * delegate all the tooltip calls for completeness and avoid surprises later
     * if we ever decide to override more things in the label provider.
     */
    public class PkgTreeColumnViewerLabelProvider extends TreeColumnViewerLabelProvider {

        private CellLabelProvider mTooltipProvider;

        public PkgTreeColumnViewerLabelProvider(TreeViewerColumn column) {
            super(new PkgCellLabelProvider(column));
        }

        @Override
        public void setProviders(Object provider) {
            super.setProviders(provider);
            if (provider instanceof CellLabelProvider) {
                mTooltipProvider = (CellLabelProvider) provider;
            }
        }

        @Override
        public Image getToolTipImage(Object object) {
            if (mTooltipProvider != null) {
                return mTooltipProvider.getToolTipImage(object);
            }
            return super.getToolTipImage(object);
        }

        @Override
        public String getToolTipText(Object element) {
            if (mTooltipProvider != null) {
                return mTooltipProvider.getToolTipText(element);
            }
            return super.getToolTipText(element);
        }

        @Override
        public Color getToolTipBackgroundColor(Object object) {
            if (mTooltipProvider != null) {
                return mTooltipProvider.getToolTipBackgroundColor(object);
            }
            return super.getToolTipBackgroundColor(object);
        }

        @Override
        public Color getToolTipForegroundColor(Object object) {
            if (mTooltipProvider != null) {
                return mTooltipProvider.getToolTipForegroundColor(object);
            }
            return super.getToolTipForegroundColor(object);
        }

        @Override
        public Font getToolTipFont(Object object) {
            if (mTooltipProvider != null) {
                return mTooltipProvider.getToolTipFont(object);
            }
            return super.getToolTipFont(object);
        }

        @Override
        public Point getToolTipShift(Object object) {
            if (mTooltipProvider != null) {
                return mTooltipProvider.getToolTipShift(object);
            }
            return super.getToolTipShift(object);
        }

        @Override
        public boolean useNativeToolTip(Object object) {
            if (mTooltipProvider != null) {
                return mTooltipProvider.useNativeToolTip(object);
            }
            return super.useNativeToolTip(object);
        }

        @Override
        public int getToolTipTimeDisplayed(Object object) {
            if (mTooltipProvider != null) {
                return mTooltipProvider.getToolTipTimeDisplayed(object);
            }
            return super.getToolTipTimeDisplayed(object);
        }

        @Override
        public int getToolTipDisplayDelayTime(Object object) {
            if (mTooltipProvider != null) {
                return mTooltipProvider.getToolTipDisplayDelayTime(object);
            }
            return super.getToolTipDisplayDelayTime(object);
        }

        @Override
        public int getToolTipStyle(Object object) {
            if (mTooltipProvider != null) {
                return mTooltipProvider.getToolTipStyle(object);
            }
            return super.getToolTipStyle(object);
        }
    }

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

                PkgCategoryApi cat = (PkgCategoryApi) findCategoryForItem(item);
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
            List<PkgCategory> cats = mDiffLogic.getCategories(isSortByApi());
            for (PkgCategory cat : cats) {
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
            ImageFactory imgFactory = mUpdaterData.getImageFactory();

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

        // -- Tooltip support

        @Override
        public String getToolTipText(Object element) {
            if (element instanceof PkgItem) {
                element = ((PkgItem) element).getMainPackage();
            }
            if (element instanceof IDescription) {
                return ((IDescription) element).getLongDescription();
            }
            return super.getToolTipText(element);
        }

        @Override
        public Point getToolTipShift(Object object) {
            return new Point(15, 5);
        }

        @Override
        public int getToolTipDisplayDelayTime(Object object) {
            return 500;
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

        @SuppressWarnings("unchecked")
        public Object getParent(Object element) {
            // This operation is a tad expensive, so we do the minimum
            // and don't try to cover all cases.

            if (element instanceof PkgItem) {
                for (PkgCategory cat : (List<PkgCategory>) mTreeViewer.getInput()) {
                    if (cat.getItems().contains(element)) {
                        return cat;
                    }
                }
            }

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

    // --- Implementation of ISdkChangeListener ---

    public void onSdkLoaded() {
        onSdkReload();
    }

    public void onSdkReload() {
        // The sdkmanager finished reloading its data. We must not call localReload() from here
        // since we don't want to alter the sdkmanager's data that just finished loading.
        loadPackages();
    }

    public void preInstallHook() {
        // nothing to be done for now.
    }

    public void postInstallHook() {
        // nothing to be done for now.
    }


    // --- End of hiding from SWT Designer ---
    //$hide<<$
}
