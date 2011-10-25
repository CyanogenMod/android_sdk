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

package com.android.tools.lint.api;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Analyzes Android projects and files */
public class Lint {
    private static final String RES_FOLDER_NAME = "res"; //$NON-NLS-1$
    private final ToolContext mToolContext;
    private volatile boolean mCanceled;
    private DetectorRegistry mRegistry;
    private Scope mScope;

    private List<ResourceXmlDetector> mResourceChecks = new ArrayList<ResourceXmlDetector>();
    private List<Detector> mXmlChecks = new ArrayList<Detector>();
    private List<Detector> mClassChecks = new ArrayList<Detector>();
    private List<Detector> mJavaChecks = new ArrayList<Detector>();
    private List<Detector> mOtherChecks = new ArrayList<Detector>();

    /**
     * Creates a new {@link Lint}
     *
     * @param registry The registry containing rules to be run
     * @param toolContext a context for the tool wrapping the analyzer, such as
     *            an IDE or a CLI
     * @param scope the scope of the analysis; detectors with a wider scope will
     *            not be run
     */
    public Lint(DetectorRegistry registry, ToolContext toolContext, Scope scope) {
        assert toolContext != null;
        mRegistry = registry;
        mToolContext = toolContext;
        mScope = scope;
    }

    /** Cancels the current lint run as soon as possible */
    public void cancel() {
        mCanceled = true;
    }

    /**
     * Analyze the given file (which can point to an Android project). Issues found
     * are reported to the associated {@link ToolContext}.
     *
     * @param files the files and directories to be analyzed
     */
    public void analyze(List<File> files) {
        List<? extends Detector> availableChecks = mRegistry.getDetectors();

        // Filter out disabled checks
        List<Detector> checks = new ArrayList<Detector>(availableChecks.size());
        for (Detector detector : availableChecks) {
            boolean hasValidScope = true;
            for (Issue issue : detector.getIssues()) {
                if (issue.getScope().within(mScope)) {
                    hasValidScope = true;
                    break;
                }
            }
            if (!hasValidScope) {
                continue;
            }
            // A detector is enabled if at least one of its issues is enabled
            for (Issue issue : detector.getIssues()) {
                if (mToolContext.isEnabled(issue)) {
                    checks.add(detector);
                    break;
                }
            }
        }

        // Process XML files in a single pass
        for (Detector check : checks) {
            boolean matched = false;
            if (check instanceof ResourceXmlDetector) {
                matched = true;
                mResourceChecks.add((ResourceXmlDetector) check);
                // Note the else-if here: we don't add resource xml detectors
                // as plain xml scanners since they are handled specially
            } else if (check instanceof Detector.XmlScanner) {
                mXmlChecks.add(check);
                matched = true;
            }
            if (check instanceof Detector.ClassScanner) {
                mClassChecks.add(check);
                matched = true;
            }
            if (check instanceof Detector.JavaScanner) {
                mJavaChecks.add(check);
                matched = true;
            }
            if (!matched) {
                mOtherChecks.add(check);
            }
        }

        // TODO: Handle multiple projects -- gotta split up the various arguments
        // passed in and partition them into different Android projects and for each
        // project call beforeCheck etc.
        File projectDir = files.get(0);

        Context projectContext = new Context(mToolContext, projectDir, projectDir, mScope);
        for (Detector check : checks) {
            check.beforeCheckProject(projectContext);
            if (mCanceled) {
                return;
            }
        }

        for (File file : files) {
            if (file.isDirectory()) {
                // Is it a resource folder?
                ResourceFolderType type = ResourceFolderType.getFolderType(file.getName());
                if (type != null && new File(file.getParentFile(), RES_FOLDER_NAME).exists()) {
                    // Yes.
                    checkResourceFolder(projectDir, file, type);
                } else if (file.getName().equals(RES_FOLDER_NAME)) { // Is it the "res" folder?
                    // Yes
                    checkResFolder(projectDir, file);
                } else {
                    // It must be a project
                    File res = new File(file, RES_FOLDER_NAME);
                    if (res.exists()) {
                        checkProject(projectDir);
                    } else {
                        mToolContext.log(null, "Unexpected folder %1$s; should be project, " +
                                "\"res\" folder or resource folder", file.getPath());
                    }
                }
            } else if (file.isFile()) {
                // Did we point to an XML resource?
                if (ResourceXmlDetector.isXmlFile(file)) {
                    // Yes, find out its resource type
                    String folderName = file.getParentFile().getName();
                    ResourceFolderType type = ResourceFolderType.getFolderType(folderName);
                    if (type != null) {
                        XmlVisitor visitor = getVisitor(type);
                        if (visitor != null) {
                            Context context = new Context(mToolContext, projectDir, file, mScope);
                            visitor.visitFile(context, file);
                        }
                    } else if (mXmlChecks.size() > 0) {
                        XmlVisitor v = new XmlVisitor(mToolContext.getParser(), mXmlChecks);
                        Context context = new Context(mToolContext, projectDir, file, mScope);
                        v.visitFile(context, file);
                    }
                } else {
                    if (mOtherChecks.size() > 0) {
                        Context context = new Context(mToolContext, projectDir, file, mScope);
                        context.location = new Location(file, null, null);
                        for (Detector detector : mOtherChecks) {
                            if (detector.appliesTo(context, file)) {
                                detector.beforeCheckFile(context);
                                detector.run(context);
                                detector.afterCheckFile(context);
                            }
                        }
                    }
                }
            }

            if (mCanceled) {
                return;
            }
        }

        for (Detector check : checks) {
            check.afterCheckProject(projectContext);
            if (mCanceled) {
                return;
            }
        }

        if (mCanceled) {
            mToolContext.report(
                projectContext,
                // Must provide an issue since API guarantees that the issue parameter
                // is valid
                Issue.create("dummy", "", "", "", 0, Severity.INFORMATIONAL, Scope.SINGLE_FILE), //$NON-NLS-1$
                null /*range*/,
                "Lint canceled by user", null);
        }
    }

    private void checkProject(File projectDir) {
        File res = new File(projectDir, RES_FOLDER_NAME);
        if (res.exists()) {
            checkResFolder(projectDir, res);
        }

        if (mClassChecks.size() > 0 || mJavaChecks.size() > 0) {
            if (mClassChecks.size() > 0) {
                List<File> binFolders = mToolContext.getJavaClassFolder(projectDir);
                checkClasses(projectDir, binFolders);
            }
            if (mJavaChecks.size() > 0) {
                List<File> sourceFolders = mToolContext.getJavaSourceFolders(projectDir);
                checkJava(projectDir, sourceFolders);
            }
        }

        if (mOtherChecks.size() > 0 || mXmlChecks.size() > 0) {
            // Run other checks on top level files in the project only -- proguard.cfg,
            // AndroidManifest.xml, build.xml, etc.
            File[] list = projectDir.listFiles();
            if (list != null) {
                for (File file : list) {
                    if (file.isFile()) {
                        Context context = new Context(mToolContext, projectDir, file, mScope);
                        context.location = new Location(file, null, null);
                        if (mOtherChecks.size() > 0) {
                            for (Detector detector : mOtherChecks) {
                                if (detector.appliesTo(context, file)) {
                                    detector.beforeCheckFile(context);
                                    detector.run(context);
                                    detector.afterCheckFile(context);
                                }
                            }
                        }

                        if (ResourceXmlDetector.isXmlFile(file) && mXmlChecks.size() > 0) {
                            XmlVisitor v = new XmlVisitor(mToolContext.getParser(), mXmlChecks);
                            v.visitFile(context, file);
                            // TBD: Run plain xml checks on other folders too, such as res/xml ?
                        }
                    }
                }
            }
        }
    }

    private void checkClasses(File projectDir, List<File> binFolders) {
        Context context = new Context(mToolContext, projectDir, projectDir, mScope);
        for (Detector detector : mClassChecks) {
            ((Detector.ClassScanner) detector).checkJavaClasses(context);

            if (mCanceled) {
                return;
            }
        }
    }

    private void checkJava(File projectDir, List<File> sourceFolders) {
        Context context = new Context(mToolContext, projectDir, projectDir, mScope);

        for (Detector detector : mJavaChecks) {
            ((Detector.JavaScanner) detector).checkJavaSources(context, sourceFolders);

            if (mCanceled) {
                return;
            }
        }
    }

    private ResourceFolderType mCurrentFolderType;
    private List<ResourceXmlDetector> mCurrentXmlDetectors;
    private XmlVisitor mCurrentVisitor;

    private XmlVisitor getVisitor(ResourceFolderType type) {
        if (type != mCurrentFolderType) {
            mCurrentFolderType = type;

            // Determine which XML resource detectors apply to the given folder type
            List<ResourceXmlDetector> applicableChecks =
                    new ArrayList<ResourceXmlDetector>(mResourceChecks.size());
            for (ResourceXmlDetector check : mResourceChecks) {
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

            mCurrentVisitor = new XmlVisitor(mToolContext.getParser(), applicableChecks);
        }

        return mCurrentVisitor;
    }

    private void checkResFolder(File projectDir, File res) {
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
            if (!dir.isDirectory()) {
                continue;
            }

            type = ResourceFolderType.getFolderType(dir.getName());
            if (type != null) {
                checkResourceFolder(projectDir, dir, type);
            }

            if (mCanceled) {
                return;
            }
        }
    }

    private void checkResourceFolder(File projectDir, File dir, ResourceFolderType type) {
        // Process the resource folder
        File[] xmlFiles = dir.listFiles();
        if (xmlFiles != null && xmlFiles.length > 0) {
            XmlVisitor visitor = getVisitor(type);
            if (visitor != null) { // if not, there are no applicable rules in this folder
                for (File xmlFile : xmlFiles) {
                    if (ResourceXmlDetector.isXmlFile(xmlFile)) {
                        Context context = new Context(mToolContext, projectDir, xmlFile, mScope);
                        visitor.visitFile(context, xmlFile);
                        if (mCanceled) {
                            return;
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns the associated tool context for the surrounding tool that is
     * embedding lint analysis
     *
     * @return the surrounding tool context
     */
    public ToolContext getToolContext() {
        return mToolContext;
    }
}
