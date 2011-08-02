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

import static com.android.assetstudiolib.LauncherIconGenerator.Options.Shape.CIRCLE;
import static com.android.assetstudiolib.LauncherIconGenerator.Options.Shape.SQUARE;

import com.android.assetstudiolib.GraphicGenerator;
import com.android.assetstudiolib.GraphicGeneratorContext;
import com.android.assetstudiolib.LauncherIconGenerator;
import com.android.assetstudiolib.LauncherIconGenerator.Options.Style;
import com.android.assetstudiolib.TextRenderUtil;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.ImageControl;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.ImageUtils;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.SwtUtils;
import com.android.resources.Density;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * This is normally page 2 of a Create New Asset Set wizard, unless we can offer actions
 * to create a specific asset type, in which case we skip page 1. On this page the user
 * gets to configure the parameters of the asset, and see a preview.
 */
public class ConfigureAssetSetPage extends WizardPage implements SelectionListener,
        GraphicGeneratorContext, ModifyListener {

    private Composite configurationArea;
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

    /**
     * Create the wizard.
     *
     * @param wizard the surrounding wizard
     */
    public ConfigureAssetSetPage() {
        super("configureAssetPage");
        setTitle("Configure Asset Set");
        setDescription("Configure the attributes of the asset set");
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

        configurationArea = new Composite(configurationScrollArea, SWT.NONE);
        GridLayout glConfigurationArea = new GridLayout(3, false);
        glConfigurationArea.marginRight = 15;
        glConfigurationArea.marginWidth = 0;
        glConfigurationArea.marginHeight = 0;
        configurationArea.setLayout(glConfigurationArea);

        Label foregroundLabel = new Label(configurationArea, SWT.NONE);
        foregroundLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        foregroundLabel.setText("Foreground:");

        Composite foregroundComposite = new Composite(configurationArea, SWT.NONE);
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
        new Label(configurationArea, SWT.NONE);

        mForegroundArea = new Composite(configurationArea, SWT.NONE);
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
        new Label(configurationArea, SWT.NONE);

        mTrimCheckBox = new Button(configurationArea, SWT.CHECK);
        mTrimCheckBox.setEnabled(false);
        mTrimCheckBox.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
        mTrimCheckBox.setSelection(true);
        mTrimCheckBox.setText("Trim Surrounding Blank Space");
        new Label(configurationArea, SWT.NONE);

        Label paddingLabel = new Label(configurationArea, SWT.NONE);
        paddingLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
        paddingLabel.setText("Additional Padding:");
        new Label(configurationArea, SWT.NONE);

        mPaddingSlider = new Slider(configurationArea, SWT.NONE);
        mPaddingSlider.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        mPaddingSlider.setEnabled(false);
        // This doesn't work right -- not sure why. For now just use a plain slider
        // and subtract from it to get the real range.
        //mPaddingSlider.setValues(0, -10, 50, 0, 1, 10);
        mPaddingSlider.setSelection(10);
        mPaddingSlider.addSelectionListener(this);

        mPercentLabel = new Label(configurationArea, SWT.NONE);
        mPercentLabel.setText("   0%"); // Enough available space for -10%

        Label scalingLabel = new Label(configurationArea, SWT.NONE);
        scalingLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        scalingLabel.setText("Foreground Scaling:");

        Composite scalingComposite = new Composite(configurationArea, SWT.NONE);
        scalingComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false, 2, 1));
        GridLayout glScalingComposite = new GridLayout(5, false);
        glScalingComposite.horizontalSpacing = 0;
        scalingComposite.setLayout(glScalingComposite);

        mCropRadio = new Button(scalingComposite, SWT.FLAT | SWT.TOGGLE);
        mCropRadio.setSelection(true);
        mCropRadio.setText("Crop");
        mCropRadio.addSelectionListener(this);

        mCenterRadio = new Button(scalingComposite, SWT.FLAT | SWT.TOGGLE);
        mCenterRadio.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 4, 1));
        mCenterRadio.setText("Center");
        mCenterRadio.addSelectionListener(this);

        Label shapeLabel = new Label(configurationArea, SWT.NONE);
        shapeLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        shapeLabel.setText("Shape");

        Composite shapeComposite = new Composite(configurationArea, SWT.NONE);
        shapeComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false, 2, 1));
        GridLayout glShapeComposite = new GridLayout(5, false);
        glShapeComposite.horizontalSpacing = 0;
        shapeComposite.setLayout(glShapeComposite);

        mSquareRadio = new Button(shapeComposite, SWT.FLAT | SWT.TOGGLE);
        mSquareRadio.setSelection(true);
        mSquareRadio.setText("Square");
        mSquareRadio.addSelectionListener(this);

        mCircleButton = new Button(shapeComposite, SWT.FLAT | SWT.TOGGLE);
        mCircleButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 4, 1));
        mCircleButton.setText("Circle");
        mCircleButton.addSelectionListener(this);

        Label bgColorLabel = new Label(configurationArea, SWT.NONE);
        bgColorLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        bgColorLabel.setText("Background Color:");

        mBgButton = new Button(configurationArea, SWT.FLAT);
        mBgButton.addSelectionListener(this);
        mBgButton.setAlignment(SWT.CENTER);
        new Label(configurationArea, SWT.NONE);

        Label fgColorLabel = new Label(configurationArea, SWT.NONE);
        fgColorLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        fgColorLabel.setText("Foreground Color:");

        mFgButton = new Button(configurationArea, SWT.FLAT);
        mFgButton.setAlignment(SWT.CENTER);
        mFgButton.addSelectionListener(this);
        new Label(configurationArea, SWT.NONE);

        Label effectsLabel = new Label(configurationArea, SWT.NONE);
        effectsLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        effectsLabel.setText("Foreground Effects:");

        Composite effectsComposite = new Composite(configurationArea, SWT.NONE);
        effectsComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false, 1, 1));
        GridLayout glEffectsComposite = new GridLayout(5, false);
        glEffectsComposite.horizontalSpacing = 0;
        effectsComposite.setLayout(glEffectsComposite);

        mSimpleRadio = new Button(effectsComposite, SWT.FLAT | SWT.TOGGLE);
        mSimpleRadio.setEnabled(false);
        mSimpleRadio.setSelection(true);
        mSimpleRadio.setText("Simple");
        mSimpleRadio.addSelectionListener(this);

        mFancyRadio = new Button(effectsComposite, SWT.FLAT | SWT.TOGGLE);
        mFancyRadio.setEnabled(false);
        mFancyRadio.setText("Fancy");
        mFancyRadio.addSelectionListener(this);

        mGlossyRadio = new Button(effectsComposite, SWT.FLAT | SWT.TOGGLE);
        mGlossyRadio.setEnabled(false);
        mGlossyRadio.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 3, 1));
        mGlossyRadio.setText("Glossy");
        mGlossyRadio.addSelectionListener(this);

        new Label(configurationArea, SWT.NONE);
        configurationScrollArea.setContent(configurationArea);
        configurationScrollArea.setMinSize(configurationArea.computeSize(SWT.DEFAULT,
                SWT.DEFAULT));

        Label previewLabel = new Label(container, SWT.NONE);
        previewLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1));
        previewLabel.setText("Preview:");

        mPreviewArea = new Composite(container, SWT.BORDER);

        RowLayout rlPreviewAreaPreviewArea = new RowLayout(SWT.VERTICAL);
        rlPreviewAreaPreviewArea.wrap = false;
        rlPreviewAreaPreviewArea.pack = true;
        rlPreviewAreaPreviewArea.center = true;
        rlPreviewAreaPreviewArea.spacing = 0;
        rlPreviewAreaPreviewArea.marginBottom = 0;
        rlPreviewAreaPreviewArea.marginTop = 0;
        rlPreviewAreaPreviewArea.marginRight = 0;
        rlPreviewAreaPreviewArea.marginLeft = 0;
        mPreviewArea.setLayout(rlPreviewAreaPreviewArea);
        GridData gdMPreviewArea = new GridData(SWT.FILL, SWT.FILL, false, false, 1, 1);
        gdMPreviewArea.widthHint = 120;
        mPreviewArea.setLayoutData(gdMPreviewArea);

        // Initial color
        Display display = parent.getDisplay();
        updateColor(display, new RGB(0xa4, 0xc6, 0x39), true /*background*/);
        updateColor(display, new RGB(0x00, 0x00, 0x00), false /*background*/);

        // Start out showing the image form
        //mImageRadio.setSelection(true);
        //chooseForegroundTab(mImageRadio, mImageForm);
        // No, start out showing the text, since the user doesn't have to enter anything
        // initially and we still get images
        mTextRadio.setSelection(true);
        chooseForegroundTab(mTextRadio, mTextForm);

        validatePage();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);

        // We update the image selection here rather than in {@link #createControl} because
        // that method is called when the wizard is created, and we want to wait until the
        // user has chosen a project before attempting to look up the right default image to use
        if (visible) {
            // Initial image - use the most recently used image, or the default launcher
            // icon created in our default projects, if there
            if (sImagePath == null) {
                IProject project = ((CreateAssetSetWizard) getWizard()).getProject();
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
            //error = "Clipart not yet implemented";
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
            mCenterRadio.setSelection(false);
        } else if (source == mCenterRadio) {
            mCropRadio.setSelection(false);
        }
        if (source == mSquareRadio) {
            mCircleButton.setSelection(false);
        } else if (source == mCircleButton) {
            mSquareRadio.setSelection(false);
        }
        if (source == mSimpleRadio) {
            mGlossyRadio.setSelection(false);
            mFancyRadio.setSelection(false);
        } else if (source == mFancyRadio) {
            mSimpleRadio.setSelection(false);
            mGlossyRadio.setSelection(false);
        } else if (source == mGlossyRadio) {
            mSimpleRadio.setSelection(false);
            mFancyRadio.setSelection(false);
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
            FontData data = dialog.open();
            if (data != null) {
                Font font = new Font(mFontButton.getDisplay(), dialog.getFontList());
                mFontButton.setFont(font);
                updateFontLabel(font);
                mFontButton.getParent().pack();
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

        // Always use a large font for the rendering, even though user is typically
        // picking small font sizes in the font chooser
        //int dpi = mFontButton.getDisplay().getDPI().y;
        //int height = (int) Math.round(fontData.getHeight() * dpi / 72.0);
        int height = new TextRenderUtil.Options().fontSize;

        return new java.awt.Font(fontData.getName(), awtStyle, height);
    }

    private int getPadding() {
        // Shifted - see comment for mPaddingSlider construction for an explanation
        return mPaddingSlider.getSelection() - 10;
    }

    public void chooseForegroundTab(Button newButton, Composite newArea) {
        if (newButton.getSelection()) {
            mImageRadio.setSelection(false);
            mClipartRadio.setSelection(false);
            mTextRadio.setSelection(false);
            newButton.setSelection(true);
            StackLayout stackLayout = (StackLayout) mForegroundArea.getLayout();
            stackLayout.topControl = newArea;
            mForegroundArea.layout();
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

        Map<String, BufferedImage> map = generatePreviews();
        for (Map.Entry<String, BufferedImage> entry : map.entrySet()) {
            String id = entry.getKey();
            BufferedImage image = entry.getValue();
            Label nameLabel = new Label(mPreviewArea, SWT.NONE);
            nameLabel.setText(id);

            Image swtImage = SwtUtils.convertToSwt(display, image, true, -1);
            if (swtImage != null) {
                @SuppressWarnings("unused") // Has side effect
                ImageControl imageControl = new ImageControl(mPreviewArea, SWT.NONE, swtImage);
            }
        }

        mPreviewArea.layout(true);
    }

    Map<String, BufferedImage> generatePreviews() {
        // Map of ids to images: Preserve insertion order (the densities)
        Map<String, BufferedImage> imageMap = new LinkedHashMap<String, BufferedImage>();

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
            sourceImage = TextRenderUtil.renderTextImage(text, options);
        } else {
            assert mClipartRadio.getSelection();
            assert mSelectedClipart != null;
            try {
                sourceImage = GraphicGenerator.getClipartImage(mSelectedClipart);
            } catch (IOException e) {
                AdtPlugin.log(e, null);
                return imageMap;
            }
        }

        LauncherIconGenerator.Options options = new LauncherIconGenerator.Options();
        options.shape = mCircleButton.getSelection() ? CIRCLE : SQUARE;
        options.crop = mCropRadio.getSelection();

        int color = (mBgColor.red << 16) | (mBgColor.green << 8) | mBgColor.blue;
        options.backgroundColor = color;
        options.sourceImage = sourceImage;

        Density[] densityValues = Density.values();
        // Sort density values into ascending order
        Arrays.sort(densityValues, new Comparator<Density>() {
            public int compare(Density d1, Density d2) {
                return d1.getDpiValue() - d2.getDpiValue();
            }

        });
        Style[] styleValues = LauncherIconGenerator.Options.Style.values();

        for (Density density : densityValues) {
            if (!density.isValidValueForDevice()) {
                continue;
            }
            if (density == Density.TV) {
                // Not yet supported -- missing stencil image
                continue;
            }
            options.density = density;
            for (LauncherIconGenerator.Options.Style style : styleValues) {
                options.style = style;
                LauncherIconGenerator generator = new LauncherIconGenerator(this, options);
                BufferedImage image = generator.generate();
                if (image != null) {
                    imageMap.put(density.getResourceValue(), image);
                }
            }
        }

        return imageMap;
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
