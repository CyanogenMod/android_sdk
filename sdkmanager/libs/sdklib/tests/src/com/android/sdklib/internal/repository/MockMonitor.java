/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.sdklib.internal.repository;

import com.android.util.Pair;

/**
 * Mock implementation of {@link ITaskMonitor} that simply captures
 * the output in local strings. Does not provide any UI and has no
 * support for creating sub-monitors.
 */
public class MockMonitor implements ITaskMonitor {

    String mCapturedLog = "";                                           //$NON-NLS-1$
    String mCapturedErrorLog = "";                                      //$NON-NLS-1$
    String mCapturedVerboseLog = "";                                    //$NON-NLS-1$
    String mCapturedDescriptions = "";                                  //$NON-NLS-1$

    public String getCapturedLog() {
        return mCapturedLog;
    }

    public String getCapturedErrorLog() {
        return mCapturedErrorLog;
    }

    public String getCapturedVerboseLog() {
        return mCapturedVerboseLog;
    }

    public String getCapturedDescriptions() {
        return mCapturedDescriptions;
    }

    public void log(String format, Object... args) {
        mCapturedLog += String.format(format, args) + "\n";             //$NON-NLS-1$
    }

    public void logError(String format, Object... args) {
        mCapturedErrorLog += String.format(format, args) + "\n";        //$NON-NLS-1$
    }

    public void logVerbose(String format, Object... args) {
        mCapturedVerboseLog += String.format(format, args) + "\n";      //$NON-NLS-1$
    }

    public void setProgressMax(int max) {
    }

    public int getProgressMax() {
        return 0;
    }

    public void setDescription(String format, Object... args) {
        mCapturedDescriptions += String.format(format, args) + "\n";    //$NON-NLS-1$
    }

    public boolean isCancelRequested() {
        return false;
    }

    public void incProgress(int delta) {
    }

    public int getProgress() {
        return 0;
    }

    public boolean displayPrompt(String title, String message) {
        return false;
    }

    public ITaskMonitor createSubMonitor(int tickCount) {
        return null;
    }

    public void error(Throwable t, String errorFormat, Object... args) {
    }

    public void printf(String msgFormat, Object... args) {
    }

    public void warning(String warningFormat, Object... args) {
    }

    public Pair<String, String> displayLoginPasswordPrompt(String title, String message) {
        return null;
    }
}
