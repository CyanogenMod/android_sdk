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

package com.android.ide.eclipse.adt.internal.resources;

import com.android.ide.common.resources.ResourceDeltaKind;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.ResourceQualifier;

import org.eclipse.core.resources.IResourceDelta;

import junit.framework.TestCase;

/**
 * Test ResourceHelper
 */
public class ResourceHelperTest extends TestCase {

    /**
     * temp fake qualifier class.
     */
    private static class FakeQualifierClass extends ResourceQualifier {

        @Override
        public boolean checkAndSet(String value, FolderConfiguration config) {
            return false;
        }

        @Override
        public boolean equals(Object object) {
            return false;
        }

        @Override
        public String getFolderSegment() {
            return null;
        }

        @Override
        public String getLongDisplayValue() {
            return null;
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public String getShortDisplayValue() {
            return null;
        }

        @Override
        public String getShortName() {
            return null;
        }

        @Override
        public boolean hasFakeValue() {
            return false;
        }

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public boolean isValid() {
            return false;
        }

    }

    public void testgetIcon() throws Exception {
        // check that the method returns null for an unknown qualifier class
        assertNull(ResourceHelper.getIcon(FakeQualifierClass.class));

        // find all the qualifiers through FolderConfiguration.createdefault()
        FolderConfiguration config = new FolderConfiguration();
        config.createDefault();
        final int count = FolderConfiguration.getQualifierCount();
        for (int i = 0 ; i < count ; i++) {
            ResourceQualifier qual = config.getQualifier(i);
            assertNotNull(qual);
            assertNotNull(qual.getClass().getCanonicalName(),
                    ResourceHelper.getIcon(qual.getClass()));
        }
    }

    public void testGetResourceDeltaKind() {
        assertEquals(ResourceDeltaKind.ADDED,
                ResourceHelper.getResourceDeltaKind(IResourceDelta.ADDED));
        assertEquals(ResourceDeltaKind.REMOVED,
                ResourceHelper.getResourceDeltaKind(IResourceDelta.REMOVED));
        assertEquals(ResourceDeltaKind.CHANGED,
                ResourceHelper.getResourceDeltaKind(IResourceDelta.CHANGED));

        assertNull(ResourceHelper.getResourceDeltaKind(IResourceDelta.ADDED_PHANTOM));
    }
}
