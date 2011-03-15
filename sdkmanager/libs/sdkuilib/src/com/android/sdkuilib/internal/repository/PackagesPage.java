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

import com.android.sdklib.internal.repository.IPackageVersion;
import com.android.sdklib.internal.repository.ITask;
import com.android.sdklib.internal.repository.ITaskMonitor;
import com.android.sdklib.internal.repository.Package;
import com.android.sdklib.internal.repository.PlatformPackage;
import com.android.sdklib.internal.repository.SdkSource;
import com.android.sdklib.internal.repository.Package.UpdateInfo;
import com.android.sdkuilib.internal.repository.icons.ImageFactory;
import com.android.sdkuilib.repository.ISdkChangeListener;

import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ITableColorProvider;
import org.eclipse.jface.viewers.ITableFontProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreeColumnViewerLabelProvider;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Page that displays both locally installed packages as well as all known
 * remote available packages. This gives an overview of what is installed
 * vs what is available and allows the user to update or install packages.
 */
public class PackagesPage extends Composite
        implements ISdkChangeListener, IPageListener {

    private final List<PkgItem> mPackages = new ArrayList<PkgItem>();
    private final List<PkgCategory> mCategories = new ArrayList<PkgCategory>();
    private final UpdaterData mUpdaterData;

    private Text mTextSdkOsPath;

    private Button mCheckSortSource;
    private Button mCheckSortApi;
    private Button mCheckFilterObsolete;
    private Button mCheckFilterInstalled;
    private Button mCheckFilterNew;
    private Composite mGroupOptions;
    private Composite mGroupSdk;
    private Group mGroupPackages;
    private Composite mGroupButtons;
    private Button mButtonDelete;
    private Button mButtonInstall;
    private Tree mTree;
    private CheckboxTreeViewer mTreeViewer;
    private TreeViewerColumn mColumnName;
    private TreeViewerColumn mColumnApi;
    private TreeViewerColumn mColumnRevision;
    private TreeViewerColumn mColumnStatus;
    private Color mColorUpdate;
    private Color mColorNew;
    private Font mTreeFontItalic;
    private Button mButtonReload;
    private Button mButtonAddonSites;

    public PackagesPage(Composite parent, UpdaterData updaterData) {
        super(parent, SWT.BORDER);

        mUpdaterData = updaterData;
        createContents(this);
        postCreate();  //$hide$
    }

    public void onPageSelected() {
        if (mPackages.isEmpty()) {
            // Initialize the package list the first time the page is shown.
            loadPackages();
        }
    }

    protected void createContents(Composite parent) {
        parent.setLayout(new GridLayout(1, false));

        mGroupSdk = new Composite(parent, SWT.NONE);
        mGroupSdk.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        mGroupSdk.setLayout(new GridLayout(2, false));

        Label label1 = new Label(mGroupSdk, SWT.NONE);
        label1.setText("SDK Path:");

        mTextSdkOsPath = new Text(mGroupSdk, SWT.NONE);
        mTextSdkOsPath.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        mTextSdkOsPath.setEnabled(false);

        mGroupPackages = new Group(parent, SWT.NONE);
        mGroupPackages.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
        mGroupPackages.setText("Packages");
        mGroupPackages.setLayout(new GridLayout(1, false));

        mTreeViewer = new CheckboxTreeViewer(mGroupPackages, SWT.BORDER);
        mTree = mTreeViewer.getTree();
        mTree.setHeaderVisible(true);
        mTree.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));

        mColumnName = new TreeViewerColumn(mTreeViewer, SWT.NONE);
        TreeColumn treeColumn1 = mColumnName.getColumn();
        treeColumn1.setImage(getImage("platform_pkg_16.png"));  //$NON-NLS-1$
        treeColumn1.setWidth(290);
        treeColumn1.setText("Name");

        mColumnApi = new TreeViewerColumn(mTreeViewer, SWT.NONE);
        TreeColumn treeColumn2 = mColumnApi.getColumn();
        treeColumn2.setWidth(50);
        treeColumn2.setText("API");

        mColumnRevision = new TreeViewerColumn(mTreeViewer, SWT.NONE);
        TreeColumn treeColumn3 = mColumnRevision.getColumn();
        treeColumn3.setWidth(50);
        treeColumn3.setText("Rev.");
        treeColumn3.setToolTipText("Revision currently installed");


        mColumnStatus = new TreeViewerColumn(mTreeViewer, SWT.NONE);
        TreeColumn treeColumn4 = mColumnStatus.getColumn();
        treeColumn4.setWidth(88);
        treeColumn4.setText("Status");

        mGroupOptions = new Composite(mGroupPackages, SWT.NONE);
        mGroupOptions.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        mGroupOptions.setLayout(new GridLayout(8, false));

        Label label2 = new Label(mGroupOptions, SWT.NONE);
        label2.setText("Sort by");

        mCheckSortSource = new Button(mGroupOptions, SWT.RADIO);
        mCheckSortSource.setToolTipText("Sort by Source");
        mCheckSortSource.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                sortPackages();
            }
        });
        mCheckSortSource.setImage(getImage("source_icon16.png"));  //$NON-NLS-1$
        mCheckSortSource.setText("Source");

        mCheckSortApi = new Button(mGroupOptions, SWT.RADIO);
        mCheckSortApi.setToolTipText("Sort by API level");
        mCheckSortApi.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                sortPackages();
            }
        });
        mCheckSortApi.setImage(getImage("platform_pkg_16.png"));  //$NON-NLS-1$
        mCheckSortApi.setText("API level");
        mCheckSortApi.setSelection(true);

        Label expandPlaceholder = new Label(mGroupOptions, SWT.NONE);
        expandPlaceholder.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

        Label label3 = new Label(mGroupOptions, SWT.NONE);
        label3.setText("Show:");

        mCheckFilterNew = new Button(mGroupOptions, SWT.CHECK);
        mCheckFilterNew.setToolTipText("Show Updates and New");
        mCheckFilterNew.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                sortPackages();
            }
        });
        mCheckFilterNew.setImage(getImage("reject_icon16.png"));
        mCheckFilterNew.setSelection(true);
        mCheckFilterNew.setText("Updates/New");

        mCheckFilterInstalled = new Button(mGroupOptions, SWT.CHECK);
        mCheckFilterInstalled.setToolTipText("Show Installed");
        mCheckFilterInstalled.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                sortPackages();
            }
        });
        mCheckFilterInstalled.setImage(getImage("accept_icon16.png"));  //$NON-NLS-1$
        mCheckFilterInstalled.setSelection(true);
        mCheckFilterInstalled.setText("Installed");

        mCheckFilterObsolete = new Button(mGroupOptions, SWT.CHECK);
        mCheckFilterObsolete.setToolTipText("Show everything including obsolete packages and all archives)");
        mCheckFilterObsolete.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                sortPackages();
            }
        });
        mCheckFilterObsolete.setImage(getImage("nopkg_icon16.png"));  //$NON-NLS-1$
        mCheckFilterObsolete.setSelection(false);
        mCheckFilterObsolete.setText("Details");

        mGroupButtons = new Composite(parent, SWT.NONE);
        mGroupButtons.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true,
                false, 1, 1));
        mGroupButtons.setLayout(new GridLayout(7, false));

        mButtonAddonSites = new Button(mGroupButtons, SWT.NONE);
        mButtonAddonSites.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onButtonAddonSites(e);
            }
        });
        mButtonAddonSites.setToolTipText("Manage the list of add-on sites");
        mButtonAddonSites.setText("Add-on Sites...");

        Label label6 = new Label(mGroupButtons, SWT.NONE);
        label6.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

        mButtonReload = new Button(mGroupButtons, SWT.NONE);
        mButtonReload.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onButtonReload(e);
            }
        });
        mButtonReload.setToolTipText("Reload the package list");
        mButtonReload.setText("Reload");

        Label label5 = new Label(mGroupButtons, SWT.NONE);
        label5.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

        mButtonDelete = new Button(mGroupButtons, SWT.NONE);
        mButtonDelete.setToolTipText("Delete an installed package");
        mButtonDelete.setText("Delete...");

        Label label4 = new Label(mGroupButtons, SWT.NONE);
        label4.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

        mButtonInstall = new Button(mGroupButtons, SWT.NONE);
        mButtonInstall.setToolTipText("Install all the selected packages");
        mButtonInstall.setText("Install Selected");
    }

    private Image getImage(String filename) {
        if (mUpdaterData != null) {
            ImageFactory imgFact = mUpdaterData.getImageFactory();
            if (imgFact != null) {
                imgFact.getImageByName(filename);
            }
        }
        return null;
    }


    // -- Start of internal part ----------
    // Hide everything down-below from SWT designer
    //$hide>>$

    private void postCreate() {
        if (mUpdaterData != null) {
            mTextSdkOsPath.setText(mUpdaterData.getOsSdkRoot());
        }

        mTreeViewer.setContentProvider(new PkgContentProvider());
        //--mTreeViewer.setLabelProvider(new PkgLabelProvider());


        mColumnApi.setLabelProvider(new TreeColumnViewerLabelProvider(new PkgCellLabelProvider(mColumnApi)));
        mColumnName.setLabelProvider(new TreeColumnViewerLabelProvider(new PkgCellLabelProvider(mColumnName)));
        mColumnStatus.setLabelProvider(new TreeColumnViewerLabelProvider(new PkgCellLabelProvider(mColumnStatus)));
        mColumnRevision.setLabelProvider(new TreeColumnViewerLabelProvider(new PkgCellLabelProvider(mColumnRevision)));

        FontData fontData = mTree.getFont().getFontData()[0];
        fontData.setStyle(SWT.ITALIC);
        mTreeFontItalic = new Font(mTree.getDisplay(), fontData);

        mColorUpdate = new Color(mTree.getDisplay(), 0xff, 0xff, 0xcc);
        mColorNew = new Color(mTree.getDisplay(), 0xff, 0xee, 0xcc);

        mTree.addDisposeListener(new DisposeListener() {
            public void widgetDisposed(DisposeEvent e) {
                mTreeFontItalic.dispose();
                mColorUpdate.dispose();
                mColorNew.dispose();
                mTreeFontItalic = null;
                mColorUpdate = null;
                mColorNew = null;
            }
        });
    }

    private void loadPackages() {
        if (mUpdaterData == null) {
            return;
        }

        mPackages.clear();

        // get local packages
        for (Package pkg : mUpdaterData.getInstalledPackages()) {
            PkgItem pi = new PkgItem(pkg, PkgState.INSTALLED);
            mPackages.add(pi);
        }

        // get remote packages
        final boolean forceHttp = mUpdaterData.getSettingsController().getForceHttp();
        mUpdaterData.loadRemoteAddonsList();
        mUpdaterData.getTaskFactory().start("Loading Source", new ITask() {
            public void run(ITaskMonitor monitor) {
                for (SdkSource source : mUpdaterData.getSources().getAllSources()) {
                    Package[] pkgs = source.getPackages();
                    if (pkgs == null) {
                        source.load(monitor, forceHttp);
                        pkgs = source.getPackages();
                    }
                    if (pkgs == null) {
                        continue;
                    }

                    nextPkg: for(Package pkg : pkgs) {
                        boolean isUpdate = false;
                        for (PkgItem pi: mPackages) {
                            if (pi.isSameAs(pkg)) {
                                continue nextPkg;
                            }
                            if (pi.isUpdatedBy(pkg)) {
                                isUpdate = true;
                                break;
                            }
                        }

                        if (!isUpdate) {
                            PkgItem pi = new PkgItem(pkg, PkgState.NEW_AVAILABLE);
                            mPackages.add(pi);
                        }
                    }
                }
            }
        });

        sortPackages();
    }

    private void sortPackages() {
        if (mCheckSortApi != null && !mCheckSortApi.isDisposed() && mCheckSortApi.getSelection()) {
            sortByAPI();
        } else {
            sortBySource();
        }
    }

    private void sortByAPI() {
        mCategories.clear();

        Set<Integer> apiSet = new HashSet<Integer>();
        for (PkgItem item : mPackages) {
            if (keepItem(item)) {
                apiSet.add(item.getApi());
            }
        }

        Integer[] apis = apiSet.toArray(new Integer[apiSet.size()]);
        Arrays.sort(apis, new Comparator<Integer>() {
            public int compare(Integer o1, Integer o2) {
                return o2.compareTo(o1);
            }
        });

        for (Integer api : apis) {
            String name = api == -1 ? "Other" : "API " + api.toString();
            Object iconRef = null;

            List<PkgItem> items = new ArrayList<PkgItem>();
            for (PkgItem item : mPackages) {
                if (item.getApi() == api) {
                    items.add(item);

                    if (api != -1) {
                        Package p = item.getPackage();
                        if (p instanceof PlatformPackage) {
                            String vn = ((PlatformPackage) p).getVersionName();
                            name = String.format("%1$s (Android %2$s)", name, vn);
                            iconRef = p;
                        }
                    }
                }
            }

            PkgCategory cat = new PkgCategory(
                    name,
                    iconRef,
                    items.toArray(new PkgItem[items.size()]));
            mCategories.add(cat);
        }

        mTreeViewer.setInput(mCategories);

        // expand all items by default
        expandItem(mCategories);
    }

    private void sortBySource() {
        mCategories.clear();

        Set<SdkSource> sourceSet = new HashSet<SdkSource>();
        for (PkgItem item : mPackages) {
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

            List<PkgItem> items = new ArrayList<PkgItem>();
            for (PkgItem item : mPackages) {
                if (item.getSource() == source) {
                    items.add(item);
                }
            }

            PkgCategory cat = new PkgCategory(
                    key,
                    key,
                    items.toArray(new PkgItem[items.size()]));
            mCategories.add(cat);
        }

        mTreeViewer.setInput(mCategories);

        // expand all items by default
        expandItem(mCategories);
    }

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
            if (item.getState() == PkgState.NEW_AVAILABLE ||
                    item.getState() == PkgState.UPDATE_AVAILABLE) {
                return false;
            }
        }

        return true;
    }

    private void expandItem(Object elem) {
        //if (elem instanceof SdkSource || elem instanceof SdkSourceCategory) {
            mTreeViewer.setExpandedState(elem, true);
            for (Object pkg :
                    ((ITreeContentProvider) mTreeViewer.getContentProvider()).getChildren(elem)) {
                mTreeViewer.setChecked(pkg, true);
                expandItem(pkg);
            }
        //}
    }

    // ----------------------

    public class PkgCellLabelProvider extends ColumnLabelProvider
        implements ITableFontProvider, ITableColorProvider {

        private final TreeViewerColumn mColumn;

        public PkgCellLabelProvider(TreeViewerColumn column) {
            super();
            mColumn = column;
        }

        @Override
        public String getText(Object element) {
            if (element instanceof PkgCategory) {
                if (mColumn == mColumnName) {
                    return ((PkgCategory) element).getLabel();
                }
            } else if (element instanceof PkgItem) {
                PkgItem pkg = (PkgItem) element;

                if (mColumn == mColumnName) {
                    return pkg.getName();

                } else if (mColumn == mColumnApi) {
                    int api = pkg.getApi();
                    if (api < 1) {
                        return "";  //$NON-NLS-1$
                    } else {
                        return Integer.toString(api);
                    }
                } else if (mColumn == mColumnRevision) {
                    if (pkg.getState() == PkgState.INSTALLED ||
                            pkg.getState() == PkgState.UPDATE_AVAILABLE) {
                        return Integer.toString(pkg.getRevision());
                    }

                } else if (mColumn == mColumnStatus) {
                    switch(pkg.getState()) {
                    case INSTALLED:
                        return "Installed";
                    case UPDATE_AVAILABLE:
                        return "Rev. " + Integer.toString(pkg.getUpdateRev());
                    case NEW_AVAILABLE:
                        return "New rev. " + Integer.toString(pkg.getRevision());
                    case LOCKED_NO_INSTALL:
                        return "Locked";
                    }
                    return pkg.getState().toString();
                }
            } else if (element instanceof Package) {
                return ((Package) element).getShortDescription();
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
                    }
                    return imgFactory.getImageForObject(element);

                } else if (mColumn == mColumnStatus && element instanceof PkgItem) {
                    switch(((PkgItem) element).getState()) {
                    case INSTALLED:
                        return imgFactory.getImageByName("accept_icon16.png");
                    case UPDATE_AVAILABLE:
                    case NEW_AVAILABLE:
                        return imgFactory.getImageByName("reject_icon16.png");
                    case LOCKED_NO_INSTALL:
                        return imgFactory.getImageByName("broken_pkg_16.png");
                    }
                }
            }
            return super.getImage(element);
        }

        // -- ITableFontProvider

        public Font getFont(Object element, int columnIndex) {
            if (element instanceof PkgItem) {
                if (((PkgItem) element).getState() != PkgState.INSTALLED) {
                    return mTreeFontItalic;
                }
            }
            return super.getFont(element);
        }

        // -- ITableColorProvider

        public Color getBackground(Object element, int columnIndex) {
            if (element instanceof PkgItem) {
                if (((PkgItem) element).getState() == PkgState.NEW_AVAILABLE) {
                    return mColorNew;
                } else if (((PkgItem) element).getState() == PkgState.UPDATE_AVAILABLE) {
                        return mColorUpdate;
                }
            }
            return null;
        }

        public Color getForeground(Object element, int columnIndex) {
            // Not used
            return null;
        }
    }

    public class PkgLabelProvider extends LabelProvider {

        @Override
        public String getText(Object element) {
            return super.getText(element);
        }

        @Override
        public Image getImage(Object element) {
            ImageFactory imgFactory = mUpdaterData.getImageFactory();

            if (imgFactory != null) {
                return imgFactory.getImageForObject(element);
            }
            return super.getImage(element);
        }

    }

    public static class PkgContentProvider implements ITreeContentProvider {

        public Object[] getChildren(Object parentElement) {

            if (parentElement instanceof ArrayList<?>) {
                return ((ArrayList<?>) parentElement).toArray();

            } else if (parentElement instanceof PkgCategory) {
                return ((PkgCategory) parentElement).getItems();

            } else if (parentElement instanceof PkgItem) {
                List<Package> pkgs = ((PkgItem) parentElement).getUpdatePkgs();
                if (pkgs != null) {
                    return pkgs.toArray();
                }
            }

            return new Object[0];
        }

        public Object getParent(Object element) {
            // TODO Auto-generated method stub
            return null;
        }

        public boolean hasChildren(Object parentElement) {
            if (parentElement instanceof ArrayList<?>) {
                return true;

            } else if (parentElement instanceof PkgCategory) {
                return true;

            } else if (parentElement instanceof PkgItem) {
                List<Package> pkgs = ((PkgItem) parentElement).getUpdatePkgs();
                if (pkgs != null) {
                    return !pkgs.isEmpty();
                }
            }

            return false;
        }

        public Object[] getElements(Object inputElement) {
            return getChildren(inputElement);
        }

        public void dispose() {
            // TODO Auto-generated method stub

        }

        public void inputChanged(Viewer arg0, Object arg1, Object arg2) {
            // TODO Auto-generated method stub

        }

    }

    public static class PkgCategory {
        private final Object mKey;
        private final PkgItem[] mItems;
        private final Object mIconRef;

        public PkgCategory(Object key, Object iconRef, PkgItem[] items) {
            mKey = key;
            mIconRef = iconRef;
            mItems = items;
        }

        public String getLabel() {
            return mKey.toString();
        }

        public Object getIconRef() {
            return mIconRef;
        }

        public PkgItem[] getItems() {
            return mItems;
        }
    }

    public enum PkgState {
        INSTALLED, UPDATE_AVAILABLE, NEW_AVAILABLE, LOCKED_NO_INSTALL
    }

    public static class PkgItem {
        private final Package mPkg;
        private PkgState mState;
        private int mUpdateRev;
        private List<Package> mUpdatePkgs;

        public PkgItem(Package pkg, PkgState state) {
            mPkg = pkg;
            mState = state;
        }

        public boolean isObsolete() {
            return mPkg.isObsolete();
        }

        public boolean isSameAs(Package pkg) {
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
                mState = PkgState.UPDATE_AVAILABLE;
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
            if (mState == PkgState.NEW_AVAILABLE) {
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

        public int getUpdateRev() {
            return mUpdateRev;
        }

        public List<Package> getUpdatePkgs() {
            return mUpdatePkgs;
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

    protected void onButtonReload(SelectionEvent e) {
        loadPackages();
    }

    protected void onButtonAddonSites(SelectionEvent e) {
        AddonSitesDialog d = new AddonSitesDialog(getShell(), mUpdaterData);
        if (d.open()) {
            loadPackages();
        }
    }
}
