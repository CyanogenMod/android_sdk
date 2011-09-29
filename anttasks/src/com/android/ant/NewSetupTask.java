/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.ant;

import com.android.io.FileWrapper;
import com.android.io.FolderWrapper;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.ISdkLog;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.SdkManager;
import com.android.sdklib.IAndroidTarget.IOptionalLibrary;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.internal.project.ProjectProperties.PropertyType;
import com.android.sdklib.xml.AndroidManifest;
import com.android.sdklib.xml.AndroidXPathFactory;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.Path.PathElement;
import org.apache.tools.ant.util.DeweyDecimal;
import org.xml.sax.InputSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;

/**
 * Setup Ant task. This task accomplishes:
 * <ul>
 * <li>Gets the project target hash string from {@link ProjectProperties#PROPERTY_TARGET},
 * and resolves it to get the project's {@link IAndroidTarget}.</li>
 *
 * <li>Sets up properties so that aapt can find the android.jar and other files/folders in
 * the resolved target.</li>
 *
 * <li>Sets up the boot classpath ref so that the <code>javac</code> task knows where to find
 * the libraries. This includes the default android.jar from the resolved target but also optional
 * libraries provided by the target (if any, when the target is an add-on).</li>
 *
 * <li>Resolve library dependencies and setup various Path references for them</li>
 * </ul>
 *
 * This is used in the main rules file only.
 *
 */
public class NewSetupTask extends Task {
    private final static String ANT_MIN_VERSION = "1.8.0";

    private String mProjectTypeOut;
    private String mAndroidJarFileOut;
    private String mAndroidAidlFileOut;
    private String mRenderScriptExeOut;
    private String mRenderScriptIncludeDirOut;
    private String mBootclasspathrefOut;
    private String mProjectLibrariesRootOut;
    private String mProjectLibrariesResOut;
    private String mProjectLibrariesPackageOut;
    private String mProjectLibrariesJarsOut;
    private String mProjectLibrariesLibsOut;
    private String mTargetApiOut;

    public void setProjectTypeOut(String projectTypeOut) {
        mProjectTypeOut = projectTypeOut;
    }

    public void setAndroidJarFileOut(String androidJarFileOut) {
        mAndroidJarFileOut = androidJarFileOut;
    }

    public void setAndroidAidlFileOut(String androidAidlFileOut) {
        mAndroidAidlFileOut = androidAidlFileOut;
    }

    public void setRenderScriptExeOut(String renderScriptExeOut) {
        mRenderScriptExeOut = renderScriptExeOut;
    }

    public void setRenderScriptIncludeDirOut(String renderScriptIncludeDirOut) {
        mRenderScriptIncludeDirOut = renderScriptIncludeDirOut;
    }

    public void setBootclasspathrefOut(String bootclasspathrefOut) {
        mBootclasspathrefOut = bootclasspathrefOut;
    }

    public void setProjectLibrariesRootOut(String projectLibrariesRootOut) {
        mProjectLibrariesRootOut = projectLibrariesRootOut;
    }

    public void setProjectLibrariesResOut(String projectLibrariesResOut) {
        mProjectLibrariesResOut = projectLibrariesResOut;
    }

    public void setProjectLibrariesPackageOut(String projectLibrariesPackageOut) {
        mProjectLibrariesPackageOut = projectLibrariesPackageOut;
    }

    public void setProjectLibrariesJarsOut(String projectLibrariesJarsOut) {
        mProjectLibrariesJarsOut = projectLibrariesJarsOut;
    }

    public void setProjectLibrariesLibsOut(String projectLibrariesLibsOut) {
        mProjectLibrariesLibsOut = projectLibrariesLibsOut;
    }

    public void setTargetApiOut(String targetApiOut) {
        mTargetApiOut = targetApiOut;
    }

    @Override
    public void execute() throws BuildException {
        if (mProjectTypeOut == null) {
            throw new BuildException("Missing attribute projectTypeOut");
        }
        if (mAndroidJarFileOut == null) {
            throw new BuildException("Missing attribute androidJarFileOut");
        }
        if (mAndroidAidlFileOut == null) {
            throw new BuildException("Missing attribute androidAidlFileOut");
        }
        if (mRenderScriptExeOut == null) {
            throw new BuildException("Missing attribute renderScriptExeOut");
        }
        if (mRenderScriptIncludeDirOut == null) {
            throw new BuildException("Missing attribute renderScriptIncludeDirOut");
        }
        if (mBootclasspathrefOut == null) {
            throw new BuildException("Missing attribute bootclasspathrefOut");
        }
        if (mProjectLibrariesRootOut == null) {
            throw new BuildException("Missing attribute projectLibrariesRootOut");
        }
        if (mProjectLibrariesResOut == null) {
            throw new BuildException("Missing attribute projectLibrariesResOut");
        }
        if (mProjectLibrariesPackageOut == null) {
            throw new BuildException("Missing attribute projectLibrariesPackageOut");
        }
        if (mProjectLibrariesJarsOut == null) {
            throw new BuildException("Missing attribute projectLibrariesJarsOut");
        }
        if (mProjectLibrariesLibsOut == null) {
            throw new BuildException("Missing attribute projectLibrariesLibsOut");
        }
        if (mTargetApiOut == null) {
            throw new BuildException("Missing attribute targetApiOut");
        }


        Project antProject = getProject();

        // check the Ant version
        DeweyDecimal version = getVersion(antProject);
        DeweyDecimal atLeast = new DeweyDecimal(ANT_MIN_VERSION);
        if (atLeast.isGreaterThan(version)) {
            throw new BuildException(
                    "The Android Ant-based build system requires Ant " +
                    ANT_MIN_VERSION +
                    " or later. Current version is " +
                    version);
        }

        // get the SDK location
        File sdkDir = TaskHelper.getSdkLocation(antProject);
        String sdkOsPath = sdkDir.getPath();

        // Make sure the OS sdk path ends with a directory separator
        if (sdkOsPath.length() > 0 && !sdkOsPath.endsWith(File.separator)) {
            sdkOsPath += File.separator;
        }

        // display SDK Tools revision
        int toolsRevison = TaskHelper.getToolsRevision(sdkDir);
        if (toolsRevison != -1) {
            System.out.println("Android SDK Tools Revision " + toolsRevison);
        }

        // detect that the platform tools is there.
        File platformTools = new File(sdkDir, SdkConstants.FD_PLATFORM_TOOLS);
        if (platformTools.isDirectory() == false) {
            throw new BuildException(String.format(
                    "SDK Platform Tools component is missing. " +
                    "Please install it with the SDK Manager (%1$s%2$c%3$s)",
                    SdkConstants.FD_TOOLS,
                    File.separatorChar,
                    SdkConstants.androidCmdName()));
        }

        // get the target property value
        String targetHashString = antProject.getProperty(ProjectProperties.PROPERTY_TARGET);

        boolean isTestProject = false;

        if (antProject.getProperty(ProjectProperties.PROPERTY_TESTED_PROJECT) != null) {
            isTestProject = true;
        }

        if (targetHashString == null) {
            throw new BuildException("Android Target is not set.");
        }

        // load up the sdk targets.
        final ArrayList<String> messages = new ArrayList<String>();
        SdkManager manager = SdkManager.createManager(sdkOsPath, new ISdkLog() {
            public void error(Throwable t, String errorFormat, Object... args) {
                if (errorFormat != null) {
                    messages.add(String.format("Error: " + errorFormat, args));
                }
                if (t != null) {
                    messages.add("Error: " + t.getMessage());
                }
            }

            public void printf(String msgFormat, Object... args) {
                messages.add(String.format(msgFormat, args));
            }

            public void warning(String warningFormat, Object... args) {
                messages.add(String.format("Warning: " + warningFormat, args));
            }
        });

        if (manager == null) {
            // since we failed to parse the SDK, lets display the parsing output.
            for (String msg : messages) {
                System.out.println(msg);
            }
            throw new BuildException("Failed to parse SDK content.");
        }

        // resolve it
        IAndroidTarget androidTarget = manager.getTargetFromHashString(targetHashString);

        if (androidTarget == null) {
            throw new BuildException(String.format(
                    "Unable to resolve target '%s'", targetHashString));
        }

        // display the project info
        System.out.println("Project Target: " + androidTarget.getName());
        if (androidTarget.isPlatform() == false) {
            System.out.println("Vendor: " + androidTarget.getVendor());
            System.out.println("Platform Version: " + androidTarget.getVersionName());
        }
        System.out.println("API level: " + androidTarget.getVersion().getApiString());

        // check if the project is a library
        boolean isLibrary = false;

        String libraryProp = antProject.getProperty(ProjectProperties.PROPERTY_LIBRARY);
        if (libraryProp != null) {
            isLibrary = Boolean.valueOf(libraryProp).booleanValue();
        }

        if (isLibrary) {
            System.out.println("Project Type: Android Library");
        }

        // look for referenced libraries.
        processReferencedLibraries(antProject, androidTarget);

        // always check the manifest minSdkVersion.
        checkManifest(antProject, androidTarget.getVersion());

        // sets up the properties to find android.jar/framework.aidl/target tools
        String androidJar = androidTarget.getPath(IAndroidTarget.ANDROID_JAR);
        antProject.setProperty(mAndroidJarFileOut, androidJar);

        String androidAidl = androidTarget.getPath(IAndroidTarget.ANDROID_AIDL);
        antProject.setProperty(mAndroidAidlFileOut, androidAidl);

        Path includePath = new Path(antProject);
        PathElement element = includePath.createPathElement();
        element.setPath(androidTarget.getPath(IAndroidTarget.ANDROID_RS));
        element = includePath.createPathElement();
        element.setPath(androidTarget.getPath(IAndroidTarget.ANDROID_RS_CLANG));
        antProject.setProperty(mRenderScriptIncludeDirOut, includePath.toString());

        // TODO: figure out the actual compiler to use based on the minSdkVersion
        antProject.setProperty(mRenderScriptExeOut,
                sdkOsPath + SdkConstants.OS_SDK_PLATFORM_TOOLS_FOLDER +
                SdkConstants.FN_RENDERSCRIPT);

        // sets up the boot classpath

        // create the Path object
        Path bootclasspath = new Path(antProject);

        // create a PathElement for the framework jar
        element = bootclasspath.createPathElement();
        element.setPath(androidJar);

        // create PathElement for each optional library.
        IOptionalLibrary[] libraries = androidTarget.getOptionalLibraries();
        if (libraries != null) {
            HashSet<String> visitedJars = new HashSet<String>();
            for (IOptionalLibrary library : libraries) {
                String jarPath = library.getJarPath();
                if (visitedJars.contains(jarPath) == false) {
                    visitedJars.add(jarPath);

                    element = bootclasspath.createPathElement();
                    element.setPath(library.getJarPath());
                }
            }
        }

        // sets the path in the project with a reference
        antProject.addReference(mBootclasspathrefOut, bootclasspath);

        // finally set the project type.
        if (isLibrary) {
            antProject.setProperty(mProjectTypeOut, "library");
        } else if (isTestProject) {
            antProject.setProperty(mProjectTypeOut, "test");
        } else {
            antProject.setProperty(mProjectTypeOut, "project");
        }
    }

    /**
     * Checks the manifest <code>minSdkVersion</code> attribute.
     * @param antProject the ant project
     * @param androidVersion the version of the platform the project is compiling against.
     */
    private void checkManifest(Project antProject, AndroidVersion androidVersion) {
        try {
            File manifest = new File(antProject.getBaseDir(), SdkConstants.FN_ANDROID_MANIFEST_XML);

            XPath xPath = AndroidXPathFactory.newXPath();

            // check the package name.
            String value = xPath.evaluate(
                    "/"  + AndroidManifest.NODE_MANIFEST +
                    "/@" + AndroidManifest.ATTRIBUTE_PACKAGE,
                    new InputSource(new FileInputStream(manifest)));
            if (value != null) { // aapt will complain if it's missing.
                // only need to check that the package has 2 segments
                if (value.indexOf('.') == -1) {
                    throw new BuildException(String.format(
                            "Application package '%1$s' must have a minimum of 2 segments.",
                            value));
                }
            }

            // check the minSdkVersion value
            value = xPath.evaluate(
                    "/"  + AndroidManifest.NODE_MANIFEST +
                    "/"  + AndroidManifest.NODE_USES_SDK +
                    "/@" + AndroidXPathFactory.DEFAULT_NS_PREFIX + ":" +
                            AndroidManifest.ATTRIBUTE_MIN_SDK_VERSION,
                    new InputSource(new FileInputStream(manifest)));

            if (androidVersion.isPreview()) {
                // in preview mode, the content of the minSdkVersion must match exactly the
                // platform codename.
                String codeName = androidVersion.getCodename();
                if (codeName.equals(value) == false) {
                    throw new BuildException(String.format(
                            "For '%1$s' SDK Preview, attribute minSdkVersion in AndroidManifest.xml must be '%1$s' (current: %2$s)",
                            codeName, value));
                }

                // set the API level to the previous API level (which is actually the value in
                // androidVersion.)
                antProject.setProperty(mTargetApiOut,
                        Integer.toString(androidVersion.getApiLevel()));

            } else if (value.length() > 0) {
                // for normal platform, we'll only display warnings if the value is lower or higher
                // than the target api level.
                // First convert to an int.
                int minSdkValue = -1;
                try {
                    minSdkValue = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    // looks like it's not a number: error!
                    throw new BuildException(String.format(
                            "Attribute %1$s in AndroidManifest.xml must be an Integer!",
                            AndroidManifest.ATTRIBUTE_MIN_SDK_VERSION));
                }

                // set the target api to the value
                antProject.setProperty(mTargetApiOut, value);

                int projectApiLevel = androidVersion.getApiLevel();
                if (minSdkValue < projectApiLevel) {
                    System.out.println(String.format(
                            "WARNING: Attribute %1$s in AndroidManifest.xml (%2$d) is lower than the project target API level (%3$d)",
                            AndroidManifest.ATTRIBUTE_MIN_SDK_VERSION,
                            minSdkValue, projectApiLevel));
                } else if (minSdkValue > androidVersion.getApiLevel()) {
                    System.out.println(String.format(
                            "WARNING: Attribute %1$s in AndroidManifest.xml (%2$d) is higher than the project target API level (%3$d)",
                            AndroidManifest.ATTRIBUTE_MIN_SDK_VERSION,
                            minSdkValue, projectApiLevel));
                }
            } else {
                // no minSdkVersion? display a warning
                System.out.println(
                        "WARNING: No minSdkVersion value set. Application will install on all Android versions.");

                // set the target api to 1
                antProject.setProperty(mTargetApiOut, "1");
            }

        } catch (XPathExpressionException e) {
            throw new BuildException(e);
        } catch (FileNotFoundException e) {
            throw new BuildException(e);
        }
    }

    private void processReferencedLibraries(Project antProject, IAndroidTarget androidTarget) {
        // prepare several paths for future tasks
        Path rootPath = new Path(antProject);
        Path resPath = new Path(antProject);
        Path libsPath = new Path(antProject);
        Path jarsPath = new Path(antProject);
        StringBuilder packageStrBuilder = new StringBuilder();

        FilenameFilter filter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(".jar");
            }
        };

        System.out.println("\n------------------\nResolving library dependencies:");

        ArrayList<File> libraries = getProjectLibraries(antProject);

        if (libraries.size() > 0) {
            System.out.println("------------------\nOrdered libraries:");

            for (File library : libraries) {
                String libRootPath = library.getAbsolutePath();
                System.out.println(libRootPath);

                // get the root path.
                PathElement element = rootPath.createPathElement();
                element.setPath(libRootPath);

                // get the res path. Always $PROJECT/res as well as the crunch cache.
                element = resPath.createPathElement();
                element.setPath(libRootPath + "/" + SdkConstants.FD_OUTPUT +
                        "/" + SdkConstants.FD_RES);
                element = resPath.createPathElement();
                element.setPath(libRootPath + "/" + SdkConstants.FD_RESOURCES);

                // get the libs path. Always $PROJECT/libs
                element = libsPath.createPathElement();
                element.setPath(libRootPath + "/" + SdkConstants.FD_NATIVE_LIBS);

                // get the jars from it too.
                // 1. the library code jar
                element = jarsPath.createPathElement();
                element.setPath(libRootPath + "/" + SdkConstants.FD_OUTPUT +
                        "/" + SdkConstants.FN_CLASSES_JAR);

                // 2. the 3rd party jar files
                File libsFolder = new File(library, SdkConstants.FD_NATIVE_LIBS);
                File[] jarFiles = libsFolder.listFiles(filter);
                if (jarFiles != null) {
                    for (File jarFile : jarFiles) {
                        element = jarsPath.createPathElement();
                        element.setPath(jarFile.getAbsolutePath());
                    }
                }

                // get the package from the manifest.
                FileWrapper manifest = new FileWrapper(library,
                        SdkConstants.FN_ANDROID_MANIFEST_XML);

                try {
                    String value = AndroidManifest.getPackage(manifest);
                    if (value != null) { // aapt will complain if it's missing.
                        packageStrBuilder.append(';');
                        packageStrBuilder.append(value);
                    }
                } catch (Exception e) {
                    throw new BuildException(e);
                }
            }
        } else {
            System.out.println("No library dependencies.\n");
        }

        System.out.println("------------------\n");

        // even with no libraries, always setup these so that various tasks in Ant don't complain
        // (the task themselves can handle a ref to an empty Path)
        antProject.addReference(mProjectLibrariesJarsOut, jarsPath);
        antProject.addReference(mProjectLibrariesLibsOut, libsPath);

        // the rest is done only if there's a library.
        if (jarsPath.list().length > 0) {
            antProject.addReference(mProjectLibrariesRootOut, rootPath);
            antProject.addReference(mProjectLibrariesResOut, resPath);
            antProject.setProperty(mProjectLibrariesPackageOut, packageStrBuilder.toString());
        }
    }

    /**
     * Returns all the library dependencies of a given Ant project.
     * @param antProject the Ant project
     * @return a list of properties, sorted from highest priority to lowest.
     */
    private ArrayList<File> getProjectLibraries(final Project antProject) {
        ArrayList<File> libraries = new ArrayList<File>();
        File baseDir = antProject.getBaseDir();

        // get the top level list of library dependencies.
        List<File> topLevelLibraries = getDirectDependencies(baseDir, new IPropertySource() {
            public String getProperty(String name) {
                return antProject.getProperty(name);
            }
        });

        // process the libraries in case they depend on other libraries.
        resolveFullLibraryDependencies(topLevelLibraries, libraries);

        return libraries;
    }

    /**
     * Resolves a given list of libraries, finds out if they depend on other libraries, and
     * returns a full list of all the direct and indirect dependencies in the proper order (first
     * is higher priority when calling aapt).
     * @param inLibraries the libraries to resolve
     * @param outLibraries where to store all the libraries.
     */
    private void resolveFullLibraryDependencies(List<File> inLibraries, List<File> outLibraries) {
        // loop in the inverse order to resolve dependencies on the libraries, so that if a library
        // is required by two higher level libraries it can be inserted in the correct place
        for (int i = inLibraries.size() - 1  ; i >= 0 ; i--) {
            File library = inLibraries.get(i);

            // get the default.property file for it
            final ProjectProperties projectProp = ProjectProperties.load(
                    new FolderWrapper(library), PropertyType.PROJECT);

            // get its libraries
            List<File> dependencies = getDirectDependencies(library, new IPropertySource() {
                public String getProperty(String name) {
                    return projectProp.getProperty(name);
                }
            });

            // resolve the dependencies for those libraries
            resolveFullLibraryDependencies(dependencies, outLibraries);

            // and add the current one (if needed) in front (higher priority)
            if (outLibraries.contains(library) == false) {
                outLibraries.add(0, library);
            }
        }
    }

    public interface IPropertySource {
        String getProperty(String name);
    }

    /**
     * Returns the top level library dependencies of a given <var>source</var> representing a
     * project properties.
     * @param baseFolder the base folder of the project (to resolve relative paths)
     * @param source a source of project properties.
     */
    private List<File> getDirectDependencies(File baseFolder, IPropertySource source) {
        ArrayList<File> libraries = new ArrayList<File>();

        // first build the list. they are ordered highest priority first.
        int index = 1;
        while (true) {
            String propName = ProjectProperties.PROPERTY_LIB_REF + Integer.toString(index++);
            String rootPath = source.getProperty(propName);

            if (rootPath == null) {
                break;
            }

            try {
                File library = new File(baseFolder, rootPath).getCanonicalFile();

                // check for validity
                File projectProp = new File(library, PropertyType.PROJECT.getFilename());
                if (projectProp.isFile() == false) {
                    // error!
                    throw new BuildException(String.format(
                            "%1$s resolve to a path with no %2$s file for project %3$s", rootPath,
                            PropertyType.PROJECT.getFilename(), baseFolder.getAbsolutePath()));
                }

                if (libraries.contains(library) == false) {
                    System.out.println(String.format("%1$s: %2$s => %3$s",
                            baseFolder.getAbsolutePath(), rootPath, library.getAbsolutePath()));

                    libraries.add(library);
                }
            } catch (IOException e) {
                throw new BuildException("Failed to resolve library path: " + rootPath, e);
            }
        }

        return libraries;
    }

    /**
     * Returns the Ant version as a {@link DeweyDecimal} object.
     *
     * This is based on the implementation of
     * org.apache.tools.ant.taskdefs.condition.AntVersion.getVersion()
     *
     * @param antProject the current ant project.
     * @return the ant version.
     */
    private DeweyDecimal getVersion(Project antProject) {
        char[] versionString = antProject.getProperty("ant.version").toCharArray();
        StringBuilder sb = new StringBuilder();
        boolean foundFirstDigit = false;
        for (int i = 0; i < versionString.length; i++) {
            if (Character.isDigit(versionString[i])) {
                sb.append(versionString[i]);
                foundFirstDigit = true;
            }
            if (versionString[i] == '.' && foundFirstDigit) {
                sb.append(versionString[i]);
            }
            if (Character.isLetter(versionString[i]) && foundFirstDigit) {
                break;
            }
        }
        return new DeweyDecimal(sb.toString());
    }
}
