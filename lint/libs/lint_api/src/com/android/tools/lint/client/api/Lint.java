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

import static com.android.tools.lint.detector.api.LintConstants.ANDROID_MANIFEST_XML;
import static com.android.tools.lint.detector.api.LintConstants.DOT_CLASS;
import static com.android.tools.lint.detector.api.LintConstants.DOT_JAVA;
import static com.android.tools.lint.detector.api.LintConstants.PROGUARD_CFG;
import static com.android.tools.lint.detector.api.LintConstants.RES_FOLDER;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.LintListener.EventType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.google.common.annotations.Beta;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.Files;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Analyzes Android projects and files
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
@Beta
public class Lint {
    /**
     * Max number of passes to run through the lint runner if requested by
     * {@link #requestRepeat}
     */
    private static final int MAX_PHASES = 3;

    private final LintClient mClient;
    private volatile boolean mCanceled;
    private IssueRegistry mRegistry;
    private EnumSet<Scope> mScope;
    private List<? extends Detector> mApplicableDetectors;
    private Map<Scope, List<Detector>> mScopeDetectors;
    private List<LintListener> mListeners;
    private int mPhase;
    private List<Detector> mRepeatingDetectors;
    private EnumSet<Scope> mRepeatScope;

    /**
     * Creates a new {@link Lint}
     *
     * @param registry The registry containing issues to be checked
     * @param client the tool wrapping the analyzer, such as an IDE or a CLI
     */
    public Lint(@NonNull IssueRegistry registry, @NonNull LintClient client) {
        mRegistry = registry;
        mClient = new LintClientWrapper(client);
    }

    /** Cancels the current lint run as soon as possible */
    public void cancel() {
        mCanceled = true;
    }

    /**
     * Returns the scope for the lint job
     *
     * @return the scope, never null
     */
    @NonNull
    public EnumSet<Scope> getScope() {
        return mScope;
    }

    /**
     * Returns the lint client requesting the lint check
     *
     * @return the client, never null
     */
    @NonNull
    public LintClient getClient() {
        return mClient;
    }

    /**
     * Returns the current phase number. The first pass is numbered 1. Only one pass
     * will be performed, unless a {@link Detector} calls {@link #requestRepeat}.
     *
     * @return the current phase, usually 1
     */
    public int getPhase() {
        return mPhase;
    }

    /**
     * Analyze the given file (which can point to an Android project). Issues found
     * are reported to the associated {@link LintClient}.
     *
     * @param files the files and directories to be analyzed
     * @param scope the scope of the analysis; detectors with a wider scope will
     *            not be run. If null, the scope will be inferred from the files.
     */
    public void analyze(@NonNull List<File> files, @Nullable EnumSet<Scope> scope) {
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
                            mScope.add(Scope.PROGUARD_FILE);
                        } else if (name.equals(RES_FOLDER)
                                || file.getParent().equals(RES_FOLDER)) {
                            mScope.add(Scope.ALL_RESOURCE_FILES);
                            mScope.add(Scope.RESOURCE_FILE);
                        } else if (name.endsWith(DOT_JAVA)) {
                            mScope.add(Scope.JAVA_FILE);
                        } else if (name.endsWith(DOT_CLASS)) {
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
            mPhase = 1;

            // The set of available detectors varies between projects
            computeDetectors(project);

            if (mApplicableDetectors.size() == 0) {
                // No detectors enabled in this project: skip it
                continue;
            }

            checkProject(project);
            if (mCanceled) {
                break;
            }

            runExtraPhases(project);
        }

        fireEvent(mCanceled ? EventType.CANCELED : EventType.COMPLETED, null);
    }

    private void runExtraPhases(Project project) {
        // Did any detectors request another phase?
        if (mRepeatingDetectors != null) {
            // Yes. Iterate up to MAX_PHASES times.

            // During the extra phases, we might be narrowing the scope, and setting it in the
            // scope field such that detectors asking about the available scope will get the
            // correct result. However, we need to restore it to the original scope when this
            // is done in case there are other projects that will be checked after this, since
            // the repeated phases is done *per project*, not after all projects have been
            // processed.
            EnumSet<Scope> oldScope = mScope;

            do {
                mPhase++;
                fireEvent(EventType.NEW_PHASE,
                        new Context(this, project, null, project.getDir()));

                // Narrow the scope down to the set of scopes requested by
                // the rules.
                if (mRepeatScope == null) {
                    mRepeatScope = Scope.ALL;
                }
                mScope = Scope.intersect(mScope, mRepeatScope);
                if (mScope.isEmpty()) {
                    break;
                }

                // Compute the detectors to use for this pass.
                // Unlike the normal computeDetectors(project) call,
                // this is going to use the existing instances, and include
                // those that apply for the configuration.
                computeRepeatingDetectors(mRepeatingDetectors, project);

                if (mApplicableDetectors.size() == 0) {
                    // No detectors enabled in this project: skip it
                    continue;
                }

                checkProject(project);
                if (mCanceled) {
                    break;
                }
            } while (mPhase < MAX_PHASES && mRepeatingDetectors != null);

            mScope = oldScope;
        }
    }

    private void computeRepeatingDetectors(List<Detector> detectors, Project project) {
        // Ensure that the current visitor is recomputed
        mCurrentFolderType = null;
        mCurrentVisitor = null;

        // Create map from detector class to issue such that we can
        // compute applicable issues for each detector in the list of detectors
        // to be repeated
        List<Issue> issues = mRegistry.getIssues();
        Multimap<Class<? extends Detector>, Issue> issueMap =
                ArrayListMultimap.create(issues.size(), 3);
        for (Issue issue : issues) {
            issueMap.put(issue.getDetectorClass(), issue);
        }

        Map<Class<? extends Detector>, EnumSet<Scope>> detectorToScope =
                new HashMap<Class<? extends Detector>, EnumSet<Scope>>();
        Map<Scope, List<Detector>> scopeToDetectors =
                new HashMap<Scope, List<Detector>>();

        List<Detector> detectorList = new ArrayList<Detector>();
        // Compute the list of detectors (narrowed down from mRepeatingDetectors),
        // and simultaneously build up the detectorToScope map which tracks
        // the scopes each detector is affected by (this is used to populate
        // the mScopeDetectors map which is used during iteration).
        Configuration configuration = project.getConfiguration();
        for (Detector detector : detectors) {
            Class<? extends Detector> detectorClass = detector.getClass();
            Collection<Issue> detectorIssues = issueMap.get(detectorClass);
            if (issues != null) {
                boolean add = false;
                for (Issue issue : detectorIssues) {
                    // The reason we have to check whether the detector is enabled
                    // is that this is a per-project property, so when running lint in multiple
                    // projects, a detector enabled only in a different project could have
                    // requested another phase, and we end up in this project checking whether
                    // the detector is enabled here.
                    if (!configuration.isEnabled(issue)) {
                        continue;
                    }

                    add = true; // Include detector if any of its issues are enabled

                    EnumSet<Scope> s = detectorToScope.get(detectorClass);
                    EnumSet<Scope> issueScope = issue.getScope();
                    if (s == null) {
                        detectorToScope.put(detectorClass, issueScope);
                    } else if (!s.containsAll(issueScope)) {
                        EnumSet<Scope> union = EnumSet.copyOf(s);
                        union.addAll(issueScope);
                        detectorToScope.put(detectorClass, union);
                    }
                }

                if (add) {
                    detectorList.add(detector);
                    EnumSet<Scope> union = detectorToScope.get(detector.getClass());
                    for (Scope s : union) {
                        List<Detector> list = scopeToDetectors.get(s);
                        if (list == null) {
                            list = new ArrayList<Detector>();
                            scopeToDetectors.put(s, list);
                        }
                        list.add(detector);
                    }
                }
            }
        }

        mApplicableDetectors = detectorList;
        mScopeDetectors = scopeToDetectors;
        mRepeatingDetectors = null;
        mRepeatScope = null;

        validateScopeList();
    }

    private void computeDetectors(@NonNull Project project) {
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

    private void registerProjectFile(
            @NonNull Map<File, Project> fileToProject,
            @NonNull File file,
            @NonNull File projectDir,
            @NonNull File rootDir) {
        fileToProject.put(file, mClient.getProject(projectDir, rootDir));
    }

    private Collection<Project> computeProjects(@NonNull List<File> files) {
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

        // Partition the projects up such that we only return projects that aren't
        // included by other projects (e.g. because they are library projects)

        Collection<Project> allProjects = fileToProject.values();
        Set<Project> roots = new HashSet<Project>(allProjects);
        for (Project project : allProjects) {
            roots.removeAll(project.getAllLibraries());
        }

        if (LintUtils.assertionsEnabled()) {
            // Make sure that all the project directories are unique. This ensures
            // that we didn't accidentally end up with different project instances
            // for a library project discovered as a directory as well as one
            // initialized from the library project dependency list
            IdentityHashMap<Project, Project> projects =
                    new IdentityHashMap<Project, Project>();
            for (Project project : roots) {
                projects.put(project, project);
                for (Project library : project.getAllLibraries()) {
                    projects.put(library, library);
                }
            }
            Set<File> dirs = new HashSet<File>();
            for (Project project : projects.keySet()) {
                assert !dirs.contains(project.getDir());
                dirs.add(project.getDir());
            }
        }

        return roots;
    }

    private void addProjects(
            @NonNull File dir,
            @NonNull Map<File, Project> fileToProject,
            @NonNull File rootDir) {
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

    private boolean isProjectDir(@NonNull File dir) {
        return new File(dir, ANDROID_MANIFEST_XML).exists();
    }

    private void checkProject(@NonNull Project project) {
        File projectDir = project.getDir();

        Context projectContext = new Context(this, project, null, projectDir);
        fireEvent(EventType.SCANNING_PROJECT, projectContext);

        for (Detector check : mApplicableDetectors) {
            check.beforeCheckProject(projectContext);
            if (mCanceled) {
                return;
            }
        }


        runFileDetectors(project, project);

        if (!Scope.checkSingleFile(mScope)) {
            List<Project> libraries = project.getDirectLibraries();
            for (Project library : libraries) {
                Context libraryContext = new Context(this, library, project, projectDir);
                fireEvent(EventType.SCANNING_LIBRARY_PROJECT, libraryContext);

                for (Detector check : mApplicableDetectors) {
                    check.beforeCheckLibraryProject(libraryContext);
                    if (mCanceled) {
                        return;
                    }
                }

                runFileDetectors(library, project);
                if (mCanceled) {
                    return;
                }

                for (Detector check : mApplicableDetectors) {
                    check.afterCheckLibraryProject(libraryContext);
                    if (mCanceled) {
                        return;
                    }
                }
            }
        }

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

    private void runFileDetectors(@NonNull Project project, @Nullable Project main) {
        // Look up manifest information (but not for library projects)
        File manifestFile = new File(project.getDir(), ANDROID_MANIFEST_XML);
        if (!project.isLibrary() && manifestFile.exists()) {
            XmlContext context = new XmlContext(this, project, main, manifestFile);
            IDomParser parser = mClient.getDomParser();
            context.document = parser.parseXml(context);
            if (context.document != null) {
                project.readManifest(context.document);

                if (mScope.contains(Scope.MANIFEST)) {
                    List<Detector> detectors = mScopeDetectors.get(Scope.MANIFEST);
                    if (detectors != null) {
                        XmlVisitor v = new XmlVisitor(parser, detectors);
                        fireEvent(EventType.SCANNING_FILE, context);
                        v.visitFile(context, manifestFile);
                    }
                }
            }
        }

        // Process both Scope.RESOURCE_FILE and Scope.ALL_RESOURCE_FILES detectors together
        // in a single pass through the resource directories.
        if (mScope.contains(Scope.ALL_RESOURCE_FILES) || mScope.contains(Scope.RESOURCE_FILE)) {
            List<Detector> checks = union(mScopeDetectors.get(Scope.RESOURCE_FILE),
                    mScopeDetectors.get(Scope.ALL_RESOURCE_FILES));
            if (checks != null && checks.size() > 0) {
                List<ResourceXmlDetector> xmlDetectors =
                        new ArrayList<ResourceXmlDetector>(checks.size());
                for (Detector detector : checks) {
                    if (detector instanceof ResourceXmlDetector) {
                        xmlDetectors.add((ResourceXmlDetector) detector);
                    }
                }
                if (xmlDetectors.size() > 0) {
                    if (project.getSubset() != null) {
                        checkIndividualResources(project, main, xmlDetectors,
                                project.getSubset());
                    } else {
                        File res = new File(project.getDir(), RES_FOLDER);
                        if (res.exists() && xmlDetectors.size() > 0) {
                            checkResFolder(project, main, res, xmlDetectors);
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
            if (checks != null && checks.size() > 0) {
                List<File> sourceFolders = project.getJavaSourceFolders();
                checkJava(project, main, sourceFolders, checks);
            }
        }

        if (mCanceled) {
            return;
        }

        if (mScope.contains(Scope.CLASS_FILE)) {
            List<Detector> detectors = mScopeDetectors.get(Scope.CLASS_FILE);
            if (detectors != null && detectors.size() > 0) {
                List<File> binFolders = project.getJavaClassFolders();
                checkClasses(project, main, binFolders, detectors);
            }
        }

        if (mCanceled) {
            return;
        }

        if (project == main && mScope.contains(Scope.PROGUARD_FILE)) {
            List<Detector> detectors = mScopeDetectors.get(Scope.PROGUARD_FILE);
            if (detectors != null) {
                File file = new File(project.getDir(), PROGUARD_CFG);
                if (file.exists()) {
                    Context context = new Context(this, project, main, file);
                    fireEvent(EventType.SCANNING_FILE, context);
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

    @Nullable
    private static List<Detector> union(
            @Nullable List<Detector> list1,
            @Nullable List<Detector> list2) {
        if (list1 == null) {
            return list2;
        } else if (list2 == null) {
            return list1;
        } else {
            // Use set to pick out unique detectors, since it's possible for there to be overlap,
            // e.g. the DuplicateIdDetector registers both a cross-resource issue and a
            // single-file issue, so it shows up on both scope lists:
            Set<Detector> set = new HashSet<Detector>(list1.size() + list2.size());
            if (list1 != null) {
                set.addAll(list1);
            }
            if (list2 != null) {
                set.addAll(list2);
            }

            return new ArrayList<Detector>(set);
        }
    }

    private void checkClasses(
            @NonNull Project project,
            @Nullable Project main,
            @NonNull List<File> binFolders,
            @NonNull List<Detector> checks) {
        if (binFolders.size() == 0) {
            //mClient.log(null, "Warning: Class-file checks are enabled, but no " +
            //        "output folders found. Does the project need to be built first?");
        }

        for (File binDir : binFolders) {
            List<File> classFiles = new ArrayList<File>();
            addClassFiles(binDir, classFiles);

            for (File file : classFiles) {
                try {
                    byte[] bytes = Files.toByteArray(file);
                    if (bytes != null) {
                        ClassReader reader = new ClassReader(bytes);
                        ClassNode classNode = new ClassNode();
                        reader.accept(classNode, 0 /*flags*/);
                        ClassContext context = new ClassContext(this, project, main, file,
                                binDir, bytes, classNode);
                        for (Detector detector : checks) {
                            if (detector.appliesTo(context, file)) {
                                fireEvent(EventType.SCANNING_FILE, context);
                                detector.beforeCheckFile(context);

                                Detector.ClassScanner scanner = (Detector.ClassScanner) detector;
                                scanner.checkClass(context, classNode);
                                detector.afterCheckFile(context);
                            }

                            if (mCanceled) {
                                return;
                            }
                        }
                    }
                } catch (IOException e) {
                    mClient.log(e, null);
                    continue;
                }

                if (mCanceled) {
                    return;
                }
            }
        }
    }

    private void addClassFiles(@NonNull File dir, @NonNull List<File> classFiles) {
        // Process the resource folder
        File[] files = dir.listFiles();
        if (files != null && files.length > 0) {
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(DOT_CLASS)) {
                    classFiles.add(file);
                } else if (file.isDirectory()) {
                    // Recurse
                    addClassFiles(file, classFiles);
                }
            }
        }
    }

    private void checkJava(
            @NonNull Project project,
            @Nullable Project main,
            @NonNull List<File> sourceFolders,
            @NonNull List<Detector> checks) {
        IJavaParser javaParser = mClient.getJavaParser();
        if (javaParser == null) {
            mClient.log(null, "No java parser provided to lint: not running Java checks");
            return;
        }

        assert checks.size() > 0;

        // Gather all Java source files in a single pass; more efficient.
        List<File> sources = new ArrayList<File>(100);
        for (File folder : sourceFolders) {
            gatherJavaFiles(folder, sources);
        }
        if (sources.size() > 0) {
            JavaVisitor visitor = new JavaVisitor(javaParser, checks);
            for (File file : sources) {
                JavaContext context = new JavaContext(this, project, main, file);
                fireEvent(EventType.SCANNING_FILE, context);
                visitor.visitFile(context, file);
                if (mCanceled) {
                    return;
                }
            }
        }
    }

    private void gatherJavaFiles(@NonNull File dir, @NonNull List<File> result) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".java")) { //$NON-NLS-1$
                    result.add(file);
                } else if (file.isDirectory()) {
                    gatherJavaFiles(file, result);
                }
            }
        }
    }

    private ResourceFolderType mCurrentFolderType;
    private List<ResourceXmlDetector> mCurrentXmlDetectors;
    private XmlVisitor mCurrentVisitor;

    @Nullable
    private XmlVisitor getVisitor(
            @NonNull ResourceFolderType type,
            @NonNull List<ResourceXmlDetector> checks) {
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

            mCurrentVisitor = new XmlVisitor(mClient.getDomParser(), applicableChecks);
        }

        return mCurrentVisitor;
    }

    private void checkResFolder(
            @NonNull Project project,
            @Nullable Project main,
            @NonNull File res,
            @NonNull List<ResourceXmlDetector> checks) {
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
                checkResourceFolder(project, main, dir, type, checks);
            }

            if (mCanceled) {
                return;
            }
        }
    }

    private void checkResourceFolder(
            @NonNull Project project,
            @Nullable Project main,
            @NonNull File dir,
            @NonNull ResourceFolderType type,
            @NonNull List<ResourceXmlDetector> checks) {
        // Process the resource folder
        File[] xmlFiles = dir.listFiles();
        if (xmlFiles != null && xmlFiles.length > 0) {
            XmlVisitor visitor = getVisitor(type, checks);
            if (visitor != null) { // if not, there are no applicable rules in this folder
                for (File file : xmlFiles) {
                    if (LintUtils.isXmlFile(file)) {
                        XmlContext context = new XmlContext(this, project, main, file);
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
    private void checkIndividualResources(
            @NonNull Project project,
            @Nullable Project main,
            @NonNull List<ResourceXmlDetector> xmlDetectors,
            @NonNull List<File> files) {
        for (File file : files) {
            if (file.isDirectory()) {
                // Is it a resource folder?
                ResourceFolderType type = ResourceFolderType.getFolderType(file.getName());
                if (type != null && new File(file.getParentFile(), RES_FOLDER).exists()) {
                    // Yes.
                    checkResourceFolder(project, main, file, type, xmlDetectors);
                } else if (file.getName().equals(RES_FOLDER)) { // Is it the res folder?
                    // Yes
                    checkResFolder(project, main, file, xmlDetectors);
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
                        XmlContext context = new XmlContext(this, project, main, file);
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
    public void addLintListener(@NonNull LintListener listener) {
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
    public void removeLintListener(@NonNull LintListener listener) {
        mListeners.remove(listener);
        if (mListeners.size() == 0) {
            mListeners = null;
        }
    }

    /** Notifies listeners, if any, that the given event has occurred */
    private void fireEvent(@NonNull LintListener.EventType type, @NonNull Context context) {
        if (mListeners != null) {
            for (int i = 0, n = mListeners.size(); i < n; i++) {
                LintListener listener = mListeners.get(i);
                listener.update(this, type, context);
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
    private class LintClientWrapper extends LintClient {
        @NonNull
        private final LintClient mDelegate;

        public LintClientWrapper(@NonNull LintClient delegate) {
            mDelegate = delegate;
        }

        @Override
        public void report(
                @NonNull Context context,
                @NonNull Issue issue,
                @Nullable Location location,
                @NonNull String message,
                @Nullable Object data) {
            Configuration configuration = context.getConfiguration();
            if (!configuration.isEnabled(issue)) {
                if (issue != IssueRegistry.PARSER_ERROR) {
                    mDelegate.log(null, "Incorrect detector reported disabled issue %1$s",
                            issue.toString());
                }
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
        @NonNull
        public Configuration getConfiguration(@NonNull Project project) {
            return mDelegate.getConfiguration(project);
        }


        @Override
        public void log(@Nullable Throwable exception, @Nullable String format,
                @Nullable Object... args) {
            mDelegate.log(exception, format, args);
        }

        @Override
        @NonNull
        public String readFile(@NonNull File file) {
            return mDelegate.readFile(file);
        }

        @Override
        @NonNull
        public List<File> getJavaSourceFolders(@NonNull Project project) {
            return mDelegate.getJavaSourceFolders(project);
        }

        @Override
        @NonNull
        public List<File> getJavaClassFolders(@NonNull Project project) {
            return mDelegate.getJavaClassFolders(project);
        }

        @Override
        @Nullable
        public IDomParser getDomParser() {
            return mDelegate.getDomParser();
        }

        @Override
        @NonNull
        public Class<? extends Detector> replaceDetector(Class<? extends Detector> detectorClass) {
            return mDelegate.replaceDetector(detectorClass);
        }

        @Override
        @NonNull
        public SdkInfo getSdkInfo(Project project) {
            return mDelegate.getSdkInfo(project);
        }

        @Override
        @NonNull
        public Project getProject(@NonNull File dir, @NonNull File referenceDir) {
            return mDelegate.getProject(dir, referenceDir);
        }

        @Override
        @Nullable
        public IJavaParser getJavaParser() {
            return mDelegate.getJavaParser();
        }
    }

    /**
     * Requests another pass through the data for the given detector. This is
     * typically done when a detector needs to do more expensive computation,
     * but it only wants to do this once it <b>knows</b> that an error is
     * present, or once it knows more specifically what to check for.
     *
     * @param detector the detector that should be included in the next pass.
     *            Note that the lint runner may refuse to run more than a couple
     *            of runs.
     * @param scope the scope to be revisited. This must be a subset of the
     *       current scope ({@link #getScope()}, and it is just a performance hint;
     *       in particular, the detector should be prepared to be called on other
     *       scopes as well (since they may have been requested by other detectors).
     *       You can pall null to indicate "all".
     */
    public void requestRepeat(@NonNull Detector detector, @Nullable EnumSet<Scope> scope) {
        if (mRepeatingDetectors == null) {
            mRepeatingDetectors = new ArrayList<Detector>();
        }
        mRepeatingDetectors.add(detector);

        if (scope != null) {
            if (mRepeatScope == null) {
                mRepeatScope = scope;
            } else {
                mRepeatScope = EnumSet.copyOf(mRepeatScope);
                mRepeatScope.addAll(scope);
            }
        } else {
            mRepeatScope = Scope.ALL;
        }
    }
}
