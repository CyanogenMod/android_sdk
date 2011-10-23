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

import com.android.tools.lint.api.DetectorRegistry;
import com.android.tools.lint.api.IDomParser;
import com.android.tools.lint.api.Lint;
import com.android.tools.lint.api.ToolContext;
import com.android.tools.lint.checks.BuiltinDetectorRegistry;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Position;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Command line driver for the rules framework
 * <p>
 * TODO:
 * <ul>
 * <li>Offer priority or category sorting
 * <li>Offer suppressing violations
 * </ul>
 */
public class Main extends ToolContext {
    private static final int MAX_LINE_WIDTH = 70;
    private static final String ARG_ENABLE     = "--enable";       //$NON-NLS-1$
    private static final String ARG_SUPPRESS   = "--suppress";     //$NON-NLS-1$
    private static final String ARG_LISTIDS    = "--list";         //$NON-NLS-1$
    private static final String ARG_SHOW       = "--show";         //$NON-NLS-1$
    private static final String ARG_FULLPATH   = "--fullpath";     //$NON-NLS-1$
    private static final String ARG_HELP       = "--help";         //$NON-NLS-1$
    private static final String ARG_LINES      = "--lines";        //$NON-NLS-1$
    private static final int ERRNO_ERRORS = -1;
    private static final int ERRNO_USAGE = -2;
    private static final int ERRNO_EXISTS = -3;
    private static final int ERRNO_HELP = -4;
    private static final int ERRNO_INVALIDARGS = -5;

    private Set<String> mSuppress = new HashSet<String>();
    private Set<String> mEnabled = null;
    private StringBuilder mOutput = new StringBuilder(2000);
    private boolean mFatal;
    private String mCommonPrefix;
    private boolean mFullPath;
    private int mErrorCount;
    private int mWarningCount;
    private boolean mShowLines;

    /** Creates a CLI driver */
    public Main() {
    }

    /**
     * Runs the static analysis command line driver
     *
     * @param args program arguments
     */
    public static void main(String[] args) {
        new Main().run(args);
    }

    /**
     * Runs the static analysis command line driver
     *
     * @param args program arguments
     */
    private void run(String[] args) {
        if (args.length < 1) {
            printUsage(System.err);
            System.exit(ERRNO_USAGE);
        }

        DetectorRegistry registry = new BuiltinDetectorRegistry();

        List<File> files = new ArrayList<File>();
        for (int index = 0; index < args.length; index++) {
            String arg = args[index];
            if (arg.equals(ARG_HELP) || arg.equals("-h")) { //$NON-NLS-1$
                printUsage(System.out);
                System.exit(ERRNO_HELP);
            } else if (arg.equals(ARG_LISTIDS)) {
                displayValidIds(registry, System.out);
                System.exit(0);
            } else if (arg.equals(ARG_SHOW)) {
                // Show specific issues?
                if (index < args.length - 1) {
                    String[] ids = args[++index].split(",");
                    for (String id : ids) {
                        Issue issue = registry.getIssue(id);
                        if (issue == null) {
                            System.err.println("Invalid id \"" + id + "\".");
                            displayValidIds(registry, System.err);
                            System.exit(ERRNO_INVALIDARGS);
                        }
                        describeIssue(issue);
                    }
                } else {
                    showIssues(registry);
                }
                System.exit(0);
            } else if (arg.equals(ARG_FULLPATH)
                    || arg.equals(ARG_FULLPATH + "s")) { // allow "--fullpaths" too
                mFullPath = true;
            } else if (arg.equals(ARG_LINES)) {
                mShowLines = true;
            } else if (arg.equals(ARG_SUPPRESS)) {
                if (index == args.length - 1) {
                    System.err.println("Missing categories to suppress");
                    System.exit(ERRNO_INVALIDARGS);
                }
                String[] ids = args[++index].split(",");
                for (String id : ids) {
                    if (!registry.isIssueId(id)) {
                        System.err.println("Invalid id \"" + id + "\".");
                        displayValidIds(registry, System.err);
                        System.exit(ERRNO_INVALIDARGS);
                    }
                    mSuppress.add(id);
                }
            } else if (arg.equals(ARG_ENABLE)) {
                if (index == args.length - 1) {
                    System.err.println("Missing categories to enable");
                    System.exit(ERRNO_INVALIDARGS);
                }
                String[] ids = args[++index].split(",");
                mEnabled = new HashSet<String>();
                for (String id : ids) {
                    if (!registry.isIssueId(id)) {
                        System.err.println("Invalid id \"" + id + "\".");
                        displayValidIds(registry, System.err);
                        System.exit(ERRNO_INVALIDARGS);
                    }
                    mEnabled.add(id);
                }
            } else if (arg.startsWith("--")) {
                System.err.println("Invalid argument " + arg + "\n");
                printUsage(System.err);
                System.exit(ERRNO_INVALIDARGS);
            } else {
                String filename = arg;
                File file = new File(filename);
                if (!file.exists()) {
                    System.err.println(String.format("%1$s does not exist.", filename));
                    System.exit(ERRNO_EXISTS);
                }
                files.add(file);
            }
            // TODO: Add flag to point to a file of specific errors to suppress
        }

        if (files.size() == 0) {
            System.err.println("No files to analyze.");
            System.exit(ERRNO_INVALIDARGS);
        }

        mCommonPrefix = files.get(0).getPath();
        for (int i = 1; i < files.size(); i++) {
            File file = files.get(i);
            String path = file.getPath();
            mCommonPrefix = getCommonPrefix(mCommonPrefix, path);
        }

        Lint analyzer = new Lint(new BuiltinDetectorRegistry(), this, Scope.PROJECT);
        analyzer.analyze(files);
        if (mOutput.length() == 0) {
            System.out.println("No issues found.");
            System.exit(0); // Success error code
        } else {
            System.out.println(mOutput.toString());

            System.out.println(String.format("%1$d errors, %2$d warnings",
                    mErrorCount, mWarningCount));
            System.exit(mFatal ? ERRNO_ERRORS : 0);
        }
    }

    private void displayValidIds(DetectorRegistry registry, PrintStream out) {
        List<Issue> issues = registry.getIssues();
        out.println("Valid issue id's:");
        for (Issue issue : issues) {
            out.println("\"" + issue.getId() + "\": " + issue.getDescription());
        }
    }

    private void showIssues(DetectorRegistry registry) {
        List<Issue> issues = registry.getIssues();
        List<Issue> sorted = new ArrayList<Issue>(issues);
        Collections.sort(sorted, new Comparator<Issue>() {
            public int compare(Issue issue1, Issue issue2) {
                int d = issue1.getCategory().compareTo(issue2.getCategory());
                if (d != 0) {
                    return d;
                }
                d = issue2.getPriority() - issue1.getPriority();
                if (d != 0) {
                    return d;
                }

                return issue1.getId().compareTo(issue2.getId());
            }
        });

        System.out.println("Available issues:");
        for (Issue issue : sorted) {
            describeIssue(issue);
            System.out.println();
        }
    }

    private void describeIssue(Issue issue) {
        System.out.println(issue.getId());
        for (int i = 0; i < issue.getId().length(); i++) {
            System.out.print('-');
        }
        System.out.println();
        System.out.println(wrap("Summary: " + issue.getDescription(), MAX_LINE_WIDTH));
        System.out.println("Priority: " + issue.getPriority() + " / 10");
        System.out.println("Severity: " + issue.getDefaultSeverity().getDescription());
        System.out.println("Category: " + issue.getCategory());

        if (issue.getExplanation() != null) {
            System.out.println();
            System.out.println(wrap(issue.getExplanation(), MAX_LINE_WIDTH));
        }
        if (issue.getMoreInfo() != null) {
            System.out.println("\nMore information: " + issue.getMoreInfo());
        }
    }

    static String wrap(String explanation, int max) {
        int explanationLength = explanation.length();
        StringBuilder sb = new StringBuilder(explanationLength * 2);
        int index = 0;

        while (index < explanationLength) {
            int lineEnd = explanation.indexOf('\n', index);
            int next;

            if (lineEnd != -1 && (lineEnd - index) < max) {
                next = lineEnd + 1;
            } else {
                // Line is longer than available width; grab as much as we can
                lineEnd = Math.min(index + max, explanationLength);
                if (lineEnd - index < max) {
                    next = explanationLength;
                } else {
                    // then back up to the last space
                    int lastSpace = explanation.lastIndexOf(' ', lineEnd);
                    if (lastSpace > index) {
                        lineEnd = lastSpace;
                        next = lastSpace + 1;
                    } else {
                        // No space anywhere on the line: it contains something wider than
                        // can fit (like a long URL) so just hard break it
                        next = lineEnd + 1;
                    }
                }
            }

            sb.append(explanation.substring(index, lineEnd));
            sb.append('\n');
            index = next;
        }

        return sb.toString();
    }

    private static void printUsage(PrintStream out) {
        // TODO: Look up launcher script name!
        String command = "lint"; //$NON-NLS-1$

        out.println("Usage: " + command + " [flags] <project directories>\n");
        out.println("Flags:");
        out.println(ARG_SUPPRESS + " <id-list>: Suppress a list of issue id's.");
        out.println(ARG_ENABLE + " <id-list>: Only check the specific list of issues");
        out.println(ARG_FULLPATH + " : Use full paths in the error output");
        out.println(ARG_LINES + " : Include the lines with errors in the output");
        out.println();
        out.println(ARG_LISTIDS + ": List the available issue id's and exit.");
        out.println(ARG_SHOW + ": List available issues along with full explanations");
        out.println(ARG_SHOW + " <ids>: Show full explanations for the given list of issue id's");
        out.println("Id lists should be comma separated with no spaces. ");
    }

    private static String getCommonPrefix(String a, String b) {
        int aLength = a.length();
        int bLength = b.length();
        int aIndex = 0, bIndex = 0;
        for (; aIndex < aLength && bIndex < bLength; aIndex++, bIndex++) {
            if (a.charAt(aIndex) != b.charAt(bIndex)) {
                break;
            }
        }

        return a.substring(0, aIndex);
    }

    @Override
    public void log(Throwable exception, String format, Object... args) {
        System.err.println(String.format(format, args));
        if (exception != null) {
            exception.printStackTrace();
        }
    }

    @Override
    public IDomParser getParser() {
        return new PositionXmlParser();
    }

    @Override
    public boolean isEnabled(Issue issue) {
        if (mEnabled != null) {
            return mEnabled.contains(issue.getId());
        }
// TODO: Also include enabled by default
//        && issue.isEnabledByDefault() || mAll);
        return !mSuppress.contains(issue.getId());
    }

    @Override
    public void report(Context context, Issue issue, Location location, String message,
            Object data) {
        if (!isEnabled(issue)) {
            return;
        }

        Severity severity = getSeverity(issue);
        if (severity == Severity.IGNORE) {
            return;
        }
        if (severity == Severity.ERROR){
            mFatal = true;
            mErrorCount++;
        } else {
            mWarningCount++;
        }

        int startLength = mOutput.length();

        String errorLine = null;

        if (location != null) {
            File file = location.getFile();
            if (file != null) {
                String path = file.getPath();
                if (!mFullPath && path.startsWith(mCommonPrefix)) {
                    int chop = mCommonPrefix.length();
                    if (path.length() > chop && path.charAt(chop) == File.separatorChar) {
                        chop++;
                    }
                    path = path.substring(chop);
                }
                mOutput.append(path);
                mOutput.append(':');
            }

            Position startPosition = location.getStart();
            if (startPosition != null) {
                int line = startPosition.getLine();
                if (line >= 0) {
                    // line is 0-based, should display 1-based
                    mOutput.append(Integer.toString(line + 1));
                    mOutput.append(':');

                    if (mShowLines) {
                        // Compute error line contents
                        errorLine = getLine(context.getContents(), line);
                        if (errorLine != null) {
                            // Column number display does not work well because the
                            // column numbers are all wrong; the XMLFilter approach gives
                            // us the position *after* the element has been parsed.
                            // Need a different approach.
                            //int column = startPosition.getColumn();
                            //if (column > 0) {
                            //    StringBuilder sb = new StringBuilder();
                            //    sb.append(errorLine);
                            //    sb.append('\n');
                            //    for (int i = 0; i < column - 1; i++) {
                            //        sb.append(' ');
                            //    }
                            //    sb.append('^');
                            //    sb.append('\n');
                            //    errorLine = sb.toString();
                            //} else {
                            errorLine = errorLine + '\n';
                            //}
                        }
                    }
                }
            }

            // Column is not particularly useful here
            //int column = location.getColumn();
            //if (column > 0) {
            //    mOutput.append(Integer.toString(column));
            //    mOutput.append(':');
            //}

            if (startLength < mOutput.length()) {
                mOutput.append(' ');
            }
        }

        mOutput.append(severity.getDescription());
        mOutput.append(':');
        mOutput.append(' ');

        mOutput.append(message);
        if (issue != null) {
            mOutput.append(' ').append('[');
            mOutput.append(issue.getId());
            mOutput.append(']');
        }

        mOutput.append('\n');

        if (errorLine != null) {
            mOutput.append(errorLine);
        }
    }

    /** Look up the contents of the given line */
    private String getLine(String contents, int line) {
        int index = 0;
        for (int i = 0; i < line - 1; i++) {
            index = contents.indexOf('\n', index);
            if (index == -1) {
                return null;
            }
            index++;
        }

        int end = contents.indexOf('\n', index);
        return contents.substring(index, end != -1 ? end : contents.length());
    }

    @Override
    public boolean isSuppressed(Context context, Issue issue, Location range, String message,
            Severity severity, Object data) {
        // Not yet supported
        return false;
    }

    @Override
    public Severity getSeverity(Issue issue) {
        return issue.getDefaultSeverity();
    }

    @Override
    public String readFile(File file) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            StringBuilder sb = new StringBuilder((int) file.length());
            while (true) {
                int c = reader.read();
                if (c == -1) {
                    return sb.toString();
                } else {
                    sb.append((char)c);
                }
            }
        } catch (IOException e) {
            // pass -- ignore files we can't read
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                log(e, null);
            }
        }

        return ""; //$NON-NLS-1$
    }
}
