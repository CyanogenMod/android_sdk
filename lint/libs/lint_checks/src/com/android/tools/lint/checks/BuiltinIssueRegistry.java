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

import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.detector.api.Issue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Registry which provides a list of checks to be performed on an Android project */
public class BuiltinIssueRegistry extends IssueRegistry {
    private static final List<Issue> sIssues;

    static {
        List<Issue> issues = new ArrayList<Issue>();

        issues.add(AccessibilityDetector.ISSUE);
        issues.add(DuplicateIdDetector.CROSS_LAYOUT);
        issues.add(DuplicateIdDetector.WITHIN_LAYOUT);
        issues.add(StateListDetector.ISSUE);
        issues.add(InefficientWeightDetector.ISSUE);
        issues.add(ScrollViewChildDetector.ISSUE);
        issues.add(MergeRootFrameLayoutDetector.ISSUE);
        issues.add(NestedScrollingWidgetDetector.ISSUE);
        issues.add(ChildCountDetector.SCROLLVIEW_ISSUE);
        issues.add(ChildCountDetector.ADAPTERVIEW_ISSUE);
        issues.add(UseCompoundDrawableDetector.ISSUE);
        issues.add(UselessViewDetector.USELESS_PARENT);
        issues.add(UselessViewDetector.USELESS_LEAF);
        issues.add(TooManyViewsDetector.TOO_MANY);
        issues.add(TooManyViewsDetector.TOO_DEEP);
        issues.add(GridLayoutDetector.ISSUE);
        issues.add(TranslationDetector.EXTRA);
        issues.add(TranslationDetector.MISSING);
        issues.add(HardcodedValuesDetector.ISSUE);
        issues.add(ProguardDetector.ISSUE);
        issues.add(PxUsageDetector.ISSUE);
        issues.add(TextFieldDetector.ISSUE);
        issues.add(UnusedResourceDetector.ISSUE);
        issues.add(UnusedResourceDetector.ISSUE_IDS);
        issues.add(ArraySizeDetector.INCONSISTENT);
        issues.add(ManifestOrderDetector.ISSUE);
        issues.add(ExportedServiceDetector.ISSUE);
        issues.add(IconDetector.GIF_USAGE);
        issues.add(IconDetector.ICON_DENSITIES);
        issues.add(IconDetector.ICON_MISSING_FOLDER);
        issues.add(IconDetector.ICON_DIP_SIZE);
        issues.add(IconDetector.ICON_EXPECTED_SIZE);
        issues.add(IconDetector.ICON_LOCATION);
        issues.add(IconDetector.DUPLICATES_NAMES);
        issues.add(IconDetector.DUPLICATES_CONFIGURATIONS);
        issues.add(IconDetector.ICON_NODPI);
        issues.add(DetectMissingPrefix.MISSING_NAMESPACE);

        // TODO: Populate dynamically somehow?

        sIssues = Collections.unmodifiableList(issues);

        // Check that ids are unique
        boolean assertionsEnabled = false;
        assert assertionsEnabled = true; // Intentional side-effect
        if (assertionsEnabled) {
            Set<String> ids = new HashSet<String>();
            for (Issue issue : sIssues) {
                String id = issue.getId();
                assert !ids.contains(id) : "Duplicate id " + id; //$NON-NLS-1$
                ids.add(id);
            }
        }
    }

    /**
     * Constructs a new {@link BuiltinIssueRegistry}
     */
    public BuiltinIssueRegistry() {
    }

    @Override
    public List<Issue> getIssues() {
        return sIssues;
    }
}
