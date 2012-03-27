/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.ide.eclipse.ndk.internal.launch;

import com.android.ide.eclipse.ndk.internal.NdkVariables;

import org.eclipse.cdt.debug.core.ICDTLaunchConfigurationConstants;
import org.eclipse.cdt.dsf.gdb.IGDBLaunchConfigurationConstants;
import org.eclipse.cdt.dsf.gdb.internal.ui.launching.RemoteApplicationCDebuggerTab;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("restriction")
public class NdkDebuggerConfigTab extends RemoteApplicationCDebuggerTab {
    public static final String DEFAULT_GDB_PORT = "5039";
    public static final String DEFAULT_GDB = getVar(NdkVariables.NDK_GDB);
    public static final String DEFAULT_PROGRAM =
            String.format("%1$s/obj/local/%2$s/app_process",
                    getVar(NdkVariables.NDK_PROJECT),
                    getVar(NdkVariables.NDK_COMPAT_ABI));
    public static final String DEFAULT_SOLIB_PATH =
            getVar(NdkVariables.NDK_PROJECT) + "/obj/local/armeabi/";


    @Override
    public void setDefaults(ILaunchConfigurationWorkingCopy config) {
        super.setDefaults(config);

        config.setAttribute(IGDBLaunchConfigurationConstants.ATTR_DEBUG_NAME, DEFAULT_GDB);
        config.setAttribute(IGDBLaunchConfigurationConstants.ATTR_GDB_INIT, ""); //$NON-NLS-1$
        config.setAttribute(IGDBLaunchConfigurationConstants.ATTR_PORT, DEFAULT_GDB_PORT);
        config.setAttribute(ICDTLaunchConfigurationConstants.ATTR_DEBUGGER_STOP_AT_MAIN, false);

        config.setAttribute(ICDTLaunchConfigurationConstants.ATTR_DEBUGGER_START_MODE,
                IGDBLaunchConfigurationConstants.DEBUGGER_MODE_REMOTE_ATTACH);
        config.setAttribute(ICDTLaunchConfigurationConstants.ATTR_PROGRAM_NAME,
                DEFAULT_PROGRAM);

        List<String> solibPaths = new ArrayList<String>(2);
        solibPaths.add(DEFAULT_SOLIB_PATH);
        config.setAttribute(IGDBLaunchConfigurationConstants.ATTR_DEBUGGER_SOLIB_PATH, solibPaths);
    }

    private static String getVar(String varName) {
        return "${" + varName + "}";
    }
}
