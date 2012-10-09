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

import static com.android.ide.eclipse.adt.internal.editors.layout.gle2.ImageUtils.SHADOW_SIZE;
import static com.android.ide.eclipse.adt.internal.editors.layout.gle2.ImageUtils.SMALL_SHADOW_SIZE;
import static com.android.ide.eclipse.adt.internal.editors.layout.gle2.RenderPreview.LARGE_SHADOWS;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.ide.common.resources.configuration.DeviceConfigHelper;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.LanguageQualifier;
import com.android.ide.common.resources.configuration.ScreenSizeQualifier;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.AdtUtils;
import com.android.ide.eclipse.adt.internal.editors.layout.configuration.ComplementingConfiguration;
import com.android.ide.eclipse.adt.internal.editors.layout.configuration.Configuration;
import com.android.ide.eclipse.adt.internal.editors.layout.configuration.ConfigurationChooser;
import com.android.ide.eclipse.adt.internal.editors.layout.configuration.ConfigurationClient;
import com.android.ide.eclipse.adt.internal.editors.layout.configuration.Locale;
import com.android.ide.eclipse.adt.internal.editors.layout.configuration.NestedConfiguration;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.IncludeFinder.Reference;
import com.android.ide.eclipse.adt.internal.preferences.AdtPrefs;
import com.android.resources.Density;
import com.android.resources.ScreenSize;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.Screen;
import com.android.sdklib.devices.State;
import com.google.common.collect.Lists;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.ScrollBar;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manager for the configuration previews, which handles layout computations,
 * managing the image buffer cache, etc
 */
public class RenderPreviewManager {
    private static double sScale = 1.0;
    private static final int RENDER_DELAY = 100;
    private static final int PREVIEW_VGAP = 18;
    private static final int PREVIEW_HGAP = 12;
    private static final int MAX_WIDTH = 200;
    private static final int MAX_HEIGHT = MAX_WIDTH;
    private @Nullable List<RenderPreview> mPreviews;
    private @Nullable RenderPreviewList mManualList;
    private final @NonNull LayoutCanvas mCanvas;
    private final @NonNull CanvasTransform mVScale;
    private final @NonNull CanvasTransform mHScale;
    private int mPrevCanvasWidth;
    private int mPrevCanvasHeight;
    private int mPrevImageWidth;
    private int mPrevImageHeight;
    private @NonNull RenderPreviewMode mMode = RenderPreviewMode.NONE;
    private @Nullable RenderPreview mActivePreview;
    private @Nullable ScrollBarListener mListener;
    private int mLayoutHeight;
    private int mMaxVisibleY;
    /** Last seen state revision in this {@link RenderPreviewManager}. If less
     * than {@link #sRevision}, the previews need to be updated on next exposure */
    private static int mRevision;
    /** Current global revision count */
    private static int sRevision;

    /**
     * Creates a {@link RenderPreviewManager} associated with the given canvas
     *
     * @param canvas the canvas to manage previews for
     */
    public RenderPreviewManager(@NonNull LayoutCanvas canvas) {
        mCanvas = canvas;
        mHScale = canvas.getHorizontalTransform();
        mVScale = canvas.getVerticalTransform();
    }

    /**
     * Revise the global state revision counter. This will cause all layout
     * preview managers to refresh themselves to the latest revision when they
     * are next exposed.
     */
    public static void bumpRevision() {
        sRevision++;
    }

    /**
     * Returns the associated chooser
     *
     * @return the associated chooser
     */
    @NonNull
    ConfigurationChooser getChooser() {
        GraphicalEditorPart editor = mCanvas.getEditorDelegate().getGraphicalEditor();
        return editor.getConfigurationChooser();
    }

    /**
     * Returns the associated canvas
     *
     * @return the canvas
     */
    @NonNull
    public LayoutCanvas getCanvas() {
        return mCanvas;
    }

    /** Zooms in (grows all previews) */
    public void zoomIn() {
        sScale = sScale * (1 / 0.9);
        if (Math.abs(sScale-1.0) < 0.0001) {
            sScale = 1.0;
        }

        updatedZoom();
    }

    /** Zooms out (shrinks all previews) */
    public void zoomOut() {
        sScale = sScale * (0.9 / 1);
        if (Math.abs(sScale-1.0) < 0.0001) {
            sScale = 1.0;
        }
        updatedZoom();
    }

    private void updatedZoom() {
        if (hasPreviews()) {
            for (RenderPreview preview : mPreviews) {
                preview.setScale(sScale);
            }
            RenderPreview preview = mCanvas.getPreview();
            if (preview != null) {
                preview.setScale(sScale);
            }
        }

        renderPreviews();
        layout(true);
        mCanvas.redraw();
    }

    static int getMaxWidth() {
        return (int) sScale * MAX_WIDTH;
    }

    static int getMaxHeight() {
        return (int) sScale * MAX_HEIGHT;
    }

    /** Delete all the previews */
    public void deleteManualPreviews() {
        disposePreviews();
        selectMode(RenderPreviewMode.NONE);
        mCanvas.setFitScale(true /* onlyZoomOut */, true /*allowZoomIn*/);

        if (mManualList != null) {
            mManualList.delete();
        }
    }

    /** Dispose all the previews */
    public void disposePreviews() {
        if (mPreviews != null) {
            List<RenderPreview> old = mPreviews;
            mPreviews = null;
            for (RenderPreview preview : old) {
                preview.dispose();
            }
        }
    }

    /**
     * Deletes the given preview
     *
     * @param preview the preview to be deleted
     */
    public void deletePreview(RenderPreview preview) {
        mPreviews.remove(preview);
        preview.dispose();
        layout(true);
        mCanvas.redraw();

        if (mManualList != null) {
            mManualList.remove(preview);
            saveList();
        }
    }

    /**
     * Compute the total width required for the previews, including internal padding
     *
     * @return total width in pixels
     */
    public int computePreviewWidth() {
        int maxPreviewWidth = 0;
        if (hasPreviews()) {
            for (RenderPreview preview : mPreviews) {
                maxPreviewWidth = Math.max(maxPreviewWidth, preview.getWidth());
            }

            if (maxPreviewWidth > 0) {
                maxPreviewWidth += 2 * PREVIEW_HGAP; // 2x for left and right side
                maxPreviewWidth += LARGE_SHADOWS ? SHADOW_SIZE : SMALL_SHADOW_SIZE;
            }

            return maxPreviewWidth;
        }

        return 0;
    }

    /**
     * Layout Algorithm. This sets the {@link RenderPreview#getX()} and
     * {@link RenderPreview#getY()} coordinates of all the previews. It also marks
     * previews as visible or invisible via {@link RenderPreview#setVisible(boolean)}
     * according to their position and the current visible view port in the layout canvas.
     * Finally, it also sets the {@code mMaxVisibleY} and {@code mLayoutHeight} fields,
     * such that the scrollbars can compute the right scrolled area, and that scrolling
     * can cause render refreshes on views that are made visible.
     *
     * <p>
     * Two shapes to fill. The screen is typically wide. When showing a phone,
     * I should use all the vertical space; I then show thumbnails on the right.
     * When showing the tablet, I need to do something in between. I reserve at least
     * 200 pixels either on the right or on the bottom and use the remainder.
     * TODO: Look up better algorithms. Optimal space division algorithm. Can prune etc.
     * <p>
     * This is not a traditional bin packing problem, because the objects to be packaged
     * do not have a fixed size; we can scale them up and down in order to provide an
     * "optimal" size.
     * <p>
     * See http://en.wikipedia.org/wiki/Packing_problem
     * See http://en.wikipedia.org/wiki/Bin_packing_problem
     * <p>
     * Returns true if the layout changed (so a redraw is desired)
     */
    boolean layout(boolean refresh) {
        if (mPreviews == null || mPreviews.isEmpty()) {
            return false;
        }

        if (mListener == null) {
            mListener = new ScrollBarListener();
            mCanvas.getVerticalBar().addSelectionListener(mListener);
        }

        // TODO: Separate layout heuristics for portrait and landscape orientations (though
        // it also depends on the dimensions of the canvas window, which determines the
        // shape of the leftover space)

        int scaledImageWidth = mHScale.getScaledImgSize();
        int scaledImageHeight = mVScale.getScaledImgSize();
        Rectangle clientArea = mCanvas.getClientArea();

        if (!refresh &&
                (scaledImageWidth == mPrevImageWidth
                && scaledImageHeight == mPrevImageHeight
                && clientArea.width == mPrevCanvasWidth
                && clientArea.height == mPrevCanvasHeight)) {
            // No change
            return false;
        }

        mPrevImageWidth = scaledImageWidth;
        mPrevImageHeight = scaledImageHeight;
        mPrevCanvasWidth = clientArea.width;
        mPrevCanvasHeight = clientArea.height;

        int availableWidth = clientArea.x + clientArea.width - getX();
        int availableHeight = clientArea.y + clientArea.height - getY();
        int maxVisibleY = clientArea.y + clientArea.height;

        int bottomBorder = scaledImageHeight;
        int rightHandSide = scaledImageWidth + PREVIEW_HGAP;
        int nextY = 0;

        // First lay out images across the top right hand side
        int x = rightHandSide;
        int y = 0;
        boolean wrapped = false;

        int vgap = PREVIEW_VGAP;
        for (RenderPreview preview : mPreviews) {
            // If we have forked previews, double the vgap to allow space for two labels
            if (preview.isForked()) {
                vgap *= 2;
                break;
            }
        }

        for (RenderPreview preview : mPreviews) {
            if (x > 0 && x + preview.getWidth() > availableWidth) {
                x = rightHandSide;
                int prevY = y;
                y = nextY;
                if ((prevY <= bottomBorder ||
                        y <= bottomBorder)
                            && Math.max(nextY, y + preview.getHeight()) > bottomBorder) {
                    // If there's really no visible room below, don't bother
                    // Similarly, don't wrap individually scaled views
                    if (bottomBorder < availableHeight - 40 && preview.getScale() < 1.2) {
                        // If it's closer to the top row than the bottom, just
                        // mark the next row for left justify instead
                        if (bottomBorder - y > y + preview.getHeight() - bottomBorder) {
                            rightHandSide = 0;
                            wrapped = true;
                        } else if (!wrapped) {
                            y = nextY = Math.max(nextY, bottomBorder + vgap);
                            x = rightHandSide = 0;
                            wrapped = true;
                        }
                    }
                }
            }
            if (x > 0 && y <= bottomBorder
                    && Math.max(nextY, y + preview.getHeight()) > bottomBorder) {
                if (clientArea.height - bottomBorder < preview.getHeight()) {
                    // No room below the device on the left; just continue on the
                    // bottom row
                } else if (preview.getScale() < 1.2) {
                    if (bottomBorder - y > y + preview.getHeight() - bottomBorder) {
                        rightHandSide = 0;
                        wrapped = true;
                    } else {
                        y = nextY = Math.max(nextY, bottomBorder + vgap);
                        x = rightHandSide = 0;
                        wrapped = true;
                    }
                }
            }

            preview.setPosition(x, y);

            if (y > maxVisibleY) {
                preview.setVisible(false);
            } else if (!preview.isVisible()) {
                preview.render(RENDER_DELAY);
                preview.setVisible(true);
            }

            x += preview.getWidth();
            x += PREVIEW_HGAP;
            nextY = Math.max(nextY, y + preview.getHeight() + vgap);
        }

        mLayoutHeight = nextY;
        mMaxVisibleY = maxVisibleY;
        mCanvas.updateScrollBars();

        return true;
    }

    /**
     * Paints the configuration previews
     *
     * @param gc the graphics context to paint into
     */
    void paint(GC gc) {
        if (hasPreviews()) {
            // Ensure up to date at all times; consider moving if it's too expensive
            layout(false);
            int rootX = getX();
            int rootY = getY();

            for (RenderPreview preview : mPreviews) {
                if (preview.isVisible()) {
                    int x = rootX + preview.getX();
                    int y = rootY + preview.getY();
                    preview.paint(gc, x, y);
                }
            }

            RenderPreview preview = mCanvas.getPreview();
            if (preview != null) {
                CanvasTransform hi = mHScale;
                CanvasTransform vi = mVScale;

                int destX = hi.translate(0);
                int destY = vi.translate(0);
                int destWidth = hi.getScaledImgSize();
                int destHeight = vi.getScaledImgSize();

                int x = destX + destWidth / 2 - preview.getWidth() / 2;
                int y = destY + destHeight - preview.getHeight();
                preview.paintTitle(gc, x, y, false /*showFile*/);
            }
        } else if (mMode == RenderPreviewMode.CUSTOM) {
            int rootX = getX();
            rootX += mHScale.getScaledImgSize();
            rootX += 2 * PREVIEW_HGAP;
            int rootY = getY();
            rootY += 20;
            gc.setFont(mCanvas.getFont());
            gc.setForeground(mCanvas.getDisplay().getSystemColor(SWT.COLOR_BLACK));
            gc.drawText("Add previews with \"Add as Thumbnail\"\nin the configuration menu",
                    rootX, rootY, true);
        }
    }

    private void addPreview(@NonNull RenderPreview preview) {
        if (mPreviews == null) {
            mPreviews = Lists.newArrayList();
        }
        mPreviews.add(preview);
    }

    /** Adds the current configuration as a new configuration preview */
    public void addAsThumbnail() {
        ConfigurationChooser chooser = getChooser();
        String name = chooser.getConfiguration().getDisplayName();
        if (name == null || name.isEmpty()) {
            name = getUniqueName();
        }
        InputDialog d = new InputDialog(
                AdtPlugin.getDisplay().getActiveShell(),
                "Add as Thumbnail Preview",  // title
                "Name of thumbnail:",
                name,
                null);
        if (d.open() == Window.OK) {
            selectMode(RenderPreviewMode.CUSTOM);

            String newName = d.getValue();
            // Create a new configuration from the current settings in the composite
            Configuration configuration = Configuration.copy(chooser.getConfiguration());
            configuration.setDisplayName(newName);

            RenderPreview preview = RenderPreview.create(this, configuration);
            addPreview(preview);

            layout(true);
            preview.render(RENDER_DELAY);
            mCanvas.setFitScale(true /* onlyZoomOut */, false /*allowZoomIn*/);

            if (mManualList == null) {
                loadList();
            }
            if (mManualList != null) {
                mManualList.add(preview);
                saveList();
            }
        }
    }

    /**
     * Computes a unique new name for a configuration preview that represents
     * the current, default configuration
     *
     * @return a unique name
     */
    private String getUniqueName() {
        if (mPreviews == null || mPreviews.isEmpty()) {
            // NO, not for the first preview!
            return "Config1";
        }

        Set<String> names = new HashSet<String>(mPreviews.size());
        for (RenderPreview preview : mPreviews) {
            names.add(preview.getDisplayName());
        }

        int index = 2;
        while (true) {
            String name = String.format("Config%1$d", index);
            if (!names.contains(name)) {
                return name;
            }
            index++;
        }
    }

    /** Generates a bunch of default configuration preview thumbnails */
    public void addDefaultPreviews() {
        ConfigurationChooser chooser = getChooser();
        Configuration parent = chooser.getConfiguration();
        if (parent instanceof NestedConfiguration) {
            parent = ((NestedConfiguration) parent).getParent();
        }
        if (mCanvas.getImageOverlay().getImage() != null) {
            // Create Language variation
            createLocaleVariation(chooser, parent);

            // Vary screen size
            // TODO: Be smarter here: Pick a screen that is both as differently as possible
            // from the current screen as well as also supported. So consider
            // things like supported screens, targetSdk etc.
            createScreenVariations(parent);

            // Vary orientation
            createStateVariation(chooser, parent);

            // Vary render target
            createRenderTargetVariation(chooser, parent);
        }

        // Make a placeholder preview for the current screen, in case we switch from it
        RenderPreview preview = RenderPreview.create(this, parent);
        mCanvas.setPreview(preview);

        sortPreviewsByOrientation();
    }

    private void createRenderTargetVariation(ConfigurationChooser chooser, Configuration parent) {
        /* This is disabled for now: need to load multiple versions of layoutlib.
        When I did this, there seemed to be some drug interactions between
        them, and I would end up with NPEs in layoutlib code which normally works.
        ComplementingConfiguration configuration =
                ComplementingConfiguration.create(chooser, parent);
        configuration.setOverrideTarget(true);
        configuration.syncFolderConfig();
        addPreview(RenderPreview.create(this, configuration));
        */
    }

    private void createStateVariation(ConfigurationChooser chooser, Configuration parent) {
        State currentState = parent.getDeviceState();
        State nextState = parent.getNextDeviceState(currentState);
        if (nextState != currentState) {
            ComplementingConfiguration configuration =
                    ComplementingConfiguration.create(chooser, parent);
            configuration.setOverrideDeviceState(true);
            configuration.setDeviceState(nextState, false);
            addPreview(RenderPreview.create(this, configuration));
        }
    }

    private void createLocaleVariation(ConfigurationChooser chooser, Configuration parent) {
        LanguageQualifier currentLanguage = parent.getLocale().language;
        for (Locale locale : chooser.getLocaleList()) {
            LanguageQualifier language = locale.language;
            if (!language.equals(currentLanguage)) {
                ComplementingConfiguration configuration =
                        ComplementingConfiguration.create(chooser, parent);
                configuration.setOverrideLocale(true);
                Locale otherLanguage = Locale.create(language);
                configuration.setLocale(otherLanguage, false);
                addPreview(RenderPreview.create(this, configuration));
                break;
            }
        }
    }

    private void createScreenVariations(Configuration parent) {
        ConfigurationChooser chooser = getChooser();
        ComplementingConfiguration configuration;

        configuration = ComplementingConfiguration.create(chooser, parent);
        configuration.setVariation(0);
        configuration.setOverrideDevice(true);
        configuration.syncFolderConfig();
        addPreview(RenderPreview.create(this, configuration));

        configuration = ComplementingConfiguration.create(chooser, parent);
        configuration.setVariation(1);
        configuration.setOverrideDevice(true);
        configuration.syncFolderConfig();
        addPreview(RenderPreview.create(this, configuration));
    }

    /**
     * Returns the current mode as seen by this {@link RenderPreviewManager}.
     * Note that it may not yet have been synced with the global mode kept in
     * {@link AdtPrefs#getRenderPreviewMode()}.
     *
     * @return the current preview mode
     */
    @NonNull
    public RenderPreviewMode getMode() {
        return mMode;
    }

    /**
     * Update the set of previews for the current mode
     *
     * @param force force a refresh even if the preview type has not changed
     * @return true if the views were recomputed, false if the previews were
     *         already showing and the mode not changed
     */
    public boolean recomputePreviews(boolean force) {
        RenderPreviewMode newMode = AdtPrefs.getPrefs().getRenderPreviewMode();
        if (newMode == mMode && !force
                && (mRevision == sRevision
                    || mMode == RenderPreviewMode.NONE
                    || mMode == RenderPreviewMode.CUSTOM)) {
            return false;
        }

        mMode = newMode;
        mRevision = sRevision;

        sScale = 1.0;
        disposePreviews();

        switch (mMode) {
            case DEFAULT:
                addDefaultPreviews();
                break;
            case INCLUDES:
                addIncludedInPreviews();
                break;
            case LOCALES:
                addLocalePreviews();
                break;
            case SCREENS:
                addScreenSizePreviews();
                break;
            case VARIATIONS:
                addVariationPreviews();
                break;
            case CUSTOM:
                addManualPreviews();
                break;
            case NONE:
                break;
            default:
                assert false : mMode;
        }

        layout(true);
        renderPreviews();
        boolean allowZoomIn = mMode == RenderPreviewMode.NONE;
        mCanvas.setFitScale(true /*onlyZoomOut*/, allowZoomIn);
        mCanvas.updateScrollBars();

        return true;
    }

    /**
     * Sets the new render preview mode to use
     *
     * @param mode the new mode
     */
    public void selectMode(@NonNull RenderPreviewMode mode) {
        if (mode != mMode) {
            AdtPrefs.getPrefs().setPreviewMode(mode);
            recomputePreviews(false);
        }
    }

    /** Similar to {@link #addDefaultPreviews()} but for locales */
    public void addLocalePreviews() {

        ConfigurationChooser chooser = getChooser();
        List<Locale> locales = chooser.getLocaleList();
        Configuration parent = chooser.getConfiguration();

        for (Locale locale : locales) {
            if (!locale.hasLanguage() && !locale.hasRegion()) {
                continue;
            }
            NestedConfiguration configuration = NestedConfiguration.create(chooser, parent);
            configuration.setOverrideLocale(true);
            configuration.setLocale(locale, false);

            String displayName = ConfigurationChooser.getLocaleLabel(chooser, locale, false);
            assert displayName != null; // it's never non null when locale is non null
            configuration.setDisplayName(displayName);

            addPreview(RenderPreview.create(this, configuration));
        }

        // Make a placeholder preview for the current screen, in case we switch from it
        Configuration configuration = parent;
        Locale locale = configuration.getLocale();
        String label = ConfigurationChooser.getLocaleLabel(chooser, locale, false);
        if (label == null) {
            label = "default";
        }
        configuration.setDisplayName(label);
        RenderPreview preview = RenderPreview.create(this, parent);
        if (preview != null) {
            mCanvas.setPreview(preview);
        }

        // No need to sort: they should all be identical
    }

    /** Similar to {@link #addDefaultPreviews()} but for screen sizes */
    public void addScreenSizePreviews() {
        ConfigurationChooser chooser = getChooser();
        List<Device> devices = chooser.getDeviceList();
        Configuration configuration = chooser.getConfiguration();

        // Rearrange the devices a bit such that the most interesting devices bubble
        // to the front
        // 10" tablet, 7" tablet, reference phones, tiny phone, and in general the first
        // version of each seen screen size
        List<Device> sorted = new ArrayList<Device>(devices);
        Set<ScreenSize> seenSizes = new HashSet<ScreenSize>();
        State currentState = configuration.getDeviceState();
        String currentStateName = currentState != null ? currentState.getName() : "";

        for (int i = 0, n = sorted.size(); i < n; i++) {
            Device device = sorted.get(i);
            boolean interesting = false;

            State state = device.getState(currentStateName);
            if (state == null) {
                state = device.getAllStates().get(0);
            }

            if (device.getName().startsWith("Nexus ")         //$NON-NLS-1$
                    || device.getName().endsWith(" Nexus")) { //$NON-NLS-1$
                // Not String#contains("Nexus") because that would also pick up all the generic
                // entries ("3.7in WVGA (Nexus One)") so we'd have them duplicated
                interesting = true;
            }

            FolderConfiguration c = DeviceConfigHelper.getFolderConfig(state);
            if (c != null) {
                ScreenSizeQualifier sizeQualifier = c.getScreenSizeQualifier();
                if (sizeQualifier != null) {
                    ScreenSize size = sizeQualifier.getValue();
                    if (!seenSizes.contains(size)) {
                        seenSizes.add(size);
                        interesting = true;
                    }
                }

                // Omit LDPI, not really used anymore
                DensityQualifier density = c.getDensityQualifier();
                if (density != null) {
                    Density d = density.getValue();
                    if (Density.LOW.equals(d)) {
                        interesting = false;
                    }
                }
            }

            if (interesting) {
                NestedConfiguration screenConfig = NestedConfiguration.create(chooser,
                        configuration);
                screenConfig.setOverrideDevice(true);
                screenConfig.setDevice(device, true);
                screenConfig.syncFolderConfig();
                screenConfig.setDisplayName(ConfigurationChooser.getDeviceLabel(device, true));
                addPreview(RenderPreview.create(this, screenConfig));
            }
        }

        // Sorted by screen size, in decreasing order
        sortPreviewsByScreenSize();
    }

    /**
     * Previews this layout as included in other layouts
     */
    public void addIncludedInPreviews() {
        ConfigurationChooser chooser = getChooser();
        IProject project = chooser.getProject();
        if (project == null) {
            return;
        }
        IncludeFinder finder = IncludeFinder.get(project);

        final List<Reference> includedBy = finder.getIncludedBy(chooser.getEditedFile());

        if (includedBy == null || includedBy.isEmpty()) {
            // TODO: Generate some useful defaults, such as including it in a ListView
            // as the list item layout?
            return;
        }

        for (final Reference reference : includedBy) {
            String title = reference.getDisplayName();
            Configuration config = Configuration.create(chooser, reference.getFile());
            RenderPreview preview = RenderPreview.create(this, config);
            preview.setDisplayName(title);
            preview.setIncludedWithin(reference);

            addPreview(preview);
        }

        sortPreviewsByOrientation();
    }

    /**
     * Previews this layout as included in other layouts
     */
    public void addVariationPreviews() {
        ConfigurationChooser chooser = getChooser();

        IFile file = chooser.getEditedFile();
        List<IFile> variations = AdtUtils.getResourceVariations(file, false /*includeSelf*/);

        // Sort by parent folder
        Collections.sort(variations, new Comparator<IFile>() {
            @Override
            public int compare(IFile file1, IFile file2) {
                return file1.getParent().getName().compareTo(file2.getParent().getName());
            }
        });

        for (IFile variation : variations) {
            String title = variation.getParent().getName();
            Configuration config = Configuration.create(chooser, variation);
            RenderPreview preview = RenderPreview.create(this, config);
            preview.setDisplayName(title);
            preview.setInput(variation);

            addPreview(preview);
        }

        sortPreviewsByOrientation();
    }

    /**
     * Previews this layout using a custom configured set of layouts
     */
    public void addManualPreviews() {
        if (mManualList == null) {
            loadList();
        } else {
            mPreviews = mManualList.createPreviews(mCanvas);
        }
    }

    private void loadList() {
        IProject project = getChooser().getProject();
        if (project == null) {
            return;
        }

        if (mManualList == null) {
            mManualList = RenderPreviewList.get(project);
        }

        try {
            mManualList.load(getChooser().getDeviceList());
            mPreviews = mManualList.createPreviews(mCanvas);
        } catch (IOException e) {
            AdtPlugin.log(e, null);
        }
    }

    private void saveList() {
        if (mManualList != null) {
            try {
                mManualList.save();
            } catch (IOException e) {
                AdtPlugin.log(e, null);
            }
        }
    }

    /**
     * Notifies that the main configuration has changed.
     *
     * @param flags the change flags, a bitmask corresponding to the
     *            {@code CHANGE_} constants in {@link ConfigurationClient}
     */
    public void configurationChanged(int flags) {
        // Similar to renderPreviews, but only acts on incomplete previews
        if (hasPreviews()) {
            long delay = 0;
            // Do zoomed images first
            for (RenderPreview preview : mPreviews) {
                if (preview.getScale() > 1.2) {
                    preview.configurationChanged(flags);
                    delay += RENDER_DELAY;
                    preview.render(delay);
                }
            }
            for (RenderPreview preview : mPreviews) {
                if (preview.getScale() <= 1.2) {
                    preview.configurationChanged(flags);
                    delay += RENDER_DELAY;
                    preview.render(delay);
                }
            }
            RenderPreview preview = mCanvas.getPreview();
            if (preview != null) {
                preview.configurationChanged(flags);
                preview.dispose();
            }
            layout(true);
            mCanvas.redraw();
        }
    }

    /** Updates the configuration preview thumbnails */
    public void renderPreviews() {
        if (hasPreviews()) {
            long delay = 0;
            // Do zoomed images first
            for (RenderPreview preview : mPreviews) {
                if (preview.getScale() > 1.2 && preview.isVisible()) {
                    delay += RENDER_DELAY;
                    preview.render(delay);
                }
            }
            // Non-zoomed images
            for (RenderPreview preview : mPreviews) {
                if (preview.getScale() <= 1.2 && preview.isVisible()) {
                    delay += RENDER_DELAY;
                    preview.render(delay);
                }
            }
        }
    }

    /**
     * Switch to the given configuration preview
     *
     * @param preview the preview to switch to
     */
    public void switchTo(@NonNull RenderPreview preview) {
        GraphicalEditorPart editor = mCanvas.getEditorDelegate().getGraphicalEditor();
        ConfigurationChooser chooser = editor.getConfigurationChooser();

        RenderPreview newPreview = mCanvas.getPreview();
        if (newPreview == null) {
            newPreview = RenderPreview.create(this, chooser.getConfiguration());
        }

        // Replace clicked preview with preview of the formerly edited main configuration
        if (newPreview != null) {
            // This doesn't work yet because the image overlay has had its image
            // replaced by the configuration previews! I should make a list of them
            //newPreview.setFullImage(mImageOverlay.getAwtImage());

            for (int i = 0, n = mPreviews.size(); i < n; i++) {
                if (preview == mPreviews.get(i)) {
                    mPreviews.set(i, newPreview);
                    break;
                }
            }
            //newPreview.setPosition(preview.getX(), preview.getY());
        }

        // Switch main editor to the clicked configuration preview
        mCanvas.setPreview(preview);
        chooser.setConfiguration(preview.getConfiguration());
        editor.recomputeLayout();
        mCanvas.getVerticalBar().setSelection(mCanvas.getVerticalBar().getMinimum());
        mCanvas.setFitScale(true /*onlyZoomOut*/, false /*allowZoomIn*/);
        layout(true);
        mCanvas.redraw();
    }

    /**
     * Gets the preview at the given location, or null if none. This is
     * currently deeply tied to where things are painted in onPaint().
     */
    RenderPreview getPreview(ControlPoint mousePos) {
        if (hasPreviews()) {
            int rootX = getX();
            if (mousePos.x < rootX) {
                return null;
            }
            int rootY = getY();

            for (RenderPreview preview : mPreviews) {
                int x = rootX + preview.getX();
                int y = rootY + preview.getY();
                if (mousePos.x >= x && mousePos.x <= x + preview.getWidth()) {
                    if (mousePos.y >= y && mousePos.y <= y + preview.getHeight()) {
                        return preview;
                    }
                }
            }
        }

        return null;
    }

    private int getX() {
        return mHScale.translate(0);
    }

    private int getY() {
        return mVScale.translate(0);
    }

    /**
     * Returns the height of the layout
     *
     * @return the height
     */
    public int getHeight() {
        return mLayoutHeight;
    }

    /**
     * Notifies that preview manager that the mouse cursor has moved to the
     * given control position within the layout canvas
     *
     * @param mousePos the mouse position, relative to the layout canvas
     */
    public void moved(ControlPoint mousePos) {
        RenderPreview hovered = getPreview(mousePos);
        if (hovered != mActivePreview) {
            if (mActivePreview != null) {
                mActivePreview.setActive(false);
            }
            mActivePreview = hovered;
            if (mActivePreview != null) {
                mActivePreview.setActive(true);
            }
            mCanvas.redraw();
        }
    }

    /**
     * Notifies that preview manager that the mouse cursor has entered the layout canvas
     *
     * @param mousePos the mouse position, relative to the layout canvas
     */
    public void enter(ControlPoint mousePos) {
        moved(mousePos);
    }

    /**
     * Notifies that preview manager that the mouse cursor has exited the layout canvas
     *
     * @param mousePos the mouse position, relative to the layout canvas
     */
    public void exit(ControlPoint mousePos) {
        if (mActivePreview != null) {
            mActivePreview.setActive(false);
        }
        mActivePreview = null;
        mCanvas.redraw();
    }

    /**
     * Process a mouse click, and return true if it was handled by this manager
     * (e.g. the click was on a preview)
     *
     * @param mousePos the mouse position where the click occurred
     * @return true if the click occurred over a preview and was handled, false otherwise
     */
    public boolean click(ControlPoint mousePos) {
        RenderPreview preview = getPreview(mousePos);
        if (preview != null) {
            boolean handled = preview.click(mousePos.x - getX() - preview.getX(),
                    mousePos.y - getY() - preview.getY());
            if (handled) {
                // In case layout was performed, there could be a new preview
                // under this coordinate now, so make sure it's hover etc
                // shows up
                moved(mousePos);
                return true;
            }
        }

        return false;
    }

    /**
     * Returns true if there are thumbnail previews
     *
     * @return true if thumbnails are being shown
     */
    public boolean hasPreviews() {
        return mPreviews != null && !mPreviews.isEmpty();
    }


    private void sortPreviewsByScreenSize() {
        if (mPreviews != null) {
            Collections.sort(mPreviews, new Comparator<RenderPreview>() {
                @Override
                public int compare(RenderPreview preview1, RenderPreview preview2) {
                    Configuration config1 = preview1.getConfiguration();
                    Configuration config2 = preview2.getConfiguration();
                    Device device1 = config1.getDevice();
                    Device device2 = config1.getDevice();
                    if (device1 != null && device2 != null) {
                        Screen screen1 = device1.getDefaultHardware().getScreen();
                        Screen screen2 = device2.getDefaultHardware().getScreen();
                        if (screen1 != null && screen2 != null) {
                            double delta = screen1.getDiagonalLength()
                                    - screen2.getDiagonalLength();
                            if (delta != 0.0) {
                                return (int) Math.signum(delta);
                            } else {
                                if (screen1.getPixelDensity() != screen2.getPixelDensity()) {
                                    return screen1.getPixelDensity().compareTo(
                                            screen2.getPixelDensity());
                                }
                            }
                        }

                    }
                    State state1 = config1.getDeviceState();
                    State state2 = config2.getDeviceState();
                    if (state1 != state2 && state1 != null && state2 != null) {
                        return state1.getName().compareTo(state2.getName());
                    }

                    return preview1.getDisplayName().compareTo(preview2.getDisplayName());
                }
            });
        }
    }

    private void sortPreviewsByOrientation() {
        if (mPreviews != null) {
            Collections.sort(mPreviews, new Comparator<RenderPreview>() {
                @Override
                public int compare(RenderPreview preview1, RenderPreview preview2) {
                    Configuration config1 = preview1.getConfiguration();
                    Configuration config2 = preview2.getConfiguration();
                    State state1 = config1.getDeviceState();
                    State state2 = config2.getDeviceState();
                    if (state1 != state2 && state1 != null && state2 != null) {
                        return state1.getName().compareTo(state2.getName());
                    }

                    return preview1.getDisplayName().compareTo(preview2.getDisplayName());
                }
            });
        }
    }

    /**
     * Vertical scrollbar listener which updates render previews which are not
     * visible and triggers a redraw
     */
    private class ScrollBarListener implements SelectionListener {
        @Override
        public void widgetSelected(SelectionEvent e) {
            if (mPreviews == null) {
                return;
            }

            ScrollBar bar = mCanvas.getVerticalBar();
            int selection = bar.getSelection();
            int thumb = bar.getThumb();
            int maxY = selection + thumb;
            if (maxY > mMaxVisibleY) {
            }
            for (RenderPreview preview : mPreviews) {
                if (!preview.isVisible() && preview.getY() <= maxY) {
                    preview.render(RENDER_DELAY);
                    preview.setVisible(true);
                }
            }
        }

        @Override
        public void widgetDefaultSelected(SelectionEvent e) {
        }
    }
}
