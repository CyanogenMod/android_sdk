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

import com.android.ddmuilib.logcat.LogCatPanel;
import com.android.ddmuilib.logcat.LogCatReceiver;
import com.android.ide.eclipse.ddms.DdmsPlugin;

import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;

public class LogCatView extends SelectionDependentViewPart {
    /** LogCatView ID as defined in plugin.xml. */
    public static final String ID = "com.android.ide.eclipse.ddms.views.LogCatView"; //$NON-NLS-1$

    public static final String CHOICE_METHOD_DECLARATION =
            DdmsPlugin.PLUGIN_ID + ".logcat.MethodDeclaration"; //$NON-NLS-1$
    public static final String CHOICE_ERROR_LINE =
            DdmsPlugin.PLUGIN_ID + ".logcat.ErrorLine"; //$NON-NLS-1$

    /** Switch perspective when a Java file is opened from logcat view. */
    public static final boolean DEFAULT_SWITCH_PERSPECTIVE = true;

    /** Target perspective to open when a Java file is opened from logcat view. */
    public static final String DEFAULT_PERSPECTIVE_ID =
            "org.eclipse.jdt.ui.JavaPerspective"; //$NON-NLS-1$

    private LogCatPanel mLogCatPanel;

    @Override
    public void createPartControl(Composite parent) {
        parent.setLayout(new FillLayout());

        mLogCatPanel = new LogCatPanel(new LogCatReceiver(),
                DdmsPlugin.getDefault().getPreferenceStore());
        mLogCatPanel.createPanel(parent);
        setSelectionDependentPanel(mLogCatPanel);
    }

    @Override
    public void setFocus() {
    }
}
