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
package com.android.ddmuilib.logcat;

import com.android.ddmlib.Log;

import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;


/**
 * A JFace {@link ViewerFilter} for the {@link LogCatPanel} displaying logcat messages.
 * This can filter logcat messages based on various properties of the message.
 */
public final class LogCatViewerFilter extends ViewerFilter {
    private LogCatFilterSettings mFilterSettings;

    private boolean mCheckPID;
    private boolean mCheckTag;
    private Pattern mTagPattern;

    /**
     * Construct a {@link ViewerFilter} filtering logcat messages based on
     * user provided filter settings for PID, Tag and log level.
     * @param filterSettings settings to use for the filter. Invalid regexes will
     * be ignored, so they should be checked for validity before constructing this object.
     */
    public LogCatViewerFilter(LogCatFilterSettings filterSettings) {
        mFilterSettings = filterSettings;

        mCheckPID = mFilterSettings.getPidString().trim().length() != 0;
        mCheckTag = mFilterSettings.getTag().trim().length() != 0;

        if (mCheckTag) {
            try {
                mTagPattern = Pattern.compile(mFilterSettings.getTag());
            } catch (PatternSyntaxException e) {
                Log.e("LogCatFilter", "Ignoring invalid regex.");
                Log.e("LogCatFilter", e);
                mCheckTag = false;
            }
        }
    }

    @Override
    public boolean select(Viewer viewer, Object parent, Object element) {
        if (!(element instanceof LogCatMessage)) {
            return false;
        }

        LogCatMessage m = (LogCatMessage) element;

        /* filter out messages of a lower priority */
        if (m.getLogLevel().getPriority() < mFilterSettings.getLogLevel().getPriority()) {
            return false;
        }

        /* if pid filter is enabled, filter out messages whose pid does not match
         * the filter's pid */
        if (mCheckPID && !m.getPidString().equals(mFilterSettings.getPidString())) {
            return false;
        }

        /* if tag filter is enabled, filter out messages not matching the tag */
        if (mCheckTag) {
            Matcher matcher = mTagPattern.matcher(m.getTag());
            if (!matcher.matches()) {
                return false;
            }
        }

        return true;
    }
}
