/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.ide.eclipse.adt.internal.editors.layout.configuration;

import com.android.ide.eclipse.adt.AdtPlugin;

import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.AbstractElementListSelectionDialog;

/**
 * A dialog to let the user select a theme
 */
public class ThemeChooser extends AbstractElementListSelectionDialog {
    /** The return code from the dialog for the user choosing "Clear" */
    public static final int CLEAR_RETURN_CODE = -5;
    /** The dialog button ID for the user choosing "Clear" */
    private static final int CLEAR_BUTTON_ID = CLEAR_RETURN_CODE;

    private String mCurrentResource;
    private String[] mThemes;

    private ThemeChooser(String[] themes, Shell parent) {
        super(parent, new LabelProvider());
        mThemes = themes;

        setTitle("Theme Chooser");
        setMessage(String.format("Choose a theme"));
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, CLEAR_BUTTON_ID, "Clear", false /*defaultButton*/);
        super.createButtonsForButtonBar(parent);
    }

    @Override
    protected void buttonPressed(int buttonId) {
        super.buttonPressed(buttonId);

        if (buttonId == CLEAR_BUTTON_ID) {
            assert CLEAR_RETURN_CODE != Window.OK && CLEAR_RETURN_CODE != Window.CANCEL;
            setReturnCode(CLEAR_RETURN_CODE);
            close();
        }
    }

    private void setCurrentResource(String resource) {
        mCurrentResource = resource;
    }

    private String getCurrentResource() {
        return mCurrentResource;
    }

    @Override
    protected void computeResult() {
        computeResultFromSelection();
    }

    private void computeResultFromSelection() {
        if (getSelectionIndex() == -1) {
            mCurrentResource = null;
            return;
        }

        Object[] elements = getSelectedElements();
        if (elements.length == 1 && elements[0] instanceof String) {
            String item = (String) elements[0];

            mCurrentResource = item;
        }
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite top = (Composite)super.createDialogArea(parent);

        createMessageArea(top);

        createFilterText(top);
        createFilteredList(top);

        setupResourceList();
        selectResourceString(mCurrentResource);

        return top;
    }

    /**
     * Setups the current list.
     */
    private String[] setupResourceList() {
        setListElements(mThemes);
        fFilteredList.setEnabled(mThemes.length > 0);

        return mThemes;
    }

    /**
     * Select an item by its name, if possible.
     */
    private void selectItemName(String itemName, String[] items) {
        if (itemName == null || items == null) {
            return;
        }
        setSelection(new String[] { itemName });
    }

    /**
     * Select an item by its full resource string.
     * This also selects between project and system repository based on the resource string.
     */
    private void selectResourceString(String item) {
        String itemName = item;

        // Update the list
        String[] items = setupResourceList();

        // If we have a selection name, select it
        if (itemName != null) {
            selectItemName(itemName, items);
        }
    }

    public static String chooseResource(
            String[] themes,
            String currentTheme) {
        Shell shell = AdtPlugin.getDisplay().getActiveShell();
        if (shell == null) {
            return null;
        }

        ThemeChooser dialog = new ThemeChooser(themes, shell);
        dialog.setCurrentResource(currentTheme);

        int result = dialog.open();
        if (result == ThemeChooser.CLEAR_RETURN_CODE) {
            return ""; //$NON-NLS-1$
        } else if (result == Window.OK) {
            return dialog.getCurrentResource();
        }

        return null;
    }
}
