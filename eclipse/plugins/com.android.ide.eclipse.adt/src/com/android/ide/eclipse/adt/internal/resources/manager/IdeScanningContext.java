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
package com.android.ide.eclipse.adt.internal.resources.manager;

import static com.android.ide.eclipse.adt.AdtConstants.MARKER_AAPT_COMPILE;
import static org.eclipse.core.resources.IResource.DEPTH_ONE;
import static org.eclipse.core.resources.IResource.DEPTH_ZERO;

import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ScanningContext;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.build.AaptParser;
import com.android.util.Pair;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * An {@link IdeScanningContext} is a specialized {@link ScanningContext} which
 * carries extra information about the scanning state, such as which file is
 * currently being scanned, and which files have been scanned in the past, such
 * that at the end of a scan we can mark and clear errors, etc.
 */
public class IdeScanningContext extends ScanningContext {
    private final IProject mProject;
    private final List<IResource> mScannedResources = new ArrayList<IResource>();
    private IResource mCurrentFile;
    private List<Pair<IResource, String>> mErrors;
    private Set<IProject> mFullAaptProjects;

    /**
     * Constructs a new {@link IdeScanningContext}
     *
     * @param repository the associated {@link ResourceRepository}
     * @param project the associated project
     */
    public IdeScanningContext(ResourceRepository repository, IProject project) {
        super(repository);
        mProject = project;
    }

    @Override
    public void addError(String error) {
        super.addError(error);

        if (mErrors == null) {
            mErrors = new ArrayList<Pair<IResource,String>>();
        }
        mErrors.add(Pair.of(mCurrentFile, error));
    }

    /**
     * Notifies the context that the given resource is about to be scanned.
     *
     * @param resource the resource about to be scanned
     */
    public void startScanning(IResource resource) {
        assert mCurrentFile == null : mCurrentFile;
        mCurrentFile = resource;
        mScannedResources.add(resource);
    }

    /**
     * Notifies the context that the given resource has been scanned.
     *
     * @param resource the resource that was scanned
     */
    public void finishScanning(IResource resource) {
        assert mCurrentFile != null;
        mCurrentFile = null;
    }

    /**
     * Process any errors found to add error markers in the affected files (and
     * also clear up any aapt errors in files that are no longer applicable)
     *
     * @param async if true, delay updating markers until the next display
     *            thread event loop update
     */
    public void updateMarkers(boolean async) {
        // Run asynchronously? This is necessary for example when adding markers
        // as the result of a resource change notification, since at that point the
        // resource tree is locked for modifications and attempting to create a
        // marker will throw a org.eclipse.core.internal.resources.ResourceException.
        if (async) {
            AdtPlugin.getDisplay().asyncExec(new Runnable() {
                public void run() {
                    updateMarkers(false);
                }
            });
            return;
        }

        // First clear out old/previous markers
        for (IResource resource :mScannedResources) {
            try {
                if (resource.exists()) {
                    int depth = resource instanceof IFolder ? DEPTH_ONE : DEPTH_ZERO;
                    resource.deleteMarkers(MARKER_AAPT_COMPILE, true, depth);
                }
            } catch (CoreException ce) {
                // Pass
            }
        }

        // Add new errors
        if (mErrors != null && mErrors.size() > 0) {
            List<String> errors = new ArrayList<String>();
            for (Pair<IResource, String> pair : mErrors) {
                errors.add(pair.getSecond());
            }
            AaptParser.parseOutput(errors, mProject);
        }
    }

    @Override
    public boolean needsFullAapt() {
        return super.needsFullAapt();
    }

    @Override
    protected void requestFullAapt() {
        super.requestFullAapt();

        if (mCurrentFile != null) {
            if (mFullAaptProjects == null) {
                mFullAaptProjects = new HashSet<IProject>();
            }
            mFullAaptProjects.add(mCurrentFile.getProject());
        } else {
            assert false : "No current context to apply IdeScanningContext to";
        }
    }

    /**
     * Returns the collection of projects that scanned resources have requested
     * a full aapt for.
     *
     * @return a collection of projects that scanned resources requested full
     *         aapt runs for, or null
     */
    public Collection<IProject> getAaptRequestedProjects() {
        return mFullAaptProjects;
    }
}
