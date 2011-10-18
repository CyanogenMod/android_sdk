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
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Position;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.util.ArrayList;
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
public class Main implements ToolContext {
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
            printUsage();
            System.exit(ERRNO_USAGE);
        }

        DetectorRegistry registry = new BuiltinDetectorRegistry();

        List<File> files = new ArrayList<File>();
        for (int index = 0; index < args.length; index++) {
            String arg = args[index];
            if (arg.equals("--help") || arg.equals("-h")) { //$NON-NLS-1$ //$NON-NLS-2$
                printUsage();
                System.err.println("\n" +
                        "Run with --suppress <category1[,category2,...]> to suppress categories.");
                System.exit(ERRNO_HELP);
            } else if (arg.equals("--suppress")) {
                if (index == args.length - 1) {
                    System.err.println("Missing categories to suppress");
                    System.exit(ERRNO_INVALIDARGS);
                }
                String[] ids = args[++index].split(",");
                for (String id : ids) {
                    if (!registry.isIssueId(id)) {
                        System.err.println("Invalid id \"" + id + "\".");
                        displayValidIds(registry);
                        System.exit(ERRNO_INVALIDARGS);
                    }
                    mSuppress.add(id);
                }
            } else if (arg.equals("--enable")) {
                if (index == args.length - 1) {
                    System.err.println("Missing categories to enable");
                    System.exit(ERRNO_INVALIDARGS);
                }
                String[] ids = args[++index].split(",");
                mEnabled = new HashSet<String>();
                for (String id : ids) {
                    if (!registry.isIssueId(id)) {
                        System.err.println("Invalid id \"" + id + "\".");
                        displayValidIds(registry);
                        System.exit(ERRNO_INVALIDARGS);
                    }
                    mEnabled.add(id);
                }
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
            System.out.println("No warnings.");
            System.exit(0); // Success error code
        } else {
            System.err.println(mOutput.toString());
            System.err.println("Run with --suppress to turn off specific types of errors, or this message");

            System.exit(mFatal ? ERRNO_ERRORS : 0);
        }
    }

    private void displayValidIds(DetectorRegistry registry) {
        List<Issue> issues = registry.getIssues();
        System.err.println("Valid issue ids:");
        for (Issue issue : issues) {
            System.err.println("\"" + issue.getId() + "\": " + issue.getDescription());
        }
    }

    private static void printUsage() {
        // TODO: Look up launcher script name!
        System.err.println("Usage: lint [--suppress ids] [--enable ids] <project | file> ...");
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

    public void log(Throwable exception, String format, Object... args) {
        System.err.println(String.format(format, args));
        if (exception != null) {
            exception.printStackTrace();
        }
    }

    public IDomParser getParser() {
        return new PositionXmlParser();
    }

    public boolean isEnabled(Issue issue) {
        if (mEnabled != null) {
            return mEnabled.contains(issue.getId());
        }
        return !mSuppress.contains(issue.getId());
    }

    public void report(Issue issue, Location location, String message) {
        if (!isEnabled(issue)) {
            return;
        }

        Severity severity = getSeverity(issue);
        if (severity == Severity.IGNORE) {
            return;
        }
        if (severity == Severity.ERROR){
            mFatal = true;
        }

        int startLength = mOutput.length();

        if (location != null) {
            File file = location.getFile();
            if (file != null) {
                String path = file.getPath();
                if (path.startsWith(mCommonPrefix)) {
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
    }

    public boolean isSuppressed(Issue issue, Location range, String message,
            Severity severity) {
        // Not yet supported
        return false;
    }

    public Severity getSeverity(Issue issue) {
        return issue.getDefaultSeverity();
    }
}
