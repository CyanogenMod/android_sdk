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
import com.android.ide.eclipse.adt.AndroidPrintStream;
import com.android.ide.eclipse.adt.internal.sdk.ProjectState;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.build.JarListSanitizer;
import com.android.sdklib.build.JarListSanitizer.DifferentLibException;
import com.android.sdklib.build.JarListSanitizer.Sha1Exception;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LibraryClasspathContainerInitializer extends BaseClasspathContainerInitializer {

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
                containers[i] = allocateLibraryContainer(androidProjects[i]);
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

    /**
     * Updates the {@link IJavaProject} objects with new library.
     * @param androidProjects the projects to update.
     * @return <code>true</code> if success, <code>false</code> otherwise.
     */
    public static boolean updateProject(List<ProjectState> projects) {
        List<IJavaProject> javaProjectList = new ArrayList<IJavaProject>(projects.size());
        for (ProjectState p : projects) {
            IJavaProject javaProject = JavaCore.create(p.getProject());
            if (javaProject != null) {
                javaProjectList.add(javaProject);
            }
        }

        IJavaProject[] javaProjects = javaProjectList.toArray(
                new IJavaProject[javaProjectList.size()]);

        return updateProjects(javaProjects);
    }

    @Override
    public void initialize(IPath containerPath, IJavaProject project) throws CoreException {
        if (AdtConstants.CONTAINER_LIBRARIES.equals(containerPath.toString())) {
            IClasspathContainer container = allocateLibraryContainer(project);
            if (container != null) {
                JavaCore.setClasspathContainer(new Path(AdtConstants.CONTAINER_LIBRARIES),
                        new IJavaProject[] { project },
                        new IClasspathContainer[] { container },
                        new NullProgressMonitor());
            }
        }
    }

    private static IClasspathContainer allocateLibraryContainer(IJavaProject javaProject) {
        final IProject iProject = javaProject.getProject();

        AdtPlugin plugin = AdtPlugin.getDefault();
        if (plugin == null) { // This is totally weird, but I've seen it happen!
            return null;
        }

        // First check that the project has a library-type container.
        try {
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

            // if there isn't any, add it.
            if (foundLibrariesContainer == false) {
                // add the android container to the array
                rawClasspath = ProjectHelper.addEntryToClasspath(rawClasspath,
                        JavaCore.newContainerEntry(new Path(AdtConstants.CONTAINER_LIBRARIES),
                                true /*isExported*/));
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

        /*
         * At this point we're going to gather a list of all that need to go in the
         * dependency container.
         * - Library project outputs (direct and indirect)
         * - Java project output (those can be indirectly referenced through library projects
         *   or other other Java projects)
         * - Jar files:
         *    + inside this project's libs/
         *    + inside the library projects' libs/
         *    + inside the referenced Java projects' classpath
         */

        List<IClasspathEntry> entries = new ArrayList<IClasspathEntry>();

        IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();

        // list of java project dependencies and jar files that will be built while
        // going through the library projects.
        Set<File> jarFiles = new HashSet<File>();
        Set<IProject> refProjects = new HashSet<IProject>();

        // process all the libraries

        List<IProject> libProjects = state.getFullLibraryProjects();
        for (IProject libProject : libProjects) {
            // get the project output
            IFolder outputFolder = BaseProjectHelper.getAndroidOutputFolder(libProject);

            if (outputFolder != null) { // can happen when closing/deleting a library)
                IFile jarIFile = outputFolder.getFile(libProject.getName().toLowerCase() +
                        AdtConstants.DOT_JAR);

                // get the source folder for the library project
                List<IPath> srcs = BaseProjectHelper.getSourceClasspaths(libProject);
                // find the first non-derived source folder.
                IPath sourceFolder = null;
                for (IPath src : srcs) {
                    IFolder srcFolder = workspaceRoot.getFolder(src);
                    if (srcFolder.isDerived() == false) {
                        sourceFolder = src;
                        break;
                    }
                }

                // we can directly add a CPE for this jar as there's no risk of a duplicate.
                IClasspathEntry entry = JavaCore.newLibraryEntry(
                        jarIFile.getLocation(),
                        sourceFolder, // source attachment path
                        null,         // default source attachment root path.
                        true /*isExported*/);

                entries.add(entry);

                // now, gather the content of this library project's libs folder.
                getJarListFromLibsFolder(libProject, jarFiles);
            }

            // get project dependencies
            processReferencedProjects(libProject, refProjects, jarFiles);
        }

        // now process this projects' referenced projects
        processReferencedProjects(iProject, refProjects, jarFiles);
        // and its own jar files from libs
        getJarListFromLibsFolder(iProject, jarFiles);

        // annotations support for older version of android
        if (state.getTarget() != null && state.getTarget().getVersion().getApiLevel() <= 15) {
            File annotationsJar = new File(Sdk.getCurrent().getSdkLocation(),
                    SdkConstants.FD_TOOLS + File.separator + SdkConstants.FD_SUPPORT +
                    File.separator + SdkConstants.FN_ANNOTATIONS_JAR);

            jarFiles.add(annotationsJar);
        }

        // now add a classpath entry for each Java project (this is a set so dups are already
        // removed)
        for (IProject p : refProjects) {
            entries.add(JavaCore.newProjectEntry(p.getFullPath(), true /*isExported*/));
        }

        // and process the jar files list, but first sanitize it to remove dups.
        JarListSanitizer sanitizer = new JarListSanitizer(
                iProject.getFolder(SdkConstants.FD_OUTPUT).getLocation().toFile(),
                new AndroidPrintStream(iProject, null /*prefix*/,
                        AdtPlugin.getOutStream()));

        String errorMessage = null;

        try {
            List<File> sanitizedList = sanitizer.sanitize(jarFiles);

            for (File jarFile : sanitizedList) {
                if (jarFile instanceof CPEFile) {
                    CPEFile cpeFile = (CPEFile) jarFile;
                    IClasspathEntry e = cpeFile.getClasspathEntry();

                    entries.add(JavaCore.newLibraryEntry(
                            e.getPath(),
                            e.getSourceAttachmentPath(),
                            e.getSourceAttachmentRootPath(),
                            e.getAccessRules(),
                            e.getExtraAttributes(),
                            true /*isExported*/));
                } else {
                    entries.add(JavaCore.newLibraryEntry(new Path(jarFile.getAbsolutePath()),
                            null /*sourceAttachmentPath*/, null /*sourceAttachmentRootPath*/,
                            true /*isExported*/));
                }
            }
        } catch (DifferentLibException e) {
            errorMessage = e.getMessage();
            AdtPlugin.printErrorToConsole(iProject, (Object[]) e.getDetails());
        } catch (Sha1Exception e) {
            errorMessage = e.getMessage();
        }

        processError(iProject, errorMessage, AdtConstants.MARKER_DEPENDENCY,
                true /*outputToConsole*/);

        return new AndroidClasspathContainer(
                entries.toArray(new IClasspathEntry[entries.size()]),
                new Path(AdtConstants.CONTAINER_LIBRARIES),
                "Android Dependencies",
                IClasspathContainer.K_APPLICATION);
    }


    private static void processReferencedProjects(IProject project,
            Set<IProject> projects, Set<File> jarFiles) {
        try {
            IProject[] refs = project.getReferencedProjects();
            for (IProject p : refs) {
                // ignore if it's an Android project, or if it's not a Java Project
                if (p.hasNature(JavaCore.NATURE_ID) &&
                        p.hasNature(AdtConstants.NATURE_DEFAULT) == false) {
                    // add this project to the list
                    projects.add(p);

                    // get the jar dependencies of the project in the list
                    getJarListFromClasspath(p, jarFiles);

                    // and then process the referenced project by this project too.
                    processReferencedProjects(p, projects, jarFiles);
                }
            }
        } catch (CoreException e) {
            // can't get the referenced projects? ignore
        }
    }

    /**
     * Finds all the jar files inside a project's libs folder.
     * @param project
     * @param jarFiles
     */
    private static void getJarListFromLibsFolder(IProject project, Set<File> jarFiles) {
        IFolder libsFolder = project.getFolder(SdkConstants.FD_NATIVE_LIBS);
        if (libsFolder.exists()) {
            try {
                IResource[] members = libsFolder.members();
                for (IResource member : members) {
                    if (member.getType() == IResource.FILE &&
                            AdtConstants.EXT_JAR.equalsIgnoreCase(member.getFileExtension())) {
                        jarFiles.add(member.getLocation().toFile());
                    }
                }
            } catch (CoreException e) {
                // can't get the list? ignore this folder.
            }
        }
    }

    /**
     * Finds all the jars a given project depends on by looking at the classpath.
     * This must be a non android project, as android project have container that really
     * we shouldn't go through.
     * @param project the project to query
     * @param jarFiles the list of file to fill.
     */
    private static void getJarListFromClasspath(IProject project, Set<File> jarFiles) {
        IJavaProject javaProject = JavaCore.create(project);
        IWorkspaceRoot wsRoot = ResourcesPlugin.getWorkspace().getRoot();

        // we could use IJavaProject.getResolvedClasspath directly, but we actually
        // want to see the containers themselves.
        IClasspathEntry[] classpaths = javaProject.readRawClasspath();
        if (classpaths != null) {
            for (IClasspathEntry e : classpaths) {
                // if this is a classpath variable reference, we resolve it.
                if (e.getEntryKind() == IClasspathEntry.CPE_VARIABLE) {
                    e = JavaCore.getResolvedClasspathEntry(e);
                }

                if (e.getEntryKind() == IClasspathEntry.CPE_LIBRARY) {
                    handleClasspathEntry(e, wsRoot, jarFiles);
                } else if (e.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
                    // get the container.
                    try {
                        IClasspathContainer container = JavaCore.getClasspathContainer(
                                e.getPath(), javaProject);
                        // ignore the system and default_system types as they represent
                        // libraries that are part of the runtime.
                        if (container != null &&
                                container.getKind() == IClasspathContainer.K_APPLICATION) {
                            IClasspathEntry[] entries = container.getClasspathEntries();
                            for (IClasspathEntry entry : entries) {
                                handleClasspathEntry(entry, wsRoot, jarFiles);
                            }
                        }
                    } catch (JavaModelException jme) {
                        // can't resolve the container? ignore it.
                        AdtPlugin.log(jme, "Failed to resolve ClasspathContainer: %s", e.getPath());
                    }
                }
            }
        }
    }

    private static final class CPEFile extends File {
        private static final long serialVersionUID = 1L;

        private final IClasspathEntry mClasspathEntry;

        public CPEFile(String pathname, IClasspathEntry classpathEntry) {
            super(pathname);
            mClasspathEntry = classpathEntry;
        }

        public CPEFile(File file, IClasspathEntry classpathEntry) {
            super(file.getAbsolutePath());
            mClasspathEntry = classpathEntry;
        }

        public IClasspathEntry getClasspathEntry() {
            return mClasspathEntry;
        }
    }

    private static void handleClasspathEntry(IClasspathEntry e, IWorkspaceRoot wsRoot,
            Set<File> jarFiles) {
        // get the IPath
        IPath path = e.getPath();

        IResource resource = wsRoot.findMember(path);

        if (AdtConstants.EXT_JAR.equalsIgnoreCase(path.getFileExtension())) {
            // case of a jar file (which could be relative to the workspace or a full path)
            if (resource != null && resource.exists() &&
                    resource.getType() == IResource.FILE) {
                jarFiles.add(new CPEFile(resource.getLocation().toFile(), e));
            } else {
                // if the jar path doesn't match a workspace resource,
                // then we get an OSString and check if this links to a valid file.
                String osFullPath = path.toOSString();

                File f = new CPEFile(osFullPath, e);
                if (f.isFile()) {
                    jarFiles.add(f);
                }
            }
        }
    }
}
