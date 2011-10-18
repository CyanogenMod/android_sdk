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

import com.android.resources.ResourceFolderType;

import org.w3c.dom.Element;

/**
 * Abstract class specifically intended for layout detectors which provides some
 * common utility methods shared by layout detectors.
 * <p/>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
public abstract class LayoutDetector extends ResourceXmlDetector {
    // Layouts
    protected static final String FRAME_LAYOUT = "FrameLayout";           //$NON-NLS-1$
    protected static final String LINEAR_LAYOUT = "LinearLayout";         //$NON-NLS-1$
    protected static final String SCROLL_VIEW = "ScrollView";             //$NON-NLS-1$
    protected static final String GALLERY = "Gallery";                    //$NON-NLS-1$
    protected static final String GRID_VIEW = "GridView";                 //$NON-NLS-1$
    protected static final String EDIT_TEXT = "EditText";                 //$NON-NLS-1$
    protected static final String LIST_VIEW = "ListView";                 //$NON-NLS-1$
    protected static final String TEXT_VIEW = "TextView";                 //$NON-NLS-1$
    protected static final String IMAGE_VIEW = "ImageView";               //$NON-NLS-1$
    protected static final String INCLUDE = "include";                    //$NON-NLS-1$
    protected static final String MERGE = "merge";                        //$NON-NLS-1$
    protected static final String HORIZONTAL_SCROLL_VIEW = "HorizontalScrollView"; //$NON-NLS-1$

    // Attributes
    protected static final String ATTR_ID = "id";                         //$NON-NLS-1$
    protected static final String ATTR_TEXT = "text";                     //$NON-NLS-1$
    protected static final String ATTR_LABEL = "label";                   //$NON-NLS-1$
    protected static final String ATTR_HINT = "hint";                     //$NON-NLS-1$
    protected static final String ATTR_PROMPT = "prompt";                 //$NON-NLS-1$
    protected static final String ATTR_INPUT_TYPE = "inputType";          //$NON-NLS-1$
    protected static final String ATTR_INPUT_METHOD = "inputMethod";      //$NON-NLS-1$
    protected static final String ATTR_LAYOUT_GRAVITY = "layout_gravity"; //$NON-NLS-1$
    protected static final String ATTR_LAYOUT_WIDTH = "layout_width";     //$NON-NLS-1$
    protected static final String ATTR_LAYOUT_HEIGHT = "layout_height";   //$NON-NLS-1$
    protected static final String ATTR_LAYOUT_WEIGHT = "layout_weight";   //$NON-NLS-1$
    protected static final String ATTR_PADDING = "padding";               //$NON-NLS-1$
    protected static final String ATTR_PADDING_BOTTOM = "paddingBottom";  //$NON-NLS-1$
    protected static final String ATTR_PADDING_TOP = "paddingTop";        //$NON-NLS-1$
    protected static final String ATTR_PADDING_RIGHT = "paddingRight";    //$NON-NLS-1$
    protected static final String ATTR_PADDING_LEFT = "paddingLeft";      //$NON-NLS-1$
    protected static final String ATTR_FOREGROUND = "foreground";         //$NON-NLS-1$
    protected static final String ATTR_BACKGROUND = "background";         //$NON-NLS-1$
    protected static final String ATTR_ORIENTATION = "orientation";       //$NON-NLS-1$
    protected static final String ATTR_LAYOUT = "layout";                 //$NON-NLS-1$
    protected static final String ATTR_LAYOUT_ROW = "layout_row";         //$NON-NLS-1$
    protected static final String ATTR_LAYOUT_ROW_SPAN = "layout_rowSpan";//$NON-NLS-1$
    protected static final String ATTR_LAYOUT_COLUMN = "layout_column";   //$NON-NLS-1$
    protected static final String ATTR_ROW_COUNT = "rowCount";            //$NON-NLS-1$
    protected static final String ATTR_COLUMN_COUNT = "columnCount";      //$NON-NLS-1$
    protected static final String ATTR_LAYOUT_COLUMN_SPAN = "layout_columnSpan"; //$NON-NLS-1$
    protected static final String ATTR_CONTENT_DESCRIPTION = "contentDescription"; //$NON-NLS-1$


    // Attribute values
    protected static final String VALUE_FILL_PARENT = "fill_parent";       //$NON-NLS-1$
    protected static final String VALUE_MATCH_PARENT = "match_parent";     //$NON-NLS-1$
    protected static final String VALUE_VERTICAL = "vertical";             //$NON-NLS-1$
    protected static final String VALUE_LAYOUT_PREFIX = "@layout/";        //$NON-NLS-1$

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    private static boolean isFillParent(Element element, String dimension) {
        String width = element.getAttributeNS(ANDROID_URI, dimension);
        return width.equals(VALUE_MATCH_PARENT) || width.equals(VALUE_FILL_PARENT);
    }

    protected static boolean isWidthFillParent(Element element) {
        return isFillParent(element, ATTR_LAYOUT_WIDTH);
    }

    protected static boolean isHeightFillParent(Element element) {
        return isFillParent(element, ATTR_LAYOUT_HEIGHT);
    }

    protected boolean hasPadding(Element root) {
        return root.hasAttributeNS(ANDROID_URI, ATTR_PADDING)
                || root.hasAttributeNS(ANDROID_URI, ATTR_PADDING_LEFT)
                || root.hasAttributeNS(ANDROID_URI, ATTR_PADDING_RIGHT)
                || root.hasAttributeNS(ANDROID_URI, ATTR_PADDING_TOP)
                || root.hasAttributeNS(ANDROID_URI, ATTR_PADDING_BOTTOM);
    }
}
