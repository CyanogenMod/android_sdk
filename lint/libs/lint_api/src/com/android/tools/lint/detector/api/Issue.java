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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.Configuration;
import com.google.common.annotations.Beta;

import java.util.EnumSet;


/**
 * An issue is a potential bug in an Android application. An issue is discovered
 * by a {@link Detector}, and has an associated {@link Severity}.
 * <p>
 * Issues and detectors are separate classes because a detector can discover
 * multiple different issues as it's analyzing code, and we want to be able to
 * different severities for different issues, the ability to suppress one but
 * not other issues from the same detector, and so on.
 * <p/>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
@Beta
public final class Issue implements Comparable<Issue> {
    private final String mId;
    private final String mDescription;
    private final String mExplanation;
    private final Category mCategory;
    private final int mPriority;
    private final Severity mSeverity;
    private String mMoreInfoUrl;
    private boolean mEnabledByDefault = true;
    private final EnumSet<Scope> mScope;
    private final Class<? extends Detector> mClass;

    // Use factory methods
    private Issue(
            @NonNull String id,
            @NonNull String description,
            @NonNull String explanation,
            @NonNull Category category,
            int priority,
            @NonNull Severity severity,
            @NonNull Class<? extends Detector> detectorClass,
            @NonNull EnumSet<Scope> scope) {
        super();
        mId = id;
        mDescription = description;
        mExplanation = explanation;
        mCategory = category;
        mPriority = priority;
        mSeverity = severity;
        mClass = detectorClass;
        mScope = scope;
    }

    /**
     * Creates a new issue
     *
     * @param id the fixed id of the issue
     * @param description the quick summary of the issue (one line)
     * @param explanation a full explanation of the issue, with suggestions for
     *            how to fix it
     * @param category the associated category, if any
     * @param priority the priority, a number from 1 to 10 with 10 being most
     *            important/severe
     * @param severity the default severity of the issue
     * @param detectorClass the class of the detector to find this issue
     * @param scope the scope of files required to analyze this issue
     * @return a new {@link Issue}
     */
    @NonNull
    public static Issue create(
            @NonNull String id,
            @NonNull String description,
            @NonNull String explanation,
            @NonNull Category category,
            int priority,
            @NonNull Severity severity,
            @NonNull Class<? extends Detector> detectorClass,
            @NonNull EnumSet<Scope> scope) {
        return new Issue(id, description, explanation, category, priority, severity,
                detectorClass, scope);
    }

    /**
     * Returns the unique id of this issue. These should not change over time
     * since they are used to persist the names of issues suppressed by the user
     * etc. It is typically a single camel-cased word.
     *
     * @return the associated fixed id, never null and always unique
     */
    @NonNull
    public String getId() {
        return mId;
    }

    /**
     * Briefly (one line) describes the kinds of checks performed by this rule
     *
     * @return a quick summary of the issue, never null
     */
    @NonNull
    public String getDescription() {
        return mDescription;
    }

    /**
     * Describes the error found by this rule, e.g.
     * "Buttons must define contentDescriptions". Preferably the explanation
     * should also contain a description of how the problem should be solved.
     * Additional info can be provided via {@link #getMoreInfo()}.
     *
     * @return an explanation of the issue, never null.
     */
    @NonNull
    public String getExplanation() {
        return mExplanation;
    }

    /**
     * The primary category of the issue
     *
     * @return the primary category of the issue, never null
     */
    @NonNull
    public Category getCategory() {
        return mCategory;
    }

    /**
     * Returns a priority, in the range 1-10, with 10 being the most severe and
     * 1 the least
     *
     * @return a priority from 1 to 10
     */
    public int getPriority() {
        return mPriority;
    }

    /**
     * Returns the default severity of the issues found by this detector (some
     * tools may allow the user to specify custom severities for detectors).
     * <p>
     * Note that even though the normal way for an issue to be disabled is for
     * the {@link Configuration} to return {@link Severity#IGNORE}, there is a
     * {@link #isEnabledByDefault()} method which can be used to turn off issues
     * by default. This is done rather than just having the severity as the only
     * attribute on the issue such that an issue can be configured with an
     * appropriate severity (such as {@link Severity#ERROR}) even when issues
     * are disabled by default for example because they are experimental or not
     * yet stable.
     *
     * @return the severity of the issues found by this detector
     */
    @NonNull
    public Severity getDefaultSeverity() {
        return mSeverity;
    }

    /**
     * Returns a link (a URL string) to more information, or null
     *
     * @return a link to more information, or null
     */
    @Nullable
    public String getMoreInfo() {
        return mMoreInfoUrl;
    }

    /**
     * Returns whether this issue should be enabled by default, unless the user
     * has explicitly disabled it.
     *
     * @return true if this issue should be enabled by default
     */
    public boolean isEnabledByDefault() {
        return mEnabledByDefault;
    }

    /**
     * Returns the scope required to analyze the code to detect this issue.
     * This is determined by the detectors which reports the issue.
     *
     * @return the required scope
     */
    @NonNull
    public EnumSet<Scope> getScope() {
        return mScope;
    }

    /**
     * Sorts the detectors alphabetically by id. This is intended to make it
     * convenient to store settings for detectors in a fixed order. It is not
     * intended as the order to be shown to the user; for that, a tool embedding
     * lint might consider the priorities, categories, severities etc of the
     * various detectors.
     *
     * @param other the {@link Issue} to compare this issue to
     */
    @Override
    public int compareTo(@NonNull Issue other) {
        return getId().compareTo(other.getId());
    }

    /**
     * Sets a more info URL string
     *
     * @param moreInfoUrl url string
     * @return this, for constructor chaining
     */
    @NonNull
    public Issue setMoreInfo(@NonNull String moreInfoUrl) {
        mMoreInfoUrl = moreInfoUrl;
        return this;
    }

    /**
     * Sets whether this issue is enabled by default.
     *
     * @param enabledByDefault whether the issue should be enabled by default
     * @return this, for constructor chaining
     */
    @NonNull
    public Issue setEnabledByDefault(boolean enabledByDefault) {
        mEnabledByDefault = enabledByDefault;
        return this;
    }

    /**
     * Returns the class of the detector to use to find this issue
     *
     * @return the class of the detector to use to find this issue
     */
    @NonNull
    public Class<? extends Detector> getDetectorClass() {
        return mClass;
    }

    @Override
    public String toString() {
        return mId;
    }
}
