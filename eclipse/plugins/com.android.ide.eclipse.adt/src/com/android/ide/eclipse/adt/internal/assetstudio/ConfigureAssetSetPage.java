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

package com.android.ide.eclipse.adt.internal.assetstudio;

import com.android.assetstudiolib.ActionBarIconGenerator;
import com.android.assetstudiolib.GraphicGenerator;
import com.android.assetstudiolib.GraphicGeneratorContext;
import com.android.assetstudiolib.LauncherIconGenerator;
import com.android.assetstudiolib.MenuIconGenerator;
import com.android.assetstudiolib.NotificationIconGenerator;
import com.android.assetstudiolib.TabIconGenerator;
import com.android.assetstudiolib.TextRenderUtil;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.ImageControl;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.ImageUtils;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.SwtUtils;
import com.android.ide.eclipse.adt.internal.editors.manifest.ManifestInfo;
import com.android.util.Pair;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.ColorDialog;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.FontDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Slider;
import org.eclipse.swt.widgets.Text;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.imageio.ImageIO;

/**
 * This is normally page 2 of a Create New Asset Set wizard, unless we can offer actions
 * to create a specific asset type, in which case we skip page 1. On this page the user
 * gets to configure the parameters of the asset, and see a preview.
 */
public class ConfigureAssetSetPage extends WizardPage implements SelectionListener,
        GraphicGeneratorContext, ModifyListener {

    private static final int PREVIEW_AREA_WIDTH = 120;

    /** Whether the alternative launcher icon styles are supported. Right now
     * the generator and stencils produce icons that don't fit within the overall
     * icon guidelines, so until that's fixed disable these from the UI to avoid
     * creating icons that don't fit in.
     */
    private static boolean SUPPORT_LAUNCHER_ICON_TYPES = false;

    private Composite mConfigurationArea;
    private Button mImageRadio;
    private Button mClipartRadio;
    private Button mTextRadio;
    private Button mPickImageButton;
    private Button mTrimCheckBox;
    private Slider mPaddingSlider;
    private Label mPercentLabel;
    private Button mCropRadio;
    private Button mCenterRadio;
    private Button mSquareRadio;
    private Button mCircleButton;
    private Button mBgButton;
    private Button mFgButton;
    private Button mSimpleRadio;
    private Button mFancyRadio;
    private Button mGlossyRadio;
    private Composite mPreviewArea;
    private Button mFontButton;
    private Composite mForegroundArea;
    private Composite mImageForm;
    private Composite mClipartForm;
    private Composite mTextForm;
    private Text mImagePathText;

    private boolean mTimerPending;
    private Map<String, BufferedImage> mImageCache = new HashMap<String, BufferedImage>();
    private RGB mBgColor;
    private RGB mFgColor;
    private Text mText;
    private String mSelectedClipart;

    /** Most recently set image path: preserved across wizard sessions */
    private static String sImagePath;
    private Button mChooseClipart;
    private Composite mClipartPreviewPanel;
    private Label mThemeLabel;
    private Composite mThemeComposite;
    private Button mHoloLightRadio;
    private Button mHoloDarkRadio;
    private Label mScalingLabel;
    private Composite mScalingComposite;
    private Label mShapeLabel;
    private Composite mShapeComposite;
    private Label mBgColorLabel;
    private Label mFgColorLabel;
    private Label mEffectsLabel;
    private Composite mEffectsComposite;

    /**
     * Create the wizard.
     */
    public ConfigureAssetSetPage() {
        super("configureAssetPage");
        setTitle("Configure Icon Set");
        setDescription("Configure the attributes of the icon set");
    }

    /**
     * Create contents of the wizard.
     *
     * @param parent the parent widget
     */
    @SuppressWarnings("unused") // Don't warn about unassigned "new Label(.)": has side-effect
    public void createControl(Composite parent) {
        Composite container = new Composite(parent, SWT.NULL);

        setControl(container);
        GridLayout glContainer = new GridLayout(2, false);
        glContainer.marginWidth = 0;
        glContainer.horizontalSpacing = 0;
        glContainer.marginHeight = 0;
        glContainer.verticalSpacing = 0;
        container.setLayout(glContainer);

        ScrolledComposite configurationScrollArea = new ScrolledComposite(container, SWT.V_SCROLL);
        configurationScrollArea.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 2));
        configurationScrollArea.setExpandHorizontal(true);
        configurationScrollArea.setExpandVertical(true);

        mConfigurationArea = new Composite(configurationScrollArea, SWT.NONE);
        GridLayout glConfigurationArea = new GridLayout(3, false);
        glConfigurationArea.horizontalSpacing = 0;
        glConfigurationArea.marginRight = 15;
        glConfigurationArea.marginWidth = 0;
        glConfigurationArea.marginHeight = 0;
        mConfigurationArea.setLayout(glConfigurationArea);

        Label foregroundLabel = new Label(mConfigurationArea, SWT.NONE);
        foregroundLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        foregroundLabel.setText("Foreground:");

        Composite foregroundComposite = new Composite(mConfigurationArea, SWT.NONE);
        foregroundComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false, 2, 1));
        GridLayout glForegroundComposite = new GridLayout(5, false);
        glForegroundComposite.horizontalSpacing = 0;
        foregroundComposite.setLayout(glForegroundComposite);

        mImageRadio = new Button(foregroundComposite, SWT.FLAT | SWT.TOGGLE);
        mImageRadio.setSelection(false);
        mImageRadio.addSelectionListener(this);
        mImageRadio.setText("Image");

        mClipartRadio = new Button(foregroundComposite, SWT.FLAT | SWT.TOGGLE);
        //mClipartRadio.setEnabled(false);
        mClipartRadio.setText("Clipart");
        mClipartRadio.addSelectionListener(this);

        mTextRadio = new Button(foregroundComposite, SWT.FLAT | SWT.TOGGLE);
        mTextRadio.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 3, 1));
        mTextRadio.setText("Text");
        mTextRadio.addSelectionListener(this);
        new Label(mConfigurationArea, SWT.NONE);

        mForegroundArea = new Composite(mConfigurationArea, SWT.NONE);
        mForegroundArea.setLayout(new StackLayout());
        mForegroundArea.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));

        mImageForm = new Composite(mForegroundArea, SWT.NONE);
        mImageForm.setLayout(new GridLayout(3, false));

        Label fileLabel = new Label(mImageForm, SWT.NONE);
        fileLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        fileLabel.setText("Image File:");

        mImagePathText = new Text(mImageForm, SWT.BORDER);
        GridData pathLayoutData = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
        pathLayoutData.widthHint = 200;
        mImagePathText.setLayoutData(pathLayoutData);
        mImagePathText.addSelectionListener(this);
        mImagePathText.addModifyListener(this);

        mPickImageButton = new Button(mImageForm, SWT.FLAT);
        mPickImageButton.setText("Browse...");
        mPickImageButton.addSelectionListener(this);

        mClipartForm = new Composite(mForegroundArea, SWT.NONE);
        mClipartForm.setLayout(new GridLayout(2, false));

        mChooseClipart = new Button(mClipartForm, SWT.FLAT);
        mChooseClipart.setText("Choose...");
        mChooseClipart.addSelectionListener(this);

        mClipartPreviewPanel = new Composite(mClipartForm, SWT.NONE);
        RowLayout rlClipartPreviewPanel = new RowLayout(SWT.HORIZONTAL);
        rlClipartPreviewPanel.marginBottom = 0;
        rlClipartPreviewPanel.marginTop = 0;
        rlClipartPreviewPanel.center = true;
        mClipartPreviewPanel.setLayout(rlClipartPreviewPanel);
        mClipartPreviewPanel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));

        mTextForm = new Composite(mForegroundArea, SWT.NONE);
        mTextForm.setLayout(new GridLayout(2, false));

        Label textLabel = new Label(mTextForm, SWT.NONE);
        textLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        textLabel.setText("Text:");

        mText = new Text(mTextForm, SWT.BORDER);
        mText.setText("Aa");
        mText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        mText.addModifyListener(this);

        Label fontLabel = new Label(mTextForm, SWT.NONE);
        fontLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        fontLabel.setText("Font:");

        mFontButton = new Button(mTextForm, SWT.FLAT);
        mFontButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
        mFontButton.addSelectionListener(this);
        mFontButton.setText("Choose Font...");
        new Label(mConfigurationArea, SWT.NONE);

        mTrimCheckBox = new Button(mConfigurationArea, SWT.CHECK);
        mTrimCheckBox.setEnabled(false);
        mTrimCheckBox.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
        mTrimCheckBox.setSelection(false);
        mTrimCheckBox.setText("Trim Surrounding Blank Space");
        new Label(mConfigurationArea, SWT.NONE);

        Label paddingLabel = new Label(mConfigurationArea, SWT.NONE);
        paddingLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
        paddingLabel.setText("Additional Padding:");
        new Label(mConfigurationArea, SWT.NONE);

        mPaddingSlider = new Slider(mConfigurationArea, SWT.NONE);
        mPaddingSlider.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        // This doesn't work right -- not sure why. For now just use a plain slider
        // and subtract 10 from it to get the real range.
        //mPaddingSlider.setValues(0, -10, 50, 0, 1, 10);
        mPaddingSlider.setSelection(10 + 15);
        mPaddingSlider.addSelectionListener(this);

        mPercentLabel = new Label(mConfigurationArea, SWT.NONE);
        mPercentLabel.setText("  15%"); // Enough available space for -10%

        mScalingLabel = new Label(mConfigurationArea, SWT.NONE);
        mScalingLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        mScalingLabel.setText("Foreground Scaling:");

        mScalingComposite = new Composite(mConfigurationArea, SWT.NONE);
        mScalingComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false, 2, 1));
        GridLayout gl_mScalingComposite = new GridLayout(5, false);
        gl_mScalingComposite.horizontalSpacing = 0;
        mScalingComposite.setLayout(gl_mScalingComposite);

        mCropRadio = new Button(mScalingComposite, SWT.FLAT | SWT.TOGGLE);
        mCropRadio.setSelection(true);
        mCropRadio.setText("Crop");
        mCropRadio.addSelectionListener(this);

        mCenterRadio = new Button(mScalingComposite, SWT.FLAT | SWT.TOGGLE);
        mCenterRadio.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 4, 1));
        mCenterRadio.setText("Center");
        mCenterRadio.addSelectionListener(this);

        mShapeLabel = new Label(mConfigurationArea, SWT.NONE);
        mShapeLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        mShapeLabel.setText("Shape");

        mShapeComposite = new Composite(mConfigurationArea, SWT.NONE);
        mShapeComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false, 2, 1));
        GridLayout gl_mShapeComposite = new GridLayout(5, false);
        gl_mShapeComposite.horizontalSpacing = 0;
        mShapeComposite.setLayout(gl_mShapeComposite);

        mSquareRadio = new Button(mShapeComposite, SWT.FLAT | SWT.TOGGLE);
        mSquareRadio.setSelection(true);
        mSquareRadio.setText("Square");
        mSquareRadio.addSelectionListener(this);

        mCircleButton = new Button(mShapeComposite, SWT.FLAT | SWT.TOGGLE);
        mCircleButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 4, 1));
        mCircleButton.setText("Circle");
        mCircleButton.addSelectionListener(this);

        mThemeLabel = new Label(mConfigurationArea, SWT.NONE);
        mThemeLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        mThemeLabel.setText("Theme");

        mThemeComposite = new Composite(mConfigurationArea, SWT.NONE);
        mThemeComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        GridLayout gl_mThemeComposite = new GridLayout(2, false);
        gl_mThemeComposite.horizontalSpacing = 0;
        mThemeComposite.setLayout(gl_mThemeComposite);

        mHoloLightRadio = new Button(mThemeComposite, SWT.FLAT | SWT.TOGGLE);
        mHoloLightRadio.setText("Holo Light");
        mHoloLightRadio.setSelection(true);
        mHoloLightRadio.addSelectionListener(this);

        mHoloDarkRadio = new Button(mThemeComposite, SWT.FLAT | SWT.TOGGLE);
        mHoloDarkRadio.setText("Holo Dark");
        mHoloDarkRadio.addSelectionListener(this);

        mBgColorLabel = new Label(mConfigurationArea, SWT.NONE);
        mBgColorLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        mBgColorLabel.setText("Background Color:");

        mBgButton = new Button(mConfigurationArea, SWT.FLAT);
        mBgButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
        mBgButton.addSelectionListener(this);
        mBgButton.setAlignment(SWT.CENTER);

        mFgColorLabel = new Label(mConfigurationArea, SWT.NONE);
        mFgColorLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        mFgColorLabel.setText("Foreground Color:");

        mFgButton = new Button(mConfigurationArea, SWT.FLAT);
        mFgButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
        mFgButton.setAlignment(SWT.CENTER);
        mFgButton.addSelectionListener(this);

        if (SUPPORT_LAUNCHER_ICON_TYPES) {
            mEffectsLabel = new Label(mConfigurationArea, SWT.NONE);
            mEffectsLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
            mEffectsLabel.setText("Foreground Effects:");

            mEffectsComposite = new Composite(mConfigurationArea, SWT.NONE);
            mEffectsComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false, 2, 1));
            GridLayout gl_mEffectsComposite = new GridLayout(5, false);
            gl_mEffectsComposite.horizontalSpacing = 0;
            mEffectsComposite.setLayout(gl_mEffectsComposite);

            mSimpleRadio = new Button(mEffectsComposite, SWT.FLAT | SWT.TOGGLE);
            mSimpleRadio.setSelection(true);
            mSimpleRadio.setText("Simple");
            mSimpleRadio.addSelectionListener(this);

            mFancyRadio = new Button(mEffectsComposite, SWT.FLAT | SWT.TOGGLE);
            mFancyRadio.setText("Fancy");
            mFancyRadio.addSelectionListener(this);

            mGlossyRadio = new Button(mEffectsComposite, SWT.FLAT | SWT.TOGGLE);
            mGlossyRadio.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 3, 1));
            mGlossyRadio.setText("Glossy");
            mGlossyRadio.addSelectionListener(this);
        }

        configurationScrollArea.setContent(mConfigurationArea);
        configurationScrollArea.setMinSize(mConfigurationArea.computeSize(SWT.DEFAULT,
                SWT.DEFAULT));

        Label previewLabel = new Label(container, SWT.NONE);
        previewLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1));
        previewLabel.setText("Preview:");

        mPreviewArea = new Composite(container, SWT.BORDER);

        RowLayout rlPreviewAreaPreviewArea = new RowLayout(SWT.HORIZONTAL);
        rlPreviewAreaPreviewArea.wrap = true;
        rlPreviewAreaPreviewArea.pack = true;
        rlPreviewAreaPreviewArea.center = true;
        rlPreviewAreaPreviewArea.spacing = 0;
        rlPreviewAreaPreviewArea.marginBottom = 0;
        rlPreviewAreaPreviewArea.marginTop = 0;
        rlPreviewAreaPreviewArea.marginRight = 0;
        rlPreviewAreaPreviewArea.marginLeft = 0;
        mPreviewArea.setLayout(rlPreviewAreaPreviewArea);
        GridData gdMPreviewArea = new GridData(SWT.FILL, SWT.FILL, false, false, 1, 1);
        gdMPreviewArea.widthHint = PREVIEW_AREA_WIDTH;
        mPreviewArea.setLayoutData(gdMPreviewArea);

        // Initial color
        Display display = parent.getDisplay();
        //updateColor(display, new RGB(0xa4, 0xc6, 0x39), true /*background*/);
        updateColor(display, new RGB(0xff, 0x00, 0x00), true /*background*/);
        updateColor(display, new RGB(0x00, 0x00, 0x00), false /*background*/);

        // Start out showing the image form
        //mImageRadio.setSelection(true);
        //chooseForegroundTab(mImageRadio, mImageForm);
        // No, start out showing the text, since the user doesn't have to enter anything
        // initially and we still get images
        mTextRadio.setSelection(true);
        chooseForegroundTab(mTextRadio, mTextForm);
        new Label(mConfigurationArea, SWT.NONE);
        new Label(mConfigurationArea, SWT.NONE);
        new Label(mConfigurationArea, SWT.NONE);

        validatePage();
    }

    void configureAssetType(AssetType type) {
        showGroup(type.needsForegroundScaling(), mScalingLabel, mScalingComposite);
        showGroup(type.needsShape(), mShapeLabel, mShapeComposite);
        showGroup(type.needsTheme(), mThemeLabel, mThemeComposite);
        showGroup(type.needsColors(), mBgColorLabel, mBgButton);
        showGroup(type.needsColors(), mFgColorLabel, mFgButton);
        if (SUPPORT_LAUNCHER_ICON_TYPES) {
            showGroup(type.needsEffects(), mEffectsLabel, mEffectsComposite);
        }

        Composite parent = mScalingLabel.getParent();
        parent.pack();
        parent.layout();
    }

    private static void showGroup(boolean show, Control control1, Control control2) {
        showControl(show, control1);
        showControl(show, control2);
    }

    private static void showControl(boolean show, Control control) {
        Object data = control.getLayoutData();
        if (data instanceof GridData) {
            GridData gridData = (GridData) data;
            gridData.exclude = !show;
        }
        control.setVisible(show);
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);

        // We update the image selection here rather than in {@link #createControl} because
        // that method is called when the wizard is created, and we want to wait until the
        // user has chosen a project before attempting to look up the right default image to use
        if (visible) {
            // Clear out old previews - important if the user goes back to page one, changes
            // asset type and steps into page 2 - at that point we arrive here and we might
            // display the old previews for a brief period until the preview delay timer expires.
            for (Control c : mPreviewArea.getChildren()) {
                c.dispose();
            }
            mPreviewArea.layout(true);

            // Update asset type configuration: will show/hide parameter controls depending
            // on which asset type is chosen
            CreateAssetSetWizard wizard = (CreateAssetSetWizard) getWizard();
            AssetType type = wizard.getAssetType();
            assert type != null;
            configureAssetType(type);

            // Initial image - use the most recently used image, or the default launcher
            // icon created in our default projects, if there
            if (sImagePath == null) {
                IProject project = wizard.getProject();
                if (project != null) {
                    IResource icon = project.findMember("res/drawable-hdpi/icon.png"); //$NON-NLS-1$
                    if (icon != null) {
                        IWorkspaceRoot workspace = ResourcesPlugin.getWorkspace().getRoot();
                        IPath workspacePath = workspace.getLocation();
                        sImagePath = workspacePath.append(icon.getFullPath()).toOSString();
                    }
                }
            }
            if (sImagePath != null) {
                mImagePathText.setText(sImagePath);
            }
            validatePage();

            requestUpdatePreview(true /*quickly*/);

            if (mTextRadio.getSelection()) {
                mText.setFocus();
            }
        }
    }

    private boolean validatePage() {
        String error = null;
        //String warning = null;

        if (mImageRadio.getSelection()) {
            String path = mImagePathText.getText().trim();
            if (path.length() == 0) {
                error = "Select an image";
            } else if (!(new File(path).exists())) {
                error = String.format("%1$s does not exist", path);
            } else {
                // Preserve across wizard sessions
                sImagePath = path;
            }
        } else if (mTextRadio.getSelection()) {
            String text = mText.getText().trim();
            if (text.length() == 0) {
                error = "Enter text";
            }
        } else {
            assert mClipartRadio.getSelection();
            if (mSelectedClipart == null) {
                error = "Select clip art";
            }
        }

        setPageComplete(error == null);
        if (error != null) {
            setMessage(error, IMessageProvider.ERROR);
        //} else if (warning != null) {
        //    setMessage(warning, IMessageProvider.WARNING);
        } else {
            setErrorMessage(null);
            setMessage(null);
        }

        return error == null;
    }

    @Override
    public boolean isPageComplete() {
        // Force user to reach second page before hitting Finish
        return isCurrentPage();
    }

    // ---- Implements ModifyListener ----

    public void modifyText(ModifyEvent e) {
        if (e.getSource() == mImagePathText) {
            requestUpdatePreview(false);
        } else if (e.getSource() == mText) {
            requestUpdatePreview(false);
        }

        validatePage();
    }

    // ---- Implements SelectionListener ----

    public void widgetDefaultSelected(SelectionEvent e) {
        // Nothing to do
    }

    public void widgetSelected(SelectionEvent e) {
        Object source = e.getSource();
        boolean updateQuickly = true;

        // Tabs
        if (source == mImageRadio) {
            chooseForegroundTab((Button) source, mImageForm);
        } else if (source == mClipartRadio) {
            chooseForegroundTab((Button) source, mClipartForm);
        } else if (source == mTextRadio) {
            updateFontLabel(mFontButton.getFont());
            chooseForegroundTab((Button) source, mTextForm);
            mText.setFocus();
        }

        // Choose image file
        if (source == mPickImageButton) {
            FileDialog dialog = new FileDialog(mPickImageButton.getShell(), SWT.OPEN);
            String file = dialog.open();
            if (file != null) {
                mImagePathText.setText(file);
            }
        }

        // Enforce Radio Groups
        if (source == mCropRadio) {
            mCropRadio.setSelection(true); // Ensure that you can't toggle it off
            mCenterRadio.setSelection(false);
        } else if (source == mCenterRadio) {
            mCenterRadio.setSelection(true);
            mCropRadio.setSelection(false);
        }
        if (source == mSquareRadio) {
            mSquareRadio.setSelection(true);
            mCircleButton.setSelection(false);
        } else if (source == mCircleButton) {
            mCircleButton.setSelection(true);
            mSquareRadio.setSelection(false);
        }

        if (SUPPORT_LAUNCHER_ICON_TYPES) {
            if (source == mSimpleRadio) {
                mSimpleRadio.setSelection(true);
                mGlossyRadio.setSelection(false);
                mFancyRadio.setSelection(false);
            } else if (source == mFancyRadio) {
                mFancyRadio.setSelection(true);
                mSimpleRadio.setSelection(false);
                mGlossyRadio.setSelection(false);
            } else if (source == mGlossyRadio) {
                mGlossyRadio.setSelection(true);
                mSimpleRadio.setSelection(false);
                mFancyRadio.setSelection(false);
            }
        }

        if (source == mHoloDarkRadio) {
            mHoloDarkRadio.setSelection(true);
            mHoloLightRadio.setSelection(false);
        } else if (source == mHoloLightRadio) {
            mHoloLightRadio.setSelection(true);
            mHoloDarkRadio.setSelection(false);
        }

        if (source == mChooseClipart) {
            MessageDialog dialog = new MessageDialog(mChooseClipart.getShell(),
                    "Choose Clip Art",
                    null, "Choose Clip Art Image:", MessageDialog.NONE,
                    new String[] { "Close" }, 0) {
                @Override
                protected Control createCustomArea(Composite parent) {
                    // Outer form which just establishes a width for the inner form which
                    // wraps in a RowLayout
                    Composite outer = new Composite(parent, SWT.NONE);
                    GridLayout gridLayout = new GridLayout();
                    outer.setLayout(gridLayout);

                    Composite chooserForm = new Composite(outer, SWT.NONE);
                    GridData gd = new GridData();
                    gd.grabExcessVerticalSpace = true;
                    gd.widthHint = 450;
                    chooserForm.setLayoutData(gd);
                    RowLayout clipartFormLayout = new RowLayout(SWT.HORIZONTAL);
                    clipartFormLayout.center = true;
                    clipartFormLayout.wrap = true;
                    chooserForm.setLayout(clipartFormLayout);

                    MouseAdapter clickListener = new MouseAdapter() {
                        @SuppressWarnings("unused")
                        @Override
                        public void mouseDown(MouseEvent event) {
                            // Clicked on some of the sample art
                            if (event.widget instanceof ImageControl) {
                                ImageControl image = (ImageControl) event.widget;
                                mSelectedClipart = (String) image.getData();
                                close();

                                for (Control c : mClipartPreviewPanel.getChildren()) {
                                    c.dispose();
                                }
                                if (mClipartPreviewPanel.getChildren().length == 0) {
                                    try {
                                        BufferedImage icon =
                                                GraphicGenerator.getClipartIcon(mSelectedClipart);
                                        if (icon != null) {
                                            Display display = mClipartForm.getDisplay();
                                            Image swtImage = SwtUtils.convertToSwt(display, icon,
                                                    false, -1);
                                            new ImageControl(mClipartPreviewPanel,
                                                    SWT.NONE, swtImage);
                                        }
                                    } catch (IOException e1) {
                                        AdtPlugin.log(e1, null);
                                    }
                                    mClipartPreviewPanel.pack();
                                    mClipartPreviewPanel.layout();
                                }

                                updatePreview();
                            }
                        }
                    };
                    Display display = chooserForm.getDisplay();
                    Color hoverColor = display.getSystemColor(SWT.COLOR_RED);
                    Iterator<String> clipartImages = GraphicGenerator.getClipartNames();
                    while (clipartImages.hasNext()) {
                        String name = clipartImages.next();
                        try {
                            BufferedImage icon = GraphicGenerator.getClipartIcon(name);
                            if (icon != null) {
                                Image swtImage = SwtUtils.convertToSwt(display, icon, false, -1);
                                ImageControl img = new ImageControl(chooserForm,
                                        SWT.NONE, swtImage);
                                img.setData(name);
                                img.setHoverColor(hoverColor);
                                img.addMouseListener(clickListener);
                            }
                        } catch (IOException e1) {
                            AdtPlugin.log(e1, null);
                        }
                    }
                    outer.pack();
                    outer.layout();
                    return outer;
                }
            };
            dialog.open();
        }

        if (source == mBgButton) {
            ColorDialog dlg = new ColorDialog(mBgButton.getShell());
            dlg.setRGB(mBgColor);
            dlg.setText("Choose a new Background Color");
            RGB rgb = dlg.open();
            if (rgb != null) {
                // Dispose the old color, create the
                // new one, and set into the label
                updateColor(mBgButton.getDisplay(), rgb, true /*background*/);
            }
        } else if (source == mFgButton) {
            ColorDialog dlg = new ColorDialog(mFgButton.getShell());
            dlg.setRGB(mFgColor);
            dlg.setText("Choose a new Foreground Color");
            RGB rgb = dlg.open();
            if (rgb != null) {
                // Dispose the old color, create the
                // new one, and set into the label
                updateColor(mFgButton.getDisplay(), rgb, false /*background*/);
            }
        }

        if (source == mFontButton) {
            FontDialog dialog = new FontDialog(mFontButton.getShell());
            FontData[] fontList;
            if (mFontButton.getData() == null) {
                fontList = mFontButton.getDisplay().getFontList("Helvetica", true /*scalable*/);
            } else {
                fontList = mFontButton.getFont().getFontData();
            }
            dialog.setFontList(fontList);
            FontData data = dialog.open();
            if (data != null) {
                Font font = new Font(mFontButton.getDisplay(), dialog.getFontList());
                mFontButton.setFont(font);
                updateFontLabel(font);
                mFontButton.getParent().pack();
                // Mark the font on the button as custom (since the renderer needs to
                // distinguish between this font and the default font it starts out with)
                mFontButton.setData(Boolean.TRUE);
            }
        }

        if (source == mPaddingSlider) {
            mPercentLabel.setText(Integer.toString(getPadding()) + '%');

            // When dragging the slider, only do periodic updates
            updateQuickly = false;
        }

        requestUpdatePreview(updateQuickly);
    }

    private void updateFontLabel(Font f) {
        FontData[] fd = f.getFontData();
        FontData primary = fd[0];
        String description = String.format("%1$s", primary.getName());
        mFontButton.setText(description);
    }

    private java.awt.Font getSelectedFont() {
        // Always use a large font for the rendering, even though user is typically
        // picking small font sizes in the font chooser
        //int dpi = mFontButton.getDisplay().getDPI().y;
        //int height = (int) Math.round(fontData.getHeight() * dpi / 72.0);
        int fontHeight = new TextRenderUtil.Options().fontSize;

        if (mFontButton.getData() == null) {
            // The user has not yet picked a font; look up the default font to use
            // (Helvetica Bold, not whatever font happened to be used for button widgets
            // in SWT on this platform)
            return new java.awt.Font("Helvetica", java.awt.Font.BOLD, fontHeight); //$NON-NLS-1$
        }

        Font font = mFontButton.getFont();
        FontData fontData = font.getFontData()[0];

        int awtStyle = java.awt.Font.PLAIN;
        int swtStyle = fontData.getStyle();

        if ((swtStyle & SWT.ITALIC) != 0) {
            awtStyle |= java.awt.Font.ITALIC;
        }
        if ((swtStyle & SWT.BOLD) != 0) {
            awtStyle = java.awt.Font.BOLD;
        }

        return new java.awt.Font(fontData.getName(), awtStyle, fontHeight);
    }

    private int getPadding() {
        // Shifted - see comment for mPaddingSlider construction for an explanation
        return mPaddingSlider.getSelection() - 10;
    }

    private void chooseForegroundTab(Button newButton, Composite newArea) {
        if (newButton.getSelection()) {
            mImageRadio.setSelection(false);
            mClipartRadio.setSelection(false);
            mTextRadio.setSelection(false);
            newButton.setSelection(true);
            StackLayout stackLayout = (StackLayout) mForegroundArea.getLayout();
            stackLayout.topControl = newArea;
            mForegroundArea.layout();
        } else {
            // Treat it as a radio button: you can't click to turn it off, you have to
            // click on one of the other buttons
            newButton.setSelection(true);
        }
    }

    /**
     * Delay updates of the preview, to ensure that the SWT UI acts immediately (to handle
     * radio group selections etc).
     *
     * @param quickly if true, update the previews soon, otherwise schedule one a bit later
     */
    private void requestUpdatePreview(boolean quickly) {
        if (mTimerPending) {
            return;
        }
        mTimerPending = true;

        final Runnable timer = new Runnable() {
            public void run() {
                mTimerPending = false;
                updatePreview();
            }
        };

        mPreviewArea.getDisplay().timerExec(quickly ? 10 : 250, timer);
    }

    private void updatePreview() {
        Display display = mPreviewArea.getDisplay();

        for (Control c : mPreviewArea.getChildren()) {
            c.dispose();
        }

        if (!validatePage()) {
            return;
        }

        Map<String, Map<String, BufferedImage>> map = generateImages(true /*previewOnly*/);
        for (Entry<String, Map<String, BufferedImage>> categoryEntry : map.entrySet()) {
            String category = categoryEntry.getKey();
            if (category.length() > 0) {
                Label nameLabel = new Label(mPreviewArea, SWT.NONE);
                nameLabel.setText(String.format("%1$s:", category));
                RowData rowData = new RowData();
                nameLabel.setLayoutData(rowData);
                // Ensure these get their own rows
                rowData.width = PREVIEW_AREA_WIDTH;
            }

            Map<String, BufferedImage> images = categoryEntry.getValue();
            for (Entry<String, BufferedImage> entry :  images.entrySet()) {
                BufferedImage image = entry.getValue();
                Image swtImage = SwtUtils.convertToSwt(display, image, true, -1);
                if (swtImage != null) {
                    @SuppressWarnings("unused") // Has side effect
                    ImageControl imageControl = new ImageControl(mPreviewArea, SWT.NONE, swtImage);
                }
            }
        }

        mPreviewArea.layout(true);
    }

    Map<String, Map<String, BufferedImage>> generateImages(boolean previewOnly) {
        // Map of ids to images: Preserve insertion order (the densities)
        Map<String, Map<String, BufferedImage>> categoryMap =
                new LinkedHashMap<String, Map<String, BufferedImage>>();

        CreateAssetSetWizard wizard = (CreateAssetSetWizard) getWizard();
        AssetType type = wizard.getAssetType();

        BufferedImage sourceImage = null;
        if (mImageRadio.getSelection()) {
            // Load the image
            // TODO: Only do this when the source image type is image
            String path = mImagePathText.getText().trim();
            if (path.length() == 0) {
                setErrorMessage("Enter a filename");
                return Collections.emptyMap();
            }
            File file = new File(path);
            if (!file.exists()) {
                setErrorMessage(String.format("%1$s does not exist", file.getPath()));
                return Collections.emptyMap();
            }

            setErrorMessage(null);
            sourceImage = getImage(path, false);
        } else if (mTextRadio.getSelection()) {
            String text = mText.getText();
            TextRenderUtil.Options options = new TextRenderUtil.Options();
            options.font = getSelectedFont();
            int color;
            if (type.needsColors()) {
                color = 0xFF000000 | (mFgColor.red << 16) | (mFgColor.green << 8) | mFgColor.blue;
            } else {
                color = 0xFFFFFFFF;
            }
            options.foregroundColor = color;
            sourceImage = TextRenderUtil.renderTextImage(text, getPadding(), options);
        } else {
            assert mClipartRadio.getSelection();
            assert mSelectedClipart != null;
            try {
                sourceImage = GraphicGenerator.getClipartImage(mSelectedClipart);
            } catch (IOException e) {
                AdtPlugin.log(e, null);
                return categoryMap;
            }
        }

        GraphicGenerator generator = null;
        GraphicGenerator.Options options = null;
        switch (type) {
            case LAUNCHER: {
                generator = new LauncherIconGenerator();
                LauncherIconGenerator.LauncherOptions launcherOptions =
                        new LauncherIconGenerator.LauncherOptions();
                launcherOptions.shape = mCircleButton.getSelection()
                        ? GraphicGenerator.Shape.CIRCLE : GraphicGenerator.Shape.SQUARE;
                launcherOptions.crop = mCropRadio.getSelection();

                if (SUPPORT_LAUNCHER_ICON_TYPES) {
                    launcherOptions.style = mFancyRadio.getSelection() ?
                        GraphicGenerator.Style.FANCY : mGlossyRadio.getSelection()
                                ? GraphicGenerator.Style.GLOSSY : GraphicGenerator.Style.SIMPLE;
                } else {
                    launcherOptions.style = GraphicGenerator.Style.SIMPLE;
                }

                int color = (mBgColor.red << 16) | (mBgColor.green << 8) | mBgColor.blue;
                launcherOptions.backgroundColor = color;
                // Flag which tells the generator iterator to include a web graphic
                launcherOptions.isWebGraphic = !previewOnly;
                options = launcherOptions;

                break;
            }
            case MENU:
                generator = new MenuIconGenerator();
                options = new GraphicGenerator.Options();
                break;
            case ACTIONBAR: {
                generator = new ActionBarIconGenerator();
                ActionBarIconGenerator.ActionBarOptions actionBarOptions =
                        new ActionBarIconGenerator.ActionBarOptions();
                actionBarOptions.theme = mHoloDarkRadio.getSelection()
                        ? ActionBarIconGenerator.Theme.HOLO_DARK
                                : ActionBarIconGenerator.Theme.HOLO_LIGHT;

                options = actionBarOptions;
                break;
            }
            case NOTIFICATION: {
                generator = new NotificationIconGenerator();
                NotificationIconGenerator.NotificationOptions notificationOptions =
                        new NotificationIconGenerator.NotificationOptions();
                notificationOptions.shape = mCircleButton.getSelection()
                        ? GraphicGenerator.Shape.CIRCLE : GraphicGenerator.Shape.SQUARE;
                options = notificationOptions;
                break;
            }
            case TAB:
                generator = new TabIconGenerator();
                options = new TabIconGenerator.TabOptions();
                break;
            default:
                AdtPlugin.log(IStatus.ERROR, "Unsupported asset type: %1$s", type);
                return categoryMap;
        }

        options.sourceImage = sourceImage;

        IProject project = wizard.getProject();
        Pair<Integer, Integer> v = ManifestInfo.computeSdkVersions(project);
        options.minSdk = v.getFirst();

        String baseName = wizard.getBaseName();
        generator.generate(null, categoryMap, this, options, baseName);

        return categoryMap;
    }

    private void updateColor(Display display, RGB color, boolean isBackground) {
        // Button.setBackgroundColor does not work (at least not on OSX) so
        // we instead have to use Button.setImage with an image of the given
        // color
        BufferedImage coloredImage = ImageUtils.createColoredImage(60, 20, color);
        Image image = SwtUtils.convertToSwt(display, coloredImage, false, -1);

        if (isBackground) {
            mBgColor = color;
            mBgButton.setImage(image);
        } else {
            mFgColor = color;
            mFgButton.setImage(image);
        }
    }

    public BufferedImage loadImageResource(String relativeName) {
        return getImage(relativeName, true);
    }

    private BufferedImage getImage(String path, boolean isPluginRelative) {
        BufferedImage image = mImageCache.get(path);
        if (image == null) {
            try {
                if (isPluginRelative) {
                    image = GraphicGenerator.getStencilImage(path);
                } else {
                    File file = new File(path);

                    // Requires Batik
                    //if (file.getName().endsWith(DOT_SVG)) {
                    //    image = loadSvgImage(file);
                    //}

                    if (image == null) {
                        image = ImageIO.read(file);
                    }
                }
            } catch (IOException e) {
                setErrorMessage(e.getLocalizedMessage());
            }

            if (image == null) {
                image = new BufferedImage(1,1, BufferedImage.TYPE_INT_ARGB);
            }

            mImageCache.put(path, image);
        }

        return image;
    }

    // This requires Batik for SVG rendering
    //
    //public static BufferedImage loadSvgImage(File file) {
    //    BufferedImageTranscoder transcoder = new BufferedImageTranscoder();
    //
    //    String svgURI = file.toURI().toString();
    //    TranscoderInput input = new TranscoderInput(svgURI);
    //
    //    try {
    //        transcoder.transcode(input, null);
    //    } catch (TranscoderException e) {
    //        e.printStackTrace();
    //        return null;
    //    }
    //
    //    return transcoder.decodedImage;
    //}
    //
    ///**
    // * A dummy implementation of an {@link ImageTranscoder} that simply stores the {@link
    // * BufferedImage} generated by the SVG library.
    // */
    //private static class BufferedImageTranscoder extends ImageTranscoder {
    //    public BufferedImage decodedImage;
    //
    //    @Override
    //    public BufferedImage createImage(int w, int h) {
    //        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    //    }
    //
    //    @Override
    //    public void writeImage(BufferedImage image, TranscoderOutput output)
    //            throws TranscoderException {
    //        this.decodedImage = image;
    //    }
    //}
}
