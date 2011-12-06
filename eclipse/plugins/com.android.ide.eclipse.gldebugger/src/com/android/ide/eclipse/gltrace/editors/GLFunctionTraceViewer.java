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

package com.android.ide.eclipse.gltrace.editors;

import com.android.ide.eclipse.gltrace.GLCallFormatter;
import com.android.ide.eclipse.gltrace.TraceFileParserTask;
import com.android.ide.eclipse.gltrace.model.GLCall;
import com.android.ide.eclipse.gltrace.model.GLTrace;
import com.android.ide.eclipse.gltrace.views.GLFramebufferView;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Scale;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IURIEditorInput;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.EditorPart;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Display OpenGL function trace in a tabular view. */
public class GLFunctionTraceViewer extends EditorPart implements ISelectionProvider {
    public static final String ID = "com.android.ide.eclipse.gltrace.GLFunctionTrace"; //$NON-NLS-1$

    private static final String GL_SPECS_FILE = "/entries.in"; //$NON-NLS-1$
    private static final String DEFAULT_FILTER_MESSAGE = "Filter list of OpenGL calls. Accepts Java regexes.";

    // TODO: The thumbnail width & height are constant right now, but should be scaled
    //       based on the size of the viewport/device.
    /** Width of thumbnail images of the framebuffer. */
    private static final int THUMBNAIL_WIDTH = 50;

    /** Height of thumbnail images of the framebuffer. */
    private static final int THUMBNAIL_HEIGHT = 50;

    private final GLCallFormatter mGLCallFormatter =
            new GLCallFormatter(getClass().getResourceAsStream(GL_SPECS_FILE));

    private String mFilePath;
    private Scale mFrameSelectionScale;
    private Spinner mFrameSelectionSpinner;

    private GLTrace mTrace;
    private TableViewer mFrameTableViewer;
    private Text mFilterText;
    private GLCallFilter mGLCallFilter;

    public GLFunctionTraceViewer() {
    }

    @Override
    public void doSave(IProgressMonitor monitor) {
    }

    @Override
    public void doSaveAs() {
    }

    @Override
    public void init(IEditorSite site, IEditorInput input) throws PartInitException {
        // we use a IURIEditorInput to allow opening files not within the workspace
        if (!(input instanceof IURIEditorInput)) {
            throw new PartInitException("GL Function Trace View: unsupported input type.");
        }

        setSite(site);
        setInput(input);
        mFilePath = ((IURIEditorInput) input).getURI().getPath();
    }

    @Override
    public boolean isDirty() {
        return false;
    }

    @Override
    public boolean isSaveAsAllowed() {
        return false;
    }

    @Override
    public void createPartControl(Composite parent) {
        Composite c = new Composite(parent, SWT.NONE);
        c.setLayout(new GridLayout(1, false));
        GridData gd = new GridData(GridData.FILL_BOTH);
        c.setLayoutData(gd);

        createFrameSelectionControls(c);
        createFilterBar(c);
        createFrameTraceView(c);

        getSite().setSelectionProvider(mFrameTableViewer);

        ProgressMonitorDialog dlg = new ProgressMonitorDialog(parent.getShell());
        TraceFileParserTask parser = new TraceFileParserTask(mFilePath, parent.getDisplay(),
                THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
        try {
            dlg.run(true, true, parser);
        } catch (InvocationTargetException e) {
            // exception while parsing, display error to user
            MessageDialog.openError(parent.getShell(),
                    "Error parsing OpenGL Trace File",
                    e.getCause().getMessage());
            return;
        } catch (InterruptedException e) {
            // operation canceled by user, just return
            return;
        }

        mTrace = parser.getTrace();
        refreshUI();
    }

    private void refreshUI() {
        int nFrames = 0;

        if (mTrace != null) {
            nFrames = mTrace.getFrames().size();
        }

        setFrameCount(nFrames);
        selectFrame(1);
    }

    private void createFrameSelectionControls(Composite parent) {
        Composite c = new Composite(parent, SWT.NONE);
        c.setLayout(new GridLayout(3, false));
        GridData gd = new GridData(GridData.FILL_HORIZONTAL);
        c.setLayoutData(gd);

        Label l = new Label(c, SWT.NONE);
        l.setText("Select Frame:");

        mFrameSelectionScale = new Scale(c, SWT.HORIZONTAL);
        mFrameSelectionScale.setMinimum(1);
        mFrameSelectionScale.setMaximum(1);
        mFrameSelectionScale.setSelection(0);
        gd = new GridData(GridData.FILL_HORIZONTAL);
        mFrameSelectionScale.setLayoutData(gd);

        mFrameSelectionScale.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                int selectedFrame = mFrameSelectionScale.getSelection();
                mFrameSelectionSpinner.setSelection(selectedFrame);
                selectFrame(selectedFrame);
            }
        });

        mFrameSelectionSpinner = new Spinner(c, SWT.BORDER);
        mFrameSelectionSpinner.setMinimum(1);
        mFrameSelectionSpinner.setMaximum(1);
        mFrameSelectionSpinner.setSelection(0);
        mFrameSelectionSpinner.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                int selectedFrame = mFrameSelectionSpinner.getSelection();
                mFrameSelectionScale.setSelection(selectedFrame);
                selectFrame(selectedFrame);
            }
        });
    }

    private void setFrameCount(int nFrames) {
        mFrameSelectionScale.setMaximum(nFrames);
        mFrameSelectionSpinner.setMaximum(nFrames);
    }

    private void selectFrame(int selectedFrame) {
        mFrameSelectionScale.setSelection(selectedFrame);
        mFrameSelectionSpinner.setSelection(selectedFrame);

        List<GLCall> glcalls = mTrace.getGLCallsForFrame(selectedFrame - 1);
        mFrameTableViewer.setInput(glcalls);
    }

    private void createFilterBar(Composite parent) {
        Composite c = new Composite(parent, SWT.NONE);
        c.setLayout(new GridLayout(2, false));
        GridData gd = new GridData(GridData.FILL_HORIZONTAL);
        c.setLayoutData(gd);

        Label l = new Label(c, SWT.NONE);
        l.setText("Filter:");

        mFilterText = new Text(c, SWT.BORDER);
        mFilterText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        mFilterText.setMessage(DEFAULT_FILTER_MESSAGE);
        mFilterText.addModifyListener(new ModifyListener() {
            public void modifyText(ModifyEvent e) {
                updateAppliedFilters();
            }
        });
    }

    private void updateAppliedFilters() {
        mGLCallFilter.setFilters(mFilterText.getText().trim());
        mFrameTableViewer.refresh();
    }

    private void createFrameTraceView(Composite parent) {
        final Table table = new Table(parent, SWT.BORDER);
        GridData gd = new GridData(GridData.FILL_BOTH);
        table.setLayoutData(gd);
        table.setLinesVisible(true);

        mFrameTableViewer = new TableViewer(table);

        // single column that has a list of function calls
        TableViewerColumn tvc = new TableViewerColumn(mFrameTableViewer, SWT.NONE);
        TableColumn column = tvc.getColumn();
        column.setText("Function");
        column.setWidth(400);

        tvc.setLabelProvider(new GLFrameLabelProvider());
        mFrameTableViewer.setContentProvider(new GLFrameContentProvider());

        table.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetDefaultSelected(SelectionEvent event) {
                int []indices = table.getSelectionIndices();
                if (indices.length != 1) {
                    return;
                }

                int selectedIndex = indices[0];
                GLCall glCall = (GLCall) table.getItem(selectedIndex).getData();
                displayFB(glCall);
            }
        });

        mGLCallFilter = new GLCallFilter();
        mFrameTableViewer.addFilter(mGLCallFilter);
    }

    private void displayFB(GLCall glCall) {
        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (window == null) {
            return;
        }

        IWorkbenchPage page = window.getActivePage();
        if (page == null) {
            return;
        }

        final GLFramebufferView v = displayFBView(page);
        if (v == null) {
            return;
        }

        v.displayFB(mTrace.getImage(glCall));
    }

    private GLFramebufferView displayFBView(IWorkbenchPage page) {
        IViewPart view = page.findView(GLFramebufferView.ID);
        if (view != null) {
            page.bringToTop(view);
            return (GLFramebufferView) view;
        }

        try {
            return (GLFramebufferView) page.showView(GLFramebufferView.ID);
        } catch (PartInitException e) {
            return null;
        }
    }

    @Override
    public void setFocus() {
        mFrameTableViewer.getTable().setFocus();
    }

    private static class GLFrameContentProvider implements IStructuredContentProvider {
        public void dispose() {
        }

        public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
        }

        public Object[] getElements(Object model) {
            if (model instanceof List<?>) {
                return ((List<?>) model).toArray();
            }

            return null;
        }
    }

    private class GLFrameLabelProvider extends ColumnLabelProvider {
        @Override
        public String getText(Object element) {
            if (element instanceof GLCall) {
                return formatGLCall((GLCall) element);
            }
            return element.toString();
        }
    }

    private String formatGLCall(GLCall c) {
        String result = mGLCallFormatter.formatGLCall(c);
        if (result == null) {
            result = c.getFunction().toString();
        }

        return result;
    }

    private static class GLCallFilter extends ViewerFilter {
        private final List<Pattern> mPatterns = new ArrayList<Pattern>();

        public void setFilters(String filter) {
            mPatterns.clear();

            // split the user input into multiple regexes
            // we assume that the regexes are OR'ed together i.e., all text that matches
            // any one of the regexes will be displayed
            for (String regex : filter.split(" ")) {
                mPatterns.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
            }
        }

        @Override
        public boolean select(Viewer viewer, Object parentElement, Object element) {
            GLCall call = (GLCall) element;
            String text = call.getFunction().toString();

            if (mPatterns.size() == 0) {
                // match if there are no regex filters
                return true;
            }

            for (Pattern p : mPatterns) {
                Matcher matcher = p.matcher(text);
                if (matcher.find()) {
                    // match if atleast one of the regexes matches this text
                    return true;
                }
            }

            return false;
        }
    }

    public void addSelectionChangedListener(ISelectionChangedListener listener) {
        if (mFrameTableViewer != null) {
            mFrameTableViewer.addSelectionChangedListener(listener);
        }
    }

    public ISelection getSelection() {
        if (mFrameTableViewer != null) {
            return mFrameTableViewer.getSelection();
        } else {
            return null;
        }
    }

    public void removeSelectionChangedListener(ISelectionChangedListener listener) {
        if (mFrameTableViewer != null) {
            mFrameTableViewer.removeSelectionChangedListener(listener);
        }
    }

    public void setSelection(ISelection selection) {
        if (mFrameTableViewer != null) {
            mFrameTableViewer.setSelection(selection);
        }
    }

    public GLTrace getTrace() {
        return mTrace;
    }
}
