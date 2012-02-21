/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.ide.eclipse.adt.internal.lint;

import static com.android.ide.eclipse.adt.AdtConstants.DOT_XML;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.AdtUtils;
import com.android.ide.eclipse.adt.internal.preferences.AdtPrefs;
import com.android.ide.eclipse.adt.internal.sdk.ProjectState;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.sdklib.SdkConstants;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.widgets.Shell;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Eclipse implementation for running lint on workspace files and projects.
 */
public class EclipseLintRunner {
    static final String MARKER_CHECKID_PROPERTY = "checkid";    //$NON-NLS-1$

    /**
     * Runs lint and updates the markers, and waits for the result. Returns
     * true if fatal errors were found.
     *
     * @param resources the resources (project, folder or file) to be analyzed
     * @param doc the associated document, if known, or null
     * @param fatalOnly if true, only report fatal issues (severity=error)
     * @return true if any fatal errors were encountered.
     */
    private static boolean runLint(List<? extends IResource> resources, IDocument doc,
            boolean fatalOnly) {
        resources = addLibraries(resources);
        CheckFileJob job = (CheckFileJob) startLint(resources, doc, fatalOnly, false /*show*/);
        try {
            job.join();
            boolean fatal = job.isFatal();

            if (fatal) {
                LintViewPart.show(resources);
            }

            return fatal;
        } catch (InterruptedException e) {
            AdtPlugin.log(e, null);
        }

        return false;
    }

    /**
     * Runs lint and updates the markers. Does not wait for the job to
     * finish - just returns immediately.
     *
     * @param resources the resources (project, folder or file) to be analyzed
     * @param doc the associated document, if known, or null
     * @param fatalOnly if true, only report fatal issues (severity=error)
     * @param show if true, show the results in a {@link LintViewPart}
     * @return the job running lint in the background.
     */
    public static Job startLint(List<? extends IResource> resources,
            IDocument doc, boolean fatalOnly, boolean show) {
        if (resources != null && !resources.isEmpty()) {
            resources = addLibraries(resources);

            cancelCurrentJobs(false);

            CheckFileJob job = new CheckFileJob(resources, doc, fatalOnly);
            job.schedule();

            if (show) {
                // Show lint view where the results are listed
                LintViewPart.show(resources);
            }
            return job;
        }

        return null;
    }

    /**
     * Run Lint for an Export APK action. If it succeeds (no fatal errors)
     * returns true, and if it fails it will display an error message and return
     * false.
     *
     * @param shell the parent shell to show error messages in
     * @param project the project to run lint on
     * @return true if the lint run succeeded with no fatal errors
     */
    public static boolean runLintOnExport(Shell shell, IProject project) {
        if (AdtPrefs.getPrefs().isLintOnExport()) {
            boolean fatal = EclipseLintRunner.runLint(Collections.singletonList(project), null,
                    true /*fatalOnly*/);
            if (fatal) {
                MessageDialog.openWarning(shell,
                        "Export Aborted",
                        "Export aborted because fatal lint errors were found. These " +
                        "are listed in the Lint View. Either fix these before " +
                        "running Export again, or turn off \"Run full error check " +
                        "when exporting app\" in the Android > Lint Error Checking " +
                        "preference page.");
                return false;
            }
        }

        return true;
    }

    /** Returns the current lint jobs, if any (never returns null but array may be empty) */
    static Job[] getCurrentJobs() {
        IJobManager jobManager = Job.getJobManager();
        return jobManager.find(CheckFileJob.FAMILY_RUN_LINT);
    }

    /** Cancels the current lint jobs, if any, and optionally waits for them to finish */
    static void cancelCurrentJobs(boolean wait) {
        // Cancel any current running jobs first
        Job[] currentJobs = getCurrentJobs();
        for (Job job : currentJobs) {
            job.cancel();
        }

        if (wait) {
            for (Job job : currentJobs) {
                try {
                    job.join();
                } catch (InterruptedException e) {
                    AdtPlugin.log(e, null);
                }
            }
        }
    }

    /** If the resource list contains projects, add in any library projects as well */
    private static List<? extends IResource> addLibraries(List<? extends IResource> resources) {
        if (resources != null && !resources.isEmpty()) {
            boolean haveProjects = false;
            for (IResource resource : resources) {
                if (resource instanceof IProject) {
                    haveProjects = true;
                    break;
                }
            }

            if (haveProjects) {
                List<IResource> result = new ArrayList<IResource>();
                Map<IProject, IProject> allProjects = new IdentityHashMap<IProject, IProject>();
                List<IProject> projects = new ArrayList<IProject>();
                for (IResource resource : resources) {
                    if (resource instanceof IProject) {
                        IProject project = (IProject) resource;
                        allProjects.put(project, project);
                        projects.add(project);
                    } else {
                        result.add(resource);
                    }
                }
                for (IProject project : projects) {
                    ProjectState state = Sdk.getProjectState(project);
                    if (state != null) {
                        for (IProject library : state.getFullLibraryProjects()) {
                            allProjects.put(library, library);
                        }
                    }
                }
                for (IProject project : allProjects.keySet()) {
                    result.add(project);
                }

                return result;
            }
        }

        return resources;
    }

    private static final class CheckFileJob extends Job {
        /** Job family */
        private static final Object FAMILY_RUN_LINT = new Object();
        private final List<? extends IResource> mResources;
        private final IDocument mDocument;
        private LintDriver mLint;
        private boolean mFatal;
        private boolean mFatalOnly;

        private CheckFileJob(List<? extends IResource> resources, IDocument doc,
                boolean fatalOnly) {
            super("Running Android Lint");
            mResources = resources;
            mDocument = doc;
            mFatalOnly = fatalOnly;
        }

        @Override
        public boolean belongsTo(Object family) {
            return family == FAMILY_RUN_LINT;
        }

        @Override
        protected void canceling() {
            super.canceling();
            if (mLint != null) {
                mLint.cancel();
            }
        }

        @Override
        protected IStatus run(IProgressMonitor monitor) {
            try {
                monitor.beginTask("Looking for errors", IProgressMonitor.UNKNOWN);
                IssueRegistry registry = EclipseLintClient.getRegistry();
                EnumSet<Scope> scope = Scope.ALL;
                List<File> files = new ArrayList<File>(mResources.size());
                for (IResource resource : mResources) {
                    File file = AdtUtils.getAbsolutePath(resource).toFile();
                    files.add(file);

                    if (resource instanceof IProject) {
                        scope = Scope.ALL;
                    } else if (resource instanceof IFile
                            && AdtUtils.endsWithIgnoreCase(resource.getName(), DOT_XML)) {
                        if (resource.getName().equals(SdkConstants.FN_ANDROID_MANIFEST_XML)) {
                            scope = EnumSet.of(Scope.MANIFEST);
                        } else {
                            scope = Scope.RESOURCE_FILE_SCOPE;
                        }
                    } else {
                        return new Status(Status.ERROR, AdtPlugin.PLUGIN_ID, Status.ERROR,
                                "Only XML files are supported for single file lint", null); //$NON-NLS-1$
                    }
                }
                if (Scope.checkSingleFile(scope)) {
                    // Delete specific markers
                    for (IResource resource : mResources) {
                        IMarker[] markers = EclipseLintClient.getMarkers(resource);
                        for (IMarker marker : markers) {
                            String id = marker.getAttribute(MARKER_CHECKID_PROPERTY, ""); //$NON-NLS-1$
                            Issue issue = registry.getIssue(id);
                            if (issue == null || issue.getScope().equals(scope)) {
                                marker.delete();
                            }
                        }
                    }
                } else {
                    EclipseLintClient.clearMarkers(mResources);
                }

                EclipseLintClient client = new EclipseLintClient(registry, mResources,
                            mDocument, mFatalOnly);
                mLint = new LintDriver(registry, client);
                mLint.analyze(files, scope);
                mFatal = client.hasFatalErrors();
                return Status.OK_STATUS;
            } catch (Exception e) {
                return new Status(Status.ERROR, AdtPlugin.PLUGIN_ID, Status.ERROR,
                                  "Failed", e); //$NON-NLS-1$
            } finally {
                if (monitor != null) {
                    monitor.done();
                }
            }
        }

        /**
         * Returns true if a fatal error was encountered
         */
        boolean isFatal() {
            return mFatal;
        }
    }
}
