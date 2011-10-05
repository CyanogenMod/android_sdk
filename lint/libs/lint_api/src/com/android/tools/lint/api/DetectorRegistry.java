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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Registry which provides a list of checks to be performed on an Android project */
public abstract class DetectorRegistry {
    private static List<Issue> sIssues;

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
     * Returns the issue for the given id, or null if it's not a valid id
     *
     * @param id the id to be checked
     * @return the corresponding issue, or null
     */
    public Issue getIssue(String id) {
        for (Issue issue : getIssues()) {
            if (issue.getId().equals(id)) {
                return issue;
            }
        }

        return null;
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
            List<Issue> issues = new ArrayList<Issue>();
            for (Detector detector : getDetectors()) {
                for (Issue issue : detector.getIssues()) {
                    issues.add(issue);
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
