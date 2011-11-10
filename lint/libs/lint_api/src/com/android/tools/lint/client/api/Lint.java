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

package com.android.tools.lint.client.api;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.LintListener.EventType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Analyzes Android projects and files */
public class Lint {
    private static final String PROGUARD_CFG = "proguard.cfg";                       //$NON-NLS-1$
    private static final String ANDROID_MANIFEST_XML = "AndroidManifest.xml";        //$NON-NLS-1$
    private static final String RES_FOLDER_NAME = "res";                             //$NON-NLS-1$

    private final LintClient mClient;
    private volatile boolean mCanceled;
    private IssueRegistry mRegistry;
    private EnumSet<Scope> mScope;
    private List<? extends Detector> mApplicableDetectors;
    private Map<Scope, List<Detector>> mScopeDetectors;
    private List<LintListener> mListeners;

    /**
     * Creates a new {@link Lint}
     *
     * @param registry The registry containing issues to be checked
     * @param client the tool wrapping the analyzer, such as an IDE or a CLI
     */
    public Lint(IssueRegistry registry, LintClient client) {
        assert client != null;
        mRegistry = registry;
        mClient = new LintClientWrapper(client);
    }

    /** Cancels the current lint run as soon as possible */
    public void cancel() {
        mCanceled = true;
    }

    /**
     * Analyze the given file (which can point to an Android project). Issues found
     * are reported to the associated {@link LintClient}.
     *
     * @param files the files and directories to be analyzed
     * @param scope the scope of the analysis; detectors with a wider scope will
     *            not be run. If null, the scope will be inferred from the files.
     */
    public void analyze(List<File> files, EnumSet<Scope> scope) {
        mCanceled = false;
        mScope = scope;

        Collection<Project> projects = computeProjects(files);
        if (projects.size() == 0) {
            mClient.log(null, "No projects found for %1$s", files.toString());
            return;
        }
        if (mCanceled) {
            return;
        }

        if (mScope == null) {
            // Infer the scope
            mScope = EnumSet.noneOf(Scope.class);
            for (Project project : projects) {
                if (project.getSubset() != null) {
                    for (File file : project.getSubset()) {
                        String name = file.getName();
                        if (name.equals(ANDROID_MANIFEST_XML)) {
                            mScope.add(Scope.MANIFEST);
                        } else if (name.endsWith(".xml")) {
                            mScope.add(Scope.RESOURCE_FILE);
                        } else if (name.equals(PROGUARD_CFG)) {
                            mScope.add(Scope.PROGUARD);
                        } else if (name.equals(RES_FOLDER_NAME)
                                || file.getParent().equals(RES_FOLDER_NAME)) {
                            mScope.add(Scope.ALL_RESOURCE_FILES);
                            mScope.add(Scope.RESOURCE_FILE);
                        } else if (name.endsWith(".java")) {
                            mScope.add(Scope.JAVA_FILE);
                        } else if (name.endsWith(".class")) {
                            mScope.add(Scope.CLASS_FILE);
                        }
                    }
                } else {
                    // Specified a full project: just use the full project scope
                    mScope = Scope.ALL;
                    break;
                }
            }
        }

        fireEvent(EventType.STARTING, null);

        for (Project project : projects) {
            // The set of available detectors varies between projects
            computeDetectors(project);

            checkProject(project);
            if (mCanceled) {
                break;
            }
        }

        fireEvent(mCanceled ? EventType.CANCELED : EventType.COMPLETED, null);
    }

    private void computeDetectors(Project project) {
        // Ensure that the current visitor is recomputed
        mCurrentFolderType = null;
        mCurrentVisitor = null;

        Configuration configuration = project.getConfiguration();
        mScopeDetectors = new HashMap<Scope, List<Detector>>();
        mApplicableDetectors = mRegistry.createDetectors(mClient, configuration,
                mScope, mScopeDetectors);

        validateScopeList();
    }

    /** Development diagnostics only, run with assertions on */
    @SuppressWarnings("all") // Turn off warnings for the intentional assertion side effect below
    private void validateScopeList() {
        boolean assertionsEnabled = false;
        assert assertionsEnabled = true; // Intentional side-effect
        if (assertionsEnabled) {
            List<Detector> resourceFileDetectors = mScopeDetectors.get(Scope.RESOURCE_FILE);
            if (resourceFileDetectors != null) {
                for (Detector detector : resourceFileDetectors) {
                    assert detector instanceof ResourceXmlDetector : detector;
                }
            }

            List<Detector> manifestDetectors = mScopeDetectors.get(Scope.MANIFEST);
            if (manifestDetectors != null) {
                for (Detector detector : manifestDetectors) {
                    assert detector instanceof Detector.XmlScanner : detector;
                }
            }
            List<Detector> javaCodeDetectors = mScopeDetectors.get(Scope.ALL_JAVA_FILES);
            if (javaCodeDetectors != null) {
                for (Detector detector : javaCodeDetectors) {
                    assert detector instanceof Detector.JavaScanner : detector;
                }
            }
            List<Detector> javaFileDetectors = mScopeDetectors.get(Scope.JAVA_FILE);
            if (javaFileDetectors != null) {
                for (Detector detector : javaFileDetectors) {
                    assert detector instanceof Detector.JavaScanner : detector;
                }
            }

            List<Detector> classDetectors = mScopeDetectors.get(Scope.CLASS_FILE);
            if (classDetectors != null) {
                for (Detector detector : classDetectors) {
                    assert detector instanceof Detector.ClassScanner : detector;
                }
            }
        }
    }

    private void registerProjectFile(Map<File, Project> fileToProject, File file,
            File projectDir, File rootDir) {
        Project project = fileToProject.get(projectDir);
        if (project == null) {
            project = new Project(mClient, projectDir, rootDir);
            project.setConfiguration(mClient.getConfiguration(project));
        }
        fileToProject.put(file, project);
    }

    private Collection<Project> computeProjects(List<File> files) {
        // Compute list of projects
        Map<File, Project> fileToProject = new HashMap<File, Project>();

        for (File file : files) {
            if (file.isDirectory()) {
                // Figure out what to do with a directory. Note that the meaning of the
                // directory can be ambiguous:
                // If you pass a directory which is unknown, we don't know if we should
                // search upwards (in case you're pointing at a deep java package folder
                // within the project), or if you're pointing at some top level directory
                // containing lots of projects you want to scan. We attempt to do the
                // right thing, which is to see if you're pointing right at a project or
                // right within it (say at the src/ or res/) folder, and if not, you're
                // hopefully pointing at a project tree that you want to scan recursively.
                if (isProjectDir(file)) {
                    registerProjectFile(fileToProject, file, file, file);
                    continue;
                } else {
                    File parent = file.getParentFile();
                    if (parent != null) {
                        if (isProjectDir(parent)) {
                            registerProjectFile(fileToProject, file, parent, parent);
                            continue;
                        } else {
                            parent = parent.getParentFile();
                            if (isProjectDir(parent)) {
                                registerProjectFile(fileToProject, file, parent, parent);
                                continue;
                            }
                        }
                    }

                    // Search downwards for nested projects
                    addProjects(file, fileToProject, file);
                }
            } else {
                // Pointed at a file: Search upwards for the containing project
                File parent = file.getParentFile();
                while (parent != null) {
                    if (isProjectDir(parent)) {
                        registerProjectFile(fileToProject, file, parent, parent);
                        break;
                    }
                    parent = parent.getParentFile();
                }
            }

            if (mCanceled) {
                return Collections.emptySet();
            }
        }

        for (Map.Entry<File, Project> entry : fileToProject.entrySet()) {
            File file = entry.getKey();
            Project project = entry.getValue();
            if (!file.equals(project.getDir())) {
                project.addFile(file);
            }
        }

        return fileToProject.values();
    }

    private void addProjects(File dir, Map<File, Project> fileToProject, File rootDir) {
        if (mCanceled) {
            return;
        }

        if (isProjectDir(dir)) {
            registerProjectFile(fileToProject, dir, dir, rootDir);
        } else {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        addProjects(file, fileToProject, rootDir);
                    }
                }
            }
        }
    }

    private boolean isProjectDir(File dir) {
        return new File(dir, ANDROID_MANIFEST_XML).exists();
    }

    private void checkProject(Project project) {

        File projectDir = project.getDir();

        Context projectContext = new Context(mClient, project, projectDir, mScope);
        fireEvent(EventType.SCANNING_PROJECT, projectContext);

        for (Detector check : mApplicableDetectors) {
            check.beforeCheckProject(projectContext);
            if (mCanceled) {
                return;
            }
        }

        runFileDetectors(project, projectDir);

        for (Detector check : mApplicableDetectors) {
            check.afterCheckProject(projectContext);
            if (mCanceled) {
                return;
            }
        }

        if (mCanceled) {
            mClient.report(
                projectContext,
                // Must provide an issue since API guarantees that the issue parameter
                // is valid
                Issue.create("Lint", "", "", Category.PERFORMANCE, 0, Severity.INFORMATIONAL, //$NON-NLS-1$
                        null, EnumSet.noneOf(Scope.class)),
                null /*range*/,
                "Lint canceled by user", null);
        }
    }

    private void runFileDetectors(Project project, File projectDir) {
        // Look up manifest information
        if (mScope.contains(Scope.MANIFEST)) {
            List<Detector> detectors = mScopeDetectors.get(Scope.MANIFEST);
            if (detectors != null) {
                File file = new File(project.getDir(), ANDROID_MANIFEST_XML);
                if (file.exists()) {
                    Context context = new Context(mClient, project, file, mScope);
                    context.location = new Location(file, null, null);
                    XmlVisitor v = new XmlVisitor(mClient.getParser(), detectors);
                    fireEvent(EventType.SCANNING_FILE, context);
                    v.visitFile(context, file);
                }
            }
        }

        // Process both Scope.RESOURCE_FILE and Scope.ALL_RESOURCE_FILES detectors together
        // in a single pass through the resource directories.
        if (mScope.contains(Scope.ALL_RESOURCE_FILES) || mScope.contains(Scope.RESOURCE_FILE)) {
            List<Detector> checks = union(mScopeDetectors.get(Scope.RESOURCE_FILE),
                    mScopeDetectors.get(Scope.ALL_RESOURCE_FILES));
            if (checks.size() > 0) {
                List<ResourceXmlDetector> xmlDetectors =
                        new ArrayList<ResourceXmlDetector>(checks.size());
                for (Detector detector : checks) {
                    if (detector instanceof ResourceXmlDetector) {
                        xmlDetectors.add((ResourceXmlDetector) detector);
                    }
                }
                if (xmlDetectors.size() > 0) {
                    if (project.getSubset() != null) {
                        checkIndividualResources(project, xmlDetectors, project.getSubset());
                    } else {
                        File res = new File(projectDir, RES_FOLDER_NAME);
                        if (res.exists() && xmlDetectors.size() > 0) {
                            checkResFolder(project, res, xmlDetectors);
                        }
                    }
                }
            }
        }

        if (mCanceled) {
            return;
        }

        if (mScope.contains(Scope.JAVA_FILE) || mScope.contains(Scope.ALL_JAVA_FILES)) {
            List<Detector> checks = union(mScopeDetectors.get(Scope.JAVA_FILE),
                    mScopeDetectors.get(Scope.ALL_JAVA_FILES));
            if (checks.size() > 0) {
                List<File> sourceFolders = project.getJavaSourceFolders();
                checkJava(project, sourceFolders, checks);
            }
        }

        if (mCanceled) {
            return;
        }

        if (mScope.contains(Scope.CLASS_FILE)) {
            List<Detector> detectors = mScopeDetectors.get(Scope.CLASS_FILE);
            if (detectors != null) {
                List<File> binFolders = project.getJavaClassFolders();
                checkClasses(project, binFolders, detectors);
            }
        }

        if (mCanceled) {
            return;
        }

        if (mScope.contains(Scope.PROGUARD)) {
            List<Detector> detectors = mScopeDetectors.get(Scope.PROGUARD);
            if (detectors != null) {
                File file = new File(project.getDir(), PROGUARD_CFG);
                if (file.exists()) {
                    Context context = new Context(mClient, project, file, mScope);
                    fireEvent(EventType.SCANNING_FILE, context);
                    context.location = new Location(file, null, null);
                    for (Detector detector : detectors) {
                        if (detector.appliesTo(context, file)) {
                            detector.beforeCheckFile(context);
                            detector.run(context);
                            detector.afterCheckFile(context);
                        }
                    }
                }
            }
        }
    }

    private static List<Detector> union(List<Detector> list1, List<Detector> list2) {
        int size = (list1 != null ? list1.size() : 0) + (list2 != null ? list2.size() : 0);
        // Use set to pick out unique detectors, since it's possible for there to be overlap,
        // e.g. the DuplicateIdDetector registers both a cross-resource issue and a
        // single-file issue, so it shows up on both scope lists:
        Set<Detector> set = new HashSet<Detector>(size);
        if (list1 != null) {
            set.addAll(list1);
        }
        if (list2 != null) {
            set.addAll(list2);
        }

        return new ArrayList<Detector>(set);
    }

    private void checkClasses(Project project, List<File> binFolders, List<Detector> checks) {
        Context context = new Context(mClient, project, project.getDir(), mScope);
        fireEvent(EventType.SCANNING_FILE, context);
        for (Detector detector : checks) {
            ((Detector.ClassScanner) detector).checkJavaClasses(context);

            if (mCanceled) {
                return;
            }
        }
    }

    private void checkJava(Project project, List<File> sourceFolders, List<Detector> checks) {
        Context context = new Context(mClient, project, project.getDir(), mScope);
        fireEvent(EventType.SCANNING_FILE, context);

        for (Detector detector : checks) {
            ((Detector.JavaScanner) detector).checkJavaSources(context, sourceFolders);

            if (mCanceled) {
                return;
            }
        }
    }

    private ResourceFolderType mCurrentFolderType;
    private List<ResourceXmlDetector> mCurrentXmlDetectors;
    private XmlVisitor mCurrentVisitor;

    private XmlVisitor getVisitor(ResourceFolderType type, List<ResourceXmlDetector> checks) {
        if (type != mCurrentFolderType) {
            mCurrentFolderType = type;

            // Determine which XML resource detectors apply to the given folder type
            List<ResourceXmlDetector> applicableChecks =
                    new ArrayList<ResourceXmlDetector>(checks.size());
            for (ResourceXmlDetector check : checks) {
                if (check.appliesTo(type)) {
                    applicableChecks.add(check);
                }
            }

            // If the list of detectors hasn't changed, then just use the current visitor!
            if (mCurrentXmlDetectors != null && mCurrentXmlDetectors.equals(applicableChecks)) {
                return mCurrentVisitor;
            }

            if (applicableChecks.size() == 0) {
                mCurrentVisitor = null;
                return null;
            }

            mCurrentVisitor = new XmlVisitor(mClient.getParser(), applicableChecks);
        }

        return mCurrentVisitor;
    }

    private void checkResFolder(Project project, File res, List<ResourceXmlDetector> checks) {
        assert res.isDirectory();
        File[] resourceDirs = res.listFiles();
        if (resourceDirs == null) {
            return;
        }

        // Sort alphabetically such that we can process related folder types at the
        // same time

        Arrays.sort(resourceDirs);
        ResourceFolderType type = null;
        for (File dir : resourceDirs) {
            type = ResourceFolderType.getFolderType(dir.getName());
            if (type != null) {
                checkResourceFolder(project, dir, type, checks);
            }

            if (mCanceled) {
                return;
            }
        }
    }

    private void checkResourceFolder(Project project, File dir, ResourceFolderType type,
            List<ResourceXmlDetector> checks) {
        // Process the resource folder
        File[] xmlFiles = dir.listFiles();
        if (xmlFiles != null && xmlFiles.length > 0) {
            XmlVisitor visitor = getVisitor(type, checks);
            if (visitor != null) { // if not, there are no applicable rules in this folder
                for (File file : xmlFiles) {
                    if (LintUtils.isXmlFile(file)) {
                        Context context = new Context(mClient, project, file,
                                mScope);
                        fireEvent(EventType.SCANNING_FILE, context);
                        visitor.visitFile(context, file);
                        if (mCanceled) {
                            return;
                        }
                    }
                }
            }
        }
    }

    /** Checks individual resources */
    private void checkIndividualResources(Project project,
            List<ResourceXmlDetector> xmlDetectors, List<File> files) {
        for (File file : files) {
            if (file.isDirectory()) {
                // Is it a resource folder?
                ResourceFolderType type = ResourceFolderType.getFolderType(file.getName());
                if (type != null && new File(file.getParentFile(), RES_FOLDER_NAME).exists()) {
                    // Yes.
                    checkResourceFolder(project, file, type, xmlDetectors);
                } else if (file.getName().equals(RES_FOLDER_NAME)) { // Is it the res folder?
                    // Yes
                    checkResFolder(project, file, xmlDetectors);
                } else {
                    mClient.log(null, "Unexpected folder %1$s; should be project, " +
                            "\"res\" folder or resource folder", file.getPath());
                    continue;
                }
            } else if (file.isFile() && LintUtils.isXmlFile(file)) {
                // Yes, find out its resource type
                String folderName = file.getParentFile().getName();
                ResourceFolderType type = ResourceFolderType.getFolderType(folderName);
                if (type != null) {
                    XmlVisitor visitor = getVisitor(type, xmlDetectors);
                    if (visitor != null) {
                        Context context = new Context(mClient, project, file, mScope);
                        fireEvent(EventType.SCANNING_FILE, context);
                        visitor.visitFile(context, file);
                    }
                }
            }
        }
    }

    /**
     * Adds a listener to be notified of lint progress
     *
     * @param listener the listener to be added
     */
    public void addLintListener(LintListener listener) {
        if (mListeners == null) {
            mListeners = new ArrayList<LintListener>(1);
        }
        mListeners.add(listener);
    }

    /**
     * Removes a listener such that it is no longer notified of progress
     *
     * @param listener the listener to be removed
     */
    public void removeLintListener(LintListener listener) {
        mListeners.remove(listener);
        if (mListeners.size() == 0) {
            mListeners = null;
        }
    }

    /** Notifies listeners, if any, that the given event has occurred */
    private void fireEvent(LintListener.EventType type, Context context) {
        if (mListeners != null) {
            for (int i = 0, n = mListeners.size(); i < n; i++) {
                LintListener listener = mListeners.get(i);
                listener.update(type, context);
            }
        }
    }

    /**
     * Wrapper around the lint client. This sits in the middle between a
     * detector calling for example
     * {@link LintClient#report(Context, Issue, Location, String, Object)} and
     * the actual embedding tool, and performs filtering etc such that detectors
     * and lint clients don't have to make sure they check for ignored issues or
     * filtered out warnings.
     */
    private static class LintClientWrapper extends LintClient {
        private LintClient mDelegate;

        public LintClientWrapper(LintClient delegate) {
            mDelegate = delegate;
        }

        @Override
        public void report(Context context, Issue issue, Location location, String message,
                Object data) {
            Configuration configuration = context.configuration;
            if (!configuration.isEnabled(issue)) {
                mDelegate.log(null, "Incorrect detector reported disabled issue %1$s",
                        issue.toString());
                return;
            }

            if (configuration.isIgnored(context, issue, location, message, data)) {
                return;
            }

            Severity severity = configuration.getSeverity(issue);
            if (severity == Severity.IGNORE) {
                return;
            }

            mDelegate.report(context, issue, location, message, data);
        }

        // Everything else just delegates to the embedding lint client

        @Override
        public Configuration getConfiguration(Project project) {
            return mDelegate.getConfiguration(project);
        }


        @Override
        public void log(Throwable exception, String format, Object... args) {
            mDelegate.log(exception, format, args);
        }

        @Override
        public IDomParser getParser() {
            return mDelegate.getParser();
        }

        @Override
        public String readFile(File file) {
            return mDelegate.readFile(file);
        }

        @Override
        public List<File> getJavaSourceFolders(Project project) {
            return mDelegate.getJavaSourceFolders(project);
        }

        @Override
        public List<File> getJavaClassFolders(Project project) {
            return mDelegate.getJavaClassFolders(project);
        }
    }
}
