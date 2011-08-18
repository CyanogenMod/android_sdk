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

import com.android.ddmlib.DdmConstants;
import com.android.ddmlib.Log.LogLevel;
import com.android.ddmuilib.ImageLoader;
import com.android.ddmuilib.SelectionDependentPanel;
import com.android.ddmuilib.TableHelper;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ScrollBar;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * LogCatPanel displays a table listing the logcat messages.
 */
public final class LogCatPanel extends SelectionDependentPanel
                        implements ILogCatMessageEventListener {
    /** Preference key to use for storing list of logcat filters. */
    public static final String LOGCAT_FILTERS_LIST = "logcat.view.filters.list";

    /** Preference key to use for storing font settings. */
    public static final String LOGCAT_VIEW_FONT_PREFKEY = "logcat.view.font";

    private static final String FONT_FAMILY =
            DdmConstants.CURRENT_PLATFORM == DdmConstants.PLATFORM_DARWIN ? "Monaco":"Courier New";
    private static final FontData DEFAULT_LOGCAT_FONT = new FontData(
            FONT_FAMILY, 12, SWT.NORMAL);

    private static final String LOGCAT_VIEW_COLSIZE_PREFKEY_PREFIX = "logcat.view.colsize.";

    /** Default message to show in the message search field. */
    private static final String DEFAULT_SEARCH_MESSAGE =
            "Search for messages. Accepts Java regexes. "
            + "Prefix with pid:, tag: or text: to limit scope.";

    private static final String IMAGE_ADD_FILTER = "add.png"; //$NON-NLS-1$
    private static final String IMAGE_DELETE_FILTER = "delete.png"; //$NON-NLS-1$
    private static final String IMAGE_EDIT_FILTER = "edit.png"; //$NON-NLS-1$
    private static final String IMAGE_SAVE_LOG_TO_FILE = "save.png"; //$NON-NLS-1$
    private static final String IMAGE_CLEAR_LOG = "clear.png"; //$NON-NLS-1$

    private LogCatReceiver mReceiver;
    private IPreferenceStore mPrefStore;

    private List<LogCatFilter> mLogCatFilters;
    private int mCurrentSelectedFilterIndex;

    private ToolItem mNewFilterToolItem;
    private ToolItem mDeleteFilterToolItem;
    private ToolItem mEditFilterToolItem;
    private TableViewer mFiltersTableViewer;

    private Combo mLiveFilterLevelCombo;
    private Text mLiveFilterText;

    private TableViewer mViewer;

    private String mLogFileExportFolder;
    private LogCatMessageLabelProvider mLogCatMessageLabelProvider;

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
        initializeFonts(prefStore);
    }

    private void initializeFilters(IPreferenceStore prefStore) {
        mLogCatFilters = new ArrayList<LogCatFilter>();

        /* add default filter matching all messages */
        String tag = "";
        String text = "";
        String pid = "";
        mLogCatFilters.add(new LogCatFilter("All messages (no filters)",
                tag, text, pid, LogLevel.VERBOSE));

        /* restore saved filters from prefStore */
        List<LogCatFilter> savedFilters = getSavedFilters();
        mLogCatFilters.addAll(savedFilters);
    }

    private void initializeFonts(IPreferenceStore prefStore) {
        PreferenceConverter.setDefault(mPrefStore, LogCatPanel.LOGCAT_VIEW_FONT_PREFKEY,
                DEFAULT_LOGCAT_FONT);

        mPrefStore.addPropertyChangeListener(new IPropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent event) {
                if (event.getProperty().equals(LogCatPanel.LOGCAT_VIEW_FONT_PREFKEY)) {
                    mLogCatMessageLabelProvider.setFont(getFontFromPrefStore());
                    refreshLogCatTable();
                }
            }
        });
    }

    private void saveFilterPreferences() {
        LogCatFilterSettingsSerializer serializer = new LogCatFilterSettingsSerializer();

        /* save all filter settings except the first one which is the default */
        String e = serializer.encodeToPreferenceString(
                mLogCatFilters.subList(1, mLogCatFilters.size()));
        mPrefStore.setValue(LOGCAT_FILTERS_LIST, e);
    }

    private List<LogCatFilter> getSavedFilters() {
        LogCatFilterSettingsSerializer serializer = new LogCatFilterSettingsSerializer();
        String e = mPrefStore.getString(LOGCAT_FILTERS_LIST);
        return serializer.decodeFromPreferenceString(e);
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

        LogCatFilter f = new LogCatFilter(d.getFilterName().trim(),
                d.getTag().trim(),
                d.getText().trim(),
                d.getPID().trim(),
                LogLevel.getByString(d.getLogLevel()));

        mLogCatFilters.add(f);
        mFiltersTableViewer.refresh();

        /* select the newly added entry */
        int idx = mLogCatFilters.size() - 1;
        mFiltersTableViewer.getTable().setSelection(idx);

        filterSelectionChanged();
        saveFilterPreferences();
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
        saveFilterPreferences();
    }

    private void editSelectedFilter() {
        int selectedIndex = mFiltersTableViewer.getTable().getSelectionIndex();
        if (selectedIndex < 0) {
            return;
        }

        LogCatFilter curFilter = mLogCatFilters.get(selectedIndex);

        LogCatFilterSettingsDialog dialog = new LogCatFilterSettingsDialog(
                Display.getCurrent().getActiveShell());
        dialog.setDefaults(curFilter.getName(), curFilter.getTag(), curFilter.getText(),
                curFilter.getPidString(), curFilter.getLogLevel());
        if (dialog.open() != Window.OK) {
            return;
        }

        LogCatFilter f = new LogCatFilter(dialog.getFilterName(),
                dialog.getTag(),
                dialog.getText(),
                dialog.getPID(),
                LogLevel.getByString(dialog.getLogLevel()));
        mLogCatFilters.set(selectedIndex, f);
        mFiltersTableViewer.refresh();

        mFiltersTableViewer.getTable().setSelection(selectedIndex);
        filterSelectionChanged();
        saveFilterPreferences();
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
        mLiveFilterText.addModifyListener(new ModifyListener() {
            public void modifyText(ModifyEvent arg0) {
                updateAppliedFilters();
            }
        });

        mLiveFilterLevelCombo = new Combo(c, SWT.READ_ONLY | SWT.DROP_DOWN);
        mLiveFilterLevelCombo.setItems(
                LogCatFilterSettingsDialog.getLogLevels().toArray(new String[0]));
        mLiveFilterLevelCombo.select(0);
        mLiveFilterLevelCombo.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent arg0) {
                updateAppliedFilters();
            }
        });

        ToolBar toolBar = new ToolBar(c, SWT.FLAT);

        ToolItem saveToLog = new ToolItem(toolBar, SWT.PUSH);
        saveToLog.setImage(ImageLoader.getDdmUiLibLoader().loadImage(IMAGE_SAVE_LOG_TO_FILE,
                toolBar.getDisplay()));
        saveToLog.setToolTipText("Export Selected Items To Text File..");
        saveToLog.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent arg0) {
                saveLogToFile();
            }
        });

        ToolItem clearLog = new ToolItem(toolBar, SWT.PUSH);
        clearLog.setImage(
                ImageLoader.getDdmUiLibLoader().loadImage(IMAGE_CLEAR_LOG, toolBar.getDisplay()));
        clearLog.setToolTipText("Clear Log");
        clearLog.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent arg0) {
                mReceiver.clearMessages();
                refreshLogCatTable();
            }
        });
    }

    /**
     * Save logcat messages selected in the table to a file.
     */
    private void saveLogToFile() {
        /* show dialog box and get target file name */
        final String fName = getLogFileTargetLocation();
        if (fName == null) {
            return;
        }

        /* obtain list of selected messages */
        final List<LogCatMessage> selectedMessages = getSelectedLogCatMessages();

        /* save messages to file in a different (non UI) thread */
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    BufferedWriter w = new BufferedWriter(new FileWriter(fName));
                    for (LogCatMessage m : selectedMessages) {
                        w.append(m.toString());
                        w.newLine();
                    }
                    w.close();
                } catch (final IOException e) {
                    Display.getDefault().asyncExec(new Runnable() {
                        public void run() {
                            MessageDialog.openError(Display.getCurrent().getActiveShell(),
                                    "Unable to export selection to file.",
                                    "Unexpected error while saving selected messages to file: "
                                            + e.getMessage());
                        }
                    });
                }
            }
        });
        t.setName("Saving selected items to logfile..");
        t.start();
    }

    /**
     * Display a {@link FileDialog} to the user and obtain the location for the log file.
     * @return path to target file, null if user canceled the dialog
     */
    private String getLogFileTargetLocation() {
        FileDialog fd = new FileDialog(Display.getCurrent().getActiveShell(), SWT.SAVE);

        fd.setText("Save Log..");
        fd.setFileName("log.txt");

        if (mLogFileExportFolder == null) {
            mLogFileExportFolder = System.getProperty("user.home");
        }
        fd.setFilterPath(mLogFileExportFolder);

        fd.setFilterNames(new String[] {
                "Text Files (*.txt)"
        });
        fd.setFilterExtensions(new String[] {
                "*.txt"
        });

        String fName = fd.open();
        if (fName != null) {
            mLogFileExportFolder = fd.getFilterPath();  /* save path to restore on future calls */
        }

        return fName;
    }

    private List<LogCatMessage> getSelectedLogCatMessages() {
        Table table = mViewer.getTable();
        int[] indices = table.getSelectionIndices();
        Arrays.sort(indices); /* Table.getSelectionIndices() does not specify an order */

        List<LogCatMessage> selectedMessages = new ArrayList<LogCatMessage>(indices.length);
        for (int i : indices) {
            LogCatMessage m = (LogCatMessage) table.getItem(i).getData();
            selectedMessages.add(m);
        }

        return selectedMessages;
    }

    private void createLogcatViewTable(Composite parent) {
        /* SWT.VIRTUAL style will make the table render faster, but all rows will be
         * of equal heights which causes wrapped messages to just be clipped. */
        final Table table = new Table(parent, SWT.FULL_SELECTION | SWT.MULTI);
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
                    getColPreferenceKey(properties[i]), /* Preference Store key for this column */
                    mPrefStore);
        }

        mViewer.getTable().setLinesVisible(true); /* zebra stripe the table */
        mViewer.getTable().setHeaderVisible(true);

        mLogCatMessageLabelProvider = new LogCatMessageLabelProvider(getFontFromPrefStore(),
                mViewer.getTable());
        mViewer.setLabelProvider(mLogCatMessageLabelProvider);
        mViewer.setContentProvider(new LogCatMessageContentProvider());
        mViewer.setInput(mReceiver.getMessages());

        initDoubleClickListener();
    }

    private String getColPreferenceKey(String field) {
        return LOGCAT_VIEW_COLSIZE_PREFKEY_PREFIX + field;
    }

    private Font getFontFromPrefStore() {
        FontData fd = PreferenceConverter.getFontData(mPrefStore,
                LogCatPanel.LOGCAT_VIEW_FONT_PREFKEY);
        return new Font(Display.getDefault(), fd);
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
        int idx = getSelectedSavedFilterIndex();
        if (idx == -1) {
            /* One of the filters should always be selected.
             * On Linux, there is no way to deselect an item.
             * On Mac, clicking inside the list view, but not an any item will result
             * in all items being deselected. In such a case, we simply reselect the
             * first entry. */
            idx = 0;
            mFiltersTableViewer.getTable().setSelection(idx);
        }

        mCurrentSelectedFilterIndex = idx;

        resetUnreadCountForSelectedFilter();
        updateFiltersToolBar();
        updateAppliedFilters();
    }

    private void resetUnreadCountForSelectedFilter() {
        int index = getSelectedSavedFilterIndex();
        mLogCatFilters.get(index).resetUnreadCount();

        refreshFiltersTable();
    }

    private int getSelectedSavedFilterIndex() {
        return mFiltersTableViewer.getTable().getSelectionIndex();
    }

    private void updateFiltersToolBar() {
        /* The default filter at index 0 can neither be edited, nor removed. */
        boolean en = getSelectedSavedFilterIndex() != 0;
        mEditFilterToolItem.setEnabled(en);
        mDeleteFilterToolItem.setEnabled(en);
    }

    private void updateAppliedFilters() {
        /* list of filters to apply = saved filter + live filters */
        List<LogCatViewerFilter> filters = new ArrayList<LogCatViewerFilter>();
        filters.add(getSelectedSavedFilter());
        filters.addAll(getCurrentLiveFilters());
        mViewer.setFilters(filters.toArray(new LogCatViewerFilter[filters.size()]));

        /* whenever filters are changed, the number of displayed logs changes
         * drastically. Display the latest log in such a situation. */
        scrollToLatestLog();
    }

    private List<LogCatViewerFilter> getCurrentLiveFilters() {
        List<LogCatViewerFilter> liveFilters = new ArrayList<LogCatViewerFilter>();

        List<LogCatFilter> liveFilterSettings = LogCatFilter.fromString(
                mLiveFilterText.getText(),                                  /* current query */
                LogLevel.getByString(mLiveFilterLevelCombo.getText()));     /* current log level */
        for (LogCatFilter s : liveFilterSettings) {
            liveFilters.add(new LogCatViewerFilter(s));
        }

        return liveFilters;
    }

    private LogCatViewerFilter getSelectedSavedFilter() {
        int index = getSelectedSavedFilterIndex();
        return new LogCatViewerFilter(mLogCatFilters.get(index));
    }


    @Override
    public void setFocus() {
    }

    /**
     * Update view whenever a message is received.
     * @param receivedMessages list of messages from logcat
     * Implements {@link ILogCatMessageEventListener#messageReceived()}.
     */
    public void messageReceived(List<LogCatMessage> receivedMessages) {
        refreshLogCatTable();

        updateUnreadCount(receivedMessages);
        refreshFiltersTable();
    }

    /**
     * When new messages are received, and they match a saved filter, update
     * the unread count associated with that filter.
     * @param receivedMessages list of new messages received
     */
    private void updateUnreadCount(List<LogCatMessage> receivedMessages) {
        for (int i = 0; i < mLogCatFilters.size(); i++) {
            if (i == mCurrentSelectedFilterIndex) {
                /* no need to update unread count for currently selected filter */
                continue;
            }
            mLogCatFilters.get(i).updateUnreadCount(receivedMessages);
        }
    }

    private void refreshFiltersTable() {
        Display.getDefault().asyncExec(new Runnable() {
            public void run() {
                if (mFiltersTableViewer.getTable().isDisposed()) {
                    return;
                }
                mFiltersTableViewer.refresh();
            }
        });
    }

    private void refreshLogCatTable() {
        Display.getDefault().asyncExec(new Runnable() {
            public void run() {
                if (mViewer.getTable().isDisposed()) {
                    return;
                }
                mViewer.refresh();

                if (shouldScrollToLatestLog()) {
                    scrollToLatestLog();
                }
            }
        });
    }

    /** Scroll to the last line. */
    private void scrollToLatestLog() {
        mViewer.getTable().setTopIndex(mViewer.getTable().getItemCount() - 1);
    }

    /**
     * Determine if the table should scroll to reveal the last entry. The scrolling
     * behavior is as follows:
     * <ul>
     *   <li> Scroll if the scrollbar "thumb" is at the bottom of the scrollbar, i.e.,
     *        the last line of the table is visible. </li>
     *   <li> Do not scroll otherwise. This happens if the user manually moves the thumb
     *        to scroll up the table.
     * </ul>
     * @return true if table should be scrolled, false otherwise.
     */
    private boolean shouldScrollToLatestLog() {
        ScrollBar sb = mViewer.getTable().getVerticalBar();
        if (sb == null) {
            return true;
        }

        return sb.getSelection() + sb.getThumb() == sb.getMaximum();
    }

    private List<ILogCatMessageSelectionListener> mMessageSelectionListeners;

    private void initDoubleClickListener() {
        mMessageSelectionListeners = new ArrayList<ILogCatMessageSelectionListener>(1);

        mViewer.getTable().addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetDefaultSelected(SelectionEvent arg0) {
                List<LogCatMessage> selectedMessages = getSelectedLogCatMessages();
                if (selectedMessages.size() == 0) {
                    return;
                }

                for (ILogCatMessageSelectionListener l : mMessageSelectionListeners) {
                    l.messageDoubleClicked(selectedMessages.get(0));
                }
            }
        });
    }

    public void addLogCatMessageSelectionListener(ILogCatMessageSelectionListener l) {
        mMessageSelectionListeners.add(l);
    }
}
