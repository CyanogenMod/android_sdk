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

package com.android.ide.eclipse.ndk.internal;

import com.android.ide.eclipse.adt.AdtPlugin;

import org.eclipse.cdt.core.CommandLauncher;
import org.eclipse.cdt.core.ICommandLauncher;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("restriction")
public class NdkHelper {
    private static final String MAKE = "make";
    private static final String CORE_MAKEFILE_PATH = "/build/core/build-local.mk";

    /**
     * Obtain the ABI's the application is compatible with.
     * The ABI's are obtained by reading the result of the following command:
     * make --no-print-dir -f ${NdkRoot}/build/core/build-local.mk -C <project-root> DUMP_APP_ABI
     */
    public static List<NativeAbi> getApplicationAbis(IProject project, IProgressMonitor monitor) {
        ICommandLauncher launcher = new CommandLauncher();
        launcher.setProject(project);
        String[] args = new String[] {
            "--no-print-dir",
            "-f",
            NdkManager.getNdkLocation() + CORE_MAKEFILE_PATH,
            "-C",
            project.getLocation().toOSString(),
            "DUMP_APP_ABI",
        };
        try {
            launcher.execute(getPathToMake(), args, null, project.getLocation(), monitor);
        } catch (CoreException e) {
            AdtPlugin.printErrorToConsole(e.getLocalizedMessage());
            return Collections.emptyList();
        }

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        launcher.waitAndRead(stdout, stderr, monitor);

        String abis = stdout.toString().trim();
        List<NativeAbi> nativeAbis = new ArrayList<NativeAbi>(3);

        for (String abi: abis.split(" ")) {
            try {
                nativeAbis.add(NativeAbi.valueOf(abi));
            } catch (IllegalArgumentException e) {

            }
        }

        return nativeAbis;
    }

    /**
     * Obtain the toolchain prefix to use for given project and abi.
     * The prefix is obtained by reading the result of:
     * make --no-print-dir -f ${NdkRoot}/build/core/build-local.mk \
     *      -C <project-root> \
     *      DUMP_TOOLCHAIN_PREFIX APP_ABI=abi
     */
    public static String getToolchainPrefix(IProject project, NativeAbi abi,
            IProgressMonitor monitor) {
        ICommandLauncher launcher = new CommandLauncher();
        launcher.setProject(project);
        String[] args = new String[] {
            "--no-print-dir",
            "-f",
            NdkManager.getNdkLocation() + CORE_MAKEFILE_PATH,
            "-C",
            project.getLocation().toOSString(),
            "DUMP_TOOLCHAIN_PREFIX",
        };
        try {
            launcher.execute(getPathToMake(), args, null, project.getLocation(), monitor);
        } catch (CoreException e) {
            AdtPlugin.printErrorToConsole(e.getLocalizedMessage());
            return null;
        }

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        launcher.waitAndRead(stdout, stderr, monitor);
        return stdout.toString().trim();
    }

    private static IPath getPathToMake() {
        return getUtilitiesFolder().append(MAKE);
    }

    /**
     * Obtain a path to the utilities prebult folder in NDK. This is typically
     * "${NdkRoot}/prebuilt/<platform>/"
     */
    private static synchronized IPath getUtilitiesFolder() {
        IPath ndkRoot = new Path(NdkManager.getNdkLocation());
        IPath prebuilt = ndkRoot.append("prebuilt"); //$NON-NLS-1$
        if (!prebuilt.toFile().exists() || !prebuilt.toFile().canRead()) {
            return ndkRoot;
        }

        File[] platforms = prebuilt.toFile().listFiles();
        if (platforms.length == 1) {
            return prebuilt.append(platforms[0].getName()).append("bin"); //$NON-NLS-1$
        }

        return ndkRoot;
    }
}
