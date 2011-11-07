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

import static com.android.tools.lint.detector.api.LintConstants.ANDROID_URI;
import static com.android.tools.lint.detector.api.LintConstants.ATTR_BACKGROUND;
import static com.android.tools.lint.detector.api.LintConstants.ATTR_FOREGROUND;
import static com.android.tools.lint.detector.api.LintConstants.ATTR_LAYOUT_GRAVITY;
import static com.android.tools.lint.detector.api.LintConstants.FRAME_LAYOUT;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Checks whether a root FrameLayout can be replaced with a {@code <merge>} tag.
 */
public class MergeRootFrameLayoutDetector extends LayoutDetector {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "MergeRootFrame", //$NON-NLS-1$
            "Checks whether a root <FrameLayout> can be replaced with a <merge> tag",
            "If a <FrameLayout> is the root of a layout and does not provide background " +
            "or padding etc, it can be replaced with a <merge> tag which is slightly " +
            "more efficient.",
            Category.PERFORMANCE,
            4,
            Severity.WARNING,
            MergeRootFrameLayoutDetector.class,
            Scope.RESOURCE_FILE_SCOPE);

    /** Constructs a new {@link MergeRootFrameLayoutDetector} */
    public MergeRootFrameLayoutDetector() {
    }

    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }

    @Override
    public void visitDocument(Context context, Document document) {
        Element root = document.getDocumentElement();
        if (root.getTagName().equals(FRAME_LAYOUT) &&
            ((isWidthFillParent(root) && isHeightFillParent(root)) ||
                    !root.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_GRAVITY))
                && !root.hasAttributeNS(ANDROID_URI, ATTR_BACKGROUND)
                && !root.hasAttributeNS(ANDROID_URI, ATTR_FOREGROUND)
                && !hasPadding(root)) {
            context.client.report(context, ISSUE, context.getLocation(root),
                    "This <FrameLayout> can be replaced with a <merge> tag", null);
        }
    }
}
