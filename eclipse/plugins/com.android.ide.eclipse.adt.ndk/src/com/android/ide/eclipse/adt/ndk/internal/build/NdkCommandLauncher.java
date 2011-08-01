/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.ide.eclipse.adt.ndk.internal.build;

import com.android.ide.eclipse.adt.ndk.internal.NdkManager;

import org.eclipse.cdt.core.CommandLauncher;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;

public class NdkCommandLauncher extends CommandLauncher {

    @Override
    public Process execute(IPath commandPath, String[] args, String[] env, IPath changeToDirectory,
            IProgressMonitor monitor)
            throws CoreException {
        if (!commandPath.isAbsolute())
            commandPath = new Path(NdkManager.getNdkLocation()).append(commandPath);

        if (Platform.getOS().equals(Platform.OS_WIN32)) {
            String[] newargs = new String[args.length + 1];
            newargs[0] = commandPath.toOSString();
            System.arraycopy(args, 0, newargs, 1, args.length);
            commandPath = new Path("sh"); //$NON-NLS-1$
            args = newargs;
        }

        return super.execute(commandPath, args, env, changeToDirectory, monitor);
    }

}
