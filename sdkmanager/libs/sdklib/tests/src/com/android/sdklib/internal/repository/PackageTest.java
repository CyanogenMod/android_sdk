/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.sdklib.SdkManager;
import com.android.sdklib.internal.repository.Archive.Arch;
import com.android.sdklib.internal.repository.Archive.Os;

import java.io.File;
import java.util.Properties;

import junit.framework.TestCase;

public class PackageTest extends TestCase {

    /** Local class used to test the abstract Package class */
    protected static class MockPackage extends Package {
        public MockPackage(
                SdkSource source,
                Properties props,
                int revision,
                String license,
                String description,
                String descUrl,
                Os archiveOs,
                Arch archiveArch,
                String archiveOsPath) {
            super(source,
                    props,
                    revision,
                    license,
                    description,
                    descUrl,
                    archiveOs,
                    archiveArch,
                    archiveOsPath);
        }

        @Override
        public File getInstallFolder(String osSdkRoot, SdkManager sdkManager) {
            throw new UnsupportedOperationException("abstract method not used in test"); //$NON-NLS-1$
        }

        @Override
        public String getListDescription() {
            throw new UnsupportedOperationException("abstract method not used in test"); //$NON-NLS-1$
        }

        @Override
        public String getShortDescription() {
            throw new UnsupportedOperationException("abstract method not used in test"); //$NON-NLS-1$
        }

        @Override
        public boolean sameItemAs(Package pkg) {
            throw new UnsupportedOperationException("abstract method not used in test"); //$NON-NLS-1$
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testCreate() {
        Properties props = createProps();

        Package p = new MockPackage(
                null, //source
                props,
                -1, //revision
                null, //license
                null, //description
                null, //descUrl
                Os.ANY, //archiveOs
                Arch.ANY, //archiveArch
                "/local/archive/path" //archiveOsPath
                );

        testCreatedPackage(p);
    }

    public void testSaveProperties() {
        Properties props = createProps();

        Package p = new MockPackage(
                null, //source
                props,
                -1, //revision
                null, //license
                null, //description
                null, //descUrl
                Os.ANY, //archiveOs
                Arch.ANY, //archiveArch
                "/local/archive/path" //archiveOsPath
                );

        Properties props2 = new Properties();
        p.saveProperties(props2);

        assertEquals(props2, props);
    }

    /**
     * Sets the properties used by {@link #testCreate()} and
     * {@link #testSaveProperties()}.
     * This is protected and used by derived classes to perform
     * a similar creation test.
     */
    protected Properties createProps() {
        Properties props = new Properties();

        // Package properties
        props.setProperty(ExtraPackage.PROP_REVISION, "42");
        props.setProperty(ExtraPackage.PROP_LICENSE, "The License");
        props.setProperty(ExtraPackage.PROP_DESC, "Some description.");
        props.setProperty(ExtraPackage.PROP_DESC_URL, "http://description/url");
        props.setProperty(ExtraPackage.PROP_RELEASE_NOTE, "Release Note");
        props.setProperty(ExtraPackage.PROP_RELEASE_URL, "http://release/note");
        props.setProperty(ExtraPackage.PROP_SOURCE_URL, "http://source/url");
        props.setProperty(ExtraPackage.PROP_OBSOLETE, "true");
        return props;
    }

    /**
     * Tests the values set via {@link #createProps()} after the
     * package has been created in {@link #testCreate()}.
     * This is protected and used by derived classes to perform
     * a similar creation test.
     */
    protected void testCreatedPackage(Package p) {
        // Package properties
        assertEquals(42, p.getRevision());
        assertEquals("The License", p.getLicense());
        assertEquals("Some description.", p.getDescription());
        assertEquals("http://description/url", p.getDescUrl());
        assertEquals("Release Note", p.getReleaseNote());
        assertEquals("http://release/note", p.getReleaseNoteUrl());
        assertEquals(new SdkRepoSource("http://source/url", null /*uiName*/), p.getParentSource());
        assertTrue(p.isObsolete());

        assertNotNull(p.getArchives());
        assertEquals(1, p.getArchives().length);
        Archive a = p.getArchives()[0];
        assertNotNull(a);
        assertEquals(Os.ANY, a.getOs());
        assertEquals(Arch.ANY, a.getArch());
        assertEquals("/local/archive/path", a.getLocalOsPath());
    }

}
