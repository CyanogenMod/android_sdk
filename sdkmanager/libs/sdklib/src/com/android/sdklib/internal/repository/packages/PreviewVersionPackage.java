/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.sdklib.internal.repository.packages;

import com.android.annotations.NonNull;
import com.android.sdklib.internal.repository.XmlParserUtils;
import com.android.sdklib.internal.repository.archives.Archive.Arch;
import com.android.sdklib.internal.repository.archives.Archive.Os;
import com.android.sdklib.internal.repository.sources.SdkSource;
import com.android.sdklib.repository.PkgProps;
import com.android.sdklib.repository.SdkRepoConstants;

import org.w3c.dom.Node;

import java.util.Map;
import java.util.Properties;

/**
 * Represents a package in an SDK repository that has a {@link PreviewVersion},
 * which is a multi-part revision number (major.minor.micro) and an optional preview revision.
 */
public abstract class PreviewVersionPackage extends Package
        implements IPreviewVersionProvider {

    private final PreviewVersion mPreviewVersion;

    /**
     * Creates a new package from the attributes and elements of the given XML node.
     * This constructor should throw an exception if the package cannot be created.
     *
     * @param source The {@link SdkSource} where this is loaded from.
     * @param packageNode The XML element being parsed.
     * @param nsUri The namespace URI of the originating XML document, to be able to deal with
     *          parameters that vary according to the originating XML schema.
     * @param licenses The licenses loaded from the XML originating document.
     */
    PreviewVersionPackage(SdkSource source,
            Node packageNode,
            String nsUri,
            Map<String,String> licenses) {
        super(source, packageNode, nsUri, licenses);

        // The major revision is getRevision(), already handled by Package.

        int minorRevision = XmlParserUtils.getXmlInt(packageNode,
                SdkRepoConstants.NODE_MINOR_REV,
                PreviewVersion.IMPLICIT_MINOR_REV);
        int microRevision = XmlParserUtils.getXmlInt(packageNode,
                SdkRepoConstants.NODE_MICRO_REV,
                PreviewVersion.IMPLICIT_MICRO_REV);
        int preview = XmlParserUtils.getXmlInt(packageNode,
                SdkRepoConstants.NODE_PREVIEW,
                PreviewVersion.NOT_A_PREVIEW);

        mPreviewVersion = new PreviewVersion(getRevision(), minorRevision, microRevision, preview);
    }

    /**
     * Manually create a new package with one archive and the given attributes.
     * This is used to create packages from local directories in which case there must be
     * one archive which URL is the actual target location.
     * <p/>
     * Properties from props are used first when possible, e.g. if props is non null.
     * <p/>
     * By design, this creates a package with one and only one archive.
     */
    public PreviewVersionPackage(
            SdkSource source,
            Properties props,
            int revision,
            String license,
            String description,
            String descUrl,
            Os archiveOs,
            Arch archiveArch,
            String archiveOsPath) {
        super(source, props, revision, license, description, descUrl,
                archiveOs, archiveArch, archiveOsPath);

        // The major revision is getRevision(), already handled by Package.

        int minorRevision = Integer.parseInt(
                getProperty(props,
                        PkgProps.PKG_MINOR_REV,
                        Integer.toString(PreviewVersion.IMPLICIT_MINOR_REV)));
        int microRevision = Integer.parseInt(
                getProperty(props,
                        PkgProps.PKG_MICRO_REV,
                        Integer.toString(PreviewVersion.IMPLICIT_MINOR_REV)));
        int preview = Integer.parseInt(
                getProperty(props,
                        PkgProps.PKG_PREVIEW_REV,
                        Integer.toString(PreviewVersion.NOT_A_PREVIEW)));

        mPreviewVersion = new PreviewVersion(getRevision(), minorRevision, microRevision, preview);
    }

    @Override @NonNull
    public PreviewVersion getPreviewVersion() {
        return mPreviewVersion;
    }

    @Override
    public void saveProperties(Properties props) {
        super.saveProperties(props);

        // The major revision is getRevision(), already handled by Package.
        assert mPreviewVersion.getMajor() == getRevision();

        props.setProperty(PkgProps.PKG_MINOR_REV, Integer.toString(mPreviewVersion.getMinor()));
        props.setProperty(PkgProps.PKG_MICRO_REV, Integer.toString(mPreviewVersion.getMicro()));
        props.setProperty(PkgProps.PKG_PREVIEW_REV, Integer.toString(mPreviewVersion.getPreview()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((mPreviewVersion == null) ? 0 : mPreviewVersion.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (!(obj instanceof PreviewVersionPackage)) {
            return false;
        }
        PreviewVersionPackage other = (PreviewVersionPackage) obj;
        if (mPreviewVersion == null) {
            if (other.mPreviewVersion != null) {
                return false;
            }
        } else if (!mPreviewVersion.equals(other.mPreviewVersion)) {
            return false;
        }
        return true;
    }
}
