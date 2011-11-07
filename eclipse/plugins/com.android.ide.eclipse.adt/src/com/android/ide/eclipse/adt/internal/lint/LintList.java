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

import com.android.ide.eclipse.adt.AdtConstants;
import com.android.ide.eclipse.adt.AdtUtils;
import com.android.ide.eclipse.adt.internal.editors.IconFactory;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.GraphicalEditorPart;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.LayoutActionBar;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.detector.api.Issue;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IMarkerDelta;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.progress.IWorkbenchSiteProgressService;
import org.eclipse.ui.progress.WorkbenchJob;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A table widget which shows a list of lint warnings for an underlying
 * {@link IResource} such as a file, a project, or a list of projects.
 */
class LintList extends Composite implements IResourceChangeListener, ControlListener {
    private static final int LINE_COLUMN_WIDTH = 50;
    private static final int FILENAME_COLUMN_WIDTH = 150;
    private static final Object UPDATE_MARKERS_FAMILY = new Object();

    private final IWorkbenchPartSite mSite;
    private final TableViewer mTableViewer;
    private final Table mTable;
    private TableColumn mFileColumn;
    private TableColumn mLineColumn;
    private TableColumn mMessageColumn;

    private List<IResource> mResources;
    private boolean mShowFileNames;
    private int mErrorCount;
    private int mWarningCount;
    private final UpdateMarkersJob mUpdateMarkersJob = new UpdateMarkersJob();

    LintList(IWorkbenchPartSite site, Composite parent) {
        super(parent, SWT.NONE);
        mSite = site;

        GridLayout gridLayout = new GridLayout(1, false);
        gridLayout.marginWidth = 0;
        gridLayout.marginHeight = 0;
        setLayout(gridLayout);

        mTableViewer = new TableViewer(this, SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI);
        mTable = mTableViewer.getTable();
        mTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));

        TableViewerColumn messageViewerColumn = new TableViewerColumn(mTableViewer, SWT.FILL);
        mMessageColumn = messageViewerColumn.getColumn();
        mMessageColumn.setWidth(100);
        mMessageColumn.setText("Message");

        TableViewerColumn fileViewerColumn = new TableViewerColumn(mTableViewer, SWT.NONE);
        mFileColumn = fileViewerColumn.getColumn();
        mFileColumn.setWidth(100);
        mFileColumn.setText("File");

        TableViewerColumn lineViewerColumn = new TableViewerColumn(mTableViewer, SWT.NONE);
        mLineColumn = lineViewerColumn.getColumn();
        mLineColumn.setWidth(100);
        mLineColumn.setText("Line");
        mLineColumn.setAlignment(SWT.RIGHT);

        mTableViewer.setContentProvider(new ContentProvider());
        mTableViewer.setLabelProvider(new LabelProvider());

        mTable.setLinesVisible(true);
        mTable.setHeaderVisible(true);
        mTable.addControlListener(this);

        ResourcesPlugin.getWorkspace().addResourceChangeListener(
                this,
                IResourceChangeEvent.POST_CHANGE
                        | IResourceChangeEvent.PRE_BUILD
                        | IResourceChangeEvent.POST_BUILD);
    }

    private void updateColumnWidths() {
        Rectangle r = mTable.getClientArea();
        int availableWidth = r.width;

        mLineColumn.setWidth(LINE_COLUMN_WIDTH);
        availableWidth -= LINE_COLUMN_WIDTH;

        if (mShowFileNames) {
            mFileColumn.setWidth(FILENAME_COLUMN_WIDTH);
            availableWidth -= FILENAME_COLUMN_WIDTH;
        } else {
            mFileColumn.setWidth(0);
            // Account for a little bit of space required for empty column
            availableWidth -= 2;
        }

        // Name absorbs everything else
        mMessageColumn.setWidth(availableWidth);
    }

    public void setResources(List<IResource> resources) {
        mResources = resources;
        List<IMarker> markerList = getMarkers();
        mTableViewer.setInput(markerList);

        // Show filenames unless we're just showing errors for a single file
        mShowFileNames = resources == null || resources.size() != 1 ||
                !(resources.get(0) instanceof IFile);

        if (mTable.getItemCount() > 0) {
            mTable.select(0);
        }

        updateColumnWidths(); // in case mShowFileNames changed
    }

    private List<IMarker> getMarkers() {
        mErrorCount = mWarningCount = 0;
        List<IMarker> markerList = new ArrayList<IMarker>();
        if (mResources != null) {
            for (IResource resource : mResources) {
                IMarker[] markers = EclipseLintClient.getMarkers(resource);
                for (IMarker marker : markers) {
                    markerList.add(marker);
                    int severity = marker.getAttribute(IMarker.SEVERITY, 0);
                    if (severity == IMarker.SEVERITY_ERROR) {
                        mErrorCount++;
                    } else if (severity == IMarker.SEVERITY_WARNING) {
                        mWarningCount++;
                    }
                }
            }

            final IssueRegistry registry = EclipseLintClient.getRegistry();
            Collections.sort(markerList, new Comparator<IMarker>() {

                public int compare(IMarker marker1, IMarker marker2) {
                    // Sort by priority, then by category, then by id,
                    // then by file, then by line
                    String id1 = EclipseLintClient.getId(marker1);
                    String id2 = EclipseLintClient.getId(marker2);
                    if (id1 == null || id2 == null) {
                        return marker1.getResource().getName().compareTo(
                                marker2.getResource().getName());
                    }
                    Issue issue1 = registry.getIssue(id1);
                    Issue issue2 = registry.getIssue(id1);
                    if (issue1 == null || issue2 == null) {
                        // Unknown issue? Can happen if you have used a thirdparty detector
                        // which is no longer available but which left a persistent marker behind
                        return id1.compareTo(id2);
                    }
                    // DECREASING priority order
                    int priorityDelta = issue2.getPriority() - issue1.getPriority();
                    if (priorityDelta != 0) {
                        return priorityDelta;
                    }
                    int categoryDelta = issue1.getCategory().compareTo(issue2.getCategory());
                    if (categoryDelta != 0) {
                        return categoryDelta;
                    }
                    int idDelta = id1.compareTo(id2);
                    if (idDelta != -1) {
                        return idDelta;
                    }
                    int fileDelta = marker1.getResource().getName().compareTo(
                            marker2.getResource().getName());
                    if (fileDelta != -1) {
                        return fileDelta;
                    }
                    return marker1.getAttribute(IMarker.LINE_NUMBER, 0)
                            - marker2.getAttribute(IMarker.LINE_NUMBER, 0);
                }

            });
        }
        return markerList;
    }

    public int getErrorCount() {
        return mErrorCount;
    }

    public int getWarningCount() {
        return mWarningCount;
    }

    @Override
    protected void checkSubclass() {
        // Disable the check that prevents subclassing of SWT components
    }

    public void addSelectionListener(SelectionListener listener) {
        mTable.addSelectionListener(listener);
    }

    public void refresh() {
        mTableViewer.refresh();
    }

    public List<IMarker> getSelectedMarkers() {
        int[] indices = mTable.getSelectionIndices();
        List<IMarker> markers = new ArrayList<IMarker>(indices.length);
        for (int index : indices) {
            TableItem tableItem = mTable.getItem(index);
            IMarker marker = (IMarker) tableItem.getData();
            markers.add(marker);
        }

        return markers;
    }

    @Override
    public void dispose() {
        cancelJobs();
        ResourcesPlugin.getWorkspace().removeResourceChangeListener(this);
        super.dispose();
    }

    private class ContentProvider implements IStructuredContentProvider {
        public Object[] getElements(Object inputElement) {
            if (inputElement == null) {
                return new IMarker[0];
            }

            @SuppressWarnings("unchecked")
            List<IMarker> list = (List<IMarker>) inputElement;
            return list.toArray(new IMarker[list.size()]);
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
                    if (LintFix.hasFix(EclipseLintClient.getId(marker))) {
                        return IconFactory.getInstance().getIcon("quickfix_error");   //$NON-NLS-1$
                    }
                    return sharedImages.getImage(ISharedImages.IMG_OBJS_ERROR_TSK);
                case IMarker.SEVERITY_WARNING:
                    if (LintFix.hasFix(EclipseLintClient.getId(marker))) {
                        return IconFactory.getInstance().getIcon("quickfix_warning"); //$NON-NLS-1$
                    }
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
                    case 1:
                        return marker.getResource().getName();
                    case 2: {
                        int line = marker.getAttribute(IMarker.LINE_NUMBER, 0);
                        return Integer.toString(line);
                    }
                }
            } catch (CoreException e) {
                // Pass: If markers are being deleted at the same time, just return ""
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

    TableViewer getTableViewer() {
        return mTableViewer;
    }

    // ---- Implements IResourceChangeListener ----

    public void resourceChanged(IResourceChangeEvent event) {
        if (mResources == null) {
            return;
        }
        IMarkerDelta[] deltas = event.findMarkerDeltas(AdtConstants.MARKER_LINT, true);
        if (deltas.length > 0) {
            // Update immediately for POST_BUILD events, otherwise do an unconditional
            // update after 30 seconds. This matches the logic in Eclipse's ProblemView
            // (see the MarkerView class).
            if (event.getType() == IResourceChangeEvent.POST_BUILD) {
                cancelJobs();
                getProgressService().schedule(mUpdateMarkersJob, 100);
            } else {
                IWorkbenchSiteProgressService progressService = getProgressService();
                if (progressService == null) {
                    mUpdateMarkersJob.schedule(30000);
                } else {
                    getProgressService().schedule(mUpdateMarkersJob, 30000);
                }
            }
        }
    }

    // ---- Updating Markers ----

    private void cancelJobs() {
        mUpdateMarkersJob.cancel();
    }

    protected IWorkbenchSiteProgressService getProgressService() {
        if (mSite != null) {
            Object siteService = mSite.getAdapter(IWorkbenchSiteProgressService.class);
            if (siteService != null) {
                return (IWorkbenchSiteProgressService) siteService;
            }
        }
        return null;
    }

    private class UpdateMarkersJob extends WorkbenchJob {
        UpdateMarkersJob() {
            super("Updating Lint Markers");
            setSystem(true);
        }

        @Override
        public IStatus runInUIThread(IProgressMonitor monitor) {
            if (mTable.isDisposed()) {
                return Status.CANCEL_STATUS;
            }
            mTableViewer.setInput(null);
            List<IMarker> markerList = getMarkers();
            if (markerList.size() == 0) {
                IEditorPart active = AdtUtils.getActiveEditor();
                if (active instanceof LayoutEditor) {
                    LayoutEditor editor = (LayoutEditor) active;
                    GraphicalEditorPart g =
                            editor.getGraphicalEditor();
                    LayoutActionBar bar = g.getLayoutActionBar();
                    bar.updateErrorIndicator();
                }
            }
            // Trigger selection update
            Event updateEvent = new Event();
            updateEvent.widget = mTable;
            mTable.notifyListeners(SWT.Selection, updateEvent);
            mTableViewer.setInput(markerList);
            mTableViewer.refresh();

            return Status.OK_STATUS;
        }

        @Override
        public boolean shouldRun() {
            // Do not run if the change came in before there is a viewer
            return PlatformUI.isWorkbenchRunning();
        }

        @Override
        public boolean belongsTo(Object family) {
            return UPDATE_MARKERS_FAMILY == family;
        }
    }

    /**
     * Returns the list of resources being shown in the list
     *
     * @return the list of resources being shown in this composite
     */
    public List<IResource> getResources() {
        return mResources;
    }

    // ---- Implements ControlListener

    public void controlMoved(ControlEvent e) {
    }

    public void controlResized(ControlEvent e) {
        updateColumnWidths();
    }
}
