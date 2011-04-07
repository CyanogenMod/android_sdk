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
import com.android.sdklib.internal.repository.IPackageVersion;
import com.android.sdklib.internal.repository.ITask;
import com.android.sdklib.internal.repository.ITaskMonitor;
import com.android.sdklib.internal.repository.Package;
import com.android.sdklib.internal.repository.PlatformPackage;
import com.android.sdklib.internal.repository.SdkSource;
import com.android.sdklib.internal.repository.Package.UpdateInfo;
import com.android.sdkuilib.internal.repository.icons.ImageFactory;
import com.android.sdkuilib.repository.ISdkChangeListener;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.ITableColorProvider;
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
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;

import java.io.File;
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
    private Button mCheckFilterDetails;
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
    private Color mColorUpdate;
    private Color mColorNew;
    private Font mTreeFontItalic;
    private Button mButtonReload;

    public PackagesPage(Composite parent, UpdaterData updaterData) {
        super(parent, SWT.NONE);

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

        mColumnName = new TreeViewerColumn(mTreeViewer, SWT.NONE);
        TreeColumn treeColumn1 = mColumnName.getColumn();
        treeColumn1.setImage(getImage("platform_pkg_16.png"));      //$NON-NLS-1$
        treeColumn1.setWidth(340);
        treeColumn1.setText("Name");

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
        GridLayout gl_GroupOptions = new GridLayout(7, false);
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
                sortPackages();
            }
        });
        mCheckFilterNew.setImage(getImage("reject_icon16.png"));        //$NON-NLS-1$
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

        mCheckFilterDetails = new Button(mGroupOptions, SWT.CHECK);
        mCheckFilterDetails.setToolTipText("Show everything including obsolete packages and all archives)");
        mCheckFilterDetails.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                sortPackages();
            }
        });
        mCheckFilterDetails.setImage(getImage("nopkg_icon16.png"));  //$NON-NLS-1$
        mCheckFilterDetails.setSelection(false);
        mCheckFilterDetails.setText("Details");

        mButtonReload = new Button(mGroupOptions, SWT.NONE);
        mButtonReload.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        mButtonReload.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onButtonReload();
            }
        });
        mButtonReload.setToolTipText("Reload the package list");
        mButtonReload.setText("Reload");

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
                sortPackages();
            }
        });
        mCheckSortApi.setImage(getImage("platform_pkg_16.png"));  //$NON-NLS-1$
        mCheckSortApi.setText("API level");
        mCheckSortApi.setSelection(true);

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

        Link link = new Link(mGroupOptions, SWT.NONE);
        link.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onButtonAddonSites();
            }
        });
        link.setToolTipText("Manage the list of add-on sites");
        link.setText("<a>Manage Sources</a>");

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

        try {
            enableUi(mGroupPackages, false);

            mPackages.clear();

            // get local packages
            for (Package pkg : mUpdaterData.getInstalledPackages()) {
                PkgItem pi = new PkgItem(pkg, PkgState.INSTALLED);
                mPackages.add(pi);
            }

            // get remote packages
            final boolean forceHttp = mUpdaterData.getSettingsController().getForceHttp();
            mUpdaterData.loadRemoteAddonsList();
            mUpdaterData.getTaskFactory().start("Loading Sources", new ITask() {
                public void run(ITaskMonitor monitor) {
                    SdkSource[] sources = mUpdaterData.getSources().getAllSources();
                    for (SdkSource source : sources) {
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

                    monitor.setDescription("Done loading %1$d packages from %2$d sources",
                            mPackages.size(),
                            sources.length);
                }
            });

            sortPackages();
        } finally {
            enableUi(mGroupPackages, true);
            updateButtonsState();
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

    private void sortPackages() {
        if (mCheckSortApi != null && !mCheckSortApi.isDisposed() && mCheckSortApi.getSelection()) {
            sortByAPI();
        } else {
            sortBySource();
        }
        updateButtonsState();
    }

    /**
     * Recompute the tree by sorting all the packages by API.
     */
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

        ImageFactory imgFactory = mUpdaterData.getImageFactory();

        for (Integer api : apis) {
            String name = api > 0 ? "API " + api.toString() : "Other";
            Object iconRef = imgFactory.getImageByName(
                    api > 0 ? "pkgcat_16.png" :"pkgcat_other_16.png");  //$NON-NLS-1$ //$NON-NLS-2$

            List<PkgItem> items = new ArrayList<PkgItem>();
            for (PkgItem item : mPackages) {
                if (item.getApi() == api) {
                    items.add(item);

                    if (api != -1) {
                        Package p = item.getPackage();
                        if (p instanceof PlatformPackage) {
                            String vn = ((PlatformPackage) p).getVersionName();
                            name = String.format("%1$s (Android %2$s)", name, vn);
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
        expandInitial(mCategories);
    }

    /**
     * Recompute the tree by sorting all packages by source.
     */
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
            Object iconRef = source != null ? source :
                        mUpdaterData.getImageFactory().getImageByName(
                                "pkg_installed_16.png"); //$NON-NLS-1$

            List<PkgItem> items = new ArrayList<PkgItem>();
            for (PkgItem item : mPackages) {
                if (item.getSource() == source) {
                    items.add(item);
                }
            }

            PkgCategory cat = new PkgCategory(
                    key,
                    iconRef,
                    items.toArray(new PkgItem[items.size()]));
            mCategories.add(cat);
        }

        mTreeViewer.setInput(mCategories);

        // expand all items by default
        expandInitial(mCategories);
    }

    /**
     * Decide whether to keep an item in the current tree based on user-choosen filter options.
     */
    private boolean keepItem(PkgItem item) {
        if (!mCheckFilterDetails.getSelection()) {
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
                            || item.getState() == PkgState.UPDATE_AVAILABLE) {
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

        if (mCheckFilterDetails.getSelection()) {
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

    protected void onButtonReload() {
        loadPackages();
    }

    protected void onButtonAddonSites() {
        AddonSitesDialog d = new AddonSitesDialog(getShell(), mUpdaterData);
        if (d.open()) {
            loadPackages();
        }
    }

    protected void onButtonInstall() {
        ArrayList<Archive> archives = new ArrayList<Archive>();

        boolean showDetails = mCheckFilterDetails.getSelection();
        if (showDetails) {
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
            // found in the selected pkg items or update packages.

            Object[] checked = mTreeViewer.getCheckedElements();
            if (checked != null) {
                for (Object c : checked) {
                    Package p = null;
                    if (c instanceof Package) {
                        // This is an update package
                        p = (Package) c;
                    } else if (c instanceof PkgItem) {
                        p = ((PkgItem) c).getPackage();
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
                    showDetails /* includeObsoletes */);
            } finally {
                // loadPackages will also re-enable the UI
                loadPackages();
            }
        }
    }

    protected void onButtonDelete() {
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

    public class PkgCellLabelProvider extends ColumnLabelProvider
        implements ITableFontProvider, ITableColorProvider {

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
                            pkg.getState() == PkgState.UPDATE_AVAILABLE) {
                        return Integer.toString(pkg.getRevision());
                    }
                }

            } else if (mColumn == mColumnStatus) {

                if (element instanceof PkgItem) {
                    PkgItem pkg = (PkgItem) element;

                    switch(pkg.getState()) {
                    case INSTALLED:
                        return "Installed";
                    case UPDATE_AVAILABLE:
                        return "Update available";
                    case NEW_AVAILABLE:
                        return "Not installed. New revision " + Integer.toString(pkg.getRevision());
                    case LOCKED_NO_INSTALL:
                        return "Locked";
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
                        return imgFactory.getImageByName("pkg_installed_16.png");   //$NON-NLS-1$
                    case UPDATE_AVAILABLE:
                        return imgFactory.getImageByName("pkg_update_16.png");      //$NON-NLS-1$
                    case NEW_AVAILABLE:
                        return imgFactory.getImageByName("pkg_new_16.png");         //$NON-NLS-1$
                    case LOCKED_NO_INSTALL:
                        return imgFactory.getImageByName("broken_pkg_16.png");      //$NON-NLS-1$
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
            } else if (element instanceof Package) {
                // update package
                return mTreeFontItalic;
            }
            return super.getFont(element);
        }

        // -- ITableColorProvider

        public Color getBackground(Object element, int columnIndex) {
            if (element instanceof PkgItem) {
                if (((PkgItem) element).getState() == PkgState.NEW_AVAILABLE) {
                    // Disabled. Current color scheme is unpretty.
                    // return mColorNew;
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

    private class PkgContentProvider implements ITreeContentProvider {

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

                if (mCheckFilterDetails.getSelection()) {
                    return ((PkgItem) parentElement).getArchives();
                }

            } else if (parentElement instanceof Package) {
                if (mCheckFilterDetails.getSelection()) {
                    return ((Package) parentElement).getArchives();
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

                if (mCheckFilterDetails.getSelection()) {
                    Archive[] archives = ((PkgItem) parentElement).getArchives();
                    return archives.length > 0;
                }
            } else if (parentElement instanceof Package) {
                if (mCheckFilterDetails.getSelection()) {
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

        public List<Package> getUpdatePkgs() {
            return mUpdatePkgs;
        }

        public Archive[] getArchives() {
            return mPkg.getArchives();
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
