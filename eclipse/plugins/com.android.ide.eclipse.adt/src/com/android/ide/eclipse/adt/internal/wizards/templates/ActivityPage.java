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
package com.android.ide.eclipse.adt.internal.wizards.templates;

import static com.android.ide.eclipse.adt.internal.wizards.templates.NewTemplateWizard.ACTIVITY_TEMPLATES;
import static com.android.ide.eclipse.adt.internal.wizards.templates.TemplateHandler.PREVIEW_PADDING;
import static com.android.ide.eclipse.adt.internal.wizards.templates.TemplateHandler.PREVIEW_WIDTH;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.ImageControl;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;

import java.io.InputStream;

class ActivityPage extends WizardPage implements SelectionListener {
    private final NewProjectWizardState mValues;
    private List mList;
    private Button mCreateToggle;

    private boolean mIgnore;
    private boolean mShown;
    private ImageControl mPreview;
    private Image mPreviewImage;
    private Label mHeading;
    private Label mDescription;

    /**
     * Create the wizard.
     */
    ActivityPage(NewProjectWizardState values) {
        super("activityPage"); //$NON-NLS-1$
        mValues = values;

        setTitle("Create Activity");
        setDescription("Select whether to create an activity, and if so, what kind of activity.");
    }

    @SuppressWarnings("unused") // SWT constructors have side effects and aren't unused
    @Override
    public void createControl(Composite parent) {
        Composite container = new Composite(parent, SWT.NULL);
        setControl(container);
        container.setLayout(new GridLayout(3, false));

        mCreateToggle = new Button(container, SWT.CHECK);
        mCreateToggle.setSelection(true);
        mCreateToggle.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 3, 1));
        mCreateToggle.setText("Create Activity");
        mCreateToggle.addSelectionListener(this);

        mList = new List(container, SWT.BORDER | SWT.V_SCROLL);
        mList.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));
        mList.setItems(ACTIVITY_TEMPLATES);
        int index = -1;
        for (int i = 0; i < ACTIVITY_TEMPLATES.length; i++) {
            if (ACTIVITY_TEMPLATES[i].equals(mValues.activityValues.getTemplateName())) {
                index = i;
                break;
            }
        }
        if (index == -1) {
            mValues.activityValues.setTemplateName(ACTIVITY_TEMPLATES[0]);
            index = 0;
        }
        mList.setSelection(index);
        mList.addSelectionListener(this);

        // Preview
        mPreview = new ImageControl(container, SWT.NONE, null);
        GridData gd_mImage = new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1);
        gd_mImage.widthHint = PREVIEW_WIDTH + 2 * PREVIEW_PADDING;
        mPreview.setLayoutData(gd_mImage);
        new Label(container, SWT.NONE);

        mHeading = new Label(container, SWT.NONE);
        mHeading.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
        new Label(container, SWT.NONE);

        mDescription = new Label(container, SWT.WRAP);
        mDescription.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 2, 1));

        Font font = JFaceResources.getFontRegistry().getBold(JFaceResources.BANNER_FONT);
        if (font != null) {
            mHeading.setFont(font);
        }

        setPreview(mValues.activityValues.getTemplateName());
    }

    private void setPreview(String templateName) {
        Image oldImage = mPreviewImage;
        mPreviewImage = null;

        String title = "";
        String description = "";
        TemplateMetadata template = TemplateHandler.getTemplate(templateName);
        if (template != null) {
            String thumb = template.getThumbnailPath();
            if (thumb != null && !thumb.isEmpty()) {
                String filePath = TemplateHandler.getTemplatePath(templateName) + '/' + thumb;
                InputStream input = AdtPlugin.readEmbeddedFileAsStream(filePath);
                if (input != null) {
                    try {
                        mPreviewImage = new Image(getControl().getDisplay(), input);
                        input.close();
                    } catch (Exception e) {
                        AdtPlugin.log(e, null);
                    }
                }
            }
            title = template.getTitle();
            description = template.getDescription();
        }

        mHeading.setText(title);
        mDescription.setText(description);
        mPreview.setImage(mPreviewImage);
        mPreview.fitToWidth(PREVIEW_WIDTH);

        if (oldImage != null) {
            oldImage.dispose();
        }

        Composite parent = (Composite) getControl();
        parent.layout(true, true);
        parent.redraw();
    }

    @Override
    public void dispose() {
        super.dispose();

        if (mPreviewImage != null) {
            mPreviewImage.dispose();
            mPreviewImage = null;
        }
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);

        if (visible) {
            mShown = true;
            try {
                mIgnore = true;
                mCreateToggle.setSelection(mValues.createActivity);
            } finally {
                mIgnore = false;
            }
        }

        validatePage();
    }


    private void validatePage() {
        IStatus status = null;

        if (status == null || status.getSeverity() != IStatus.ERROR) {
            if (mList.getSelectionCount() < 1) {
                status = new Status(IStatus.ERROR, AdtPlugin.PLUGIN_ID,
                        "Select an activity type");
            }
        }

        setPageComplete(status == null || status.getSeverity() != IStatus.ERROR);
        if (status != null) {
            setMessage(status.getMessage(),
                    status.getSeverity() == IStatus.ERROR
                        ? IMessageProvider.ERROR : IMessageProvider.WARNING);
        } else {
            setErrorMessage(null);
            setMessage(null);
        }
    }

    @Override
    public boolean isPageComplete() {
        if (!mValues.createAppSkeleton) {
            return true;
        }

        // Ensure that the Finish button isn't enabled until
        // the user has reached and completed this page
        if (!mShown) {
            return false;
        }

        return super.isPageComplete();
    }

    // ---- Implements SelectionListener ----

    @Override
    public void widgetSelected(SelectionEvent e) {
        if (mIgnore) {
            return;
        }

        Object source = e.getSource();
        if (source == mCreateToggle) {
            mValues.createActivity = mCreateToggle.getSelection();
            mList.setEnabled(mValues.createActivity);
        } else if (source == mList) {
            int index = mList.getSelectionIndex();
            String[] items = mList.getItems();
            if (index >= 0 && index < items.length) {
                String templateName = items[index];
                mValues.activityValues.setTemplateName(templateName);
                setPreview(templateName);
            }
        }

        validatePage();
    }

    @Override
    public void widgetDefaultSelected(SelectionEvent e) {
    }
}
