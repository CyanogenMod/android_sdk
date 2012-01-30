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

package com.android.tools.lint;

import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Multiplexing" reporter which allows output to be split up into a separate
 * report for each separate project
 */
class MultiProjectHtmlReporter extends Reporter {
    MultiProjectHtmlReporter(Main client, File dir) {
        super(client, dir);
    }

    @Override
    void write(int errorCount, int warningCount, List<Warning> allIssues) throws IOException {
        Map<Project, List<Warning>> projectToWarnings = new HashMap<Project, List<Warning>>();
        for (Warning warning : allIssues) {
            List<Warning> list = projectToWarnings.get(warning.project);
            if (list == null) {
                list = new ArrayList<Warning>();
                projectToWarnings.put(warning.project, list);
            }
            list.add(warning);
        }
        for (Project project : projectToWarnings.keySet()) {
            // TODO: Can I get the project name from the Android manifest file instead?
            String projectName = project.getName();
            File output = new File(mOutput, projectName + ".html"); //$NON-NLS-1$
            if (output.exists()) {
                boolean deleted = output.delete();
                if (!deleted) {
                    mClient.log(null, "Could not delete old file %1$s", output);
                    continue;
                }
            }
            if (!output.getParentFile().canWrite()) {
                mClient.log(null, "Cannot write output file %1$s", output);
                continue;
            }
            HtmlReporter reporter = new HtmlReporter(mClient, output);
            reporter.setBundleResources(mBundleResources);
            reporter.setSimpleFormat(mSimpleFormat);
            reporter.setTitle(String.format("Lint Report for %1$s", projectName));

            List<Warning> issues = projectToWarnings.get(project);
            errorCount = 0;
            warningCount = 0;
            for (Warning warning: issues) {
                if (warning.severity == Severity.ERROR) {
                    errorCount++;
                } else if (warning.severity == Severity.WARNING) {
                    warningCount++;
                }
            }
            reporter.write(errorCount, warningCount, issues);
        }
    }
}
