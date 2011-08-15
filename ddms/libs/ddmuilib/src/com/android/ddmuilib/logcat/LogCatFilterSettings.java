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

import com.android.ddmlib.Log.LogLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings for a filter for logcat messages.
 */
public final class LogCatFilterSettings {
    private static final String PID_KEYWORD = "pid:";   //$NON-NLS-1$
    private static final String TAG_KEYWORD = "tag:";   //$NON-NLS-1$
    private static final String TEXT_KEYWORD = "text:"; //$NON-NLS-1$

    private final String mName;
    private final String mTag;
    private String mText;
    private final String mPid;
    private final LogLevel mLogLevel;

    public LogCatFilterSettings(String name, String tag, String text, String pid,
            LogLevel logLevel) {
        mName = name;
        mTag = tag;
        mText = text;
        mPid = pid;
        mLogLevel = logLevel;
    }

    /**
     * Construct a list of {@link LogCatFilterSettings} objects by decoding the query.
     * @param query encoded search string. The query is simply a list of words (can be regexes)
     * a user would type in a search bar. These words are searched for in the text field of
     * each collected logcat message. To search in a different field, the word could be prefixed
     * with a keyword corresponding to the field name. Currently, the following keywords are
     * supported: "pid:", "tag:" and "text:". Invalid regexes are ignored.
     * @param minLevel minimum log level to match
     * @return list of filter settings that fully match the given query
     */
    public static List<LogCatFilterSettings> fromString(String query, LogLevel minLevel) {
        List<LogCatFilterSettings> filterSettings = new ArrayList<LogCatFilterSettings>();

        for (String s : query.trim().split(" ")) {
            String tag = "";
            String text = "";
            String pid = "";

            if (s.startsWith(PID_KEYWORD)) {
                pid = s.substring(PID_KEYWORD.length());
            } else if (s.startsWith(TAG_KEYWORD)) {
                tag = s.substring(TAG_KEYWORD.length());
            } else {
                if (s.startsWith(TEXT_KEYWORD)) {
                    text = s.substring(TEXT_KEYWORD.length());
                } else {
                    text = s;
                }
            }
            filterSettings.add(new LogCatFilterSettings("livefilter-" + s,
                    tag, text, pid, minLevel));
        }

        return filterSettings;
    }

    public String getName() {
        return mName;
    }

    public String getTag() {
        return mTag;
    }

    public String getText() {
        return mText;
    }

    public String getPidString() {
        return mPid;
    }

    public LogLevel getLogLevel() {
        return mLogLevel;
    }
}
