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

import java.io.File;
import java.util.ArrayList;

/**
 * A base class for the ant task that contains logic for handling dependency files
 */
public class BaseTask extends Task {

    private DependencyGraph mDependencies;

    /*
     * (non-Javadoc)
     *
     * Executes the loop. Based on the values inside default.properties, this will
     * create alternate temporary ap_ files.
     *
     * @see org.apache.tools.ant.Task#execute()
     */
    @Override
    public void execute() throws BuildException {

    }

    /**
     * Set up the dependency graph by passing it the location of the ".d" file
     * @param dependencyFile path to the dependency file to use
     * @param watchPaths a list of folders to watch for new files
     * @return true if the dependency graph was sucessfully initialized
     */
    protected boolean initDependencies(String dependencyFile, ArrayList<File> watchPaths) {
        File depFile = new File(dependencyFile);
        if (depFile.exists()) {
            mDependencies = new DependencyGraph(dependencyFile, watchPaths);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Wrapper check to see if we need to execute this task at all
     * @return true if the DependencyGraph reports that our prereqs or targets
     *         have changed since the last run
     */
    protected boolean dependenciesHaveChanged() {
        assert mDependencies != null : "Dependencies have not been initialized";
        return mDependencies.dependenciesHaveChanged();
    }
}
