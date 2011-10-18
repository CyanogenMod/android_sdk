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

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Checks whether a layout_weight is declared inefficiently.
 */
public class InefficientWeightDetector extends LayoutDetector {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "InefficientWeight", //$NON-NLS-1$
            "Looks for inefficient weight declarations in LinearLayouts",
            "When only a single widget in a LinearLayout defines a weight, it is more " +
            "efficient to assign a width/height of 0dp to it since it will absorb all " +
            "the remaining space anyway. With a declared width/height of 0dp it " +
            "does not have to measure its own size first.",
            CATEGORY_LAYOUT, 5, Severity.WARNING);

    /** Constructs a new {@link InefficientWeightDetector} */
    public InefficientWeightDetector() {
    }

    @Override
    public Issue[] getIssues() {
        return new Issue[] { ISSUE };
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
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(Context context, Element element) {
        List<Element> children = getChildren(element);
        // See if there is exactly one child with a weight
        Element weightChild = null;
        for (Element child : children) {
            if (child.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)) {
                if (weightChild != null) {
                    // More than one child defining a weight!
                    return;
                } else {
                    weightChild = child;
                }
            }
        }

        if (weightChild != null) {
            String dimension;
            if (VALUE_VERTICAL.equals(element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION))) {
                dimension = ATTR_LAYOUT_HEIGHT;
            } else {
                dimension = ATTR_LAYOUT_WIDTH;
            }
            Attr sizeNode = weightChild.getAttributeNodeNS(ANDROID_URI, dimension);
            String size = sizeNode != null ? sizeNode.getValue() : "(undefined)";
            if (!size.startsWith("0")) { //$NON-NLS-1$
                String msg = String.format(
                        "Use a %1$s of 0dip instead of %2$s for better performance",
                        dimension, size);
                context.toolContext.report(ISSUE,
                        context.getLocation(sizeNode != null ? sizeNode : weightChild),
                        msg);

            }
        }
    }
}
