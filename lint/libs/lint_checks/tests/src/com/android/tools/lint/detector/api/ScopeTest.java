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

package com.android.tools.lint.detector.api;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public class ScopeTest extends TestCase {
    public void testWithin() throws Exception {
        assertTrue(Scope.SINGLE_FILE.within(Scope.SINGLE_FILE));
        assertTrue(Scope.SINGLE_FILE.within(Scope.JAVA_CODE));
        assertTrue(Scope.SINGLE_FILE.within(Scope.JAVA));
        assertTrue(Scope.SINGLE_FILE.within(Scope.RESOURCES));
        assertTrue(Scope.SINGLE_FILE.within(Scope.PROJECT));
        assertTrue(Scope.RESOURCES.within(Scope.RESOURCES));
        assertTrue(Scope.RESOURCES.within(Scope.PROJECT));
        assertTrue(Scope.JAVA_CODE.within(Scope.JAVA));
        assertTrue(Scope.JAVA_CODE.within(Scope.PROJECT));
        assertTrue(Scope.JAVA.within(Scope.JAVA));
        assertTrue(Scope.JAVA.within(Scope.PROJECT));
        assertTrue(Scope.PROJECT.within(Scope.PROJECT));

        assertFalse(Scope.PROJECT.within(Scope.SINGLE_FILE));
        assertFalse(Scope.RESOURCES.within(Scope.SINGLE_FILE));
        assertFalse(Scope.JAVA.within(Scope.SINGLE_FILE));
        assertFalse(Scope.JAVA_CODE.within(Scope.SINGLE_FILE));
        assertFalse(Scope.PROJECT.within(Scope.RESOURCES));
        assertFalse(Scope.JAVA.within(Scope.RESOURCES));
        assertFalse(Scope.JAVA_CODE.within(Scope.RESOURCES));
        assertFalse(Scope.PROJECT.within(Scope.JAVA));
        assertFalse(Scope.PROJECT.within(Scope.JAVA_CODE));
        assertFalse(Scope.JAVA.within(Scope.JAVA_CODE));
        assertFalse(Scope.JAVA.within(Scope.JAVA_CODE));
    }
}
