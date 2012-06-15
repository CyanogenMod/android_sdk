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

package com.android.ant;

import com.android.sdklib.SdkConstants;
import com.android.sdklib.internal.project.ProjectProperties;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

import java.io.File;

public class GetProjectPropertyTask extends Task {

    private String mProjectPath;
    private String mBinName;
    private String mSrcName;

    public void setProjectPath(String projectPath) {
        mProjectPath = projectPath;
    }

    public void setBin(String binName) {
        mBinName = binName;
    }

    public void setSrc(String srcName) {
        mSrcName = srcName;
    }

    @Override
    public void execute() throws BuildException {
        if (mProjectPath == null) {
            throw new BuildException("Missing attribute projectPath");
        }

        ProjectProperties props = TaskHelper.getProperties(mProjectPath);

        if (mBinName != null) {
            handleProp(props, "out.dir", mBinName, SdkConstants.FD_OUTPUT);
        }

        if (mSrcName != null) {
            handleProp(props, "source.dir", mSrcName, SdkConstants.FD_SOURCES);
        }

    }

    private void handleProp(ProjectProperties props, String inName, String outName,
            String defaultValue) {
        String value = props.getProperty(inName);
        if (value == null) {
            value = defaultValue;
        }
        getProject().setProperty(outName, new File(mProjectPath, value).getAbsolutePath());

    }
}
