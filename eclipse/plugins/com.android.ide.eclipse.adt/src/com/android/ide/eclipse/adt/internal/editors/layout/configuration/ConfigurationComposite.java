/*
 * Copyright (C) 2008 The Android Open Source Project
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

import static com.android.SdkConstants.ANDROID_NS_NAME_PREFIX;
import static com.android.SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX;
import static com.android.SdkConstants.FD_RES_LAYOUT;
import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.SdkConstants.RES_QUALIFIER_SEP;
import static com.android.SdkConstants.STYLE_RESOURCE_PREFIX;
import static com.android.SdkConstants.TOOLS_URI;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.api.Rect;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.resources.ResourceFile;
import com.android.ide.common.resources.ResourceFolder;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.ide.common.resources.configuration.DeviceConfigHelper;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.LanguageQualifier;
import com.android.ide.common.resources.configuration.NightModeQualifier;
import com.android.ide.common.resources.configuration.RegionQualifier;
import com.android.ide.common.resources.configuration.ResourceQualifier;
import com.android.ide.common.resources.configuration.ScreenDimensionQualifier;
import com.android.ide.common.resources.configuration.ScreenOrientationQualifier;
import com.android.ide.common.resources.configuration.ScreenSizeQualifier;
import com.android.ide.common.resources.configuration.UiModeQualifier;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.ide.common.sdk.LoadStatus;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.AdtUtils;
import com.android.ide.eclipse.adt.internal.editors.IconFactory;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutEditorDelegate;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.DomUtilities;
import com.android.ide.eclipse.adt.internal.editors.manifest.ManifestInfo;
import com.android.ide.eclipse.adt.internal.resources.ResourceHelper;
import com.android.ide.eclipse.adt.internal.resources.manager.ProjectResources;
import com.android.ide.eclipse.adt.internal.resources.manager.ResourceManager;
import com.android.ide.eclipse.adt.internal.sdk.AndroidTargetData;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.ide.eclipse.adt.internal.wizards.newxmlfile.AddTranslationDialog;
import com.android.resources.Density;
import com.android.resources.NightMode;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ScreenOrientation;
import com.android.resources.ScreenSize;
import com.android.resources.UiMode;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.DeviceManager;
import com.android.sdklib.devices.DeviceManager.DevicesChangeListener;
import com.android.sdklib.devices.State;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.repository.PkgProps;
import com.android.sdklib.util.SparseIntArray;
import com.android.utils.Pair;
import com.google.common.collect.Maps;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.jdt.ui.ISharedImages;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PartInitException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;

/**
 * A composite that displays the current configuration displayed in a Graphical Layout Editor.
 * <p/>
 * The composite has several entry points:<br>
 * - {@link #setFile(IFile)}<br>
 *   Called after the constructor to set the file being edited. Nothing else is performed.<br>
 *<br>
 * - {@link #onXmlModelLoaded()}<br>
 *   Called when the XML model is loaded, either the first time or when the Target/SDK changes.
 *   This initializes the UI, either with the first compatible configuration found, or attempts
 *   to restore a configuration if one is found to have been saved in the file persistent storage.
 *   (see {@link #storeState()})<br>
 *<br>
 * - {@link #replaceFile(IFile)}<br>
 *   Called when a file, representing the same resource but with a different config is opened<br>
 *   by the user.<br>
 *<br>
 * - {@link #changeFileOnNewConfig(IFile)}<br>
 *   Called when config change triggers the editing of a file with a different config.
 *<p/>
 * Additionally, the composite can handle the following events.<br>
 * - SDK reload. This is when the main SDK is finished loading.<br>
 * - Target reload. This is when the target used by the project is the edited file has finished<br>
 *   loading.<br>
 */
public class ConfigurationComposite extends Composite
        implements SelectionListener, DevicesChangeListener, DisposeListener {
    public static final String ATTR_CONTEXT = "context";          //$NON-NLS-1$
    private static final String ICON_SQUARE = "square";           //$NON-NLS-1$
    private static final String ICON_LANDSCAPE = "landscape";     //$NON-NLS-1$
    private static final String ICON_PORTRAIT = "portrait";       //$NON-NLS-1$
    private static final String ICON_LANDSCAPE_FLIP = "flip_landscape";//$NON-NLS-1$
    private static final String ICON_PORTRAIT_FLIP = "flip_portrait";//$NON-NLS-1$
    private static final String ICON_DISPLAY = "display";         //$NON-NLS-1$
    private static final String ICON_NEW_CONFIG = "newConfig";    //$NON-NLS-1$
    private static final String ICON_THEMES = "themes";           //$NON-NLS-1$
    private static final String ICON_ACTIVITY = "activity";       //$NON-NLS-1$
    private final static String SEP = ":";                        //$NON-NLS-1$
    private final static String SEP_LOCALE = "-";                 //$NON-NLS-1$
    private final static String MARKER_FRAMEWORK = "-";           //$NON-NLS-1$
    private final static String MARKER_PROJECT = "+";           //$NON-NLS-1$

    /**
     * Setting name for project-wide setting controlling rendering target and locale which
     * is shared for all files
     */
    public final static QualifiedName NAME_RENDER_STATE =
        new QualifiedName(AdtPlugin.PLUGIN_ID, "render");//$NON-NLS-1$

    /**
     * Settings name for file-specific configuration preferences, such as which theme or
     * device to render the current layout with
     */
    public final static QualifiedName NAME_CONFIG_STATE =
        new QualifiedName(AdtPlugin.PLUGIN_ID, "state");//$NON-NLS-1$

    private final static int LOCALE_LANG = 0;
    private final static int LOCALE_REGION = 1;

    private ToolItem mDeviceCombo;
    private ToolItem mThemeCombo;
    private ToolItem mOrientationCombo;
    private ToolItem mLocaleCombo;
    private ToolItem mTargetCombo;
    private ToolItem mConfigCombo;
    private ToolItem mActivityCombo;

    /** updates are disabled if > 0 */
    private int mDisableUpdates = 0;

    private List<Device> mDeviceList = new ArrayList<Device>();
    private final List<IAndroidTarget> mTargetList = new ArrayList<IAndroidTarget>();

    private final List<String> mThemeList = new ArrayList<String>();

    private final List<ResourceQualifier[] > mLocaleList =
        new ArrayList<ResourceQualifier[]>();

    private final ConfigState mState = new ConfigState();

    private boolean mSdkChanged = false;
    private boolean mFirstXmlModelChange = true;

    /** The config listener given to the constructor. Never null. */
    private final IConfigListener mListener;

    /** The device menu listener, so we can remove it when the device lists are updated */
    private Listener mDeviceListener;

    /** The {@link FolderConfiguration} representing the state of the UI controls */
    private final FolderConfiguration mCurrentConfig = new FolderConfiguration();

    /** The file being edited */
    private IFile mEditedFile;
    /** The {@link ProjectResources} for the edited file's project */
    private ProjectResources mResources;
    /** The target of the project of the file being edited. */
    private IAndroidTarget mProjectTarget;
    /** The target of the project of the file being edited. */
    private IAndroidTarget mRenderingTarget;
    /** The {@link FolderConfiguration} being edited. */
    private FolderConfiguration mEditedConfig;
    /** Serialized state to use when initializing the configuration after the SDK is loaded */
    private String mInitialState;

    /**
     * Interface implemented by the part which owns a {@link ConfigurationComposite}.
     * This notifies the owners when the configuration change.
     * The owner must also provide methods to provide the configuration that will
     * be displayed.
     */
    public interface IConfigListener {
        /**
         * Called when the {@link FolderConfiguration} change. The new config can be queried
         * with {@link ConfigurationComposite#getCurrentConfig()}.
         */
        void onConfigurationChange();

        /**
         * Called after a device has changed (in addition to {@link #onConfigurationChange}
         * getting called)
         */
        void onDevicePostChange();

        /**
         * Called when the current theme changes. The theme can be queried with
         * {@link ConfigurationComposite#getThemeName()}.
         */
        void onThemeChange();

        /**
         * Called when the "Create" button is clicked.
         */
        void onCreate();

        /**
         * Called when an associated activity is picked
         *
         * @param fqcn the fully qualified class name for the associated activity context
         */
        void onSetActivity(String fqcn);

        /**
         * Called before the rendering target changes.
         * @param oldTarget the old rendering target
         */
        void onRenderingTargetPreChange(IAndroidTarget oldTarget);

        /**
         * Called after the rendering target changes.
         *
         * @param target the new rendering target
         */
        void onRenderingTargetPostChange(IAndroidTarget target);

        ResourceRepository getProjectResources();
        ResourceRepository getFrameworkResources();
        ResourceRepository getFrameworkResources(IAndroidTarget target);
        Map<ResourceType, Map<String, ResourceValue>> getConfiguredProjectResources();
        Map<ResourceType, Map<String, ResourceValue>> getConfiguredFrameworkResources();
        String getIncludedWithin();
    }

    /**
     * State of the current config. This is used during UI reset to attempt to return the
     * rendering to its original configuration.
     */
    private class ConfigState {
        Device device;
        String stateName;
        ResourceQualifier[] locale;
        String theme;
        // TODO: Need to know if it's the project theme or the framework theme!
        /** UI mode. Guaranteed to be non null */
        UiMode uiMode = UiMode.NORMAL;
        /** night mode. Guaranteed to be non null */
        NightMode night = NightMode.NOTNIGHT;
        /** the version being targeted for rendering */
        IAndroidTarget target;
        String activity;

        String getData() {
            StringBuilder sb = new StringBuilder();
            if (device != null) {
                sb.append(device.getName());
                sb.append(SEP);
                if (stateName == null) {
                    State state= getSelectedDeviceState();
                    if (state != null) {
                        stateName = state.getName();
                    }
                }
                if (stateName != null) {
                    sb.append(stateName);
                }
                sb.append(SEP);
                if (isLocaleSpecificLayout() && locale != null) {
                    if (locale[0] != null && locale[1] != null) {
                        // locale[0]/[1] can be null sometimes when starting Eclipse
                        sb.append(((LanguageQualifier) locale[0]).getValue());
                        sb.append(SEP_LOCALE);
                        sb.append(((RegionQualifier) locale[1]).getValue());
                    }
                }
                sb.append(SEP);
                // Need to escape the theme: if we write the full theme style, then
                // we can end up with ":"'s in the string (as in @android:style/Theme) which
                // can be mistaken for {@link #SEP}. Instead use {@link #MARKER_FRAMEWORK}.
                if (theme != null) {
                    String themeName = ResourceHelper.styleToTheme(theme);
                    if (theme.startsWith(STYLE_RESOURCE_PREFIX)) {
                        sb.append(MARKER_PROJECT);
                    } else if (theme.startsWith(ANDROID_STYLE_RESOURCE_PREFIX)) {
                        sb.append(MARKER_FRAMEWORK);
                    }
                    sb.append(themeName);
                }
                sb.append(SEP);
                if (uiMode != null) {
                    sb.append(uiMode.getResourceValue());
                }
                sb.append(SEP);
                if (night != null) {
                    sb.append(night.getResourceValue());
                }
                sb.append(SEP);

                // We used to store the render target here in R9. Leave a marker
                // to ensure that we don't reuse this slot; add new extra fields after it.
                sb.append(SEP);
                if (activity != null) {
                    sb.append(activity);
                }
            }

            return sb.toString();
        }

        boolean setData(String data) {
            String[] values = data.split(SEP);
            if (values.length >= 6 && values.length <= 8) {
                for (Device d : mDeviceList) {
                    if (d.getName().equals(values[0])) {
                        device = d;
                        FolderConfiguration config = null;
                        if (!values[1].isEmpty() && !values[1].equals("null")) { //$NON-NLS-1$
                            stateName = values[1];
                            config = DeviceConfigHelper.getFolderConfig(device, stateName);
                        } else if (device.getAllStates().size() > 0) {
                            State first = device.getAllStates().get(0);
                            stateName = first.getName();
                            config = DeviceConfigHelper.getFolderConfig(first);
                        }
                        if (config != null) {
                            // Load locale. Note that this can get overwritten by the
                            // project-wide settings read below.
                            locale = new ResourceQualifier[2];
                            String locales[] = values[2].split(SEP_LOCALE);
                            if (locales.length >= 2) {
                                if (locales[0].length() > 0) {
                                    locale[0] = new LanguageQualifier(locales[0]);
                                }
                                if (locales[1].length() > 0) {
                                    locale[1] = new RegionQualifier(locales[1]);
                                }
                            }

                            // Decode the theme name: See {@link #getData}
                            theme = values[3];
                            if (theme.startsWith(MARKER_FRAMEWORK)) {
                                theme = ANDROID_STYLE_RESOURCE_PREFIX
                                        + theme.substring(MARKER_FRAMEWORK.length());
                            } else if (theme.startsWith(MARKER_PROJECT)) {
                                theme = STYLE_RESOURCE_PREFIX
                                        + theme.substring(MARKER_PROJECT.length());
                            }

                            uiMode = UiMode.getEnum(values[4]);
                            if (uiMode == null) {
                                uiMode = UiMode.NORMAL;
                            }
                            night = NightMode.getEnum(values[5]);
                            if (night == null) {
                                night = NightMode.NOTNIGHT;
                            }

                            // element 7/values[6]: used to store render target in R9.
                            // No longer stored here. If adding more data, make
                            // sure you leave 7 alone.

                            Pair<ResourceQualifier[], IAndroidTarget> pair = loadRenderState();

                            // We only use the "global" setting
                            if (!isLocaleSpecificLayout()) {
                                locale = pair.getFirst();
                            }
                            target = pair.getSecond();

                            if (values.length == 8) {
                                activity = values[7];
                            }

                            return true;
                        }
                    }
                }
            }

            return false;
        }

        @Override
        public String toString() {
            return getData();
        }
    }

    /**
     * Returns a String id to represent an {@link IAndroidTarget} which can be translated
     * back to an {@link IAndroidTarget} by the matching {@link #stringToTarget}. The id
     * will never contain the {@link #SEP} character.
     *
     * @param target the target to return an id for
     * @return an id for the given target; never null
     */
    private String targetToString(IAndroidTarget target) {
        return target.getFullName().replace(SEP, "");  //$NON-NLS-1$
    }

    /**
     * Returns an {@link IAndroidTarget} that corresponds to the given id that was
     * originally returned by {@link #targetToString}. May be null, if the platform is no
     * longer available, or if the platform list has not yet been initialized.
     *
     * @param id the id that corresponds to the desired platform
     * @return an {@link IAndroidTarget} that matches the given id, or null
     */
    private IAndroidTarget stringToTarget(String id) {
        if (mTargetList != null && mTargetList.size() > 0) {
            for (IAndroidTarget target : mTargetList) {
                if (id.equals(targetToString(target))) {
                    return target;
                }
            }
        }

        return null;
    }

    /**
     * Creates a new {@link ConfigurationComposite} and adds it to the parent.
     *
     * The method also receives custom buttons to set into the configuration composite. The list
     * is organized as an array of arrays. Each array represents a group of buttons thematically
     * grouped together.
     *
     * @param listener An {@link IConfigListener} that gets and sets configuration properties.
     *          Mandatory, cannot be null.
     * @param parent The parent composite.
     * @param style The style of this composite.
     * @param initialState The initial state (serialized form) to use for the configuration
     */
    public ConfigurationComposite(IConfigListener listener,
            Composite parent, int style, String initialState) {
        super(parent, style);
        setVisible(false); // Delayed until the targets are loaded

        mListener = listener;
        mInitialState = initialState;
        setLayout(new GridLayout(1, false));

        IconFactory icons = IconFactory.getInstance();

        // TODO: Consider switching to a CoolBar instead
        ToolBar toolBar = new ToolBar(this, SWT.WRAP | SWT.FLAT | SWT.RIGHT | SWT.HORIZONTAL);
        toolBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

        mConfigCombo = new ToolItem(toolBar, SWT.DROP_DOWN | SWT.BOLD);
        mConfigCombo.setImage(null);
        mConfigCombo.setToolTipText("Configuration to render this layout with in Eclipse");

        @SuppressWarnings("unused")
        ToolItem separator2 = new ToolItem(toolBar, SWT.SEPARATOR);

        mDeviceCombo = new ToolItem(toolBar, SWT.DROP_DOWN);
        mDeviceCombo.setImage(icons.getIcon(ICON_DISPLAY));

        @SuppressWarnings("unused")
        ToolItem separator3 = new ToolItem(toolBar, SWT.SEPARATOR);

        mOrientationCombo = new ToolItem(toolBar, SWT.DROP_DOWN);
        mOrientationCombo.setImage(icons.getIcon(ICON_PORTRAIT));
        mOrientationCombo.setToolTipText("Go to next state");

        @SuppressWarnings("unused")
        ToolItem separator4 = new ToolItem(toolBar, SWT.SEPARATOR);

        mThemeCombo = new ToolItem(toolBar, SWT.DROP_DOWN);
        mThemeCombo.setImage(icons.getIcon(ICON_THEMES));

        @SuppressWarnings("unused")
        ToolItem separator5 = new ToolItem(toolBar, SWT.SEPARATOR);

        mActivityCombo = new ToolItem(toolBar, SWT.DROP_DOWN);
        mActivityCombo.setToolTipText("Associated activity or fragment providing context");
        // The JDT class icon is lopsided, presumably because they've left room in the
        // bottom right corner for badges (for static, final etc). Unfortunately, this
        // means that the icon looks out of place when sitting close to the language globe
        // icon, the theme icon, etc so that it looks vertically misaligned:
        //mActivityCombo.setImage(JavaUI.getSharedImages().getImage(ISharedImages.IMG_OBJS_CLASS));
        // ...so use one that is centered instead:
        mActivityCombo.setImage(icons.getIcon(ICON_ACTIVITY));

        @SuppressWarnings("unused")
        ToolItem separator6 = new ToolItem(toolBar, SWT.SEPARATOR);

        //ToolBar rightToolBar = new ToolBar(this, SWT.WRAP | SWT.FLAT | SWT.RIGHT | SWT.HORIZONTAL);
        //rightToolBar.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 1));
        ToolBar rightToolBar = toolBar;

        mLocaleCombo = new ToolItem(rightToolBar, SWT.DROP_DOWN);
        mLocaleCombo.setImage(LocaleManager.getGlobeIcon());
        mLocaleCombo.setToolTipText("Locale to use when rendering layouts in Eclipse");

        @SuppressWarnings("unused")
        ToolItem separator7 = new ToolItem(rightToolBar, SWT.SEPARATOR);

        mTargetCombo = new ToolItem(rightToolBar, SWT.DROP_DOWN);
        mTargetCombo.setImage(AdtPlugin.getAndroidLogo());
        mTargetCombo.setToolTipText("Android version to use when rendering layouts in Eclipse");

        addConfigurationMenuListener(mConfigCombo);
        addActivityMenuListener(mActivityCombo);
        addLocaleMenuListener(mLocaleCombo);
        addDeviceMenuListener(mDeviceCombo);
        addTargetMenuListener(mTargetCombo);
        addThemeListener(mThemeCombo);
        addOrientationMenuListener(mOrientationCombo);

        addDisposeListener(this);
    }

    private void updateActivity() {
        if (mEditedFile != null) {
            String preferred = getPreferredActivity(mEditedFile);
            selectActivity(preferred);
        }
    }

    // ---- Dispose

    @Override
    public void widgetDisposed(DisposeEvent e) {
        dispose();
    }

    @Override
    public void dispose() {
        if (!isDisposed()) {
            super.dispose();

            final Sdk sdk = Sdk.getCurrent();
            if (sdk != null) {
                DeviceManager manager = sdk.getDeviceManager();
                manager.unregisterListener(this);
            }
        }
    }

    // ---- Init and reset/reload methods ----

    /**
     * Sets the reference to the file being edited.
     * <p/>The UI is initialized in {@link #onXmlModelLoaded()} which is called as the XML model is
     * loaded (or reloaded as the SDK/target changes).
     *
     * @param file the file being opened
     *
     * @see #onXmlModelLoaded()
     * @see #replaceFile(IFile)
     * @see #changeFileOnNewConfig(IFile)
     */
    public void setFile(IFile file) {
        mEditedFile = file;
    }

    /**
     * Replaces the UI with a given file configuration. This is meant to answer the user
     * explicitly opening a different version of the same layout from the Package Explorer.
     * <p/>This attempts to keep the current config, but may change it if it's not compatible or
     * not the best match
     * <p/>This will NOT trigger a redraw event (will not call
     * {@link IConfigListener#onConfigurationChange()}.)
     * @param file the file being opened.
     */
    public void replaceFile(IFile file) {
        // if there is no previous selection, revert to default mode.
        if (mState.device == null) {
            setFile(file); // onTargetChanged will be called later.
            return;
        }

        mEditedFile = file;
        IProject iProject = mEditedFile.getProject();
        mResources = ResourceManager.getInstance().getProjectResources(iProject);

        ResourceFolder resFolder = ResourceManager.getInstance().getResourceFolder(file);
        mEditedConfig = resFolder.getConfiguration();

        mDisableUpdates++; // we do not want to trigger onXXXChange when setting
                           // new values in the widgets.

        try {
            // only attempt to do anything if the SDK and targets are loaded.
            LoadStatus sdkStatus = AdtPlugin.getDefault().getSdkLoadStatus();
            if (sdkStatus == LoadStatus.LOADED) {
                setVisible(true);

                LoadStatus targetStatus = Sdk.getCurrent().checkAndLoadTargetData(mProjectTarget,
                        null /*project*/);

                if (targetStatus == LoadStatus.LOADED) {

                    // update the current config selection to make sure it's
                    // compatible with the new file
                    adaptConfigSelection(true /*needBestMatch*/);

                    // compute the final current config
                    computeCurrentConfig();

                    // update the string showing the config value
                    updateConfigDisplay(mEditedConfig);

                    updateActivity();
                }
            }
        } finally {
            mDisableUpdates--;
        }
    }

    /**
     * Updates the UI with a new file that was opened in response to a config change.
     * @param file the file being opened.
     *
     * @see #replaceFile(IFile)
     */
    public void changeFileOnNewConfig(IFile file) {
        mEditedFile = file;
        IProject iProject = mEditedFile.getProject();
        mResources = ResourceManager.getInstance().getProjectResources(iProject);

        ResourceFolder resFolder = ResourceManager.getInstance().getResourceFolder(file);
        mEditedConfig = resFolder.getConfiguration();

        // All that's needed is to update the string showing the config value
        // (since the config combo were chosen by the user).
        updateConfigDisplay(mEditedConfig);
    }

    /**
     * Responds to the event that the basic SDK information finished loading.
     * @param target the possibly new target object associated with the file being edited (in case
     * the SDK path was changed).
     */
    public void onSdkLoaded(IAndroidTarget target) {
        // a change to the SDK means that we need to check for new/removed devices.
        mSdkChanged = true;

        // store the new target.
        mProjectTarget = target;

        mDisableUpdates++; // we do not want to trigger onXXXChange when setting
                           // new values in the widgets.
        try {
            // this is going to be followed by a call to onTargetLoaded.
            // So we can only care about the layout devices in this case.
            initDevices();
            initTargets();
        } finally {
            mDisableUpdates--;
        }
    }

    /**
     * Answers to the XML model being loaded, either the first time or when the Target/SDK changes.
     * <p>This initializes the UI, either with the first compatible configuration found,
     * or attempts to restore a configuration if one is found to have been saved in the file
     * persistent storage.
     * <p>If the SDK or target are not loaded, nothing will happened (but the method must be called
     * back when those are loaded).
     * <p>The method automatically handles being called the first time after editor creation, or
     * being called after during SDK/Target changes (as long as {@link #onSdkLoaded(IAndroidTarget)}
     * is properly called).
     *
     * @see #storeState()
     * @see #onSdkLoaded(IAndroidTarget)
     */
    public AndroidTargetData onXmlModelLoaded() {
        AndroidTargetData targetData = null;

        // only attempt to do anything if the SDK and targets are loaded.
        LoadStatus sdkStatus = AdtPlugin.getDefault().getSdkLoadStatus();
        if (sdkStatus == LoadStatus.LOADED) {
            mDisableUpdates++; // we do not want to trigger onXXXChange when setting

            try {
                // init the devices if needed (new SDK or first time going through here)
                if (mSdkChanged || mFirstXmlModelChange) {
                    initDevices();
                    initTargets();
                    mSdkChanged = false;
                }

                IProject iProject = mEditedFile.getProject();

                Sdk currentSdk = Sdk.getCurrent();
                if (currentSdk != null) {
                    mProjectTarget = currentSdk.getTarget(iProject);
                }

                LoadStatus targetStatus = LoadStatus.FAILED;
                if (mProjectTarget != null) {
                    targetStatus = Sdk.getCurrent().checkAndLoadTargetData(mProjectTarget, null);
                    initTargets();
                }

                if (targetStatus == LoadStatus.LOADED) {
                    setVisible(true);
                    if (mResources == null) {
                        mResources = ResourceManager.getInstance().getProjectResources(iProject);
                    }
                    if (mEditedConfig == null) {
                        IFolder parent = (IFolder) mEditedFile.getParent();
                        ResourceFolder resFolder = mResources.getResourceFolder(parent);
                        if (resFolder != null) {
                            mEditedConfig = resFolder.getConfiguration();
                        } else {
                            mEditedConfig = FolderConfiguration.getConfig(
                                    parent.getName().split(RES_QUALIFIER_SEP));
                        }
                    }

                    targetData = Sdk.getCurrent().getTargetData(mProjectTarget);

                    // get the file stored state
                    boolean loadedConfigData = false;
                    String data = AdtPlugin.getFileProperty(mEditedFile, NAME_CONFIG_STATE);
                    if (mInitialState != null) {
                        data = mInitialState;
                        mInitialState = null;
                    }
                    if (data != null) {
                        loadedConfigData = mState.setData(data);
                    }

                    updateLocales();

                    // If the current state was loaded from the persistent storage, we update the
                    // UI with it and then try to adapt it (which will handle incompatible
                    // configuration).
                    // Otherwise, just look for the first compatible configuration.
                    if (loadedConfigData) {
                        // first make sure we have the config to adapt
                        selectDevice(mState.device);
                        selectState(mState.stateName);

                        adaptConfigSelection(false /*needBestMatch*/);

                        selectTarget(mState.target);

                        targetData = Sdk.getCurrent().getTargetData(mState.target);
                    } else {
                        findAndSetCompatibleConfig(false /*favorCurrentConfig*/);

                        // Default to modern layout lib
                        IAndroidTarget target = findDefaultRenderTarget();
                        if (target != null) {
                            targetData = Sdk.getCurrent().getTargetData(target);
                            selectTarget(target);
                        }
                    }

                    // Update activity: This is done before updateThemes() since
                    // the themes selection can depend on the currently selected activity
                    // (e.g. when there are manifest registrations for the theme to use
                    // for a given activity)
                    updateActivity();

                    // Update themes. This is done after updating the devices above,
                    // since we want to look at the chosen device size to decide
                    // what the default theme (for example, with Honeycomb we choose
                    // Holo as the default theme but only if the screen size is XLARGE
                    // (and of course only if the manifest does not specify another
                    // default theme).
                    updateThemes();

                    // update the string showing the config value
                    updateConfigDisplay(mEditedConfig);

                    // compute the final current config
                    computeCurrentConfig();
                }
            } finally {
                mDisableUpdates--;
                mFirstXmlModelChange = false;
            }
        }

        return targetData;
    }

    private void selectActivity(@Nullable String fqcn) {
        if (fqcn != null) {
            mActivityCombo.setData(fqcn);
            String label = getActivityLabel(fqcn, true);
            mActivityCombo.setText(label);
        } else {
            mActivityCombo.setText("(Select)");
        }
        resizeToolBar();
    }

    @Nullable
    private String getPreferredActivity(@NonNull IFile file) {
        // Store/restore the activity context in the config state to help with
        // performance if for some reason we can't write it into the XML file and to
        // avoid having to open the model below
        if (mState.activity != null) {
            return mState.activity;
        }

        IProject project = file.getProject();

        // Look up from XML file
        Document document = DomUtilities.getDocument(file);
        if (document != null) {
            Element element = document.getDocumentElement();
            if (element != null) {
                String activity = element.getAttributeNS(TOOLS_URI, ATTR_CONTEXT);
                if (activity != null && !activity.isEmpty()) {
                    if (activity.startsWith(".") || activity.indexOf('.') == -1) { //$NON-NLS-1$
                        ManifestInfo manifest = ManifestInfo.get(project);
                        String pkg = manifest.getPackage();
                        if (!pkg.isEmpty()) {
                            if (activity.startsWith(".")) { //$NON-NLS-1$
                                activity = pkg + activity;
                            } else {
                                activity = activity + "." + pkg;
                            }
                        }
                    }

                    mState.activity = activity;
                    storeState();
                    return activity;
                }
            }
        }

        // No, not available there: try to infer it from the code index
        String includedIn = mListener != null ? mListener.getIncludedWithin() : null;

        ManifestInfo manifest = ManifestInfo.get(project);
        String pkg = manifest.getPackage();
        String layoutName = ResourceHelper.getLayoutName(mEditedFile);

        // If we are rendering a layout in included context, pick the theme
        // from the outer layout instead
        if (includedIn != null) {
            layoutName = includedIn;
        }

        String activity = ManifestInfo.guessActivity(project, layoutName, pkg);

        if (activity == null) {
            List<String> activities = ManifestInfo.getProjectActivities(project);
            if (activities.size() == 1) {
                activity = activities.get(0);
            }
        }

        if (activity != null) {
            mState.activity = activity;
            storeState();
            return activity;
        }

        // TODO: Do anything else, such as pick the first activity found?
        // Or just leave some default label instead?
        // Also, figure out what to store in the mState so I don't keep trying

        return null;
    }

    private void onSelectActivity() {
        String activity = getSelectedActivity();
        mState.activity = activity;
        saveState();
        storeState();

        if (activity == null) {
            return;
        }

        // See if there is a default theme assigned to this activity, and if so, use it
        ManifestInfo manifest = ManifestInfo.get(mEditedFile.getProject());
        Map<String, String> activityThemes = manifest.getActivityThemes();
        String preferred = activityThemes.get(activity);
        if (preferred != null) {
            // Yes, switch to it
            selectTheme(preferred);
            onThemeChange();
        }

        // Persist in XML
        if (mListener != null) {
            mListener.onSetActivity(activity);
        }
    }


    /** Update the toolbar whenever a label has changed, to not only
     * cause the layout in the current toolbar to update, but to possibly
     * wrap the toolbars and update the layout of the surrounding area.
     */
    private void resizeToolBar() {
        Point size = getSize();
        Point newSize = computeSize(size.x, SWT.DEFAULT, true);
        setSize(newSize);
        Composite parent = getParent();
        parent.layout();
        parent.redraw();
    }

    private String getActivityLabel(String fqcn, boolean brief) {
        if (brief) {
            String label = fqcn;
            int packageIndex = label.lastIndexOf('.');
            if (packageIndex != -1) {
                label = label.substring(packageIndex + 1);
            }
            int innerClass = label.lastIndexOf('$');
            if (innerClass != -1) {
                label = label.substring(innerClass + 1);
            }

            // Also strip out the "Activity" or "Fragment" common suffix
            // if this is a long name
            if (label.endsWith("Activity") && label.length() > 8 + 12) { // 12 chars + 8 in suffix
                label = label.substring(0, label.length() - 8);
            } else if (label.endsWith("Fragment") && label.length() > 8 + 12) {
                label = label.substring(0, label.length() - 8);
            }

            return label;
        }

        return fqcn;
    }

    String getSelectedActivity() {
        return (String) mActivityCombo.getData();
    }

    private void selectTarget(IAndroidTarget target) {
        mTargetCombo.setData(target);
        String label = getRenderingTargetLabel(target, true);
        mTargetCombo.setText(label);
        resizeToolBar();
    }

    private static String getRenderingTargetLabel(IAndroidTarget target, boolean brief) {
        if (target == null) {
            return "<null>";
        }

        AndroidVersion version = target.getVersion();

        if (brief) {
            if (target.isPlatform()) {
                return Integer.toString(version.getApiLevel());
            } else {
                return target.getName() + ':' + Integer.toString(version.getApiLevel());
            }
        }

        String label = String.format("API %1$d: %2$s",
                version.getApiLevel(),
                target.getShortClasspathName());

        return label;
    }

    private String getLocaleLabel(ResourceQualifier[] qualifiers, boolean brief) {
        if (qualifiers == null) {
            return null;
        }

        LanguageQualifier language = (LanguageQualifier) qualifiers[LOCALE_LANG];

        if (language.hasFakeValue()) {
            if (brief) {
                // Just use the icon
                return "";
            }

            boolean hasLocale = false;
            ResourceRepository projectRes = mListener.getProjectResources();
            if (projectRes != null) {
                hasLocale = projectRes.getLanguages().size() > 0;
            }

            if (hasLocale) {
                return "Other";
            } else {
                return "Any";
            }
        }

        String languageCode = language.getValue();
        String languageName = LocaleManager.getLanguageName(languageCode);

        RegionQualifier region = (RegionQualifier) qualifiers[LOCALE_REGION];
        if (region.hasFakeValue()) {
            // TODO: Make the region string use "Other" instead of "Any" if
            // there is more than one region for a given language
            //if (regions.size() > 0) {
            //    return String.format("%1$s / Other", language);
            //} else {
            //    return String.format("%1$s / Any", language);
            //}
            if (!brief && languageName != null) {
                return String.format("%1$s (%2$s)", languageName, languageCode);
            } else {
                return languageCode;
            }
        } else {
            String regionCode = region.getValue();
            if (!brief && languageName != null) {
                String regionName = LocaleManager.getRegionName(regionCode);
                if (regionName != null) {
                    return String.format("%1$s (%2$s) in %3$s (%4$s)", languageName, languageCode,
                            regionName, regionCode);
                }
                return String.format("%1$s (%2$s) in %3$s", languageName, languageCode,
                        regionCode);
            }
            return String.format("%1$s / %2$s", languageCode, regionCode);
        }
    }

    private void selectLocale(ResourceQualifier[] qualifiers) {
        mLocaleCombo.setData(qualifiers);
        String label = getLocaleLabel(qualifiers, true);

        mLocaleCombo.setText(label);

        Image image = getFlagImage(qualifiers);
        mLocaleCombo.setImage(image);

        resizeToolBar();
    }

    private ResourceQualifier[] getSelectedLocale() {
        return (ResourceQualifier[]) mLocaleCombo.getData();
    }

    private IAndroidTarget getSelectedTarget() {
        if (!mTargetCombo.isDisposed()) {
            return (IAndroidTarget) mTargetCombo.getData();
        }

        return null;
    }

    void selectTheme(String theme) {
        assert theme.startsWith(STYLE_RESOURCE_PREFIX)
            || theme.startsWith(ANDROID_STYLE_RESOURCE_PREFIX) : theme;
        mThemeCombo.setData(theme);
        if (theme != null) {
            mThemeCombo.setText(getThemeLabel(theme, true));
        } else {
            // FIXME eclipse claims this is dead code.
            mThemeCombo.setText("(Set Theme)");
        }
        resizeToolBar();
    }

    /** Return the default render target to use, or null if no strong preference */
    private IAndroidTarget findDefaultRenderTarget() {
        // Default to layoutlib version 5
        Sdk current = Sdk.getCurrent();
        if (current != null) {
            IAndroidTarget projectTarget = current.getTarget(mEditedFile.getProject());
            int minProjectApi = Integer.MAX_VALUE;
            if (projectTarget != null) {
                if (!projectTarget.isPlatform() && projectTarget.hasRenderingLibrary()) {
                    // Renderable non-platform targets are all going to be adequate (they
                    // will have at least version 5 of layoutlib) so use the project
                    // target as the render target.
                    return projectTarget;
                }

                if (projectTarget.getVersion().isPreview()
                        && projectTarget.hasRenderingLibrary()) {
                    // If the project target is a preview version, then just use it
                    return projectTarget;
                }

                minProjectApi = projectTarget.getVersion().getApiLevel();
            }

            // We want to pick a render target that contains at least version 5 (and
            // preferably version 6) of the layout library. To do this, we go through the
            // targets and pick the -smallest- API level that is both simultaneously at
            // least as big as the project API level, and supports layoutlib level 5+.
            IAndroidTarget best = null;
            int bestApiLevel = Integer.MAX_VALUE;

            for (IAndroidTarget target : current.getTargets()) {
                // Non-platform targets are not chosen as the default render target
                if (!target.isPlatform()) {
                    continue;
                }

                int apiLevel = target.getVersion().getApiLevel();

                // Ignore targets that have a lower API level than the minimum project
                // API level:
                if (apiLevel < minProjectApi) {
                    continue;
                }

                // Look up the layout lib API level. This property is new so it will only
                // be defined for version 6 or higher, which means non-null is adequate
                // to see if this target is eligible:
                String property = target.getProperty(PkgProps.LAYOUTLIB_API);
                // In addition, Android 3.0 with API level 11 had version 5.0 which is adequate:
                if (property != null || apiLevel >= 11) {
                    if (apiLevel < bestApiLevel) {
                        bestApiLevel = apiLevel;
                        best = target;
                    }
                }
            }

            return best;
        }

        return null;
    }

    private static class ConfigBundle {
        FolderConfiguration config;
        int localeIndex;
        int dockModeIndex;
        int nightModeIndex;

        ConfigBundle() {
            config = new FolderConfiguration();
            localeIndex = 0;
            dockModeIndex = 0;
            nightModeIndex = 0;
        }

        ConfigBundle(ConfigBundle bundle) {
            config = new FolderConfiguration();
            config.set(bundle.config);
            localeIndex = bundle.localeIndex;
            dockModeIndex = bundle.dockModeIndex;
            nightModeIndex = bundle.nightModeIndex;
        }
    }

    private static class ConfigMatch {
        final FolderConfiguration testConfig;
        final Device device;
        final String name;
        final ConfigBundle bundle;

        public ConfigMatch(@NonNull FolderConfiguration testConfig, Device device, String name,
                ConfigBundle bundle) {
            this.testConfig = testConfig;
            this.device = device;
            this.name = name;
            this.bundle = bundle;
        }

        @Override
        public String toString() {
            return device.getName() + " - " + name;
        }
    }

    /**
     * Finds a device/config that can display {@link #mEditedConfig}.
     * <p/>Once found the device and config combos are set to the config.
     * <p/>If there is no compatible configuration, a custom one is created.
     * @param favorCurrentConfig if true, and no best match is found, don't change
     * the current config. This must only be true if the current config is compatible.
     */
    private void findAndSetCompatibleConfig(boolean favorCurrentConfig) {
        // list of compatible device/state/locale
        List<ConfigMatch> anyMatches = new ArrayList<ConfigMatch>();

        // list of actual best match (ie the file is a best match for the
        // device/state)
        List<ConfigMatch> bestMatches = new ArrayList<ConfigMatch>();

        // get a locale that match the host locale roughly (may not be exact match on the region.)
        int localeHostMatch = getLocaleMatch();

        // build a list of combinations of non standard qualifiers to add to each device's
        // qualifier set when testing for a match.
        // These qualifiers are: locale, night-mode, car dock.
        List<ConfigBundle> configBundles = new ArrayList<ConfigBundle>(200);

        // If the edited file has locales, then we have to select a matching locale from
        // the list.
        // However, if it doesn't, we don't randomly take the first locale, we take one
        // matching the current host locale (making sure it actually exist in the project)
        int start, max;
        if (mEditedConfig.getLanguageQualifier() != null || localeHostMatch == -1) {
            // add all the locales
            start = 0;
            max = mLocaleList.size();
        } else {
            // only add the locale host match
            start = localeHostMatch;
            max = localeHostMatch + 1; // test is <
        }

        for (int i = start ; i < max ; i++) {
            ResourceQualifier[] l = mLocaleList.get(i);

            ConfigBundle bundle = new ConfigBundle();
            bundle.config.setLanguageQualifier((LanguageQualifier) l[LOCALE_LANG]);
            bundle.config.setRegionQualifier((RegionQualifier) l[LOCALE_REGION]);

            bundle.localeIndex = i;
            configBundles.add(bundle);
        }

        // add the dock mode to the bundle combinations.
        addDockModeToBundles(configBundles);

        // add the night mode to the bundle combinations.
        addNightModeToBundles(configBundles);

        addRenderTargetToBundles(configBundles);

        for (Device device : mDeviceList) {
            for (State state : device.getAllStates()) {

                // loop on the list of config bundles to create full
                // configurations.
                FolderConfiguration stateConfig = DeviceConfigHelper.getFolderConfig(state);
                for (ConfigBundle bundle : configBundles) {
                    // create a new config with device config
                    FolderConfiguration testConfig = new FolderConfiguration();
                    testConfig.set(stateConfig);

                    // add on top of it, the extra qualifiers from the bundle
                    testConfig.add(bundle.config);

                    if (mEditedConfig.isMatchFor(testConfig)) {
                        // this is a basic match. record it in case we don't
                        // find a match
                        // where the edited file is a best config.
                        anyMatches
                                .add(new ConfigMatch(testConfig, device, state.getName(), bundle));

                        if (isCurrentFileBestMatchFor(testConfig)) {
                            // this is what we want.
                            bestMatches.add(new ConfigMatch(testConfig, device, state.getName(),
                                    bundle));
                        }
                    }
                }
            }
        }

        if (bestMatches.size() == 0) {
            if (favorCurrentConfig) {
                // quick check
                if (mEditedConfig.isMatchFor(mCurrentConfig) == false) {
                    AdtPlugin.log(IStatus.ERROR,
                            "favorCurrentConfig can only be true if the current config is compatible");
                }

                // just display the warning
                AdtPlugin.printErrorToConsole(mEditedFile.getProject(),
                        String.format(
                                "'%1$s' is not a best match for any device/locale combination.",
                                mEditedConfig.toDisplayString()),
                        String.format(
                                "Displaying it with '%1$s'",
                                mCurrentConfig.toDisplayString()));
            } else if (anyMatches.size() > 0) {
                // select the best device anyway.
                ConfigMatch match = selectConfigMatch(anyMatches);
                selectDevice(mState.device = match.device);
                selectState(match.name);
                selectLocale(mLocaleList.get(match.bundle.localeIndex));

                mState.uiMode = UiMode.getByIndex(match.bundle.dockModeIndex);
                mState.night = NightMode.getByIndex(match.bundle.nightModeIndex);

                // TODO: display a better warning!
                computeCurrentConfig();
                AdtPlugin.printErrorToConsole(mEditedFile.getProject(),
                        String.format(
                                "'%1$s' is not a best match for any device/locale combination.",
                                mEditedConfig.toDisplayString()),
                        String.format(
                                "Displaying it with '%1$s' which is compatible, but will actually be displayed with another more specific version of the layout.",
                                mCurrentConfig.toDisplayString()));

            } else {
                // TODO: there is no device/config able to display the layout, create one.
                // For the base config values, we'll take the first device and state,
                // and replace whatever qualifier required by the layout file.
            }
        } else {
            ConfigMatch match = selectConfigMatch(bestMatches);
            selectDevice(mState.device = match.device);
            selectState(match.name);
            selectLocale(mLocaleList.get(match.bundle.localeIndex));
            mState.uiMode = UiMode.getByIndex(match.bundle.dockModeIndex);
            mState.night = NightMode.getByIndex(match.bundle.nightModeIndex);
        }
    }

    /**
     * Note: this comparator imposes orderings that are inconsistent with equals.
     */
    private static class TabletConfigComparator implements Comparator<ConfigMatch> {
        @Override
        public int compare(ConfigMatch o1, ConfigMatch o2) {
            FolderConfiguration config1 = o1 != null ? o1.testConfig : null;
            FolderConfiguration config2 = o2 != null ? o2.testConfig : null;
            if (config1 == null) {
                if (config2 == null) {
                    return 0;
                } else {
                    return -1;
                }
            } else if (config2 == null) {
                return 1;
            }

            ScreenSizeQualifier size1 = config1.getScreenSizeQualifier();
            ScreenSizeQualifier size2 = config2.getScreenSizeQualifier();
            ScreenSize ss1 = size1 != null ? size1.getValue() : ScreenSize.NORMAL;
            ScreenSize ss2 = size2 != null ? size2.getValue() : ScreenSize.NORMAL;

            // X-LARGE is better than all others (which are considered identical)
            // if both X-LARGE, then LANDSCAPE is better than all others (which are identical)

            if (ss1 == ScreenSize.XLARGE) {
                if (ss2 == ScreenSize.XLARGE) {
                    ScreenOrientationQualifier orientation1 =
                            config1.getScreenOrientationQualifier();
                    ScreenOrientation so1 = orientation1.getValue();
                    if (so1 == null) {
                        so1 = ScreenOrientation.PORTRAIT;
                    }
                    ScreenOrientationQualifier orientation2 =
                            config2.getScreenOrientationQualifier();
                    ScreenOrientation so2 = orientation2.getValue();
                    if (so2 == null) {
                        so2 = ScreenOrientation.PORTRAIT;
                    }

                    if (so1 == ScreenOrientation.LANDSCAPE) {
                        if (so2 == ScreenOrientation.LANDSCAPE) {
                            return 0;
                        } else {
                            return -1;
                        }
                    } else if (so2 == ScreenOrientation.LANDSCAPE) {
                        return 1;
                    } else {
                        return 0;
                    }
                } else {
                    return -1;
                }
            } else if (ss2 == ScreenSize.XLARGE) {
                return 1;
            } else {
                return 0;
            }
        }
    }

    /**
     * Note: this comparator imposes orderings that are inconsistent with equals.
     */
    private static class PhoneConfigComparator implements Comparator<ConfigMatch> {

        private SparseIntArray mDensitySort = new SparseIntArray(4);

        public PhoneConfigComparator() {
            // put the sort order for the density.
            mDensitySort.put(Density.HIGH.getDpiValue(),   1);
            mDensitySort.put(Density.MEDIUM.getDpiValue(), 2);
            mDensitySort.put(Density.XHIGH.getDpiValue(),  3);
            mDensitySort.put(Density.LOW.getDpiValue(),    4);
        }

        @Override
        public int compare(ConfigMatch o1, ConfigMatch o2) {
            FolderConfiguration config1 = o1 != null ? o1.testConfig : null;
            FolderConfiguration config2 = o2 != null ? o2.testConfig : null;
            if (config1 == null) {
                if (config2 == null) {
                    return 0;
                } else {
                    return -1;
                }
            } else if (config2 == null) {
                return 1;
            }

            int dpi1 = Density.DEFAULT_DENSITY;
            int dpi2 = Density.DEFAULT_DENSITY;

            DensityQualifier dpiQualifier1 = config1.getDensityQualifier();
            if (dpiQualifier1 != null) {
                Density value = dpiQualifier1.getValue();
                dpi1 = value != null ? value.getDpiValue() : Density.DEFAULT_DENSITY;
            }
            dpi1 = mDensitySort.get(dpi1, 100 /* valueIfKeyNotFound*/);

            DensityQualifier dpiQualifier2 = config2.getDensityQualifier();
            if (dpiQualifier2 != null) {
                Density value = dpiQualifier2.getValue();
                dpi2 = value != null ? value.getDpiValue() : Density.DEFAULT_DENSITY;
            }
            dpi2 = mDensitySort.get(dpi2, 100 /* valueIfKeyNotFound*/);

            if (dpi1 == dpi2) {
                // portrait is better
                ScreenOrientation so1 = ScreenOrientation.PORTRAIT;
                ScreenOrientationQualifier orientationQualifier1 =
                        config1.getScreenOrientationQualifier();
                if (orientationQualifier1 != null) {
                    so1 = orientationQualifier1.getValue();
                    if (so1 == null) {
                        so1 = ScreenOrientation.PORTRAIT;
                    }
                }
                ScreenOrientation so2 = ScreenOrientation.PORTRAIT;
                ScreenOrientationQualifier orientationQualifier2 =
                        config2.getScreenOrientationQualifier();
                if (orientationQualifier2 != null) {
                    so2 = orientationQualifier2.getValue();
                    if (so2 == null) {
                        so2 = ScreenOrientation.PORTRAIT;
                    }
                }

                if (so1 == ScreenOrientation.PORTRAIT) {
                    if (so2 == ScreenOrientation.PORTRAIT) {
                        return 0;
                    } else {
                        return -1;
                    }
                } else if (so2 == ScreenOrientation.PORTRAIT) {
                    return 1;
                } else {
                    return 0;
                }
            }

            return dpi1 - dpi2;
        }
    }

    private ConfigMatch selectConfigMatch(List<ConfigMatch> matches) {
        // API 11-13: look for a x-large device
        int apiLevel = mProjectTarget.getVersion().getApiLevel();
        if (apiLevel >= 11 && apiLevel < 14) {
            // TODO: Maybe check the compatible-screen tag in the manifest to figure out
            // what kind of device should be used for display.
            Collections.sort(matches, new TabletConfigComparator());
        } else {
            // lets look for a high density device
            Collections.sort(matches, new PhoneConfigComparator());
        }

        // Look at the currently active editor to see if it's a layout editor, and if so,
        // look up its configuration and if the configuration is in our match list,
        // use it. This means we "preserve" the current configuration when you open
        // new layouts.
        IEditorPart activeEditor = AdtUtils.getActiveEditor();
        LayoutEditorDelegate delegate = LayoutEditorDelegate.fromEditor(activeEditor);
        if (delegate != null
                && mEditedFile != null
                // (Only do this when the two files are in the same project)
                && delegate.getEditor().getProject() == mEditedFile.getProject()) {
            FolderConfiguration configuration = delegate.getGraphicalEditor().getConfiguration();
            if (configuration != null) {
                for (ConfigMatch match : matches) {
                    if (configuration.equals(match.testConfig)) {
                        return match;
                    }
                }
            }
        }

        // the list has been sorted so that the first item is the best config
        return matches.get(0);
    }

    private void addRenderTargetToBundles(List<ConfigBundle> configBundles) {
        Pair<ResourceQualifier[], IAndroidTarget> state = loadRenderState();
        if (state != null) {
            IAndroidTarget target = state.getSecond();
            if (target != null) {
                int apiLevel = target.getVersion().getApiLevel();
                for (ConfigBundle bundle : configBundles) {
                    bundle.config.setVersionQualifier(
                            new VersionQualifier(apiLevel));
                }
            }
        }
    }

    private void addDockModeToBundles(List<ConfigBundle> addConfig) {
        ArrayList<ConfigBundle> list = new ArrayList<ConfigBundle>();

        // loop on each item and for each, add all variations of the dock modes
        for (ConfigBundle bundle : addConfig) {
            int index = 0;
            for (UiMode mode : UiMode.values()) {
                ConfigBundle b = new ConfigBundle(bundle);
                b.config.setUiModeQualifier(new UiModeQualifier(mode));
                b.dockModeIndex = index++;
                list.add(b);
            }
        }

        addConfig.clear();
        addConfig.addAll(list);
    }

    private void addNightModeToBundles(List<ConfigBundle> addConfig) {
        ArrayList<ConfigBundle> list = new ArrayList<ConfigBundle>();

        // loop on each item and for each, add all variations of the night modes
        for (ConfigBundle bundle : addConfig) {
            int index = 0;
            for (NightMode mode : NightMode.values()) {
                ConfigBundle b = new ConfigBundle(bundle);
                b.config.setNightModeQualifier(new NightModeQualifier(mode));
                b.nightModeIndex = index++;
                list.add(b);
            }
        }

        addConfig.clear();
        addConfig.addAll(list);
    }

    /**
     * Adapts the current device/config selection so that it's compatible with
     * {@link #mEditedConfig}.
     * <p/>If the current selection is compatible, nothing is changed.
     * <p/>If it's not compatible, configs from the current devices are tested.
     * <p/>If none are compatible, it reverts to
     * {@link #findAndSetCompatibleConfig(boolean)}
     */
    private void adaptConfigSelection(boolean needBestMatch) {
        // check the device config (ie sans locale)
        boolean needConfigChange = true; // if still true, we need to find another config.
        boolean currentConfigIsCompatible = false;
        State selectedState = getSelectedDeviceState();
        if (selectedState != null) {
            FolderConfiguration currentConfig = DeviceConfigHelper.getFolderConfig(selectedState);
            if (currentConfig != null && mEditedConfig.isMatchFor(currentConfig)) {
                currentConfigIsCompatible = true; // current config is compatible
                if (needBestMatch == false || isCurrentFileBestMatchFor(currentConfig)) {
                    needConfigChange = false;
                }
            }
        }

        if (needConfigChange) {
            // if the current state/locale isn't a correct match, then
            // look for another state/locale in the same device.
            FolderConfiguration testConfig = new FolderConfiguration();

            // first look in the current device.
            String matchName = null;
            int localeIndex = -1;
            mainloop: for (State state : mState.device.getAllStates()) {
                testConfig.set(DeviceConfigHelper.getFolderConfig(state));

                // loop on the locales.
                for (int i = 0 ; i < mLocaleList.size() ; i++) {
                    ResourceQualifier[] locale = mLocaleList.get(i);

                    // update the test config with the locale qualifiers
                    testConfig.setLanguageQualifier((LanguageQualifier)locale[LOCALE_LANG]);
                    testConfig.setRegionQualifier((RegionQualifier)locale[LOCALE_REGION]);

                    if (mEditedConfig.isMatchFor(testConfig) &&
                            isCurrentFileBestMatchFor(testConfig)) {
                        matchName = state.getName();
                        localeIndex = i;
                        break mainloop;
                    }
                }
            }

            if (matchName != null) {
                selectState(matchName);
                selectLocale(mLocaleList.get(localeIndex));
            } else {
                // no match in current device with any state/locale
                // attempt to find another device that can display this
                // particular state.
                findAndSetCompatibleConfig(currentConfigIsCompatible);
            }
        }
    }

    /**
     * Finds a locale matching the config from a file.
     * @param language the language qualifier or null if none is set.
     * @param region the region qualifier or null if none is set.
     * @return true if there was a change in the combobox as a result of applying the locale
     */
    private boolean setLocaleCombo(ResourceQualifier language, ResourceQualifier region) {
        boolean changed = false;

        // find the locale match. Since the locale list is based on the content of the
        // project resources there must be an exact match.
        // The only trick is that the region could be null in the fileConfig but in our
        // list of locales, this is represented as a RegionQualifier with value of
        // FAKE_LOCALE_VALUE.
        ResourceQualifier[] selectedLocale = getSelectedLocale();
        //changed = prevLanguage != language || region != prevRegion;
        if (selectedLocale != null) {
            ResourceQualifier prevLanguage = selectedLocale[LOCALE_LANG];
            ResourceQualifier prevRegion = selectedLocale[LOCALE_REGION];
            changed = !prevLanguage.equals(language) || !prevRegion.equals(region);
        }

        selectLocale(new ResourceQualifier[] { language, region});

        return changed;
    }

    private void updateConfigDisplay(FolderConfiguration fileConfig) {
        // Label currently hidden
        //String current = fileConfig.toDisplayString();
        //String current = fileConfig.getFolderName(ResourceFolderType.LAYOUT);
        String current = mEditedFile.getParent().getName();
        if (current.equals(FD_RES_LAYOUT)) {
            current = "default";
        }

        // Pretty things up a bit
        //if (current == null || current.equals("default")) {
        //    current = "Default Configuration";
        //}
        mConfigCombo.setText(current);
        resizeToolBar();
    }

    private void saveState() {
        if (mDisableUpdates == 0) {
            State state = getSelectedDeviceState();
            String stateName = state != null ? state.getName() : null;
            mState.stateName = stateName;

            // since the locales are relative to the project, only keeping the index is enough
            mState.locale = getSelectedLocale();
            mState.theme = getSelectedTheme();
            mState.target = getRenderingTarget();
            mState.activity = getSelectedActivity();
        }
    }

    /**
     * Stores the current config selection into the edited file.
     */
    public void storeState() {
        AdtPlugin.setFileProperty(mEditedFile, NAME_CONFIG_STATE, mState.getData());
    }

    private void addLocaleMenuListener(final ToolItem combo) {
        Listener menuListener = new Listener() {
            @Override
            public void handleEvent(Event event) {
                Menu menu = new Menu(ConfigurationComposite.this.getShell(), SWT.POP_UP);
                ResourceQualifier[] current = getSelectedLocale();

                for (final ResourceQualifier[] qualifiers : mLocaleList) {
                    String title = getLocaleLabel(qualifiers, false);
                    MenuItem item = new MenuItem(menu, SWT.CHECK);
                    item.setText(title);
                    Image image = getFlagImage(qualifiers);
                    item.setImage(image);

                    boolean selected = current == qualifiers;
                    if (selected) {
                        item.setSelection(true);
                    }

                    item.addSelectionListener(new SelectionAdapter() {
                        @Override
                        public void widgetSelected(SelectionEvent e) {
                            selectLocale(qualifiers);
                            onLocaleChange();
                        }
                    });
                }

                @SuppressWarnings("unused")
                MenuItem separator = new MenuItem(menu, SWT.SEPARATOR);

                MenuItem item = new MenuItem(menu, SWT.PUSH);
                item.setText("Add New Translation...");
                item.addSelectionListener(new SelectionAdapter() {
                    @Override
                    public void widgetSelected(SelectionEvent e) {
                        IProject project = mEditedFile.getProject();
                        Shell shell = ConfigurationComposite.this.getShell();
                        AddTranslationDialog dialog = new AddTranslationDialog(shell, project);
                        dialog.open();
                    }
                });

                Rectangle bounds = combo.getBounds();
                Point location = new Point(bounds.x, bounds.y + bounds.height);
                location = combo.getParent().toDisplay(location);
                menu.setLocation(location.x, location.y);
                menu.setVisible(true);
            }
        };
        combo.addListener(SWT.Selection, menuListener);
    }

    private Map<String, String> mCountryToLanguage;

    @SuppressWarnings("unused") // FIXME cleanup if really not used anymore?
    private String getCountry(String language, String region) {
        if (RegionQualifier.FAKE_REGION_VALUE.equals(region)) {
            region = "";
        }

        String country = region;
        if (country.isEmpty()) {
            // Special cases
            if (language.equals("ar")) {        //$NON-NLS-1$
                country = "AE";                 //$NON-NLS-1$
            } else if (language.equals("zh")) { //$NON-NLS-1$
                country = "CN";                 //$NON-NLS-1$
            } else if (language.equals("en")) { //$NON-NLS-1$
                country = "US";                 //$NON-NLS-1$
            } else if (language.equals("fa")) { //$NON-NLS-1$
                country = "IR";                 //$NON-NLS-1$
            }
        }

        if (country.isEmpty()) {
            if (mCountryToLanguage == null) {
                Locale[] locales = Locale.getAvailableLocales();
                mCountryToLanguage = Maps.newHashMapWithExpectedSize(locales.length);
                Map<String, Locale> localeMap = Maps.newHashMapWithExpectedSize(locales.length);
                for (int i = 0; i < locales.length; i++) {
                    Locale locale = locales[i];
                    String localeLanguage = locale.getLanguage();
                    String localeCountry = locale.getCountry();
                    if (!localeCountry.isEmpty()) {
                        localeCountry = localeCountry.toLowerCase(Locale.US);
                        Locale old = localeMap.get(localeLanguage);
                        if (old != null) {
                            // For Italian for example it has both a locale with country = Italy
                            // and one with country = Switzerland, so prefer the one where the
                            // language code matches the country.
                            if (!localeLanguage.equals(localeCountry)) {
                                continue;
                            }
                        }
                        mCountryToLanguage.put(localeLanguage, localeCountry);
                        localeMap.put(localeLanguage, locale);
                    }
                }
            }

            country = mCountryToLanguage.get(language);
        }

        return country;
    }

    @NonNull
    private Image getFlagImage(@NonNull ResourceQualifier[] qualifiers) {
        Image image = null;
        assert qualifiers.length == 2;
        String language = ((LanguageQualifier) qualifiers[LOCALE_LANG]).getValue();
        if (LanguageQualifier.FAKE_LANG_VALUE.equals(language)) {
            language = null;
        }
        String region = ((RegionQualifier) qualifiers[LOCALE_REGION]).getValue();
        if (RegionQualifier.FAKE_REGION_VALUE.equals(region)) {
            region = null;
        }
        LocaleManager icons = LocaleManager.get();
        if (language == null && region == null) {
            return LocaleManager.getGlobeIcon();
        } else {
            image = icons.getFlag(language, region);
            if (image == null) {
                image = LocaleManager.getEmptyIcon();
            }

            return image;
        }
    }

    private String getDeviceLabel(Device device, boolean brief) {
        if(device == null) {
            return "";
        }
        String name = device.getName();

        if (brief) {
            // Produce a really brief summary of the device name, suitable for
            // use in the narrow space available in the toolbar for example
            int nexus = name.indexOf("Nexus"); //$NON-NLS-1$
            if (nexus != -1) {
                int begin = name.indexOf('(');
                if (begin != -1) {
                    begin++;
                    int end = name.indexOf(')', begin);
                    if (end != -1) {
                        return name.substring(begin, end).trim();
                    }
                }
            }
        }

        return name;
    }

    private void addDeviceMenuListener(final ToolItem combo) {
        Listener menuListener = new Listener() {
            @Override
            public void handleEvent(Event event) {
                Device current = getSelectedDevice();
                Menu menu = new Menu(ConfigurationComposite.this.getShell(), SWT.POP_UP);

                AvdManager avdManager = Sdk.getCurrent().getAvdManager();
                AvdInfo[] avds = avdManager.getValidAvds();
                boolean separatorNeeded = false;
                for (AvdInfo avd : avds) {
                    for (final Device d : mDeviceList) {
                        if (d.getManufacturer().equals(avd.getDeviceManufacturer())
                                && d.getName().equals(avd.getDeviceName())) {
                            separatorNeeded = true;
                            MenuItem item = new MenuItem(menu, SWT.CHECK);
                            item.setText(avd.getName());
                            item.setSelection(current == d);

                            item.addSelectionListener(new SelectionAdapter() {

                                @Override
                                public void widgetSelected(SelectionEvent e) {
                                    selectDevice(d);
                                    onDeviceChange(true /*recomputeLayout*/);
                                }
                            });
                        }
                    }
                }

                if (separatorNeeded) {
                    @SuppressWarnings("unused")
                    MenuItem separator = new MenuItem(menu, SWT.SEPARATOR);
                }

                // Group the devices by manufacturer, then put them in the menu
                if (!mDeviceList.isEmpty()) {
                    Map<String, List<Device>> manufacturers = new TreeMap<String, List<Device>>();
                    for (Device device : mDeviceList) {
                        List<Device> devices;
                        if(manufacturers.containsKey(device.getManufacturer())) {
                            devices = manufacturers.get(device.getManufacturer());
                        } else {
                            devices = new ArrayList<Device>();
                            manufacturers.put(device.getManufacturer(), devices);
                        }
                        devices.add(device);
                    }
                    for (List<Device> devices : manufacturers.values()) {
                        Menu manufacturerMenu = menu;
                        if (manufacturers.size() > 1) {
                            MenuItem item = new MenuItem(menu, SWT.CASCADE);
                            item.setText(devices.get(0).getManufacturer());
                            manufacturerMenu = new Menu(menu);
                            item.setMenu(manufacturerMenu);
                        }
                        for (final Device d : devices) {
                            MenuItem deviceItem = new MenuItem(manufacturerMenu, SWT.CHECK);
                            deviceItem.setText(d.getName());
                            deviceItem.setSelection(current == d);

                            deviceItem.addSelectionListener(new SelectionAdapter() {

                                @Override
                                public void widgetSelected(SelectionEvent e) {
                                    selectDevice(d);
                                    onDeviceChange(true /*recomputeLayout*/);
                                }
                            });
                        }
                    }
                }

                // TODO - how do I dispose of this?

                Rectangle bounds = combo.getBounds();
                Point location = new Point(bounds.x, bounds.y + bounds.height);
                location = combo.getParent().toDisplay(location);
                menu.setLocation(location.x, location.y);
                menu.setVisible(true);
            }
        };

        if (mDeviceListener != null) {
            combo.removeListener(SWT.Selection, mDeviceListener);
        }
        mDeviceListener = menuListener;
        combo.addListener(SWT.Selection, menuListener);
    }

    private void addTargetMenuListener(final ToolItem combo) {
        Listener menuListener = new Listener() {
            @Override
            public void handleEvent(Event event) {
                Menu menu = new Menu(ConfigurationComposite.this.getShell(), SWT.POP_UP);
                IAndroidTarget current = getSelectedTarget();

                for (final IAndroidTarget target : mTargetList) {
                    String title = getRenderingTargetLabel(target, false);
                    MenuItem item = new MenuItem(menu, SWT.CHECK);
                    item.setText(title);

                    boolean selected = current == target;
                    if (selected) {
                        item.setSelection(true);
                    }

                    item.addSelectionListener(new SelectionAdapter() {
                        @Override
                        public void widgetSelected(SelectionEvent e) {
                            selectTarget(target);
                            onRenderingTargetChange();
                        }
                    });
                }

                Rectangle bounds = combo.getBounds();
                Point location = new Point(bounds.x, bounds.y + bounds.height);
                location = combo.getParent().toDisplay(location);
                menu.setLocation(location.x, location.y);
                menu.setVisible(true);
            }
        };
        combo.addListener(SWT.Selection, menuListener);
    }

    private void addThemeListener(final ToolItem combo) {
        Listener menuListener = new Listener() {
            @Override
            public void handleEvent(Event event) {
                ThemeMenuAction.showThemeMenu(ConfigurationComposite.this, combo, mThemeList);
            }
        };
        combo.addListener(SWT.Selection, menuListener);
    }

    private void addOrientationMenuListener(final ToolItem combo) {
        Listener menuListener = new Listener() {
            @Override
            public void handleEvent(Event event) {
                 if (event.detail == SWT.ARROW) {
                     OrientationMenuAction.showMenu(ConfigurationComposite.this, combo);
                 } else {
                     gotoNextState();
                 }
            }
        };
        combo.addListener(SWT.Selection, menuListener);
    }

    /** Move to the next device state, changing the icon if it changes orientation */
    private void gotoNextState() {
        State state = getSelectedDeviceState();
        State flipped = getNextDeviceState(state);
        if (flipped != state) {
            selectDeviceState(flipped);
            onDeviceConfigChange();
        }
    }

    /** Get the next cyclical state after the given state */
    @Nullable
    State getNextDeviceState(State state) {
        Device device = getSelectedDevice();
        List<State> states = device.getAllStates();
        for (int i = 0; i < states.size(); i++) {
            if (states.get(i) == state) {
                return states.get((i + 1) % states.size());
            }
        }

        return null;
    }

    protected String getThemeLabel(String theme, boolean brief) {
        theme = ResourceHelper.styleToTheme(theme);

        if (brief) {
            int index = theme.lastIndexOf('.');
            if (index < theme.length() - 1) {
                return theme.substring(index + 1);
            }
        }
        return theme;
    }

    private void addActivityMenuListener(final ToolItem combo) {
        Listener menuListener = new Listener() {
            @Override
            public void handleEvent(Event event) {
                // TODO: Allow using fragments here as well?
                Menu menu = new Menu(ConfigurationComposite.this.getShell(), SWT.POP_UP);
                ISharedImages sharedImages = JavaUI.getSharedImages();
                String current = getSelectedActivity();

                if (current != null) {
                    MenuItem item = new MenuItem(menu, SWT.PUSH);
                    String label = getActivityLabel(current, true);;
                    item.setText( String.format("Open %1$s...", label));
                    Image image = sharedImages.getImage(ISharedImages.IMG_OBJS_CUNIT);
                    item.setImage(image);

                    item.addSelectionListener(new SelectionAdapter() {
                        @Override
                        public void widgetSelected(SelectionEvent e) {
                            String fqcn = getSelectedActivity();
                            AdtPlugin.openJavaClass(mEditedFile.getProject(), fqcn);
                        }
                    });

                    @SuppressWarnings("unused")
                    MenuItem separator = new MenuItem(menu, SWT.SEPARATOR);
                }

                IProject project = mEditedFile.getProject();
                Image image = sharedImages.getImage(ISharedImages.IMG_OBJS_CLASS);

                // Add activities found to be relevant to this layout
                String layoutName = ResourceHelper.getLayoutName(mEditedFile);
                String pkg = ManifestInfo.get(project).getPackage();
                List<String> preferred = ManifestInfo.guessActivities(project, layoutName, pkg);
                current = addActivities(menu, current, image, preferred);

                // Add all activities
                List<String> activities = ManifestInfo.getProjectActivities(project);
                if (preferred.size() > 0) {
                    // Filter out the activities we've already listed above
                    List<String> filtered = new ArrayList<String>(activities.size());
                    Set<String> remove = new HashSet<String>(preferred);
                    for (String fqcn : activities) {
                        if (!remove.contains(fqcn)) {
                            filtered.add(fqcn);
                        }
                    }
                    activities = filtered;
                }

                if (activities.size() > 0) {
                    if (preferred.size() > 0) {
                        @SuppressWarnings("unused")
                        MenuItem separator = new MenuItem(menu, SWT.SEPARATOR);
                    }

                    addActivities(menu, current, image, activities);
                }

                Rectangle bounds = combo.getBounds();
                Point location = new Point(bounds.x, bounds.y + bounds.height);
                location = combo.getParent().toDisplay(location);
                menu.setLocation(location.x, location.y);
                menu.setVisible(true);
            }

            private String addActivities(Menu menu, String current, Image image,
                    List<String> activities) {
                for (final String fqcn : activities) {
                    String title = getActivityLabel(fqcn, false);
                    MenuItem item = new MenuItem(menu, SWT.CHECK);
                    item.setText(title);
                    item.setImage(image);

                    boolean selected = title.equals(current);
                    if (selected) {
                        item.setSelection(true);
                        current = null; // Only show the first occurrence as selected
                        // such that we don't show it selected again in the full activity list
                    }

                    item.addSelectionListener(new SelectionAdapter() {
                        @Override
                        public void widgetSelected(SelectionEvent e) {
                            selectActivity(fqcn);
                            onSelectActivity();
                        }
                    });
                }

                return current;
            }
        };
        combo.addListener(SWT.Selection, menuListener);
    }

    private void addConfigurationMenuListener(final ToolItem combo) {
        Listener menuListener = new Listener() {
            @Override
            public void handleEvent(Event event) {
                Menu menu = new Menu(ConfigurationComposite.this.getShell(), SWT.POP_UP);

                // Compute the set of layout files defining this layout resource
                String name = mEditedFile.getName();
                IContainer resFolder = mEditedFile.getParent().getParent();
                List<IFile> variations = new ArrayList<IFile>();
                try {
                    for (IResource resource : resFolder.members()) {
                        if (resource.getName().startsWith(FD_RES_LAYOUT)
                                && resource instanceof IContainer) {
                            IContainer layoutFolder = (IContainer) resource;
                            IResource variation = layoutFolder.findMember(name);
                            if (variation instanceof IFile) {
                                variations.add((IFile) variation);
                            }
                        }
                    }
                } catch (CoreException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }

                ResourceManager manager = ResourceManager.getInstance();
                for (final IFile resource : variations) {
                    MenuItem item = new MenuItem(menu, SWT.CHECK);

                    IFolder parent = (IFolder) resource.getParent();
                    ResourceFolder parentResource = manager.getResourceFolder(parent);
                    FolderConfiguration configuration = parentResource.getConfiguration();
                    String title = configuration.toDisplayString();
                    item.setText(title);

                    boolean selected = mEditedFile.equals(resource);
                    if (selected) {
                        item.setSelection(true);
                    }

                    item.addSelectionListener(new SelectionAdapter() {
                        @Override
                        public void widgetSelected(SelectionEvent e) {
                            ConfigurationComposite.this.getDisplay().asyncExec(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        AdtPlugin.openFile(resource, null, false);
                                    } catch (PartInitException ex) {
                                        AdtPlugin.log(ex, null);
                                    }
                                }
                            });
                        }
                    });
                }

                if (!mEditedConfig.equals(mCurrentConfig)) {
                    if (variations.size() > 0) {
                        @SuppressWarnings("unused")
                        MenuItem separator = new MenuItem(menu, SWT.SEPARATOR);
                    }

                    // Add action for creating a new configuration
                    MenuItem item = new MenuItem(menu, SWT.CHECK);
                    item.setText("Create New...");
                    item.setImage(IconFactory.getInstance().getIcon(ICON_NEW_CONFIG));
                    //item.setToolTipText("Duplicate: Create new configuration for this layout");

                    item.addSelectionListener(new SelectionAdapter() {
                        @Override
                        public void widgetSelected(SelectionEvent e) {
                            if (mListener != null) {
                                mListener.onCreate();
                            }
                        }
                    });
                }

                Rectangle bounds = combo.getBounds();
                Point location = new Point(bounds.x, bounds.y + bounds.height);
                location = combo.getParent().toDisplay(location);
                menu.setLocation(location.x, location.y);
                menu.setVisible(true);
            }
        };
        combo.addListener(SWT.Selection, menuListener);
    }

    /**
     * Updates the locale combo.
     * This must be called from the UI thread.
     */
    public void updateLocales() {
        if (mListener == null) {
            return; // can't do anything w/o it.
        }

        mDisableUpdates++;

        try {
            mLocaleList.clear();

            SortedSet<String> languages = null;

            // get the languages from the project.
            ResourceRepository projectRes = mListener.getProjectResources();

            // in cases where the opened file is not linked to a project, this could be null.
            if (projectRes != null) {
                // now get the languages from the project.
                languages = projectRes.getLanguages();

                for (String language : languages) {
                    LanguageQualifier langQual = new LanguageQualifier(language);

                    // find the matching regions and add them
                    SortedSet<String> regions = projectRes.getRegions(language);
                    for (String region : regions) {
                        RegionQualifier regionQual = new RegionQualifier(region);
                        mLocaleList.add(new ResourceQualifier[] { langQual, regionQual });
                    }

                    // now the entry for the other regions the language alone
                    // create a region qualifier that will never be matched by qualified resources.
                    mLocaleList.add(new ResourceQualifier[] {
                            langQual,
                            new RegionQualifier(RegionQualifier.FAKE_REGION_VALUE)
                    });
                }
            }

            // create language/region qualifier that will never be matched by qualified resources.
            mLocaleList.add(new ResourceQualifier[] {
                    new LanguageQualifier(LanguageQualifier.FAKE_LANG_VALUE),
                    new RegionQualifier(RegionQualifier.FAKE_REGION_VALUE)
            });

            if (mState.locale != null) {
                // FIXME: this may fails if the layout was deleted (and was the last one to have
                // that local. (we have other problem in this case though)
                setLocaleCombo(mState.locale[LOCALE_LANG],
                        mState.locale[LOCALE_REGION]);
            } else {
                //mLocaleCombo.select(0);
                selectLocale(mLocaleList.get(0));
            }
        } finally {
            mDisableUpdates--;
        }
    }

    private int getLocaleMatch() {
        Locale locale = Locale.getDefault();
        if (locale != null) {
            String currentLanguage = locale.getLanguage();
            String currentRegion = locale.getCountry();

            final int count = mLocaleList.size();
            for (int l = 0 ; l < count ; l++) {
                ResourceQualifier[] localeArray = mLocaleList.get(l);
                LanguageQualifier langQ = (LanguageQualifier)localeArray[LOCALE_LANG];
                RegionQualifier regionQ = (RegionQualifier)localeArray[LOCALE_REGION];

                // there's always a ##/Other or ##/Any (which is the same, the region
                // contains FAKE_REGION_VALUE). If we don't find a perfect region match
                // we take the fake region. Since it's last in the list, this makes the
                // test easy.
                if (langQ.getValue().equals(currentLanguage) &&
                        (regionQ.getValue().equals(currentRegion) ||
                         regionQ.getValue().equals(RegionQualifier.FAKE_REGION_VALUE))) {
                    return l;
                }
            }

            // if no locale match the current local locale, it's likely that it is
            // the default one which is the last one.
            return count - 1;
        }

        return -1;
    }

    /**
     * Updates the theme combo.
     * This must be called from the UI thread.
     */
    private void updateThemes() {
        if (mListener == null) {
            return; // can't do anything w/o it.
        }

        ResourceRepository frameworkRes = mListener.getFrameworkResources(getRenderingTarget());

        mDisableUpdates++;

        try {
            if (mEditedFile != null) {
                if (mState.theme == null || mState.theme.isEmpty()
                        || mListener.getIncludedWithin() != null) {
                    mState.theme = null;
                    getPreferredTheme();
                }
                assert mState.theme != null;
            }

            mThemeList.clear();

            ArrayList<String> themes = new ArrayList<String>();
            ResourceRepository projectRes = mListener.getProjectResources();
            // in cases where the opened file is not linked to a project, this could be null.
            if (projectRes != null) {
                // get the configured resources for the project
                Map<ResourceType, Map<String, ResourceValue>> configuredProjectRes =
                    mListener.getConfiguredProjectResources();

                if (configuredProjectRes != null) {
                    // get the styles.
                    Map<String, ResourceValue> styleMap = configuredProjectRes.get(
                            ResourceType.STYLE);

                    if (styleMap != null) {
                        // collect the themes out of all the styles, ie styles that extend,
                        // directly or indirectly a platform theme.
                        for (ResourceValue value : styleMap.values()) {
                            if (isTheme(value, styleMap, null)) {
                                String theme = value.getName();
                                themes.add(theme);
                            }
                        }

                        Collections.sort(themes);

                        for (String theme : themes) {
                            if (!theme.startsWith(PREFIX_RESOURCE_REF)) {
                                theme = STYLE_RESOURCE_PREFIX + theme;
                            }
                            mThemeList.add(theme);
                        }
                    }
                }
                themes.clear();
            }

            // get the themes, and languages from the Framework.
            if (frameworkRes != null) {
                // get the configured resources for the framework
                Map<ResourceType, Map<String, ResourceValue>> frameworResources =
                    frameworkRes.getConfiguredResources(getCurrentConfig());

                if (frameworResources != null) {
                    // get the styles.
                    Map<String, ResourceValue> styles = frameworResources.get(ResourceType.STYLE);

                    // collect the themes out of all the styles.
                    for (ResourceValue value : styles.values()) {
                        String name = value.getName();
                        if (name.startsWith("Theme.") || name.equals("Theme")) { //$NON-NLS-1$ //$NON-NLS-2$
                            themes.add(value.getName());
                        }
                    }

                    // sort them and add them to the combo
                    Collections.sort(themes);

                    for (String theme : themes) {
                        if (!theme.startsWith(PREFIX_RESOURCE_REF)) {
                            theme = ANDROID_STYLE_RESOURCE_PREFIX + theme;
                        }
                        mThemeList.add(theme);
                    }

                    themes.clear();
                }
            }

            // Migration: In the past we didn't store the style prefix in the settings;
            // this meant we might lose track of whether the theme is a project style
            // or a framework style. For now we need to migrate. Search through the
            // theme list until we have a match
            if (!mState.theme.startsWith(PREFIX_RESOURCE_REF)) {
                String projectStyle = STYLE_RESOURCE_PREFIX + mState.theme;
                String frameworkStyle = ANDROID_STYLE_RESOURCE_PREFIX + mState.theme;
                for (String theme : mThemeList) {
                    if (theme.equals(projectStyle)) {
                        mState.theme = projectStyle;
                        break;
                    } else if (theme.equals(frameworkStyle)) {
                        mState.theme = frameworkStyle;
                        break;
                    }
                }
            }

            // TODO: Handle the case where you have a theme persisted that isn't available??
            // We could look up mState.theme and make sure it appears in the list! And if not,
            // picking one.

            selectTheme(mState.theme);
        } finally {
            mDisableUpdates--;
        }
    }

    /** Returns the preferred theme, or null */
    @Nullable
    String getPreferredTheme() {
        if (mListener == null) {
            return null;
        }

        IProject project = mEditedFile.getProject();
        ManifestInfo manifest = ManifestInfo.get(project);

        // Look up the screen size for the current state
        ScreenSize screenSize = null;
        if (mState.device != null) {
            List<State> states = mState.device.getAllStates();
            for (State state : states) {
                ScreenSizeQualifier qualifier =
                    DeviceConfigHelper.getFolderConfig(state).getScreenSizeQualifier();
                screenSize = qualifier.getValue();
                break;
            }
        }

        // Look up the default/fallback theme to use for this project (which
        // depends on the screen size when no particular theme is specified
        // in the manifest)
        String defaultTheme = manifest.getDefaultTheme(mState.target, screenSize);

        String preferred = defaultTheme;
        if (mState.theme == null) {
            // If we are rendering a layout in included context, pick the theme
            // from the outer layout instead

            String activity = getSelectedActivity();
            if (activity != null) {
                Map<String, String> activityThemes = manifest.getActivityThemes();
                preferred = activityThemes.get(activity);
            }
            if (preferred == null) {
                preferred = defaultTheme;
            }
            mState.theme = preferred;
        }

        return preferred;
    }

    // ---- getters for the config selection values ----

    public FolderConfiguration getEditedConfig() {
        return mEditedConfig;
    }

    public FolderConfiguration getCurrentConfig() {
        return mCurrentConfig;
    }

    public void getCurrentConfig(FolderConfiguration config) {
        config.set(mCurrentConfig);
    }

    /**
     * Returns the currently selected {@link Density}. This is guaranteed to be non null.
     */
    public Density getDensity() {
        if (mCurrentConfig != null) {
            DensityQualifier qual = mCurrentConfig.getDensityQualifier();
            if (qual != null) {
                // just a sanity check
                Density d = qual.getValue();
                if (d != Density.NODPI) {
                    return d;
                }
            }
        }

        // no config? return medium as the default density.
        return Density.MEDIUM;
    }

    /**
     * Returns the current device xdpi.
     */
    public float getXDpi() {
        if (mState.device != null) {

            State currState = mState.device.getState(mState.stateName);
            if (currState == null) {
                currState = mState.device.getDefaultState();
            }
            float dpi = (float) currState.getHardware().getScreen().getXdpi();
            if (Float.isNaN(dpi) == false) {
                return dpi;
            }
        }

        // get the pixel density as the density.
        return getDensity().getDpiValue();
    }

    /**
     * Returns the current device ydpi.
     */
    public float getYDpi() {
        if (mState.device != null) {

            State currState = mState.device.getState(mState.stateName);
            if (currState == null) {
                currState = mState.device.getDefaultState();
            }
            float dpi = (float) currState.getHardware().getScreen().getYdpi();
            if (Float.isNaN(dpi) == false) {
                return dpi;
            }
        }

        // get the pixel density as the density.
        return getDensity().getDpiValue();
    }

    public Rect getScreenBounds() {
        // get the orientation from the current device config
        ScreenOrientationQualifier qual = mCurrentConfig.getScreenOrientationQualifier();
        ScreenOrientation orientation = ScreenOrientation.PORTRAIT;
        if (qual != null) {
            orientation = qual.getValue();
        }

        // get the device screen dimension
        ScreenDimensionQualifier qual2 = mCurrentConfig.getScreenDimensionQualifier();
        int s1, s2;
        if (qual2 != null) {
            s1 = qual2.getValue1();
            s2 = qual2.getValue2();
        } else {
            s1 = 480;
            s2 = 320;
        }

        switch (orientation) {
            default:
            case PORTRAIT:
                return new Rect(0, 0, s2, s1);
            case LANDSCAPE:
                return new Rect(0, 0, s1, s2);
            case SQUARE:
                return new Rect(0, 0, s1, s1);
        }
    }

    /**
     * Returns the current theme, or null if the combo has no selection.
     *
     * @return the theme name, or null
     */
    @Nullable
    public String getThemeName() {
        String theme = getSelectedTheme();
        if (theme != null) {
            theme = ResourceHelper.styleToTheme(theme);
        }

        return theme;
    }

    @Nullable
    String getSelectedTheme() {
        return (String) mThemeCombo.getData();
    }

    /**
     * Returns the current device string, or null if the combo has no selection.
     *
     * @return the device name, or null
     */
    public String getDevice() {
        Device device = getSelectedDevice();
        if (device != null) {
            return device.getName();
        }

        return null;
    }

    /**
     * Returns whether the current theme selection is a project theme.
     * <p/>The returned value is meaningless if {@link #getThemeName()} returns <code>null</code>.
     * @return true for project theme, false for framework theme
     */
    public boolean isProjectTheme() {
        String theme = getSelectedTheme();
        if (theme != null) {
            assert theme.startsWith(STYLE_RESOURCE_PREFIX)
            || theme.startsWith(ANDROID_STYLE_RESOURCE_PREFIX);

            return ResourceHelper.isProjectStyle(theme);
        }

        return false;
    }

    @Nullable
    public IAndroidTarget getRenderingTarget() {
        return getSelectedTarget();
    }

    /**
     * Loads the list of {@link IAndroidTarget} and inits the UI with it.
     */
    private void initTargets() {
        mTargetList.clear();

        Sdk currentSdk = Sdk.getCurrent();
        if (currentSdk != null) {
            IAndroidTarget[] targets = currentSdk.getTargets();
            IAndroidTarget match = null;
            for (int i = 0 ; i < targets.length; i++) {
                // FIXME: add check based on project minSdkVersion
                if (targets[i].hasRenderingLibrary()) {
                    mTargetList.add(targets[i]);

                    if (mRenderingTarget != null) {
                        // use equals because the rendering could be from a previous SDK, so
                        // it may not be the same instance.
                        if (mRenderingTarget.equals(targets[i])) {
                            match = targets[i];
                        }
                    } else if (mProjectTarget == targets[i]) {
                        match = targets[i];
                    }
                }
            }

            if (match == null) {
                selectTarget(null);

                // the rendering target is the same as the project.
                mRenderingTarget = mProjectTarget;
            } else {
                selectTarget(match);

                // set the rendering target to the new object.
                mRenderingTarget = match;
            }
        }
    }

    /**
     * Loads the list of {@link Device}s and inits the UI with it.
     */
    private void initDevices() {
        final Sdk sdk = Sdk.getCurrent();
        if (sdk != null) {
            mDeviceList = sdk.getDevices();
            DeviceManager manager = sdk.getDeviceManager();
            // This method can be called more than once, so avoid duplicate entries
            manager.unregisterListener(this);
            manager.registerListener(this);
        } else {
            mDeviceList = new ArrayList<Device>();
        }

        // fill with the devices
        if (!mDeviceList.isEmpty()) {
            Device first = mDeviceList.get(0);
            selectDevice(first);
            List<State> states = first.getAllStates();
            selectDeviceState(states.get(0));
        } else {
            selectDevice(null);
        }
    }

    @Override
    public void onDevicesChange() {
        final Sdk sdk = Sdk.getCurrent();
        mDeviceList = sdk.getDevices();
        Display.getDefault().asyncExec(new Runnable() {
            @Override
            public void run() {
                if (!mDeviceCombo.isDisposed()) {
                    addDeviceMenuListener(mDeviceCombo);
                }
            }
        });
    }

    Image getOrientationIcon(ScreenOrientation orientation, boolean flip) {
        IconFactory icons = IconFactory.getInstance();
        switch (orientation) {
            case LANDSCAPE:
                return icons.getIcon(flip ? ICON_LANDSCAPE_FLIP : ICON_LANDSCAPE);
            case SQUARE:
                return icons.getIcon(ICON_SQUARE);
            case PORTRAIT:
            default:
                return icons.getIcon(flip ? ICON_PORTRAIT_FLIP : ICON_PORTRAIT);
        }
    }

    ImageDescriptor getOrientationImage(ScreenOrientation orientation, boolean flip) {
        IconFactory icons = IconFactory.getInstance();
        switch (orientation) {
            case LANDSCAPE:
                return icons.getImageDescriptor(flip ? ICON_LANDSCAPE_FLIP : ICON_LANDSCAPE);
            case SQUARE:
                return icons.getImageDescriptor(ICON_SQUARE);
            case PORTRAIT:
            default:
                return icons.getImageDescriptor(flip ? ICON_PORTRAIT_FLIP : ICON_PORTRAIT);
        }
    }

    @NonNull
    ScreenOrientation getOrientation(State state) {
        FolderConfiguration config = DeviceConfigHelper.getFolderConfig(state);
        ScreenOrientation orientation = null;
        if (config != null && config.getScreenOrientationQualifier() != null) {
            orientation = config.getScreenOrientationQualifier().getValue();
        }

        if (orientation == null) {
            orientation = ScreenOrientation.PORTRAIT;
        }

        return orientation;
    }

    void selectDeviceState(@Nullable State state) {
        mOrientationCombo.setData(state);

        State nextState = getNextDeviceState(state);
        mOrientationCombo.setImage(getOrientationIcon(getOrientation(state),
                nextState != state));
    }

    State getSelectedDeviceState() {
        return (State) mOrientationCombo.getData();
    }

    /**
     * Selects a given {@link Device} in the device combo, if it is found.
     * @param device the device to select
     * @return true if the device was found.
     */
    private boolean selectDevice(@Nullable Device device) {
        mDeviceCombo.setData(device);
        if (device != null) {
            mDeviceCombo.setText(getDeviceLabel(device, true));
        } else {
            mDeviceCombo.setText("Device");
        }
        resizeToolBar();

        return false;
    }

    @Nullable
    Device getSelectedDevice() {
        return (Device) mDeviceCombo.getData();
    }

    /**
     * Selects a state by name.
     * @param name the name of the state to select.
     */
    private void selectState(String name) {
        Device device = getSelectedDevice();
        State state = null;
        if (device != null) {
            state = device.getState(name);
        }
        selectDeviceState(state);
    }

    /**
     * Called when the selection of the device combo changes.
     * @param recomputeLayout
     */
    private void onDeviceChange(boolean recomputeLayout) {
        // because changing the content of a combo triggers a change event, respect the
        // mDisableUpdates flag
        if (mDisableUpdates > 0) {
            return;
        }

        String newConfigName = null;

        State prevState = getSelectedDeviceState();
        Device device = getSelectedDevice();
        if (mState.device != null && prevState != null && device != null) {
            // get the previous config, so that we can look for a close match
            FolderConfiguration oldConfig = DeviceConfigHelper.getFolderConfig(prevState);
            newConfigName = getClosestMatch(oldConfig, device.getAllStates());
        }
        mState.device = device;

        selectState(newConfigName);

        computeCurrentConfig();

        if (recomputeLayout) {
            onDeviceConfigChange();
        }
    }

    /**
     * Attempts to find a close state among a list
     * @param oldConfig the reference config.
     * @param states the list of states to search through
     * @return the name of the closest state match, or possibly null if no states are compatible
     * (this can only happen if the states don't have a single qualifier that is the same).
     */
    private String getClosestMatch(FolderConfiguration oldConfig, List<State> states) {

        // create 2 lists as we're going to go through one and put the
        // candidates in the other.
        ArrayList<State> list1 = new ArrayList<State>();
        ArrayList<State> list2 = new ArrayList<State>();

        list1.addAll(states);

        final int count = FolderConfiguration.getQualifierCount();
        for (int i = 0 ; i < count ; i++) {
            // compute the new candidate list by only taking states that have
            // the same i-th qualifier as the old state
            for (State s : list1) {
                ResourceQualifier oldQualifier = oldConfig.getQualifier(i);

                FolderConfiguration folderConfig = DeviceConfigHelper.getFolderConfig(s);
                ResourceQualifier newQualifier = folderConfig.getQualifier(i);

                if (oldQualifier == null) {
                    if (newQualifier == null) {
                        list2.add(s);
                    }
                } else if (oldQualifier.equals(newQualifier)) {
                    list2.add(s);
                }
            }

            // at any moment if the new candidate list contains only one match, its name
            // is returned.
            if (list2.size() == 1) {
                return list2.get(0).getName();
            }

            // if the list is empty, then all the new states failed. It is considered ok, and
            // we move to the next qualifier anyway. This way, if a qualifier is different for
            // all new states it is simply ignored.
            if (list2.size() != 0) {
                // move the candidates back into list1.
                list1.clear();
                list1.addAll(list2);
                list2.clear();
            }
        }

        // the only way to reach this point is if there's an exact match.
        // (if there are more than one, then there's a duplicate state and it doesn't matter,
        // we take the first one).
        if (list1.size() > 0) {
            return list1.get(0).getName();
        }

        return null;
    }

    /**
     * Called when the device config selection changes.
     */
    void onDeviceConfigChange() {
        // because changing the content of a combo triggers a change event, respect the
        // mDisableUpdates flag
        if (mDisableUpdates > 0) {
            return;
        }

        if (computeCurrentConfig() && mListener != null) {
            mListener.onConfigurationChange();
            mListener.onDevicePostChange();
        }
    }

    /**
     * Call back for language combo selection
     */
    private void onLocaleChange() {
        // because mLocaleList triggers onLocaleChange at each modification, the filling
        // of the combo with data will trigger notifications, and we don't want that.
        if (mDisableUpdates > 0) {
            return;
        }

        if (computeCurrentConfig() &&  mListener != null) {
            mListener.onConfigurationChange();
        }

        // Store locale project-wide setting
        saveRenderState();
    }

    /**
     * Call back for api level combo selection
     */
    private void onRenderingTargetChange() {
        // because mApiCombo triggers onApiLevelChange at each modification, the filling
        // of the combo with data will trigger notifications, and we don't want that.
        if (mDisableUpdates > 0) {
            return;
        }

        // tell the listener a new rendering target is being set. Need to do this before updating
        // mRenderingTarget.
        if (mListener != null && mRenderingTarget != null) {
            mListener.onRenderingTargetPreChange(mRenderingTarget);
        }

        mRenderingTarget = getRenderingTarget();

        boolean computeOk = computeCurrentConfig();

        // force a theme update to reflect the new rendering target.
        // This must be done after computeCurrentConfig since it'll depend on the currentConfig
        // to figure out the theme list.
        updateThemes();

        // since the state is saved in computeCurrentConfig, we need to resave it since theme
        // change could have impacted it.
        saveState();

        if (mListener != null && mRenderingTarget != null) {
            mListener.onRenderingTargetPostChange(mRenderingTarget);
        }

        // Store project-wide render-target setting
        saveRenderState();

        if (computeOk &&  mListener != null) {
            mListener.onConfigurationChange();
        }
    }

    /**
     * Saves the current state and the current configuration
     *
     * @see #saveState()
     */
    private boolean computeCurrentConfig() {
        saveState();

        if (mState.device != null) {
            // get the device config from the device/state combos.
            FolderConfiguration config =
                DeviceConfigHelper.getFolderConfig(getSelectedDeviceState());

            // replace the config with the one from the device
            mCurrentConfig.set(config);

            // replace the locale qualifiers with the one coming from the locale combo
            ResourceQualifier[] localeQualifiers = getSelectedLocale();
            if (localeQualifiers != null) {
                mCurrentConfig.setLanguageQualifier(
                        (LanguageQualifier)localeQualifiers[LOCALE_LANG]);
                mCurrentConfig.setRegionQualifier(
                        (RegionQualifier)localeQualifiers[LOCALE_REGION]);
            }

            // Replace the UiMode with the selected one, if one is selected
            UiMode uiMode = getSelectedUiMode();
            if (uiMode != null) {
                mCurrentConfig.setUiModeQualifier(new UiModeQualifier(uiMode));
            }

            // Replace the NightMode with the selected one, if one is selected
            NightMode night = getSelectedNightMode();
            if (night != null) {
                mCurrentConfig.setNightModeQualifier(new NightModeQualifier(night));
            }

            // replace the API level by the selection of the combo
            IAndroidTarget target = getRenderingTarget();
            if (target == null) {
                target = mProjectTarget;
            }
            if (target != null) {
                mCurrentConfig.setVersionQualifier(
                        new VersionQualifier(target.getVersion().getApiLevel()));
            }

            return true;
        }

        return false;
    }

    void onThemeChange() {
        saveState();

        String theme = getSelectedTheme();
        if (theme != null && mListener != null) {
            mListener.onThemeChange();
        }
    }

    /**
     * Returns whether the given <var>style</var> is a theme.
     * This is done by making sure the parent is a theme.
     * @param value the style to check
     * @param styleMap the map of styles for the current project. Key is the style name.
     * @param seen the map of styles we have already processed (or null if not yet
     *          initialized). Only the keys are significant (since there is no IdentityHashSet).
     * @return True if the given <var>style</var> is a theme.
     */
    private boolean isTheme(ResourceValue value, Map<String, ResourceValue> styleMap,
            IdentityHashMap<ResourceValue, Boolean> seen) {
        if (value instanceof StyleResourceValue) {
            StyleResourceValue style = (StyleResourceValue)value;

            boolean frameworkStyle = false;
            String parentStyle = style.getParentStyle();
            if (parentStyle == null) {
                // if there is no specified parent style we look an implied one.
                // For instance 'Theme.light' is implied child style of 'Theme',
                // and 'Theme.light.fullscreen' is implied child style of 'Theme.light'
                String name = style.getName();
                int index = name.lastIndexOf('.');
                if (index != -1) {
                    parentStyle = name.substring(0, index);
                }
            } else {
                // remove the useless @ if it's there
                if (parentStyle.startsWith("@")) {
                    parentStyle = parentStyle.substring(1);
                }

                // check for framework identifier.
                if (parentStyle.startsWith(ANDROID_NS_NAME_PREFIX)) {
                    frameworkStyle = true;
                    parentStyle = parentStyle.substring(ANDROID_NS_NAME_PREFIX.length());
                }

                // at this point we could have the format style/<name>. we want only the name
                if (parentStyle.startsWith("style/")) {
                    parentStyle = parentStyle.substring("style/".length());
                }
            }

            if (parentStyle != null) {
                if (frameworkStyle) {
                    // if the parent is a framework style, it has to be 'Theme' or 'Theme.*'
                    return parentStyle.equals("Theme") || parentStyle.startsWith("Theme.");
                } else {
                    // if it's a project style, we check this is a theme.
                    ResourceValue parentValue = styleMap.get(parentStyle);

                    // also prevent stack overflow in case the dev mistakenly declared
                    // the parent of the style as the style itself.
                    if (parentValue != null && parentValue.equals(value) == false) {
                        if (seen == null) {
                            seen = new IdentityHashMap<ResourceValue, Boolean>();
                            seen.put(value, Boolean.TRUE);
                        } else if (seen.containsKey(parentValue)) {
                            return false;
                        }
                        seen.put(parentValue, Boolean.TRUE);
                        return isTheme(parentValue, styleMap, seen);
                    }
                }
            }
        }

        return false;
    }

    /**
     * Checks whether the current edited file is the best match for a given config.
     * <p/>
     * This tests against other versions of the same layout in the project.
     * <p/>
     * The given config must be compatible with the current edited file.
     * @param config the config to test.
     * @return true if the current edited file is the best match in the project for the
     * given config.
     */
    private boolean isCurrentFileBestMatchFor(FolderConfiguration config) {
        ResourceFile match = mResources.getMatchingFile(mEditedFile.getName(),
                ResourceFolderType.LAYOUT, config);

        if (match != null) {
            return match.getFile().equals(mEditedFile);
        } else {
            // if we stop here that means the current file is not even a match!
            AdtPlugin.log(IStatus.ERROR, "Current file is not a match for the given config.");
        }

        return false;
    }

    /**
     * Resets the configuration chooser to reflect the given file configuration. This is
     * intended to be used by the "Show Included In" functionality where the user has
     * picked a non-default configuration (such as a particular landscape layout) and the
     * configuration chooser must be switched to a landscape layout. This method will
     * trigger a model change.
     * <p>
     * This will NOT trigger a redraw event!
     * <p>
     * FIXME: We are currently setting the configuration file to be the configuration for
     * the "outer" (the including) file, rather than the inner file, which is the file the
     * user is actually editing. We need to refine this, possibly with a way for the user
     * to choose which configuration they are editing. And in particular, we should be
     * filtering the configuration chooser to only show options in the outer configuration
     * that are compatible with the inner included file.
     *
     * @param file the file to be configured
     */
    public void resetConfigFor(IFile file) {
        setFile(file);
        mEditedConfig = null;
        onXmlModelLoaded();
    }

    /**
     * Syncs this configuration to the project wide locale and render target settings. The
     * locale may ignore the project-wide setting if it is a locale-specific
     * configuration.
     *
     * @return true if one or both of the toggles were changed, false if there were no
     *         changes
     */
    public boolean syncRenderState() {
        if (mEditedConfig == null) {
            // Startup; ignore
            return false;
        }

        boolean localeChanged = false;
        boolean renderTargetChanged = false;

        // When a page is re-activated, force the toggles to reflect the current project
        // state

        Pair<ResourceQualifier[], IAndroidTarget> pair = loadRenderState();

        // Only sync the locale if this layout is not already a locale-specific layout!
        if (pair != null && !isLocaleSpecificLayout()) {
            ResourceQualifier[] locale = pair.getFirst();
            if (locale != null) {
                localeChanged = setLocaleCombo(locale[0], locale[1]);
            }
        }

        // Sync render target
        IAndroidTarget target = pair != null ? pair.getSecond() : getSelectedTarget();
        if (target != null) {
            if (getRenderingTarget() != target) {
                selectTarget(target);
                renderTargetChanged = true;
            }
        }

        if (!renderTargetChanged && !localeChanged) {
            return false;
        }

        // Update the locale and/or the render target. This code contains a logical
        // merge of the onRenderingTargetChange() and onLocaleChange() methods, combined
        // such that we don't duplicate work.

        if (renderTargetChanged) {
            if (mListener != null && mRenderingTarget != null) {
                mListener.onRenderingTargetPreChange(mRenderingTarget);
            }
            mRenderingTarget = target;
        }

        // Compute the new configuration; we want to do this both for locale changes
        // and for render targets.
        boolean computeOk = computeCurrentConfig();

        if (renderTargetChanged) {
            // force a theme update to reflect the new rendering target.
            // This must be done after computeCurrentConfig since it'll depend on the currentConfig
            // to figure out the theme list.
            updateThemes();

            if (mListener != null && mRenderingTarget != null) {
                mListener.onRenderingTargetPostChange(mRenderingTarget);
            }
        }

        // For both locale and render target changes
        if (computeOk &&  mListener != null) {
            mListener.onConfigurationChange();
        }

        return true;
    }

    /**
     * Loads the render state (the locale and the render target, which are shared among
     * all the layouts meaning that changing it in one will change it in all) and returns
     * the current project-wide locale and render target to be used.
     *
     * @return a pair of locale resource qualifiers and render target
     */
    private Pair<ResourceQualifier[], IAndroidTarget> loadRenderState() {
        IProject project = mEditedFile.getProject();
        if (!project.isAccessible()) {
            return null;
        }

        try {
            String data = project.getPersistentProperty(NAME_RENDER_STATE);
            if (data != null) {
                ResourceQualifier[] locale = null;
                IAndroidTarget target = null;

                String[] values = data.split(SEP);
                if (values.length == 2) {
                    locale = new ResourceQualifier[2];
                    String locales[] = values[0].split(SEP_LOCALE);
                    if (locales.length >= 2) {
                        if (locales[0].length() > 0) {
                            locale[0] = new LanguageQualifier(locales[0]);
                        }
                        if (locales[1].length() > 0) {
                            locale[1] = new RegionQualifier(locales[1]);
                        }
                    }
                    target = stringToTarget(values[1]);

                    // See if we should "correct" the rendering target to a better version.
                    // If you're using a pre-release version of the render target, and a
                    // final release is available and installed, we should switch to that
                    // one instead.
                    if (target != null) {
                        AndroidVersion version = target.getVersion();
                        if (version.getCodename() != null && mTargetList != null) {
                            int targetApiLevel = version.getApiLevel() + 1;
                            for (IAndroidTarget t : mTargetList) {
                                if (t.getVersion().getApiLevel() == targetApiLevel
                                        && t.isPlatform()) {
                                    target = t;
                                    break;
                                }
                            }
                        }
                    }
                }

                return Pair.of(locale, target);
            }

            ResourceQualifier[] any = new ResourceQualifier[] {
                    new LanguageQualifier(LanguageQualifier.FAKE_LANG_VALUE),
                    new RegionQualifier(RegionQualifier.FAKE_REGION_VALUE)
            };

            return Pair.of(any, findDefaultRenderTarget());
        } catch (CoreException e) {
            AdtPlugin.log(e, null);
        }

        return null;
    }

    /** Returns true if the current layout is locale-specific */
    private boolean isLocaleSpecificLayout() {
        return mEditedConfig == null || mEditedConfig.getLanguageQualifier() != null;
    }

    /**
     * Saves the render state (the current locale and render target settings) into the
     * project wide settings storage
     */
    private void saveRenderState() {
        IProject project = mEditedFile.getProject();
        try {
            ResourceQualifier[] locale = getSelectedLocale();
            IAndroidTarget target = getRenderingTarget();

            // Generate a persistent string from locale+target
            StringBuilder sb = new StringBuilder();
            if (locale != null) {
                if (locale[0] != null && locale[1] != null) {
                    // locale[0]/[1] can be null sometimes when starting Eclipse
                    sb.append(((LanguageQualifier) locale[0]).getValue());
                    sb.append(SEP_LOCALE);
                    sb.append(((RegionQualifier) locale[1]).getValue());
                }
            }
            sb.append(SEP);
            if (target != null) {
                sb.append(targetToString(target));
                sb.append(SEP);
            }

            String data = sb.toString();
            project.setPersistentProperty(NAME_RENDER_STATE, data);
        } catch (CoreException e) {
            AdtPlugin.log(e, null);
        }
    }

    // ---- Implements SelectionListener ----

    @Override
    public void widgetSelected(SelectionEvent e) {
        if (mDisableUpdates > 0) {
            return;
        }

        final Object source = e.getSource();
        if (source == mOrientationCombo) {
            gotoNextState();
        }
    }

    @Override
    public void widgetDefaultSelected(SelectionEvent e) {
    }

    /** Returns the file whose rendering is being configured by this configuration composite */
    IFile getEditedFile() {
        return mEditedFile;
    }

    UiMode getSelectedUiMode() {
        return mState.uiMode;
    }

    NightMode getSelectedNightMode() {
        return mState.night;
    }

    void selectNightMode(NightMode night) {
        mState.night = night;
        if (computeCurrentConfig() && mListener != null) {
            mListener.onConfigurationChange();
        }
    }

    void selectUiMode(UiMode uiMode) {
        mState.uiMode = uiMode;
        if (computeCurrentConfig() && mListener != null) {
            mListener.onConfigurationChange();
        }
    }
}
