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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.resources.ResourceFolder;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.AdtUtils;
import com.android.ide.eclipse.adt.internal.editors.IconFactory;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutEditorDelegate;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.IncludeFinder;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.IncludeFinder.Reference;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.LayoutCanvas;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.RenderPreviewManager;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.RenderPreviewMode;
import com.android.ide.eclipse.adt.internal.preferences.AdtPrefs;
import com.android.ide.eclipse.adt.internal.resources.manager.ResourceManager;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PartInitException;

import java.util.List;

/**
 * The {@linkplain ConfigurationMenuListener} class is responsible for
 * generating the configuration menu in the {@link ConfigurationChooser}.
 */
class ConfigurationMenuListener extends SelectionAdapter {
    private static final String ICON_NEW_CONFIG = "newConfig";    //$NON-NLS-1$
    private static final int ACTION_SELECT_CONFIG = 1;
    private static final int ACTION_CREATE_CONFIG_FILE = 2;
    private static final int ACTION_ADD = 3;
    private static final int ACTION_GENERATE_DEFAULT = 4;
    private static final int ACTION_DELETE_ALL = 5;
    private static final int ACTION_PREVIEW_LOCALES = 6;
    private static final int ACTION_PREVIEW_SCREENS = 7;
    private static final int ACTION_PREVIEW_INCLUDED_IN = 8;
    private static final int ACTION_PREVIEW_VARIATIONS = 9;
    private static final int ACTION_PREVIEW_CUSTOM = 10;
    private static final int ACTION_PREVIEW_NONE = 11;

    private final ConfigurationChooser mConfigChooser;
    private final int mAction;
    private final IFile mResource;

    ConfigurationMenuListener(
            @NonNull ConfigurationChooser configChooser,
            int action,
            @Nullable IFile resource) {
        mConfigChooser = configChooser;
        mAction = action;
        mResource = resource;
    }

    @Override
    public void widgetSelected(SelectionEvent e) {
        switch (mAction) {
            case ACTION_SELECT_CONFIG: {
                try {
                    AdtPlugin.openFile(mResource, null, false);
                } catch (PartInitException ex) {
                    AdtPlugin.log(ex, null);
                }
                return;
            }
            case ACTION_CREATE_CONFIG_FILE: {
                ConfigurationClient client = mConfigChooser.getClient();
                if (client != null) {
                    client.createConfigFile();
                }
                return;
            }
        }

        IEditorPart activeEditor = AdtUtils.getActiveEditor();
        LayoutEditorDelegate delegate = LayoutEditorDelegate.fromEditor(activeEditor);
        IFile editedFile = mConfigChooser.getEditedFile();

        if (delegate == null || editedFile == null) {
            return;
        }
        // (Only do this when the two files are in the same project)
        IProject project = delegate.getEditor().getProject();
        if (project == null ||
                !project.equals(editedFile.getProject())) {
            return;
        }
        LayoutCanvas canvas = delegate.getGraphicalEditor().getCanvasControl();
        RenderPreviewManager previewManager = canvas.getPreviewManager();

        switch (mAction) {
            case ACTION_ADD: {
                previewManager.addAsThumbnail();
                break;
            }
            case ACTION_GENERATE_DEFAULT: {
                previewManager.selectMode(RenderPreviewMode.DEFAULT);
                break;
            }
            case ACTION_DELETE_ALL: {
                previewManager.deleteManualPreviews();
                break;
            }
            case ACTION_PREVIEW_LOCALES: {
                previewManager.selectMode(RenderPreviewMode.LOCALES);
                break;
            }
            case ACTION_PREVIEW_SCREENS: {
                previewManager.selectMode(RenderPreviewMode.SCREENS);
                break;
            }
            case ACTION_PREVIEW_INCLUDED_IN: {
                previewManager.selectMode(RenderPreviewMode.INCLUDES);
                break;
            }
            case ACTION_PREVIEW_VARIATIONS: {
                previewManager.selectMode(RenderPreviewMode.VARIATIONS);
                break;
            }
            case ACTION_PREVIEW_CUSTOM: {
                previewManager.selectMode(RenderPreviewMode.CUSTOM);
                break;
            }
            case ACTION_PREVIEW_NONE: {
                previewManager.selectMode(RenderPreviewMode.NONE);
                break;
            }
            default: assert false : mAction;
        }
        canvas.setFitScale(true /*onlyZoomOut*/, false /*allowZoomIn*/);
        canvas.redraw();
    }

    static void show(ConfigurationChooser chooser, ToolItem combo) {
        Menu menu = new Menu(chooser.getShell(), SWT.POP_UP);
        RenderPreviewMode mode = AdtPrefs.getPrefs().getRenderPreviewMode();

        // Configuration Previews
        create(menu, "Add As Thumbnail...",
                new ConfigurationMenuListener(chooser, ACTION_ADD, null), SWT.PUSH, false);
        if (mode == RenderPreviewMode.CUSTOM) {
            create(menu, "Delete All Thumbnails",
                new ConfigurationMenuListener(chooser, ACTION_DELETE_ALL, null), SWT.PUSH, false);
        }

        @SuppressWarnings("unused")
        MenuItem configSeparator = new MenuItem(menu, SWT.SEPARATOR);

        //create(menu, "Generate Default Thumbnails",
        create(menu, "Preview Sample",
                new ConfigurationMenuListener(chooser, ACTION_GENERATE_DEFAULT, null), SWT.RADIO,
                mode == RenderPreviewMode.DEFAULT);
        create(menu, "Preview All Screen Sizes",
                new ConfigurationMenuListener(chooser, ACTION_PREVIEW_SCREENS, null), SWT.RADIO,
                mode == RenderPreviewMode.SCREENS);

        MenuItem localeItem = create(menu, "Preview All Locales",
                new ConfigurationMenuListener(chooser, ACTION_PREVIEW_LOCALES, null), SWT.RADIO,
                mode == RenderPreviewMode.LOCALES);
        if (chooser.getLocaleList().size() <= 1) {
            localeItem.setEnabled(false);
        }

        boolean canPreviewIncluded = false;
        IProject project = chooser.getProject();
        if (project != null) {
            IncludeFinder finder = IncludeFinder.get(project);
            final List<Reference> includedBy = finder.getIncludedBy(chooser.getEditedFile());
            canPreviewIncluded = includedBy != null && !includedBy.isEmpty();
        }
        //if (!graphicalEditor.renderingSupports(Capability.EMBEDDED_LAYOUT)) {
        //    canPreviewIncluded = false;
        //}
        MenuItem includedItem = create(menu, "Preview Included",
                new ConfigurationMenuListener(chooser, ACTION_PREVIEW_INCLUDED_IN, null),
                SWT.RADIO, mode == RenderPreviewMode.INCLUDES);
        if (!canPreviewIncluded) {
            includedItem.setEnabled(false);
        }

        IFile file = chooser.getEditedFile();
        List<IFile> variations = AdtUtils.getResourceVariations(file, true);
        MenuItem variationsItem = create(menu, "Preview Layout Versions",
                new ConfigurationMenuListener(chooser, ACTION_PREVIEW_VARIATIONS, null),
                SWT.RADIO, mode == RenderPreviewMode.VARIATIONS);
        if (variations.size() <= 1) {
            variationsItem.setEnabled(false);
        }

        create(menu, "Manual Previews",
                new ConfigurationMenuListener(chooser, ACTION_PREVIEW_CUSTOM, null),
                SWT.RADIO, mode == RenderPreviewMode.CUSTOM);
        create(menu, "None",
                new ConfigurationMenuListener(chooser, ACTION_PREVIEW_NONE, null),
                SWT.RADIO, mode == RenderPreviewMode.NONE);

        if (variations.size() > 1) {
            @SuppressWarnings("unused")
            MenuItem separator = new MenuItem(menu, SWT.SEPARATOR);

            ResourceManager manager = ResourceManager.getInstance();
            for (final IFile resource : variations) {
                IFolder parent = (IFolder) resource.getParent();
                ResourceFolder parentResource = manager.getResourceFolder(parent);
                FolderConfiguration configuration = parentResource.getConfiguration();
                String title = configuration.toDisplayString();

                MenuItem item = create(menu, title,
                        new ConfigurationMenuListener(chooser, ACTION_SELECT_CONFIG, resource),
                        SWT.CHECK, false);

                if (file != null) {
                    boolean selected = file.equals(resource);
                    if (selected) {
                        item.setSelection(true);
                        item.setEnabled(false);
                    }
                }
            }
        }

        Configuration configuration = chooser.getConfiguration();
        if (configuration.getEditedConfig() != null &&
                !configuration.getEditedConfig().equals(configuration.getFullConfig())) {
            if (variations.size() > 0) {
                @SuppressWarnings("unused")
                MenuItem separator = new MenuItem(menu, SWT.SEPARATOR);
            }

            // Add action for creating a new configuration
            MenuItem item = create(menu, "Create New...",
                    new ConfigurationMenuListener(chooser, ACTION_CREATE_CONFIG_FILE, null),
                    SWT.PUSH, false);
            item.setImage(IconFactory.getInstance().getIcon(ICON_NEW_CONFIG));
        }

        Rectangle bounds = combo.getBounds();
        Point location = new Point(bounds.x, bounds.y + bounds.height);
        location = combo.getParent().toDisplay(location);
        menu.setLocation(location.x, location.y);
        menu.setVisible(true);
    }

    @NonNull
    public static MenuItem create(@NonNull Menu menu, String title,
            ConfigurationMenuListener listener, int style, boolean selected) {
        MenuItem item = new MenuItem(menu, style);
        item.setText(title);
        item.addSelectionListener(listener);
        if (selected) {
            item.setSelection(true);
        }
        return item;
    }
}
