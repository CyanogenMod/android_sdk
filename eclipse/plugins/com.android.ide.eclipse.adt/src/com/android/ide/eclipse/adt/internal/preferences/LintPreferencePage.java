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
import com.android.ide.eclipse.adt.AdtUtils;
import com.android.ide.eclipse.adt.internal.lint.EclipseLintClient;
import com.android.ide.eclipse.adt.internal.lint.LintRunner;
import com.android.ide.eclipse.adt.internal.project.BaseProjectHelper;
import com.android.tools.lint.client.api.Configuration;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Severity;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IColorProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.TreeNodeContentProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.dialogs.PropertyPage;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Preference page for configuring Lint preferences */
public class LintPreferencePage extends PropertyPage implements IWorkbenchPreferencePage,
        SelectionListener, ControlListener {
    private static final String ID =
            "com.android.ide.eclipse.common.preferences.LintPreferencePage"; //$NON-NLS-1$
    private static final int ID_COLUMN_WIDTH = 150;

    private EclipseLintClient mClient;
    private IssueRegistry mRegistry;
    private Configuration mConfiguration;
    private IProject mProject;
    private Map<Issue, Severity> mSeverities = new HashMap<Issue, Severity>();
    private boolean mIgnoreEvent;

    private Tree mTree;
    private TreeViewer mTreeViewer;
    private Text mDetailsText;
    private Button mCheckFileCheckbox;
    private Button mCheckExportCheckbox;
    private Link mWorkspaceLink;
    private TreeColumn mNameColumn;
    private TreeColumn mIdColumn;
    private Combo mSeverityCombo;

    /**
     * Create the preference page.
     */
    public LintPreferencePage() {
        setPreferenceStore(AdtPlugin.getDefault().getPreferenceStore());
    }

    @Override
    public Control createContents(Composite parent) {
        IAdaptable resource = getElement();
        if (resource != null) {
            mProject = (IProject) resource.getAdapter(IProject.class);
        }

        Composite container = new Composite(parent, SWT.NULL);
        container.setLayout(new GridLayout(2, false));

        Project project = null;
        if (mProject != null) {
            File dir = AdtUtils.getAbsolutePath(mProject).toFile();
            project = new Project(mClient, dir, dir);

            Label projectLabel = new Label(container, SWT.CHECK);
            projectLabel.setLayoutData(new GridData(SWT.LEFT, SWT.BOTTOM, false, false, 1,
                    1));
            projectLabel.setText("Project-specific configuration:");

            mWorkspaceLink = new Link(container, SWT.NONE);
            mWorkspaceLink.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
            mWorkspaceLink.setText("<a>Configure Workspace Settings...</a>");
            mWorkspaceLink.addSelectionListener(this);
        } else {
            mCheckFileCheckbox = new Button(container, SWT.CHECK);
            mCheckFileCheckbox.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false,
                    2, 1));
            mCheckFileCheckbox.setSelection(true);
            mCheckFileCheckbox.setText("When saving files, check for errors");

            mCheckExportCheckbox = new Button(container, SWT.CHECK);
            mCheckExportCheckbox.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false,
                    2, 1));
            mCheckExportCheckbox.setSelection(true);
            mCheckExportCheckbox.setText("Run full error check when exporting app");

            Label separator = new Label(container, SWT.SEPARATOR | SWT.HORIZONTAL);
            separator.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 2, 1));

            Label checkListLabel = new Label(container, SWT.NONE);
            checkListLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
            checkListLabel.setText("Issues:");
        }

        mRegistry = EclipseLintClient.getRegistry();
        mClient = new EclipseLintClient(mRegistry, mProject, null, false);
        mConfiguration = mClient.getConfiguration(project);

        mTreeViewer = new TreeViewer(container, SWT.BORDER | SWT.FULL_SELECTION);
        mTree = mTreeViewer.getTree();
        GridData gdTable = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
        gdTable.widthHint = 500;
        gdTable.heightHint = 160;
        mTree.setLayoutData(gdTable);
        mTree.setLinesVisible(true);
        mTree.setHeaderVisible(true);

        TreeViewerColumn column1 = new TreeViewerColumn(mTreeViewer, SWT.NONE);
        mIdColumn = column1.getColumn();
        mIdColumn.setWidth(100);
        mIdColumn.setText("Id");

        TreeViewerColumn column2 = new TreeViewerColumn(mTreeViewer, SWT.FILL);
        mNameColumn = column2.getColumn();
        mNameColumn.setWidth(100);
        mNameColumn.setText("Name");

        mTreeViewer.setContentProvider(new ContentProvider());
        mTreeViewer.setLabelProvider(new LabelProvider());

        mDetailsText = new Text(container, SWT.BORDER | SWT.READ_ONLY | SWT.WRAP |SWT.V_SCROLL
                | SWT.MULTI);
        GridData gdText = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 2);
        gdText.heightHint = 80;
        mDetailsText.setLayoutData(gdText);

        Label severityLabel = new Label(container, SWT.NONE);
        severityLabel.setText("Severity:");

        mSeverityCombo = new Combo(container, SWT.READ_ONLY);
        mSeverityCombo.setItems(new String[] {
                "(Default)", "Error", "Warning", "Information", "Ignore"
        });
        GridData gdSeverityCombo = new GridData(SWT.FILL, SWT.TOP, false, false, 1, 1);
        gdSeverityCombo.widthHint = 90;
        mSeverityCombo.setLayoutData(gdSeverityCombo);
        mSeverityCombo.setText("");
        mSeverityCombo.addSelectionListener(this);

        List<Issue> issues = mRegistry.getIssues();
        for (Issue issue : issues) {
            Severity severity = mConfiguration.getSeverity(issue);
            mSeverities.put(issue, severity);
        }

        mTreeViewer.setInput(mRegistry);

        mTree.addSelectionListener(this);
        // Add a listener to resize the column to the full width of the table
        mTree.addControlListener(this);

        loadSettings(false);

        mTreeViewer.expandAll();

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

    @Override
    protected void performDefaults() {
        super.performDefaults();

        mConfiguration.startBulkEditing();

        List<Issue> issues = mRegistry.getIssues();
        for (Issue issue : issues) {
            mConfiguration.setSeverity(issue, null);
        }

        mConfiguration.finishBulkEditing();

        loadSettings(true);
    }

    @Override
    public boolean performOk() {
        storeSettings();
        return true;
    }

    private void loadSettings(boolean refresh) {
        if (mCheckExportCheckbox != null) {
            AdtPrefs prefs = AdtPrefs.getPrefs();
            mCheckFileCheckbox.setSelection(prefs.isLintOnSave());
            mCheckExportCheckbox.setSelection(prefs.isLintOnExport());
        }

        mSeverities.clear();
        List<Issue> issues = mRegistry.getIssues();
        for (Issue issue : issues) {
            Severity severity = mConfiguration.getSeverity(issue);
            mSeverities.put(issue, severity);
        }

        if (refresh) {
            mTreeViewer.refresh();
        }
    }

    private void storeSettings() {
        // Lint on Save, Lint on Export
        if (mCheckExportCheckbox != null) {
            AdtPrefs prefs = AdtPrefs.getPrefs();
            prefs.setLintOnExport(mCheckExportCheckbox.getSelection());
            prefs.setLintOnSave(mCheckFileCheckbox.getSelection());
        }

        mConfiguration.startBulkEditing();
        boolean changed = false;
        try {
            // Severities
            for (Map.Entry<Issue, Severity> entry : mSeverities.entrySet()) {
                Issue issue = entry.getKey();
                Severity severity = entry.getValue();
                if (mConfiguration.getSeverity(issue) != severity) {
                    if (severity == issue.getDefaultSeverity()) {
                        severity = null;
                    }
                    mConfiguration.setSeverity(issue, severity);
                    changed = true;
                }
            }
        } finally {
            mConfiguration.finishBulkEditing();
        }

        if (changed) {
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
                        LintRunner.startLint(project, null, false);
                    }
                }
            }
        }
    }

    // ---- Implements SelectionListener ----

    public void widgetSelected(SelectionEvent e) {
        if (mIgnoreEvent) {
            return;
        }

        Object source = e.getSource();
        if (source == mTree) {
            TreeItem item = (TreeItem) e.item;
            Object data = item.getData();
            if (data instanceof Issue) {
                Issue issue = (Issue) data;
                String summary = issue.getDescription();
                String explanation = issue.getExplanation();

                StringBuilder sb = new StringBuilder(summary.length() + explanation.length() + 20);
                sb.append(summary);
                sb.append('\n').append('\n');
                sb.append(explanation);
                mDetailsText.setText(sb.toString());
                try {
                    mIgnoreEvent = true;
                    Severity severity = getSeverity(issue);
                    mSeverityCombo.select(severity.ordinal() + 1); // Skip the default option
                    mSeverityCombo.setEnabled(true);
                } finally {
                    mIgnoreEvent = false;
                }
            } else {
                mDetailsText.setText("");
                try {
                    mIgnoreEvent = true;
                    mSeverityCombo.setText("");
                    mSeverityCombo.setEnabled(false);
                } finally {
                    mIgnoreEvent = false;
                }
            }
        } else if (source == mWorkspaceLink) {
            int result = PreferencesUtil.createPreferenceDialogOn(getShell(), ID,
                    new String[] { ID }, null).open();
            if (result == Window.OK) {
                loadSettings(true);
            }
        } else if (source == mSeverityCombo) {
            int index = mSeverityCombo.getSelectionIndex();
            Issue issue = (Issue) mTree.getSelection()[0].getData();
            Severity severity;
            if (index == -1 || index == 0) {
                // "(Default)"
                severity = issue.getDefaultSeverity();
            } else {
                // -1: Skip the "(Default)"
                severity = Severity.values()[index - 1];
            }
            mSeverities.put(issue, severity);
            mTreeViewer.refresh();
        }
    }

    private Severity getSeverity(Issue issue) {
        Severity severity = mSeverities.get(issue);
        if (severity != null) {
            return severity;
        }

        return mConfiguration.getSeverity(issue);
    }

    public void widgetDefaultSelected(SelectionEvent e) {
        if (e.getSource() == mTree) {
            widgetSelected(e);
        }
    }

    // ---- Implements ControlListener ----

    public void controlMoved(ControlEvent e) {
    }

    public void controlResized(ControlEvent e) {
        Rectangle r = mTree.getClientArea();
        int availableWidth = r.width;

        mIdColumn.setWidth(ID_COLUMN_WIDTH);
        availableWidth -= ID_COLUMN_WIDTH;

        // Name absorbs everything else
        mNameColumn.setWidth(availableWidth);
    }

    private class ContentProvider extends TreeNodeContentProvider {
        private Map<Category, List<Issue>> mCategoryToIssues;

        @Override
        public Object[] getElements(Object inputElement) {
            return mRegistry.getCategories().toArray();
        }

        @Override
        public boolean hasChildren(Object element) {
            return element instanceof Category;
        }

        @Override
        public Object[] getChildren(Object parentElement) {
            if (mCategoryToIssues == null) {
                mCategoryToIssues = new HashMap<Category, List<Issue>>();
                List<Issue> issues = mRegistry.getIssues();
                for (Issue issue : issues) {
                    List<Issue> list = mCategoryToIssues.get(issue.getCategory());
                    if (list == null) {
                        list = new ArrayList<Issue>();
                        mCategoryToIssues.put(issue.getCategory(), list);
                    }
                    list.add(issue);
                }
            }

            return mCategoryToIssues.get(parentElement).toArray();
        }

        @Override
        public Object getParent(Object element) {
            return super.getParent(element);
        }
    }

    private class LabelProvider implements ITableLabelProvider, IColorProvider {

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
            if (element instanceof Category) {
                return null;
            }

            if (columnIndex == 1) {
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
                        return sharedImages.getImage(ISharedImages.IMG_ELCL_REMOVE_DISABLED);
                }
            }
            return null;
        }

        public String getColumnText(Object element, int columnIndex) {
            if (element instanceof Category) {
                if (columnIndex == 0) {
                    return ((Category) element).getName();
                } else {
                    return ((Category) element).getExplanation();
                }
            }

            Issue issue = (Issue) element;
            switch (columnIndex) {
                case 0:
                    return issue.getId();
                case 1:
                    return issue.getDescription();
            }

            return null;
        }

        // ---- IColorProvider ----

        public Color getForeground(Object element) {
            if (element instanceof Category) {
                return mTree.getDisplay().getSystemColor(SWT.COLOR_INFO_FOREGROUND);
            }

            if (element instanceof Issue) {
                Issue issue = (Issue) element;
                Severity severity = mSeverities.get(issue);
                if (severity == Severity.IGNORE) {
                    return mTree.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY);
                }
            }

            return null;
        }

        public Color getBackground(Object element) {
            if (element instanceof Category) {
                return mTree.getDisplay().getSystemColor(SWT.COLOR_INFO_BACKGROUND);
            }
            return null;
        }
    }
}