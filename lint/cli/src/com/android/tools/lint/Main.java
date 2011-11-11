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

import com.android.tools.lint.checks.BuiltinIssueRegistry;
import com.android.tools.lint.client.api.Configuration;
import com.android.tools.lint.client.api.DefaultConfiguration;
import com.android.tools.lint.client.api.IDomParser;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.client.api.Lint;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.client.api.LintListener;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Position;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Severity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
public class Main extends LintClient {
    private static final int MAX_LINE_WIDTH = 78;
    private static final String ARG_ENABLE     = "--enable";       //$NON-NLS-1$
    private static final String ARG_DISABLE    = "--disable";      //$NON-NLS-1$
    private static final String ARG_CHECK      = "--check";        //$NON-NLS-1$
    private static final String ARG_IGNORE     = "--ignore";       //$NON-NLS-1$
    private static final String ARG_LISTIDS    = "--list";         //$NON-NLS-1$
    private static final String ARG_SHOW       = "--show";         //$NON-NLS-1$
    private static final String ARG_QUIET      = "--quiet";        //$NON-NLS-1$
    private static final String ARG_FULLPATH   = "--fullpath";     //$NON-NLS-1$
    private static final String ARG_HELP       = "--help";         //$NON-NLS-1$
    private static final String ARG_NOLINES    = "--nolines";      //$NON-NLS-1$
    private static final String ARG_HTML       = "--html";         //$NON-NLS-1$
    private static final String ARG_SIMPLEHTML = "--simplehtml";   //$NON-NLS-1$
    private static final String ARG_XML        = "--xml";          //$NON-NLS-1$
    private static final String ARG_URL        = "--url";          //$NON-NLS-1$
    private static final int ERRNO_ERRORS = -1;
    private static final int ERRNO_USAGE = -2;
    private static final int ERRNO_EXISTS = -3;
    private static final int ERRNO_HELP = -4;
    private static final int ERRNO_INVALIDARGS = -5;

    private List<Warning> mWarnings = new ArrayList<Warning>();
    private Set<String> mSuppress = new HashSet<String>();
    private Set<String> mEnabled = new HashSet<String>();
    /** If non-null, only run the specified checks (possibly modified by enable/disables) */
    private Set<String> mCheck = null;
    private boolean mFatal;
    private boolean mFullPath;
    private int mErrorCount;
    private int mWarningCount;
    private boolean mShowLines = true;
    private Reporter mReporter;
    private boolean mQuiet;

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

        IssueRegistry registry = new BuiltinIssueRegistry();

        // Mapping from file path prefix to URL. Applies only to HTML reports
        String urlMap = null;

        List<File> files = new ArrayList<File>();
        for (int index = 0; index < args.length; index++) {
            String arg = args[index];

            if (arg.equals(ARG_HELP)
                    || arg.equals("-h") || arg.equals("-?")) { //$NON-NLS-1$ //$NON-NLS-2$
                printUsage(System.out);
                System.exit(ERRNO_HELP);
            } else if (arg.equals(ARG_LISTIDS)) {
                // Did the user provide a category list?
                if (index < args.length - 1 && !args[index + 1].startsWith("-")) { //$NON-NLS-1$
                    String[] ids = args[++index].split(",");
                    for (String id : ids) {
                        if (registry.isCategoryName(id)) {
                            // List all issues with the given category
                            String category = id;
                            for (Issue issue : registry.getIssues()) {
                                // Check prefix such that filtering on the "Usability" category
                                // will match issue category "Usability:Icons" etc.
                                if (issue.getCategory().getName().startsWith(category) ||
                                        issue.getCategory().getFullName().startsWith(category)) {
                                    listIssue(System.out, issue);
                                }
                            }
                        } else {
                            System.err.println("Invalid category \"" + id + "\".\n");
                            displayValidIds(registry, System.err);
                            System.exit(ERRNO_INVALIDARGS);
                        }
                    }
                } else {
                    displayValidIds(registry, System.out);
                }
                System.exit(0);
            } else if (arg.equals(ARG_SHOW)) {
                // Show specific issues?
                if (index < args.length - 1 && !args[index + 1].startsWith("-")) { //$NON-NLS-1$
                    String[] ids = args[++index].split(",");
                    for (String id : ids) {
                        if (registry.isCategoryName(id)) {
                            // Show all issues in the given category
                            String category = id;
                            for (Issue issue : registry.getIssues()) {
                                // Check prefix such that filtering on the "Usability" category
                                // will match issue category "Usability:Icons" etc.
                                if (issue.getCategory().getName().startsWith(category) ||
                                        issue.getCategory().getFullName().startsWith(category)) {
                                    describeIssue(issue);
                                }
                            }
                        } else if (registry.isIssueId(id)) {
                            describeIssue(registry.getIssue(id));
                        } else {
                            System.err.println("Invalid id or category \"" + id + "\".\n");
                            displayValidIds(registry, System.err);
                            System.exit(ERRNO_INVALIDARGS);
                        }
                    }
                } else {
                    showIssues(registry);
                }
                System.exit(0);
            } else if (arg.equals(ARG_FULLPATH)
                    || arg.equals(ARG_FULLPATH + "s")) { // allow "--fullpaths" too
                mFullPath = true;
            } else if (arg.equals(ARG_QUIET) || arg.equals("-q")) {
                mQuiet = true;
            } else if (arg.equals(ARG_NOLINES)) {
                mShowLines = false;
            } else if (arg.equals(ARG_URL)) {
                if (index == args.length - 1) {
                    System.err.println("Missing URL mapping string");
                    System.exit(ERRNO_INVALIDARGS);
                }
                String map = args[++index];
                // Allow repeated usage of the argument instead of just comma list
                if (urlMap != null) {
                    urlMap = urlMap + ',' + map;
                } else {
                    urlMap = map;
                }
            } else if (arg.equals(ARG_HTML) || arg.equals(ARG_SIMPLEHTML)) {
                if (index == args.length - 1) {
                    System.err.println("Missing HTML output file name");
                    System.exit(ERRNO_INVALIDARGS);
                }
                File output = new File(args[++index]);
                if (output.exists()) {
                    boolean delete = output.delete();
                    if (!delete) {
                        System.err.println("Could not delete old " + output);
                        System.exit(ERRNO_EXISTS);
                    }
                }
                if (output.canWrite()) {
                    System.err.println("Cannot write HTML output file " + output);
                    System.exit(ERRNO_EXISTS);
                }
                try {
                    HtmlReporter htmlReporter = new HtmlReporter(output);
                    if (arg.equals(ARG_SIMPLEHTML)) {
                        htmlReporter.setSimpleFormat(true);
                    }
                    mReporter = htmlReporter;
                } catch (IOException e) {
                    log(e, null);
                    System.exit(ERRNO_INVALIDARGS);
                }
            } else if (arg.equals(ARG_XML)) {
                if (index == args.length - 1) {
                    System.err.println("Missing XML output file name");
                    System.exit(ERRNO_INVALIDARGS);
                }
                File output = new File(args[++index]);
                if (output.exists()) {
                    boolean delete = output.delete();
                    if (!delete) {
                        System.err.println("Could not delete old " + output);
                        System.exit(ERRNO_EXISTS);
                    }
                }
                if (output.canWrite()) {
                    System.err.println("Cannot write XML output file " + output);
                    System.exit(ERRNO_EXISTS);
                }
                try {
                    mReporter = new XmlReporter(output);
                } catch (IOException e) {
                    log(e, null);
                    System.exit(ERRNO_INVALIDARGS);
                }
            } else if (arg.equals(ARG_DISABLE) || arg.equals(ARG_IGNORE)) {
                if (index == args.length - 1) {
                    System.err.println("Missing categories or id's to disable");
                    System.exit(ERRNO_INVALIDARGS);
                }
                String[] ids = args[++index].split(",");
                for (String id : ids) {
                    if (registry.isCategoryName(id)) {
                        // Suppress all issues with the given category
                        String category = id;
                        for (Issue issue : registry.getIssues()) {
                            // Check prefix such that filtering on the "Usability" category
                            // will match issue category "Usability:Icons" etc.
                            if (issue.getCategory().getName().startsWith(category) ||
                                    issue.getCategory().getFullName().startsWith(category)) {
                                mSuppress.add(issue.getId());
                            }
                        }
                    } else if (!registry.isIssueId(id)) {
                        System.err.println("Invalid id or category \"" + id + "\".\n");
                        displayValidIds(registry, System.err);
                        System.exit(ERRNO_INVALIDARGS);
                    } else {
                        mSuppress.add(id);
                    }
                }
            } else if (arg.equals(ARG_ENABLE)) {
                if (index == args.length - 1) {
                    System.err.println("Missing categories or id's to enable");
                    System.exit(ERRNO_INVALIDARGS);
                }
                String[] ids = args[++index].split(",");
                for (String id : ids) {
                    if (registry.isCategoryName(id)) {
                        // Enable all issues with the given category
                        String category = id;
                        for (Issue issue : registry.getIssues()) {
                            if (issue.getCategory().getName().startsWith(category) ||
                                    issue.getCategory().getFullName().startsWith(category)) {
                                mEnabled.add(issue.getId());
                            }
                        }
                    } else if (!registry.isIssueId(id)) {
                        System.err.println("Invalid id or category \"" + id + "\".\n");
                        displayValidIds(registry, System.err);
                        System.exit(ERRNO_INVALIDARGS);
                    } else {
                        mEnabled.add(id);
                    }
                }
            } else if (arg.equals(ARG_CHECK)) {
                if (index == args.length - 1) {
                    System.err.println("Missing categories or id's to check");
                    System.exit(ERRNO_INVALIDARGS);
                }
                mCheck = new HashSet<String>();
                String[] ids = args[++index].split(",");
                for (String id : ids) {
                    if (registry.isCategoryName(id)) {
                        // Check all issues with the given category
                        String category = id;
                        for (Issue issue : registry.getIssues()) {
                            // Check prefix such that filtering on the "Usability" category
                            // will match issue category "Usability:Icons" etc.
                            if (issue.getCategory().getName().startsWith(category) ||
                                    issue.getCategory().getFullName().startsWith(category)) {
                                mCheck.add(issue.getId());
                            }
                        }
                    } else if (!registry.isIssueId(id)) {
                        System.err.println("Invalid id or category \"" + id + "\".\n");
                        displayValidIds(registry, System.err);
                        System.exit(ERRNO_INVALIDARGS);
                    } else {
                        mCheck.add(id);
                    }
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

        if (mReporter == null) {
            if (urlMap != null) {
                System.err.println(String.format(
                        "Warning: The %1$s option only applies to HTML reports (%2$s)",
                            ARG_URL, ARG_HTML));
            }

            mReporter = new TextReporter(new PrintWriter(System.out, true));
        } else if (mReporter instanceof HtmlReporter) {
            HtmlReporter htmlReporter = (HtmlReporter) mReporter;

            if (urlMap == null) {
                // By default just map from /foo to file:///foo
                // TODO: Find out if we need file:// on Windows.
                urlMap = "=file://"; //$NON-NLS-1$
                if (!htmlReporter.isSimpleFormat()) {
                    htmlReporter.setBundleResources(true);
                }
            }
            Map<String, String> map = new HashMap<String, String>();
            String[] replace = urlMap.split(","); //$NON-NLS-1$
            for (String s : replace) {
                String[] v = s.split("="); //$NON-NLS-1$
                if (v.length != 2) {
                    System.err.println(
                            "The URL map argument must be of the form 'path_prefix=url_prefix'");
                    System.exit(ERRNO_INVALIDARGS);
                }
                map.put(v[0], v[1]);
            }
            htmlReporter.setUrlMap(map);
        }

        Lint analyzer = new Lint(registry, this);

        if (!mQuiet) {
            analyzer.addLintListener(new ProgressPrinter());
        }

        analyzer.analyze(files, null /* scope */);

        Collections.sort(mWarnings);

        try {
            mReporter.write(mErrorCount, mWarningCount, mWarnings);
        } catch (IOException e) {
            log(e, null);
            System.exit(ERRNO_INVALIDARGS);
        }

        System.exit(mFatal ? ERRNO_ERRORS : 0);
    }

    private void displayValidIds(IssueRegistry registry, PrintStream out) {
        List<Category> categories = registry.getCategories();
        out.println("Valid issue categories:");
        for (Category category : categories) {
            out.println("    " + category.getFullName());
        }
        out.println();
        List<Issue> issues = registry.getIssues();
        out.println("Valid issue id's:");
        for (Issue issue : issues) {
            listIssue(out, issue);
        }
    }

    private void listIssue(PrintStream out, Issue issue) {
        out.print(wrapArg("\"" + issue.getId() + "\": " + issue.getDescription()));
    }

    private void showIssues(IssueRegistry registry) {
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

        System.out.println("Available issues:\n");
        Category previousCategory = null;
        for (Issue issue : sorted) {
            Category category = issue.getCategory();
            if (!category.equals(previousCategory)) {
                String name = category.getFullName();
                System.out.println(name);
                for (int i = 0, n = name.length(); i < n; i++) {
                    System.out.print('=');
                }
                System.out.println('\n');
                previousCategory = category;
            }

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
        System.out.println(wrap("Summary: " + issue.getDescription()));
        System.out.println("Priority: " + issue.getPriority() + " / 10");
        System.out.println("Severity: " + issue.getDefaultSeverity().getDescription());
        System.out.println("Category: " + issue.getCategory().getFullName());

        if (!issue.isEnabledByDefault()) {
            System.out.println("NOTE: This issue is disabled by default!");
            System.out.println(String.format("You can enable it by adding %1$s %2$s", ARG_ENABLE,
                    issue.getId()));
        }

        if (issue.getExplanation() != null) {
            System.out.println();
            System.out.println(wrap(issue.getExplanation()));
        }
        if (issue.getMoreInfo() != null) {
            System.out.println("\nMore information: " + issue.getMoreInfo());
        }
    }

    static String wrapArg(String explanation) {
        // Wrap arguments such that the wrapped lines are not showing up in the left column
        return wrap(explanation, MAX_LINE_WIDTH, "      ");
    }


    static String wrap(String explanation) {
        return wrap(explanation, MAX_LINE_WIDTH, "");
    }

    static String wrap(String explanation, int lineWidth, String hangingIndent) {
        int explanationLength = explanation.length();
        StringBuilder sb = new StringBuilder(explanationLength * 2);
        int index = 0;

        while (index < explanationLength) {
            int lineEnd = explanation.indexOf('\n', index);
            int next;

            if (lineEnd != -1 && (lineEnd - index) < lineWidth) {
                next = lineEnd + 1;
            } else {
                // Line is longer than available width; grab as much as we can
                lineEnd = Math.min(index + lineWidth, explanationLength);
                if (lineEnd - index < lineWidth) {
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

            if (sb.length() > 0) {
                sb.append(hangingIndent);
            } else {
                lineWidth -= hangingIndent.length();
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
        out.print(wrapArg(ARG_HELP + ": This message."));
        out.print(wrapArg(ARG_DISABLE + " <list>: Disable the list of categories or " +
            "specific issue id's. The list should be a comma-separated list of issue " +
            "id's or categories."));
        out.print(wrapArg(ARG_ENABLE + " <list>: Enable the specific list of issues. " +
            "This checks all the default issues plus the specifically enabled issues. The " +
            "list should be a comma-separated list of issue id's or categories."));
        out.print(wrapArg(ARG_CHECK + " <list>: Only check the specific list of issues. " +
            "This will disable everything and re-enable the given list of issues. " +
            "The list should be a comma-separated list of issue id's or categories."));
        out.print(wrapArg(ARG_FULLPATH + " : Use full paths in the error output."));
        out.print(wrapArg(ARG_NOLINES + " : Do not include the source file lines with errors " +
            "in the output. By default, the error output includes snippets of source code " +
            "on the line containing the error, but this flag turns it off."));
        out.print(wrapArg(ARG_HTML + " <filename>: Create an HTML report instead."));
        out.print(wrapArg(ARG_URL + " filepath=url: Add links to HTML report, replacing local " +
            "path prefixes with url prefix. The mapping can be a comma-separated list of " +
            "path prefixes to corresponding URL prefixes, such as " +
            "C:\\temp\\Proj1=http://buildserver/sources/temp/Proj1"));
        out.print(wrapArg(ARG_SIMPLEHTML + " <filename>: Create a simple HTML report"));
        out.print(wrapArg(ARG_XML + " <filename>: Create an XML report instead."));
        out.println();
        out.print(wrapArg(ARG_LISTIDS + ": List the available issue id's and exit."));
        out.print(wrapArg(ARG_SHOW + ": List available issues along with full explanations."));
        out.print(wrapArg(ARG_SHOW + " <ids>: Show full explanations for the given list of issue id's."));
        out.print(wrapArg(ARG_QUIET + ": Don't show progress."));
    }

    @Override
    public void log(Throwable exception, String format, Object... args) {
        if (format != null) {
            System.err.println(String.format(format, args));
        }
        if (exception != null) {
            exception.printStackTrace();
        }
    }

    @Override
    public IDomParser getParser() {
        return new PositionXmlParser();
    }

    @Override
    public Configuration getConfiguration(Project project) {
        return new CliConfiguration(null, project);
    }

    @Override
    public void report(Context context, Issue issue, Location location, String message,
            Object data) {
        assert context.configuration.isEnabled(issue);

        Severity severity = context.configuration.getSeverity(issue);
        if (severity == Severity.IGNORE) {
            return;
        }
        if (severity == Severity.ERROR){
            mFatal = true;
            mErrorCount++;
        } else {
            mWarningCount++;
        }

        Warning warning = new Warning(issue, message, severity, data);
        mWarnings.add(warning);

        if (location != null) {
            warning.location = location;
            File file = location.getFile();
            if (file != null) {
                warning.file = file;

                String path = file.getPath();
                if (!mFullPath && path.startsWith(context.project.getReferenceDir().getPath())) {
                    int chop = context.project.getReferenceDir().getPath().length();
                    if (path.length() > chop && path.charAt(chop) == File.separatorChar) {
                        chop++;
                    }
                    path = path.substring(chop);
                }
                warning.path = path;
            }

            Position startPosition = location.getStart();
            if (startPosition != null) {
                int line = startPosition.getLine();
                warning.line = line;
                warning.offset = startPosition.getOffset();
                if (line >= 0) {
                    warning.fileContents = context.client.readFile(location.getFile());

                    if (mShowLines) {
                        // Compute error line contents
                        warning.errorLine = getLine(context.getContents(), line);
                        if (warning.errorLine != null) {
                            // Replace tabs with spaces such that the column
                            // marker (^) lines up properly:
                            warning.errorLine = warning.errorLine.replace('\t', ' ');
                            int column = startPosition.getColumn();
                            if (column < 0) {
                                column = 0;
                                for (int i = 0; i < warning.errorLine.length(); i++, column++) {
                                    if (!Character.isWhitespace(warning.errorLine.charAt(i))) {
                                        break;
                                    }
                                }
                            }
                            StringBuilder sb = new StringBuilder();
                            sb.append(warning.errorLine);
                            sb.append('\n');
                            for (int i = 0; i < column - 1; i++) {
                                sb.append(' ');
                            }
                            sb.append('^');
                            sb.append('\n');
                            warning.errorLine = sb.toString();
                        }
                    }
                }
            }
        }
    }

    /** Look up the contents of the given line */
    static String getLine(String contents, int line) {
        int index = getLineOffset(contents, line);
        if (index != -1) {
            return getLineOfOffset(contents, index);
        } else {
            return null;
        }
    }

    static String getLineOfOffset(String contents, int offset) {
        int end = contents.indexOf('\n', offset);
        return contents.substring(offset, end != -1 ? end : contents.length());
    }


    /** Look up the contents of the given line */
    static int getLineOffset(String contents, int line) {
        int index = 0;
        for (int i = 0; i < line; i++) {
            index = contents.indexOf('\n', index);
            if (index == -1) {
                return -1;
            }
            index++;
        }

        return index;
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

    /**
     * Consult the lint.xml file, but override with the --enable and --disable
     * flags supplied on the command line
     */
    private class CliConfiguration extends DefaultConfiguration {
        CliConfiguration(Configuration parent, Project project) {
            super(Main.this, project, parent);
        }

        @Override
        public Severity getSeverity(Issue issue) {
            Severity severity = super.getSeverity(issue);

            String id = issue.getId();
            if (mSuppress.contains(id)) {
                return Severity.IGNORE;
            }

            if (mEnabled.contains(id) || (mCheck != null && mCheck.contains(id))) {
                // Overriding default
                // Detectors shouldn't be returning ignore as a default severity,
                // but in case they do, force it up to warning here to ensure that
                // it's run
                if (severity == Severity.IGNORE) {
                    return Severity.WARNING;
                } else {
                    return severity;
                }
            }

            if (mCheck != null) {
                return Severity.IGNORE;
            }

            return severity;
        }
    }

    private class ProgressPrinter implements LintListener {
        public void update(EventType type, Context context) {
            switch (type) {
                case SCANNING_PROJECT:
                    System.out.print(String.format(
                            "\nScanning %1$s: ",
                            context.project.getDir().getName()));
                    break;
                case SCANNING_FILE:
                    System.out.print('.');
                    break;
                case CANCELED:
                case COMPLETED:
                    System.out.println();
                    break;
            }
        }
    }
}
