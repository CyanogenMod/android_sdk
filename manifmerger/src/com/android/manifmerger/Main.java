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

package com.android.manifmerger;

import com.android.sdklib.ISdkLog;

import java.io.File;

/**
 * Command-line entry point of the Manifest Merger.
 * The goal of the manifest merger is to merge library manifest into a main application manifest.
 * See {@link ManifestMerger} for the exact merging rules.
 * <p/>
 * The command-line version creates a {@link ManifestMerger}
 * which takes file arguments from the command-line and dumps all errors and warnings on the
 * stdout/stderr console.
 * <p/>
 * Usage: <br/>
 * {@code $ manifmerger merge --main main_manifest.xml --libs lib1.xml lib2.xml --out result.xml}
 * <p/>
 * When used as a library, please call {@link ManifestMerger#process(File, File, File[])} directly.
 */
public class Main {

    /** Logger object. Use this to print normal output, warnings or errors. Never null. */
    private ISdkLog mSdkLog;
    /** Command line parser. Never null. */
    private ArgvParser mArgvParser;

    public static void main(String[] args) {
        new Main().run(args);
    }

    /**
     * Runs the sdk manager app
     */
    private void run(String[] args) {
        createLogger();

        mArgvParser = new ArgvParser(mSdkLog);
        mArgvParser.parseArgs(args);

        // Create a new ManifestMerger and call its process method.
        // It will take care of validating its own arguments.
        ManifestMerger mm = new ManifestMerger(mSdkLog);

        String[] libPaths = mArgvParser.getParamLibs();
        File[] libFiles = new File[libPaths.length];
        for (int n = libPaths.length - 1; n >= 0; n--) {
            libFiles[n] = new File(libPaths[n]);
        }

        boolean ok = mm.process(
                new File(mArgvParser.getParamOut()),
                new File(mArgvParser.getParamMain()),
                libFiles
                );
        System.exit(ok ? 0 : 1);
    }

    /**
     * Creates the {@link #mSdkLog} object.
     * This logger prints to the attached console.
     */
    private void createLogger() {
        mSdkLog = new ISdkLog() {
            @Override
            public void error(Throwable t, String errorFormat, Object... args) {
                if (errorFormat != null) {
                    System.err.printf("Error: " + errorFormat, args);
                    if (!errorFormat.endsWith("\n")) {
                        System.err.printf("\n");
                    }
                }
                if (t != null) {
                    System.err.printf("Error: %s\n", t.getMessage());
                }
            }

            @Override
            public void warning(String warningFormat, Object... args) {
                System.out.printf("Warning: " + warningFormat, args);
                if (!warningFormat.endsWith("\n")) {
                    System.out.printf("\n");
                }
            }

            @Override
            public void printf(String msgFormat, Object... args) {
                System.out.printf(msgFormat, args);
            }
        };
    }

    /** For testing */
    public void setLogger(ISdkLog logger) {
        mSdkLog = logger;
    }

}
