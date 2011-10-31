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

package com.android.tools.lint.api;

import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Registry which provides a list of checks to be performed on an Android project */
public abstract class DetectorRegistry {
    private static List<Issue> sIssues;
    private static List<String> sCategories;
    private static Map<String, Issue> sIdToIssue;

    /**
     * Returns the list of detectors to be run.
     *
     * @return the list of checks to be performed (including those that may be
     *         disabled!)
     */
    public abstract List<? extends Detector> getDetectors();

    /**
     * Returns true if the given id represents a valid issue id
     *
     * @param id the id to be checked
     * @return true if the given id is valid
     */
    public boolean isIssueId(String id) {
        return getIssue(id) != null;
    }

    /**
     * Returns true if the given category is a valid category
     *
     * @param category the category to be checked
     * @return true if the given string is a valid category
     */
    public boolean isCategory(String category) {
        for (String c : getCategories()) {
            if (c.equals(category)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns the available categories
     *
     * @return an iterator for all the categories, never null
     */
    public List<String> getCategories() {
        if (sCategories == null) {
            // Compute the categories from the available issues. The order of categories
            // will be determined by finding the maximum severity and maximum priority of
            // the issues in each category and then sorting in descending order, using
            // alphabetical order if the others are the same.
            final Map<String, Integer> maxPriority = new HashMap<String, Integer>();
            final Map<String, Severity> maxSeverity = new HashMap<String, Severity>();
            for (Issue issue : getIssues()) {
                String category = issue.getCategory();
                Integer priority = maxPriority.get(category);
                if (priority == null || priority.intValue() < issue.getPriority()) {
                    maxPriority.put(category, issue.getPriority());
                }
                Severity severity = maxSeverity.get(category);
                if (severity == null || severity.compareTo(issue.getDefaultSeverity()) < 0) {
                    maxSeverity.put(category, issue.getDefaultSeverity());
                }
            }
            List<String> categories = new ArrayList<String>(maxPriority.keySet());
            Collections.sort(categories, new Comparator<String>() {
                public int compare(String category1, String category2) {
                    Severity severity1 = maxSeverity.get(category1);
                    Severity severity2 = maxSeverity.get(category2);
                    if (severity1 != severity2) {
                        return severity2.compareTo(severity1);
                    }

                    Integer priority1 = maxPriority.get(category1);
                    Integer priority2 = maxPriority.get(category2);
                    int compare = priority2.compareTo(priority1);
                    if (compare != 0) {
                        return compare;
                    }

                    return category1.compareTo(category2);
                }
            });

            sCategories = Collections.unmodifiableList(categories);
        }

        return sCategories;
    }

    /**
     * Returns the issue for the given id, or null if it's not a valid id
     *
     * @param id the id to be checked
     * @return the corresponding issue, or null
     */
    public Issue getIssue(String id) {
        getIssues(); // Ensure initialized
        return sIdToIssue.get(id);
    }

    /**
     * Returns the list of issues that can be found by all known detectors.
     *
     * @return the list of issues to be checked (including those that may be
     *         disabled!)
     */
    @SuppressWarnings("all") // Turn off warnings for the intentional assertion side effect below
    public List<Issue> getIssues() {
        if (sIssues == null) {
            sIdToIssue = new HashMap<String, Issue>();

            List<Issue> issues = new ArrayList<Issue>();
            for (Detector detector : getDetectors()) {
                for (Issue issue : detector.getIssues()) {
                    issues.add(issue);
                    sIdToIssue.put(issue.getId(), issue);
                }
            }

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

        return sIssues;
    }
}
