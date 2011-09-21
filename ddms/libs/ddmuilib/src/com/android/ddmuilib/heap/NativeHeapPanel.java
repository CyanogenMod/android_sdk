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

package com.android.ddmuilib.heap;

import com.android.ddmlib.Client;
import com.android.ddmlib.Log;
import com.android.ddmlib.NativeAllocationInfo;
import com.android.ddmlib.NativeLibraryMapInfo;
import com.android.ddmlib.NativeStackCallInfo;
import com.android.ddmuilib.Addr2Line;
import com.android.ddmuilib.BaseHeapPanel;
import com.android.ddmuilib.ImageLoader;
import com.android.ddmuilib.TableHelper;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Sash;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Panel to display native heap information. */
public class NativeHeapPanel extends BaseHeapPanel {
    private static final String TOOLTIP_EXPORT_DATA = "Export Heap Data";
    private static final String TOOLTIP_ZYGOTE_ALLOCATIONS = "Show Zygote Allocations";
    private static final String TOOLTIP_DIFFS_ONLY = "Only show differences from previous snapshot";
    private static final String TOOLTIP_GROUPBY = "Group allocations by library.";

    private static final String EXPORT_DATA_IMAGE = "save.png";
    private static final String ZYGOTE_IMAGE = "zygote.png";
    private static final String DIFFS_ONLY_IMAGE = "diff.png";
    private static final String GROUPBY_IMAGE = "groupby.png";

    private static final String SNAPSHOT_HEAP_BUTTON_TEXT = "Snapshot Current Native Heap Usage";
    private static final String SYMBOL_SEARCH_PATH_LABEL_TEXT = "Symbol Search Path:";
    private static final String SYMBOL_SEARCH_PATH_TEXT_MESSAGE =
            "List of colon separated paths to search for symbol debug information. See tooltip for examples.";
    private static final String SYMBOL_SEARCH_PATH_TOOLTIP_TEXT =
            "Colon separated paths that contain unstripped libraries with debug symbols.\n"
                    + "e.g.: <android-src>/out/target/product/generic/symbols/system/lib:/path/to/my/app/obj/local/armeabi";

    private static final NumberFormat NUMBER_FORMATTER = NumberFormat.getInstance();

    private static final String PREFS_SYMBOL_SEARCH_PATH = "nativeheap.search.path";
    private static final String PREFS_SASH_HEIGHT_PERCENT = "nativeheap.sash.percent";
    private IPreferenceStore mPrefStore;

    private List<List<NativeAllocationInfo>> mNativeHeapAllocations;

    private Button mSnapshotHeapButton;
    private Text mSymbolSearchPathText;
    private Combo mSnapshotIndexCombo;
    private Label mMemoryAllocatedText;

    private TreeViewer mDetailsTreeViewer;
    private TreeViewer mStackTraceTreeViewer;

    private ToolBar mDetailsToolBar;
    private ToolItem mGroupByButton;
    private ToolItem mDiffsOnlyButton;
    private ToolItem mShowZygoteAllocationsButton;
    private ToolItem mExportHeapDataButton;

    public NativeHeapPanel(IPreferenceStore prefStore) {
        mPrefStore = prefStore;
        mPrefStore.setDefault(PREFS_SASH_HEIGHT_PERCENT, 75);
        mPrefStore.setDefault(PREFS_SYMBOL_SEARCH_PATH, "");

        mNativeHeapAllocations = new ArrayList<List<NativeAllocationInfo>>();
    }

    /** {@inheritDoc} */
    public void clientChanged(Client client, int changeMask) {
        if (client != getCurrentClient()) {
            return;
        }

        if ((changeMask & Client.CHANGE_NATIVE_HEAP_DATA) != Client.CHANGE_NATIVE_HEAP_DATA) {
            return;
        }

        List<NativeAllocationInfo> allocations = client.getClientData().getNativeAllocationList();
        if (allocations.size() == 0) {
            return;
        }

        // We need to clone this list since getClientData().getNativeAllocationList() clobbers
        // the list on future updates
        allocations = shallowCloneList(allocations);

        mNativeHeapAllocations.add(allocations);
        updateDisplay();

        // Attempt to resolve symbols in a separate thread.
        // The UI should be refreshed once the symbols have been resolved.
        Thread t = new Thread(new SymbolResolverTask(allocations,
                client.getClientData().getMappedNativeLibraries()));
        t.setName("Address to Symbol Resolver");
        t.start();
    }

    private List<NativeAllocationInfo> shallowCloneList(List<NativeAllocationInfo> allocations) {
        List<NativeAllocationInfo> clonedList =
                new ArrayList<NativeAllocationInfo>(allocations.size());

        for (NativeAllocationInfo i : allocations) {
            clonedList.add(i);
        }

        return clonedList;
    }

    @Override
    public void deviceSelected() {
        // pass
    }

    @Override
    public void clientSelected() {
        Client c = getCurrentClient();

        mSnapshotHeapButton.setEnabled(c != null);

        mNativeHeapAllocations = new ArrayList<List<NativeAllocationInfo>>();
        if (c != null) {
            List<NativeAllocationInfo> allocations = c.getClientData().getNativeAllocationList();
            if (allocations.size() > 0) {
                mNativeHeapAllocations.add(allocations);
            }
        }

        updateDisplay();
    }

    private void updateDisplay() {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                updateSnapshotIndexCombo();

                List<NativeAllocationInfo> lastHeapSnapshot = null;
                if (mNativeHeapAllocations.size() > 0) {
                    lastHeapSnapshot =
                            mNativeHeapAllocations.get(mNativeHeapAllocations.size() - 1);
                }

                displaySnapshot(lastHeapSnapshot);
                displayStackTraceForSelection();
            }
        });
    }

    private void displaySelectedSnapshot() {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                int idx = mSnapshotIndexCombo.getSelectionIndex();
                displaySnapshot(mNativeHeapAllocations.get(idx));
            }
        });
    }

    private void displaySnapshot(List<NativeAllocationInfo> heapSnapshot) {
        mDetailsTreeViewer.setInput(heapSnapshot);

        if (heapSnapshot != null) {
            mMemoryAllocatedText.setText(formatMemorySize(getTotalMemory(heapSnapshot)));
            mMemoryAllocatedText.pack();
        } else {
            mMemoryAllocatedText.setText("");
        }
    }

    private String formatMemorySize(long totalMemory) {
        return NUMBER_FORMATTER.format(totalMemory) + " bytes";
    }

    private long getTotalMemory(List<NativeAllocationInfo> heapSnapshot) {
        long total = 0;

        for (NativeAllocationInfo info : heapSnapshot) {
            total += info.getAllocationCount() * info.getSize();
        }

        return total;
    }

    private void updateSnapshotIndexCombo() {
        List<String> items = new ArrayList<String>();

        int numSnapshots = mNativeHeapAllocations.size();
        for (int i = 0; i < numSnapshots; i++) {
            // offset indices by 1 so that users see index starting at 1 rather than 0
            items.add("Snapshot " + (i + 1));
        }

        mSnapshotIndexCombo.setItems(items.toArray(new String[0]));

        if (numSnapshots > 0) {
            mSnapshotIndexCombo.setEnabled(true);
            mSnapshotIndexCombo.select(numSnapshots - 1);
        } else {
            mSnapshotIndexCombo.setEnabled(false);
        }
    }

    @Override
    protected Control createControl(Composite parent) {
        parent.setLayout(new GridLayout(1, false));

        Composite c = new Composite(parent, SWT.NONE);
        c.setLayout(new GridLayout(1, false));
        c.setLayoutData(new GridData(GridData.FILL_BOTH));

        createControlsSection(c);
        createDetailsSection(c);

        // Initialize widget state based on whether a client
        // is selected or not.
        clientSelected();

        return c;
    }

    private void createControlsSection(Composite parent) {
        Composite c = new Composite(parent, SWT.NONE);
        c.setLayout(new GridLayout(3, false));
        c.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        createGetHeapDataButton(c);

        Label l = new Label(c, SWT.SEPARATOR | SWT.VERTICAL);
        l.setLayoutData(new GridData(GridData.FILL_VERTICAL));

        createDisplaySection(c);
    }

    private void createGetHeapDataButton(Composite parent) {
        mSnapshotHeapButton = new Button(parent, SWT.BORDER | SWT.PUSH);
        mSnapshotHeapButton.setText(SNAPSHOT_HEAP_BUTTON_TEXT);
        mSnapshotHeapButton.setLayoutData(new GridData());

        // disable by default, enabled only when a client is selected
        mSnapshotHeapButton.setEnabled(false);

        mSnapshotHeapButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent evt) {
                snapshotHeap();
            }
        });
    }

    private void snapshotHeap() {
        Client c = getCurrentClient();
        assert c != null : "Snapshot Heap could not have been enabled w/o a selected client.";

        // send an async request
        c.requestNativeHeapInformation();
    }

    private void createDisplaySection(Composite parent) {
        Composite c = new Composite(parent, SWT.NONE);
        c.setLayout(new GridLayout(2, false));
        c.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        // Create: Display: __________________
        createLabel(c, "Display:");
        mSnapshotIndexCombo = new Combo(c, SWT.NONE | SWT.READ_ONLY);
        mSnapshotIndexCombo.setItems(new String[] {"No heap snapshots available."});
        mSnapshotIndexCombo.setEnabled(false);
        mSnapshotIndexCombo.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent arg0) {
                displaySelectedSnapshot();
            }
        });

        // Create: Memory Allocated (bytes): _________________
        createLabel(c, "Memory Allocated:");
        mMemoryAllocatedText = new Label(c, SWT.NONE);
        GridData gd = new GridData();
        gd.widthHint = 100;
        mMemoryAllocatedText.setLayoutData(gd);

        // Create: Search Path: __________________
        createLabel(c, SYMBOL_SEARCH_PATH_LABEL_TEXT);
        mSymbolSearchPathText = new Text(c, SWT.BORDER);
        mSymbolSearchPathText.setMessage(SYMBOL_SEARCH_PATH_TEXT_MESSAGE);
        mSymbolSearchPathText.setToolTipText(SYMBOL_SEARCH_PATH_TOOLTIP_TEXT);
        mSymbolSearchPathText.addModifyListener(new ModifyListener() {
            public void modifyText(ModifyEvent arg0) {
                String path = mSymbolSearchPathText.getText();
                updateSearchPath(path);
                mPrefStore.setValue(PREFS_SYMBOL_SEARCH_PATH, path);
            }
        });
        mSymbolSearchPathText.setText(mPrefStore.getString(PREFS_SYMBOL_SEARCH_PATH));
    }

    private void updateSearchPath(String path) {
        Addr2Line.setSearchPath(path);
    }

    private void createLabel(Composite parent, String text) {
        Label l = new Label(parent, SWT.NONE);
        l.setText(text);
        GridData gd = new GridData();
        gd.horizontalAlignment = SWT.RIGHT;
        l.setLayoutData(gd);
    }

    /**
     * Create the details section displaying the details table and the stack trace
     * corresponding to the selection.
     *
     * The details is laid out like so:
     *   Details Toolbar
     *   Details Table
     *   ------------sash---
     *   Stack Trace Label
     *   Stack Trace Text
     * There is a sash in between the two sections, and we need to save/restore the sash
     * preferences. Using FormLayout seems like the easiest solution here, but the layout
     * code looks ugly as a result.
     */
    private void createDetailsSection(Composite parent) {
        final Composite c = new Composite(parent, SWT.NONE);
        c.setLayout(new FormLayout());
        c.setLayoutData(new GridData(GridData.FILL_BOTH));

        mDetailsToolBar = new ToolBar(c, SWT.FLAT | SWT.BORDER);
        initializeDetailsToolBar(mDetailsToolBar);

        Tree detailsTree = new Tree(c, SWT.VIRTUAL | SWT.BORDER);
        initializeDetailsTree(detailsTree);

        final Sash sash = new Sash(c, SWT.HORIZONTAL | SWT.BORDER);

        Label stackTraceLabel = new Label(c, SWT.NONE);
        stackTraceLabel.setText("Stack Trace:");

        Tree stackTraceTree = new Tree(c, SWT.BORDER);
        initializeStackTraceTree(stackTraceTree);

        // layout the widgets created above
        FormData data = new FormData();
        data.top    = new FormAttachment(0, 0);
        data.left   = new FormAttachment(0, 0);
        data.right  = new FormAttachment(100, 0);
        mDetailsToolBar.setLayoutData(data);

        data = new FormData();
        data.top    = new FormAttachment(mDetailsToolBar, 0);
        data.bottom = new FormAttachment(sash, 0);
        data.left   = new FormAttachment(0, 0);
        data.right  = new FormAttachment(100, 0);
        detailsTree.setLayoutData(data);

        final FormData sashData = new FormData();
        sashData.top    = new FormAttachment(mPrefStore.getInt(PREFS_SASH_HEIGHT_PERCENT), 0);
        sashData.left   = new FormAttachment(0, 0);
        sashData.right  = new FormAttachment(100, 0);
        sash.setLayoutData(sashData);

        data = new FormData();
        data.top    = new FormAttachment(sash, 0);
        data.left   = new FormAttachment(0, 0);
        data.right  = new FormAttachment(100, 0);
        stackTraceLabel.setLayoutData(data);

        data = new FormData();
        data.top    = new FormAttachment(stackTraceLabel, 0);
        data.left   = new FormAttachment(0, 0);
        data.bottom = new FormAttachment(100, 0);
        data.right  = new FormAttachment(100, 0);
        stackTraceTree.setLayoutData(data);

        sash.addListener(SWT.Selection, new Listener() {
            public void handleEvent(Event e) {
                Rectangle sashRect = sash.getBounds();
                Rectangle panelRect = c.getClientArea();
                int sashPercent = sashRect.y * 100 / panelRect.height;
                mPrefStore.setValue(PREFS_SASH_HEIGHT_PERCENT, sashPercent);

                sashData.top = new FormAttachment(0, e.y);
                c.layout();
            }
        });
    }

    private void initializeDetailsToolBar(ToolBar toolbar) {
        mGroupByButton = new ToolItem(toolbar, SWT.CHECK);
        mGroupByButton.setImage(ImageLoader.getDdmUiLibLoader().loadImage(GROUPBY_IMAGE,
                toolbar.getDisplay()));
        mGroupByButton.setToolTipText(TOOLTIP_GROUPBY);

        mDiffsOnlyButton = new ToolItem(toolbar, SWT.CHECK);
        mDiffsOnlyButton.setImage(ImageLoader.getDdmUiLibLoader().loadImage(DIFFS_ONLY_IMAGE,
                toolbar.getDisplay()));
        mDiffsOnlyButton.setToolTipText(TOOLTIP_DIFFS_ONLY);

        mShowZygoteAllocationsButton = new ToolItem(toolbar, SWT.CHECK);
        mShowZygoteAllocationsButton.setImage(ImageLoader.getDdmUiLibLoader().loadImage(
                ZYGOTE_IMAGE, toolbar.getDisplay()));
        mShowZygoteAllocationsButton.setToolTipText(TOOLTIP_ZYGOTE_ALLOCATIONS);
        mShowZygoteAllocationsButton.setSelection(true);

        mExportHeapDataButton = new ToolItem(toolbar, SWT.PUSH);
        mExportHeapDataButton.setImage(ImageLoader.getDdmUiLibLoader().loadImage(
                EXPORT_DATA_IMAGE, toolbar.getDisplay()));
        mExportHeapDataButton.setToolTipText(TOOLTIP_EXPORT_DATA);

        // disable all toolbar items until they implement the necessary features
        mGroupByButton.setEnabled(false);
        mDiffsOnlyButton.setEnabled(false);
        mShowZygoteAllocationsButton.setEnabled(false);
        mExportHeapDataButton.setEnabled(false);
    }

    private void initializeDetailsTree(Tree tree) {
        tree.setHeaderVisible(true);
        tree.setLinesVisible(true);

        List<String> properties = Arrays.asList(new String[] {
                "Total",
                "Count",
                "Size",
                "Library",
                "Method",
        });

        List<String> sampleValues = Arrays.asList(new String[] {
                "123456789",
                "123456789",
                "123456789",
                "/path/in/device/to/system/library.so",
                "PossiblyLongDemangledMethodName",
        });

        // right align numbers
        List<Integer> swtFlags = Arrays.asList(new Integer[] {
                SWT.RIGHT, // Note: On some platforms, the first column is *always* left aligned.
                SWT.RIGHT,
                SWT.RIGHT,
                SWT.LEFT,
                SWT.LEFT,
        });

        for (int i = 0; i < properties.size(); i++) {
            String p = properties.get(i);
            String v = sampleValues.get(i);
            int flags = swtFlags.get(i);
            TableHelper.createTreeColumn(tree, p, flags, v, getPref("details", p), mPrefStore);
        }

        mDetailsTreeViewer = new TreeViewer(tree);

        mDetailsTreeViewer.setUseHashlookup(true);
        mDetailsTreeViewer.setContentProvider(new NativeHeapContentProvider(mDetailsTreeViewer));
        mDetailsTreeViewer.setLabelProvider(new NativeHeapLabelProvider());

        mDetailsTreeViewer.setInput(null);

        tree.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent event) {
                displayStackTraceForSelection();
            }
        });
    }

    private void initializeStackTraceTree(Tree tree) {
        tree.setHeaderVisible(true);
        tree.setLinesVisible(true);

        List<String> properties = Arrays.asList(new String[] {
                "Address",
                "Library",
                "Method",
                "File",
                "Line",
        });

        List<String> sampleValues = Arrays.asList(new String[] {
                "0x1234_5678",
                "/path/in/device/to/system/library.so",
                "PossiblyLongDemangledMethodName",
                "/android/out/prefix/in/home/directory/to/path/in/device/to/system/library.so",
                "2000",
        });

        for (int i = 0; i < properties.size(); i++) {
            String p = properties.get(i);
            String v = sampleValues.get(i);
            TableHelper.createTreeColumn(tree, p, SWT.LEFT, v, getPref("stack", p), mPrefStore);
        }

        mStackTraceTreeViewer = new TreeViewer(tree);

        mStackTraceTreeViewer.setContentProvider(new NativeStackContentProvider());
        mStackTraceTreeViewer.setLabelProvider(new NativeStackLabelProvider());

        mStackTraceTreeViewer.setInput(null);
    }

    private void displayStackTraceForSelection() {
        TreeItem []items = mDetailsTreeViewer.getTree().getSelection();
        if (items.length == 0) {
            mStackTraceTreeViewer.setInput(null);
            return;
        }

        Object data = items[0].getData();
        if (!(data instanceof NativeAllocationInfo)) {
            mStackTraceTreeViewer.setInput(null);
            return;
        }

        NativeAllocationInfo info = (NativeAllocationInfo) data;
        if (info.isStackCallResolved()) {
            mStackTraceTreeViewer.setInput(info.getResolvedStackCall());
        } else {
            mStackTraceTreeViewer.setInput(info.getStackCallAddresses());
        }
    }

    private String getPref(String prefix, String s) {
        return "nativeheap.tree." + prefix + "." + s;
    }

    @Override
    public void setFocus() {
        // TODO
    }

    private class SymbolResolverTask implements Runnable {
        private List<NativeAllocationInfo> mCallSites;
        private List<NativeLibraryMapInfo> mMappedLibraries;
        private Map<Long, NativeStackCallInfo> mResolvedSymbolCache;

        public SymbolResolverTask(List<NativeAllocationInfo> callSites,
                List<NativeLibraryMapInfo> mappedLibraries) {
            mCallSites = callSites;
            mMappedLibraries = mappedLibraries;

            mResolvedSymbolCache = new HashMap<Long, NativeStackCallInfo>();
        }

        public void run() {
            for (NativeAllocationInfo callSite: mCallSites) {
                if (callSite.isStackCallResolved()) {
                    continue;
                }

                List<Long> addresses = callSite.getStackCallAddresses();
                List<NativeStackCallInfo> resolvedStackInfo =
                        new ArrayList<NativeStackCallInfo>(addresses.size());

                for (Long address : addresses) {
                    NativeStackCallInfo info = mResolvedSymbolCache.get(address);

                    if (info != null) {
                        resolvedStackInfo.add(info);
                    } else {
                        info = resolveAddress(address);
                        resolvedStackInfo.add(info);
                        mResolvedSymbolCache.put(address, info);
                    }
                }

                callSite.setResolvedStackCall(resolvedStackInfo);
            }

            Display.getDefault().asyncExec(new Runnable() {
                public void run() {
                    mDetailsTreeViewer.refresh();
                    mStackTraceTreeViewer.refresh();
                }
            });
        }

        private NativeStackCallInfo resolveAddress(long addr) {
            NativeLibraryMapInfo library = getLibraryFor(addr);

            if (library != null) {
                Addr2Line process = Addr2Line.getProcess(library.getLibraryName());
                if (process != null) {
                    long offset = addr - library.getStartAddress();
                    NativeStackCallInfo info = process.getAddress(offset);
                    if (info != null) {
                        return info;
                    }
                }
            }

            return new NativeStackCallInfo(addr,
                    library != null ? library.getLibraryName() : null,
                    Long.toHexString(addr),
                    "");
        }

        private NativeLibraryMapInfo getLibraryFor(long addr) {
            for (NativeLibraryMapInfo info : mMappedLibraries) {
                if (info.isWithinLibrary(addr)) {
                    return info;
                }
            }

            Log.d("ddm-nativeheap", "Failed finding Library for " + Long.toHexString(addr));
            return null;
        }
    }
}
