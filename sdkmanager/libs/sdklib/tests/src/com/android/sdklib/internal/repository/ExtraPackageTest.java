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

import com.android.sdklib.internal.repository.Archive.Arch;
import com.android.sdklib.internal.repository.Archive.Os;

import java.io.File;
import java.util.Arrays;
import java.util.Properties;

public class ExtraPackageTest extends MinToolsPackageTest {

    private static final char PS = File.pathSeparatorChar;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    @Override
    public final void testCreate() {
        Properties props = createProps();

        ExtraPackage p = (ExtraPackage) ExtraPackage.create(
                null, //source
                props,
                null, //vendor
                null, //path
                -1, //revision
                null, //license
                null, //description
                null, //descUrl
                Os.ANY, //archiveOs
                Arch.ANY, //archiveArch
                "/local/archive/path" //archiveOsPath
                );

        testCreatedExtraPackage(p);
    }

    @Override
    public void testSaveProperties() {
        Properties props = createProps();

        ExtraPackage p = (ExtraPackage) ExtraPackage.create(
                null, //source
                props,
                null, //vendor
                null, //path
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

    @Override
    protected Properties createProps() {
        Properties props = super.createProps();

        // ExtraPackage properties
        props.setProperty(ExtraPackage.PROP_VENDOR, "vendor");
        props.setProperty(ExtraPackage.PROP_PATH, "the_path");
        props.setProperty(ExtraPackage.PROP_MIN_API_LEVEL, "11");
        props.setProperty(ExtraPackage.PROP_PROJECT_FILES,
                "path1.jar" + PS + "dir2/jar 2.jar" + PS + "dir/3/path");

        return props;
    }

    protected void testCreatedExtraPackage(ExtraPackage p) {
        super.testCreatedPackage(p);

        // Package properties
        assertEquals("vendor", p.getVendor());
        assertEquals("the_path", p.getPath());
        assertEquals(11, p.getMinApiLevel());
        assertEquals(
                "[path1.jar, dir2/jar 2.jar, dir/3/path]",
                Arrays.toString(p.getProjectFiles()));
    }
}
