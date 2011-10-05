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

package com.android.tools.lint.detector.api;

import java.io.File;

/**
 * A detector is able to find a particular problem. It might also be thought of as enforcing
 * a rule, but "rule" is a bit overloaded in ADT terminology since ViewRules are used in
 * the Rules API to allow views to specify designtime behavior in the graphical layout editor.
 * <p>
 * Each detector provides information about the issues it can find, such as an explanation
 * of how to fix the issue, the priority, the category, etc. It also has an id which is
 * used to persistently identify a particular type of error.
 * <p/>
 * NOTE: Detectors might be constructed just once and shared between lint runs, so
 * any per-detector state should be initialized and reset via the before/after
 * methods.
 * <p/>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
public abstract class Detector /*implements Comparable<Detector>*/ {
    /**
     * Returns a list of issues detected by this detector.
     *
     * @return a list of issues detected by this detector, never null.
     */
    public abstract Issue[] getIssues();

//    /**
//     * Returns the id of this detector. These should not change over time since
//     * they are used to persist the names of disabled detectors etc. It is
//     * typically a single camel-cased word.
//     *
//     * @return the associated fixed id
//     */
//    public abstract String getId();

    /**
     * Runs the detector
     * @param context the context describing the work to be done
     */
    public abstract void run(Context context);


    public abstract boolean appliesTo(Context context, File file);

    /** Analysis is about to begin, perform any setup steps. */
    public void beforeCheckProject(Context context) {
    }

    /**
     * Analysis has just been finished for the whole project, perform any
     * cleanup or report issues found
     */
    public void afterCheckProject(Context context) {
    }

    /** Analysis is about to be performed on a specific file, perform any setup steps. */
    public void beforeCheckFile(Context context) {
    }

    /**
     * Analysis has just been finished for a specific file, perform any cleanup
     * or report issues found
     */
    public void afterCheckFile(Context context) {
    }

    /**
     * Returns the expected speed of this detector
     *
     * @return the expected speed of this detector
     */
    public abstract Speed getSpeed();

    /**
     * Returns the scope of this detector
     *
     * @return the scope of this detector
     */
    public abstract Scope getScope();

    protected static final String CATEGORY_CORRECTNESS = "Correctness";
    protected static final String CATEGORY_PERFORMANCE = "Performance";
    protected static final String CATEGORY_USABILITY = "Usability";
    protected static final String CATEGORY_I18N = "Internationalization";
    protected static final String CATEGORY_A11Y = "Accessibility";
    protected static final String CATEGORY_LAYOUT = "Layout";
}
