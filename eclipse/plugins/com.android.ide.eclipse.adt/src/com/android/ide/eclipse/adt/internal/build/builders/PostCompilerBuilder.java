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

package com.android.ide.eclipse.adt.internal.build.builders;

import com.android.ide.eclipse.adt.AdtConstants;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.AndroidPrintStream;
import com.android.ide.eclipse.adt.internal.build.AaptExecException;
import com.android.ide.eclipse.adt.internal.build.AaptParser;
import com.android.ide.eclipse.adt.internal.build.AaptResultException;
import com.android.ide.eclipse.adt.internal.build.BuildHelper;
import com.android.ide.eclipse.adt.internal.build.DexException;
import com.android.ide.eclipse.adt.internal.build.Messages;
import com.android.ide.eclipse.adt.internal.build.NativeLibInJarException;
import com.android.ide.eclipse.adt.internal.build.BuildHelper.ResourceMarker;
import com.android.ide.eclipse.adt.internal.preferences.AdtPrefs;
import com.android.ide.eclipse.adt.internal.preferences.AdtPrefs.BuildVerbosity;
import com.android.ide.eclipse.adt.internal.project.ApkInstallManager;
import com.android.ide.eclipse.adt.internal.project.BaseProjectHelper;
import com.android.ide.eclipse.adt.internal.project.ProjectHelper;
import com.android.ide.eclipse.adt.internal.sdk.ProjectState;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.prefs.AndroidLocation.AndroidLocationException;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.build.ApkCreationException;
import com.android.sdklib.build.DuplicateFileException;
import com.android.sdklib.build.IArchiveBuilder;
import com.android.sdklib.build.SealedApkException;
import com.android.sdklib.internal.build.DebugKeyProvider.KeytoolException;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

public class PostCompilerBuilder extends BaseBuilder {

    /** This ID is used in plugin.xml and in each project's .project file.
     * It cannot be changed even if the class is renamed/moved */
    public static final String ID = "com.android.ide.eclipse.adt.ApkBuilder"; //$NON-NLS-1$

    private static final String PROPERTY_CONVERT_TO_DEX = "convertToDex"; //$NON-NLS-1$
    private static final String PROPERTY_UPDATE_CRUNCH_CACHE = "updateCrunchCache"; //$NON-NLS-1$
    private static final String PROPERTY_PACKAGE_RESOURCES = "packageResources"; //$NON-NLS-1$
    private static final String PROPERTY_BUILD_APK = "buildApk"; //$NON-NLS-1$

    /** Flag to pass to PostCompiler builder that sets if it runs or not.
     *  Set this flag whenever calling build if PostCompiler is to run
     */
    public final static String POST_C_REQUESTED = "RunPostCompiler"; //$NON-NLS-1$

    /**
     * Dex conversion flag. This is set to true if one of the changed/added/removed
     * file is a .class file. Upon visiting all the delta resource, if this
     * flag is true, then we know we'll have to make the "classes.dex" file.
     */
    private boolean mConvertToDex = false;

    /**
     * PNG Cache update flag. This is set to true if one of the changed/added/removed
     * files is a .png file. Upon visiting all the delta resources, if this
     * flag is true, then we know we'll have to update the PNG cache
     */
    private boolean mUpdateCrunchCache = false;

    /**
     * Package resources flag. This is set to true if one of the changed/added/removed
     * file is a resource file. Upon visiting all the delta resource, if
     * this flag is true, then we know we'll have to repackage the resources.
     */
    private boolean mPackageResources = false;

    /**
     * Final package build flag.
     */
    private boolean mBuildFinalPackage = false;

    private AndroidPrintStream mOutStream = null;
    private AndroidPrintStream mErrStream = null;

    /**
     * Basic Resource Delta Visitor class to check if a referenced project had a change in its
     * compiled java files.
     */
    private static class ReferencedProjectDeltaVisitor implements IResourceDeltaVisitor {

        private boolean mConvertToDex = false;
        private boolean mMakeFinalPackage;

        private IPath mOutputFolder;
        private List<IPath> mSourceFolders;

        private ReferencedProjectDeltaVisitor(IJavaProject javaProject) {
            try {
                mOutputFolder = javaProject.getOutputLocation();
                mSourceFolders = BaseProjectHelper.getSourceClasspaths(javaProject);
            } catch (JavaModelException e) {
            }
        }

        /**
         * {@inheritDoc}
         * @throws CoreException
         */
        public boolean visit(IResourceDelta delta) throws CoreException {
            //  no need to keep looking if we already know we need to convert
            // to dex and make the final package.
            if (mConvertToDex && mMakeFinalPackage) {
                return false;
            }

            // get the resource and the path segments.
            IResource resource = delta.getResource();
            IPath resourceFullPath = resource.getFullPath();

            if (mOutputFolder.isPrefixOf(resourceFullPath)) {
                int type = resource.getType();
                if (type == IResource.FILE) {
                    String ext = resource.getFileExtension();
                    if (AdtConstants.EXT_CLASS.equals(ext)) {
                        mConvertToDex = true;
                    }
                }
                return true;
            } else {
                for (IPath sourceFullPath : mSourceFolders) {
                    if (sourceFullPath.isPrefixOf(resourceFullPath)) {
                        int type = resource.getType();
                        if (type == IResource.FILE) {
                            // check if the file is a valid file that would be
                            // included during the final packaging.
                            if (BuildHelper.checkFileForPackaging((IFile)resource)) {
                                mMakeFinalPackage = true;
                            }

                            return false;
                        } else if (type == IResource.FOLDER) {
                            // if this is a folder, we check if this is a valid folder as well.
                            // If this is a folder that needs to be ignored, we must return false,
                            // so that we ignore its content.
                            return BuildHelper.checkFolderForPackaging((IFolder)resource);
                        }
                    }
                }
            }

            return true;
        }

        /**
         * Returns if one of the .class file was modified.
         */
        boolean needDexConvertion() {
            return mConvertToDex;
        }

        boolean needMakeFinalPackage() {
            return mMakeFinalPackage;
        }
    }

    private ResourceMarker mResourceMarker = new ResourceMarker() {
        public void setWarning(IResource resource, String message) {
            BaseProjectHelper.markResource(resource, AdtConstants.MARKER_PACKAGING,
                    message, IMarker.SEVERITY_WARNING);
        }
    };


    public PostCompilerBuilder() {
        super();
    }

    @Override
    protected void clean(IProgressMonitor monitor) throws CoreException {
        super.clean(monitor);

        // Get the project.
        IProject project = getProject();

        // Clear the project of the generic markers
        removeMarkersFromContainer(project, AdtConstants.MARKER_AAPT_PACKAGE);
        removeMarkersFromContainer(project, AdtConstants.MARKER_PACKAGING);

        // also remove the files in the output folder (but not the Eclipse output folder).
        IFolder javaOutput = BaseProjectHelper.getJavaOutputFolder(project);
        IFolder androidOutput = BaseProjectHelper.getAndroidOutputFolder(project);

        if (javaOutput.equals(androidOutput) == false) {
            // get the content
            IResource[] members = androidOutput.members();
            for (IResource member : members) {
                if (member.equals(javaOutput) == false) {
                    member.delete(true /*force*/, monitor);
                }
            }
        }
    }

    // build() returns a list of project from which this project depends for future compilation.
    @SuppressWarnings({"unchecked"})
    @Override
    protected IProject[] build(int kind, Map args, IProgressMonitor monitor)
            throws CoreException {
        // get a project object
        IProject project = getProject();

        // Benchmarking start
        long startBuildTime = 0;
        if (BuildHelper.BENCHMARK_FLAG) {
            // End JavaC Timer
            String msg = "BENCHMARK ADT: Ending Compilation \n BENCHMARK ADT: Time Elapsed: " +    //$NON-NLS-1$
                         (System.nanoTime() - BuildHelper.sStartJavaCTime)/Math.pow(10, 6) + "ms"; //$NON-NLS-1$
            AdtPlugin.printBuildToConsole(BuildVerbosity.ALWAYS, project, msg);
            msg = "BENCHMARK ADT: Starting PostCompilation";                                        //$NON-NLS-1$
            AdtPlugin.printBuildToConsole(BuildVerbosity.ALWAYS, project, msg);
            startBuildTime = System.nanoTime();
        }

        // list of referenced projects. This is a mix of java projects and library projects
        // and is computed below.
        IProject[] allRefProjects = null;

        try {
            // get the project info
            ProjectState projectState = Sdk.getProjectState(project);

            // this can happen if the project has no project.properties.
            if (projectState == null) {
                return null;
            }

            boolean isLibrary = projectState.isLibrary();

            // get the libraries
            List<IProject> libProjects = projectState.getFullLibraryProjects();

            IJavaProject javaProject = JavaCore.create(project);

            // get the list of referenced projects.
            List<IProject> javaProjects = ProjectHelper.getReferencedProjects(project);
            List<IJavaProject> referencedJavaProjects = BuildHelper.getJavaProjects(
                    javaProjects);

            // mix the java project and the library projects
            final int size = libProjects.size() + javaProjects.size();
            ArrayList<IProject> refList = new ArrayList<IProject>(size);
            refList.addAll(libProjects);
            refList.addAll(javaProjects);
            allRefProjects = refList.toArray(new IProject[size]);

            // Top level check to make sure the build can move forward.
            abortOnBadSetup(javaProject);

            // get the android output folder
            IFolder androidOutputFolder = BaseProjectHelper.getAndroidOutputFolder(project);

            // now we need to get the classpath list
            List<IPath> sourceList = BaseProjectHelper.getSourceClasspaths(javaProject);

            // First thing we do is go through the resource delta to not
            // lose it if we have to abort the build for any reason.
            PostCompilerDeltaVisitor dv = null;
            if (args.containsKey(POST_C_REQUESTED)
                    && AdtPrefs.getPrefs().getBuildSkipPostCompileOnFileSave()) {
                // Skip over flag setting
            } else if (kind == FULL_BUILD) {
                AdtPlugin.printBuildToConsole(BuildVerbosity.VERBOSE, project,
                        Messages.Start_Full_Apk_Build);

                // Full build: we do all the steps.
                mUpdateCrunchCache = true;
                mPackageResources = true;
                mConvertToDex = true;
                mBuildFinalPackage = true;
            } else {
                AdtPlugin.printBuildToConsole(BuildVerbosity.VERBOSE, project,
                        Messages.Start_Inc_Apk_Build);

                // go through the resources and see if something changed.
                IResourceDelta delta = getDelta(project);
                if (delta == null) {
                    // no delta? Same as full build: we do all the steps.
                    mUpdateCrunchCache = true;
                    mPackageResources = true;
                    mConvertToDex = true;
                    mBuildFinalPackage = true;
                } else {
                    dv = new PostCompilerDeltaVisitor(this, sourceList, androidOutputFolder);
                    delta.accept(dv);

                    // save the state
                    mUpdateCrunchCache |= dv.getUpdateCrunchCache();
                    mPackageResources |= dv.getPackageResources();
                    mConvertToDex |= dv.getConvertToDex();
                    mBuildFinalPackage |= dv.getMakeFinalPackage();
                }

                // if the main resources didn't change, then we check for the library
                // ones (will trigger resource repackaging too)
                if ((mPackageResources == false || mBuildFinalPackage == false) &&
                        libProjects.size() > 0) {
                    for (IProject libProject : libProjects) {
                        delta = getDelta(libProject);
                        if (delta != null) {
                            LibraryDeltaVisitor visitor = new LibraryDeltaVisitor();
                            delta.accept(visitor);

                            mPackageResources |= visitor.getResChange();
                            mBuildFinalPackage |= visitor.getLibChange();

                            if (mPackageResources && mBuildFinalPackage) {
                                break;
                            }
                        }
                    }
                }

                // also go through the delta for all the referenced projects, until we are forced to
                // compile anyway
                final int referencedCount = referencedJavaProjects.size();
                for (int i = 0 ; i < referencedCount &&
                        (mBuildFinalPackage == false || mConvertToDex == false); i++) {
                    IJavaProject referencedJavaProject = referencedJavaProjects.get(i);
                    delta = getDelta(referencedJavaProject.getProject());
                    if (delta != null) {
                        ReferencedProjectDeltaVisitor refProjectDv =
                                new ReferencedProjectDeltaVisitor(referencedJavaProject);

                        delta.accept(refProjectDv);

                        // save the state
                        mConvertToDex |= refProjectDv.needDexConvertion();
                        mBuildFinalPackage |= refProjectDv.needMakeFinalPackage();
                    }
                }
            }

            // store the build status in the persistent storage
            saveProjectBooleanProperty(PROPERTY_CONVERT_TO_DEX, mConvertToDex);
            saveProjectBooleanProperty(PROPERTY_UPDATE_CRUNCH_CACHE, mUpdateCrunchCache);
            saveProjectBooleanProperty(PROPERTY_PACKAGE_RESOURCES, mPackageResources);
            saveProjectBooleanProperty(PROPERTY_BUILD_APK, mBuildFinalPackage);

            if (dv != null && dv.mXmlError) {
                AdtPlugin.printBuildToConsole(BuildVerbosity.VERBOSE, project,
                Messages.Xml_Error);

                // if there was some XML errors, we just return w/o doing
                // anything since we've put some markers in the files anyway
                return allRefProjects;
            }

            // remove older packaging markers.
            removeMarkersFromContainer(javaProject.getProject(), AdtConstants.MARKER_PACKAGING);

            if (androidOutputFolder == null) {
                // mark project and exit
                markProject(AdtConstants.MARKER_PACKAGING, Messages.Failed_To_Get_Output,
                        IMarker.SEVERITY_ERROR);
                return allRefProjects;
            }

            // finished with the common init and tests. Special case of the library.
            if (isLibrary) {
                // check the jar output file is present, if not create it.
                IFile jarIFile = androidOutputFolder.getFile(
                        project.getName().toLowerCase() + AdtConstants.DOT_JAR);
                if (mConvertToDex == false && jarIFile.exists() == false) {
                    mConvertToDex = true;
                }

                if (mConvertToDex) { // in this case this means some class files changed and
                                     // we need to update the jar file.
                    IFolder javaOutputFolder = BaseProjectHelper.getJavaOutputFolder(project);

                    writeLibraryPackage(jarIFile, project, javaOutputFolder,
                            referencedJavaProjects);
                    saveProjectBooleanProperty(PROPERTY_CONVERT_TO_DEX, mConvertToDex = false);
                }

                // also update the crunch cache if needed.
                if (mUpdateCrunchCache) {
                    BuildHelper helper = new BuildHelper(project,
                            mOutStream, mErrStream,
                            true /*debugMode*/,
                            AdtPrefs.getPrefs().getBuildVerbosity() == BuildVerbosity.VERBOSE);
                    updateCrunchCache(project, helper);
                }

                return allRefProjects;
            }

            // Check to see if we're going to launch or export. If not, we can skip
            // the packaging and dexing process.
            if (!args.containsKey(POST_C_REQUESTED)
                    && AdtPrefs.getPrefs().getBuildSkipPostCompileOnFileSave()) {
                AdtPlugin.printBuildToConsole(BuildVerbosity.VERBOSE, project,
                        Messages.Skip_Post_Compiler);
                return allRefProjects;
            } else {
                AdtPlugin.printBuildToConsole(BuildVerbosity.VERBOSE, project,
                        Messages.Start_Full_Post_Compiler);
            }

            // first thing we do is check that the SDK directory has been setup.
            String osSdkFolder = AdtPlugin.getOsSdkFolder();

            if (osSdkFolder.length() == 0) {
                // this has already been checked in the precompiler. Therefore,
                // while we do have to cancel the build, we don't have to return
                // any error or throw anything.
                return allRefProjects;
            }

            // do some extra check, in case the output files are not present. This
            // will force to recreate them.
            IResource tmp = null;

            if (mPackageResources == false) {
                // check the full resource package
                tmp = androidOutputFolder.findMember(AdtConstants.FN_RESOURCES_AP_);
                if (tmp == null || tmp.exists() == false) {
                    mPackageResources = true;
                    mBuildFinalPackage = true;
                }
            }

            // check classes.dex is present. If not we force to recreate it.
            if (mConvertToDex == false) {
                tmp = androidOutputFolder.findMember(SdkConstants.FN_APK_CLASSES_DEX);
                if (tmp == null || tmp.exists() == false) {
                    mConvertToDex = true;
                    mBuildFinalPackage = true;
                }
            }

            // also check the final file(s)!
            String finalPackageName = ProjectHelper.getApkFilename(project, null /*config*/);
            if (mBuildFinalPackage == false) {
                tmp = androidOutputFolder.findMember(finalPackageName);
                if (tmp == null || (tmp instanceof IFile &&
                        tmp.exists() == false)) {
                    String msg = String.format(Messages.s_Missing_Repackaging, finalPackageName);
                    AdtPlugin.printBuildToConsole(BuildVerbosity.VERBOSE, project, msg);
                    mBuildFinalPackage = true;
                }
            }

            // at this point we know if we need to recreate the temporary apk
            // or the dex file, but we don't know if we simply need to recreate them
            // because they are missing

            // refresh the output directory first
            IContainer ic = androidOutputFolder.getParent();
            if (ic != null) {
                ic.refreshLocal(IResource.DEPTH_ONE, monitor);
            }

            // Get the DX output stream. Since the builder is created for the life of the
            // project, they can be kept around.
            if (mOutStream == null) {
                mOutStream = new AndroidPrintStream(project, null /*prefix*/,
                        AdtPlugin.getOutStream());
                mErrStream = new AndroidPrintStream(project, null /*prefix*/,
                        AdtPlugin.getOutStream());
            }

            // we need to test all three, as we may need to make the final package
            // but not the intermediary ones.
            if (mPackageResources || mConvertToDex || mBuildFinalPackage) {
                BuildHelper helper = new BuildHelper(project,
                        mOutStream, mErrStream,
                        true /*debugMode*/,
                        AdtPrefs.getPrefs().getBuildVerbosity() == BuildVerbosity.VERBOSE);

                // resource to the AndroidManifest.xml file
                IFile manifestFile = project.getFile(SdkConstants.FN_ANDROID_MANIFEST_XML);

                if (manifestFile == null || manifestFile.exists() == false) {
                    // mark project and exit
                    String msg = String.format(Messages.s_File_Missing,
                            SdkConstants.FN_ANDROID_MANIFEST_XML);
                    markProject(AdtConstants.MARKER_PACKAGING, msg, IMarker.SEVERITY_ERROR);
                    return allRefProjects;
                }

                IPath androidBinLocation = androidOutputFolder.getLocation();
                if (androidBinLocation == null) {
                    markProject(AdtConstants.MARKER_PACKAGING, Messages.Output_Missing,
                            IMarker.SEVERITY_ERROR);
                    return allRefProjects;
                }
                String osAndroidBinPath = androidBinLocation.toOSString();

                // Remove the old .apk.
                // This make sure that if the apk is corrupted, then dx (which would attempt
                // to open it), will not fail.
                String osFinalPackagePath = osAndroidBinPath + File.separator + finalPackageName;
                File finalPackage = new File(osFinalPackagePath);

                // if delete failed, this is not really a problem, as the final package generation
                // handle already present .apk, and if that one failed as well, the user will be
                // notified.
                finalPackage.delete();

                // Check if we need to update the PNG cache
                if (mUpdateCrunchCache) {
                    if (updateCrunchCache(project, helper) == false) {
                        return allRefProjects;
                    }
                }

                // Check if we need to package the resources.
                if (mPackageResources) {
                    // remove some aapt_package only markers.
                    removeMarkersFromContainer(project, AdtConstants.MARKER_AAPT_PACKAGE);

                    try {
                        helper.packageResources(manifestFile, libProjects, null /*resfilter*/,
                                0 /*versionCode */, osAndroidBinPath,
                                AdtConstants.FN_RESOURCES_AP_);
                    } catch (AaptExecException e) {
                        BaseProjectHelper.markResource(project, AdtConstants.MARKER_PACKAGING,
                                e.getMessage(), IMarker.SEVERITY_ERROR);
                        return allRefProjects;
                    } catch (AaptResultException e) {
                        // attempt to parse the error output
                        String[] aaptOutput = e.getOutput();
                        boolean parsingError = AaptParser.parseOutput(aaptOutput, project);

                        // if we couldn't parse the output we display it in the console.
                        if (parsingError) {
                            AdtPlugin.printErrorToConsole(project, (Object[]) aaptOutput);

                            // if the exec failed, and we couldn't parse the error output (and
                            // therefore not all files that should have been marked, were marked),
                            // we put a generic marker on the project and abort.
                            BaseProjectHelper.markResource(project,
                                    AdtConstants.MARKER_PACKAGING,
                                    Messages.Unparsed_AAPT_Errors,
                                    IMarker.SEVERITY_ERROR);
                        }
                    }

                    // build has been done. reset the state of the builder
                    mPackageResources = false;

                    // and store it
                    saveProjectBooleanProperty(PROPERTY_PACKAGE_RESOURCES, mPackageResources);
                }

                String classesDexPath = osAndroidBinPath + File.separator +
                        SdkConstants.FN_APK_CLASSES_DEX;

                // then we check if we need to package the .class into classes.dex
                if (mConvertToDex) {
                    try {
                        String[] dxInputPaths = helper.getCompiledCodePaths(
                                true /*includeProjectOutputs*/, mResourceMarker);

                        helper.executeDx(javaProject, dxInputPaths, classesDexPath);
                    } catch (DexException e) {
                        String message = e.getMessage();

                        AdtPlugin.printErrorToConsole(project, message);
                        BaseProjectHelper.markResource(project, AdtConstants.MARKER_PACKAGING,
                                message, IMarker.SEVERITY_ERROR);

                        Throwable cause = e.getCause();

                        if (cause instanceof NoClassDefFoundError
                                || cause instanceof NoSuchMethodError) {
                            AdtPlugin.printErrorToConsole(project, Messages.Incompatible_VM_Warning,
                                    Messages.Requires_1_5_Error);
                        }

                        // dx failed, we return
                        return allRefProjects;
                    }

                    // build has been done. reset the state of the builder
                    mConvertToDex = false;

                    // and store it
                    saveProjectBooleanProperty(PROPERTY_CONVERT_TO_DEX, mConvertToDex);
                }

                // now we need to make the final package from the intermediary apk
                // and classes.dex.
                // This is the default package with all the resources.

                try {
                    helper.finalDebugPackage(
                            osAndroidBinPath + File.separator + AdtConstants.FN_RESOURCES_AP_,
                        classesDexPath, osFinalPackagePath,
                        javaProject, libProjects, referencedJavaProjects, mResourceMarker);
                } catch (KeytoolException e) {
                    String eMessage = e.getMessage();

                    // mark the project with the standard message
                    String msg = String.format(Messages.Final_Archive_Error_s, eMessage);
                    BaseProjectHelper.markResource(project, AdtConstants.MARKER_PACKAGING, msg,
                            IMarker.SEVERITY_ERROR);

                    // output more info in the console
                    AdtPlugin.printErrorToConsole(project,
                            msg,
                            String.format(Messages.ApkBuilder_JAVA_HOME_is_s, e.getJavaHome()),
                            Messages.ApkBuilder_Update_or_Execute_manually_s,
                            e.getCommandLine());

                    return allRefProjects;
                } catch (ApkCreationException e) {
                    String eMessage = e.getMessage();

                    // mark the project with the standard message
                    String msg = String.format(Messages.Final_Archive_Error_s, eMessage);
                    BaseProjectHelper.markResource(project, AdtConstants.MARKER_PACKAGING, msg,
                            IMarker.SEVERITY_ERROR);
                } catch (AndroidLocationException e) {
                    String eMessage = e.getMessage();

                    // mark the project with the standard message
                    String msg = String.format(Messages.Final_Archive_Error_s, eMessage);
                    BaseProjectHelper.markResource(project, AdtConstants.MARKER_PACKAGING, msg,
                            IMarker.SEVERITY_ERROR);
                } catch (NativeLibInJarException e) {
                    String msg = e.getMessage();

                    BaseProjectHelper.markResource(project, AdtConstants.MARKER_PACKAGING,
                            msg, IMarker.SEVERITY_ERROR);

                    AdtPlugin.printErrorToConsole(project, (Object[]) e.getAdditionalInfo());
                } catch (CoreException e) {
                    // mark project and return
                    String msg = String.format(Messages.Final_Archive_Error_s, e.getMessage());
                    AdtPlugin.printErrorToConsole(project, msg);
                    BaseProjectHelper.markResource(project, AdtConstants.MARKER_PACKAGING, msg,
                            IMarker.SEVERITY_ERROR);
                } catch (DuplicateFileException e) {
                    String msg1 = String.format(
                            "Found duplicate file for APK: %1$s\nOrigin 1: %2$s\nOrigin 2: %3$s",
                            e.getArchivePath(), e.getFile1(), e.getFile2());
                    String msg2 = String.format(Messages.Final_Archive_Error_s, msg1);
                    AdtPlugin.printErrorToConsole(project, msg2);
                    BaseProjectHelper.markResource(project, AdtConstants.MARKER_PACKAGING, msg2,
                            IMarker.SEVERITY_ERROR);
                }

                // we are done.

                // get the resource to bin
                androidOutputFolder.refreshLocal(IResource.DEPTH_ONE, monitor);

                // build has been done. reset the state of the builder
                mBuildFinalPackage = false;

                // and store it
                saveProjectBooleanProperty(PROPERTY_BUILD_APK, mBuildFinalPackage);

                // reset the installation manager to force new installs of this project
                ApkInstallManager.getInstance().resetInstallationFor(project);

                AdtPlugin.printBuildToConsole(BuildVerbosity.VERBOSE, getProject(),
                        "Build Success!");
            }
        } catch (AbortBuildException e) {
            return allRefProjects;
        } catch (Exception exception) {
            // try to catch other exception to actually display an error. This will be useful
            // if we get an NPE or something so that we can at least notify the user that something
            // went wrong.

            // first check if this is a CoreException we threw to cancel the build.
            if (exception instanceof CoreException) {
                if (((CoreException)exception).getStatus().getSeverity() == IStatus.CANCEL) {
                    // Project is already marked with an error. Nothing to do
                    return allRefProjects;
                }
            }

            String msg = exception.getMessage();
            if (msg == null) {
                msg = exception.getClass().getCanonicalName();
            }

            msg = String.format("Unknown error: %1$s", msg);
            AdtPlugin.logAndPrintError(exception, project.getName(), msg);
            markProject(AdtConstants.MARKER_PACKAGING, msg, IMarker.SEVERITY_ERROR);
        }

        // Benchmarking end
        if (BuildHelper.BENCHMARK_FLAG) {
            String msg = "BENCHMARK ADT: Ending PostCompilation. \n BENCHMARK ADT: Time Elapsed: " + //$NON-NLS-1$
                         ((System.nanoTime() - startBuildTime)/Math.pow(10, 6)) + "ms";              //$NON-NLS-1$
            AdtPlugin.printBuildToConsole(BuildVerbosity.ALWAYS, project, msg);
            // End Overall Timer
            msg = "BENCHMARK ADT: Done with everything! \n BENCHMARK ADT: Time Elapsed: " +          //$NON-NLS-1$
                  (System.nanoTime() - BuildHelper.sStartOverallTime)/Math.pow(10, 6) + "ms";        //$NON-NLS-1$
            AdtPlugin.printBuildToConsole(BuildVerbosity.ALWAYS, project, msg);
        }

        return allRefProjects;
    }

    private static class JarBuilder implements IArchiveBuilder {

        private static Pattern R_PATTERN = Pattern.compile("R(\\$.*)?\\.class"); //$NON-NLS-1$

        private final byte[] buffer = new byte[1024];
        private final JarOutputStream mOutputStream;

        JarBuilder(JarOutputStream outputStream) {
            mOutputStream = outputStream;
        }

        public void addFile(IFile file, IFolder rootFolder) throws ApkCreationException {
            // we only package class file from the output folder
            if (AdtConstants.EXT_CLASS.equals(file.getFileExtension()) == false) {
                return;
            }

            // we don't package any R[$*] classes.
            String name = file.getName();
            if (R_PATTERN.matcher(name).matches()) {
                return;
            }

            IPath path = file.getFullPath().makeRelativeTo(rootFolder.getFullPath());
            try {
                addFile(file.getContents(), file.getLocalTimeStamp(), path.toString());
            } catch (ApkCreationException e) {
                throw e;
            } catch (Exception e) {
                throw new ApkCreationException(e, "Failed to add %s", file);
            }
        }

        public void addFile(File file, String archivePath) throws ApkCreationException,
                SealedApkException, DuplicateFileException {
            try {
                FileInputStream inputStream = new FileInputStream(file);
                long lastModified = file.lastModified();
                addFile(inputStream, lastModified, archivePath);
            } catch (ApkCreationException e) {
                throw e;
            } catch (Exception e) {
                throw new ApkCreationException(e, "Failed to add %s", file);
            }
        }

        private void addFile(InputStream content, long lastModified, String archivePath)
                throws IOException, ApkCreationException {
            // create the jar entry
            JarEntry entry = new JarEntry(archivePath);
            entry.setTime(lastModified);

            try {
                // add the entry to the jar archive
                mOutputStream.putNextEntry(entry);

                // read the content of the entry from the input stream, and write
                // it into the archive.
                int count;
                while ((count = content.read(buffer)) != -1) {
                    mOutputStream.write(buffer, 0, count);
                }
            } finally {
                try {
                    if (content != null) {
                        content.close();
                    }
                } catch (Exception e) {
                    throw new ApkCreationException(e, "Failed to close stream");
                }
            }
        }
    }

    /**
     * Updates the crunch cache if needed and return true if the build must continue.
     */
    private boolean updateCrunchCache(IProject project, BuildHelper helper) {
        try {
            helper.updateCrunchCache();
        } catch (AaptExecException e) {
            BaseProjectHelper.markResource(project, AdtConstants.MARKER_PACKAGING,
                    e.getMessage(), IMarker.SEVERITY_ERROR);
            return false;
        } catch (AaptResultException e) {
            // attempt to parse the error output
            String[] aaptOutput = e.getOutput();
            boolean parsingError = AaptParser.parseOutput(aaptOutput, project);
            // if we couldn't parse the output we display it in the console.
            if (parsingError) {
                AdtPlugin.printErrorToConsole(project, (Object[]) aaptOutput);
            }
        }

        // crunch has been done. Reset state
        mUpdateCrunchCache = false;

        // and store it
        saveProjectBooleanProperty(PROPERTY_UPDATE_CRUNCH_CACHE, mUpdateCrunchCache);

        return true;
    }

    private void writeLibraryPackage(IFile jarIFile, IProject project, IFolder javaOutputFolder,
            List<IJavaProject> referencedJavaProjects) {

        JarOutputStream jos = null;
        try {
            Manifest manifest = new Manifest();
            Attributes mainAttributes = manifest.getMainAttributes();
            mainAttributes.put(Attributes.Name.CLASS_PATH, "Android ADT"); //$NON-NLS-1$
            mainAttributes.putValue("Created-By", "1.0 (Android)"); //$NON-NLS-1$  //$NON-NLS-2$
            jos = new JarOutputStream(
                    new FileOutputStream(jarIFile.getLocation().toFile()), manifest);

            JarBuilder jarBuilder = new JarBuilder(jos);

            // write the class files
            writeClassFilesIntoJar(jarBuilder, javaOutputFolder, javaOutputFolder);

            // now write the standard Java resources
            BuildHelper.writeResources(jarBuilder, JavaCore.create(project));

            // do the same for all the referencedJava project
            for (IJavaProject javaProject : referencedJavaProjects) {
                // in case an Android project was referenced (which won't work), the
                // best thing is to ignore this project.
                if (javaProject.getProject().hasNature(AdtConstants.NATURE_DEFAULT)) {
                    continue;
                }

                IFolder refProjectOutput = BaseProjectHelper.getJavaOutputFolder(
                        javaProject.getProject());

                if (refProjectOutput != null) {
                    // write the class files
                    writeClassFilesIntoJar(jarBuilder, refProjectOutput, refProjectOutput);

                    // now write the standard Java resources
                    BuildHelper.writeResources(jarBuilder, javaProject);
                }
            }

            saveProjectBooleanProperty(PROPERTY_CONVERT_TO_DEX, mConvertToDex);
        } catch (Exception e) {
            AdtPlugin.log(e, "Failed to write jar file %s", jarIFile.getLocation().toOSString());
        } finally {
            if (jos != null) {
                try {
                    jos.close();
                } catch (IOException e) {
                    // pass
                }
            }
        }
    }

    private void writeClassFilesIntoJar(JarBuilder builder, IFolder folder, IFolder rootFolder)
            throws CoreException, IOException, ApkCreationException {
        IResource[] members = folder.members();
        for (IResource member : members) {
            if (member.getType() == IResource.FOLDER) {
                writeClassFilesIntoJar(builder, (IFolder) member, rootFolder);
            } else if (member.getType() == IResource.FILE) {
                IFile file = (IFile) member;
                builder.addFile(file, rootFolder);
            }
        }
    }

    @Override
    protected void startupOnInitialize() {
        super.startupOnInitialize();

        // load the build status. We pass true as the default value to
        // force a recompile in case the property was not found
        mConvertToDex = loadProjectBooleanProperty(PROPERTY_CONVERT_TO_DEX, true);
        mUpdateCrunchCache = loadProjectBooleanProperty(PROPERTY_UPDATE_CRUNCH_CACHE, true);
        mPackageResources = loadProjectBooleanProperty(PROPERTY_PACKAGE_RESOURCES, true);
        mBuildFinalPackage = loadProjectBooleanProperty(PROPERTY_BUILD_APK, true);
    }

    @Override
    protected void abortOnBadSetup(IJavaProject javaProject) throws AbortBuildException {
        super.abortOnBadSetup(javaProject);

        IProject iProject = getProject();

        // do a (hopefully quick) search for Precompiler type markers. Those are always only
        // errors.
        stopOnMarker(iProject, AdtConstants.MARKER_AAPT_COMPILE, IResource.DEPTH_INFINITE,
                false /*checkSeverity*/);
        stopOnMarker(iProject, AdtConstants.MARKER_AIDL, IResource.DEPTH_INFINITE,
                false /*checkSeverity*/);
        stopOnMarker(iProject, AdtConstants.MARKER_RENDERSCRIPT, IResource.DEPTH_INFINITE,
                false /*checkSeverity*/);
        stopOnMarker(iProject, AdtConstants.MARKER_ANDROID, IResource.DEPTH_ZERO,
                false /*checkSeverity*/);

        // do a search for JDT markers. Those can be errors or warnings
        stopOnMarker(iProject, IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER,
                IResource.DEPTH_INFINITE, true /*checkSeverity*/);
        stopOnMarker(iProject, IJavaModelMarker.BUILDPATH_PROBLEM_MARKER,
                IResource.DEPTH_INFINITE, true /*checkSeverity*/);
    }
}
