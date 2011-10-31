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

import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Severity;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * A reporter which emits lint results into an HTML report.
 */
class HtmlReporter extends Reporter {
    private final File mOutput;
    private Map<String, String> mUrlMap;

    HtmlReporter(File output) throws IOException {
        super(new BufferedWriter(new FileWriter(output)));
        mOutput = output;
        mWriter.write(
                "<html>\n" +                                             //$NON-NLS-1$
                "<head>\n" +                                             //$NON-NLS-1$
                "<title>Lint Report</title>\n" +                         //$NON-NLS-1$
                "<style type=\"text/css\">\n" +                          //$NON-NLS-1$

                // CSS stylesheet for the report:

                "body { max-width: 800px }\n" +                          //$NON-NLS-1$
                // The div surrounding each issue with groups of warnings within
                ".issue {\n" +                                           //$NON-NLS-1$
                "    border: solid 1px #cccccc;\n" +                     //$NON-NLS-1$
                "    margin-top: 10px;\n" +                              //$NON-NLS-1$
                "    margin-bottom: 10px;\n" +                           //$NON-NLS-1$
                "    padding: 5px;\n" +                                  //$NON-NLS-1$
                "    background-color: #eeeeee;\n" +                     //$NON-NLS-1$
                "}\n" +                                                  //$NON-NLS-1$
                // The issue id header label
                ".id {\n" +                                              //$NON-NLS-1$
                "    font-size: 14pt;\n" +                               //$NON-NLS-1$
                "    font-weight: bold;\n" +                             //$NON-NLS-1$
                "    margin: 5px 0px 5px 0px;\n" +                       //$NON-NLS-1$
                "}\n" +                                                  //$NON-NLS-1$
                // The issue summary line
                //".summary {\n" +                                       //$NON-NLS-1$
                //"    font-weight: bold;\n" +                           //$NON-NLS-1$
                //"    margin-bottom: 20px;\n" +                         //$NON-NLS-1$
                //"}\n" +
                // The explanation area
                ".explanation {\n" +                                     //$NON-NLS-1$
                "    margin-top: 10px;\n" +                              //$NON-NLS-1$
                "}\n" +                                                  //$NON-NLS-1$
                // The warning/error severity label
                //".error { color: red; }\n" +                           //$NON-NLS-1$
                //".warning { color: rgb(244,200,45); }\n" +             //$NON-NLS-1$
                "pre {\n" +                                              //$NON-NLS-1$
                "    border: solid 1px #cccccc;\n" +                     //$NON-NLS-1$
                "    background-color: #dddddd;" +                       //$NON-NLS-1$
                "    margin: 10pt;" +                                    //$NON-NLS-1$
                "}\n" +                                                  //$NON-NLS-1$
                ".moreinfo {\n" +                                        //$NON-NLS-1$
                "}\n" +                                                  //$NON-NLS-1$
                ".location {\n" +                                        //$NON-NLS-1$
                "    font-family: monospace;\n" +                        //$NON-NLS-1$
                "}\n" +
                // Preview images for icon issues: limit size to at most 200 in one dimension
                ".embedimage {\n" +                                      //$NON-NLS-1$
                "    max-width: 200px;\n" +                              //$NON-NLS-1$
                "    max-height: 200px;\n" +                             //$NON-NLS-1$
                "}\n" +                                                  //$NON-NLS-1$
                // The Priority/Category section
                ".metadata { }\n" +                                      //$NON-NLS-1$
                // Each error message
                ".message { font-weight:bold; }\n" +                     //$NON-NLS-1$
                // The <div> containing the source code fragment
                ".errorlines { font-family: monospace; }\n" +            //$NON-NLS-1$
                // The span within a line containing the highlighted error
                ".errorspan { font-weight: bold; }\n" +                  //$NON-NLS-1$
                // The whole line containing the highlighted error
                ".errorline { font-weight: bold; }\n" +                  //$NON-NLS-1$
                // The list of specific warnings for a given issue
                ".warningslist { margin-bottom: 20px; }\n" +             //$NON-NLS-1$

                "</style>\n" +                                           //$NON-NLS-1$
                "</head>\n" +                                            //$NON-NLS-1$
                "<body>\n" +                                             //$NON-NLS-1$
                "<h1>" +                                                 //$NON-NLS-1$
                "Lint Report" +
                "</h1>");                                                //$NON-NLS-1$
    }

    @Override
    void write(int errorCount, int warningCount, List<Warning> issues) throws IOException {
        Issue previousIssue = null;
        if (issues.size() > 0) {
            List<List<Warning>> related = new ArrayList<List<Warning>>();
            List<Warning> currentList = null;
            for (Warning warning : issues) {
                if (warning.issue != previousIssue) {
                    previousIssue = warning.issue;
                    currentList = new ArrayList<Warning>();
                    related.add(currentList);
                }
                assert currentList != null;
                currentList.add(warning);
            }

            mWriter.write(String.format("Check performed at %1$s.",
                    new Date().toString()));
            mWriter.write("<br/>");                                       //$NON-NLS-1$
            mWriter.write(String.format("%1$d errors and %2$d warnings found:",
                    errorCount, warningCount));
            mWriter.write("<br/>");                                       //$NON-NLS-1$

            // Write issue id summary
            mWriter.write("<ul>\n");                                     //$NON-NLS-1$
            for (List<Warning> warnings : related) {
                mWriter.write("<li> <a href=\"#"                         //$NON-NLS-1$
                        + warnings.get(0).issue.getId()
                        +"\">");                                         //$NON-NLS-1$
                mWriter.write(String.format("%1$3d %2$s",                //$NON-NLS-1$
                        warnings.size(), warnings.get(0).issue.getId()));
                mWriter.write("</a>\n");                                 //$NON-NLS-1$
            }
            mWriter.write("</ul>\n");                                    //$NON-NLS-1$
            mWriter.write("<br/>");                                      //$NON-NLS-1$

            for (List<Warning> warnings : related) {
                Warning first = warnings.get(0);
                Issue issue = first.issue;

                mWriter.write("<a name=\"" + issue.getId() + "\">\n");  //$NON-NLS-1$ //$NON-NLS-2$
                mWriter.write("<div class=\"issue\">\n");                //$NON-NLS-1$

                // Explain this issue
                mWriter.write("<div class=\"id\">");                     //$NON-NLS-1$
                mWriter.write(issue.getId());
                mWriter.write("</div>\n"); //$NON-NLS-1$

                mWriter.write("<div class=\"warningslist\">\n");         //$NON-NLS-1$
                for (Warning warning : warnings) {
                    String url = null;
                    if (warning.path != null) {
                        mWriter.write("<span class=\"location\">");      //$NON-NLS-1$

                        url = getUrl(warning.file.getPath());
                        if (url != null) {
                            mWriter.write("<a href=\"");                 //$NON-NLS-1$
                            mWriter.write(url);
                            mWriter.write("\">");                        //$NON-NLS-1$
                        }
                        mWriter.write(warning.path);
                        if (url != null) {
                            mWriter.write("</a>");                       //$NON-NLS-1$
                        }
                        mWriter.write(':');
                        if (warning.line >= 0) {
                            // 0-based line numbers, but display 1-based
                            mWriter.write(Integer.toString(warning.line + 1) + ':');
                        }
                        mWriter.write("</span>"); //$NON-NLS-1$
                        mWriter.write(' ');
                    }

                    // Is the URL for a single image? If so, place it here near the top
                    // of the error floating on the right. If there are multiple images,
                    // they will instead be placed in a horizontal box below the error
                    boolean addedImage = false;
                    if (url != null && warning.location != null
                            && warning.location.getSecondary() == null) {
                        addedImage = addImage(url, warning.location);
                    }
                    mWriter.write("<span class=\"message\">");           //$NON-NLS-1$
                    appendEscapedText(warning.message);
                    mWriter.write("</span>");                            //$NON-NLS-1$
                    if (addedImage) {
                        mWriter.write("<br clear=\"right\"/>");          //$NON-NLS-1$
                    } else {
                        mWriter.write("<br />");                         //$NON-NLS-1$
                    }

                    // Insert surrounding code block window
                    if (warning.line >= 0 && warning.fileContents != null) {
                        mWriter.write("<pre class=\"errorlines\">\n");   //$NON-NLS-1$
                        appendCodeBlock(warning.fileContents, warning.line, warning.offset);
                        mWriter.write("\n</pre>");                       //$NON-NLS-1$
                    }
                    mWriter.write('\n');

                    // Place a block of images?
                    if (!addedImage && url != null && warning.location != null
                            && warning.location.getSecondary() != null) {
                        addImage(url, warning.location);
                    }
                }
                mWriter.write("</div>\n");                               //$NON-NLS-1$

                mWriter.write("<div class=\"metadata\">");               //$NON-NLS-1$
                mWriter.write("Priority: ");
                mWriter.write(issue.getPriority());
                mWriter.write("<br/>\n");                                //$NON-NLS-1$
                mWriter.write("Category: ");
                mWriter.write(issue.getCategory());
                mWriter.write("</div>\n");                               //$NON-NLS-1$

                mWriter.write("Severity: ");
                if (first.severity == Severity.ERROR) {
                    mWriter.write("<span class=\"error\">");             //$NON-NLS-1$
                } else if (first.severity == Severity.WARNING) {
                    mWriter.write("<span class=\"warning\">");           //$NON-NLS-1$
                } else {
                    mWriter.write("<span>");                             //$NON-NLS-1$
                }
                appendEscapedText(first.severity.getDescription());
                mWriter.write("</span>");                                //$NON-NLS-1$

                mWriter.write("<div class=\"summary\">\n");              //$NON-NLS-1$
                mWriter.write("Explanation: ");
                String description = issue.getDescription();
                mWriter.write(description);
                if (description.length() > 0
                        && Character.isLetter(description.charAt(description.length() - 1))) {
                    mWriter.write('.');
                }
                mWriter.write("</div>\n");                               //$NON-NLS-1$
                mWriter.write("<div class=\"explanation\">\n");          //$NON-NLS-1$
                String explanation = issue.getExplanation();
                explanation = explanation.replace("\n", "<br/>");       //$NON-NLS-1$ //$NON-NLS-2$
                explanation = Main.wrap(explanation);
                mWriter.write(explanation);
                mWriter.write("\n</div>\n");                             //$NON-NLS-1$;
                if (issue.getMoreInfo() != null) {
                    mWriter.write("<div class=\"moreinfo\">");           //$NON-NLS-1$
                    mWriter.write("More info: ");
                    mWriter.write("<a href=\"");                         //$NON-NLS-1$
                    mWriter.write(issue.getMoreInfo());
                    mWriter.write("\">");                                //$NON-NLS-1$
                    mWriter.write(issue.getMoreInfo());
                    mWriter.write("</a></div>\n");                       //$NON-NLS-1$
                }

                mWriter.write("<br/>");                                  //$NON-NLS-1$
                mWriter.write("To suppress this error, run lint with <code>--suppress ");
                mWriter.write(issue.getId());
                mWriter.write("</code><br/>");                           //$NON-NLS-1$

                mWriter.write("</div>");                                 //$NON-NLS-1$
            }
        }
        mWriter.write("\n</body>\n</html>");                             //$NON-NLS-1$
        mWriter.close();

        String path = mOutput.getAbsolutePath();
        System.out.println(String.format("Wrote HTML report to %1$s", path));
    }

    private boolean addImage(String url, Location location) throws IOException {
        if (url != null && url.endsWith(".png")) {                       //$NON-NLS-1$
            if (location.getSecondary() != null) {
                // Emit many images
                // Add in linked images as well
                List<String> urls = new ArrayList<String>();
                while (location != null && location.getFile() != null) {
                    String imageUrl = getUrl(location.getFile().getPath());
                    if (imageUrl != null
                            && imageUrl.endsWith(".png")) {              //$NON-NLS-1$
                        urls.add(imageUrl);
                    }
                    location = location.getSecondary();
                }
                if (urls.size() > 0) {
                    // Sort in order
                    Collections.sort(urls, new Comparator<String>() {
                        public int compare(String s1, String s2) {
                            return getDpiRank(s1) - getDpiRank(s2);
                        }
                    });
                    mWriter.write("<table normal\" border=\"0\"><tr>");  //$NON-NLS-1$
                    for (String linkedUrl : urls) {
                        mWriter.write("<th>");                           //$NON-NLS-1$
                        int index = linkedUrl.lastIndexOf("drawable-");  //$NON-NLS-1$
                        if (index != -1) {
                            index += "drawable-".length();               //$NON-NLS-1$
                            int end = linkedUrl.indexOf('/', index);
                            if (end != -1) {
                                mWriter.write(linkedUrl.substring(index, end));
                            }
                        }
                        mWriter.write("</th>");                          //$NON-NLS-1$
                    }
                    mWriter.write("</tr>\n<tr>");                        //$NON-NLS-1$
                    for (String linkedUrl : urls) {
                        // Image series: align top
                        mWriter.write("<td>");                           //$NON-NLS-1$
                        mWriter.write("<a href=\"");                     //$NON-NLS-1$
                        mWriter.write(linkedUrl);
                        mWriter.write("\">");                            //$NON-NLS-1$
                        mWriter.write("<img border=\"0\" align=\"top\" src=\"");      //$NON-NLS-1$
                        mWriter.write(linkedUrl);
                        mWriter.write("\" /></a>\n");                    //$NON-NLS-1$
                        mWriter.write("</td>");                          //$NON-NLS-1$
                    }
                    mWriter.write("</tr></table>");                      //$NON-NLS-1$
                }
            } else {
                // Just this image: float to the right
                mWriter.write("<img class=\"embedimage\" align=\"right\" src=\""); //$NON-NLS-1$
                mWriter.write(url);
                mWriter.write("\" />");                                  //$NON-NLS-1$
            }

            return true;
        }

        return false;
    }

    /** Provide a sorting rank for a url */
    private static int getDpiRank(String url) {
        if (url.contains("-xhdpi")) {                                   //$NON-NLS-1$
            return 0;
        } else if (url.contains("-hdpi")) {                             //$NON-NLS-1$
            return 1;
        } else if (url.contains("-mdpi")) {                             //$NON-NLS-1$
            return 2;
        } else if (url.contains("-ldpi")) {                             //$NON-NLS-1$
            return 3;
        } else {
            return 4;
        }
    }

    private void appendCodeBlock(String contents, int lineno, int offset)
            throws IOException {
        int max = lineno + 3;
        int min = lineno - 3;
        for (int l = min; l < max; l++) {
            if (l >= 0) {
                int lineOffset = Main.getLineOffset(contents, l);
                if (lineOffset == -1) {
                    break;
                }

                mWriter.write(String.format("%1$4d ", (l + 1)));         //$NON-NLS-1$

                String line = Main.getLineOfOffset(contents, lineOffset);
                if (offset != -1 && lineOffset <= offset && lineOffset+line.length() >= offset) {
                    assert l == lineno;
                    // This line contains the beginning of the offset
                    // First print everything before
                    int delta = offset - lineOffset;
                    appendEscapedText(line.substring(0, delta));
                    mWriter.write("<span class=\"errorspan\">");         //$NON-NLS-1$
                    appendEscapedText(line.substring(delta));
                    mWriter.write("</span>");                            //$NON-NLS-1$
                } else if (offset == -1 && l == lineno) {
                    mWriter.write("<span class=\"errorline\">");         //$NON-NLS-1$
                    appendEscapedText(line);
                    mWriter.write("</span>");                            //$NON-NLS-1$
                } else {
                    appendEscapedText(line);
                }
                if (l < max - 1) {
                    mWriter.write("\n");                                 //$NON-NLS-1$
                }
            }
        }
    }

    private void appendEscapedText(String textValue) throws IOException {
        for (int i = 0, n = textValue.length(); i < n; i++) {
            char c = textValue.charAt(i);
            if (c == '<') {
                mWriter.write("&lt;");                                   //$NON-NLS-1$
            } else if (c == '&') {
                mWriter.write("&amp;");                                  //$NON-NLS-1$
            } else {
                mWriter.write(c);
            }
        }
    }

    private String getUrl(String path) {
        if (mUrlMap != null) {
            try {
                // Perform the comparison using URLs such that we properly escape spaces etc.
                String pathUrl = URLEncoder.encode(path, "UTF-8");         //$NON-NLS-1$
                for (Map.Entry<String, String> entry : mUrlMap.entrySet()) {
                    String prefix = entry.getKey();
                    String prefixUrl = URLEncoder.encode(prefix, "UTF-8"); //$NON-NLS-1$
                    if (pathUrl.startsWith(prefixUrl)) {
                        String relative = pathUrl.substring(prefixUrl.length());
                        return entry.getValue()
                                + relative.replace("%2F", "/"); //$NON-NLS-1$ //$NON-NLS-2$
                    }
                }
            } catch (UnsupportedEncodingException e) {
                // This shouldn't happen for UTF-8
                System.err.println("Invalid URL map specification - " + e.getLocalizedMessage());
            }
        }

        return null;
    }

    /** Set mapping of path prefixes to corresponding URLs in the HTML report */
    void setUrlMap(Map<String, String> urlMap) {
        mUrlMap = urlMap;
    }
}