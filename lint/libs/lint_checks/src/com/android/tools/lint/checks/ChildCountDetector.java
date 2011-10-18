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
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;

import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

/**
 * Check which makes sure that views have the expected number of declared
 * children (e.g. at most one in ScrollViews and none in AdapterViews)
 */
public class ChildCountDetector extends LayoutDetector {

    /** The main issue discovered by this detector */
    public static final Issue SCROLLVIEW_ISSUE = Issue.create(
            "ScrollViewCount", //$NON-NLS-1$
            "Checks that ScrollViews have exactly one child widget",
            "ScrollViews can only have one child widget. If you want more children, wrap them " +
            "in a container layout.",
            CATEGORY_LAYOUT, 8, Severity.WARNING);

    /** The main issue discovered by this detector */
    public static final Issue ADAPTERVIEW_ISSUE = Issue.create(
            "AdapterViewChildren", //$NON-NLS-1$
            "Checks that AdapterViews do not define their children in XML",
            "AdapterViews such as ListViews must be configured with data from Java code, " +
            "such as a ListAdapter.",
            CATEGORY_LAYOUT, 8, Severity.WARNING).setMoreInfo(
                "http://developer.android.com/reference/android/widget/AdapterView.html"); //$NON-NLS-1$

    /** Constructs a new {@link ChildCountDetector} */
    public ChildCountDetector() {
    }

    @Override
    public Issue[] getIssues() {
        return new Issue[] { SCROLLVIEW_ISSUE, ADAPTERVIEW_ISSUE };
    }

    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }

    @Override
    public Scope getScope() {
        return Scope.SINGLE_FILE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(new String[] {
                SCROLL_VIEW,
                HORIZONTAL_SCROLL_VIEW,
                LIST_VIEW,
                GRID_VIEW
                // TODO: Shouldn't Spinner be in this list too? (Was not there in layoutopt)
        });
    }

    @Override
    public void visitElement(Context context, Element element) {
        int childCount = getChildCount(element);
        String tagName = element.getTagName();
        if (tagName.equals(SCROLL_VIEW) || tagName.equals(HORIZONTAL_SCROLL_VIEW)) {
            if (childCount > 1) {
                context.toolContext.report(SCROLLVIEW_ISSUE, context.getLocation(element),
                        "A scroll view can have only one child");
            }
        } else {
            // Adapter view
            if (childCount > 0) {
                context.toolContext.report(ADAPTERVIEW_ISSUE, context.getLocation(element),
                        "A list/grid should have no children declared in XML");
            }
        }
    }
}
