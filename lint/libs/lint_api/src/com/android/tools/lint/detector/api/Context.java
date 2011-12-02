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

import com.android.tools.lint.client.api.Configuration;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.client.api.SdkInfo;

import java.io.File;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Context passed to the detectors during an analysis run. It provides
 * information about the file being analyzed, it allows shared properties (so
 * the detectors can share results), etc.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
public class Context {
    /**
     * The file being checked. Note that this may not always be to a concrete
     * file. For example, in the {@link Detector#beforeCheckProject(Context)}
     * method, the context file is the directory of the project.
     */
    public final File file;

    /** The client requesting a lint check */
    private final LintClient mClient;

    /** The project containing the file being checked */
    private final Project mProject;

    /** The current configuration controlling which checks are enabled etc */
    private final Configuration mConfiguration;

    /** The contents of the file */
    private String mContents;

    /** The scope of the current lint check */
    private final EnumSet<Scope> mScope;

    /** The SDK info, if any */
    private SdkInfo mSdkInfo;

    /**
     * Whether the lint job has been canceled.
     * <p>
     * Slow-running detectors should check this flag via
     * {@link AtomicBoolean#get()} and abort if canceled
     */
    public final AtomicBoolean canceled = new AtomicBoolean();

    /** Map of properties to share results between detectors */
    private Map<String, Object> mProperties;

    /**
     * Construct a new {@link Context}
     *
     * @param client the client requesting a lint check
     * @param project the project containing the file being checked
     * @param file the file being checked
     * @param scope the scope for the lint job
     */
    public Context(LintClient client, Project project, File file,
            EnumSet<Scope> scope) {
        this.file = file;

        mClient = client;
        mProject = project;
        mScope = scope;
        mConfiguration = project.getConfiguration();
    }

    /**
     * Returns the scope for the lint job
     *
     * @return the scope, never null
     */
    public EnumSet<Scope> getScope() {
        return mScope;
    }

    /**
     * Returns the configuration for this project.
     *
     * @return the configuration, never null
     */
    public Configuration getConfiguration() {
        return mConfiguration;
    }

    /**
     * Returns the project containing the file being checked
     *
     * @return the project, never null
     */
    public Project getProject() {
        return mProject;
    }

    /**
     * Returns the lint client requesting the lint check
     *
     * @return the client, never null
     */
    public LintClient getClient() {
        return mClient;
    }

    /**
     * Returns the contents of the file. This may not be the contents of the
     * file on disk, since it delegates to the {@link LintClient}, which in turn
     * may decide to return the current edited contents of the file open in an
     * editor.
     *
     * @return the contents of the given file, or null if an error occurs.
     */
    public String getContents() {
        if (mContents == null) {
            mContents = mClient.readFile(file);
        }

        return mContents;
    }

    /**
     * Returns the value of the given named property, or null.
     *
     * @param name the name of the property
     * @return the corresponding value, or null
     */
    public Object getProperty(String name) {
        if (mProperties == null) {
            return null;
        }

        return mProperties.get(name);
    }

    /**
     * Sets the value of the given named property.
     *
     * @param name the name of the property
     * @param value the corresponding value
     */
    public void setProperty(String name, Object value) {
        if (mProperties == null) {
            mProperties = new HashMap<String, Object>();
        }

        mProperties.put(name, value);
    }

    /**
     * Gets the SDK info for the current project.
     *
     * @return the SDK info for the current project, never null
     */
    public SdkInfo getSdkInfo() {
        if (mSdkInfo == null) {
            mSdkInfo = mClient.getSdkInfo(mProject);
        }

        return mSdkInfo;
    }

    // ---- Convenience wrappers  ---- (makes the detector code a bit leaner)

    /**
     * Returns false if the given issue has been disabled. Convenience wrapper
     * around {@link Configuration#getSeverity(Issue)}.
     *
     * @param issue the issue to check
     * @return false if the issue has been disabled
     */
    public boolean isEnabled(Issue issue) {
        return mConfiguration.isEnabled(issue);
    }

    /**
     * Reports an issue. Convenience wrapper around {@link LintClient#report}
     *
     * @param issue the issue to report
     * @param location the location of the issue, or null if not known
     * @param message the message for this warning
     * @param data any associated data, or null
     */
    public void report(Issue issue, Location location, String message, Object data) {
        mClient.report(this, issue, location, message, data);
    }

    /**
     * Send an exception to the log. Convenience wrapper around {@link LintClient#log}.
     *
     * @param exception the exception, possibly null
     * @param format the error message using {@link String#format} syntax
     * @param args any arguments for the format string
     */
    public void log(Throwable exception, String format, Object... args) {
        mClient.log(exception, format, args);
    }

}
