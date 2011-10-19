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

package com.android.ide.common.layout;

import static com.android.ide.eclipse.adt.AdtConstants.ANDROID_PKG;

import com.android.sdklib.SdkConstants;

/**
 * A bunch of constants that map to either:
 * <ul>
 * <li>Android Layouts XML element names (Linear, Relative, Absolute, etc.)
 * <li>Attributes for layout XML elements.
 * <li>Values for attributes.
 * </ul>
 */
public class LayoutConstants {
    /** The element name in a {@code <view class="...">} element. */
    public static final String VIEW = "view";                           //$NON-NLS-1$

    /** The attribute name in a {@code <view class="...">} element. */
    public static final String ATTR_CLASS = "class";                    //$NON-NLS-1$
    public static final String ATTR_ON_CLICK = "onClick";               //$NON-NLS-1$
    public static final String ATTR_TAG = "tag";                        //$NON-NLS-1$
    public static final String ATTR_NUM_COLUMNS = "numColumns";         //$NON-NLS-1$
    public static final String ATTR_PADDING = "padding";                //$NON-NLS-1$

    // Some common layout element names
    public static final String RELATIVE_LAYOUT = "RelativeLayout";      //$NON-NLS-1$
    public static final String LINEAR_LAYOUT   = "LinearLayout";        //$NON-NLS-1$
    public static final String ABSOLUTE_LAYOUT = "AbsoluteLayout";      //$NON-NLS-1$
    public static final String TABLE_LAYOUT = "TableLayout";            //$NON-NLS-1$
    public static final String TABLE_ROW = "TableRow";                  //$NON-NLS-1$
    public static final String CALENDAR_VIEW = "CalendarView";          //$NON-NLS-1$
    public static final String LIST_VIEW = "ListView";                  //$NON-NLS-1$
    public static final String EDIT_TEXT = "EditText";                  //$NON-NLS-1$
    public static final String GALLERY = "Gallery";                     //$NON-NLS-1$
    public static final String GRID_LAYOUT = "GridLayout";              //$NON-NLS-1$
    public static final String GRID_VIEW = "GridView";                  //$NON-NLS-1$
    public static final String SPINNER = "Spinner";                     //$NON-NLS-1$
    public static final String SCROLL_VIEW = "ScrollView";              //$NON-NLS-1$
    public static final String RADIO_BUTTON = "RadioButton";            //$NON-NLS-1$
    public static final String RADIO_GROUP = "RadioGroup";              //$NON-NLS-1$
    public static final String SPACE = "Space";                         //$NON-NLS-1$
    public static final String EXPANDABLE_LIST_VIEW = "ExpandableListView";//$NON-NLS-1$
    public static final String GESTURE_OVERLAY_VIEW = "GestureOverlayView";//$NON-NLS-1$
    public static final String HORIZONTAL_SCROLL_VIEW = "HorizontalScrollView"; //$NON-NLS-1$

    public static final String ATTR_TEXT = "text";                      //$NON-NLS-1$
    public static final String ATTR_HINT = "hint";                      //$NON-NLS-1$
    public static final String ATTR_ID = "id";                          //$NON-NLS-1$
    public static final String ATTR_STYLE = "style";                    //$NON-NLS-1$
    public static final String ATTR_HANDLE = "handle";                  //$NON-NLS-1$
    public static final String ATTR_CONTENT = "content";                //$NON-NLS-1$
    public static final String ATTR_CHECKED = "checked";                //$NON-NLS-1$
    public static final String ATTR_BACKGROUND = "background";          //$NON-NLS-1$

    public static final String ATTR_LAYOUT_PREFIX = "layout_";          //$NON-NLS-1$
    public static final String ATTR_LAYOUT_HEIGHT = "layout_height";    //$NON-NLS-1$
    public static final String ATTR_LAYOUT_WIDTH = "layout_width";      //$NON-NLS-1$
    public static final String ATTR_LAYOUT_GRAVITY = "layout_gravity";  //$NON-NLS-1$
    public static final String ATTR_LAYOUT_WEIGHT = "layout_weight";    //$NON-NLS-1$

    public static final String ATTR_LAYOUT_MARGIN = "layout_margin";               //$NON-NLS-1$
    public static final String ATTR_LAYOUT_MARGIN_LEFT = "layout_marginLeft";      //$NON-NLS-1$
    public static final String ATTR_LAYOUT_MARGIN_RIGHT = "layout_marginRight";    //$NON-NLS-1$
    public static final String ATTR_LAYOUT_MARGIN_TOP = "layout_marginTop";        //$NON-NLS-1$
    public static final String ATTR_LAYOUT_MARGIN_BOTTOM = "layout_marginBottom";  //$NON-NLS-1$

    // RelativeLayout layout params:
    public static final String ATTR_LAYOUT_ALIGN_LEFT = "layout_alignLeft";        //$NON-NLS-1$
    public static final String ATTR_LAYOUT_ALIGN_RIGHT = "layout_alignRight";      //$NON-NLS-1$
    public static final String ATTR_LAYOUT_ALIGN_TOP = "layout_alignTop";          //$NON-NLS-1$
    public static final String ATTR_LAYOUT_ALIGN_BOTTOM = "layout_alignBottom";    //$NON-NLS-1$
    public static final String ATTR_LAYOUT_ALIGN_PARENT_TOP = "layout_alignParentTop"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_ALIGN_PARENT_BOTTOM = "layout_alignParentBottom"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_ALIGN_PARENT_LEFT = "layout_alignParentLeft";//$NON-NLS-1$
    public static final String ATTR_LAYOUT_ALIGN_PARENT_RIGHT = "layout_alignParentRight";   //$NON-NLS-1$
    public static final String ATTR_LAYOUT_ALIGN_WITH_PARENT_MISSING = "layout_alignWithParentMissing"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_ALIGN_BASELINE = "layout_alignBaseline"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_CENTER_IN_PARENT = "layout_centerInParent"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_CENTER_VERTICAL = "layout_centerVertical"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_CENTER_HORIZONTAL = "layout_centerHorizontal"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_TO_RIGHT_OF = "layout_toRightOf";    //$NON-NLS-1$
    public static final String ATTR_LAYOUT_TO_LEFT_OF = "layout_toLeftOf";      //$NON-NLS-1$
    public static final String ATTR_LAYOUT_BELOW = "layout_below";              //$NON-NLS-1$
    public static final String ATTR_LAYOUT_ABOVE = "layout_above";              //$NON-NLS-1$

    // GridLayout
    public static final String ATTR_ROW_COUNT = "rowCount";                         //$NON-NLS-1$
    public static final String ATTR_COLUMN_COUNT = "columnCount";                   //$NON-NLS-1$
    public static final String ATTR_USE_DEFAULT_MARGINS = "useDefaultMargins";      //$NON-NLS-1$
    public static final String ATTR_MARGINS_INCLUDED_IN_ALIGNMENT = "marginsIncludedInAlignment"; //$NON-NLS-1$

    // GridLayout layout params
    public static final String ATTR_LAYOUT_ROW = "layout_row";                      //$NON-NLS-1$
    public static final String ATTR_LAYOUT_ROW_SPAN = "layout_rowSpan";             //$NON-NLS-1$
    //public static final String ATTR_LAYOUT_ROW_WEIGHT = "layout_rowWeight";         //$NON-NLS-1$
    public static final String ATTR_LAYOUT_COLUMN = "layout_column";                //$NON-NLS-1$
    public static final String ATTR_LAYOUT_COLUMN_SPAN = "layout_columnSpan";       //$NON-NLS-1$
    //public static final String ATTR_LAYOUT_COLUMN_WEIGHT = "layout_columnWeight";   //$NON-NLS-1$

    public static final String ATTR_LAYOUT_Y = "layout_y";                      //$NON-NLS-1$
    public static final String ATTR_LAYOUT_X = "layout_x";                      //$NON-NLS-1$
    public static final String ATTR_NAME = "name";                              //$NON-NLS-1$

    public static final String VALUE_WRAP_CONTENT = "wrap_content";             //$NON-NLS-1$
    public static final String VALUE_FILL_PARENT = "fill_parent";               //$NON-NLS-1$
    public static final String VALUE_TRUE = "true";                             //$NON-NLS-1$
    public static final String VALUE_FALSE= "false";                            //$NON-NLS-1$
    public static final String VALUE_N_DP = "%ddp";                             //$NON-NLS-1$
    public static final String VALUE_ZERO_DP = "0dp";                           //$NON-NLS-1$
    public static final String VALUE_ONE_DP = "1dp";                            //$NON-NLS-1$
    public static final String VALUE_TOP = "top";                               //$NON-NLS-1$
    public static final String VALUE_LEFT = "left";                             //$NON-NLS-1$
    public static final String VALUE_RIGHT = "right";                           //$NON-NLS-1$
    public static final String VALUE_BOTTOM = "bottom";                         //$NON-NLS-1$
    public static final String VALUE_CENTER_VERTICAL = "center_vertical";       //$NON-NLS-1$
    public static final String VALUE_CENTER_HORIZONTAL = "center_horizontal";   //$NON-NLS-1$
    public static final String VALUE_FILL_HORIZONTAL = "fill_horizontal";       //$NON-NLS-1$
    public static final String VALUE_FILL_VERTICAL = "fill_vertical";           //$NON-NLS-1$
    public static final String VALUE_0 = "0";                                   //$NON-NLS-1$
    public static final String VALUE_1 = "1";                                   //$NON-NLS-1$

    // Gravity values. These have the GRAVITY_ prefix in front of value because we already
    // have VALUE_CENTER_HORIZONTAL defined for layouts, and its definition conflicts
    // (centerHorizontal versus center_horizontal)
    public static final String GRAVITY_VALUE_ = "center";                             //$NON-NLS-1$
    public static final String GRAVITY_VALUE_CENTER = "center";                       //$NON-NLS-1$
    public static final String GRAVITY_VALUE_RIGHT = "right";                         //$NON-NLS-1$
    public static final String GRAVITY_VALUE_LEFT = "left";                           //$NON-NLS-1$
    public static final String GRAVITY_VALUE_BOTTOM = "bottom";                       //$NON-NLS-1$
    public static final String GRAVITY_VALUE_TOP = "top";                             //$NON-NLS-1$
    public static final String GRAVITY_VALUE_FILL_HORIZONTAL = "fill_horizontal";     //$NON-NLS-1$
    public static final String GRAVITY_VALUE_FILL_VERTICAL = "fill_vertical";         //$NON-NLS-1$
    public static final String GRAVITY_VALUE_CENTER_HORIZONTAL = "center_horizontal"; //$NON-NLS-1$
    public static final String GRAVITY_VALUE_CENTER_VERTICAL = "center_vertical";     //$NON-NLS-1$
    public static final String GRAVITY_VALUE_FILL = "fill";                           //$NON-NLS-1$

    /** The default prefix used for the {@link #ANDROID_URI} name space */
    public static final String ANDROID_NS_NAME = "android"; //$NON-NLS-1$
    /** The default prefix used for the {@link #ANDROID_URI} name space including the colon  */
    public static final String ANDROID_NS_NAME_PREFIX = "android:"; //$NON-NLS-1$

    /**
     * Namespace for the Android resource XML, i.e.
     * "http://schemas.android.com/apk/res/android"
     */
    public static final String ANDROID_URI = SdkConstants.NS_RESOURCES;

    /**
     * The top level android package as a prefix, "android.".
     */
    public static final String ANDROID_PKG_PREFIX = ANDROID_PKG + '.';

    /** The android.view. package prefix */
    public static final String ANDROID_VIEW_PKG = ANDROID_PKG_PREFIX + "view."; //$NON-NLS-1$

    /** The android.widget. package prefix */
    public static final String ANDROID_WIDGET_PREFIX = ANDROID_PKG_PREFIX + "widget."; //$NON-NLS-1$

    /** The android.webkit. package prefix */
    public static final String ANDROID_WEBKIT_PKG = ANDROID_PKG_PREFIX + "webkit."; //$NON-NLS-1$

    /** The LayoutParams inner-class name suffix, .LayoutParams */
    public static final String DOT_LAYOUT_PARAMS = ".LayoutParams"; //$NON-NLS-1$

    /** The fully qualified class name of an EditText view */
    public static final String FQCN_EDIT_TEXT = "android.widget.EditText"; //$NON-NLS-1$

    /** The fully qualified class name of a LinearLayout view */
    public static final String FQCN_LINEAR_LAYOUT = "android.widget.LinearLayout"; //$NON-NLS-1$

    /** The fully qualified class name of a RelativeLayout view */
    public static final String FQCN_RELATIVE_LAYOUT = "android.widget.RelativeLayout"; //$NON-NLS-1$

    /** The fully qualified class name of a RelativeLayout view */
    public static final String FQCN_GRID_LAYOUT = "android.widget.GridLayout"; //$NON-NLS-1$

    /** The fully qualified class name of a FrameLayout view */
    public static final String FQCN_FRAME_LAYOUT = "android.widget.FrameLayout"; //$NON-NLS-1$

    /** The fully qualified class name of a TableRow view */
    public static final String FQCN_TABLE_ROW = "android.widget.TableRow"; //$NON-NLS-1$

    /** The fully qualified class name of a TableLayout view */
    public static final String FQCN_TABLE_LAYOUT = "android.widget.TableLayout"; //$NON-NLS-1$

    /** The fully qualified class name of a GridView view */
    public static final String FQCN_GRID_VIEW = "android.widget.GridView"; //$NON-NLS-1$

    /** The fully qualified class name of a TabWidget view */
    public static final String FQCN_TAB_WIDGET = "android.widget.TabWidget"; //$NON-NLS-1$

    /** The fully qualified class name of a Button view */
    public static final String FQCN_BUTTON = "android.widget.Button"; //$NON-NLS-1$

    /** The fully qualified class name of a RadioButton view */
    public static final String FQCN_RADIO_BUTTON = "android.widget.RadioButton"; //$NON-NLS-1$

    /** The fully qualified class name of a ToggleButton view */
    public static final String FQCN_TOGGLE_BUTTON = "android.widget.ToggleButton"; //$NON-NLS-1$

    /** The fully qualified class name of a Spinner view */
    public static final String FQCN_SPINNER = "android.widget.Spinner"; //$NON-NLS-1$

    /** The fully qualified class name of an AdapterView */
    public static final String FQCN_ADAPTER_VIEW = "android.widget.AdapterView"; //$NON-NLS-1$

    /** The fully qualified class name of a ListView */
    public static final String FQCN_LIST_VIEW = "android.widget.ListView"; //$NON-NLS-1$

    /** The fully qualified class name of an ExpandableListView */
    public static final String FQCN_EXPANDABLE_LIST_VIEW = "android.widget.ExpandableListView"; //$NON-NLS-1$

    /** The fully qualified class name of a GestureOverlayView */
    public static final String FQCN_GESTURE_OVERLAY_VIEW = "android.gesture.GestureOverlayView"; //$NON-NLS-1$

    /** The fully qualified class name of a DatePicker */
    public static final String FQCN_DATE_PICKER = "android.widget.DatePicker"; //$NON-NLS-1$

    /** The fully qualified class name of a TimePicker */
    public static final String FQCN_TIME_PICKER = "android.widget.TimePicker"; //$NON-NLS-1$

    /** The fully qualified class name of a RadioGroup */
    public static final String FQCN_RADIO_GROUP = "android.widgets.RadioGroup";  //$NON-NLS-1$

    /** The fully qualified class name of a Space */
    public static final String FQCN_SPACE = "android.widget.Space"; //$NON-NLS-1$

    public static final String ATTR_SRC = "src"; //$NON-NLS-1$

    // like fill_parent for API 8
    public static final String VALUE_MATCH_PARENT = "match_parent"; //$NON-NLS-1$

    public static final String ATTR_GRAVITY = "gravity"; //$NON-NLS-1$
    public static final String ATTR_WEIGHT_SUM = "weightSum"; //$NON-NLS-1$
    public static final String ATTR_BASELINE_ALIGNED = "baselineAligned"; //$NON-NLS-1$
    public static String ATTR_ORIENTATION = "orientation"; //$NON-NLS-1$

    public static String VALUE_HORIZONTAL = "horizontal"; //$NON-NLS-1$

    public static String VALUE_VERTICAL = "vertical"; //$NON-NLS-1$

    /** The prefix for new id attribute values, @+id/ */
    public static String NEW_ID_PREFIX = "@+id/"; //$NON-NLS-1$

    /** The prefix for existing id attribute values, @id/ */
    public static String ID_PREFIX = "@id/"; //$NON-NLS-1$

    /** Prefix for resources that reference layouts */
    public static String LAYOUT_PREFIX = "@layout/"; //$NON-NLS-1$

    /** Prefix for resources that reference drawables */
    public static String DRAWABLE_PREFIX = "@drawable/"; //$NON-NLS-1$

    /** Prefix for resources that reference strings */
    public static String STRING_PREFIX = "@string/"; //$NON-NLS-1$

    /** Prefix for resources that reference Android strings */
    public static String ANDROID_STRING_PREFIX = "@android:string/"; //$NON-NLS-1$

    /** Prefix for resources that reference Android layouts */
    public static String ANDROID_LAYOUT_PREFIX = "@android:layout/"; //$NON-NLS-1$
}
