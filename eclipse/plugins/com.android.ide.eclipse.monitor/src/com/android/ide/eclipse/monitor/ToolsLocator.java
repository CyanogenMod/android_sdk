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

package com.android.ide.eclipse.monitor;

import com.android.ide.eclipse.ddms.IToolsLocator;
import com.android.sdklib.SdkConstants;

public class ToolsLocator implements IToolsLocator {
    public static final String PLATFORM_EXECUTABLE_EXTENSION =
            (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS) ?
                    ".exe" : ""; //$NON-NLS-1$

    public static final String PLATFORM_SCRIPT_EXTENSION =
            (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS) ?
                    ".bat" : ""; //$NON-NLS-1$

    public static final String FN_HPROF_CONV = "hprof-conv" + PLATFORM_EXECUTABLE_EXTENSION; //$NON-NLS-1$
    public static final String FN_TRACEVIEW = "traceview" + PLATFORM_SCRIPT_EXTENSION; //$NON-NLS-1$

    public String getAdbLocation() {
        return getSdkPlatformToolsFolder() + SdkConstants.FN_ADB;
    }

    public String getTraceViewLocation() {
        return getSdkToolsFolder() + FN_TRACEVIEW;
    }

    public String getHprofConvLocation() {
        return getSdkToolsFolder() + FN_HPROF_CONV;
    }

    private String getSdkToolsFolder() {
        return getSdkFolder() + "/tools/"; //$NON-NLS-1$
    }

    private String getSdkPlatformToolsFolder() {
        return getSdkFolder() + "/platform-tools/"; //$NON-NLS-1$
    }

    private String getSdkFolder() {
        // FIXME!
        return MonitorPlugin.getDdmsPreferenceStore().getLastSdkPath();
    }
}
