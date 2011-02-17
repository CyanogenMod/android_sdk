/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.ide.eclipse.ddms.preferences;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.ComboFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.FontFieldEditor;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.ui.IPerspectiveDescriptor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.PlatformUI;

import com.android.ide.eclipse.ddms.DdmsPlugin;
import com.android.ide.eclipse.ddms.i18n.Messages;
import com.android.ide.eclipse.ddms.views.LogCatView;

/**
 * Preference Pane for LogCat.
 */
public class LogCatPreferencePage extends FieldEditorPreferencePage implements
        IWorkbenchPreferencePage {

    private BooleanFieldEditor mSwitchPerspective;
    private ComboFieldEditor mWhichPerspective;

    public LogCatPreferencePage() {
        super(GRID);
        setPreferenceStore(DdmsPlugin.getDefault().getPreferenceStore());
    }

    @Override
    protected void createFieldEditors() {
        FontFieldEditor ffe = new FontFieldEditor(PreferenceInitializer.ATTR_LOGCAT_FONT,
                Messages.LogCatPreferencePage_Display_Font, getFieldEditorParent());
        addField(ffe);

        getPreferenceStore().addPropertyChangeListener(new IPropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent event) {
                // get the name of the property that changed.
                String property = event.getProperty();

                if (PreferenceInitializer.ATTR_LOGCAT_FONT.equals(property)) {
                    try {
                        FontData fdat = new FontData((String) event.getNewValue());
                        LogCatView.setFont(new Font(getFieldEditorParent().getDisplay(), fdat));
                    } catch (IllegalArgumentException e) {
                        // Looks like the data from the store is not valid.
                        // We do nothing (default font will be used).
                    } catch (SWTError e2) {
                        // Looks like the Font() constructor failed.
                        // We do nothing in this case, the logcat view will use
                        // the default font.
                    }
                }
            }
        });

        ComboFieldEditor cfe = new ComboFieldEditor(PreferenceInitializer.ATTR_LOGCAT_GOTO_PROBLEM,
                Messages.LogCatPreferencePage_Double_Click_Action, new String[][] {
                        {
                                Messages.LogCatPreferencePage_Go_To_Problem_Declararion,
                                LogCatView.CHOICE_METHOD_DECLARATION
                        },
                        {
                                Messages.LogCatPreferencePage_Go_To_Problem_Error_Line,
                                LogCatView.CHOICE_ERROR_LINE
                        },
                }, getFieldEditorParent());
        addField(cfe);
        mSwitchPerspective = new BooleanFieldEditor(PreferenceInitializer.ATTR_SWITCH_PERSPECTIVE,
                Messages.LogCatPreferencePage_Switch_Perspective, getFieldEditorParent());
        addField(mSwitchPerspective);
        IPerspectiveDescriptor[] perspectiveDescriptors =
                PlatformUI.getWorkbench().getPerspectiveRegistry().getPerspectives();
        String[][] perspectives;
        if (perspectiveDescriptors.length > 0) {
            perspectives = new String[perspectiveDescriptors.length][2];
            for (int i = 0; i < perspectiveDescriptors.length; i++) {
                IPerspectiveDescriptor perspective = perspectiveDescriptors[i];
                perspectives[i][0] = perspective.getLabel();
                perspectives[i][1] = perspective.getId();
            }
        } else {
            perspectives = new String[0][0];
        }
        mWhichPerspective = new ComboFieldEditor(PreferenceInitializer.ATTR_PERSPECTIVE_ID,
                Messages.LogCatPreferencePage_Switch_To, perspectives, getFieldEditorParent());
        mWhichPerspective.setEnabled(getPreferenceStore()
                .getBoolean(PreferenceInitializer.ATTR_SWITCH_PERSPECTIVE), getFieldEditorParent());
        addField(mWhichPerspective);
    }

    public void init(IWorkbench workbench) {
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
        if (event.getSource().equals(mSwitchPerspective)) {
            mWhichPerspective.setEnabled(mSwitchPerspective.getBooleanValue()
                    , getFieldEditorParent());
        }
    }

    @Override
    protected void performDefaults() {
        super.performDefaults();
        mWhichPerspective.setEnabled(mSwitchPerspective.getBooleanValue(), getFieldEditorParent());
    }
}
