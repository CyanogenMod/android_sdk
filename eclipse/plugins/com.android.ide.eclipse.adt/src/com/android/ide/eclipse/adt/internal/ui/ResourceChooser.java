/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.ide.eclipse.adt.internal.ui;


import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.AdtUtils;
import com.android.ide.eclipse.adt.internal.assetstudio.OpenCreateAssetSetWizardAction;
import com.android.ide.eclipse.adt.internal.refactorings.extractstring.ExtractStringRefactoring;
import com.android.ide.eclipse.adt.internal.refactorings.extractstring.ExtractStringWizard;
import com.android.ide.eclipse.adt.internal.resources.ResourceHelper;
import com.android.ide.eclipse.adt.internal.resources.ResourceNameValidator;
import com.android.resources.ResourceType;
import com.android.util.Pair;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.window.Window;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.AbstractElementListSelectionDialog;
import org.eclipse.ui.dialogs.SelectionStatusDialog;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A dialog to let the user select a resource based on a resource type.
 */
public class ResourceChooser extends AbstractElementListSelectionDialog {
    /** The return code from the dialog for the user choosing "Clear" */
    public static final int CLEAR_RETURN_CODE = -5;
    /** The dialog button ID for the user choosing "Clear" */
    private static final int CLEAR_BUTTON_ID = CLEAR_RETURN_CODE;

    private Pattern mProjectResourcePattern;
    private ResourceType mResourceType;
    private final ResourceRepository mProjectResources;
    private final ResourceRepository mFrameworkResources;
    private Pattern mSystemResourcePattern;
    private Button mProjectButton;
    private Button mSystemButton;
    private Button mNewButton;
    private String mCurrentResource;
    private final IProject mProject;
    private IInputValidator mInputValidator;
    private ResourcePreviewHelper mPreviewHelper;

    /**
     * Creates a Resource Chooser dialog.
     * @param project Project being worked on
     * @param type The type of the resource to choose
     * @param projectResources The repository for the project
     * @param frameworkResources The Framework resource repository
     * @param parent the parent shell
     */
    public ResourceChooser(IProject project, ResourceType type,
            ResourceRepository projectResources,
            ResourceRepository frameworkResources,
            Shell parent) {
        super(parent, new ResourceLabelProvider());
        mProject = project;

        mResourceType = type;
        mProjectResources = projectResources;
        mFrameworkResources = frameworkResources;

        mProjectResourcePattern = Pattern.compile(
                "@" + mResourceType.getName() + "/(.+)"); //$NON-NLS-1$ //$NON-NLS-2$

        mSystemResourcePattern = Pattern.compile(
                "@android:" + mResourceType.getName() + "/(.+)"); //$NON-NLS-1$ //$NON-NLS-2$

        setTitle("Resource Chooser");
        setMessage(String.format("Choose a %1$s resource",
                mResourceType.getDisplayName().toLowerCase()));
    }

    public void setPreviewHelper(ResourcePreviewHelper previewHelper) {
        mPreviewHelper = previewHelper;
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

    public void setCurrentResource(String resource) {
        mCurrentResource = resource;
    }

    public String getCurrentResource() {
        return mCurrentResource;
    }

    public void setInputValidator(IInputValidator inputValidator) {
        mInputValidator = inputValidator;
    }

    @Override
    protected void computeResult() {
        if (getSelectionIndex() == -1) {
            mCurrentResource = null;
            return;
        }

        Object[] elements = getSelectedElements();
        if (elements.length == 1 && elements[0] instanceof ResourceItem) {
            ResourceItem item = (ResourceItem)elements[0];

            mCurrentResource = item.getXmlString(mResourceType, mSystemButton.getSelection());

            if (mInputValidator != null && mInputValidator.isValid(mCurrentResource) != null) {
                mCurrentResource = null;
            }
        }
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite top = (Composite)super.createDialogArea(parent);

        createMessageArea(top);

        createButtons(top);
        createFilterText(top);
        createFilteredList(top);

        // create the "New Resource" button
        createNewResButtons(top);

        setupResourceList();
        selectResourceString(mCurrentResource);

        return top;
    }

    /**
     * Creates the radio button to switch between project and system resources.
     * @param top the parent composite
     */
    private void createButtons(Composite top) {
        mProjectButton = new Button(top, SWT.RADIO);
        mProjectButton.setText("Project Resources");
        mProjectButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                super.widgetSelected(e);
                if (mProjectButton.getSelection()) {
                    setupResourceList();
                    updateNewButton(false /*isSystem*/);
                    updatePreview();
                }
            }
        });
        mSystemButton = new Button(top, SWT.RADIO);
        mSystemButton.setText("System Resources");
        mSystemButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                super.widgetSelected(e);
                if (mSystemButton.getSelection()) {
                    setupResourceList();
                    updateNewButton(true /*isSystem*/);
                    updatePreview();
                }
            }
        });
    }

    /**
     * Creates the "New Resource" button.
     * @param top the parent composite
     */
    private void createNewResButtons(Composite top) {
        mNewButton = new Button(top, SWT.NONE);

        String title = String.format("New %1$s...", mResourceType.getDisplayName());
        if (mResourceType == ResourceType.DRAWABLE) {
            title = "Create New Icon...";
        }
        mNewButton.setText(title);

        mNewButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                super.widgetSelected(e);

                if (mResourceType == ResourceType.STRING) {
                    // Special case: Use Extract String refactoring wizard UI
                    String newName = createNewString();
                    selectAddedItem(newName);
                } else if (mResourceType == ResourceType.DRAWABLE) {
                    // Special case: Use the "Create Icon Set" wizard
                    OpenCreateAssetSetWizardAction action =
                            new OpenCreateAssetSetWizardAction(mProject);
                    action.run();
                    List<IResource> files = action.getCreatedFiles();
                    if (files != null && files.size() > 0) {
                        String newName = AdtUtils.stripAllExtensions(files.get(0).getName());
                        // Recompute the "current resource" to select the new id
                        ResourceItem[] items = setupResourceList();
                        selectItemName(newName, items);
                    }
                } else {
                    if (ResourceHelper.isValueBasedResourceType(mResourceType)) {
                        String newName = createNewValue(mResourceType);
                        selectAddedItem(newName);
                    } else {
                        String newName = createNewFile(mResourceType);
                        selectAddedItem(newName);
                    }
                }
            }

            private void selectAddedItem(String newName) {
                // Recompute the "current resource" to select the new id
                ResourceItem[] items = setupResourceList();

                // Ensure that the name is in the list. There's a delay after
                // an item is added (until the builder runs and processes the delta)
                // so if it's not in the list, add it
                boolean found = false;
                for (ResourceItem item : items) {
                    if (newName.equals(item.getName())) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    ResourceItem[] newItems = new ResourceItem[items.length + 1];
                    System.arraycopy(items, 0, newItems, 0, items.length);
                    newItems[items.length] = new ResourceItem(newName);
                    items = newItems;
                    Arrays.sort(items);
                    setListElements(items);
                }

                selectItemName(newName, items);
            }
        });
    }

    @Override
    protected void handleSelectionChanged() {
        super.handleSelectionChanged();
        if (mInputValidator != null) {
            Object[] elements = getSelectedElements();
            if (elements.length == 1 && elements[0] instanceof ResourceItem) {
                ResourceItem item = (ResourceItem)elements[0];
                String current = item.getXmlString(mResourceType, mSystemButton.getSelection());
                String error = mInputValidator.isValid(current);
                IStatus status;
                if (error != null) {
                    status = new Status(IStatus.ERROR, AdtPlugin.PLUGIN_ID, error);
                } else {
                    status = new Status(IStatus.OK, AdtPlugin.PLUGIN_ID, null);
                }
                updateStatus(status);
            }
        }

        updatePreview();
    }

    private void updatePreview() {
        if (mPreviewHelper != null) {
            computeResult();
            mPreviewHelper.updatePreview(mResourceType, mCurrentResource);
        }
    }

    private String createNewFile(ResourceType type) {
        // Show a name/value dialog entering the key name and the value
        Shell shell = AdtPlugin.getDisplay().getActiveShell();
        if (shell == null) {
            return null;
        }

        ResourceNameValidator validator = ResourceNameValidator.create(true /*allowXmlExtension*/,
                mProject, mResourceType);
        InputDialog d = new InputDialog(
                AdtPlugin.getDisplay().getActiveShell(),
                "Enter name",  // title
                "Enter name",
                "", //$NON-NLS-1$
                validator);
        if (d.open() == Window.OK) {
            String name = d.getValue().trim();
            if (name.length() == 0) {
                return null;
            }

            Pair<IFile, IRegion> resource = ResourceHelper.createResource(mProject, type, name,
                    null);
            if (resource != null) {
                return name;
            }
        }

        return null;
    }


    private String createNewValue(ResourceType type) {
        // Show a name/value dialog entering the key name and the value
        Shell shell = AdtPlugin.getDisplay().getActiveShell();
        if (shell == null) {
            return null;
        }
        NameValueDialog dialog = new NameValueDialog(shell, getFilter());
        if (dialog.open() != Window.OK) {
            return null;
        }

        String name = dialog.getName();
        String value = dialog.getValue();
        if (name.length() == 0 || value.length() == 0) {
            return null;
        }

        Pair<IFile, IRegion> resource = ResourceHelper.createResource(mProject, type, name, value);
        if (resource != null) {
            return name;
        }

        return null;
    }

    private String createNewString() {
        ExtractStringRefactoring ref = new ExtractStringRefactoring(
                mProject, true /*enforceNew*/);
        RefactoringWizard wizard = new ExtractStringWizard(ref, mProject);
        RefactoringWizardOpenOperation op = new RefactoringWizardOpenOperation(wizard);
        try {
            IWorkbench w = PlatformUI.getWorkbench();
            if (op.run(w.getDisplay().getActiveShell(), wizard.getDefaultPageTitle()) ==
                    IDialogConstants.OK_ID) {
                return ref.getXmlStringId();
            }
        } catch (InterruptedException ex) {
            // Interrupted. Pass.
        }

        return null;
    }

    /**
     * Setups the current list.
     */
    private ResourceItem[] setupResourceList() {
        Collection<ResourceItem> items = null;
        if (mProjectButton.getSelection()) {
            items = mProjectResources.getResourceItemsOfType(mResourceType);
        } else if (mSystemButton.getSelection()) {
            items = mFrameworkResources.getResourceItemsOfType(mResourceType);
        }

        if (items == null) {
            items = Collections.emptyList();
        }

        ResourceItem[] arrayItems = items.toArray(new ResourceItem[items.size()]);

        // sort the array
        Arrays.sort(arrayItems);

        setListElements(arrayItems);

        return arrayItems;
    }

    /**
     * Select an item by its name, if possible.
     */
    private void selectItemName(String itemName, ResourceItem[] items) {
        if (itemName == null || items == null) {
            return;
        }

        for (ResourceItem item : items) {
            if (itemName.equals(item.getName())) {
                setSelection(new Object[] { item });
                break;
            }
        }
    }

    /**
     * Select an item by its full resource string.
     * This also selects between project and system repository based on the resource string.
     */
    private void selectResourceString(String resourceString) {
        boolean isSystem = false;
        String itemName = null;

        if (resourceString != null) {
            // Is this a system resource?
            // If not a system resource or if they are not available, this will be a project res.
            Matcher m = mSystemResourcePattern.matcher(resourceString);
            if (m.matches()) {
                itemName = m.group(1);
                isSystem = true;
            }

            if (!isSystem && itemName == null) {
                // Try to match project resource name
                m = mProjectResourcePattern.matcher(resourceString);
                if (m.matches()) {
                    itemName = m.group(1);
                }
            }
        }

        // Update the repository selection
        mProjectButton.setSelection(!isSystem);
        mSystemButton.setSelection(isSystem);
        updateNewButton(isSystem);

        // Update the list
        ResourceItem[] items = setupResourceList();

        // If we have a selection name, select it
        if (itemName != null) {
            selectItemName(itemName, items);
        }
    }

    private void updateNewButton(boolean isSystem) {
        mNewButton.setEnabled(!isSystem && ResourceHelper.canCreateResourceType(mResourceType));
    }

    /** Dialog asking for a Name/Value pair */
    private class NameValueDialog extends SelectionStatusDialog implements Listener {
        private org.eclipse.swt.widgets.Text mNameText;
        private org.eclipse.swt.widgets.Text mValueText;
        private String mInitialName;
        private String mName;
        private String mValue;
        private ResourceNameValidator mValidator;

        public NameValueDialog(Shell parent, String initialName) {
            super(parent);
            mInitialName = initialName;
        }

        @Override
        protected Control createDialogArea(Composite parent) {
            Composite container = new Composite(parent, SWT.NONE);
            container.setLayout(new GridLayout(2, false));
            GridData gridData = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1);
            // Wide enough to accommodate the error label
            gridData.widthHint = 500;
            container.setLayoutData(gridData);


            Label nameLabel = new Label(container, SWT.NONE);
            nameLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
            nameLabel.setText("Name:");

            mNameText = new org.eclipse.swt.widgets.Text(container, SWT.BORDER);
            mNameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
            if (mInitialName != null) {
                mNameText.setText(mInitialName);
                mNameText.selectAll();
            }

            Label valueLabel = new Label(container, SWT.NONE);
            valueLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
            valueLabel.setText("Value:");

            mValueText = new org.eclipse.swt.widgets.Text(container, SWT.BORDER);
            mValueText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

            mNameText.addListener(SWT.Modify, this);
            mValueText.addListener(SWT.Modify, this);

            validate();

            return container;
        }

        @Override
        protected void computeResult() {
            mName = mNameText.getText().trim();
            mValue = mValueText.getText().trim();
        }

        private String getName() {
            return mName;
        }

        private String getValue() {
            return mValue;
        }

        public void handleEvent(Event event) {
            validate();
        }

        private void validate() {
            IStatus status;
            computeResult();
            if (mName.length() == 0) {
                status = new Status(IStatus.ERROR, AdtPlugin.PLUGIN_ID, "Enter a name");
            } else if (mValue.length() == 0) {
                status = new Status(IStatus.ERROR, AdtPlugin.PLUGIN_ID, "Enter a value");
            } else {
                if (mValidator == null) {
                    mValidator = ResourceNameValidator.create(false, mProject, mResourceType);
                }
                String error = mValidator.isValid(mName);
                if (error != null) {
                    status = new Status(IStatus.ERROR, AdtPlugin.PLUGIN_ID, error);
                } else {
                    status = new Status(IStatus.OK, AdtPlugin.PLUGIN_ID, null);
                }
            }
            updateStatus(status);
        }
    }
}
