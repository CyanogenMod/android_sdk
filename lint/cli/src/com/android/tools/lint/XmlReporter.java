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

import com.android.tools.lint.detector.api.Position;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * A reporter which emits lint results into an XML report.
 */
class XmlReporter extends Reporter {
    private final File mOutput;

    XmlReporter(File output) throws IOException {
        super(new BufferedWriter(new FileWriter(output)));
        mOutput = output;
    }

    @Override
    void write(int errorCount, int warningCount, List<Warning> issues) throws IOException {
        mWriter.write(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +     //$NON-NLS-1$
                "<issues>\n");                                       //$NON-NLS-1$

        if (issues.size() > 0) {
            for (Warning warning : issues) {
                mWriter.write("\n    <issue");
                writeAttribute(mWriter, "id", warning.issue.getId());   //$NON-NLS-1$
                writeAttribute(mWriter, "severity", warning.severity.getDescription()); //$NON-NLS-1$
                writeAttribute(mWriter, "message", warning.issue.getId());  //$NON-NLS-1$
                if (warning.file != null) {
                    writeAttribute(mWriter, "file", warning.file.getPath());  //$NON-NLS-1$
                    if (warning.location != null) {
                        Position start = warning.location.getStart();
                        if (start != null) {
                            int line = start.getLine();
                            int column = start.getColumn();
                            if (line >= 0) {
                                writeAttribute(mWriter, "line", Integer.toString(line));  //$NON-NLS-1$
                                if (column >= 0) {
                                    writeAttribute(mWriter, "column", Integer.toString(column)); //$NON-NLS-1$
                                }
                            }
                        }
                    }
                }
                mWriter.write("\n    />\n");
            }
        }

        mWriter.write(
                "\n</issues>\n");                                      //$NON-NLS-1$
        mWriter.close();

        String path = mOutput.getAbsolutePath();
        System.out.println(String.format("Wrote HTML report to %1$s", path));
    }

    private static void writeAttribute(Writer writer, String name, String value)
            throws IOException {
        writer.write("\n      ");           //$NON-NLS-1$
        writer.write(name);
        writer.write('=');
        writer.write('"');
        for (int i = 0, n = value.length(); i < n; i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    writer.write("&quot;"); //$NON-NLS-1$
                    break;
                case '\'':
                    writer.write("&apos;"); //$NON-NLS-1$
                    break;
                case '&':
                    writer.write("&amp;");  //$NON-NLS-1$
                    break;
                case '<':
                    writer.write("&lt;");   //$NON-NLS-1$
                    break;
                default:
                    writer.write(c);
                    break;
            }
        }
        writer.write('"');
    }
}