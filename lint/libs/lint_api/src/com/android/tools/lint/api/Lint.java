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
            if (!detector.getScope().within(mScope)) {
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
        List<ResourceXmlDetector> xmlChecks = new ArrayList<ResourceXmlDetector>(checks.size());
        List<Detector> other = new ArrayList<Detector>(checks.size());
        for (Detector check : checks) {
            if (check instanceof ResourceXmlDetector) {
                xmlChecks.add((ResourceXmlDetector) check);
            } else {
                // TODO:
                other.add(check);
            }
        }

        Context projectContext = new Context(mToolContext, null);
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
                    checkResourceFolder(file, type, xmlChecks);
                } else if (file.getName().equals(RES_FOLDER_NAME)) { // Is it the "res" folder?
                    // Yes
                    checkResFolder(file, xmlChecks);
                } else {
                    // It must be a project
                    File res = new File(file, RES_FOLDER_NAME);
                    if (res.exists()) {
                        checkResFolder(res, xmlChecks);
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
                        XmlVisitor visitor = getVisitor(xmlChecks, type);
                        if (visitor != null) {
                            Context context = new Context(mToolContext, file);
                            visitor.visitFile(context, file);
                        }
                    }
                } else {
                    if (other.size() > 0) {
                        Context context = new Context(mToolContext, file);
                        context.location = new Location(file, null, null);
                        for (Detector detector : other) {
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
                    // Must provide an issue since API guarantees that the issue parameter
                    // is valid
                    Issue.create("dummy", "", "", "", 0, Severity.INFORMATIONAL), //$NON-NLS-1$
                    null /*range*/,
                    "Lint canceled by user");
        }
    }

    private ResourceFolderType mCurrentFolderType;
    private List<ResourceXmlDetector> mCurrentXmlDetectors;
    private XmlVisitor mCurrentVisitor;

    private XmlVisitor getVisitor(List<ResourceXmlDetector> checks, ResourceFolderType type) {
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

            mCurrentVisitor = new XmlVisitor(mToolContext.getParser(), applicableChecks);
        }

        return mCurrentVisitor;
    }

    private void checkResFolder(File res, List<ResourceXmlDetector> xmlChecks) {
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
                checkResourceFolder(dir, type, xmlChecks);
            }

            if (mCanceled) {
                return;
            }
        }
    }

    private void checkResourceFolder(File dir, ResourceFolderType type,
            List<ResourceXmlDetector> xmlChecks) {
        // Process the resource folder
        File[] xmlFiles = dir.listFiles();
        if (xmlFiles != null && xmlFiles.length > 0) {
            XmlVisitor visitor = getVisitor(xmlChecks, type);
            if (visitor != null) { // if not, there are no applicable rules in this folder
                for (File xmlFile : xmlFiles) {
                    if (ResourceXmlDetector.isXmlFile(xmlFile)) {
                        Context context = new Context(mToolContext, xmlFile);
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
