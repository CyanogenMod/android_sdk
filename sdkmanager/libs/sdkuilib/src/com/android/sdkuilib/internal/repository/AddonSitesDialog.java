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

import com.android.sdklib.SdkConstants;
import com.android.sdklib.internal.repository.SdkAddonSource;
import com.android.sdklib.internal.repository.SdkSource;
import com.android.sdklib.internal.repository.SdkSourceCategory;
import com.android.sdklib.internal.repository.SdkSources;

import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;

import java.util.Arrays;

public class AddonSitesDialog extends Dialog {

    /**
     * Min Y location for dialog. Need to deal with the menu bar on mac os.
     */
    private final static int MIN_Y = SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_DARWIN ?
            20 : 0;


    /** Last dialog size for this session. */
    private static Point sLastSize;

    private final UpdaterData mUpdaterData;
    private boolean mChanged;

    private Shell mShell;
    private Table mTable;
    private TableViewer mTableViewer;
    private Button mButtonNew;
    private Button mButtonDelete;
    private Button mButtonClose;
    private Label mlabel;
    private Button mButtonEdit;
    private TableColumn mColumnUrl;

    /**
     * Create the dialog.
     *
     * @param parent The parent's shell
     */
    public AddonSitesDialog(Shell parent, UpdaterData updaterData) {
        super(parent, SWT.NONE);
        mUpdaterData = updaterData;
        setText("Add-on Sites");
    }

    /**
     * Open the dialog.
     *
     * @return True if anything was changed.
     */
    public boolean open() {
        createContents();
        positionShell();
        postCreate();
        mShell.open();
        mShell.layout();
        Display display = getParent().getDisplay();
        while (!mShell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }

        return mChanged;
    }

    /**
     * Create contents of the dialog.
     */
    private void createContents() {
        mShell = new Shell(getParent(), SWT.DIALOG_TRIM | SWT.RESIZE | SWT.APPLICATION_MODAL);
        mShell.setMinimumSize(new Point(450, 300));
        mShell.setSize(450, 300);
        mShell.setText(getText());
        GridLayout gl_shell = new GridLayout();
        gl_shell.numColumns = 2;
        mShell.setLayout(gl_shell);

        mlabel = new Label(mShell, SWT.NONE);
        mlabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
        mlabel.setText(
            "This dialog lets you manage the URLs of external add-on sites to be used.\n" +
            "\n" +
            "Add-on sites can provide new add-ons or \"user\" packages.\n" +
            "They cannot provide standard Android platforms, docs or samples packages.\n" +
            "Adding a URL here will not allow you to clone an official Android repository."
        );

        mTableViewer = new TableViewer(mShell, SWT.BORDER | SWT.FULL_SELECTION);
        mTableViewer.addPostSelectionChangedListener(new ISelectionChangedListener() {
            public void selectionChanged(SelectionChangedEvent event) {
                on_TableViewer_selectionChanged(event);
            }
        });
        mTable = mTableViewer.getTable();
        mTable.setLinesVisible(false);
        mTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseUp(MouseEvent e) {
                on_Table_mouseUp(e);
            }
        });
        mTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 5));

        TableViewerColumn tableViewerColumn = new TableViewerColumn(mTableViewer, SWT.NONE);
        mColumnUrl = tableViewerColumn.getColumn();
        mColumnUrl.setWidth(100);
        mColumnUrl.setText("New Column");

        mButtonNew = new Button(mShell, SWT.NONE);
        mButtonNew.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                newOrEdit(false /*isEdit*/);
            }
        });
        mButtonNew.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        mButtonNew.setText("New...");

        mButtonEdit = new Button(mShell, SWT.NONE);
        mButtonEdit.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                newOrEdit(true /*isEdit*/);
            }
        });
        mButtonEdit.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        mButtonEdit.setText("Edit...");

        mButtonDelete = new Button(mShell, SWT.NONE);
        mButtonDelete.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                on_ButtonDelete_widgetSelected(e);
            }
        });
        mButtonDelete.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        mButtonDelete.setText("Delete...");
        new Label(mShell, SWT.NONE);

        mButtonClose = new Button(mShell, SWT.NONE);
        mButtonClose.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                on_ButtonClose_widgetSelected(e);
            }
        });
        mButtonClose.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, false, false, 1, 1));
        mButtonClose.setText("Close");

        adjustColumnsWidth(mTable, mColumnUrl);
    }

    /**
     * Adds a listener to adjust the column width when the parent is resized.
     */
    private void adjustColumnsWidth(final Table table, final TableColumn column0) {
        // Add a listener to resize the column to the full width of the table
        table.addControlListener(new ControlAdapter() {
            @Override
            public void controlResized(ControlEvent e) {
                Rectangle r = table.getClientArea();
                column0.setWidth(r.width * 100 / 100); // 100%
            }
        });
    }

    /**
     * Centers the dialog in its parent shell.
     */
    private void positionShell() {
        // Centers the dialog in its parent shell
        Shell child = mShell;
        Shell parent = getParent();
        if (child != null && parent != null) {

            // get the parent client area with a location relative to the display
            Rectangle parentArea = parent.getClientArea();
            Point parentLoc = parent.getLocation();
            int px = parentLoc.x;
            int py = parentLoc.y;
            int pw = parentArea.width;
            int ph = parentArea.height;

            // Reuse the last size if there's one, otherwise use the default
            Point childSize = sLastSize != null ? sLastSize : child.getSize();
            int cw = childSize.x;
            int ch = childSize.y;

            int x = px + (pw - cw) / 2;
            if (x < 0) x = 0;

            int y = py + (ph - ch) / 2;
            if (y < MIN_Y) y = MIN_Y;

            child.setLocation(x, y);
            child.setSize(cw, ch);
        }
    }

    private void newOrEdit(final boolean isEdit) {
        SdkSources sources = mUpdaterData.getSources();
        final SdkSource[] knownSources = sources.getAllSources();
        String title = isEdit ? "Edit Add-on Site URL" : "Add Add-on Site URL";
        String msg = "Please enter the URL of the addon.xml:";
        IStructuredSelection sel = (IStructuredSelection) mTableViewer.getSelection();
        final String initialValue = !isEdit || sel.isEmpty() ? null :
                                                               sel.getFirstElement().toString();

        if (isEdit && initialValue == null) {
            // Edit with no actual value is not supposed to happen. Ignore this case.
            return;
        }

        InputDialog dlg = new InputDialog(mShell, title, msg, initialValue, new IInputValidator() {
            public String isValid(String newText) {

                newText = newText == null ? null : newText.trim();

                if (newText == null || newText.length() == 0) {
                    return "Error: URL field is empty. Please enter a URL.";
                }

                // A URL should have one of the following prefixes
                if (!newText.startsWith("file://") &&                 //$NON-NLS-1$
                        !newText.startsWith("ftp://") &&              //$NON-NLS-1$
                        !newText.startsWith("http://") &&             //$NON-NLS-1$
                        !newText.startsWith("https://")) {            //$NON-NLS-1$
                    return "Error: The URL must start by one of file://, ftp://, http:// or https://";
                }

                if (isEdit && newText.equals(initialValue)) {
                    // Edited value hasn't changed. This isn't an error.
                    return null;
                }

                // Reject URLs that are already in the source list.
                // URLs are generally case-insensitive (except for file:// where it all depends
                // on the current OS so we'll ignore this case.)
                for (SdkSource s : knownSources) {
                    if (newText.equalsIgnoreCase(s.getUrl())) {
                        return "Error: This site is already listed.";
                    }
                }

                return null;
            }
        });

        if (dlg.open() == Window.OK) {
            String url = dlg.getValue().trim();

            if (!url.equals(initialValue)) {
                if (isEdit && initialValue != null) {
                    // Remove the old value before we add the new one, which is we just
                    // asserted will be different.
                    for (SdkSource source : sources.getSources(SdkSourceCategory.USER_ADDONS)) {
                        if (initialValue.equals(source.getUrl())) {
                            sources.remove(source);
                            break;
                        }
                    }

                }

                // create the source, store it and update the list
                SdkAddonSource newSource = new SdkAddonSource(url, null/*uiName*/);
                sources.add(
                        SdkSourceCategory.USER_ADDONS,
                        newSource);
                mChanged = true;
                loadList();

                // select the new source
                IStructuredSelection newSel = new StructuredSelection(newSource);
                mTableViewer.setSelection(newSel, true /*reveal*/);
            }
        }
    }

    private void on_ButtonDelete_widgetSelected(SelectionEvent e) {
        IStructuredSelection sel = (IStructuredSelection) mTableViewer.getSelection();
        String selectedUrl = sel.isEmpty() ? null : sel.getFirstElement().toString();

        if (selectedUrl == null) {
            return;
        }

        MessageBox mb = new MessageBox(mShell,
                SWT.YES | SWT.NO | SWT.ICON_QUESTION | SWT.APPLICATION_MODAL);
        mb.setText("Delete add-on site");
        mb.setMessage(String.format("Do you want to delete the URL %1$s?", selectedUrl));
        if (mb.open() == SWT.YES) {
            SdkSources sources = mUpdaterData.getSources();
            for (SdkSource source : sources.getSources(SdkSourceCategory.USER_ADDONS)) {
                if (selectedUrl.equals(source.getUrl())) {
                    sources.remove(source);
                    mChanged = true;
                    loadList();
                }
            }
        }
    }

    private void on_ButtonClose_widgetSelected(SelectionEvent e) {
        mShell.close();
    }

    private void on_Table_mouseUp(MouseEvent e) {
        Point p = new Point(e.x, e.y);
        if (mTable.getItem(p) == null) {
            mTable.deselectAll();
            on_TableViewer_selectionChanged(null /*event*/);
        }
    }

    private void on_TableViewer_selectionChanged(SelectionChangedEvent event) {
        ISelection sel = mTableViewer.getSelection();
        mButtonDelete.setEnabled(!sel.isEmpty());
        mButtonEdit.setEnabled(!sel.isEmpty());
    }

    private void postCreate() {
        // initialize the list
        mTableViewer.setLabelProvider(new LabelProvider());
        mTableViewer.setContentProvider(new SourcesContentProvider());
        loadList();
    }

    private void loadList() {
        if (mUpdaterData != null) {
            SdkSource[] knownSources =
                mUpdaterData.getSources().getSources(SdkSourceCategory.USER_ADDONS);
            Arrays.sort(knownSources);

            ISelection oldSelection = mTableViewer.getSelection();

            mTableViewer.setInput(knownSources);
            mTableViewer.refresh();
            // initialize buttons' state that depend on the list
            on_TableViewer_selectionChanged(null /*event*/);

            if (oldSelection != null && !oldSelection.isEmpty()) {
                mTableViewer.setSelection(oldSelection, true /*reveal*/);
            }
        }
    }

    private static class SourcesContentProvider implements IStructuredContentProvider {

        public void dispose() {
            // pass
        }

        public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
            // pass
        }

        public Object[] getElements(Object inputElement) {
            if (inputElement instanceof SdkSource[]) {
                return (Object[]) inputElement;
            } else {
                return new Object[0];
            }
        }
    }
}
