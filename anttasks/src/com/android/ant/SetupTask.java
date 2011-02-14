/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.ImportTask;

/**
 * Legacy setupTask class used by older build system.
 *
 * If this is used it actually only display an error about the need to update the build file.
 */
public final class SetupTask extends Task {

    /**
     * @param b unused.
     *
     * @deprecated only present because the original {@link SetupTask} extends {@link ImportTask}.
     */
    @Deprecated
    public void setImport(boolean b) {
        // do nothing
    }

    @Override
    public void execute() throws BuildException {
        throw new BuildException("\n\nError. You are using an obsolete build.xml\n" +
                "You need to delete it and regenerate it using\n" +
                "\tandroid update project\n");
    }
}
