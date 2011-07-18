/*
 * Copyright (C) 2007 The Android Open Source Project
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

import com.android.ide.common.resources.FrameworkResources;
import com.android.ide.common.resources.ResourceFile;
import com.android.ide.common.resources.ResourceFolder;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.eclipse.adt.AdtConstants;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.resources.ResourceHelper;
import com.android.ide.eclipse.adt.internal.resources.manager.GlobalProjectMonitor.IProjectListener;
import com.android.ide.eclipse.adt.internal.resources.manager.GlobalProjectMonitor.IRawDeltaListener;
import com.android.ide.eclipse.adt.internal.resources.manager.GlobalProjectMonitor.IResourceEventListener;
import com.android.ide.eclipse.adt.io.IFileWrapper;
import com.android.ide.eclipse.adt.io.IFolderWrapper;
import com.android.io.FolderWrapper;
import com.android.resources.ResourceFolderType;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkConstants;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarkerDelta;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * The ResourceManager tracks resources for all opened projects.
 * <p/>
 * It provide direct access to all the resources of a project as a {@link ProjectResources}
 * object that allows accessing the resources through their file representation or as Android
 * resources (similar to what is seen by an Android application).
 * <p/>
 * The ResourceManager automatically tracks file changes to update its internal representation
 * of the resources so that they are always up to date.
 * <p/>
 * It also gives access to a monitor that is more resource oriented than the
 * {@link GlobalProjectMonitor}.
 * This monitor will let you track resource changes by giving you direct access to
 * {@link ResourceFile}, or {@link ResourceFolder}.
 *
 * @see ProjectResources
 */
public final class ResourceManager {
    public final static boolean DEBUG = false;

    private final static ResourceManager sThis = new ResourceManager();

    /**
     * Map associating project resource with project objects.
     * <p/><b>All accesses must be inside a synchronized(mMap) block</b>, and do as a little as
     * possible and <b>not call out to other classes</b>.
     */
    private final HashMap<IProject, ProjectResources> mMap =
        new HashMap<IProject, ProjectResources>();

    /**
     * Interface to be notified of resource changes.
     *
     * @see ResourceManager#addListener(IResourceListener)
     * @see ResourceManager#removeListener(IResourceListener)
     */
    public interface IResourceListener {
        /**
         * Notification for resource file change.
         * @param project the project of the file.
         * @param file the {@link ResourceFile} representing the file.
         * @param eventType the type of event. See {@link IResourceDelta}.
         */
        void fileChanged(IProject project, ResourceFile file, int eventType);
        /**
         * Notification for resource folder change.
         * @param project the project of the file.
         * @param folder the {@link ResourceFolder} representing the folder.
         * @param eventType the type of event. See {@link IResourceDelta}.
         */
        void folderChanged(IProject project, ResourceFolder folder, int eventType);
    }

    private final ArrayList<IResourceListener> mListeners = new ArrayList<IResourceListener>();

    /**
     * Sets up the resource manager with the global project monitor.
     * @param monitor The global project monitor
     */
    public static void setup(GlobalProjectMonitor monitor) {
        monitor.addResourceEventListener(sThis.mResourceEventListener);
        monitor.addProjectListener(sThis.mProjectListener);
        monitor.addRawDeltaListener(sThis.mRawDeltaListener);

        CompiledResourcesMonitor.setupMonitor(monitor);
    }

    /**
     * Returns the singleton instance.
     */
    public static ResourceManager getInstance() {
        return sThis;
    }

    /**
     * Adds a new {@link IResourceListener} to be notified of resource changes.
     * @param listener the listener to be added.
     */
    public void addListener(IResourceListener listener) {
        synchronized (mListeners) {
            mListeners.add(listener);
        }
    }

    /**
     * Removes an {@link IResourceListener}, so that it's not notified of resource changes anymore.
     * @param listener the listener to be removed.
     */
    public void removeListener(IResourceListener listener) {
        synchronized (mListeners) {
            mListeners.remove(listener);
        }
    }

    /**
     * Returns the resources of a project.
     * @param project The project
     * @return a ProjectResources object or null if none was found.
     */
    public ProjectResources getProjectResources(IProject project) {
        synchronized (mMap) {
            return mMap.get(project);
        }
    }

    /**
     * Update the resource repository with a delta
     * @param delta the resource changed delta to process.
     */
    public void processDelta(IResourceDelta delta) {
        // Skip over deltas that don't fit our mask
        int mask = IResourceDelta.ADDED | IResourceDelta.REMOVED | IResourceDelta.CHANGED;
        int kind = delta.getKind();
        if ( (mask & kind) == 0) {
            return;
        }
        // If our delta was handed to us from the PreCompiler then it's a single delta
        // with lots of children. GlobalProjectMonitor will hand us a delta for each
        // item in the tree of modifications so we only need to recurse into delta
        // children if we're autobuilding.
        if (ResourcesPlugin.getWorkspace().getDescription().isAutoBuilding()) {
            IResourceDelta[] children = delta.getAffectedChildren();
            for (IResourceDelta child : children)  {
                processDelta(child);
            }
        }

        // Process this delta
        IResource r = delta.getResource();
        int type = r.getType();

        if (type == IResource.FILE) {
            updateFile((IFile)r, delta.getMarkerDeltas(), kind);
        } else if (type == IResource.FOLDER) {
            updateFolder((IFolder)r, kind);
        } // We only care about files and folders.
          // Project deltas are handled by our project listener
    }

    /**
     * Private implementation of a resource event listener that registers with
     * GlobalProjectMonitor.
     *
     */
    private class ResourceEventListener implements IResourceEventListener {
        private final List<IProject> mChangedProjects = new ArrayList<IProject>();

        public void resourceChangeEventEnd() {
            for (IProject project : mChangedProjects) {
                ProjectResources resources;
                synchronized (mMap) {
                    resources = mMap.get(project);
                }
            }

            mChangedProjects.clear();
        }

        public void resourceChangeEventStart() {
            // pass
        }

        void addProject(IProject project) {
            if (mChangedProjects.contains(project) == false) {
                mChangedProjects.add(project);
            }
        }
    }

    /**
     * Delegate listener for resource changes. This is called before and after any calls to the
     * project and file listeners (for a given resource change event).
     */
    private final ResourceEventListener mResourceEventListener = new ResourceEventListener();

    /**
     * Update a resource folder that we know about
     * @param folder the folder that was updated
     * @param kind the delta type (added/removed/updated)
     */
    private void updateFolder(IFolder folder, int kind) {
        ProjectResources resources;

        final IProject project = folder.getProject();

        try {
            if (project.hasNature(AdtConstants.NATURE_DEFAULT) == false) {
                return;
            }
        } catch (CoreException e) {
            // can't get the project nature? return!
            return;
        }

        mResourceEventListener.addProject(project);

        switch (kind) {
            case IResourceDelta.ADDED:
                // checks if the folder is under res.
                IPath path = folder.getFullPath();

                // the path will be project/res/<something>
                if (path.segmentCount() == 3) {
                    if (isInResFolder(path)) {
                        // get the project and its resource object.
                        synchronized (mMap) {
                            resources = mMap.get(project);

                            // if it doesn't exist, we create it.
                            if (resources == null) {
                                resources = new ProjectResources(project);
                                mMap.put(project, resources);
                            }
                        }

                        ResourceFolder newFolder = resources.processFolder(
                                new IFolderWrapper(folder));
                        if (newFolder != null) {
                            notifyListenerOnFolderChange(project, newFolder, kind);
                        }
                    }
                }
                break;
            case IResourceDelta.CHANGED:
                // only call the listeners.
                synchronized (mMap) {
                    resources = mMap.get(folder.getProject());
                }
                if (resources != null) {
                    ResourceFolder resFolder = resources.getResourceFolder(folder);
                    if (resFolder != null) {
                        notifyListenerOnFolderChange(project, resFolder, kind);
                    }
                }
                break;
            case IResourceDelta.REMOVED:
                synchronized (mMap) {
                    resources = mMap.get(folder.getProject());
                }
                if (resources != null) {
                    // lets get the folder type
                    ResourceFolderType type = ResourceFolderType.getFolderType(
                            folder.getName());

                    ResourceFolder removedFolder = resources.removeFolder(type,
                            new IFolderWrapper(folder));
                    if (removedFolder != null) {
                        notifyListenerOnFolderChange(project, removedFolder, kind);
                    }
                }
                break;
        }
    }

    /**
     * Called when a delta indicates that a file has changed.
     * Depending on the file being changed, and the type of change
     * (ADDED, REMOVED, CHANGED), the file change is processed to update the resource
     * manager data.
     *
     * @param file The file that changed.
     * @param markerDeltas The marker deltas for the file.
     * @param kind The change kind. This is equivalent to
     * {@link IResourceDelta#accept(IResourceDeltaVisitor)}
     */
    private void updateFile(IFile file, IMarkerDelta[] markerDeltas, int kind) {
        final IProject project = file.getProject();

        try {
            if (project.hasNature(AdtConstants.NATURE_DEFAULT) == false) {
                return;
            }
        } catch (CoreException e) {
            // can't get the project nature? return!
            return;
        }

        // get the project resources
        ProjectResources resources;
        synchronized (mMap) {
            resources = mMap.get(project);
        }

        if (resources == null) {
            return;
        }

        // checks if the file is under res/something.
        IPath path = file.getFullPath();

        if (path.segmentCount() == 4) {
            if (isInResFolder(path)) {
                IContainer container = file.getParent();
                if (container instanceof IFolder) {

                    ResourceFolder folder = resources.getResourceFolder(
                            (IFolder)container);

                    // folder can be null as when the whole folder is deleted, the
                    // REMOVED event for the folder comes first. In this case, the
                    // folder will have taken care of things.
                    if (folder != null) {
                        ResourceFile resFile = folder.processFile(
                                new IFileWrapper(file),
                                ResourceHelper.getResourceDeltaKind(kind));
                        notifyListenerOnFileChange(project, resFile, kind);
                    }
                }
            }
        }
    }

    /**
     * Implementation of the {@link IProjectListener} as an internal class so that the methods
     * do not appear in the public API of {@link ResourceManager}.
     */
    private final IProjectListener mProjectListener = new IProjectListener() {
        public void projectClosed(IProject project) {
            synchronized (mMap) {
                mMap.remove(project);
            }
        }

        public void projectDeleted(IProject project) {
            synchronized (mMap) {
                mMap.remove(project);
            }
        }

        public void projectOpened(IProject project) {
            createProject(project);
        }

        public void projectOpenedWithWorkspace(IProject project) {
            createProject(project);
        }

        public void projectRenamed(IProject project, IPath from) {
            // renamed project get a delete/open event too, so this can be ignored.
        }
    };

    /**
     * Implementation of {@link IRawDeltaListener} as an internal class so that the methods
     * do not appear in the public API of {@link ResourceManager}. Delta processing can be
     * accessed through the {@link ResourceManager#visitDelta(IResourceDelta delta)} method.
     */
    private final IRawDeltaListener mRawDeltaListener = new IRawDeltaListener() {
        public void visitDelta(IResourceDelta delta) {
            // If we're autobuilding, then PreCompilerBuilder will pass us deltas
            if (ResourcesPlugin.getWorkspace().getDescription().isAutoBuilding()) {
                return;
            }
            processDelta(delta);
        }
    };

    /**
     * Returns the {@link ResourceFolder} for the given file or <code>null</code> if none exists.
     */
    public ResourceFolder getResourceFolder(IFile file) {
        IContainer container = file.getParent();
        if (container.getType() == IResource.FOLDER) {
            IFolder parent = (IFolder)container;
            IProject project = file.getProject();

            ProjectResources resources = getProjectResources(project);
            if (resources != null) {
                return resources.getResourceFolder(parent);
            }
        }

        return null;
    }

    /**
     * Returns the {@link ResourceFolder} for the given folder or <code>null</code> if none exists.
     */
    public ResourceFolder getResourceFolder(IFolder folder) {
        IProject project = folder.getProject();

        ProjectResources resources = getProjectResources(project);
        if (resources != null) {
            return resources.getResourceFolder(folder);
        }

        return null;
    }

    /**
     * Loads and returns the resources for a given {@link IAndroidTarget}
     * @param androidTarget the target from which to load the framework resources
     */
    public ResourceRepository loadFrameworkResources(IAndroidTarget androidTarget) {
        String osResourcesPath = androidTarget.getPath(IAndroidTarget.RESOURCES);

        FolderWrapper frameworkRes = new FolderWrapper(osResourcesPath);
        if (frameworkRes.exists()) {
            FrameworkResources resources = new FrameworkResources();

            try {
                resources.loadResources(frameworkRes);
                resources.loadPublicResources(frameworkRes, AdtPlugin.getDefault());
                return resources;
            } catch (IOException e) {
                // since we test that folders are folders, and files are files, this shouldn't
                // happen. We can ignore it.
            }
        }

        return null;
    }

    /**
     * Initial project parsing to gather resource info.
     * @param project
     */
    private void createProject(IProject project) {
        if (project.isOpen()) {
            try {
                if (project.hasNature(AdtConstants.NATURE_DEFAULT) == false) {
                    return;
                }
            } catch (CoreException e1) {
                // can't check the nature of the project? ignore it.
                return;
            }

            IFolder resourceFolder = project.getFolder(SdkConstants.FD_RESOURCES);

            ProjectResources projectResources;
            synchronized (mMap) {
                projectResources = mMap.get(project);
                if (projectResources == null) {
                    projectResources = new ProjectResources(project);
                    mMap.put(project, projectResources);
                }
            }

            if (resourceFolder != null && resourceFolder.exists()) {
                try {
                    IResource[] resources = resourceFolder.members();

                    for (IResource res : resources) {
                        if (res.getType() == IResource.FOLDER) {
                            IFolder folder = (IFolder)res;
                            ResourceFolder resFolder = projectResources.processFolder(
                                    new IFolderWrapper(folder));

                            if (resFolder != null) {
                                // now we process the content of the folder
                                IResource[] files = folder.members();

                                for (IResource fileRes : files) {
                                    if (fileRes.getType() == IResource.FILE) {
                                        IFile file = (IFile)fileRes;

                                        resFolder.processFile(new IFileWrapper(file),
                                                ResourceHelper.getResourceDeltaKind(
                                                        IResourceDelta.ADDED));
                                    }
                                }
                            }
                        }
                    }
                } catch (CoreException e) {
                    // This happens if the project is closed or if the folder doesn't exist.
                    // Since we already test for that, we can ignore this exception.
                }
            }
        }
    }


    /**
     * Returns true if the path is under /project/res/
     * @param path a workspace relative path
     * @return true if the path is under /project res/
     */
    private boolean isInResFolder(IPath path) {
        return SdkConstants.FD_RESOURCES.equalsIgnoreCase(path.segment(1));
    }

    private void notifyListenerOnFolderChange(IProject project, ResourceFolder folder,
            int eventType) {
        synchronized (mListeners) {
            for (IResourceListener listener : mListeners) {
                try {
                    listener.folderChanged(project, folder, eventType);
                } catch (Throwable t) {
                    AdtPlugin.log(t,
                            "Failed to execute ResourceManager.IResouceListener.folderChanged()"); //$NON-NLS-1$
                }
            }
        }
    }

    private void notifyListenerOnFileChange(IProject project, ResourceFile file, int eventType) {
        synchronized (mListeners) {
            for (IResourceListener listener : mListeners) {
                try {
                    listener.fileChanged(project, file, eventType);
                } catch (Throwable t) {
                    AdtPlugin.log(t,
                            "Failed to execute ResourceManager.IResouceListener.fileChanged()"); //$NON-NLS-1$
                }
            }
        }
    }

    /**
     * Private constructor to enforce singleton design.
     */
    private ResourceManager() {
    }

    // debug only
    @SuppressWarnings("unused")
    private String getKindString(int kind) {
        if (DEBUG) {
            switch (kind) {
                case IResourceDelta.ADDED: return "ADDED";
                case IResourceDelta.REMOVED: return "REMOVED";
                case IResourceDelta.CHANGED: return "CHANGED";
            }
        }

        return Integer.toString(kind);
    }
}
