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
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Closeables;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * "Multiplexing" reporter which allows output to be split up into a separate
 * report for each separate project. It also adds an overview index.
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


        // Set of unique file names: lowercase names to avoid case conflicts in web environment
        Set<String> unique = Sets.newHashSet();
        List<ProjectEntry> projects = Lists.newArrayList();

        String indexName = "index.html"; //$NON-NLS-1$
        unique.add(indexName.toLowerCase(Locale.US));

        for (Project project : projectToWarnings.keySet()) {
            // TODO: Can I get the project name from the Android manifest file instead?
            String projectName = project.getName();

            // Produce file names of the form Project.html, Project1.html, Project2.html, etc
            int number = 1;
            String fileName;
            while (true) {
                String numberString = number > 1 ? Integer.toString(number) : "";
                fileName = String.format("%1$s%2$s.html", projectName, numberString); //$NON-NLS-1$
                String lowercase = fileName.toLowerCase(Locale.US);
                if (!unique.contains(lowercase)) {
                    unique.add(lowercase);
                    break;
                }
                number++;
            }

            File output = new File(mOutput, fileName);
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
            int projectErrorCount = 0;
            int projectWarningCount = 0;
            for (Warning warning: issues) {
                if (warning.severity == Severity.ERROR) {
                    projectErrorCount++;
                } else if (warning.severity == Severity.WARNING) {
                    projectWarningCount++;
                }
            }
            reporter.write(projectErrorCount, projectWarningCount, issues);

            projects.add(new ProjectEntry(project, fileName, projectErrorCount,
                    projectWarningCount));
        }

        // Write overview index?
        if (projects.size() > 1) {
            writeOverview(errorCount, warningCount, projects, indexName);
        }
    }

    private void writeOverview(int errorCount, int warningCount, List<ProjectEntry> projects,
            String indexName) throws IOException {
        File index = new File(mOutput, indexName);
        if (index.exists()) {
            boolean deleted = index.delete();
            if (!deleted) {
                mClient.log(null, "Could not delete old index file %1$s", index);
                System.exit(-1);
            }
        }
        Writer writer = new BufferedWriter(new FileWriter(index));
        writer.write(
                "<html>\n" +                                             //$NON-NLS-1$
                "<head>\n" +                                             //$NON-NLS-1$
                "<title>" + mTitle + "</title>\n" +                      //$NON-NLS-1$//$NON-NLS-2$
                "<style type=\"text/css\">\n" +                          //$NON-NLS-1$

                // CSS stylesheet for the report:
                // TODO: Export the CSS into a separate file?
                HtmlReporter.getStyleSheet() +

                ".overview th { font-weight: bold; }\n" +                //$NON-NLS-1$
                ".overview {\n" +                                        //$NON-NLS-1$
                "    padding: 10pt;\n" +                                 //$NON-NLS-1$
                "    width: 70%;\n" +                                    //$NON-NLS-1$
                "    text-align: right;\n" +                             //$NON-NLS-1$
                "    border: solid 1px #cccccc;\n" +                     //$NON-NLS-1$
                "    background-color: #eeeeee;\n" +                     //$NON-NLS-1$
                "    margin: 10pt;\n" +                                  //$NON-NLS-1$
                "    overflow: auto;\n" +                                //$NON-NLS-1$
                "}\n" +                                                  //$NON-NLS-1$

                "</style>\n" +                                           //$NON-NLS-1$

                "</head>\n" +                                            //$NON-NLS-1$
                "<body>\n" +                                             //$NON-NLS-1$
                "<h1>" +                                                 //$NON-NLS-1$
                mTitle +
                "</h1>");                                                //$NON-NLS-1$


        // Sort project list in decreasing order of errors, warnings and names
        Collections.sort(projects);

        writer.write(String.format("Check performed at %1$s.",
                new Date().toString()));
        writer.write("<br/><br/>");                                     //$NON-NLS-1$
        writer.write(String.format("%1$d errors and %2$d warnings found:\n",
                errorCount, warningCount));
        writer.write("<br/>");                                          //$NON-NLS-1$

        writer.write("<table class=\"overview\">\n");                   //$NON-NLS-1$
        writer.write("<tr><th>");                                       //$NON-NLS-1$
        writer.write("Project");
        writer.write("</th><th>");                                      //$NON-NLS-1$
        writer.write("Errors");
        writer.write("</th><th>");                                      //$NON-NLS-1$
        writer.write("Warnings");
        writer.write("</th></tr>\n");                                   //$NON-NLS-1$

        for (ProjectEntry entry : projects) {
            writer.write("<tr><td>");                                   //$NON-NLS-1$
            writer.write("<a href=\"");
            writer.write(entry.fileName); // TODO: Escape?
            writer.write("\">");                                        //$NON-NLS-1$
            writer.write(entry.project.getName());
            writer.write("</a></td><td>");                              //$NON-NLS-1$
            writer.write(Integer.toString(entry.errorCount));
            writer.write("</td><td>");                                  //$NON-NLS-1$
            writer.write(Integer.toString(entry.warningCount));
            writer.write("</td></tr>\n");                               //$NON-NLS-1$
        }
        writer.write("</table>\n");                                     //$NON-NLS-1$

        Closeables.closeQuietly(writer);

        System.out.println();
        System.out.println(String.format("Wrote overview index to %1$s", index));
    }

    private static class ProjectEntry implements Comparable<ProjectEntry> {
        public Project project;
        public int errorCount;
        public int warningCount;
        public String fileName;


        public ProjectEntry(Project project, String fileName, int errorCount, int warningCount) {
            super();
            this.project = project;
            this.fileName = fileName;
            this.errorCount = errorCount;
            this.warningCount = warningCount;
        }

        @Override
        public int compareTo(ProjectEntry other) {
            int delta = other.errorCount - errorCount;
            if (delta != 0) {
                return delta;
            }

            delta = other.warningCount - warningCount;
            if (delta != 0) {
                return delta;
            }

            return project.getName().compareTo(other.project.getName());
        }
    }
}
