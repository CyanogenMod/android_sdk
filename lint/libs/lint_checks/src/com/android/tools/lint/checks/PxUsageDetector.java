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

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;

import java.util.Collection;

/**
 * Check which looks at the children of ScrollViews and ensures that they fill/match
 * the parent width instead of setting wrap_content.
 */
public class PxUsageDetector extends LayoutDetector {
    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "PxUsage", //$NON-NLS-1$
            "Looks for use of the \"px\" dimension",
            // This description is from the below screen support document
            "For performance reasons and to keep the code simpler, the Android system uses pixels " +
            "as the standard unit for expressing dimension or coordinate values. That means that " +
            "the dimensions of a view are always expressed in the code using pixels, but " +
            "always based on the current screen density. For instance, if myView.getWidth() " +
            "returns 10, the view is 10 pixels wide on the current screen, but on a device with " +
            "a higher density screen, the value returned might be 15. If you use pixel values " +
            "in your application code to work with bitmaps that are not pre-scaled for the " +
            "current screen density, you might need to scale the pixel values that you use in " +
            "your code to match the un-scaled bitmap source.",
            Category.CORRECTNESS,
            2,
            Severity.WARNING,
            PxUsageDetector.class,
            Scope.RESOURCE_FILE_SCOPE).setMoreInfo(
            "http://developer.android.com/guide/practices/screens_support.html#screen-independence"); //$NON-NLS-1$

    /** Constructs a new {@link PxUsageDetector} */
    public PxUsageDetector() {
    }

    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return ALL;
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value.endsWith("px") && value.matches("\\d+px")) { //$NON-NLS-1$
            if (value.charAt(0) == '0') {
                // 0px is fine. 0px is 0dp regardless of density...
                return;
            }
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    "Avoid using \"px\" as units; use \"dp\" instead", null);
        }
    }
}
