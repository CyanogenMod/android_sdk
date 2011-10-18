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
package com.android.ide.eclipse.adt.internal.lint;

import static com.android.ide.eclipse.adt.internal.lint.LintEclipseContext.MARKER_CHECKID_PROPERTY;

import com.android.ide.eclipse.adt.AdtConstants;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.AdtUtils;
import com.android.ide.eclipse.adt.internal.editors.AndroidXmlEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.GraphicalEditorPart;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.LayoutActionBar;
import com.android.tools.lint.api.DetectorRegistry;
import com.android.tools.lint.detector.api.Issue;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IMarkerDelta;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocument;

@SuppressWarnings("restriction") // WST DOM access
class LintListDialog extends TitleAreaDialog implements SelectionListener,
        IResourceChangeListener {
    private static final String PROJECT_LOGO_LARGE = "icons/android-64.png"; //$NON-NLS-1$
    private IFile mFile;
    private Table mTable;
    private Button mFixButton;
    private Button mIgnoreButton;
    private Button mShowButton;
    private Text mDetailsText;
    private Button mIgnoreTypeButton;
    private TableViewer mTableViewer;

    LintListDialog(Shell parentShell, IFile file) {
        super(parentShell);
        this.mFile = file;
    }

    @Override
    protected Control createContents(Composite parent) {
      Control contents = super.createContents(parent);
      setTitle("Lint Warnings in Layout");
      setMessage("Lint Errors found for the current layout:");
      setTitleImage(AdtPlugin.getImageDescriptor(PROJECT_LOGO_LARGE).createImage());
      return contents;
    }

    @SuppressWarnings("unused") // SWT constructors have side effects, they are not unused
    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        Composite container = new Composite(area, SWT.NONE);
        container.setLayoutData(new GridData(GridData.FILL_BOTH));

        container.setLayout(new GridLayout(2, false));
        mTableViewer = new TableViewer(container, SWT.BORDER | SWT.MULTI);
        mTable = mTableViewer.getTable();
        mTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 5));

        TableViewerColumn messageViewerColumn = new TableViewerColumn(mTableViewer, SWT.FILL);
        final TableColumn messageColumn = messageViewerColumn.getColumn();
        messageColumn.setWidth(100);
        messageColumn.setText("Message");

        TableViewerColumn lineViewerColumn = new TableViewerColumn(mTableViewer, SWT.NONE);
        final TableColumn lineColumn = lineViewerColumn.getColumn();
        lineColumn.setWidth(100);
        lineColumn.setText("Line");
        lineColumn.setAlignment(SWT.RIGHT);

        mTableViewer.setContentProvider(new ContentProvider());
        mTableViewer.setLabelProvider(new LabelProvider());

        mShowButton = new Button(container, SWT.NONE);
        mShowButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        mShowButton.setText("Show");
        mShowButton.addSelectionListener(this);

        mIgnoreButton = new Button(container, SWT.NONE);
        mIgnoreButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        mIgnoreButton.setText("Ignore");
        mIgnoreButton.setEnabled(false);
        mIgnoreButton.addSelectionListener(this);

        mIgnoreTypeButton = new Button(container, SWT.NONE);
        mIgnoreTypeButton.setText("Ignore Type");
        mIgnoreTypeButton.addSelectionListener(this);

        mFixButton = new Button(container, SWT.NONE);
        mFixButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        mFixButton.setText("Fix");
        mFixButton.setEnabled(false);
        mFixButton.addSelectionListener(this);

        new Label(container, SWT.NONE);

        mDetailsText = new Text(container, SWT.BORDER | SWT.READ_ONLY | SWT.WRAP
                | SWT.V_SCROLL | SWT.MULTI);
        GridData gdText = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
        gdText.heightHint = 80;
        mDetailsText.setLayoutData(gdText);

        IMarker[] markers = LintEclipseContext.getMarkers(mFile);
        mTableViewer.setInput(markers);

        mTable.setLinesVisible(true);
        mTable.setHeaderVisible(true);
        new Label(container, SWT.NONE);

        mTable.addSelectionListener(this);

        // Add a listener to resize the column to the full width of the
        // table
        mTable.addControlListener(new ControlAdapter() {
            @Override
            public void controlResized(ControlEvent e) {
                Rectangle r = mTable.getClientArea();
                int availableWidth = r.width;

                int categoryFixedSize = 50;
                lineColumn.setWidth(categoryFixedSize);
                availableWidth -= categoryFixedSize;

                // Name absorbs everything else
                messageColumn.setWidth(availableWidth);
            }
        });

        if (mTable.getItemCount() > 0) {
            mTable.select(0);
            updateSelectionState();
        }

        ResourcesPlugin.getWorkspace().addResourceChangeListener(
                this,
                IResourceChangeEvent.POST_CHANGE
                        | IResourceChangeEvent.PRE_BUILD
                        | IResourceChangeEvent.POST_BUILD);


        return area;
    }

    /**
     * Create contents of the button bar.
     */
    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
    }

    /**
     * Return the initial size of the dialog.
     */
    @Override
    protected Point getInitialSize() {
        return new Point(600, 400);
    }

    private void selectMarker(IMarker marker) {
        if (marker == null) {
            mDetailsText.setText(""); //$NON-NLS-1$
            return;
        }

        String id = getId(marker);
        DetectorRegistry registry = LintEclipseContext.getRegistry();
        Issue issue = registry.getIssue(id);
        String summary = issue.getDescription();
        String explanation = issue.getExplanation();

        StringBuilder sb = new StringBuilder(summary.length() + explanation.length() + 20);
        sb.append(summary);
        sb.append('\n').append('\n');
        sb.append(explanation);
        mDetailsText.setText(sb.toString());
    }

    private String getId(IMarker marker) {
        try {
            return (String) marker.getAttribute(MARKER_CHECKID_PROPERTY);
        } catch (CoreException e) {
            AdtPlugin.log(e, null);
        }

        return null;
    }

    private void showMarker(IMarker marker) {
        IRegion region = null;
        try {
            int start = marker.getAttribute(IMarker.CHAR_START, -1);
            int end = marker.getAttribute(IMarker.CHAR_END, -1);
            if (start >= 0 && end >= 0) {
                region = new org.eclipse.jface.text.Region(start, end - start);
            }

            AdtPlugin.openFile(mFile, region, true /* showEditorTab */);
        } catch (PartInitException ex) {
            AdtPlugin.log(ex, null);
        }
    }

    @Override
    public boolean close() {
        ResourcesPlugin.getWorkspace().removeResourceChangeListener(this);

        return super.close();
    }

    // ---- Implements SelectionListener ----

    public void widgetSelected(SelectionEvent e) {
        Object source = e.getSource();
        if (source == mTable) {
            // Enable/disable buttons
            updateSelectionState();
        } else if (source == mShowButton) {
            int index = mTable.getSelectionIndex();
            if (index != -1) {
                IMarker marker = (IMarker) mTable.getItem(index).getData();
                showMarker(marker);
            }
        } else if (source == mFixButton) {
            int[] indices = mTable.getSelectionIndices();
            for (int index : indices) {
                TableItem tableItem = mTable.getItem(index);
                IMarker marker = (IMarker) tableItem.getData();
                LintFix fix = LintFix.getFix(getId(marker), marker);
                IEditorPart editor = AdtUtils.getActiveEditor();
                if (editor instanceof AndroidXmlEditor) {
                    @SuppressWarnings("restriction")
                    IStructuredDocument doc = ((AndroidXmlEditor) editor).getStructuredDocument();
                    fix.apply(doc);
                    if (fix.needsFocus()) {
                        close();
                    }
                } else {
                    AdtPlugin.log(IStatus.ERROR, "Did not find associated editor to apply fix");
                }
            }
        } else if (source == mIgnoreTypeButton) {
            int[] indices = mTable.getSelectionIndices();
            for (int index : indices) {
                TableItem tableItem = mTable.getItem(index);
                IMarker marker = (IMarker) tableItem.getData();
                String id = getId(marker);
                if (id != null) {
                    LintFixGenerator.suppressDetector(id);
                }
            }
            mTableViewer.setInput(null);
            mTableViewer.refresh();
        }
    }

    private void updateSelectionState() {
        TableItem[] selection = mTable.getSelection();

        if (selection.length == 1) {
            selectMarker((IMarker) selection[0].getData());
        } else {
            selectMarker(null);
        }

        boolean canFix = selection.length > 0;
        for (TableItem item : selection) {
            IMarker marker = (IMarker) item.getData();
            if (!LintFix.hasFix(getId(marker))) {
                canFix = false;
                break;
            }

            // Some fixes cannot be run in bulk
            if (selection.length > 1) {
                LintFix fix = LintFix.getFix(getId(marker), marker);
                if (!fix.isBulkCapable()) {
                    canFix = false;
                    break;
                }
            }
        }

        mFixButton.setEnabled(canFix);
    }

    public void widgetDefaultSelected(SelectionEvent e) {
        Object source = e.getSource();
        if (source == mTable) {
            // Jump to editor
            TableItem item = (TableItem) e.item;
            IMarker marker = (IMarker) item.getData();
            showMarker(marker);
            close();
        }
    }

    // ---- Implements IResourceChangeListener ----

    public void resourceChanged(IResourceChangeEvent event) {
        IMarkerDelta[] deltas = event.findMarkerDeltas(AdtConstants.MARKER_LINT, true);
        if (deltas.length > 0) {
            for (IMarkerDelta delta : deltas) {
                if (delta.getResource().equals(mFile)) {
                    Shell shell = LintListDialog.this.getShell();
                    if (shell == null) {
                        return;
                    }
                    Display display = shell.getDisplay();
                    if (display == null) {
                        return;
                    }
                    display.asyncExec(new Runnable() {
                        public void run() {
                            if (mTable.isDisposed()) {
                                return;
                            }
                            mTableViewer.setInput(null);
                            IMarker[] markers = LintEclipseContext.getMarkers(mFile);
                            if (markers.length == 0) {
                                IEditorPart active = AdtUtils.getActiveEditor();
                                if (active instanceof LayoutEditor) {
                                    LayoutEditor editor = (LayoutEditor) active;
                                    if (mFile.equals(editor.getInputFile())) {
                                        GraphicalEditorPart g = editor.getGraphicalEditor();
                                        LayoutActionBar bar = g.getLayoutActionBar();
                                        bar.updateErrorIndicator();
                                    }
                                }

                                close();
                                return;
                            }
                            mTableViewer.setInput(markers);
                            mTableViewer.refresh();
                        }
                    });
                    return;
                }
            }
        }
    }


    private class ContentProvider implements IStructuredContentProvider {
        public Object[] getElements(Object inputElement) {
            if (inputElement == null) {
                return new IMarker[0];
            }

            return (IMarker[]) inputElement;
        }

        public void dispose() {
        }

        public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
        }
    }

    private class LabelProvider implements ITableLabelProvider {
        public Image getColumnImage(Object element, int columnIndex) {
            if (columnIndex != 0) {
                return null;
            }

            IMarker marker = (IMarker) element;
            int severity = marker.getAttribute(IMarker.SEVERITY, 0);
            ISharedImages sharedImages = PlatformUI.getWorkbench().getSharedImages();
            switch (severity) {
                case IMarker.SEVERITY_ERROR:
                    return sharedImages.getImage(ISharedImages.IMG_OBJS_ERROR_TSK);
                case IMarker.SEVERITY_WARNING:
                    return sharedImages.getImage(ISharedImages.IMG_OBJS_WARN_TSK);
                case IMarker.SEVERITY_INFO:
                    return sharedImages.getImage(ISharedImages.IMG_OBJS_INFO_TSK);
                default:
                    return null;
            }
        }

        public String getColumnText(Object element, int columnIndex) {
            IMarker marker = (IMarker) element;
            try {
                switch (columnIndex) {
                    case 0:
                        return (String) marker.getAttribute(IMarker.MESSAGE);
                    case 1: {
                        int line = marker.getAttribute(IMarker.LINE_NUMBER, 0);
                        return Integer.toString(line);
                    }
                }
            } catch (CoreException e) {
                AdtPlugin.log(e, null);
            }

            return ""; //$NON-NLS-1$
        }

        public void addListener(ILabelProviderListener listener) {
        }

        public void removeListener(ILabelProviderListener listener) {
        }

        public boolean isLabelProperty(Object element, String property) {
            return false;
        }

        public void dispose() {
        }
    }
}
