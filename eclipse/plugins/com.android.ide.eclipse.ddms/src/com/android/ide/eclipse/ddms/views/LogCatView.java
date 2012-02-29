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

import com.android.ddmuilib.logcat.ILogCatMessageSelectionListener;
import com.android.ddmuilib.logcat.LogCatMessage;
import com.android.ddmuilib.logcat.LogCatPanel;
import com.android.ddmuilib.logcat.LogCatStackTraceParser;
import com.android.ide.eclipse.ddms.DdmsPlugin;
import com.android.ide.eclipse.ddms.JavaSourceRevealer;
import com.android.ide.eclipse.ddms.i18n.Messages;
import com.android.ide.eclipse.ddms.preferences.PreferenceInitializer;

import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IPerspectiveRegistry;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.WorkbenchException;
import org.eclipse.ui.actions.ActionFactory;

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
            @Override
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

    private void onDoubleClick(LogCatMessage m) {
        String msg = m.getMessage();
        if (!mStackTraceParser.isValidExceptionTrace(msg)) {
            return;
        }

        IPreferenceStore store = DdmsPlugin.getDefault().getPreferenceStore();
        String jumpToLocation = store.getString(PreferenceInitializer.ATTR_LOGCAT_GOTO_PROBLEM);

        String perspectiveId = null;
        if (store.getBoolean(PreferenceInitializer.ATTR_SWITCH_PERSPECTIVE)) {
            perspectiveId = store.getString(PreferenceInitializer.ATTR_PERSPECTIVE_ID);
        }


        if (jumpToLocation.equals(CHOICE_ERROR_LINE)) {
            String fileName = mStackTraceParser.getFileName(msg);
            int lineNumber = mStackTraceParser.getLineNumber(msg);
            JavaSourceRevealer.revealLine(fileName, lineNumber, perspectiveId);
        } else {
            String methodName = mStackTraceParser.getMethodName(msg);
            JavaSourceRevealer.revealMethod(methodName, perspectiveId);
        }
    }

    public void selectTransientAppFilter(String appName) {
        mLogCatPanel.selectTransientAppFilter(appName);
    }
}
