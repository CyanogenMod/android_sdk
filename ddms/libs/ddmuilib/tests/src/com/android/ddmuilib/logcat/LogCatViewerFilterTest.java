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

import junit.framework.TestCase;

public class LogCatViewerFilterTest extends TestCase {
    public void testFilterByLogLevel() {
        LogCatFilterSettings settings = new LogCatFilterSettings("",  //$NON-NLS-1$
                "", "", LogLevel.DEBUG);                              //$NON-NLS-1$ //$NON-NLS-2$
        LogCatViewerFilter filter = new LogCatViewerFilter(settings);

        /* filter message below filter's log level */
        LogCatMessage msg = new LogCatMessage(LogLevel.VERBOSE,
                "", "", "", "");  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        assertEquals(false, filter.select(null, null, msg));

        /* do not filter message above filter's log level */
        msg = new LogCatMessage(LogLevel.ERROR,
                "", "", "", "");  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        assertEquals(true, filter.select(null, null, msg));
    }

    public void testFilterByPid() {
        LogCatFilterSettings settings = new LogCatFilterSettings("",  //$NON-NLS-1$
                "", "123", LogLevel.VERBOSE);                         //$NON-NLS-1$ //$NON-NLS-2$
        LogCatViewerFilter filter = new LogCatViewerFilter(settings);

        /* show message with pid matching filter */
        LogCatMessage msg = new LogCatMessage(LogLevel.VERBOSE,
                "123", "", "", "");  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        assertEquals(true, filter.select(null, null, msg));

        /* don't show message with pid not matching filter */
        msg = new LogCatMessage(LogLevel.VERBOSE,
                "12", "", "", "");  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        assertEquals(false, filter.select(null, null, msg));
    }

    public void testFilterByTagRegex() {
        LogCatFilterSettings settings = new LogCatFilterSettings("",  //$NON-NLS-1$
                "tag.*", "", LogLevel.VERBOSE);                       //$NON-NLS-1$ //$NON-NLS-2$
        LogCatViewerFilter filter = new LogCatViewerFilter(settings);

        /* show message with tag matching filter */
        LogCatMessage msg = new LogCatMessage(LogLevel.VERBOSE,
                "", "tag123", "", "");  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        assertEquals(true, filter.select(null, null, msg));

        msg = new LogCatMessage(LogLevel.VERBOSE,
                "", "ta123", "", "");  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        assertEquals(false, filter.select(null, null, msg));
    }
}
