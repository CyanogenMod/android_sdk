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

package com.android.ide.eclipse.adt.internal.actions;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.build.DexWrapper;
import com.android.ide.eclipse.adt.internal.sdk.AdtConsoleSdkLog;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.io.FileOp;
import com.android.sdklib.util.GrabProcessOutput;
import com.android.sdklib.util.GrabProcessOutput.IProcessOutput;
import com.android.sdklib.util.GrabProcessOutput.Wait;
import com.android.sdkuilib.repository.ISdkChangeListener;
import com.android.sdkuilib.repository.SdkUpdaterWindow;
import com.android.sdkuilib.repository.SdkUpdaterWindow.SdkInvocationContext;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;

import java.io.File;

/**
 * Delegate for the toolbar/menu action "Android SDK Manager".
 * It displays the Android SDK Manager.
 */
public class SdkManagerAction implements IWorkbenchWindowActionDelegate, IObjectActionDelegate {

    @Override
    public void dispose() {
        // nothing to dispose.
    }

    @Override
    public void init(IWorkbenchWindow window) {
        // no init
    }

    @Override
    public void run(IAction action) {
        if (!openAdtSdkManager()) {
            AdtPlugin.displayError(
                    "Android SDK",
                    "Location of the Android SDK has not been setup in the preferences.");
        }
    }

    /**
     * Opens the SDK Manager as an external application.
     * This call is asynchronous, it doesn't wait for the manager to be closed.
     *
     * @return True if the application was found and executed. False if it could not
     *   be located or could not be launched.
     */
    public static boolean openExternalSdkManager() {
        final Sdk sdk = Sdk.getCurrent();
        if (sdk == null) {
            return false;
        }

        File androidBat = FileOp.append(
                sdk.getSdkLocation(),
                SdkConstants.FD_TOOLS,
                SdkConstants.androidCmdName());

        if (!androidBat.exists()) {
            return false;
        }

        try {
            final AdtConsoleSdkLog logger = new AdtConsoleSdkLog();

            String command[] = new String[] {
                    androidBat.getAbsolutePath(),
                    "sdk"   //$NON-NLS-1$
            };
            Process process = Runtime.getRuntime().exec(command);
            GrabProcessOutput.grabProcessOutput(
                    process,
                    Wait.ASYNC,
                    new IProcessOutput() {
                        @Override
                        public void out(String line) {
                            // Ignore stdout
                        }

                        @Override
                        public void err(String line) {
                            if (line != null) {
                                logger.printf("[SDK Manager] %s", line);
                            }
                        }
                    });
        } catch (Exception ignore) {
        }

        return true;
    }

    /**
     * Opens the SDK Manager bundled within ADT.
     * The call is blocking and does not return till the SD Manager window is closed.
     *
     * @return True if the SDK location is known and the SDK Manager was started.
     *   False if the SDK location is not set and we can't open a SDK Manager to
     *   manage files in an unknown location.
     */
    public static boolean openAdtSdkManager() {
        final Sdk sdk = Sdk.getCurrent();
        if (sdk == null) {
            return false;
        }

        // Runs the updater window, directing only warning/errors logs to the ADT console
        // (normal log is just dropped, which is fine since the SDK Manager has its own
        // log window now.)

        SdkUpdaterWindow window = new SdkUpdaterWindow(
                AdtPlugin.getDisplay().getActiveShell(),
                new AdtConsoleSdkLog() {
                    @Override
                    public void printf(String msgFormat, Object... args) {
                        // Do not show non-error/warning log in Eclipse.
                    };
                },
                sdk.getSdkLocation(),
                SdkInvocationContext.IDE);

        ISdkChangeListener listener = new ISdkChangeListener() {
            @Override
            public void onSdkLoaded() {
                // Ignore initial load of the SDK.
            }

            /**
             * Unload all we can from the SDK before new packages are installed.
             * Typically we need to get rid of references to dx from platform-tools
             * and to any platform resource data.
             * <p/>
             * {@inheritDoc}
             */
            @Override
            public void preInstallHook() {

                // TODO we need to unload as much of as SDK as possible. Otherwise
                // on Windows we end up with Eclipse locking some files and we can't
                // replace them.
                //
                // At this point, we know what the user wants to install so it would be
                // possible to pass in flags to know what needs to be unloaded. Typically
                // we need to:
                // - unload dex if platform-tools is going to be updated. There's a vague
                //   attempt below at removing any references to dex and GCing. Seems
                //   to do the trick.
                // - unload any target that is going to be updated since it may have
                //   resource data used by a current layout editor (e.g. data/*.ttf
                //   and various data/res/*.xml).
                //
                // Most important we need to make sure there isn't a build going on
                // and if there is one, either abort it or wait for it to complete and
                // then we want to make sure we don't get any attempt to use the SDK
                // before the postInstallHook is called.

                if (sdk != null) {
                    sdk.unloadTargetData(true /*preventReload*/);

                    DexWrapper dx = sdk.getDexWrapper();
                    dx.unload();
                }
            }

            /**
             * Nothing to do. We'll reparse the SDK later in onSdkReload.
             * <p/>
             * {@inheritDoc}
             */
            @Override
            public void postInstallHook() {
            }

            /**
             * Reparse the SDK in case anything was add/removed.
             * <p/>
             * {@inheritDoc}
             */
            @Override
            public void onSdkReload() {
                AdtPlugin.getDefault().reparseSdk();
            }
        };

        window.addListener(listener);
        window.open();

        return true;
    }

    @Override
    public void selectionChanged(IAction action, ISelection selection) {
        // nothing related to the current selection.
    }

    @Override
    public void setActivePart(IAction action, IWorkbenchPart targetPart) {
        // nothing to do.
    }
}
