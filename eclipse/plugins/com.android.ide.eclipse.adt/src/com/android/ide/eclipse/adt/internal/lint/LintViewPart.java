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

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.AdtUtils;
import com.android.ide.eclipse.adt.internal.editors.AndroidXmlEditor;
import com.android.ide.eclipse.adt.internal.editors.IconFactory;
import com.android.ide.eclipse.adt.internal.project.BaseProjectHelper;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.IJobChangeListener;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocument;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Eclipse View which shows lint warnings for the current project
 */
@SuppressWarnings("restriction") // DOM model
public class LintViewPart extends ViewPart implements SelectionListener, IJobChangeListener {
    /** The view id for this view part */
    public static final String ID = "com.android.ide.eclipse.adt.internal.lint.LintViewPart"; //$NON-NLS-1$
    private static final String QUICKFIX_DISABLED_ICON = "quickfix-disabled"; //$NON-NLS-1$
    private static final String QUICKFIX_ICON = "quickfix";                   //$NON-NLS-1$
    private static final String REFRESH_ICON = "refresh";                     //$NON-NLS-1$

    private LintList mLintView;
    private Text mDetailsText;
    private Label mErrorLabel;

    private Action mFixAction;
    private Action mRemoveAction;
    private Action mIgnoreAction;
    private Action mRemoveAllAction;
    private Action mRefreshAction;

    /**
     * Initial project to show: this field is only briefly not null during the
     * construction initiated by {@link #show(IProject)}
     */
    private static IProject sInitialProject;

    /**
     * Constructs a new {@link LintViewPart}
     */
    public LintViewPart() {
    }

    @Override
    public void dispose() {
        mLintView.dispose();
        super.dispose();
    }

    @Override
    public void createPartControl(Composite parent) {
        GridLayout gridLayout = new GridLayout(1, false);
        gridLayout.verticalSpacing = 0;
        gridLayout.marginWidth = 0;
        gridLayout.marginHeight = 0;
        parent.setLayout(gridLayout);

        mErrorLabel = new Label(parent, SWT.NONE);
        mErrorLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));

        sashForm = new SashForm(parent, SWT.NONE);
        sashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
        mLintView = new LintList(getSite(), sashForm);

        mDetailsText = new Text(sashForm,
                SWT.BORDER | SWT.READ_ONLY | SWT.WRAP | SWT.V_SCROLL | SWT.MULTI);
        Display display = parent.getDisplay();
        mDetailsText.setBackground(display.getSystemColor(SWT.COLOR_INFO_BACKGROUND));
        mDetailsText.setForeground(display.getSystemColor(SWT.COLOR_INFO_FOREGROUND));

        mLintView.addSelectionListener(this);
        sashForm.setWeights(new int[] {8, 2});

        createActions();
        initializeToolBar();

        // If there are currently running jobs, listen for them such that we can update the
        // button state
        refreshStopIcon();

        if (sInitialProject != null) {
            mLintView.setResources(Collections.<IResource>singletonList(sInitialProject));
            sInitialProject = null;
        } else {
            // No supplied context: show lint warnings for all projects
            IJavaProject[] androidProjects = BaseProjectHelper.getAndroidProjects(null);
            if (androidProjects.length > 0) {
                List<IResource> projects = new ArrayList<IResource>();
                for (IJavaProject project : androidProjects) {
                    projects.add(project.getProject());
                }
                mLintView.setResources(projects);
            }
        }

        updateIssueCount();
    }

    /**
     * Create the actions.
     */
    private void createActions() {
        ISharedImages sharedImages = PlatformUI.getWorkbench().getSharedImages();
        IconFactory iconFactory = IconFactory.getInstance();
        mFixAction = new LintViewAction("Fix", ACTION_FIX,
                iconFactory.getImageDescriptor(QUICKFIX_ICON),
                iconFactory.getImageDescriptor(QUICKFIX_DISABLED_ICON));
        mIgnoreAction = new LintViewAction("Ignore Type", ACTION_IGNORE,
                sharedImages.getImageDescriptor(ISharedImages.IMG_ETOOL_CLEAR),
                sharedImages.getImageDescriptor(ISharedImages.IMG_ETOOL_CLEAR_DISABLED));
        mRemoveAction = new LintViewAction("Remove", ACTION_REMOVE,
                sharedImages.getImageDescriptor(ISharedImages.IMG_ELCL_REMOVE),
                sharedImages.getImageDescriptor(ISharedImages.IMG_ELCL_REMOVE_DISABLED));
        mRemoveAllAction = new LintViewAction("Remove All", ACTION_REMOVE_ALL,
                sharedImages.getImageDescriptor(ISharedImages.IMG_ELCL_REMOVEALL),
                sharedImages.getImageDescriptor(ISharedImages.IMG_ELCL_REMOVEALL_DISABLED));
        mRefreshAction = new LintViewAction("Refresh", ACTION_REFRESH,
                iconFactory.getImageDescriptor(REFRESH_ICON), null);
        mRemoveAllAction.setEnabled(true);

        enableActions(Collections.<IMarker>emptyList(), false /*updateWidgets*/);
    }

    /**
     * Initialize the toolbar.
     */
    private void initializeToolBar() {
        IToolBarManager toolbarManager = getViewSite().getActionBars().getToolBarManager();
        toolbarManager.add(mRefreshAction);
        toolbarManager.add(mFixAction);
        toolbarManager.add(mIgnoreAction);
        toolbarManager.add(mRemoveAction);
        toolbarManager.add(mRemoveAllAction);
    }

    @Override
    public void setFocus() {
        mLintView.setFocus();
    }

    /**
     * Sets the resource associated with the lint view
     *
     * @param resources the associated resources
     */
    public void setResources(List<IResource> resources) {
        mLintView.setResources(resources);

        // Refresh the stop/refresh icon status
        refreshStopIcon();
    }

    private void refreshStopIcon() {
        Job[] currentJobs = LintRunner.getCurrentJobs();
        if (currentJobs.length > 0) {
            ISharedImages sharedImages = PlatformUI.getWorkbench().getSharedImages();
            mRefreshAction.setImageDescriptor(sharedImages.getImageDescriptor(
                    ISharedImages.IMG_ELCL_STOP));
            for (Job job : currentJobs) {
                job.addJobChangeListener(this);
            }
        } else {
            mRefreshAction.setImageDescriptor(
                    IconFactory.getInstance().getImageDescriptor(REFRESH_ICON));

        }
    }

    // ---- Implements SelectionListener ----

    public void widgetSelected(SelectionEvent e) {
        List<IMarker> markers = mLintView.getSelectedMarkers();
        if (markers.size() != 1) {
            mDetailsText.setText(""); //$NON-NLS-1$
        } else {
            mDetailsText.setText(EclipseLintClient.describe(markers.get(0)));
        }
        updateIssueCount();

        enableActions(markers, true /* updateWidgets */);
    }

    private void enableActions(List<IMarker> markers, boolean updateWidgets) {
        // Update enabled state of actions
        boolean hasSelection = markers.size() > 0;
        boolean canFix = hasSelection;
        for (IMarker marker : markers) {
            if (!LintFix.hasFix(EclipseLintClient.getId(marker))) {
                canFix = false;
                break;
            }

            // Some fixes cannot be run in bulk
            if (markers.size() > 1) {
                LintFix fix = LintFix.getFix(EclipseLintClient.getId(marker), marker);
                if (!fix.isBulkCapable()) {
                    canFix = false;
                    break;
                }
            }
        }

        mFixAction.setEnabled(canFix);
        mIgnoreAction.setEnabled(hasSelection);
        mRemoveAction.setEnabled(hasSelection);

        if (updateWidgets) {
            getViewSite().getActionBars().getToolBarManager().update(false);
        }
    }

    public void widgetDefaultSelected(SelectionEvent e) {
        Object source = e.getSource();
        if (source == mLintView.getTableViewer().getControl()) {
            // Jump to editor
            List<IMarker> selection = mLintView.getSelectedMarkers();
            if (selection.size() > 0) {
                EclipseLintClient.showMarker(selection.get(0));
            }
        }
    }

    // --- Implements IJobChangeListener ----

    public void done(IJobChangeEvent event) {
        mRefreshAction.setImageDescriptor(
                IconFactory.getInstance().getImageDescriptor(REFRESH_ICON));

        if (!mLintView.isDisposed()) {
            mLintView.getDisplay().asyncExec(new Runnable()  {
                public void run() {
                    if (!mLintView.isDisposed()) {
                        updateIssueCount();
                    }
                }
            });
        }
    }

    private void updateIssueCount() {
        int errors = mLintView.getErrorCount();
        int warnings = mLintView.getWarningCount();
        mErrorLabel.setText(String.format("%1$d errors, %2$d warnings", errors, warnings));
    }

    public void aboutToRun(IJobChangeEvent event) {
    }

    public void awake(IJobChangeEvent event) {
    }

    public void running(IJobChangeEvent event) {
    }

    public void scheduled(IJobChangeEvent event) {
    }

    public void sleeping(IJobChangeEvent event) {
    }

    // ---- Actions ----

    private static final int ACTION_REFRESH = 1;
    private static final int ACTION_FIX = 2;
    private static final int ACTION_IGNORE = 3;
    private static final int ACTION_REMOVE = 4;
    private static final int ACTION_REMOVE_ALL = 5;
    private SashForm sashForm;

    private class LintViewAction extends Action {

        private final int mAction;

        private LintViewAction(String label, int action,
                ImageDescriptor imageDesc, ImageDescriptor disabledImageDesc) {
            super(label);
            mAction = action;
            setImageDescriptor(imageDesc);
            if (disabledImageDesc != null) {
                setDisabledImageDescriptor(disabledImageDesc);
            }
        }

        @Override
        public void run() {
            switch (mAction) {
                case ACTION_REFRESH: {
                    Job[] jobs = LintRunner.getCurrentJobs();
                    if (jobs.length > 0) {
                        LintRunner.cancelCurrentJobs(false);
                    } else {
                        List<IResource> resources = mLintView.getResources();
                        if (resources == null) {
                            return;
                        }
                        for (IResource resource : resources) {
                            Job job = LintRunner.startLint(resource, null, false);
                            if (job != null) {
                                job.addJobChangeListener(LintViewPart.this);
                                IWorkbench workbench = PlatformUI.getWorkbench();
                                ISharedImages sharedImages = workbench.getSharedImages();
                                setImageDescriptor(sharedImages.getImageDescriptor(
                                        ISharedImages.IMG_ELCL_STOP));
                            }
                        }
                    }
                    break;
                }
                case ACTION_FIX: {
                    List<IMarker> markers = mLintView.getSelectedMarkers();
                    for (IMarker marker : markers) {
                        LintFix fix = LintFix.getFix(EclipseLintClient.getId(marker), marker);
                        IEditorPart editor = AdtUtils.getActiveEditor();
                        if (editor instanceof AndroidXmlEditor) {
                            IStructuredDocument doc =
                                ((AndroidXmlEditor) editor).getStructuredDocument();
                            fix.apply(doc);
                        } else {
                            AdtPlugin.log(IStatus.ERROR,
                                    "Did not find associated editor to apply fix");
                        }
                    }
                    break;
                }
                case ACTION_REMOVE: {
                    for (IMarker marker : mLintView.getSelectedMarkers()) {
                        try {
                            marker.delete();
                        } catch (CoreException e) {
                            AdtPlugin.log(e, null);
                        }
                    }
                    break;
                }
                case ACTION_REMOVE_ALL:
                    List<IResource> resources = mLintView.getResources();
                    if (resources != null) {
                        for (IResource resource : resources) {
                            EclipseLintClient.clearMarkers(resource);
                        }
                    }
                    break;
                case ACTION_IGNORE: {
                    for (IMarker marker : mLintView.getSelectedMarkers()) {
                        String id = EclipseLintClient.getId(marker);
                        if (id != null) {
                            // TODO: Add "ignore in all" button!
                            LintFixGenerator.suppressDetector(id, true, null, true/*all*/);
                        }
                    }
                    break;
                }
                default:
                    assert false : mAction;
            }
            updateIssueCount();
        }
    }

    /**
     * Shows or reconfigures the LintView to show the lint warnings for the
     * given project
     *
     * @param project the project to show lint warnings for
     */
    public static void show(IProject project) {
        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (window != null) {
            IWorkbenchPage page = window.getActivePage();
            if (page != null) {
                try {
                    // Pass initial project context via static field read by constructor
                    sInitialProject = project;
                    IViewPart view = page.showView(LintViewPart.ID, null,
                            IWorkbenchPage.VIEW_ACTIVATE);
                    if (sInitialProject != null && view instanceof LintViewPart) {
                        // The view must be showing already since the constructor was not
                        // run, so reconfigure the view instead
                        LintViewPart lintView = (LintViewPart) view;
                        lintView.setResources(Collections.<IResource>singletonList(project));
                    }
                } catch (PartInitException e) {
                    AdtPlugin.log(e, "Cannot open Lint View");
                } finally {
                    sInitialProject = null;
                }
            }
        }
    }
}
