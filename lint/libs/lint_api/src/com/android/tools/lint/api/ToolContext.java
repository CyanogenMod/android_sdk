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

import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;

/**
 * Information about the tool embedding the lint analyzer. IDEs and other tools
 * implementing lint support will extend this to integrate logging, displaying errors,
 * etc.
 * <p/>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
public abstract class ToolContext {
    /**
     * Report the given issue.
     *
     * @param context the context used by the detector when the issue was found
     * @param issue the issue that was found
     * @param location the location of the issue
     * @param message the associated user message
     * @param data optional extra data for a discovered issue, or null. The
     *            content depends on the specific issue. Detectors can pass
     *            extra info here which automatic fix tools etc can use to
     *            extract relevant information instead of relying on parsing the
     *            error message text. See each detector for details on which
     *            data if any is supplied for a given issue.
     */
    public abstract void report(Context context, Issue issue, Location location, String message,
            Object data);

    /**
     * Checks whether this issue should be ignored because the user has already
     * suppressed the error? Note that this refers to individual issues being
     * suppressed/ignored, not a whole detector being disabled via something
     * like {@link #isEnabled(Issue)}.
     *
     * @param context the context used by the detector when the issue was found
     * @param issue the issue that was found
     * @param location the location of the issue
     * @param message the associated user message
     * @param severity the severity of the issue
     * @param data additional information about an issue (see
     *            {@link #report(Context, Issue, Location, String, Object)} for
     *            more information
     * @return true if this issue should be suppressed
     */
    public boolean isSuppressed(Context context, Issue issue, Location location,
            String message, Severity severity, Object data) {
        return false;
    }

    /**
     * Send an exception to the log
     *
     * @param exception the exception, possibly null
     * @param format the error message using {@link String#format} syntax
     * @param args any arguments for the format string
     */
    public abstract void log(Throwable exception, String format, Object... args);

    /**
     * Returns a {@link IDomParser} to use to parse XML
     *
     * @return a new {@link IDomParser}
     */
    public abstract IDomParser getParser();

    /**
     * Returns false if the given issue has been disabled
     *
     * @param issue the issue to check
     * @return false if the issue has been disabled
     */
    public boolean isEnabled(Issue issue) {
        return true;
    }

    /**
     * Returns the severity for a given issue. This is the same as the
     * {@link Issue#getDefaultSeverity()} unless the user has selected a custom
     * severity (which is tool context dependent).
     *
     * @param issue the issue to look up the severity from
     * @return the severity use for issues for the given detector
     */
    public Severity getSeverity(Issue issue) {
        return issue.getDefaultSeverity();
    }

    /**
     * Reads the given text file and returns the content as a string
     *
     * @param file the file to read
     * @return the string to return, never null (will be empty if there is an
     *         I/O error)
     */
    public abstract String readFile(File file);
}
