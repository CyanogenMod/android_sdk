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

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.AdtUtils;
import com.android.ide.eclipse.adt.internal.preferences.AdtPrefs;
import com.android.tools.lint.api.DetectorRegistry;
import com.android.tools.lint.api.Lint;
import com.android.tools.lint.detector.api.Scope;

import org.eclipse.core.resources.IFolder;
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
import java.util.Collections;

/**
 * Eclipse implementation for running lint on workspace files and projects.
 */
public class LintRunner {
    static final String MARKER_CHECKID_PROPERTY = "checkid";    //$NON-NLS-1$

    /**
     * Runs lint and updates the markers, and waits for the result. Returns
     * true if fatal errors were found.
     *
     * @param resource the resource (project, folder or file) to be analyzed
     * @param doc the associated document, if known, or null
     * @return true if any fatal errors were encountered.
     */
    public static boolean runLint(IResource resource, IDocument doc) {
        CheckFileJob job = (CheckFileJob) startLint(resource, doc);
        try {
            job.join();
            return job.isFatal();
        } catch (InterruptedException e) {
            AdtPlugin.log(e, null);
        }

        return false;
    }

    /**
     * Runs lint and updates the markers. Does not wait for the job to
     * finish - just returns immediately.
     *
     * @param resource the resource (project, folder or file) to be analyzed
     * @param doc the associated document, if known, or null
     * @return the job running lint in the background.
     */
    public static Job startLint(IResource resource, IDocument doc) {
        if (resource != null) {
            cancelCurrentJobs();

            CheckFileJob job = new CheckFileJob(resource, doc);
            job.schedule();
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
            boolean fatal = LintRunner.runLint(project, null);
            if (fatal) {
                MessageDialog.openWarning(shell,
                        "Export Aborted",
                        "Export aborted because fatal lint errors were found. These " +
                        "are listed in the Problems view. Either fix these before " +
                        "running Export again, or turn off \"Run full error check " +
                        "when exporting app\" in the Android > Lint Error Checking " +
                        "preference page.");
                return false;
            }
        }

        return true;
    }

    /** Cancels the current lint jobs, if any */
    static void cancelCurrentJobs() {
        // Cancel any current running jobs first
        IJobManager jobManager = Job.getJobManager();
        Job[] jobs = jobManager.find(CheckFileJob.FAMILY_RUN_LINT);
        for (Job job : jobs) {
            job.cancel();
        }
    }

    private static final class CheckFileJob extends Job {
        /** Job family */
        private static final Object FAMILY_RUN_LINT = new Object();
        private final IResource mResource;
        private final IDocument mDocument;
        private Lint mLint;
        private boolean mFatal;

        private CheckFileJob(IResource resource, IDocument doc) {
            super("Running Android Lint");
            this.mResource = resource;
            this.mDocument = doc;
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
                LintEclipseContext.clearMarkers(mResource);

                DetectorRegistry registry = LintEclipseContext.getRegistry();
                LintEclipseContext toolContext = new LintEclipseContext(registry, mResource,
                        mDocument);
                File file = AdtUtils.getAbsolutePath(mResource).toFile();
                Scope scope = (mResource instanceof IProject) ? Scope.PROJECT :
                        (mResource instanceof IFolder) ? Scope.RESOURCES : Scope.SINGLE_FILE;
                mLint = new Lint(registry, toolContext, scope);
                mLint.analyze(Collections.singletonList(file));
                mFatal = toolContext.isFatal();
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
