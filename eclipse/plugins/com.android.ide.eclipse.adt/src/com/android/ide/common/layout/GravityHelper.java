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
package com.android.ide.common.layout;

import static com.android.ide.common.layout.LayoutConstants.ANDROID_URI;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_GRAVITY;
import static com.android.ide.common.layout.LayoutConstants.GRAVITY_VALUE_BOTTOM;
import static com.android.ide.common.layout.LayoutConstants.GRAVITY_VALUE_CENTER;
import static com.android.ide.common.layout.LayoutConstants.GRAVITY_VALUE_CENTER_HORIZONTAL;
import static com.android.ide.common.layout.LayoutConstants.GRAVITY_VALUE_CENTER_VERTICAL;
import static com.android.ide.common.layout.LayoutConstants.GRAVITY_VALUE_FILL;
import static com.android.ide.common.layout.LayoutConstants.GRAVITY_VALUE_FILL_HORIZONTAL;
import static com.android.ide.common.layout.LayoutConstants.GRAVITY_VALUE_FILL_VERTICAL;
import static com.android.ide.common.layout.LayoutConstants.GRAVITY_VALUE_LEFT;
import static com.android.ide.common.layout.LayoutConstants.GRAVITY_VALUE_RIGHT;
import static com.android.ide.common.layout.LayoutConstants.GRAVITY_VALUE_TOP;

import org.w3c.dom.Element;

/** Helper class for looking up the gravity masks of gravity attributes */
public class GravityHelper {
    public static final int GRAVITY_LEFT         = 1 << 0;
    public static final int GRAVITY_RIGHT        = 1 << 1;
    public static final int GRAVITY_CENTER_HORIZ = 1 << 2;
    public static final int GRAVITY_FILL_HORIZ   = 1 << 3;
    public static final int GRAVITY_CENTER_VERT  = 1 << 4;
    public static final int GRAVITY_FILL_VERT    = 1 << 5;
    public static final int GRAVITY_TOP          = 1 << 6;
    public static final int GRAVITY_BOTTOM       = 1 << 7;
    public static final int GRAVITY_HORIZ_MASK = GRAVITY_CENTER_HORIZ | GRAVITY_FILL_HORIZ
            | GRAVITY_LEFT | GRAVITY_RIGHT;
    public static final int GRAVITY_VERT_MASK = GRAVITY_CENTER_VERT | GRAVITY_FILL_VERT
            | GRAVITY_TOP | GRAVITY_BOTTOM;

    /**
     * Returns the gravity of the given element
     *
     * @param element the element to look up the gravity for
     * @return a bit mask corresponding to the selected gravities
     */
    public static int getGravity(Element element) {
        String gravityString = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_GRAVITY);
        return getGravity(gravityString, GRAVITY_LEFT | GRAVITY_TOP);
    }

    /**
     * Returns the gravity bitmask for the given gravity string description
     *
     * @param gravityString the gravity string description
     * @param defaultMask the default/initial bitmask to start with
     * @return a bitmask corresponding to the gravity description
     */
    public static int getGravity(String gravityString, int defaultMask) {
        int gravity = defaultMask;
        if (gravityString != null && gravityString.length() > 0) {
            String[] anchors = gravityString.split("\\|"); //$NON-NLS-1$
            for (String anchor : anchors) {
                if (GRAVITY_VALUE_CENTER.equals(anchor)) {
                    gravity = GRAVITY_CENTER_HORIZ | GRAVITY_CENTER_VERT;
                } else if (GRAVITY_VALUE_FILL.equals(anchor)) {
                    gravity = GRAVITY_FILL_HORIZ | GRAVITY_FILL_VERT;
                } else if (GRAVITY_VALUE_CENTER_VERTICAL.equals(anchor)) {
                    gravity = (gravity & GRAVITY_HORIZ_MASK) | GRAVITY_CENTER_VERT;
                } else if (GRAVITY_VALUE_CENTER_HORIZONTAL.equals(anchor)) {
                    gravity = (gravity & GRAVITY_VERT_MASK) | GRAVITY_CENTER_HORIZ;
                } else if (GRAVITY_VALUE_FILL_VERTICAL.equals(anchor)) {
                    gravity = (gravity & GRAVITY_HORIZ_MASK) | GRAVITY_FILL_VERT;
                } else if (GRAVITY_VALUE_FILL_HORIZONTAL.equals(anchor)) {
                    gravity = (gravity & GRAVITY_VERT_MASK) | GRAVITY_FILL_HORIZ;
                } else if (GRAVITY_VALUE_TOP.equals(anchor)) {
                    gravity = (gravity & GRAVITY_HORIZ_MASK) | GRAVITY_TOP;
                } else if (GRAVITY_VALUE_BOTTOM.equals(anchor)) {
                    gravity = (gravity & GRAVITY_HORIZ_MASK) | GRAVITY_BOTTOM;
                } else if (GRAVITY_VALUE_LEFT.equals(anchor)) {
                    gravity = (gravity & GRAVITY_VERT_MASK) | GRAVITY_LEFT;
                } else if (GRAVITY_VALUE_RIGHT.equals(anchor)) {
                    gravity = (gravity & GRAVITY_VERT_MASK) | GRAVITY_RIGHT;
                } else {
                    // "clip" not supported
                }
            }
        }

        return gravity;
    }
}
