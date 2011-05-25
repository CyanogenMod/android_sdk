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

package com.android.sdklib.internal.repository;

import com.android.annotations.VisibleForTesting;
import com.android.annotations.VisibleForTesting.Visibility;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.SdkManager;
import com.android.sdklib.internal.repository.Archive.Arch;
import com.android.sdklib.internal.repository.Archive.Os;
import com.android.sdklib.repository.SdkRepoConstants;
import com.android.util.Pair;

import org.w3c.dom.Node;

import java.io.File;
import java.util.Map;
import java.util.Properties;

/**
 * Represents a platform XML node in an SDK repository.
 */
public class PlatformPackage extends MinToolsPackage implements IPackageVersion {

    public static final String PROP_VERSION       = "Platform.Version";             //$NON-NLS-1$
    public static final String PROP_LAYOUTLIB_API = "Platform.Layoutlib.Api";       //$NON-NLS-1$
    public static final String PROP_LAYOUTLIB_REV = "Platform.Layoutlib.Revision";  //$NON-NLS-1$
    public static final int LAYOUTLIB_API_NOT_SPECIFIED = 0;
    public static final int LAYOUTLIB_REV_NOT_SPECIFIED = 0;

    /** The package version, for platform, add-on and doc packages. */
    private final AndroidVersion mVersion;

    /** The version, a string, for platform packages. */
    private final String mVersionName;

    /**
     * The layoutlib version. Mandatory starting with XSD rev 4.
     * The first integer is the API of layoublib, which should be > 0.
     * It will be equal to {@link #LAYOUTLIB_API_NOT_SPECIFIED} (0) if the layoutlib
     * version isn't specified.
     * The second integer is the revision for that given API. It is >= 0
     * and works as a minor revision number, incremented for the same API level.
     */
    private final Pair<Integer, Integer> mLayoutlibVersion;

    /**
     * Creates a new platform package from the attributes and elements of the given XML node.
     * This constructor should throw an exception if the package cannot be created.
     *
     * @param source The {@link SdkSource} where this is loaded from.
     * @param packageNode The XML element being parsed.
     * @param nsUri The namespace URI of the originating XML document, to be able to deal with
     *          parameters that vary according to the originating XML schema.
     * @param licenses The licenses loaded from the XML originating document.
     */
    PlatformPackage(SdkSource source, Node packageNode, String nsUri, Map<String,String> licenses) {
        super(source, packageNode, nsUri, licenses);

        mVersionName = XmlParserUtils.getXmlString(packageNode, SdkRepoConstants.NODE_VERSION);
        int apiLevel = XmlParserUtils.getXmlInt   (packageNode, SdkRepoConstants.NODE_API_LEVEL, 0);
        String codeName = XmlParserUtils.getXmlString(packageNode, SdkRepoConstants.NODE_CODENAME);
        if (codeName.length() == 0) {
            codeName = null;
        }

        mVersion = new AndroidVersion(apiLevel, codeName);

        mLayoutlibVersion = parseLayoutlib(
                XmlParserUtils.getFirstChild(packageNode, SdkRepoConstants.NODE_LAYOUT_LIB));
    }

    /**
     * Parses an XML node to process the {@code <layoutlib>} element.
     *
     * The layoutlib element is new in the XSD rev 4, so we need to cope with it missing
     * in earlier XMLs even though it is now mandatory.
     */
    private Pair<Integer, Integer> parseLayoutlib(Node layoutlibNode) {
        int api = LAYOUTLIB_API_NOT_SPECIFIED;
        int rev = LAYOUTLIB_REV_NOT_SPECIFIED;
        if (layoutlibNode != null) {
            api = XmlParserUtils.getXmlInt(layoutlibNode, SdkRepoConstants.NODE_API, 0);
            rev = XmlParserUtils.getXmlInt(layoutlibNode, SdkRepoConstants.NODE_REVISION, 0);
        }

        return Pair.of(api, rev);
    }

    /**
     * Creates a new platform package based on an actual {@link IAndroidTarget} (which
     * must have {@link IAndroidTarget#isPlatform()} true) from the {@link SdkManager}.
     * This is used to list local SDK folders in which case there is one archive which
     * URL is the actual target location.
     * <p/>
     * By design, this creates a package with one and only one archive.
     */
    static Package create(IAndroidTarget target, Properties props) {
        return new PlatformPackage(target, props);
    }

    @VisibleForTesting(visibility=Visibility.PRIVATE)
    protected PlatformPackage(IAndroidTarget target, Properties props) {
        super(  null,                       //source
                props,                      //properties
                target.getRevision(),       //revision
                null,                       //license
                target.getDescription(),    //description
                null,                       //descUrl
                Os.getCurrentOs(),          //archiveOs
                Arch.getCurrentArch(),      //archiveArch
                target.getLocation()        //archiveOsPath
                );

        mVersion = target.getVersion();
        mVersionName  = target.getVersionName();

        int layoutlibApi = Integer.parseInt(
            getProperty(props, PROP_LAYOUTLIB_API, Integer.toString(LAYOUTLIB_API_NOT_SPECIFIED)));
        int layoutlibRev = Integer.parseInt(
            getProperty(props, PROP_LAYOUTLIB_REV, Integer.toString(LAYOUTLIB_REV_NOT_SPECIFIED)));
        mLayoutlibVersion = Pair.of(layoutlibApi, layoutlibRev);
    }

    /**
     * Save the properties of the current packages in the given {@link Properties} object.
     * These properties will later be given to a constructor that takes a {@link Properties} object.
     */
    @Override
    void saveProperties(Properties props) {
        super.saveProperties(props);

        mVersion.saveProperties(props);

        if (mVersionName != null) {
            props.setProperty(PROP_VERSION, mVersionName);
        }

        if (mLayoutlibVersion.getFirst().intValue() != LAYOUTLIB_API_NOT_SPECIFIED) {
            props.setProperty(PROP_LAYOUTLIB_API, mLayoutlibVersion.getFirst().toString());
            props.setProperty(PROP_LAYOUTLIB_REV, mLayoutlibVersion.getSecond().toString());
        }
    }

    /** Returns the version, a string, for platform packages. */
    public String getVersionName() {
        return mVersionName;
    }

    /** Returns the package version, for platform, add-on and doc packages. */
    public AndroidVersion getVersion() {
        return mVersion;
    }

    /**
     * Returns the layoutlib version. Mandatory starting with repository XSD rev 4.
     * <p/>
     * The first integer is the API of layoublib, which should be > 0.
     * It will be equal to {@link #LAYOUTLIB_API_NOT_SPECIFIED} (0) if the layoutlib
     * version isn't specified.
     * <p/>
     * The second integer is the revision for that given API. It is >= 0
     * and works as a minor revision number, incremented for the same API level.
     *
     * @since sdk-repository-4.xsd
     */
    public Pair<Integer, Integer> getLayoutlibVersion() {
        return mLayoutlibVersion;
    }

    /**
     * Returns a description of this package that is suitable for a list display.
     * <p/>
     * {@inheritDoc}
     */
    @Override
    public String getListDescription() {
        String s;

        if (mVersion.isPreview()) {
            s = String.format("SDK Platform Android %1$s Preview%2$s",
                    getVersionName(),
                    isObsolete() ? " (Obsolete)" : "");  //$NON-NLS-2$
        } else {
            s = String.format("SDK Platform Android %1$s%2$s",
                getVersionName(),
                isObsolete() ? " (Obsolete)" : "");      //$NON-NLS-2$
        }

        return s;
    }

    /**
     * Returns a short description for an {@link IDescription}.
     */
    @Override
    public String getShortDescription() {
        String s;

        if (mVersion.isPreview()) {
            s = String.format("SDK Platform Android %1$s Preview, revision %2$s%3$s",
                    getVersionName(),
                    getRevision(),
                    isObsolete() ? " (Obsolete)" : "");  //$NON-NLS-2$
        } else {
            s = String.format("SDK Platform Android %1$s, API %2$d, revision %3$s%4$s",
                getVersionName(),
                mVersion.getApiLevel(),
                getRevision(),
                isObsolete() ? " (Obsolete)" : "");      //$NON-NLS-2$
        }

        return s;
    }

    /**
     * Returns a long description for an {@link IDescription}.
     *
     * The long description is whatever the XML contains for the &lt;description&gt; field,
     * or the short description if the former is empty.
     */
    @Override
    public String getLongDescription() {
        String s = getDescription();
        if (s == null || s.length() == 0) {
            s = getShortDescription();
        }

        if (s.indexOf("revision") == -1) {
            s += String.format("\nRevision %1$d%2$s",
                    getRevision(),
                    isObsolete() ? " (Obsolete)" : "");
        }

        return s;
    }

    /**
     * Computes a potential installation folder if an archive of this package were
     * to be installed right away in the given SDK root.
     * <p/>
     * A platform package is typically installed in SDK/platforms/android-"version".
     * However if we can find a different directory under SDK/platform that already
     * has this platform version installed, we'll use that one.
     *
     * @param osSdkRoot The OS path of the SDK root folder.
     * @param sdkManager An existing SDK manager to list current platforms and addons.
     * @return A new {@link File} corresponding to the directory to use to install this package.
     */
    @Override
    public File getInstallFolder(String osSdkRoot, SdkManager sdkManager) {

        // First find if this platform is already installed. If so, reuse the same directory.
        for (IAndroidTarget target : sdkManager.getTargets()) {
            if (target.isPlatform() && target.getVersion().equals(mVersion)) {
                return new File(target.getLocation());
            }
        }

        File platforms = new File(osSdkRoot, SdkConstants.FD_PLATFORMS);
        File folder = new File(platforms,
                String.format("android-%s", getVersion().getApiString())); //$NON-NLS-1$

        return folder;
    }

    @Override
    public boolean sameItemAs(Package pkg) {
        if (pkg instanceof PlatformPackage) {
            PlatformPackage newPkg = (PlatformPackage)pkg;

            // check they are the same platform.
            return newPkg.getVersion().equals(this.getVersion());
        }

        return false;
    }
}
