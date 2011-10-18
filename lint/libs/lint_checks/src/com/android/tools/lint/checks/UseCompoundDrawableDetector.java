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
import java.util.List;

/**
 * Checks whether the current node can be replaced by a TextView using compound
 * drawables.
 */
public class UseCompoundDrawableDetector extends LayoutDetector {
    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "UseCompoundDrawables", //$NON-NLS-1$
            "Checks whether the current node can be replaced by a TextView using compound drawables.",
            // TODO: OFFER MORE HELP!
            "A LinearLayout which contains an ImageView and a TextView can be more efficiently " +
            "handled as a compound drawable",
            CATEGORY_PERFORMANCE, 6, Severity.WARNING);

    /** Constructs a new {@link UseCompoundDrawableDetector} */
    public UseCompoundDrawableDetector() {
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
        return Arrays.asList(new String[] {
                LINEAR_LAYOUT
        });
    }

    @Override
    public void visitElement(Context context, Element element) {
        int childCount = getChildCount(element);
        if (childCount == 2) {
            List<Element> children = getChildren(element);
            Element first = children.get(0);
            Element second = children.get(1);
            if ((first.getTagName().equals(IMAGE_VIEW) &&
                    second.getTagName().equals(TEXT_VIEW) &&
                    !first.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)) ||
                ((second.getTagName().equals(IMAGE_VIEW) &&
                        first.getTagName().equals(TEXT_VIEW) &&
                        !second.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)))) {
                context.toolContext.report(ISSUE, context.getLocation(element),
                        "This tag and its children can be replaced by one <TextView/> and " +
                                "a compound drawable");
            }
        }
    }
}
