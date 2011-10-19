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
package com.android.ide.eclipse.ddms.views;

import com.android.ddmlib.IDevice;
import com.android.ddmuilib.logcat.ILogCatMessageSelectionListener;
import com.android.ddmuilib.logcat.LogCatMessage;
import com.android.ddmuilib.logcat.LogCatPanel;
import com.android.ddmuilib.logcat.LogCatStackTraceParser;
import com.android.ide.eclipse.ddms.DdmsPlugin;
import com.android.ide.eclipse.ddms.i18n.Messages;
import com.android.ide.eclipse.ddms.preferences.PreferenceInitializer;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IPerspectiveRegistry;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.WorkbenchException;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.ide.IDE;

import java.util.HashMap;
import java.util.Map;

public class LogCatView extends SelectionDependentViewPart {
    /** LogCatView ID as defined in plugin.xml. */
    public static final String ID = "com.android.ide.eclipse.ddms.views.LogCatView"; //$NON-NLS-1$

    /** Constant indicating that double clicking on a stack trace should
     * open the method declaration. */
    public static final String CHOICE_METHOD_DECLARATION =
            DdmsPlugin.PLUGIN_ID + ".logcat.MethodDeclaration"; //$NON-NLS-1$

    /** Constant indicating that double clicking on a stack trace should
     * open the line at which error occurred. */
    public static final String CHOICE_ERROR_LINE =
            DdmsPlugin.PLUGIN_ID + ".logcat.ErrorLine"; //$NON-NLS-1$

    /** Switch perspective when a Java file is opened from logcat view. */
    public static final boolean DEFAULT_SWITCH_PERSPECTIVE = true;

    /** Target perspective to open when a Java file is opened from logcat view. */
    public static final String DEFAULT_PERSPECTIVE_ID =
            "org.eclipse.jdt.ui.JavaPerspective"; //$NON-NLS-1$

    private LogCatPanel mLogCatPanel;
    private LogCatStackTraceParser mStackTraceParser = new LogCatStackTraceParser();

    private Clipboard mClipboard;

    @Override
    public void createPartControl(Composite parent) {
        parent.setLayout(new FillLayout());

        IPreferenceStore prefStore = DdmsPlugin.getDefault().getPreferenceStore();
        mLogCatPanel = new LogCatPanel(prefStore);
        mLogCatPanel.createPanel(parent);
        setSelectionDependentPanel(mLogCatPanel);

        mLogCatPanel.addLogCatMessageSelectionListener(new ILogCatMessageSelectionListener() {
            public void messageDoubleClicked(LogCatMessage m) {
                onDoubleClick(m);
            }
        });

        mClipboard = new Clipboard(parent.getDisplay());
        IActionBars actionBars = getViewSite().getActionBars();
        actionBars.setGlobalActionHandler(ActionFactory.COPY.getId(),
                new Action(Messages.LogCatView_Copy) {
            @Override
            public void run() {
                mLogCatPanel.copySelectionToClipboard(mClipboard);
            }
        });

        actionBars.setGlobalActionHandler(ActionFactory.SELECT_ALL.getId(),
                new Action(Messages.LogCatView_Select_All) {
            @Override
            public void run() {
                mLogCatPanel.selectAll();
            }
        });
    }

    @Override
    public void setFocus() {
    }

    /**
     * This class defines what to do with the search match returned by a
     * double-click or by the Go to Problem action.
     */
    private class LogCatViewSearchRequestor extends SearchRequestor {
        private boolean mFoundFirstMatch = false;
        private String mChoice;
        private int mLineNumber;

        public LogCatViewSearchRequestor(String choice, int lineNumber) {
            super();
            mChoice = choice;
            mLineNumber = lineNumber;
        }

        IMarker createMarkerFromSearchMatch(IFile file, SearchMatch match) {
            IMarker marker = null;
            try {
                if (CHOICE_METHOD_DECLARATION.equals(mChoice)) {
                    Map<String, Object> attrs = new HashMap<String, Object>();
                    attrs.put(IMarker.CHAR_START, Integer.valueOf(match.getOffset()));
                    attrs.put(IMarker.CHAR_END, Integer.valueOf(match.getOffset()
                            + match.getLength()));
                    marker = file.createMarker(IMarker.TEXT);
                    marker.setAttributes(attrs);
                } else if (CHOICE_ERROR_LINE.equals(mChoice)) {
                    marker = file.createMarker(IMarker.TEXT);
                    marker.setAttribute(IMarker.LINE_NUMBER, mLineNumber);
                }
            } catch (CoreException e) {
                Status s = new Status(Status.ERROR, DdmsPlugin.PLUGIN_ID, e.getMessage(), e);
                DdmsPlugin.getDefault().getLog().log(s);
            }
            return marker;
        }

        @Override
        public void acceptSearchMatch(SearchMatch match) throws CoreException {
            if (match.getResource() instanceof IFile && !mFoundFirstMatch) {
                mFoundFirstMatch = true;
                IFile matchedFile = (IFile) match.getResource();
                IMarker marker = createMarkerFromSearchMatch(matchedFile, match);
                // There should only be one exact match,
                // so we go immediately to that one.
                if (marker != null) {
                    switchPerspective();
                    showMarker(marker);
                }
            }
        }
    }

    /**
     * Switch to perspective specified by user when opening a source file.
     * User preferences control whether the perspective should be switched,
     * and if so, what the target perspective is.
     */
    private void switchPerspective() {
        IPreferenceStore store = DdmsPlugin.getDefault().getPreferenceStore();
        if (store.getBoolean(PreferenceInitializer.ATTR_SWITCH_PERSPECTIVE)) {
            IWorkbench workbench = PlatformUI.getWorkbench();
            IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
            IPerspectiveRegistry perspectiveRegistry = workbench.getPerspectiveRegistry();
            String perspectiveId = store.getString(PreferenceInitializer.ATTR_PERSPECTIVE_ID);
            if (perspectiveId != null
                    && perspectiveId.length() > 0
                    && perspectiveRegistry.findPerspectiveWithId(perspectiveId) != null) {
                try {
                    workbench.showPerspective(perspectiveId, window);
                } catch (WorkbenchException e) {
                    Status s = new Status(Status.ERROR, DdmsPlugin.PLUGIN_ID, e.getMessage(), e);
                    DdmsPlugin.getDefault().getLog().log(s);
                }
            }
        }
    }

    private void showMarker(IMarker marker) {
        try {
            IWorkbenchPage page = getViewSite().getWorkbenchWindow()
                    .getActivePage();
            if (page != null) {
                IDE.openEditor(page, marker);
                marker.delete();
            }
        } catch (CoreException e) {
            Status s = new Status(Status.ERROR, DdmsPlugin.PLUGIN_ID, e.getMessage(), e);
            DdmsPlugin.getDefault().getLog().log(s);
        }
    }

    private void onDoubleClick(LogCatMessage m) {
        String msg = m.getMessage();
        if (!mStackTraceParser.isValidExceptionTrace(msg)) {
            return;
        }

        String methodName = mStackTraceParser.getMethodName(msg);
        String fileName = mStackTraceParser.getFileName(msg);
        int lineNumber = mStackTraceParser.getLineNumber(msg);

        IPreferenceStore store = DdmsPlugin.getDefault().getPreferenceStore();
        String jumpToLocation = store.getString(PreferenceInitializer.ATTR_LOGCAT_GOTO_PROBLEM);

        String stringPattern = methodName;
        LogCatViewSearchRequestor requestor =
                new LogCatViewSearchRequestor(CHOICE_METHOD_DECLARATION, 0);
        int searchFor = IJavaSearchConstants.METHOD;
        if (jumpToLocation.equals(CHOICE_ERROR_LINE)) {
            searchFor = IJavaSearchConstants.CLASS;
            stringPattern = fileName;
            requestor = new LogCatViewSearchRequestor(CHOICE_ERROR_LINE, lineNumber);
        }

        SearchEngine se = new SearchEngine();
        SearchPattern searchPattern = SearchPattern.createPattern(stringPattern,
                searchFor,
                IJavaSearchConstants.DECLARATIONS,
                SearchPattern.R_EXACT_MATCH | SearchPattern.R_CASE_SENSITIVE);
        try {
            se.search(searchPattern,
                    new SearchParticipant[] {SearchEngine.getDefaultSearchParticipant()},
                    SearchEngine.createWorkspaceScope(),
                    requestor,
                    new NullProgressMonitor());
        } catch (CoreException e) {
            Status s = new Status(Status.ERROR, DdmsPlugin.PLUGIN_ID, e.getMessage(), e);
            DdmsPlugin.getDefault().getLog().log(s);
        }
    }

    public void selectTransientAppFilter(String appName) {
        mLogCatPanel.selectTransientAppFilter(appName);
    }
}
