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

import static com.android.tools.lint.detector.api.LintConstants.ANDROID_MANIFEST_XML;
import static com.android.tools.lint.detector.api.LintConstants.TAG_APPLICATION;
import static com.android.tools.lint.detector.api.LintConstants.TAG_USES_PERMISSION;
import static com.android.tools.lint.detector.api.LintConstants.TAG_USES_SDK;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;

import org.w3c.dom.Element;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;

/**
 * Checks for issues in AndroidManifest files such as declaring elements in the
 * wrong order.
 */
public class ManifestOrderDetector extends Detector.XmlDetectorAdapter {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "ManifestOrder", //$NON-NLS-1$
            "Checks for manifest problems like <uses-sdk> after the <application> tag",
            "The <application> tag should appear after the elements which declare " +
            "which version you need, which features you need, which libraries you " +
            "need, and so on. In the past there have been subtle bugs (such as " +
            "themes not getting applied correctly) when the <application> tag appears " +
            "before some of these other elements, so it's best to order your" +
            "manifest in the logical dependency order.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            ManifestOrderDetector.class,
            EnumSet.of(Scope.MANIFEST));

    /** Constructs a new accessibility check */
    public ManifestOrderDetector() {
    }

    private boolean mSeenApplication;

    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }

    @Override
    public boolean appliesTo(Context context, File file) {
        return file.getName().equals(ANDROID_MANIFEST_XML);
    }

    @Override
    public void beforeCheckFile(Context context) {
        mSeenApplication = false;
    }

    // ---- Implements Detector.XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(new String[] {
                TAG_APPLICATION,
                TAG_USES_PERMISSION,
                "permission",              //$NON-NLS-1$
                "permission-tree",         //$NON-NLS-1$
                "permission-group",        //$NON-NLS-1$
                TAG_USES_SDK,
                "uses-configuration",      //$NON-NLS-1$
                "uses-feature",            //$NON-NLS-1$
                "supports-screens",        //$NON-NLS-1$
                "compatible-screens",      //$NON-NLS-1$
                "supports-gl-texture",     //$NON-NLS-1$
        });
    }

    @Override
    public void visitElement(Context context, Element element) {
        String tag = element.getTagName();
        if (tag.equals(TAG_APPLICATION)) {
            mSeenApplication = true;
        } else if (mSeenApplication) {
            context.client.report(context, ISSUE, context.getLocation(element),
                    String.format("<%1$s> tag appears after <application> tag", tag), null);

            // Don't complain for *every* element following the <application> tag
            mSeenApplication = false;
        }
    }
}
