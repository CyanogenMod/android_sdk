/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.tools.lint.detector.api;

import com.google.common.annotations.Beta;

/**
 * Constants used by the various detectors, defined in one place
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
*/
@Beta
@SuppressWarnings("javadoc") // Not documenting each and every obvious constant
public class LintConstants {
    /** Namespace prefix used for all resources */
    public static final String URI_PREFIX =
            "http://schemas.android.com/apk/res/";                     //$NON-NLS-1$
    /** Namespace used in XML files for Android attributes */
    public static final String ANDROID_URI =
            "http://schemas.android.com/apk/res/android";              //$NON-NLS-1$
    /** Namespace used in XML files for Android Tooling attributes */
    public static final String TOOLS_URI =
            "http://schemas.android.com/tools";                        //$NON-NLS-1$
    /** Namespace used for auto-adjusting namespaces */
    public final static String AUTO_URI =
            "http://schemas.android.com/apk/res-auto";                 //$NON-NLS-1$
    /** Default prefix used for tools attributes */
    public static final String TOOLS_PREFIX = "tools";                 //$NON-NLS-1$
    public static final String XMLNS_PREFIX = "xmlns:";                //$NON-NLS-1$
    public static final String R_CLASS = "R";                          //$NON-NLS-1$
    public static final String ANDROID_PKG = "android";                //$NON-NLS-1$

    // Tags: Manifest
    public static final String TAG_SERVICE = "service";                //$NON-NLS-1$
    public static final String TAG_USES_PERMISSION = "uses-permission";//$NON-NLS-1$
    public static final String TAG_USES_LIBRARY = "uses-library";      //$NON-NLS-1$
    public static final String TAG_APPLICATION = "application";        //$NON-NLS-1$
    public static final String TAG_INTENT_FILTER = "intent-filter";    //$NON-NLS-1$
    public static final String TAG_USES_SDK = "uses-sdk";              //$NON-NLS-1$
    public static final String TAG_ACTIVITY = "activity";              //$NON-NLS-1$
    public static final String TAG_RECEIVER = "receiver";              //$NON-NLS-1$
    public static final String TAG_PROVIDER = "provider";              //$NON-NLS-1$
    public static final String TAG_GRANT_PERMISSION = "grant-uri-permission"; //$NON-NLS-1$
    public static final String TAG_PATH_PERMISSION = "path-permission"; //$NON-NLS-1$

    // Tags: Resources
    public static final String TAG_RESOURCES = "resources";            //$NON-NLS-1$
    public static final String TAG_STRING = "string";                  //$NON-NLS-1$
    public static final String TAG_ARRAY = "array";                    //$NON-NLS-1$
    public static final String TAG_STYLE = "style";                    //$NON-NLS-1$
    public static final String TAG_ITEM = "item";                      //$NON-NLS-1$
    public static final String TAG_STRING_ARRAY = "string-array";      //$NON-NLS-1$
    public static final String TAG_INTEGER_ARRAY = "integer-array";    //$NON-NLS-1$

    // Tags: Layouts
    public static final String VIEW_TAG = "view";                      //$NON-NLS-1$
    public static final String INCLUDE = "include";                    //$NON-NLS-1$
    public static final String MERGE = "merge";                        //$NON-NLS-1$
    public static final String REQUEST_FOCUS = "requestFocus";         //$NON-NLS-1$

    public static final String VIEW = "View";                          //$NON-NLS-1$
    public static final String VIEW_GROUP = "ViewGroup";               //$NON-NLS-1$
    public static final String FRAME_LAYOUT = "FrameLayout";           //$NON-NLS-1$
    public static final String LINEAR_LAYOUT = "LinearLayout";         //$NON-NLS-1$
    public static final String RELATIVE_LAYOUT = "RelativeLayout";     //$NON-NLS-1$
    public static final String GRID_LAYOUT = "GridLayout";             //$NON-NLS-1$
    public static final String SCROLL_VIEW = "ScrollView";             //$NON-NLS-1$
    public static final String BUTTON = "Button";                      //$NON-NLS-1$
    public static final String COMPOUND_BUTTON = "CompoundButton";     //$NON-NLS-1$
    public static final String ADAPTER_VIEW = "AdapterView";           //$NON-NLS-1$
    public static final String GALLERY = "Gallery";                    //$NON-NLS-1$
    public static final String GRID_VIEW = "GridView";                 //$NON-NLS-1$
    public static final String TAB_HOST = "TabHost";                   //$NON-NLS-1$
    public static final String RADIO_GROUP = "RadioGroup";             //$NON-NLS-1$
    public static final String RADIO_BUTTON = "RadioButton";           //$NON-NLS-1$
    public static final String SWITCH = "Switch";                      //$NON-NLS-1$
    public static final String EDIT_TEXT = "EditText";                 //$NON-NLS-1$
    public static final String LIST_VIEW = "ListView";                 //$NON-NLS-1$
    public static final String TEXT_VIEW = "TextView";                 //$NON-NLS-1$
    public static final String CHECKED_TEXT_VIEW = "CheckedTextView";  //$NON-NLS-1$
    public static final String IMAGE_VIEW = "ImageView";               //$NON-NLS-1$
    public static final String SURFACE_VIEW = "SurfaceView";           //$NON-NLS-1$
    public static final String ABSOLUTE_LAYOUT = "AbsoluteLayout";     //$NON-NLS-1$
    public static final String TABLE_LAYOUT = "TableLayout";           //$NON-NLS-1$
    public static final String TABLE_ROW = "TableRow";                 //$NON-NLS-1$
    public static final String TAB_WIDGET = "TabWidget";               //$NON-NLS-1$
    public static final String IMAGE_BUTTON = "ImageButton";           //$NON-NLS-1$
    public static final String SEEK_BAR = "SeekBar";                   //$NON-NLS-1$
    public static final String VIEW_STUB = "ViewStub";                 //$NON-NLS-1$
    public static final String SPINNER = "Spinner";                    //$NON-NLS-1$
    public static final String WEB_VIEW = "WebView";                   //$NON-NLS-1$
    public static final String TOGGLE_BUTTON = "ToggleButton";         //$NON-NLS-1$
    public static final String CHECK_BOX = "CheckBox";                 //$NON-NLS-1$
    public static final String ABS_LIST_VIEW = "AbsListView";          //$NON-NLS-1$
    public static final String PROGRESS_BAR = "ProgressBar";           //$NON-NLS-1$
    public static final String ABS_SPINNER = "AbsSpinner";             //$NON-NLS-1$
    public static final String ABS_SEEK_BAR = "AbsSeekBar";            //$NON-NLS-1$
    public static final String VIEW_ANIMATOR = "ViewAnimator";         //$NON-NLS-1$
    public static final String VIEW_SWITCHER = "ViewSwitcher";         //$NON-NLS-1$
    public static final String EXPANDABLE_LIST_VIEW = "ExpandableListView";    //$NON-NLS-1$
    public static final String HORIZONTAL_SCROLL_VIEW = "HorizontalScrollView"; //$NON-NLS-1$

    // Tags: Drawables
    public static final String TAG_BITMAP = "bitmap";                  //$NON-NLS-1$

    // Attributes: Manifest
    public static final String ATTR_EXPORTED = "exported";             //$NON-NLS-1$
    public static final String ATTR_PERMISSION = "permission";         //$NON-NLS-1$
    public static final String ATTR_MIN_SDK_VERSION = "minSdkVersion"; //$NON-NLS-1$
    public static final String ATTR_TARGET_SDK_VERSION = "targetSdkVersion"; //$NON-NLS-1$
    public static final String ATTR_ICON = "icon";                     //$NON-NLS-1$
    public final static String ATTR_PACKAGE = "package";               //$NON-NLS-1$
    public static final String ATTR_THEME = "theme";                   //$NON-NLS-1$
    public static final String ATTR_PATH = "path";                     //$NON-NLS-1$
    public static final String ATTR_PATH_PREFIX = "pathPrefix";        //$NON-NLS-1$
    public static final String ATTR_PATH_PATTERN = "pathPattern";      //$NON-NLS-1$
    public final static String ATTR_DEBUGGABLE = "debuggable";         //$NON-NLS-1$
    public final static String ATTR_READ_PERMISSION = "readPermission"; //$NON_NLS-1$
    public final static String ATTR_WRITE_PERMISSION = "writePermission"; //$NON_NLS-1$

    // Attributes: Resources
    public static final String ATTR_NAME = "name";                     //$NON-NLS-1$
    public static final String ATTR_TYPE = "type";                     //$NON-NLS-1$
    public static final String ATTR_PARENT = "parent";                 //$NON-NLS-1$
    public static final String ATTR_TRANSLATABLE = "translatable";     //$NON-NLS-1$

    // Attributes: Layout
    public static final String ATTR_LAYOUT_PREFIX = "layout_";         //$NON-NLS-1$
    public static final String ATTR_CLASS = "class";                   //$NON-NLS-1$
    public static final String ATTR_STYLE = "style";                   //$NON-NLS-1$

    public static final String ATTR_ID = "id";                         //$NON-NLS-1$
    public static final String ATTR_TEXT = "text";                     //$NON-NLS-1$
    public static final String ATTR_LABEL = "label";                   //$NON-NLS-1$
    public static final String ATTR_HINT = "hint";                     //$NON-NLS-1$
    public static final String ATTR_PROMPT = "prompt";                 //$NON-NLS-1$
    public static final String ATTR_ON_CLICK = "onClick";              //$NON-NLS-1$
    public static final String ATTR_INPUT_TYPE = "inputType";          //$NON-NLS-1$
    public static final String ATTR_INPUT_METHOD = "inputMethod";      //$NON-NLS-1$
    public static final String ATTR_LAYOUT_GRAVITY = "layout_gravity"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_WIDTH = "layout_width";     //$NON-NLS-1$
    public static final String ATTR_LAYOUT_HEIGHT = "layout_height";   //$NON-NLS-1$
    public static final String ATTR_LAYOUT_WEIGHT = "layout_weight";   //$NON-NLS-1$
    public static final String ATTR_PADDING = "padding";               //$NON-NLS-1$
    public static final String ATTR_PADDING_BOTTOM = "paddingBottom";  //$NON-NLS-1$
    public static final String ATTR_PADDING_TOP = "paddingTop";        //$NON-NLS-1$
    public static final String ATTR_PADDING_RIGHT = "paddingRight";    //$NON-NLS-1$
    public static final String ATTR_PADDING_LEFT = "paddingLeft";      //$NON-NLS-1$
    public static final String ATTR_FOREGROUND = "foreground";         //$NON-NLS-1$
    public static final String ATTR_BACKGROUND = "background";         //$NON-NLS-1$
    public static final String ATTR_ORIENTATION = "orientation";       //$NON-NLS-1$
    public static final String ATTR_LAYOUT = "layout";                 //$NON-NLS-1$
    public static final String ATTR_ROW_COUNT = "rowCount";            //$NON-NLS-1$
    public static final String ATTR_COLUMN_COUNT = "columnCount";      //$NON-NLS-1$
    public static final String ATTR_BASELINE_ALIGNED = "baselineAligned";       //$NON-NLS-1$
    public static final String ATTR_CONTENT_DESCRIPTION = "contentDescription"; //$NON-NLS-1$
    public static final String ATTR_IME_ACTION_LABEL = "imeActionLabel";        //$NON-NLS-1$
    public static final String ATTR_PRIVATE_IME_OPTIONS = "privateImeOptions";  //$NON-NLS-1$
    public static final String VALUE_NONE = "none";                    //$NON-NLS-1$
    public static final String ATTR_NUMERIC = "numeric";               //$NON-NLS-1$
    public static final String ATTR_IME_ACTION_ID = "imeActionId";     //$NON-NLS-1$
    public static final String ATTR_IME_OPTIONS = "imeOptions";        //$NON-NLS-1$
    public static final String ATTR_FREEZES_TEXT = "freezesText";      //$NON-NLS-1$
    public static final String ATTR_EDITOR_EXTRAS = "editorExtras";    //$NON-NLS-1$
    public static final String ATTR_EDITABLE = "editable";             //$NON-NLS-1$
    public static final String ATTR_DIGITS = "digits";                 //$NON-NLS-1$
    public static final String ATTR_CURSOR_VISIBLE = "cursorVisible";  //$NON-NLS-1$
    public static final String ATTR_CAPITALIZE = "capitalize";         //$NON-NLS-1$
    public static final String ATTR_PHONE_NUMBER = "phoneNumber";      //$NON-NLS-1$
    public static final String ATTR_PASSWORD = "password";             //$NON-NLS-1$
    public static final String ATTR_BUFFER_TYPE = "bufferType";        //$NON-NLS-1$
    public static final String ATTR_AUTO_TEXT = "autoText";            //$NON-NLS-1$
    public static final String ATTR_ENABLED = "enabled";               //$NON-NLS-1$
    public static final String ATTR_SINGLE_LINE = "singleLine";        //$NON-NLS-1$

    // AbsoluteLayout layout params
    public static final String ATTR_LAYOUT_Y = "layout_y";             //$NON-NLS-1$
    public static final String ATTR_LAYOUT_X = "layout_x";             //$NON-NLS-1$

    // GridLayout layout params
    public static final String ATTR_LAYOUT_ROW = "layout_row";         //$NON-NLS-1$
    public static final String ATTR_LAYOUT_ROW_SPAN = "layout_rowSpan";//$NON-NLS-1$
    public static final String ATTR_LAYOUT_COLUMN = "layout_column";   //$NON-NLS-1$
    public static final String ATTR_LAYOUT_COLUMN_SPAN = "layout_columnSpan";       //$NON-NLS-1$

    // TableRow
    public static final String ATTR_LAYOUT_SPAN = "layout_span";       //$NON-NLS-1$

    // RelativeLayout layout params:
    public static final String ATTR_LAYOUT_ALIGN_LEFT = "layout_alignLeft";        //$NON-NLS-1$
    public static final String ATTR_LAYOUT_ALIGN_RIGHT = "layout_alignRight";      //$NON-NLS-1$
    public static final String ATTR_LAYOUT_ALIGN_TOP = "layout_alignTop";          //$NON-NLS-1$
    public static final String ATTR_LAYOUT_ALIGN_BOTTOM = "layout_alignBottom";    //$NON-NLS-1$
    public static final String ATTR_LAYOUT_ALIGN_PARENT_TOP = "layout_alignParentTop"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_ALIGN_PARENT_BOTTOM = "layout_alignParentBottom"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_ALIGN_PARENT_LEFT = "layout_alignParentLeft";//$NON-NLS-1$
    public static final String ATTR_LAYOUT_ALIGN_PARENT_RIGHT = "layout_alignParentRight";   //$NON-NLS-1$
    public static final String ATTR_LAYOUT_ALIGN_WITH_PARENT_MISSING = "layout_alignWithParentIfMissing"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_ALIGN_BASELINE = "layout_alignBaseline"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_CENTER_IN_PARENT = "layout_centerInParent"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_CENTER_VERTICAL = "layout_centerVertical"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_CENTER_HORIZONTAL = "layout_centerHorizontal"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_TO_RIGHT_OF = "layout_toRightOf";    //$NON-NLS-1$
    public static final String ATTR_LAYOUT_TO_LEFT_OF = "layout_toLeftOf";      //$NON-NLS-1$
    public static final String ATTR_LAYOUT_BELOW = "layout_below";              //$NON-NLS-1$
    public static final String ATTR_LAYOUT_ABOVE = "layout_above";              //$NON-NLS-1$

    // Margins
    public static final String ATTR_LAYOUT_MARGIN = "layout_margin";               //$NON-NLS-1$
    public static final String ATTR_LAYOUT_MARGIN_LEFT = "layout_marginLeft";      //$NON-NLS-1$
    public static final String ATTR_LAYOUT_MARGIN_RIGHT = "layout_marginRight";    //$NON-NLS-1$
    public static final String ATTR_LAYOUT_MARGIN_TOP = "layout_marginTop";        //$NON-NLS-1$
    public static final String ATTR_LAYOUT_MARGIN_BOTTOM = "layout_marginBottom";  //$NON-NLS-1$

    // Attributes: Drawables
    public static final String ATTR_TILE_MODE = "tileMode";            //$NON-NLS-1$

    // Values: Layouts
    public static final String VALUE_FILL_PARENT = "fill_parent";       //$NON-NLS-1$
    public static final String VALUE_MATCH_PARENT = "match_parent";     //$NON-NLS-1$
    public static final String VALUE_VERTICAL = "vertical";             //$NON-NLS-1$
    public static final String VALUE_TRUE = "true";                     //$NON-NLS-1$
    public static final String VALUE_EDITABLE = "editable";             //$NON-NLS-1$


    // Values: Resources
    public static final String VALUE_ID = "id";                        //$NON-NLS-1$

    // Values: Drawables
    public static final String VALUE_DISABLED = "disabled";            //$NON-NLS-1$
    public static final String VALUE_CLAMP = "clamp";                  //$NON-NLS-1$

    // Menus
    public static final String ATTR_SHOW_AS_ACTION = "showAsAction";   //$NON-NLS-1$
    public static final String VALUE_IF_ROOM = "ifRoom";               //$NON-NLS-1$
    public static final String VALUE_ALWAYS = "always";                //$NON-NLS-1$

    // Filenames and folder names
    public static final String ANDROID_MANIFEST_XML = "AndroidManifest.xml"; //$NON-NLS-1$
    public static final String OLD_PROGUARD_FILE = "proguard.cfg";     //$NON-NLS-1$
    public static final String PROGUARD_FILE = "proguard-project.txt"; //$NON-NLS-1$

    public static final String RES_FOLDER = "res";                     //$NON-NLS-1$
    public static final String DOT_XML = ".xml";                       //$NON-NLS-1$
    public static final String DOT_GIF = ".gif";                       //$NON-NLS-1$
    public static final String DOT_JPG = ".jpg";                       //$NON-NLS-1$
    public static final String DOT_PNG = ".png";                       //$NON-NLS-1$
    public static final String DOT_9PNG = ".9.png";                    //$NON-NLS-1$
    public static final String DOT_JAVA = ".java";                     //$NON-NLS-1$
    public static final String DOT_CLASS = ".class";                   //$NON-NLS-1$
    public static final String DOT_JAR = ".jar";                       //$NON-NLS-1$
    public static final String DRAWABLE_FOLDER = "drawable";           //$NON-NLS-1$
    public static final String DRAWABLE_XHDPI = "drawable-xhdpi";      //$NON-NLS-1$
    public static final String DRAWABLE_HDPI = "drawable-hdpi";        //$NON-NLS-1$
    public static final String DRAWABLE_MDPI = "drawable-mdpi";        //$NON-NLS-1$
    public static final String DRAWABLE_LDPI = "drawable-ldpi";        //$NON-NLS-1$

    // Resources
    public static final String ANDROID_RESOURCE_PREFIX = "@android:";   //$NON-NLS-1$
    public static final String ID_RESOURCE_PREFIX = "@id/";             //$NON-NLS-1$
    public static final String NEW_ID_RESOURCE_PREFIX = "@+id/";        //$NON-NLS-1$
    public static final String DRAWABLE_RESOURCE_PREFIX = "@drawable/"; //$NON-NLS-1$
    public static final String LAYOUT_RESOURCE_PREFIX = "@layout/";     //$NON-NLS-1$
    public static final String STYLE_RESOURCE_PREFIX = "@style/";       //$NON-NLS-1$
    public static final String STRING_RESOURCE_PREFIX = "@string/";     //$NON-NLS-1$
    public static final String RESOURCE_CLZ_ID = "id";                  //$NON-NLS-1$
    public static final String RESOURCE_CLZ_COLOR = "color";            //$NON-NLS-1$
    public static final String RESOURCE_CLZ_ARRAY = "array";            //$NON-NLS-1$
    public static final String RESOURCE_CLZ_ATTR = "attr";              //$NON-NLS-1$
    public static final String RESOURCE_CLR_STYLEABLE = "styleable";    //$NON-NLS-1$
    public static final String NULL_RESOURCE = "@null";                 //$NON-NLS-1$
    public static final String TRANSPARENT_COLOR = "@android:color/transparent";      //$NON-NLS-1$
    public static final String ANDROID_STYLE_RESOURCE_PREFIX = "@android:style/";     //$NON-NLS-1$
    public static final String ANDROID_STRING_RESOURCE_PREFIX = "@android:string/";   //$NON-NLS-1$

    // Packages
    public static final String ANDROID_PKG_PREFIX = "android.";         //$NON-NLS-1$
    public static final String WIDGET_PKG_PREFIX = "android.widget.";   //$NON-NLS-1$
    public static final String VIEW_PKG_PREFIX = "android.view.";       //$NON-NLS-1$

    // Project properties
    public static final String ANDROID_LIBRARY = "android.library";     //$NON-NLS-1$
    public static final String PROGUARD_CONFIG = "proguard.config";     //$NON-NLS-1$
    public static final String ANDROID_LIBRARY_REFERENCE_FORMAT = "android.library.reference.%1$d";//$NON-NLS-1$
    public static final String PROJECT_PROPERTIES = "project.properties";//$NON-NLS-1$

    // Java References
    public static final String ATTR_REF_PREFIX = "?attr/";               //$NON-NLS-1$
    public static final String R_PREFIX = "R.";                          //$NON-NLS-1$
    public static final String R_ID_PREFIX = "R.id.";                    //$NON-NLS-1$
    public static final String R_LAYOUT_PREFIX = "R.layout.";            //$NON-NLS-1$
    public static final String R_DRAWABLE_PREFIX = "R.drawable.";        //$NON-NLS-1$
    public static final String R_ATTR_PREFIX = "R.attr.";                //$NON-NLS-1$

    // Attributes related to tools
    public final static String ATTR_IGNORE = "ignore";                   //$NON-NLS-1$

    // SuppressLint
    public static final String SUPPRESS_ALL = "all";                     //$NON-NLS-1$
    public static final String SUPPRESS_LINT = "SuppressLint";           //$NON-NLS-1$
    public static final String TARGET_API = "TargetApi";                 //$NON-NLS-1$
    public static final String FQCN_SUPPRESS_LINT = "android.annotation." + SUPPRESS_LINT; //$NON-NLS-1$
    public static final String FQCN_TARGET_API = "android.annotation." + TARGET_API; //$NON-NLS-1$
}
