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
import org.apache.tools.ant.taskdefs.ExecTask;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.resources.FileResource;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Custom task to execute dx while handling dependencies.
 */
public class DexExecTask extends BaseTask {

    private String mExecutable;
    private String mOutput;
    private boolean mVerbose = false;
    private boolean mNoLocals = false;
    private List<Path> mPathInputs;
    private List<FileSet> mFileSetInputs;


    /**
     * Sets the value of the "executable" attribute.
     * @param executable the value.
     */
    public void setExecutable(Path executable) {
        mExecutable = TaskHelper.checkSinglePath("executable", executable);
    }

    /**
     * Sets the value of the "verbose" attribute.
     * @param verbose the value.
     */
    public void setVerbose(boolean verbose) {
        mVerbose = verbose;
    }

    /**
     * Sets the value of the "output" attribute.
     * @param output the value.
     */
    public void setOutput(Path output) {
        mOutput = TaskHelper.checkSinglePath("output", output);
    }

    /**
     * Sets the value of the "nolocals" attribute.
     * @param verbose the value.
     */
    public void setNoLocals(boolean nolocals) {
        mNoLocals = nolocals;
    }

    /**
     * Returns an object representing a nested <var>path</var> element.
     */
    public Object createPath() {
        if (mPathInputs == null) {
            mPathInputs = new ArrayList<Path>();
        }

        Path path = new Path(getProject());
        mPathInputs.add(path);

        return path;
    }

    /**
     * Returns an object representing a nested <var>path</var> element.
     */
    public Object createFileSet() {
        if (mFileSetInputs == null) {
            mFileSetInputs = new ArrayList<FileSet>();
        }

        FileSet fs = new FileSet();
        fs.setProject(getProject());
        mFileSetInputs.add(fs);

        return fs;
    }


    @Override
    public void execute() throws BuildException {

        // get all input paths
        List<File> paths = new ArrayList<File>();
        if (mPathInputs != null) {
            for (Path pathList : mPathInputs) {
                for (String path : pathList.list()) {
                    paths.add(new File(path));
                }
            }
        }

        if (mFileSetInputs != null) {
            for (FileSet fs : mFileSetInputs) {
                Iterator<?> iter = fs.iterator();
                while (iter.hasNext()) {
                    FileResource fr = (FileResource) iter.next();
                    paths.add(fr.getFile());
                }
            }
        }

        // figure out the path to the dependency file.
        String depFile = mOutput + ".d";

        // get InputPath with no extension restrictions
        List<InputPath> inputPaths = getInputPaths(paths, null /*extensionsToCheck*/);

        if (initDependencies(depFile, inputPaths) && dependenciesHaveChanged() == false) {
            System.out.println(
                    "No new compiled code. No need to convert bytecode to dalvik format.");
            return;
        }

        System.out.println(String.format(
                "Converting compiled files and external libraries into %1$s...", mOutput));

        ExecTask task = new ExecTask();
        task.setProject(getProject());
        task.setOwningTarget(getOwningTarget());
        task.setExecutable(mExecutable);
        task.setTaskName(getExecTaskName());
        task.setFailonerror(true);

        task.createArg().setValue("--dex");

        if (mNoLocals) {
            task.createArg().setValue("--no-locals");
        }

        if (mVerbose) {
            task.createArg().setValue("--verbose");
        }

        task.createArg().setValue("--output");
        task.createArg().setValue(mOutput);


        for (File f : paths) {
            task.createArg().setValue(f.getAbsolutePath());
        }

        // execute it.
        task.execute();

        // generate the dependency file.
        generateDependencyFile(depFile, inputPaths, mOutput);
    }

    @Override
    protected String getExecTaskName() {
        return "dx";
    }
}
