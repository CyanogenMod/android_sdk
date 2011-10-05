/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.ide.eclipse.adt.internal.preferences;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.lint.LintEclipseContext;
import com.android.ide.eclipse.adt.internal.lint.LintRunner;
import com.android.ide.eclipse.adt.internal.project.BaseProjectHelper;
import com.android.sdklib.SdkConstants;
import com.android.tools.lint.api.DetectorRegistry;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.ColumnViewer;
import org.eclipse.jface.viewers.ComboBoxViewerCellEditor;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.PlatformUI;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Preference page for configuring Lint preferences */
public class LintPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {
    private static final int CATEGORY_COLUMN_WIDTH = 60;
    private static final int SEVERITY_COLUMN_WIDTH = 80;
    private static final int ID_COLUMN_WIDTH = 80;

    private Map<Issue, Severity> mSeverities = new HashMap<Issue, Severity>();
    private LintEclipseContext mContext;
    private DetectorRegistry mRegistry;

    private Table mTable;
    private Text mDetailsText;
    private Text mSuppressedText;
    private Button mCheckFileCheckbox;
    private Button mCheckExportCheckbox;

    /**
     * Create the preference page.
     */
    public LintPreferencePage() {
        setPreferenceStore(AdtPlugin.getDefault().getPreferenceStore());
    }

    @Override
    public Control createContents(Composite parent) {
        Composite container = new Composite(parent, SWT.NULL);
        container.setLayout(new GridLayout(2, false));

        mCheckFileCheckbox = new Button(container, SWT.CHECK);
        mCheckFileCheckbox.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
        mCheckFileCheckbox.setSelection(true);
        mCheckFileCheckbox.setText("When saving files, check for errors");

        mCheckExportCheckbox = new Button(container, SWT.CHECK);
        mCheckExportCheckbox.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
        mCheckExportCheckbox.setSelection(true);
        mCheckExportCheckbox.setText("Run full error check when exporting app");

        Label label = new Label(container, SWT.SEPARATOR | SWT.HORIZONTAL);
        label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 2, 1));

        Label checkListLabel = new Label(container, SWT.NONE);
        checkListLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
        checkListLabel.setText("Enabled checks:");

        CheckboxTableViewer checkboxTableViewer = CheckboxTableViewer.newCheckList(
                container, SWT.BORDER | SWT.FULL_SELECTION);
        mTable = checkboxTableViewer.getTable();
        mTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));

        TableViewerColumn column1 = new TableViewerColumn(checkboxTableViewer, SWT.NONE);
        final TableColumn idColumn = column1.getColumn();
        idColumn.setWidth(100);
        idColumn.setText("Id");

        TableViewerColumn column2 = new TableViewerColumn(checkboxTableViewer, SWT.FILL);
        final TableColumn nameColumn = column2.getColumn();
        nameColumn.setWidth(100);
        nameColumn.setText("Name");

        TableViewerColumn column3 = new TableViewerColumn(checkboxTableViewer, SWT.NONE);
        final TableColumn categoryColumn = column3.getColumn();
        categoryColumn.setWidth(100);
        categoryColumn.setText("Category");

        TableViewerColumn column4 = new TableViewerColumn(checkboxTableViewer, SWT.NONE);
        final TableColumn severityColumn = column4.getColumn();
        severityColumn.setWidth(100);
        severityColumn.setText("Severity");
        column4.setEditingSupport(new SeverityEditingSupport(column4.getViewer()));

        checkboxTableViewer.setContentProvider(new ContentProvider());
        checkboxTableViewer.setLabelProvider(new LabelProvider());

        mDetailsText = new Text(container, SWT.BORDER | SWT.READ_ONLY | SWT.WRAP |
                SWT.V_SCROLL | SWT.MULTI);
        GridData gdText = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
        gdText.heightHint = 80;
        mDetailsText.setLayoutData(gdText);

        mRegistry = LintEclipseContext.getRegistry();
        mContext = new LintEclipseContext(mRegistry, null, null);

        List<Issue> issues = mRegistry.getIssues();
        for (Issue issue : issues) {
            mSeverities.put(issue, mContext.getSeverity(issue));
        }
        checkboxTableViewer.setInput(issues);

        mTable.setLinesVisible(true);
        mTable.setHeaderVisible(true);

        Label suppressLabel = new Label(container, SWT.NONE);
        suppressLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        suppressLabel.setText("Suppressed Warnings:");

        mSuppressedText = new Text(container, SWT.BORDER);
        // Default path relative to the project
        mSuppressedText.setText("${project}/lint-suppressed.xml"); //$NON-NLS-1$
        mSuppressedText.setEnabled(false);
        mSuppressedText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

        mTable.addSelectionListener(new SelectionListener() {
            public void widgetDefaultSelected(SelectionEvent e) {
                widgetSelected(e);
            }

            public void widgetSelected(SelectionEvent e) {
                TableItem item = (TableItem) e.item;
                Issue issue = (Issue) item.getData();
                String summary = issue.getDescription();
                String explanation = issue.getExplanation();

                StringBuilder sb = new StringBuilder(summary.length() + explanation.length() + 20);
                sb.append(summary);
                sb.append('\n').append('\n');
                sb.append(explanation);
                mDetailsText.setText(sb.toString());
            }
        });

        // Add a listener to resize the column to the full width of the table
        mTable.addControlListener(new ControlAdapter() {
            @Override
            public void controlResized(ControlEvent e) {
                Rectangle r = mTable.getClientArea();
                int availableWidth = r.width;

                // On the Mac, the width of the checkbox column is not included (and checkboxes
                // are shown if mAllowSelection=true). Subtract this size from the available
                // width to be distributed among the columns.
                if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_DARWIN) {
                    availableWidth -= getCheckboxWidth(e.display.getActiveShell());
                }

                idColumn.setWidth(ID_COLUMN_WIDTH);
                availableWidth -= ID_COLUMN_WIDTH;

                severityColumn.setWidth(SEVERITY_COLUMN_WIDTH);
                availableWidth -= SEVERITY_COLUMN_WIDTH;

                categoryColumn.setWidth(CATEGORY_COLUMN_WIDTH);
                availableWidth -= CATEGORY_COLUMN_WIDTH;

                // Name absorbs everything else
                nameColumn.setWidth(availableWidth);
            }
        });

        loadSettings();

        return container;
    }

    /**
     * Initialize the preference page.
     */
    public void init(IWorkbench workbench) {
        // Initialize the preference page
    }

    @Override
    public void dispose() {
        super.dispose();
    }

    /** Cache for {@link #getCheckboxWidth()} */
    private static int sCheckboxWidth = -1;

    /** Computes the width of a checkbox */
    private int getCheckboxWidth(Shell shell) {
        if (sCheckboxWidth == -1) {
            Shell tempShell = new Shell(shell, SWT.NO_TRIM);
            Button checkBox = new Button(tempShell, SWT.CHECK);
            sCheckboxWidth = checkBox.computeSize(SWT.DEFAULT, SWT.DEFAULT).x;
            tempShell.dispose();
        }

        return sCheckboxWidth;
    }

    @Override
    public boolean performOk() {
        storeSettings();
        return true;
    }

    private void loadSettings() {
        AdtPrefs prefs = AdtPrefs.getPrefs();
        mCheckFileCheckbox.setSelection(prefs.isLintOnSave());
        mCheckExportCheckbox.setSelection(prefs.isLintOnExport());

        IPreferenceStore store = getPreferenceStore();
        Set<String> excluded = new HashSet<String>();
        String ids = store.getString(AdtPrefs.PREFS_DISABLED_ISSUES);
        if (ids != null && ids.length() > 0) {
            for (String s : ids.split(",")) { //$NON-NLS-1$
                excluded.add(s);
            }
        }
        TableItem[] itemList = mTable.getItems();
        for (int i = 0; i < itemList.length; i++) {
            Issue issue = (Issue) itemList[i].getData();
            itemList[i].setChecked(!excluded.contains(issue.getId()));
        }
    }

    private void storeSettings() {
        // Lint on Save, Lint on Export
        AdtPrefs prefs = AdtPrefs.getPrefs();
        prefs.setLintOnExport(mCheckExportCheckbox.getSelection());
        prefs.setLintOnSave(mCheckFileCheckbox.getSelection());

        // Severities
        mContext.setSeverities(mSeverities);

        // Excluded checks
        StringBuilder sb = new StringBuilder();
        TableItem[] itemList = mTable.getItems();
        for (int i = 0; i < itemList.length; i++) {
            if (!itemList[i].getChecked()) {
                Issue check = (Issue) itemList[i].getData();
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(check.getId());
            }
        }
        String value = sb.toString();
        if (value.length() == 0) {
            value = null;
        }

        IPreferenceStore store = getPreferenceStore();
        String previous = store.getString(AdtPrefs.PREFS_DISABLED_ISSUES);
        boolean unchanged = (previous != null && previous.equals(value)) || (previous == value);
        if (!unchanged) {
            if (value == null) {
                store.setToDefault(AdtPrefs.PREFS_DISABLED_ISSUES);
            } else {
                store.setValue(AdtPrefs.PREFS_DISABLED_ISSUES, value);
            }

            // Ask user whether we should re-run the rules.
            MessageDialog dialog = new MessageDialog(
                    null, "Lint Settings Have Changed", null,
                    "The list of enabled checks has changed. Would you like to run lint now " +
                            "to update the results?",
                    MessageDialog.QUESTION,
                    new String[] {
                            "Yes", "No"
                    },
                    0); // yes is the default
            int result = dialog.open();
            if (result == 0) {
                // Run lint on all the open Android projects
                IWorkspace workspace = ResourcesPlugin.getWorkspace();
                IProject[] projects = workspace.getRoot().getProjects();
                for (IProject project : projects) {
                    if (project.isOpen() && BaseProjectHelper.isAndroidProject(project)) {
                        LintRunner.startLint(project, null);
                    }
                }
            }
        }
    }

    private static class ContentProvider implements IStructuredContentProvider {
        public Object[] getElements(Object inputElement) {
            @SuppressWarnings("unchecked")
            List<Issue> issues = (List<Issue>) inputElement;
            return issues.toArray();
        }
        public void dispose() {
        }
        public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
        }
    }

    private class LabelProvider implements ITableLabelProvider {
        // TODO: add IColorProvider ?

        public void addListener(ILabelProviderListener listener) {
        }

        public void dispose() {
        }

        public boolean isLabelProperty(Object element, String property) {
            return true;
        }

        public void removeListener(ILabelProviderListener listener) {
        }

        public Image getColumnImage(Object element, int columnIndex) {
            if (columnIndex == 3) {
                Issue issue = (Issue) element;
                Severity severity = mSeverities.get(issue);
                if (severity == null) {
                    return null;
                }

                ISharedImages sharedImages = PlatformUI.getWorkbench().getSharedImages();
                switch (severity) {
                    case ERROR:
                        return sharedImages.getImage(ISharedImages.IMG_OBJS_ERROR_TSK);
                    case WARNING:
                        return sharedImages.getImage(ISharedImages.IMG_OBJS_WARN_TSK);
                    case INFORMATIONAL:
                        return sharedImages.getImage(ISharedImages.IMG_OBJS_INFO_TSK);
                    case IGNORE:
                        // TBD: Is this icon okay?
                        return sharedImages.getImage(ISharedImages.IMG_ELCL_REMOVE_DISABLED);
                }
            }
            return null;
        }

        public String getColumnText(Object element, int columnIndex) {
            Issue issue = (Issue) element;
            switch (columnIndex) {
                case 0:
                    return issue.getId();
                case 1:
                    return issue.getDescription();
                case 2:
                    return issue.getCategory();
                case 3: {
                    Severity severity = mSeverities.get(issue);
                    if (severity == null) {
                        return null;
                    }
                    return severity.getDescription();
                }
            }

            return null;
        }
    }

    /** Editing support for the severity column */
    private class SeverityEditingSupport extends EditingSupport
            implements ILabelProvider, IStructuredContentProvider {
        private final ComboBoxViewerCellEditor mCellEditor;

        @SuppressWarnings("deprecation") // Can't use the new form of setContentProvider until 3.7
        private SeverityEditingSupport(ColumnViewer viewer) {
            super(viewer);
            Composite control = (Composite) getViewer().getControl();
            mCellEditor = new ComboBoxViewerCellEditor(control, SWT.READ_ONLY);
            mCellEditor.setLabelProvider(this);
            mCellEditor.setContenProvider(this);
            mCellEditor.setInput(Severity.values());
        }

        @Override
        protected boolean canEdit(Object element) {
            return true;
        }

        @Override
        protected Object getValue(Object element) {
            if (element instanceof Issue) {
                Issue issue = (Issue) element;
                Severity severity = mSeverities.get(issue);
                return severity;
            }

            return null;
        }

        @Override
        protected void setValue(Object element, Object value) {
            if (element instanceof Issue && value instanceof Severity) {
                Issue issue = (Issue) element;
                Severity newValue = (Severity) value;
                mSeverities.put(issue, newValue);
                getViewer().update(element, null);
            }
        }

        @Override
        protected CellEditor getCellEditor(Object element) {
            return mCellEditor;
        }

        // ---- Implements ILabelProvider ----

        public String getText(Object element) {
            return ((Severity) element).getDescription();
        }

        public void addListener(ILabelProviderListener listener) {
        }

        public void dispose() {
        }

        public boolean isLabelProperty(Object element, String property) {
            return false;
        }

        public void removeListener(ILabelProviderListener listener) {
        }

        public Image getImage(Object element) {
            return null;
        }

        // ---- Implements IStructuredContentProvider ----

        public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
        }

        public Object[] getElements(Object inputElement) {
            return Severity.values();
        }
    }

}