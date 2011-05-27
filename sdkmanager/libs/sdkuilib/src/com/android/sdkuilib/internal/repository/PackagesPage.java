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
import com.android.sdklib.internal.repository.IDescription;
import com.android.sdklib.internal.repository.ITask;
import com.android.sdklib.internal.repository.ITaskMonitor;
import com.android.sdklib.internal.repository.Package;
import com.android.sdklib.internal.repository.PlatformPackage;
import com.android.sdklib.internal.repository.PlatformToolPackage;
import com.android.sdklib.internal.repository.SdkSource;
import com.android.sdklib.internal.repository.ToolPackage;
import com.android.sdkuilib.internal.repository.PackageManager.PkgItem;
import com.android.sdkuilib.internal.repository.PackageManager.PkgState;
import com.android.sdkuilib.internal.repository.icons.ImageFactory;
import com.android.sdkuilib.repository.ISdkChangeListener;
import com.android.util.Pair;

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
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
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
        SHOW_ADDON_SITES            (SWT.NONE,  "Manage Sources..."),
        TOGGLE_SHOW_ARCHIVES        (SWT.CHECK, "Show Archives Details"),
        TOGGLE_SHOW_INSTALLED_PKG   (SWT.CHECK, "Show Installed Packages"),
        TOGGLE_SHOW_OBSOLETE_PKG    (SWT.CHECK, "Show Obsolete Packages"),
        TOGGLE_SHOW_UPDATE_NEW_PKG  (SWT.CHECK, "Show Updates/New Packages"),
        SORT_API_LEVEL              (SWT.RADIO, "Sort by API Level"),
        SORT_SOURCE                 (SWT.RADIO, "Sort by Source")
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

    private final PackageManagerImpl mPkgManager;
    private final List<PkgCategory> mCategories = new ArrayList<PkgCategory>();
    private final UpdaterData mUpdaterData;

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

    public PackagesPage(Composite parent, int swtStyle, UpdaterData updaterData) {
        super(parent, swtStyle);

        mUpdaterData = updaterData;
        mPkgManager = new PackageManagerImpl(updaterData);
        createContents(this);

        postCreate();  //$hide$
    }

    public void onPageSelected() {
        if (mPkgManager.getPackages().isEmpty()) {
            // Initialize the package list the first time the page is shown.
            loadPackages();
        }
    }

    private void createContents(Composite parent) {
        GridLayout gridLayout = new GridLayout(2, false);
        gridLayout.marginWidth = 0;
        gridLayout.marginHeight = 0;
        parent.setLayout(gridLayout);

        mGroupSdk = new Composite(parent, SWT.NONE);
        mGroupSdk.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        mGroupSdk.setLayout(new GridLayout(2, false));

        Label label1 = new Label(mGroupSdk, SWT.NONE);
        label1.setText("SDK Path:");

        mTextSdkOsPath = new Text(mGroupSdk, SWT.NONE);
        mTextSdkOsPath.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        mTextSdkOsPath.setEnabled(false);

        mGroupPackages = new Group(parent, SWT.NONE);
        mGroupPackages.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));
        mGroupPackages.setText("Packages");
        mGroupPackages.setLayout(new GridLayout(1, false));

        mTreeViewer = new CheckboxTreeViewer(mGroupPackages, SWT.BORDER);

        mTreeViewer.addCheckStateListener(new ICheckStateListener() {
            public void checkStateChanged(CheckStateChangedEvent event) {
                onTreeCheckStateChanged(event); //$hide$
            }
        });

        mTree = mTreeViewer.getTree();
        mTree.setLinesVisible(true);
        mTree.setHeaderVisible(true);
        mTree.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));

        // column name icon is set in sortPackages() depending on the current filter type
        // (e.g. API level or source)
        mColumnName = new TreeViewerColumn(mTreeViewer, SWT.NONE);
        mTreeColumnName = mColumnName.getColumn();
        mTreeColumnName.setWidth(340);
        mTreeColumnName.setText("Name");

        mColumnApi = new TreeViewerColumn(mTreeViewer, SWT.NONE);
        TreeColumn treeColumn2 = mColumnApi.getColumn();
        treeColumn2.setAlignment(SWT.CENTER);
        treeColumn2.setWidth(50);
        treeColumn2.setText("API");

        mColumnRevision = new TreeViewerColumn(mTreeViewer, SWT.NONE);
        TreeColumn treeColumn3 = mColumnRevision.getColumn();
        treeColumn3.setAlignment(SWT.CENTER);
        treeColumn3.setWidth(50);
        treeColumn3.setText("Rev.");
        treeColumn3.setToolTipText("Revision currently installed");


        mColumnStatus = new TreeViewerColumn(mTreeViewer, SWT.NONE);
        TreeColumn treeColumn4 = mColumnStatus.getColumn();
        treeColumn4.setAlignment(SWT.LEAD);
        treeColumn4.setWidth(190);
        treeColumn4.setText("Status");

        mGroupOptions = new Composite(mGroupPackages, SWT.NONE);
        mGroupOptions.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        GridLayout gl_GroupOptions = new GridLayout(6, false);
        gl_GroupOptions.marginWidth = 0;
        gl_GroupOptions.marginHeight = 0;
        mGroupOptions.setLayout(gl_GroupOptions);

        Label label3 = new Label(mGroupOptions, SWT.NONE);
        label3.setText("Show:");

        mCheckFilterNew = new Button(mGroupOptions, SWT.CHECK);
        mCheckFilterNew.setToolTipText("Show Updates and New");
        mCheckFilterNew.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                sortPackages(true /*updateButtons*/);
            }
        });
        mCheckFilterNew.setSelection(true);
        mCheckFilterNew.setText("Updates/New");

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
        mCheckFilterObsolete.setToolTipText("Also show obsolete packages");
        mCheckFilterObsolete.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                sortPackages(true /*updateButtons*/);
            }
        });
        mCheckFilterObsolete.setSelection(false);
        mCheckFilterObsolete.setText("Obsolete");

        Label placeholder2 = new Label(mGroupOptions, SWT.NONE);
        placeholder2.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

        mButtonInstall = new Button(mGroupOptions, SWT.NONE);
        mButtonInstall.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        mButtonInstall.setToolTipText("Install all the selected packages");
        mButtonInstall.setText("Install Selected...");
        mButtonInstall.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onButtonInstall();  //$hide$
            }
        });

        Label label2 = new Label(mGroupOptions, SWT.NONE);
        label2.setText("Sort by:");

        mCheckSortApi = new Button(mGroupOptions, SWT.RADIO);
        mCheckSortApi.setToolTipText("Sort by API level");
        mCheckSortApi.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                sortPackages(true /*updateButtons*/);
                // Reset the expanded state when changing sort algorithm
                expandInitial(mCategories);
            }
        });
        mCheckSortApi.setText("API level");
        mCheckSortApi.setSelection(true);

        mCheckSortSource = new Button(mGroupOptions, SWT.RADIO);
        mCheckSortSource.setToolTipText("Sort by Source");
        mCheckSortSource.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                sortPackages(true /*updateButtons*/);
                // Reset the expanded state when changing sort algorithm
                expandInitial(mCategories);
            }
        });
        mCheckSortSource.setText("Source");

        new Label(mGroupOptions, SWT.NONE);
        new Label(mGroupOptions, SWT.NONE);

        mButtonDelete = new Button(mGroupOptions, SWT.NONE);
        mButtonDelete.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        mButtonDelete.setToolTipText("Delete an installed package");
        mButtonDelete.setText("Delete...");
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
                    loadPackages();
                    break;
                case SHOW_ADDON_SITES:
                    AddonSitesDialog d = new AddonSitesDialog(getShell(), mUpdaterData);
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
        if (mUpdaterData != null) {
            mTextSdkOsPath.setText(mUpdaterData.getOsSdkRoot());
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
        if (mUpdaterData == null) {
            return;
        }


        try {
            enableUi(mGroupPackages, false);

            boolean firstLoad = mPkgManager.getPackages().isEmpty();

            mPkgManager.loadPackages();

            if (firstLoad) {
                // set the initial expanded state
                expandInitial(mCategories);
            }

        } finally {
            enableUi(mGroupPackages, true);
            updateButtonsState();
            updateMenuCheckmarks();
        }
    }

    private void enableUi(Composite root, boolean enabled) {
        root.setEnabled(enabled);
        for (Control child : root.getChildren()) {
            if (child instanceof Composite) {
                enableUi((Composite) child, enabled);
            } else {
                child.setEnabled(enabled);
            }
        }
    }

    private void sortPackages(boolean updateButtons) {
        if (mCheckSortApi != null && !mCheckSortApi.isDisposed() && mCheckSortApi.getSelection()) {
            sortByApiLevel();
        } else {
            sortBySource();
        }
        if (updateButtons) {
            updateButtonsState();
            updateMenuCheckmarks();
        }
    }

    /**
     * Recompute the tree by sorting all the packages by API.
     * This does an update in-place of the mCategories list so that the table
     * can preserve its state (checked / expanded / selected) properly.
     */
    private void sortByApiLevel() {

        ImageFactory imgFactory = mUpdaterData.getImageFactory();

        if (!mTreeColumnName.isDisposed()) {
            mTreeColumnName.setImage(getImage(ICON_SORT_BY_API));
        }

        // keep a map of the initial state so that we can detect which items or categories are
        // no longer being used, so that we can removed them at the end of the in-place update.
        final Map<Integer, Pair<PkgCategory, HashSet<PkgItem>> > unusedItemsMap =
            new HashMap<Integer, Pair<PkgCategory, HashSet<PkgItem>> >();
        final Set<PkgCategory> unusedCatSet = new HashSet<PkgCategory>();

        // get existing categories
        for (PkgCategory cat : mCategories) {
            unusedCatSet.add(cat);
            unusedItemsMap.put(cat.getKey(), Pair.of(cat, new HashSet<PkgItem>(cat.getItems())));
        }

        // always add the tools & extras categories, even if empty (unlikely anyway)
        if (!unusedItemsMap.containsKey(PkgCategory.KEY_TOOLS)) {
            PkgCategory cat = new PkgCategory(
                    PkgCategory.KEY_TOOLS,
                    "Tools",
                    imgFactory.getImageByName(ICON_CAT_OTHER));
            unusedItemsMap.put(PkgCategory.KEY_TOOLS, Pair.of(cat, new HashSet<PkgItem>()));
            mCategories.add(cat);
        }

        if (!unusedItemsMap.containsKey(PkgCategory.KEY_EXTRA)) {
            PkgCategory cat = new PkgCategory(
                    PkgCategory.KEY_EXTRA,
                    "Add-ons & Extras",
                    imgFactory.getImageByName(ICON_CAT_OTHER));
            unusedItemsMap.put(PkgCategory.KEY_EXTRA, Pair.of(cat, new HashSet<PkgItem>()));
            mCategories.add(cat);
        }

        for (PkgItem item : mPkgManager.getPackages()) {
            if (!keepItem(item)) {
                continue;
            }

            int apiKey = item.getApi();

            if (apiKey < 1) {
                Package p = item.getPackage();
                if (p instanceof ToolPackage || p instanceof PlatformToolPackage) {
                    apiKey = PkgCategory.KEY_TOOLS;
                } else {
                    apiKey = PkgCategory.KEY_EXTRA;
                }
            }

            Pair<PkgCategory, HashSet<PkgItem>> mapEntry = unusedItemsMap.get(apiKey);

            if (mapEntry == null) {
                // This is a new category. Create it and add it to the map.
                // We need a label for the category. Use null right now and set it later.
                PkgCategory cat = new PkgCategory(
                        apiKey,
                        null /*label*/,
                        imgFactory.getImageByName(ICON_CAT_PLATFORM));
                mapEntry = Pair.of(cat, new HashSet<PkgItem>());
                unusedItemsMap.put(apiKey, mapEntry);
                mCategories.add(0, cat);
            }
            PkgCategory cat = mapEntry.getFirst();
            assert cat != null;
            unusedCatSet.remove(cat);

            HashSet<PkgItem> unusedItemsSet = mapEntry.getSecond();
            unusedItemsSet.remove(item);
            if (!cat.getItems().contains(item)) {
                cat.getItems().add(item);
            }

            if (apiKey != -1 && cat.getLabel() == null) {
                // Check whether we can get the actual platform version name (e.g. "1.5")
                // from the first Platform package we find in this category.
                Package p = item.getPackage();
                if (p instanceof PlatformPackage) {
                    String vn = ((PlatformPackage) p).getVersionName();
                    String name = String.format("Android %1$s (API %2$d)", vn, apiKey);
                    cat.setLabel(name);
                }
            }
        }

        for (Iterator<PkgCategory> iterCat = mCategories.iterator(); iterCat.hasNext(); ) {
            PkgCategory cat = iterCat.next();

            // Remove any unused categories.
            if (unusedCatSet.contains(cat)) {
                iterCat.remove();
                continue;
            }

            // Remove any unused items in the category.
            Integer apikey = cat.getKey();
            Pair<PkgCategory, HashSet<PkgItem>> mapEntry = unusedItemsMap.get(apikey);
            assert mapEntry != null;
            HashSet<PkgItem> unusedItems = mapEntry.getSecond();
            for (Iterator<PkgItem> iterItem = cat.getItems().iterator(); iterItem.hasNext(); ) {
                PkgItem item = iterItem.next();
                if (unusedItems.contains(item)) {
                    iterItem.remove();
                }
            }

            // Sort the items
            Collections.sort(cat.getItems());

            // Fix the category name for any API where we might not have found a platform package.
            if (cat.getLabel() == null) {
                int api = cat.getKey().intValue();
                String name = String.format("API %1$d", api);
                cat.setLabel(name);
            }
        }

        // Sort the categories list in decreasing order
        Collections.sort(mCategories, new Comparator<PkgCategory>() {
            public int compare(PkgCategory cat1, PkgCategory cat2) {
                // compare in descending order (o2-o1)
                return cat2.getKey().compareTo(cat1.getKey());
            }
        });

        if (mTreeViewer.getInput() != mCategories) {
            // set initial input
            mTreeViewer.setInput(mCategories);
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

        mCategories.clear();

        Set<SdkSource> sourceSet = new HashSet<SdkSource>();
        for (PkgItem item : mPkgManager.getPackages()) {
            if (keepItem(item)) {
                sourceSet.add(item.getSource());
            }
        }

        SdkSource[] sources = sourceSet.toArray(new SdkSource[sourceSet.size()]);
        Arrays.sort(sources, new Comparator<SdkSource>() {
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

        for (SdkSource source : sources) {
            Object key = source != null ? source : "Installed Packages";
            Object iconRef = source != null ? source :
                        mUpdaterData.getImageFactory().getImageByName(ICON_PKG_INSTALLED);

            PkgCategory cat = new PkgCategory(
                    key.hashCode(),
                    key.toString(),
                    iconRef);

            for (PkgItem item : mPkgManager.getPackages()) {
                if (item.getSource() == source) {
                    cat.getItems().add(item);
                }
            }

            mCategories.add(cat);
        }

        // We don't support in-place incremental updates so the table gets reset
        // each time we load when sorted by source.
        mTreeViewer.setInput(mCategories);
    }

    /**
     * Decide whether to keep an item in the current tree based on user-choosen filter options.
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
            if (item.getState() == PkgState.NEW ||
                    item.getState() == PkgState.HAS_UPDATE) {
                return false;
            }
        }

        return true;
    }

    /**
     * Performs the initial expansion of the tree. The expands categories that contains
     * at least one installed item and collapses the ones with nothing installed.
     */
    private void expandInitial(Object elem) {
        mTreeViewer.setExpandedState(elem, true);
        for (Object pkg :
                ((ITreeContentProvider) mTreeViewer.getContentProvider()).getChildren(elem)) {
            if (pkg instanceof PkgCategory) {
                PkgCategory cat = (PkgCategory) pkg;
                for (PkgItem item : cat.getItems()) {
                    if (item.getState() == PkgState.INSTALLED
                            || item.getState() == PkgState.HAS_UPDATE) {
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

    private void updateButtonsState() {
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
                        if (((PkgItem) c).getPackage().hasCompatibleArchive()) {
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
                    if (((PkgItem) c).getState() == PkgState.INSTALLED) {
                        canDelete = true;
                        break;
                    }
                }
            }
        }

        mButtonDelete.setEnabled(canDelete);
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
                        p = ((PkgItem) c).getPackage();

                        PkgItem pi = (PkgItem) c;
                        if (pi.getState() == PkgState.HAS_UPDATE) {
                            List<Package> updates = pi.getUpdatePkgs();
                            // If there's one and only one update, auto-select it instead.
                            if (updates != null && updates.size() == 1) {
                                p = updates.get(0);
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

        if (mUpdaterData != null) {
            try {
                enableUi(mGroupPackages, false);

                mUpdaterData.updateOrInstallAll_WithGUI(
                    archives,
                    mCheckFilterObsolete.getSelection() /* includeObsoletes */);
            } finally {
                // loadPackages will also re-enable the UI
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

        String title = "Delete SDK Package";
        String msg = "Are you sure you want to delete:";
        final List<Archive> archives = new ArrayList<Archive>();

        for (Object c : checked) {
            if (c instanceof PkgItem && ((PkgItem) c).getState() == PkgState.INSTALLED) {
                Package p = ((PkgItem) c).getPackage();

                Archive[] as = p.getArchives();
                if (as.length == 1 && as[0] != null && as[0].isLocal()) {
                    Archive archive = as[0];
                    String osPath = archive.getLocalOsPath();

                    File dir = new File(osPath);
                    if (dir.isDirectory()) {
                        msg += "\n - " + p.getShortDescription();
                        archives.add(archive);
                    }
                }
            }
        }

        if (!archives.isEmpty()) {
            msg += "\n" + "This cannot be undone.";
            if (MessageDialog.openQuestion(getShell(), title, msg)) {
                try {
                    enableUi(mGroupPackages, false);

                    mUpdaterData.getTaskFactory().start("Loading Sources", new ITask() {
                        public void run(ITaskMonitor monitor) {
                            monitor.setProgressMax(archives.size() + 1);
                            for (Archive a : archives) {
                                monitor.setDescription("Deleting '%1$s' (%2$s)",
                                        a.getParentPackage().getShortDescription(),
                                        a.getLocalOsPath());
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
                    // loadPackages will also re-enable the UI
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
                    return ((PkgItem) element).getName();
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

                    if (pkg.getState() == PkgState.INSTALLED ||
                            pkg.getState() == PkgState.HAS_UPDATE) {
                        return Integer.toString(pkg.getRevision());
                    }
                }

            } else if (mColumn == mColumnStatus) {

                if (element instanceof PkgItem) {
                    PkgItem pkg = (PkgItem) element;

                    switch(pkg.getState()) {
                    case INSTALLED:
                        return "Installed";
                    case HAS_UPDATE:
                        List<Package> updates = pkg.getUpdatePkgs();
                        if (updates.size() == 1) {
                            return String.format("Update available: rev. %1$s",
                                    updates.get(0).getRevision());
                        } else {
                            // This case should not happen in *our* typical release workflow.
                            return "Multiple updates available";
                        }
                    case NEW:
                        return "Not installed";
                        // TODO display pkg.getRevision() in a tooltip
                    }
                    return pkg.getState().toString();

                } else if (element instanceof Package) {
                    // This is an update package.
                    return "New revision " + Integer.toString(((Package) element).getRevision());
                }
            }

            return "";  //$NON-NLS-1$
        }

        @Override
        public Image getImage(Object element) {
            ImageFactory imgFactory = mUpdaterData.getImageFactory();

            if (imgFactory != null) {
                if (mColumn == mColumnName) {
                    if (element instanceof PkgCategory) {
                        return imgFactory.getImageForObject(((PkgCategory) element).getIconRef());
                    } else if (element instanceof PkgItem) {
                        return imgFactory.getImageForObject(((PkgItem) element).getPackage());
                    }
                    return imgFactory.getImageForObject(element);

                } else if (mColumn == mColumnStatus && element instanceof PkgItem) {
                    switch(((PkgItem) element).getState()) {
                    case INSTALLED:
                        return imgFactory.getImageByName(ICON_PKG_INSTALLED);
                    case HAS_UPDATE:
                        return imgFactory.getImageByName(ICON_PKG_UPDATE);
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
                List<Package> pkgs = ((PkgItem) parentElement).getUpdatePkgs();

                // Display update packages as sub-items if there's more than one
                // or if the archive/details mode is activated.
                if (pkgs != null && (pkgs.size() > 1 || mDisplayArchives)) {
                    return pkgs.toArray();
                }

                if (mDisplayArchives) {
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
                List<Package> pkgs = ((PkgItem) parentElement).getUpdatePkgs();

                // Display update packages as sub-items if there's more than one
                // or if the archive/details mode is activated.
                if (pkgs != null && (pkgs.size() > 1 || mDisplayArchives)) {
                    return !pkgs.isEmpty();
                }

                if (mDisplayArchives) {
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

    private static class PkgCategory {
        private final Integer mKey;
        private final Object mIconRef;
        private final List<PkgItem> mItems = new ArrayList<PkgItem>();
        private String mLabel;

        // When storing by API, key is the API level (>=1), except 0 is tools and 1 is extra/addons.
        // When sorting by Source, key is the hash of the source's name.
        public final static Integer KEY_TOOLS = Integer.valueOf(0);
        public final static Integer KEY_EXTRA = Integer.valueOf(-1);

        public PkgCategory(Integer key, String label, Object iconRef) {
            mKey = key;
            mLabel = label;
            mIconRef = iconRef;
        }

        public Integer getKey() {
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
    }

    private class PackageManagerImpl extends PackageManager {

        public PackageManagerImpl(UpdaterData updaterData) {
            super(updaterData);
        }

        @Override
        public void updatePackageTable() {
            // Dynamically update the table while we load after each source.
            // Since the official Android source gets loaded first, it makes the
            // window look non-empty a lot sooner.
            mGroupPackages.getDisplay().syncExec(new Runnable() {
                public void run() {
                    sortPackages(true /* updateButtons */);
                }
            });
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

    // --- End of hiding from SWT Designer ---
    //$hide<<$
}
