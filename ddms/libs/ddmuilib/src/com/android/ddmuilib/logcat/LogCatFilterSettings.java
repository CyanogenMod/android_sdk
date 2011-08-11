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

/**
 * Settings for a filter for logcat messages.
 */
public final class LogCatFilterSettings {
    private final String mName;
    private final String mTag;
    private final String mPid;
    private final LogLevel mLogLevel;

    public LogCatFilterSettings(String name, String tag, String pid, LogLevel logLevel) {
        mName = name;
        mTag = tag;
        mPid = pid;
        mLogLevel = logLevel;
    }

    public String getName() {
        return mName;
    }

    public String getTag() {
        return mTag;
    }

    public String getPidString() {
        return mPid;
    }

    public LogLevel getLogLevel() {
        return mLogLevel;
    }
}
