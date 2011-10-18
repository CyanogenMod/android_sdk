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

package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Checks whether the current node can be removed without affecting the layout.
 */
public class UselessViewDetector extends LayoutDetector {
    /** Issue of including a parent that has no value on its own */
    public static final Issue USELESS_PARENT = Issue.create(
            "UselessParent", //$NON-NLS-1$
            "Checks whether a parent layout can be removed.",
            "A layout with children that has no siblings, is not a scrollview or " +
            "a root layout, and does not have a background, can be removed and have " +
            "its children moved directly into the parent for a flatter and more " +
            "efficient layout hierarchy.",
            CATEGORY_LAYOUT, 2, Severity.WARNING);

    /** Issue of including a leaf that isn't shown */
    public static final Issue USELESS_LEAF = Issue.create(
            "UselessLeaf", //$NON-NLS-1$
            "Checks whether a leaf layout can be removed.",
            "A layout that has no children or no background can often be removed (since it " +
            "is invisible) for a flatter and more efficient layout hierarchy.",
            CATEGORY_LAYOUT, 2, Severity.WARNING);

    /** Constructs a new {@link UselessViewDetector} */
    public UselessViewDetector() {
    }

    @Override
    public Issue[] getIssues() {
        return new Issue[] { USELESS_PARENT, USELESS_LEAF };
    }

    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }

    @Override
    public Scope getScope() {
        return Scope.SINGLE_FILE;
    }

    private static final List<String> CONTAINERS = new ArrayList<String>(20);
    static {
        CONTAINERS.add("android.gesture.GestureOverlayView"); //$NON-NLS-1$
        CONTAINERS.add("AbsoluteLayout");                     //$NON-NLS-1$
        CONTAINERS.add(FRAME_LAYOUT);
        CONTAINERS.add("GridLayout");                         //$NON-NLS-1$
        CONTAINERS.add(GRID_VIEW);
        CONTAINERS.add(HORIZONTAL_SCROLL_VIEW);
        CONTAINERS.add("ImageSwitcher");                      //$NON-NLS-1$
        CONTAINERS.add(LINEAR_LAYOUT);
        CONTAINERS.add("RadioGroup");                         //$NON-NLS-1$
        CONTAINERS.add("RelativeLayout");                     //$NON-NLS-1$
        CONTAINERS.add(SCROLL_VIEW);
        CONTAINERS.add("SlidingDrawer");                      //$NON-NLS-1$
        CONTAINERS.add("StackView");                          //$NON-NLS-1$
        CONTAINERS.add("TabHost");                            //$NON-NLS-1$
        CONTAINERS.add("TableLayout");                        //$NON-NLS-1$
        CONTAINERS.add("TableRow");                           //$NON-NLS-1$
        CONTAINERS.add("TextSwitcher");                       //$NON-NLS-1$
        CONTAINERS.add("ViewAnimator");                       //$NON-NLS-1$
        CONTAINERS.add("ViewFlipper");                        //$NON-NLS-1$
        CONTAINERS.add("ViewSwitcher");                       //$NON-NLS-1$
        // Available ViewGroups that are not included by this check:
        //  CONTAINERS.add("AdapterViewFlipper");
        //  CONTAINERS.add("DialerFilter");
        //  CONTAINERS.add("ExpandableListView");
        //  CONTAINERS.add("ListView");
        //  CONTAINERS.add("MediaController");
        //  CONTAINERS.add("merge");
        //  CONTAINERS.add("SearchView");
        //  CONTAINERS.add("TabWidget");
    }
    @Override
    public Collection<String> getApplicableElements() {
        return CONTAINERS;
    }

    @Override
    public void visitElement(Context context, Element element) {
        int childCount = getChildCount(element);
        if (childCount == 0) {
            // Check to see if this is a leaf layout that can be removed
            checkUselessLeaf(context, element);
        } else {
            // Check to see if this is a middle-man layout which can be removed
            checkUselessMiddleLayout(context, element);
        }
    }

    // This is the old UselessLayoutCheck from layoutopt
    private void checkUselessMiddleLayout(Context context, Element element) {
        // Conditions:
        // - The node has children
        // - The node does not have siblings
        // - The node's parent is not a scroll view (horizontal or vertical)
        // - The node does not have a background or its parent does not have a
        //   background or neither the node and its parent have a background
        // - The parent is not a <merge/>

        Node parentNode = element.getParentNode();
        if (parentNode.getNodeType() != Node.ELEMENT_NODE) {
            // Can't remove root
            return;
        }

        Element parent = (Element) parentNode;
        String parentTag = parent.getTagName();
        if (parentTag.equals(SCROLL_VIEW) || parentTag.equals(HORIZONTAL_SCROLL_VIEW) ||
                parentTag.equals(MERGE)) {
            // Can't remove if the parent is a scroll view or a merge
            return;
        }

        // This method is only called when we've already ensured that it has children
        assert getChildCount(element) > 0;

        int parentChildCount = getChildCount(parent);
        if (parentChildCount != 1) {
            // Don't remove if the node has siblings
            return;
        }

        boolean nodeHasBackground = element.hasAttributeNS(ANDROID_URI, ATTR_BACKGROUND);
        boolean parentHasBackground = parent.hasAttributeNS(ANDROID_URI, ATTR_BACKGROUND);
        // TODO: The logic on this has background stuff is a bit unclear to me; this is
        // a literal translation of the Groovy code in layoutopt
        // TODO: Get clarification on what the criteria are.
        if (nodeHasBackground || parentHasBackground ||
                (!nodeHasBackground && !parentHasBackground)) {
            boolean hasId = element.hasAttributeNS(ANDROID_URI, ATTR_ID);
            Location location = context.getLocation(element);
            String tag = element.getTagName();
            String format;
            if (hasId) {
                format = "This %1$s layout or its %2$s parent is possibly useless";
            } else {
                format = "This %1$s layout or its %2$s parent is useless";
            }
            String message = String.format(format, tag, parentTag);
            context.toolContext.report(USELESS_PARENT, location, message);
        }
    }

    // This is the old UselessView check from layoutopt
    private void checkUselessLeaf(Context context, Element element) {
        assert getChildCount(element) == 0;

        // Conditions:
        // - The node is a container view (LinearLayout, etc.)
        // - The node has no id
        // - The node has no background
        // - The node has no children

        if (element.hasAttributeNS(ANDROID_URI, ATTR_ID)) {
            return;
        }

        if (element.hasAttributeNS(ANDROID_URI, ATTR_BACKGROUND)) {
            return;
        }

        Location location = context.getLocation(element);
        String tag = element.getTagName();
        String message = String.format(
                "This %1$s view is useless (no children, no background, no id)", tag);
        context.toolContext.report(USELESS_LEAF, location, message);
    }
}
