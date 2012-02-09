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

package com.android.sdklib.util;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class GrabProcessOutput {

    public interface IProcessOutput {
        /**
         * Processes an stdout message line.
         * @param line The stdout message line. Null when the reader reached the end of stdout.
         */
        public void out(@Nullable String line);
        /**
         * Processes an stderr message line.
         * @param line The stderr message line. Null when the reader reached the end of stderr.
         */
        public void err(@Nullable String line);
    }

    /**
     * Get the stderr/stdout outputs of a process and return when the process is done.
     * Both <b>must</b> be read or the process will block on windows.
     *
     * @param process The process to get the ouput from.
     * @param output Optional object to capture stdout/stderr.
     *      Note that on Windows capturing the output is not optional. If output is null
     *      the stdout/stderr will be captured and discarded.
     * @param waitForReaders True to wait for the reader threads to finish.
     * @return the process return code.
     * @throws InterruptedException if {@link Process#waitFor()} was interrupted.
     */
    public static int grabProcessOutput(
            @NonNull final Process process,
            boolean waitForReaders,
            @Nullable final IProcessOutput output) throws InterruptedException {
        // read the lines as they come. if null is returned, it's
        // because the process finished
        Thread threadErr = new Thread("stderr") {
            @Override
            public void run() {
                // create a buffer to read the stderr output
                InputStreamReader is = new InputStreamReader(process.getErrorStream());
                BufferedReader errReader = new BufferedReader(is);

                try {
                    while (true) {
                        String line = errReader.readLine();
                        if (output != null) {
                            output.err(line);
                        }
                        if (line == null) {
                            break;
                        }
                    }
                } catch (IOException e) {
                    // do nothing.
                }
            }
        };

        Thread threadOut = new Thread("stdout") {
            @Override
            public void run() {
                InputStreamReader is = new InputStreamReader(process.getInputStream());
                BufferedReader outReader = new BufferedReader(is);

                try {
                    while (true) {
                        String line = outReader.readLine();
                        if (output != null) {
                            output.out(line);
                        }
                        if (line == null) {
                            break;
                        }
                    }
                } catch (IOException e) {
                    // do nothing.
                }
            }
        };

        threadErr.start();
        threadOut.start();

        // it looks like on windows process#waitFor() can return
        // before the thread have filled the arrays, so we wait for both threads and the
        // process itself.
        if (waitForReaders) {
            try {
                threadErr.join();
            } catch (InterruptedException e) {
            }
            try {
                threadOut.join();
            } catch (InterruptedException e) {
            }
        }

        // get the return code from the process
        return process.waitFor();
    }
}
