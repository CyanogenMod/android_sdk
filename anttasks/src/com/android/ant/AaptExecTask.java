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

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.ExecTask;
import org.apache.tools.ant.types.Path;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Task to execute aapt.
 *
 * <p>It does not follow the exec task format, instead it has its own parameters, which maps
 * directly to aapt.</p>
 * <p>It is able to run aapt several times if library setup requires generating several
 * R.java files.
 * <p>The following map shows how to use the task for each supported aapt command line
 * parameter.</p>
 *
 * <table border="1">
 * <tr><td><b>Aapt Option</b></td><td><b>Ant Name</b></td><td><b>Type</b></td></tr>
 * <tr><td>path to aapt</td><td>executable</td><td>attribute (Path)</td>
 * <tr><td>command</td><td>command</td><td>attribute (String)</td>
 * <tr><td>-v</td><td>verbose</td><td>attribute (boolean)</td></tr>
 * <tr><td>-f</td><td>force</td><td>attribute (boolean)</td></tr>
 * <tr><td>-M AndroidManifest.xml</td><td>manifest</td><td>attribute (Path)</td></tr>
 * <tr><td>-I base-package</td><td>androidjar</td><td>attribute (Path)</td></tr>
 * <tr><td>-A asset-source-dir</td><td>assets</td><td>attribute (Path</td></tr>
 * <tr><td>-S resource-sources</td><td>&lt;res path=""&gt;</td><td>nested element(s)<br>with attribute (Path)</td></tr>
 * <tr><td>-0 extension</td><td>&lt;nocompress extension=""&gt;<br>&lt;nocompress&gt;</td><td>nested element(s)<br>with attribute (String)</td></tr>
 * <tr><td>-F apk-file</td><td>apkfolder<br>outfolder<br>apkbasename<br>basename</td><td>attribute (Path)<br>attribute (Path) deprecated<br>attribute (String)<br>attribute (String) deprecated</td></tr>
 * <tr><td>-J R-file-dir</td><td>rfolder</td><td>attribute (Path)<br>-m always enabled</td></tr>
 * <tr><td></td><td></td><td></td></tr>
 * </table>
 */
public final class AaptExecTask extends BaseTask {

    /**
     * Class representing a &lt;nocompress&gt; node in the main task XML.
     * This let the developers prevent compression of some files in assets/ and res/raw/
     * by extension.
     * If the extension is null, this will disable compression for all  files in assets/ and
     * res/raw/
     */
    public final static class NoCompress {
        String mExtension;

        /**
         * Sets the value of the "extension" attribute.
         * @param extention the extension.
         */
        public void setExtension(String extention) {
            mExtension = extention;
        }
    }

    private String mExecutable;
    private String mCommand;
    private boolean mForce = true; // true due to legacy reasons
    private boolean mDebug = false;
    private boolean mVerbose = false;
    private boolean mUseCrunchCache = false;
    private int mVersionCode = 0;
    private String mVersionName;
    private String mManifest;
    private ArrayList<Path> mResources;
    private String mAssets;
    private String mAndroidJar;
    private String mApkFolder;
    private String mApkName;
    private String mResourceFilter;
    private String mRFolder;
    private final ArrayList<NoCompress> mNoCompressList = new ArrayList<NoCompress>();
    private String mProjectLibrariesResName;
    private String mProjectLibrariesPackageName;
    private boolean mNonConstantId;

    /**
     * Sets the value of the "executable" attribute.
     * @param executable the value.
     */
    public void setExecutable(Path executable) {
        mExecutable = TaskHelper.checkSinglePath("executable", executable);
    }

    /**
     * Sets the value of the "command" attribute.
     * @param command the value.
     */
    public void setCommand(String command) {
        mCommand = command;
    }

    /**
     * Sets the value of the "force" attribute.
     * @param force the value.
     */
    public void setForce(boolean force) {
        mForce = force;
    }

    /**
     * Sets the value of the "verbose" attribute.
     * @param verbose the value.
     */
    public void setVerbose(boolean verbose) {
        mVerbose = verbose;
    }

    /**
     * Sets the value of the "usecrunchcache" attribute
     * @param usecrunch whether to use the crunch cache.
     */
    public void setNoCrunch(boolean nocrunch) {
        mUseCrunchCache = nocrunch;
    }

    public void setNonConstantId(boolean nonConstantId) {
        mNonConstantId = nonConstantId;
    }

    public void setVersioncode(String versionCode) {
        if (versionCode.length() > 0) {
            try {
                mVersionCode = Integer.decode(versionCode);
            } catch (NumberFormatException e) {
                System.out.println(String.format(
                        "WARNING: Ignoring invalid version code value '%s'.", versionCode));
            }
        }
    }

    /**
     * Sets the value of the "versionName" attribute
     * @param versionName the value
     */
    public void setVersionname(String versionName) {
        mVersionName = versionName;
    }

    public void setDebug(boolean value) {
        mDebug = value;
    }

    /**
     * Sets the value of the "manifest" attribute.
     * @param manifest the value.
     */
    public void setManifest(Path manifest) {
        mManifest = TaskHelper.checkSinglePath("manifest", manifest);
    }

    /**
     * Sets the value of the "resources" attribute.
     * @param resources the value.
     *
     * @deprecated Use nested element(s) <res path="value" />
     */
    @Deprecated
    public void setResources(Path resources) {
        System.out.println("WARNNG: Using deprecated 'resources' attribute in AaptExecLoopTask." +
                "Use nested element(s) <res path=\"value\" /> instead.");
        if (mResources == null) {
            mResources = new ArrayList<Path>();
        }

        mResources.add(new Path(getProject(), resources.toString()));
    }

    /**
     * Sets the value of the "assets" attribute.
     * @param assets the value.
     */
    public void setAssets(Path assets) {
        mAssets = TaskHelper.checkSinglePath("assets", assets);
    }

    /**
     * Sets the value of the "androidjar" attribute.
     * @param androidJar the value.
     */
    public void setAndroidjar(Path androidJar) {
        mAndroidJar = TaskHelper.checkSinglePath("androidjar", androidJar);
    }

    /**
     * Sets the value of the "outfolder" attribute.
     * @param outFolder the value.
     * @deprecated use {@link #setApkfolder(Path)}
     */
    @Deprecated
    public void setOutfolder(Path outFolder) {
        System.out.println("WARNNG: Using deprecated 'outfolder' attribute in AaptExecLoopTask." +
                "Use 'apkfolder' (path) instead.");
        mApkFolder = TaskHelper.checkSinglePath("outfolder", outFolder);
    }

    /**
     * Sets the value of the "apkfolder" attribute.
     * @param apkFolder the value.
     */
    public void setApkfolder(Path apkFolder) {
        mApkFolder = TaskHelper.checkSinglePath("apkfolder", apkFolder);
    }

    /**
     * Sets the value of the resourcefilename attribute
     * @param apkName the value
     */
    public void setResourcefilename(String apkName) {
        mApkName = apkName;
    }

    /**
     * Sets the value of the "rfolder" attribute.
     * @param rFolder the value.
     */
    public void setRfolder(Path rFolder) {
        mRFolder = TaskHelper.checkSinglePath("rfolder", rFolder);
    }

    public void setresourcefilter(String filter) {
        if (filter != null && filter.length() > 0) {
            mResourceFilter = filter;
        }
    }

    public void setProjectLibrariesResName(String projectLibrariesResName) {
        mProjectLibrariesResName = projectLibrariesResName;
    }

    public void setProjectLibrariesPackageName(String projectLibrariesPackageName) {
        mProjectLibrariesPackageName = projectLibrariesPackageName;
    }


    /**
     * Returns an object representing a nested <var>nocompress</var> element.
     */
    public Object createNocompress() {
        NoCompress nc = new NoCompress();
        mNoCompressList.add(nc);
        return nc;
    }

    /**
     * Returns an object representing a nested <var>res</var> element.
     */
    public Object createRes() {
        if (mResources == null) {
            mResources = new ArrayList<Path>();
        }

        Path path = new Path(getProject());
        mResources.add(path);

        return path;
    }

    /*
     * (non-Javadoc)
     *
     * Executes the loop. Based on the values inside project.properties, this will
     * create alternate temporary ap_ files.
     *
     * @see org.apache.tools.ant.Task#execute()
     */
    @Override
    public void execute() throws BuildException {
        if (mProjectLibrariesResName == null) {
            throw new BuildException("Missing attribute projectLibrariesResName");
        }
        if (mProjectLibrariesPackageName == null) {
            throw new BuildException("Missing attribute projectLibrariesPackageName");
        }

        Project taskProject = getProject();

        String libPkgProp = null;

        // if the parameters indicate generation of the R class, check if
        // more R classes need to be created for libraries.
        if (mRFolder != null && new File(mRFolder).isDirectory()) {
            libPkgProp = taskProject.getProperty(mProjectLibrariesPackageName);
            if (libPkgProp != null) {
                // Replace ";" with ":" since that's what aapt expects
                libPkgProp = libPkgProp.replace(';', ':');
            }
        }
        // Call aapt. If there are libraries, we'll pass a non-null string of libs.
        callAapt(libPkgProp);
    }

    @Override
    protected String getExecTaskName() {
        return "aapt";
    }

    /**
     * Calls aapt with the given parameters.
     * @param resourceFilter the resource configuration filter to pass to aapt (if configName is
     * non null)
     * @param extraPackages an optional list of colon-separated packages. Can be null
     *        Ex: com.foo.one:com.foo.two:com.foo.lib
     */
    private void callAapt(String extraPackages) {
        Project taskProject = getProject();

        final boolean generateRClass = mRFolder != null && new File(mRFolder).isDirectory();

        // Get whether we have libraries
        Object libResRef = taskProject.getReference(mProjectLibrariesResName);

        // Set up our input paths that matter for dependency checks
        ArrayList<File> paths = new ArrayList<File>();

        // the project res folder is an input path of course
        for (Path pathList : mResources) {
            for (String path : pathList.list()) {
                paths.add(new File(path));
            }
        }

        // and if libraries exist, their res folders folders too.
        if (libResRef instanceof Path) {
            for (String path : ((Path)libResRef).list()) {
                paths.add(new File(path));
            }
        }

        // Now we figure out what we need to do
        if (generateRClass) {
            // in this case we only want to run aapt if an XML file was touched, or if any
            // file is added/removed
            List<InputPath> inputPaths = getInputPaths(paths, Collections.singleton("xml"));

            // let's not forget the manifest as an input path (with no extension restrictions).
            if (mManifest != null) {
                inputPaths.add(new InputPath(new File(mManifest)));
            }

            // Check to see if our dependencies have changed. If not, then skip
            if (initDependencies(mRFolder + File.separator + "R.java.d", inputPaths)
                              && dependenciesHaveChanged() == false) {
                System.out.println("No changed resources. R.java and Manifest.java untouched.");
                return;
            } else {
                System.out.println("Generating resource IDs...");
            }
        } else {
            // in this case we want to run aapt if any file was updated/removed/added in any of the
            // input paths
            List<InputPath> inputPaths = getInputPaths(paths, null /*extensionsToCheck*/);

            // let's not forget the manifest as an input path.
            if (mManifest != null) {
                inputPaths.add(new InputPath(new File(mManifest)));
            }

            // If we're here to generate a .ap_ file we need to use assets as an input path as well.
            if (mAssets != null) {
                File assetsDir = new File(mAssets);
                if (assetsDir.isDirectory()) {
                    inputPaths.add(new InputPath(assetsDir));
                }
            }

            // Find our dependency file. It should have the same name as our target .ap_ but
            // with a .d extension
            String dependencyFilePath = mApkFolder + File.separator + mApkName;
            dependencyFilePath += ".d";

            // Check to see if our dependencies have changed
            if (initDependencies(dependencyFilePath, inputPaths)
                            && dependenciesHaveChanged() == false) {
                System.out.println("No changed resources or assets. " + mApkName
                                    + " remains untouched");
                return;
            }
            if (mResourceFilter == null) {
                System.out.println("Creating full resource package...");
            } else {
                System.out.println(String.format(
                        "Creating resource package with filter: (%1$s)...",
                        mResourceFilter));
            }
        }

        // create a task for the default apk.
        ExecTask task = new ExecTask();
        task.setExecutable(mExecutable);
        task.setFailonerror(true);

        task.setTaskName(getExecTaskName());

        // aapt command. Only "package" is supported at this time really.
        task.createArg().setValue(mCommand);

        // No crunch flag
        if (mUseCrunchCache) {
            task.createArg().setValue("--no-crunch");
        }

        if (mNonConstantId) {
            task.createArg().setValue("--non-constant-id");
        }

        // force flag
        if (mForce) {
            task.createArg().setValue("-f");
        }

        // verbose flag
        if (mVerbose) {
            task.createArg().setValue("-v");
        }

        if (mDebug) {
            task.createArg().setValue("--debug-mode");
        }

        if (generateRClass) {
            task.createArg().setValue("-m");
        }

        // filters if needed
        if (mResourceFilter != null) {
            task.createArg().setValue("-c");
            task.createArg().setValue(mResourceFilter);
        }

        // no compress flag
        // first look to see if there's a NoCompress object with no specified extension
        boolean compressNothing = false;
        for (NoCompress nc : mNoCompressList) {
            if (nc.mExtension == null) {
                task.createArg().setValue("-0");
                task.createArg().setValue("");
                compressNothing = true;
                break;
            }
        }

        if (compressNothing == false) {
            for (NoCompress nc : mNoCompressList) {
                task.createArg().setValue("-0");
                task.createArg().setValue(nc.mExtension);
            }
        }

        if (extraPackages != null) {
            task.createArg().setValue("--extra-packages");
            task.createArg().setValue(extraPackages);
        }

        // if the project contains libraries, force auto-add-overlay
        if (libResRef != null) {
            task.createArg().setValue("--auto-add-overlay");
        }

        if (mVersionCode != 0) {
            task.createArg().setValue("--version-code");
            task.createArg().setValue(Integer.toString(mVersionCode));
        }

        if ((mVersionName != null) && (mVersionName.length() > 0)) {
            task.createArg().setValue("--version-name");
            task.createArg().setValue(mVersionName);
        }

        // manifest location
        if (mManifest != null) {
            task.createArg().setValue("-M");
            task.createArg().setValue(mManifest);
        }

        // resources locations.
        if (mResources.size() > 0) {
            for (Path pathList : mResources) {
                for (String path : pathList.list()) {
                    // This may not exists, and aapt doesn't like it, so we check first.
                    File res = new File(path);
                    if (res.isDirectory()) {
                        task.createArg().setValue("-S");
                        task.createArg().setValue(path);
                    }
                }
            }
        }

        // add other resources coming from library project
        if (libResRef instanceof Path) {
            for (String path : ((Path)libResRef).list()) {
                // This may not exists, and aapt doesn't like it, so we check first.
                File res = new File(path);
                if (res.isDirectory()) {
                    task.createArg().setValue("-S");
                    task.createArg().setValue(path);
                }
            }
        }

        // assets location. This may not exists, and aapt doesn't like it, so we check first.
        if (mAssets != null && new File(mAssets).isDirectory()) {
            task.createArg().setValue("-A");
            task.createArg().setValue(mAssets);
        }

        // android.jar
        if (mAndroidJar != null) {
            task.createArg().setValue("-I");
            task.createArg().setValue(mAndroidJar);
        }

        // apk file. This is based on the apkFolder, apkBaseName, and the configName (if applicable)
        String filename = null;
        if (mApkName != null) {
            filename = mApkName;
        }

        if (filename != null) {
            File file = new File(mApkFolder, filename);
            task.createArg().setValue("-F");
            task.createArg().setValue(file.getAbsolutePath());
        }

        // R class generation
        if (generateRClass) {
            task.createArg().setValue("-J");
            task.createArg().setValue(mRFolder);
        }

        // Use dependency generation
        task.createArg().setValue("--generate-dependencies");

        // final setup of the task
        task.setProject(taskProject);
        task.setOwningTarget(getOwningTarget());

        // execute it.
        task.execute();
    }
}
