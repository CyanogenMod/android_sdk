/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.ide.common.resources.configuration;

import com.android.resources.ScreenLayoutSize;

import junit.framework.TestCase;

public class ScreenSizeQualifierTest extends TestCase {

    private ScreenLayoutSizeQualifier ssq;
    private FolderConfiguration config;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        ssq = new ScreenLayoutSizeQualifier();
        config = new FolderConfiguration();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        ssq = null;
        config = null;
    }

    public void testSmall() {
        assertEquals(true, ssq.checkAndSet("small", config)); //$NON-NLS-1$
        assertTrue(config.getScreenLayoutSizeQualifier() != null);
        assertEquals(ScreenLayoutSize.SMALL, config.getScreenLayoutSizeQualifier().getValue());
        assertEquals("small", config.getScreenLayoutSizeQualifier().toString()); //$NON-NLS-1$
    }

    public void testNormal() {
        assertEquals(true, ssq.checkAndSet("normal", config)); //$NON-NLS-1$
        assertTrue(config.getScreenLayoutSizeQualifier() != null);
        assertEquals(ScreenLayoutSize.NORMAL, config.getScreenLayoutSizeQualifier().getValue());
        assertEquals("normal", config.getScreenLayoutSizeQualifier().toString()); //$NON-NLS-1$
    }

    public void testLarge() {
        assertEquals(true, ssq.checkAndSet("large", config)); //$NON-NLS-1$
        assertTrue(config.getScreenLayoutSizeQualifier() != null);
        assertEquals(ScreenLayoutSize.LARGE, config.getScreenLayoutSizeQualifier().getValue());
        assertEquals("large", config.getScreenLayoutSizeQualifier().toString()); //$NON-NLS-1$
    }

    public void testXLarge() {
        assertEquals(true, ssq.checkAndSet("xlarge", config)); //$NON-NLS-1$
        assertTrue(config.getScreenLayoutSizeQualifier() != null);
        assertEquals(ScreenLayoutSize.XLARGE, config.getScreenLayoutSizeQualifier().getValue());
        assertEquals("xlarge", config.getScreenLayoutSizeQualifier().toString()); //$NON-NLS-1$
    }
}
