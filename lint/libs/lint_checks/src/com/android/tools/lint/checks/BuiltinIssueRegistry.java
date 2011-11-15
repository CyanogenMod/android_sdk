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

package com.android.tools.lint.checks;

import static com.android.tools.lint.detector.api.LintUtils.endsWith;

import com.android.prefs.AndroidLocation;
import com.android.prefs.AndroidLocation.AndroidLocationException;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.detector.api.Issue;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/** Registry which provides a list of checks to be performed on an Android project */
public class BuiltinIssueRegistry extends IssueRegistry {
    /** Folder name in the .android dir where additional detector jars are found */
    private static final String LINT_FOLDER = "lint"; //$NON-NLS-1$

    /**
     * Manifest constant for declaring an issue provider. Example:
     * Lint-Issues: foo.bar.CustomIssueRegistry
     */
    private static final String MF_LINT_REGISTRY = "Lint-Registry"; //$NON-NLS-1$

    private static final List<Issue> sIssues;

    static {
        List<Issue> issues = new ArrayList<Issue>();

        issues.add(AccessibilityDetector.ISSUE);
        issues.add(DuplicateIdDetector.CROSS_LAYOUT);
        issues.add(DuplicateIdDetector.WITHIN_LAYOUT);
        issues.add(StateListDetector.ISSUE);
        issues.add(InefficientWeightDetector.INEFFICIENT_WEIGHT);
        issues.add(InefficientWeightDetector.NESTED_WEIGHTS);
        issues.add(InefficientWeightDetector.BASELINE_WEIGHTS);
        issues.add(ScrollViewChildDetector.ISSUE);
        issues.add(MergeRootFrameLayoutDetector.ISSUE);
        issues.add(NestedScrollingWidgetDetector.ISSUE);
        issues.add(ChildCountDetector.SCROLLVIEW_ISSUE);
        issues.add(ChildCountDetector.ADAPTERVIEW_ISSUE);
        issues.add(UseCompoundDrawableDetector.ISSUE);
        issues.add(UselessViewDetector.USELESS_PARENT);
        issues.add(UselessViewDetector.USELESS_LEAF);
        issues.add(TooManyViewsDetector.TOO_MANY);
        issues.add(TooManyViewsDetector.TOO_DEEP);
        issues.add(GridLayoutDetector.ISSUE);
        issues.add(TranslationDetector.EXTRA);
        issues.add(TranslationDetector.MISSING);
        issues.add(HardcodedValuesDetector.ISSUE);
        issues.add(ProguardDetector.ISSUE);
        issues.add(PxUsageDetector.ISSUE);
        issues.add(TextFieldDetector.ISSUE);
        issues.add(UnusedResourceDetector.ISSUE);
        issues.add(UnusedResourceDetector.ISSUE_IDS);
        issues.add(ArraySizeDetector.INCONSISTENT);
        issues.add(ManifestOrderDetector.ISSUE);
        issues.add(ExportedServiceDetector.ISSUE);
        issues.add(IconDetector.GIF_USAGE);
        issues.add(IconDetector.ICON_DENSITIES);
        issues.add(IconDetector.ICON_MISSING_FOLDER);
        issues.add(IconDetector.ICON_DIP_SIZE);
        issues.add(IconDetector.ICON_EXPECTED_SIZE);
        issues.add(IconDetector.ICON_LOCATION);
        issues.add(IconDetector.DUPLICATES_NAMES);
        issues.add(IconDetector.DUPLICATES_CONFIGURATIONS);
        issues.add(IconDetector.ICON_NODPI);
        issues.add(DetectMissingPrefix.MISSING_NAMESPACE);

        addCustomIssues(issues);

        sIssues = Collections.unmodifiableList(issues);

        // Check that ids are unique
        boolean assertionsEnabled = false;
        assert assertionsEnabled = true; // Intentional side-effect
        if (assertionsEnabled) {
            Set<String> ids = new HashSet<String>();
            for (Issue issue : sIssues) {
                String id = issue.getId();
                assert !ids.contains(id) : "Duplicate id " + id; //$NON-NLS-1$
                ids.add(id);
            }
        }
    }

    /**
     * Constructs a new {@link BuiltinIssueRegistry}
     */
    public BuiltinIssueRegistry() {
    }

    @Override
    public List<Issue> getIssues() {
        return sIssues;
    }

    /**
     * Add in custom issues registered by the user - via an environment variable
     * or in the .android/lint directory.
     */
    private static void addCustomIssues(List<Issue> issues) {
        // Look for additional detectors registered by the user, via
        // (1) an environment variable (useful for build servers etc), and
        // (2) via jar files in the .android/lint directory
        Set<File> files = null;
        try {
            File lint = new File(AndroidLocation.getFolder() + File.separator + LINT_FOLDER);
            if (lint.exists()) {
                File[] list = lint.listFiles();
                if (list != null) {
                    for (File jarFile : list) {
                        if (endsWith(jarFile.getName(), ".jar")) { //$NON-NLS-1$
                            if (files == null) {
                                files = new HashSet<File>();
                            }
                            files.add(jarFile);
                            addIssuesFromJar(jarFile, issues);
                        }
                    }
                }
            }
        } catch (AndroidLocationException e) {
            // Ignore -- no android dir, so no rules to load.
        }

        String lintClassPath = System.getenv("ANDROID_LINT_JARS"); //$NON-NLS-1$
        if (lintClassPath != null && lintClassPath.length() > 0) {
            String[] paths = lintClassPath.split(File.pathSeparator);
            for (String path : paths) {
                File jarFile = new File(path);
                if (jarFile.exists() && (files == null || !files.contains(jarFile))) {
                    addIssuesFromJar(jarFile, issues);
                }
            }
        }

    }

    /** Add the issues found in the given jar file into the given list of issues */
    private static void addIssuesFromJar(File jarFile, List<Issue> issues) {
        try {
            JarFile jarfile = new JarFile(jarFile);
            Manifest manifest = jarfile.getManifest();
            Attributes attrs = manifest.getMainAttributes();
            Object object = attrs.get(new Attributes.Name(MF_LINT_REGISTRY));
            if (object instanceof String) {
                String className = (String) object;

                // Make a class loader for this jar
                try {
                    URL url = jarFile.toURI().toURL();
                    URLClassLoader loader = new URLClassLoader(new URL[] { url },
                            BuiltinIssueRegistry.class.getClassLoader());
                    try {
                        Class<?> registryClass = Class.forName(className, true, loader);
                        IssueRegistry registry = (IssueRegistry) registryClass.newInstance();
                        for (Issue issue : registry.getIssues()) {
                            issues.add(issue);
                        }
                    } catch (Throwable e) {
                        log(e);
                    }
                } catch (MalformedURLException e) {
                    log(e);
                }
            }
        } catch (IOException e) {
            log(e);
        }
    }

    private static void log(Throwable e) {
        // TODO: Where do we log this? There's no embedding tool context here. For now,
        // just dump to the console so detector developers get some feedback on what went
        // wrong.
        e.printStackTrace();
    }
}
