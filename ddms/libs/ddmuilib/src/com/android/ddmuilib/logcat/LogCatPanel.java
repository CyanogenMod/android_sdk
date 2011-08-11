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

package com.android.ddmuilib.logcat;

import com.android.ddmlib.Log.LogLevel;
import com.android.ddmuilib.ImageLoader;
import com.android.ddmuilib.SelectionDependentPanel;
import com.android.ddmuilib.TableHelper;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

import java.util.ArrayList;
import java.util.List;

/**
 * LogCatPanel displays a table listing the logcat messages.
 */
public final class LogCatPanel extends SelectionDependentPanel
                        implements ILogCatMessageEventListener {
    /** Width (in characters) at which to wrap messages. SWT Tables do not
     * auto wrap long text - they simply clip the text.
     * FIXME: this should be a preference. */
    private static final int MSG_WRAP_WIDTH = 150;

    /** Default message to show in the message search field. */
    private static final String DEFAULT_SEARCH_MESSAGE =
            "Search for messages. Accepts Java regexes. "
            + "Prefix with pid:, tag: or text: to limit scope.";

    private static final String IMAGE_ADD_FILTER = "add.png"; //$NON-NLS-1$
    private static final String IMAGE_DELETE_FILTER = "delete.png"; //$NON-NLS-1$
    private static final String IMAGE_EDIT_FILTER = "edit.png"; //$NON-NLS-1$
    private static final String IMAGE_SAVE_LOG_TO_FILE = "save.png"; //$NON-NLS-1$
    private static final String IMAGE_CLEAR_LOG = "clear.png"; //$NON-NLS-1$

    /* FIXME: create appropriate icon */
    private static final String IMAGE_SCROLL_LOG_TO_LATEST = "halt.png"; //$NON-NLS-1$

    private LogCatReceiver mReceiver;
    private IPreferenceStore mPrefStore;

    private List<LogCatFilterSettings> mLogCatFilters;

    private ToolItem mNewFilterToolItem;
    private ToolItem mDeleteFilterToolItem;
    private ToolItem mEditFilterToolItem;
    private TableViewer mFiltersTableViewer;

    private Combo mLiveFilterLevelCombo;
    private Text mLiveFilterText;

    private TableViewer mViewer;

    /**
     * Construct a logcat panel.
     * @param r source of logcat messages.
     * @param prefStore preference store where UI preferences will be saved
     */
    public LogCatPanel(LogCatReceiver r, IPreferenceStore prefStore) {
        mReceiver = r;
        mPrefStore = prefStore;

        mReceiver.addMessageReceivedEventListener(this);

        initializeFilters(prefStore);
    }

    private void initializeFilters(IPreferenceStore prefStore) {
        mLogCatFilters = new ArrayList<LogCatFilterSettings>();

        /* add a couple of filters by default */
        mLogCatFilters.add(new LogCatFilterSettings("All messages (no filters)",
                "", "", LogLevel.VERBOSE));
        mLogCatFilters.add(new LogCatFilterSettings("Errors only",
                "", "", LogLevel.ERROR));

        /* FIXME restore saved filters from prefStore */
    }

    @Override
    public void deviceSelected() {
        mReceiver.stop();
        mReceiver.start(getCurrentDevice());
        mViewer.setInput(mReceiver.getMessages());
    }

    @Override
    public void clientSelected() {
    }

    @Override
    protected void postCreation() {
    }

    @Override
    protected Control createControl(Composite parent) {
        GridLayout layout = new GridLayout(1, false);
        parent.setLayout(layout);

        createViews(parent);
        setupDefaults();

        return null;
    }

    private void createViews(Composite parent) {
        SashForm sash = createSash(parent);

        createListOfFilters(sash);
        createLogTableView(sash);

        /* allocate widths of the two columns 20%:80% */
        /* FIXME: save/restore sash widths */
        sash.setWeights(new int[] {20, 80});
    }

    private SashForm createSash(Composite parent) {
        SashForm sash = new SashForm(parent, SWT.HORIZONTAL);
        sash.setLayoutData(new GridData(GridData.FILL_BOTH));
        return sash;
    }

    private void createListOfFilters(SashForm sash) {
        Composite c = new Composite(sash, SWT.BORDER);
        GridLayout layout = new GridLayout(2, false);
        c.setLayout(layout);
        c.setLayoutData(new GridData(GridData.FILL_BOTH));

        createFiltersToolbar(c);
        createFiltersTable(c);
    }

    private void createFiltersToolbar(Composite parent) {
        Label l = new Label(parent, SWT.NONE);
        l.setText("Saved Filters");
        GridData gd = new GridData();
        gd.horizontalAlignment = SWT.LEFT;
        l.setLayoutData(gd);

        ToolBar t = new ToolBar(parent, SWT.FLAT | SWT.BORDER);
        gd = new GridData();
        gd.horizontalAlignment = SWT.RIGHT;
        t.setLayoutData(gd);

        /* new filter */
        mNewFilterToolItem = new ToolItem(t, SWT.PUSH);
        mNewFilterToolItem.setImage(
                ImageLoader.getDdmUiLibLoader().loadImage(IMAGE_ADD_FILTER, t.getDisplay()));
        mNewFilterToolItem.setToolTipText("Add a new logcat filter");
        mNewFilterToolItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent arg0) {
                addNewFilter();
            }
        });

        /* delete filter */
        mDeleteFilterToolItem = new ToolItem(t, SWT.PUSH);
        mDeleteFilterToolItem.setImage(
                ImageLoader.getDdmUiLibLoader().loadImage(IMAGE_DELETE_FILTER, t.getDisplay()));
        mDeleteFilterToolItem.setToolTipText("Delete selected logcat filter");
        mDeleteFilterToolItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent arg0) {
                deleteSelectedFilter();
            }
        });

        /* edit filter */
        mEditFilterToolItem = new ToolItem(t, SWT.PUSH);
        mEditFilterToolItem.setImage(
                ImageLoader.getDdmUiLibLoader().loadImage(IMAGE_EDIT_FILTER, t.getDisplay()));
        mEditFilterToolItem.setToolTipText("Edit selected logcat filter");
        mEditFilterToolItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent arg0) {
                editSelectedFilter();
            }
        });
    }

    private void addNewFilter() {
        LogCatFilterSettingsDialog d = new LogCatFilterSettingsDialog(
                Display.getCurrent().getActiveShell());
        if (d.open() != Window.OK) {
            return;
        }

        LogCatFilterSettings f = new LogCatFilterSettings(d.getFilterName().trim(),
                d.getTag().trim(),
                d.getPID().trim(),
                LogLevel.getByString(d.getLogLevel()));

        mLogCatFilters.add(f);
        mFiltersTableViewer.refresh();

        /* select the newly added entry */
        int idx = mLogCatFilters.size() - 1;
        mFiltersTableViewer.getTable().setSelection(idx);

        filterSelectionChanged();
    }

    private void deleteSelectedFilter() {
        int selectedIndex = mFiltersTableViewer.getTable().getSelectionIndex();
        if (selectedIndex <= 0) {
            /* return if no selected filter, or the default filter was selected (0th). */
            return;
        }

        mLogCatFilters.remove(selectedIndex);
        mFiltersTableViewer.refresh();
        mFiltersTableViewer.getTable().setSelection(selectedIndex - 1);

        filterSelectionChanged();
    }

    private void editSelectedFilter() {
        int selectedIndex = mFiltersTableViewer.getTable().getSelectionIndex();
        if (selectedIndex < 0) {
            return;
        }

        LogCatFilterSettings curFilter = mLogCatFilters.get(selectedIndex);

        LogCatFilterSettingsDialog dialog = new LogCatFilterSettingsDialog(
                Display.getCurrent().getActiveShell());
        dialog.setDefaults(curFilter.getName(), curFilter.getTag(), curFilter.getPidString(),
                curFilter.getLogLevel());
        if (dialog.open() != Window.OK) {
            return;
        }

        LogCatFilterSettings f = new LogCatFilterSettings(dialog.getFilterName(),
                dialog.getTag(),
                dialog.getPID(),
                LogLevel.getByString(dialog.getLogLevel()));
        mLogCatFilters.set(selectedIndex, f);
        mFiltersTableViewer.refresh();

        mFiltersTableViewer.getTable().setSelection(selectedIndex);
        filterSelectionChanged();
    }

    private void createFiltersTable(Composite parent) {
        final Table table = new Table(parent, SWT.FULL_SELECTION);

        GridData gd = new GridData(GridData.FILL_BOTH);
        gd.horizontalSpan = 2;
        table.setLayoutData(gd);

        mFiltersTableViewer = new TableViewer(table);
        mFiltersTableViewer.setContentProvider(new LogCatFilterContentProvider());
        mFiltersTableViewer.setLabelProvider(new LogCatFilterLabelProvider());
        mFiltersTableViewer.setInput(mLogCatFilters);

        mFiltersTableViewer.getTable().addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent event) {
                filterSelectionChanged();
            }
        });
    }

    private void createLogTableView(SashForm sash) {
        Composite c = new Composite(sash, SWT.NONE);
        c.setLayout(new GridLayout());
        c.setLayoutData(new GridData(GridData.FILL_BOTH));

        createLiveFilters(c);
        createLogcatViewTable(c);
    }

    /**
     * Create the search bar at the top of the logcat messages table.
     * FIXME: Currently, this feature is incomplete: The UI elements are created, but they
     * are all set to disabled state.
     */
    private void createLiveFilters(Composite parent) {
        Composite c = new Composite(parent, SWT.NONE);
        c.setLayout(new GridLayout(3, false));
        c.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        mLiveFilterText = new Text(c, SWT.BORDER | SWT.SEARCH);
        mLiveFilterText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        mLiveFilterText.setMessage(DEFAULT_SEARCH_MESSAGE);

        mLiveFilterLevelCombo = new Combo(c, SWT.READ_ONLY | SWT.DROP_DOWN);
        mLiveFilterLevelCombo.setItems(
                LogCatFilterSettingsDialog.getLogLevels().toArray(new String[0]));
        mLiveFilterLevelCombo.select(0);

        ToolBar toolBar = new ToolBar(c, SWT.FLAT);

        ToolItem saveToLog = new ToolItem(toolBar, SWT.PUSH);
        saveToLog.setImage(ImageLoader.getDdmUiLibLoader().loadImage(IMAGE_SAVE_LOG_TO_FILE,
                toolBar.getDisplay()));
        saveToLog.setToolTipText("Export Log To Text File.");

        ToolItem clearLog = new ToolItem(toolBar, SWT.PUSH);
        clearLog.setImage(
                ImageLoader.getDdmUiLibLoader().loadImage(IMAGE_CLEAR_LOG, toolBar.getDisplay()));
        clearLog.setToolTipText("Clear Log");

        ToolItem scrollToLast = new ToolItem(toolBar, SWT.PUSH);
        scrollToLast.setImage(
                ImageLoader.getDdmUiLibLoader().loadImage(IMAGE_SCROLL_LOG_TO_LATEST,
                        toolBar.getDisplay()));
        scrollToLast.setToolTipText("Always scroll to the latest output from logcat");

        /* FIXME: Enable all the UI elements after adding support for user interaction with them. */
        mLiveFilterText.setEnabled(false);
        mLiveFilterLevelCombo.setEnabled(false);
        toolBar.setEnabled(false);
    }

    private void createLogcatViewTable(Composite parent) {
        /* SWT.VIRTUAL style will make the table render faster, but all rows will be
         * of equal heights which causes wrapped messages to just be clipped. */
        final Table table = new Table(parent, SWT.FULL_SELECTION);
        mViewer = new TableViewer(table);

        table.setLayoutData(new GridData(GridData.FILL_BOTH));
        table.getHorizontalBar().setVisible(true);

        /** Columns to show in the table. */
        String[] properties = {
                "Level",
                "Time",
                "PID",
                "Tag",
                "Text",
        };

        /** The sampleText for each column is used to determine the default widths
         * for each column. The contents do not matter, only their lengths are needed. */
        String[] sampleText = {
                "    II",
                "    00-00 00:00:00.0000 ",
                "    0000",
                "    SampleTagText",
                "    Log Message field should be pretty long by default.",
        };

        for (int i = 0; i < properties.length; i++) {
            TableHelper.createTableColumn(mViewer.getTable(),
                    properties[i],                      /* Column title */
                    SWT.LEFT,                           /* Column Style */
                    sampleText[i],                      /* String to compute default col width */
                    getPreferenceKey(properties[i]),    /* Preference Store key for this column */
                    mPrefStore);
        }

        mViewer.getTable().setLinesVisible(true); /* zebra stripe the table */
        mViewer.getTable().setHeaderVisible(true);

        mViewer.setLabelProvider(new LogCatMessageLabelProvider(MSG_WRAP_WIDTH));
        mViewer.setContentProvider(new LogCatMessageContentProvider());
        mViewer.setInput(mReceiver.getMessages());
    }

    private String getPreferenceKey(String field) {
        return "logcat.view.colsize." + field;
    }

    private void setupDefaults() {
        int defaultFilterIndex = 0;
        mFiltersTableViewer.getTable().setSelection(defaultFilterIndex);

        filterSelectionChanged();
    }

    /**
     * Perform all necessary updates whenever a filter is selected (by user or programmatically).
     */
    private void filterSelectionChanged() {
        int idx = mFiltersTableViewer.getTable().getSelectionIndex();

        updateFiltersToolBar(idx);
        selectFilter(idx);
    }

    private void updateFiltersToolBar(int index) {
        boolean en = true;
        if (index == 0) {
            en = false;
        }

        /* The default filter at index 0 can neither be edited or removed. */
        mEditFilterToolItem.setEnabled(en);
        mDeleteFilterToolItem.setEnabled(en);
    }

    private void selectFilter(int index) {
        assert index > 0 && index < mLogCatFilters.size();

        mViewer.setFilters(new ViewerFilter[] {
                new LogCatViewerFilter(mLogCatFilters.get(index)),
        });
    }

    @Override
    public void setFocus() {
    }

    /**
     * Update view whenever a message is received.
     * Implements {@link ILogCatMessageEventListener#messageReceived()}.
     */
    public void messageReceived() {
        Display.getDefault().asyncExec(new Runnable() {
            public void run() {
                if (mViewer.getTable().isDisposed()) {
                    return;
                }
                mViewer.refresh();

                /* if an item has been selected, then don't scroll the table,
                 * otherwise, always display the latest output.
                 * FIXME: this behavior should be controlled via a "scroll lock" button in UI. */
                if (mViewer.getTable().getSelectionCount() == 0) {
                    mViewer.getTable().setTopIndex(mViewer.getTable().getItemCount() - 1);
                }
            }
        });
    }
}
