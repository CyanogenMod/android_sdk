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

package com.android.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.ExecTask;
import org.apache.tools.ant.types.Path;

import java.io.File;

public class ZipAlignTask extends Task {

    private String mExecutable;
    private String mInput;
    private String mOutput;
    private int mAlign = 4;
    private boolean mVerbose = false;

    /**
     * Sets the value of the "executable" attribute.
     * @param executable the value.
     */
    public void setExecutable(Path executable) {
        mExecutable = TaskHelper.checkSinglePath("executable", executable);
    }

    public void setInput(Path inputPath) {
        mInput = TaskHelper.checkSinglePath("input", inputPath);
    }

    public void setOutput(Path outputPath) {
        mOutput = TaskHelper.checkSinglePath("output", outputPath);
    }

    public void setAlign(int align) {
        mAlign = align;
    }

    public void setVerbose(boolean verbose) {
        mVerbose = verbose;
    }

    @Override
    public void execute() throws BuildException {
        if (mExecutable == null) {
            throw new BuildException("Missing attribute executable");
        }
        if (mInput == null) {
            throw new BuildException("Missing attribute input");
        }
        if (mOutput == null) {
            throw new BuildException("Missing attribute output");
        }

        // check if there's a need for the task to run.
        File outputFile = new File(mOutput);
        if (outputFile.isFile()) {
            File inputFile = new File(mInput);
            if (outputFile.lastModified() >= inputFile.lastModified()) {
                System.out.println("No changes. No need to run zip-align on the apk.");
                return;
            }
        }

        System.out.println("Running zip align on final apk...");
        doZipAlign();
    }

    private void doZipAlign() {
        ExecTask task = new ExecTask();
        task.setExecutable(mExecutable);
        task.setFailonerror(true);
        task.setProject(getProject());
        task.setOwningTarget(getOwningTarget());

        task.setTaskName("zip-align");

        // force overwrite of existing output file
        task.createArg().setValue("-f");

        // verbose flag
        if (mVerbose) {
            task.createArg().setValue("-v");
        }

        // align value
        task.createArg().setValue(Integer.toString(mAlign));

        // input
        task.createArg().setValue(mInput);

        // output
        task.createArg().setValue(mOutput);

        // execute
        task.execute();
    }
}
