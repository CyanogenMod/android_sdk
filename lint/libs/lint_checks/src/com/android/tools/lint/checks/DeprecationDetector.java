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

import static com.android.tools.lint.detector.api.LintConstants.ABSOLUTE_LAYOUT;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

/**
 * Check which looks for usage of deprecated tags, attributes, etc.
 */
public class DeprecationDetector extends LayoutDetector {
    /** Usage of deprecated views or attributes */
    public static final Issue ISSUE = Issue.create(
            "Deprecated", //$NON-NLS-1$
            "Looks for usages of deprecated layouts, attributes, and so on.",
            "Deprecated views, attributes and so on are deprecated because there " +
            "is a better way to do something. Do it that new way. You've been warned.",
            Category.CORRECTNESS,
            2,
            Severity.WARNING,
            DeprecationDetector.class,
            Scope.RESOURCE_FILE_SCOPE);

    /** Constructs a new {@link DeprecationDetector} */
    public DeprecationDetector() {
    }

    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(new String[] {
                ABSOLUTE_LAYOUT
        });
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        context.report(ISSUE, context.getLocation(element),
                String.format("%1$s is deprecated", element.getTagName()), null);
    }
}
