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
package com.android.ide.eclipse.adt.internal.editors.layout.gle2;

import static com.android.SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX;
import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.SdkConstants.STYLE_RESOURCE_PREFIX;
import static com.android.ide.eclipse.adt.internal.editors.layout.configuration.ConfigurationClient.CHANGED_DEVICE;
import static com.android.ide.eclipse.adt.internal.editors.layout.configuration.ConfigurationClient.CHANGED_FOLDER;
import static com.android.ide.eclipse.adt.internal.editors.layout.configuration.ConfigurationClient.CHANGED_LOCALE;
import static com.android.ide.eclipse.adt.internal.editors.layout.configuration.ConfigurationClient.CHANGED_RENDER_TARGET;
import static com.android.ide.eclipse.adt.internal.editors.layout.configuration.ConfigurationClient.CHANGED_THEME;
import static com.android.ide.eclipse.adt.internal.editors.layout.gle2.ImageUtils.SHADOW_SIZE;
import static com.android.ide.eclipse.adt.internal.editors.layout.gle2.ImageUtils.SMALL_SHADOW_SIZE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.api.Rect;
import com.android.ide.common.rendering.api.RenderSession;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.Result;
import com.android.ide.common.rendering.api.Result.Status;
import com.android.ide.common.resources.ResourceFile;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.ScreenDimensionQualifier;
import com.android.ide.common.resources.configuration.ScreenOrientationQualifier;
import com.android.ide.common.resources.configuration.ScreenSizeQualifier;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.AdtUtils;
import com.android.ide.eclipse.adt.internal.editors.IconFactory;
import com.android.ide.eclipse.adt.internal.editors.descriptors.DocumentDescriptor;
import com.android.ide.eclipse.adt.internal.editors.layout.configuration.Configuration;
import com.android.ide.eclipse.adt.internal.editors.layout.configuration.ConfigurationChooser;
import com.android.ide.eclipse.adt.internal.editors.layout.configuration.ConfigurationClient;
import com.android.ide.eclipse.adt.internal.editors.layout.configuration.ConfigurationDescription;
import com.android.ide.eclipse.adt.internal.editors.layout.configuration.Locale;
import com.android.ide.eclipse.adt.internal.editors.layout.configuration.NestedConfiguration;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.IncludeFinder.Reference;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiDocumentNode;
import com.android.ide.eclipse.adt.internal.resources.ResourceHelper;
import com.android.ide.eclipse.adt.internal.resources.manager.ProjectResources;
import com.android.ide.eclipse.adt.internal.resources.manager.ResourceManager;
import com.android.ide.eclipse.adt.internal.sdk.AndroidTargetData;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.ide.eclipse.adt.io.IFileWrapper;
import com.android.io.IAbstractFile;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ScreenOrientation;
import com.android.sdklib.IAndroidTarget;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.IJobChangeListener;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Region;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.progress.UIJob;
import org.w3c.dom.Document;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;

/**
 * Represents a preview rendering of a given configuration
 */
public class RenderPreview implements IJobChangeListener {
    /** Whether previews should use large shadows */
    static final boolean LARGE_SHADOWS = false;

    /**
     * Still doesn't work; get exceptions from layoutlib:
     * java.lang.IllegalStateException: After scene creation, #init() must be called
     *   at com.android.layoutlib.bridge.impl.RenderAction.acquire(RenderAction.java:151)
     * <p>
     * TODO: Investigate.
     */
    private static final boolean RENDER_ASYNC = false;

    /**
     * Height of the toolbar shown over a preview during hover. Needs to be
     * large enough to accommodate icons below.
     */
    private static final int HEADER_HEIGHT = 20;

    /** Whether to dump out rendering failures of the previews to the log */
    private static final boolean DUMP_RENDER_DIAGNOSTICS = false;

    private static final Image EDIT_ICON;
    private static final Image ZOOM_IN_ICON;
    private static final Image ZOOM_OUT_ICON;
    private static final Image CLOSE_ICON;
    private static final int EDIT_ICON_WIDTH;
    private static final int ZOOM_IN_ICON_WIDTH;
    private static final int ZOOM_OUT_ICON_WIDTH;
    private static final int CLOSE_ICON_WIDTH;
    static {
        ISharedImages sharedImages = PlatformUI.getWorkbench().getSharedImages();
        IconFactory icons = IconFactory.getInstance();
        CLOSE_ICON = sharedImages.getImage(ISharedImages.IMG_ETOOL_DELETE);
        EDIT_ICON = icons.getIcon("editPreview");   //$NON-NLS-1$
        ZOOM_IN_ICON = icons.getIcon("zoomplus");   //$NON-NLS-1$
        ZOOM_OUT_ICON = icons.getIcon("zoomminus"); //$NON-NLS-1$
        CLOSE_ICON_WIDTH = CLOSE_ICON.getImageData().width;
        EDIT_ICON_WIDTH = EDIT_ICON.getImageData().width;
        ZOOM_IN_ICON_WIDTH = ZOOM_IN_ICON.getImageData().width;
        ZOOM_OUT_ICON_WIDTH = ZOOM_OUT_ICON.getImageData().width;
    }

    /** The configuration being previewed */
    private final @NonNull Configuration mConfiguration;

    /** The associated manager */
    private final @NonNull RenderPreviewManager mManager;
    private final @NonNull LayoutCanvas mCanvas;
    private @Nullable ResourceResolver mResourceResolver;
    private @Nullable Job mJob;
    private @Nullable Map<ResourceType, Map<String, ResourceValue>> mConfiguredFrameworkRes;
    private @Nullable Map<ResourceType, Map<String, ResourceValue>> mConfiguredProjectRes;
    private @Nullable Image mThumbnail;
    private @Nullable String mDisplayName;
    private int mWidth;
    private int mHeight;
    private int mX;
    private int mY;
    private double mScale = 1.0;

    /** If non null, points to a separate file containing the source */
    private @Nullable IFile mInput;

    /** If included within another layout, the name of that outer layout */
    private @Nullable Reference mIncludedWithin;

    /** Whether the mouse is actively hovering over this preview */
    private boolean mActive;

    /** Whether this preview cannot be rendered because of a model error - such as
     * an invalid configuration, a missing resource, an error in the XML markup, etc */
    private boolean mError;

    /**
     * Whether this preview presents a file that has been "forked" (separate,
     * not linked) from the primary layout.
     * <p>
     * TODO: Decide if this is redundant and I can just use {@link #mInput} != null
     * instead.
     */
    private boolean mForked;

    /** Whether in the current layout, this preview is visible */
    private boolean mVisible;

    /** Whether the configuration has changed and needs to be refreshed the next time
     * this preview made visible. This corresponds to the change flags in
     * {@link ConfigurationClient}. */
    private int mDirty;

    /**
     * Creates a new {@linkplain RenderPreview}
     *
     * @param manager the manager
     * @param canvas canvas where preview is painted
     * @param configuration the associated configuration
     * @param width the initial width to use for the preview
     * @param height the initial height to use for the preview
     */
    private RenderPreview(
            @NonNull RenderPreviewManager manager,
            @NonNull LayoutCanvas canvas,
            @NonNull Configuration configuration,
            int width,
            int height) {
        mManager = manager;
        mCanvas = canvas;
        mConfiguration = configuration;
        mWidth = width;
        mHeight = height;
    }

    /**
     * Gets the scale being applied to the thumbnail
     *
     * @return the scale being applied to the thumbnail
     */
    public double getScale() {
        return mScale;
    }

    /**
     * Sets the scale to apply to the thumbnail
     *
     * @param scale the factor to scale the thumbnail picture by
     */
    public void setScale(double scale) {
        Image thumbnail = mThumbnail;
        mThumbnail = null;
        if (thumbnail != null) {
            thumbnail.dispose();
        }
        mScale = scale;
    }

    /**
     * Returns whether the preview is actively hovered
     *
     * @return whether the mouse is hovering over the preview
     */
    public boolean isActive() {
        return mActive;
    }

    /**
     * Sets whether the preview is actively hovered
     *
     * @param active if the mouse is hovering over the preview
     */
    public void setActive(boolean active) {
        mActive = active;
    }

    /**
     * Returns whether the preview is visible. Previews that are off
     * screen are typically marked invisible during layout, which means we don't
     * have to expend effort computing preview thumbnails etc
     *
     * @return true if the preview is visible
     */
    public boolean isVisible() {
        return mVisible;
    }

    /**
     * Sets whether this preview represents a forked layout (e.g. a layout which lives
     * in a separate file and is not connected to the main layout)
     *
     * @param forked true if this preview represents a separate file
     */
    public void setForked(boolean forked) {
        mForked = forked;
    }

    /**
     * Returns whether this preview represents a forked layout
     *
     * @return true if this preview represents a separate file
     */
    public boolean isForked() {
        return mForked;
    }

    /**
     * Sets whether the preview is visible. Previews that are off
     * screen are typically marked invisible during layout, which means we don't
     * have to expend effort computing preview thumbnails etc
     *
     * @param visible whether this preview is visible
     */
    public void setVisible(boolean visible) {
        if (visible != mVisible) {
            mVisible = visible;
            if (mVisible) {
                if (mDirty != 0) {
                    // Just made the render preview visible:
                    configurationChanged(mDirty);
                } else {
                    updateForkStatus();
                }
            }
        }
    }

    /**
     * Sets the layout position relative to the top left corner of the preview
     * area, in control coordinates
     */
    void setPosition(int x, int y) {
        mX = x;
        mY = y;
    }

    /**
     * Gets the layout X position relative to the top left corner of the preview
     * area, in control coordinates
     */
    int getX() {
        return mX;
    }

    /**
     * Gets the layout Y position relative to the top left corner of the preview
     * area, in control coordinates
     */
    int getY() {
        return mY;
    }

    /** Determine whether this configuration has a better match in a different layout file */
    private void updateForkStatus() {
        mForked = false;
        mInput = null;
        ConfigurationChooser chooser = mManager.getChooser();
        IFile editedFile = chooser.getEditedFile();
        if (editedFile != null) {
            FolderConfiguration config = mConfiguration.getFullConfig();
            if (!chooser.isBestMatchFor(editedFile, config)) {
                ProjectResources resources = chooser.getResources();
                if (resources != null) {
                    ResourceFile best = resources.getMatchingFile(editedFile.getName(),
                            ResourceFolderType.LAYOUT, config);
                    if (best != null) {
                        IAbstractFile file = best.getFile();
                        if (file instanceof IFileWrapper) {
                            mInput = ((IFileWrapper) file).getIFile();
                        } else if (file instanceof File) {
                            mInput = AdtUtils.fileToIFile(((File) file));
                        }
                    }
                }
                mForked = true;
            }
        }
    }

    /**
     * Creates a new {@linkplain RenderPreview}
     *
     * @param manager the manager
     * @param configuration the associated configuration
     * @return a new configuration
     */
    @NonNull
    public static RenderPreview create(
            @NonNull RenderPreviewManager manager,
            @NonNull Configuration configuration) {
        LayoutCanvas canvas = manager.getCanvas();

        Image image = canvas.getImageOverlay().getImage();

        // Image size
        int screenWidth = 0;
        int screenHeight = 0;
        FolderConfiguration myconfig = configuration.getFullConfig();
        ScreenDimensionQualifier dimension = myconfig.getScreenDimensionQualifier();
        if (dimension != null) {
            screenWidth = dimension.getValue1();
            screenHeight = dimension.getValue2();
            ScreenOrientationQualifier orientation = myconfig.getScreenOrientationQualifier();
            if (orientation != null) {
                ScreenOrientation value = orientation.getValue();
                if (value == ScreenOrientation.PORTRAIT) {
                    int temp = screenWidth;
                    screenWidth = screenHeight;
                    screenHeight = temp;
                }
            }
        } else {
            if (image != null) {
                screenWidth = image.getImageData().width;
                screenHeight = image.getImageData().height;
            }
        }
        int width = RenderPreviewManager.getMaxWidth();
        int height = RenderPreviewManager.getMaxHeight();
        if (screenWidth > 0) {
            double scale = getScale(screenWidth, screenHeight);
            width = (int) (screenWidth * scale);
            height = (int) (screenHeight * scale);
        }

        return new RenderPreview(manager, canvas,
                configuration, width, height);
    }

    /**
     * Throws away this preview: cancels any pending rendering jobs and disposes
     * of image resources etc
     */
    public void dispose() {
        if (mThumbnail != null) {
            mThumbnail.dispose();
            mThumbnail = null;
        }

        if (mJob != null) {
            mJob.cancel();
            mJob = null;
        }
    }

    /**
     * Returns the display name of this preview
     *
     * @return the name of the preview
     */
    @NonNull
    public String getDisplayName() {
        if (mDisplayName == null) {
            String displayName = getConfiguration().getDisplayName();
            if (displayName == null) {
                // No display name: this must be the configuration used by default
                // for the view which is originally displayed (before adding thumbnails),
                // and you've switched away to something else; now we need to display a name
                // for this original configuration. For now, just call it "Original"
                return "Original";
            }

            return displayName;
        }

        return mDisplayName;
    }

    /**
     * Sets the display name of this preview. By default, the display name is
     * the display name of the configuration, but it can be overridden by calling
     * this setter (which only sets the preview name, without editing the configuration.)
     *
     * @param displayName the new display name
     */
    public void setDisplayName(@NonNull String displayName) {
        mDisplayName = displayName;
    }

    /**
     * Sets an inclusion context to use for this layout, if any. This will render
     * the configuration preview as the outer layout with the current layout
     * embedded within.
     *
     * @param includedWithin a reference to a layout which includes this one
     */
    public void setIncludedWithin(Reference includedWithin) {
        mIncludedWithin = includedWithin;
    }

    /**
     * Request a new render after the given delay
     *
     * @param delay the delay to wait before starting the render job
     */
    public void render(long delay) {
        Job job = mJob;
        if (job != null) {
            job.cancel();
        }
        if (RENDER_ASYNC) {
            job = new AsyncRenderJob();
        } else {
            job = new RenderJob();
        }
        job.schedule(delay);
        job.addJobChangeListener(this);
        mJob = job;
    }

    /** Render immediately */
    private void renderSync() {
        if (mThumbnail != null) {
            mThumbnail.dispose();
            mThumbnail = null;
        }

        GraphicalEditorPart editor = mCanvas.getEditorDelegate().getGraphicalEditor();
        ResourceResolver resolver = getResourceResolver();
        FolderConfiguration config = mConfiguration.getFullConfig();
        RenderService renderService = RenderService.create(editor, config, resolver);
        ScreenSizeQualifier screenSize = config.getScreenSizeQualifier();
        renderService.setScreen(screenSize, mConfiguration.getXDpi(), mConfiguration.getYDpi());

        if (mIncludedWithin != null) {
            renderService.setIncludedWithin(mIncludedWithin);
        }

        if (mInput != null) {
            IAndroidTarget target = editor.getRenderingTarget();
            AndroidTargetData data = null;
            if (target != null) {
                Sdk sdk = Sdk.getCurrent();
                if (sdk != null) {
                    data = sdk.getTargetData(target);
                }
            }

            // Construct UI model from XML
            DocumentDescriptor documentDescriptor;
            if (data == null) {
                documentDescriptor = new DocumentDescriptor("temp", null);//$NON-NLS-1$
            } else {
                documentDescriptor = data.getLayoutDescriptors().getDescriptor();
            }
            UiDocumentNode model = (UiDocumentNode) documentDescriptor.createUiNode();
            model.setEditor(mCanvas.getEditorDelegate().getEditor());
            model.setUnknownDescriptorProvider(editor.getModel().getUnknownDescriptorProvider());

            Document document = DomUtilities.getDocument(mInput);
            if (document == null) {
                mError = true;
                return;
            }
            model.loadFromXmlNode(document);
            renderService.setModel(model);
        } else {
            renderService.setModel(editor.getModel());
        }
        Rect rect = Configuration.getScreenBounds(config);
        renderService.setSize(rect.w, rect.h);
        RenderLogger log = new RenderLogger(getDisplayName());
        renderService.setLog(log);
        RenderSession session = renderService.createRenderSession();
        Result render = session.render(1000);

        if (DUMP_RENDER_DIAGNOSTICS) {
            if (log.hasProblems() || !render.isSuccess()) {
                AdtPlugin.log(IStatus.ERROR, "Found problems rendering preview "
                        + getDisplayName() + ": "
                        + render.getErrorMessage() + " : "
                        + log.getProblems(false));
                Throwable exception = render.getException();
                if (exception != null) {
                    AdtPlugin.log(exception, "Failure rendering preview " + getDisplayName());
                }
            }
        }

        mError = !render.isSuccess();

        if (render.getStatus() == Status.ERROR_TIMEOUT) {
            // TODO: Special handling? schedule update again later
            return;
        }
        if (render.isSuccess()) {
            BufferedImage image = session.getImage();
            if (image != null) {
                setFullImage(image);
            }
        }
    }

    private ResourceResolver getResourceResolver() {
        if (mResourceResolver != null) {
            return mResourceResolver;
        }

        GraphicalEditorPart graphicalEditor = mCanvas.getEditorDelegate().getGraphicalEditor();
        String theme = mConfiguration.getTheme();
        if (theme == null) {
            return null;
        }

        mConfiguredFrameworkRes = mConfiguredProjectRes = null;
        mResourceResolver = null;

        FolderConfiguration config = mConfiguration.getFullConfig();
        IAndroidTarget target = graphicalEditor.getRenderingTarget();
        ResourceRepository frameworkRes = null;
        if (target != null) {
            Sdk sdk = Sdk.getCurrent();
            if (sdk == null) {
                return null;
            }
            AndroidTargetData data = sdk.getTargetData(target);

            if (data != null) {
                // TODO: SHARE if possible
                frameworkRes = data.getFrameworkResources();
                mConfiguredFrameworkRes = frameworkRes.getConfiguredResources(config);
            } else {
                return null;
            }
        } else {
            return null;
        }
        assert mConfiguredFrameworkRes != null;


        // get the resources of the file's project.
        ProjectResources projectRes = ResourceManager.getInstance().getProjectResources(
                graphicalEditor.getProject());
        mConfiguredProjectRes = projectRes.getConfiguredResources(config);

        if (!theme.startsWith(PREFIX_RESOURCE_REF)) {
            if (frameworkRes.hasResourceItem(ANDROID_STYLE_RESOURCE_PREFIX + theme)) {
                theme = ANDROID_STYLE_RESOURCE_PREFIX + theme;
            } else {
                theme = STYLE_RESOURCE_PREFIX + theme;
            }
        }

        mResourceResolver = ResourceResolver.create(
                mConfiguredProjectRes, mConfiguredFrameworkRes,
                ResourceHelper.styleToTheme(theme),
                ResourceHelper.isProjectStyle(theme));

        return mResourceResolver;
    }

    /**
     * Sets the new image of the preview and generates a thumbnail
     *
     * @param image the full size image
     */
    void setFullImage(BufferedImage image) {
        if (image == null) {
            mThumbnail = null;
            return;
        }

        //double scale = getScale(image);
        double scale = getWidth() / (double) image.getWidth();
        if (scale < 1.0) {
            if (LARGE_SHADOWS) {
                image = ImageUtils.scale(image, scale, scale,
                        SHADOW_SIZE, SHADOW_SIZE);
                ImageUtils.drawRectangleShadow(image, 0, 0,
                        image.getWidth() - SHADOW_SIZE,
                        image.getHeight() - SHADOW_SIZE);
            } else {
                image = ImageUtils.scale(image, scale, scale,
                        SMALL_SHADOW_SIZE, SMALL_SHADOW_SIZE);
                ImageUtils.drawSmallRectangleShadow(image, 0, 0,
                        image.getWidth() - SMALL_SHADOW_SIZE,
                        image.getHeight() - SMALL_SHADOW_SIZE);
            }
        }

        // Adjust size; for different aspect ratios the height might get adjusted etc
        /*
        if (LARGE_SHADOWS) {
            mWidth = image.getWidth() - SMALL_SHADOW_SIZE;
            mHeight = image.getHeight() - SMALL_SHADOW_SIZE;
        } else {
            mWidth = image.getWidth() - SHADOW_SIZE;
            mHeight = image.getHeight() - SHADOW_SIZE;
        }*/

        mThumbnail = SwtUtils.convertToSwt(mCanvas.getDisplay(), image,
                true /* transferAlpha */, -1);
    }

    private static double getScale(int width, int height) {
        int maxWidth = RenderPreviewManager.getMaxWidth();
        int maxHeight = RenderPreviewManager.getMaxHeight();
        if (width > 0 && height > 0
                && (width > maxWidth || height > maxHeight)) {
            if (width >= height) { // landscape
                return maxWidth / (double) width;
            } else { // portrait
                return maxHeight / (double) height;
            }
        }

        return 1.0;
    }

    /**
     * Returns the width of the preview, in pixels
     *
     * @return the width in pixels
     */
    public int getWidth() {
        return (int) (mScale * mWidth);
    }

    /**
     * Returns the height of the preview, in pixels
     *
     * @return the height in pixels
     */
    public int getHeight() {
        return (int) (mScale * mHeight);
    }

    /**
     * Handles clicks within the preview (x and y are positions relative within the
     * preview
     *
     * @param x the x coordinate within the preview where the click occurred
     * @param y the y coordinate within the preview where the click occurred
     * @return true if this preview handled (and therefore consumed) the click
     */
    public boolean click(int x, int y) {
        if (y < HEADER_HEIGHT) {
            int left = 0;
            left += CLOSE_ICON_WIDTH;
            if (x <= left) {
                // Delete
                mManager.deletePreview(this);
                return true;
            }
            left += ZOOM_IN_ICON_WIDTH;
            if (x <= left) {
                // Zoom in
                mScale = mScale * (1 / 0.5);
                if (Math.abs(mScale-1.0) < 0.0001) {
                    mScale = 1.0;
                }

                render(0);
                mManager.layout(true);
                mCanvas.redraw();
                return true;
            }
            left += ZOOM_OUT_ICON_WIDTH;
            if (x <= left) {
                // Zoom out
                mScale = mScale * (0.5 / 1);
                if (Math.abs(mScale-1.0) < 0.0001) {
                    mScale = 1.0;
                }
                render(0);

                mManager.layout(true);
                mCanvas.redraw();
                return true;
            }
            left += EDIT_ICON_WIDTH;
            if (x <= left) {
                // Edit. For now, just rename
                InputDialog d = new InputDialog(
                        AdtPlugin.getDisplay().getActiveShell(),
                        "Rename Preview",  // title
                        "Name:",
                        getDisplayName(),
                        null);
                if (d.open() == Window.OK) {
                    String newName = d.getValue();
                    mConfiguration.setDisplayName(newName);
                    mCanvas.redraw();
                }

                return true;
            }

            // Clicked anywhere else on header
            // Perhaps open Edit dialog here?
        }

        mManager.switchTo(this);
        return true;
    }

    /**
     * Paints the preview at the given x/y position
     *
     * @param gc the graphics context to paint it into
     * @param x the x coordinate to paint the preview at
     * @param y the y coordinate to paint the preview at
     */
    void paint(GC gc, int x, int y) {
        if (mThumbnail != null) {
            gc.drawImage(mThumbnail, x, y);

            if (mActive) {
                int oldWidth = gc.getLineWidth();
                gc.setLineWidth(3);
                gc.setForeground(gc.getDevice().getSystemColor(SWT.COLOR_LIST_SELECTION));
                gc.drawRectangle(x - 1, y - 1, getWidth() + 2, getHeight() + 2);
                gc.setLineWidth(oldWidth);
            }
        } else if (mError) {
            gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_WIDGET_BORDER));
            gc.drawRectangle(x, y, getWidth(), getHeight());
            Image icon = IconFactory.getInstance().getIcon("renderError"); //$NON-NLS-1$
            ImageData data = icon.getImageData();
            int prevAlpha = gc.getAlpha();
            gc.setAlpha(128-32);
            gc.drawImage(icon, x + (getWidth() - data.width) / 2,
                    y + (getHeight() - data.height) / 2);
            gc.setAlpha(prevAlpha);
        } else {
            gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_WIDGET_BORDER));
            gc.drawRectangle(x, y, getWidth(), getHeight());
            Image icon = IconFactory.getInstance().getIcon("refreshPreview"); //$NON-NLS-1$
            ImageData data = icon.getImageData();
            int prevAlpha = gc.getAlpha();
            gc.setAlpha(128-32);
            gc.drawImage(icon, x + (getWidth() - data.width) / 2,
                    y + (getHeight() - data.height) / 2);
            gc.setAlpha(prevAlpha);
        }

        if (mActive) {
            int left = x ;
            int prevAlpha = gc.getAlpha();
            gc.setAlpha(128+32);
            Color bg = mCanvas.getDisplay().getSystemColor(SWT.COLOR_WHITE);
            gc.setBackground(bg);
            gc.fillRectangle(left, y, x + getWidth() - left, HEADER_HEIGHT);
            gc.setAlpha(prevAlpha);

            // Paint icons
            gc.drawImage(CLOSE_ICON, left, y);
            left += CLOSE_ICON_WIDTH;

            gc.drawImage(ZOOM_IN_ICON, left, y);
            left += ZOOM_IN_ICON_WIDTH;

            gc.drawImage(ZOOM_OUT_ICON, left, y);
            left += ZOOM_OUT_ICON_WIDTH;

            gc.drawImage(EDIT_ICON, left, y);
            left += EDIT_ICON_WIDTH;
        }

        paintTitle(gc, x, y, true /*showFile*/);
    }

    /**
     * Paints the preview title at the given position
     *
     * @param gc the graphics context to paint into
     * @param x the left edge of the preview rectangle
     * @param y the top edge of the preview rectangle
     */
    void paintTitle(GC gc, int x, int y, boolean showFile) {
        String displayName = getDisplayName();
        if (displayName != null && displayName.length() > 0) {
            gc.setForeground(gc.getDevice().getSystemColor(SWT.COLOR_WHITE));

            int width = getWidth();
            int height = getHeight();
            Point extent = gc.textExtent(displayName);
            int labelLeft = Math.max(x, x + (width - extent.x) / 2);
            int labelTop = y + height + 1;
            Image flagImage = null;
            Locale locale = mConfiguration.getLocale();
            if (locale != null && (locale.hasLanguage() || locale.hasRegion())
                    && (!(mConfiguration instanceof NestedConfiguration)
                            || ((NestedConfiguration) mConfiguration).isOverridingLocale())) {
                flagImage = locale.getFlagImage();
            }

            gc.setClipping(x, y, width, height + 100);
            if (flagImage != null) {
                int flagWidth = flagImage.getImageData().width;
                int flagHeight = flagImage.getImageData().height;
                gc.drawImage(flagImage, labelLeft - flagWidth / 2 - 1, labelTop);
                labelLeft += flagWidth / 2 + 1;
                gc.drawText(displayName, labelLeft,
                        labelTop - (extent.y - flagHeight) / 2, true);
            } else {
                gc.drawText(displayName, labelLeft, labelTop, true);
            }

            if (mForked && mInput != null && showFile) {
                // Draw file flag, and parent folder name
                labelTop += extent.y;
                String fileName = mInput.getParent().getName() + File.separator + mInput.getName();
                extent = gc.textExtent(fileName);
                flagImage = IconFactory.getInstance().getIcon("android_file"); //$NON-NLS-1$
                int flagWidth = flagImage.getImageData().width;
                int flagHeight = flagImage.getImageData().height;

                labelLeft = Math.max(x, x + (width - extent.x - flagWidth - 1) / 2);

                gc.drawImage(flagImage, labelLeft, labelTop);

                gc.setForeground(gc.getDevice().getSystemColor(SWT.COLOR_GRAY));
                labelLeft += flagWidth + 1;
                labelTop -= (extent.y - flagHeight) / 2;
                gc.drawText(fileName, labelLeft, labelTop, true);
            }

            gc.setClipping((Region) null);
        }
    }

    /**
     * Notifies that the preview's configuration has changed.
     *
     * @param flags the change flags, a bitmask corresponding to the
     *            {@code CHANGE_} constants in {@link ConfigurationClient}
     */
    public void configurationChanged(int flags) {
        if (!mVisible) {
            mDirty |= flags;
            return;
        }

        if ((flags & (CHANGED_FOLDER | CHANGED_THEME | CHANGED_DEVICE
                | CHANGED_RENDER_TARGET | CHANGED_LOCALE)) != 0) {
            mResourceResolver = null;
            // Handle inheritance
            mConfiguration.syncFolderConfig();
            updateForkStatus();
        }

        FolderConfiguration folderConfig = mConfiguration.getFullConfig();
        ScreenOrientationQualifier qualifier = folderConfig.getScreenOrientationQualifier();
        ScreenOrientation orientation = qualifier == null
                ? ScreenOrientation.PORTRAIT :  qualifier.getValue();
        if (orientation == ScreenOrientation.LANDSCAPE
                || orientation == ScreenOrientation.SQUARE) {
            orientation = ScreenOrientation.PORTRAIT;
        } else {
            orientation = ScreenOrientation.LANDSCAPE;
        }

        if ((mWidth < mHeight && orientation == ScreenOrientation.PORTRAIT)
                || (mWidth > mHeight && orientation == ScreenOrientation.LANDSCAPE)) {
            Image thumbnail = mThumbnail;
            mThumbnail = null;
            if (thumbnail != null) {
                thumbnail.dispose();
            }

            // Flip icon size
            int temp = mHeight;
            mHeight = mWidth;
            mWidth = temp;
        }

        mDirty = 0;
    }

    /**
     * Returns the configuration associated with this preview
     *
     * @return the configuration
     */
    @NonNull
    public Configuration getConfiguration() {
        return mConfiguration;
    }

    // ---- Implements IJobChangeListener ----

    @Override
    public void aboutToRun(IJobChangeEvent event) {
    }

    @Override
    public void awake(IJobChangeEvent event) {
    }

    @Override
    public void done(IJobChangeEvent event) {
        mJob = null;
    }

    @Override
    public void running(IJobChangeEvent event) {
    }

    @Override
    public void scheduled(IJobChangeEvent event) {
    }

    @Override
    public void sleeping(IJobChangeEvent event) {
    }

    // ---- Delayed Rendering ----

    private final class RenderJob extends UIJob {
        public RenderJob() {
            super("RenderPreview");
            setSystem(true);
            setUser(false);
        }

        @Override
        public IStatus runInUIThread(IProgressMonitor monitor) {
            mJob = null;
            if (!mCanvas.isDisposed()) {
                renderSync();
                mCanvas.redraw();
                return org.eclipse.core.runtime.Status.OK_STATUS;
            }

            return org.eclipse.core.runtime.Status.CANCEL_STATUS;
        }

        @Override
        public Display getDisplay() {
            if (mCanvas.isDisposed()) {
                return null;
            }
            return mCanvas.getDisplay();
        }
    }

    private final class AsyncRenderJob extends Job {
        public AsyncRenderJob() {
            super("RenderPreview");
            setSystem(true);
            setUser(false);
        }

        @Override
        protected IStatus run(IProgressMonitor monitor) {
            mJob = null;

            if (mCanvas.isDisposed()) {
                return org.eclipse.core.runtime.Status.CANCEL_STATUS;
            }

            renderSync();

            // Update display
            mCanvas.getDisplay().asyncExec(new Runnable() {
                @Override
                public void run() {
                    mCanvas.redraw();
                }
            });

            return org.eclipse.core.runtime.Status.OK_STATUS;
        }
    }

    /**
     * Sets the input file to use for rendering. If not set, this will just be
     * the same file as the configuration chooser. This is used to render other
     * layouts, such as variations of the currently edited layout, which are
     * not kept in sync with the main layout.
     *
     * @param file the file to set as input
     */
    public void setInput(@Nullable IFile file) {
        mInput = file;
    }

    /** Corresponding description for this preview if it is a manually added preview */
    private @Nullable ConfigurationDescription mDescription;

    /**
     * Sets the description of this preview, if this preview is a manually added preview
     *
     * @param description the description of this preview
     */
    public void setDescription(@Nullable ConfigurationDescription description) {
        mDescription = description;
    }

    /**
     * Returns the description of this preview, if this preview is a manually added preview
     *
     * @return the description
     */
    @Nullable
    public ConfigurationDescription getDescription() {
        return mDescription;
    }
}
