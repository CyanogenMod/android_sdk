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
 * Check which looks for accessibility problems like missing content descriptions
 * <p>
 * TODO: Resolve styles and don't warn where styles are defining the content description
 * (though this seems unusual; content descriptions are not typically generic enough to
 * put in styles)
 */
public class AccessibilityDetector extends LayoutDetector {
    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "ContentDescription", //$NON-NLS-1$
            "Ensures that image widgets provide a contentDescription",
            "Non-textual widgets like ImageViews and ImageButtons should use the " +
            "contentDescription attribute to specify a textual description of " +
            "the widget such that screen readers and other accessibility tools " +
            "can adequately describe the user interface.",
            CATEGORY_A11Y, 5, Severity.WARNING);

    /** Constructs a new accessibility check */
    public AccessibilityDetector() {
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
                "ImageButton", //$NON-NLS-1$
                "ImageView"    //$NON-NLS-1$
        });
    }

    @Override
    public void visitElement(Context context, Element element) {
        if (!element.hasAttributeNS(ANDROID_URI, ATTR_CONTENT_DESCRIPTION)) {
            context.toolContext.report(ISSUE, context.getLocation(context),
                    "[Accessibility] Missing contentDescription attribute on image");
        } else {
            String attribute = element.getAttributeNS(ANDROID_URI, ATTR_CONTENT_DESCRIPTION);
            if (attribute.length() == 0 || attribute.equals("TODO")) { //$NON-NLS-1$
                context.toolContext.report(ISSUE, context.getLocation(context),
                        "[Accessibility] Empty contentDescription attribute on image");
            }
        }
    }
}
