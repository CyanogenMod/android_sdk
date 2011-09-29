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

package com.android.ide.eclipse.adt.internal.project;

import com.android.ide.eclipse.adt.AdtConstants;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.sdk.ProjectState;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.ClasspathContainerInitializer;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import java.util.ArrayList;
import java.util.List;

public class LibraryClasspathContainerInitializer extends ClasspathContainerInitializer {

    public LibraryClasspathContainerInitializer() {
    }

    /**
     * Updates the {@link IJavaProject} objects with new library.
     * @param androidProjects the projects to update.
     * @return <code>true</code> if success, <code>false</code> otherwise.
     */
    public static boolean updateProjects(IJavaProject[] androidProjects) {
        try {
            // Allocate a new AndroidClasspathContainer, and associate it to the library
            // container id for each projects.
            int projectCount = androidProjects.length;

            IClasspathContainer[] containers = new IClasspathContainer[projectCount];
            for (int i = 0 ; i < projectCount; i++) {
                containers[i] = allocateAndroidContainer(androidProjects[i]);
            }

            // give each project their new container in one call.
            JavaCore.setClasspathContainer(
                    new Path(AdtConstants.CONTAINER_LIBRARIES),
                    androidProjects, containers, new NullProgressMonitor());

            return true;
        } catch (JavaModelException e) {
            return false;
        }
    }

    @Override
    public void initialize(IPath containerPath, IJavaProject project) throws CoreException {
        if (AdtConstants.CONTAINER_LIBRARIES.equals(containerPath.toString())) {
            IClasspathContainer container = allocateAndroidContainer(project);
            if (container != null) {
                JavaCore.setClasspathContainer(new Path(AdtConstants.CONTAINER_LIBRARIES),
                        new IJavaProject[] { project },
                        new IClasspathContainer[] { container },
                        new NullProgressMonitor());
            }
        }
    }

    private static IClasspathContainer allocateAndroidContainer(IJavaProject javaProject) {
        final IProject iProject = javaProject.getProject();

        AdtPlugin plugin = AdtPlugin.getDefault();
        if (plugin == null) { // This is totally weird, but I've seen it happen!
            return null;
        }

        try {
            // First check that the project has a library-type container.
            IClasspathEntry[] rawClasspath = javaProject.getRawClasspath();
            IClasspathEntry[] oldRawClasspath = rawClasspath;

            boolean foundLibrariesContainer = false;
            for (IClasspathEntry entry : rawClasspath) {
                // get the entry and kind
                int kind = entry.getEntryKind();

                if (kind == IClasspathEntry.CPE_CONTAINER) {
                    String path = entry.getPath().toString();
                    if (AdtConstants.CONTAINER_LIBRARIES.equals(path)) {
                        foundLibrariesContainer = true;
                        break;
                    }
                }
            }

            // same thing for the library container
            if (foundLibrariesContainer == false) {
                // add the android container to the array
                rawClasspath = ProjectHelper.addEntryToClasspath(rawClasspath,
                        JavaCore.newContainerEntry(new Path(AdtConstants.CONTAINER_LIBRARIES)));
            }

            // set the new list of entries to the project
            if (rawClasspath != oldRawClasspath) {
                javaProject.setRawClasspath(rawClasspath, new NullProgressMonitor());
            }
        } catch (JavaModelException e) {
            // This really shouldn't happen, but if it does, simply return null (the calling
            // method will fails as well)
            return null;
        }

        // check if the project has a valid target.
        ProjectState state = Sdk.getProjectState(iProject);

        List<IClasspathEntry> entries = new ArrayList<IClasspathEntry>();

        List<IProject> libProjects = state.getFullLibraryProjects();
        for (IProject libProject : libProjects) {
            // get the project output
            IFolder outputFolder = BaseProjectHelper.getAndroidOutputFolder(libProject);

            if (outputFolder != null) { // can happen when closing/deleting a library)
                IFile jarIFile = outputFolder.getFile(libProject.getName().toLowerCase() +
                        AdtConstants.DOT_JAR);

                IClasspathEntry entry = JavaCore.newLibraryEntry(
                        jarIFile.getLocation(),
                        libProject.getLocation(), // source attachment path
                        null);                    // default source attachment root path.

                entries.add(entry);
            }
        }

        return new AndroidClasspathContainer(
                entries.toArray(new IClasspathEntry[entries.size()]),
                new Path(AdtConstants.CONTAINER_LIBRARIES),
                "Library Projects",
                IClasspathContainer.K_APPLICATION);
    }

}
