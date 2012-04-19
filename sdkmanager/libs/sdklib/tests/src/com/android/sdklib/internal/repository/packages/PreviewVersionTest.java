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

import junit.framework.TestCase;

public class PreviewVersionTest extends TestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public final void testPreviewVersion() {
        PreviewVersion p = new PreviewVersion(5);
        assertEquals(5, p.getMajor());
        assertEquals(PreviewVersion.IMPLICIT_MINOR_REV, p.getMinor());
        assertEquals(PreviewVersion.IMPLICIT_MICRO_REV, p.getMicro());
        assertEquals(PreviewVersion.NOT_A_PREVIEW, p.getPreview());
        assertFalse (p.isPreview());
        assertEquals("5", p.toShortString());
        assertEquals("5.0.0", p.toString());

        p = new PreviewVersion(5, 0, 0, 6);
        assertEquals(5, p.getMajor());
        assertEquals(PreviewVersion.IMPLICIT_MINOR_REV, p.getMinor());
        assertEquals(PreviewVersion.IMPLICIT_MICRO_REV, p.getMicro());
        assertEquals(6, p.getPreview());
        assertTrue  (p.isPreview());
        assertEquals("5 rc6", p.toShortString());
        assertEquals("5.0.0 rc6", p.toString());

        p = new PreviewVersion(6, 7, 0);
        assertEquals(6, p.getMajor());
        assertEquals(7, p.getMinor());
        assertEquals(0, p.getMicro());
        assertEquals(0, p.getPreview());
        assertFalse (p.isPreview());
        assertEquals("6.7", p.toShortString());
        assertEquals("6.7.0", p.toString());

        p = new PreviewVersion(10, 11, 12, PreviewVersion.NOT_A_PREVIEW);
        assertEquals(10, p.getMajor());
        assertEquals(11, p.getMinor());
        assertEquals(12, p.getMicro());
        assertEquals(0, p.getPreview());
        assertFalse (p.isPreview());
        assertEquals("10.11.12", p.toShortString());
        assertEquals("10.11.12", p.toString());

        p = new PreviewVersion(10, 11, 12, 13);
        assertEquals(10, p.getMajor());
        assertEquals(11, p.getMinor());
        assertEquals(12, p.getMicro());
        assertEquals(13, p.getPreview());
        assertTrue  (p.isPreview());
        assertEquals("10.11.12 rc13", p.toShortString());
        assertEquals("10.11.12 rc13", p.toString());
    }

    public final void testCompareTo() {
        PreviewVersion s4 = new PreviewVersion(4);
        PreviewVersion i4 = new PreviewVersion(4);
        PreviewVersion g5 = new PreviewVersion(5, 1, 0, 6);
        PreviewVersion y5 = new PreviewVersion(5);
        PreviewVersion c5 = new PreviewVersion(5, 1, 0, 6);
        PreviewVersion o5 = new PreviewVersion(5, 0, 0, 7);
        PreviewVersion p5 = new PreviewVersion(5, 1, 0, 0);

        assertEquals(s4, i4);                   // 4.0.0-0 == 4.0.0-0
        assertEquals(g5, c5);                   // 5.1.0-6 == 5.1.0-6

        assertFalse(y5.equals(p5));             // 5.0.0-0 != 5.1.0-0
        assertFalse(g5.equals(p5));             // 5.1.0-6 != 5.1.0-0
        assertTrue (s4.compareTo(i4) == 0);     // 4.0.0-0 == 4.0.0-0
        assertTrue (s4.compareTo(y5)  < 0);     // 4.0.0-0  < 5.0.0-0
        assertTrue (y5.compareTo(y5) == 0);     // 5.0.0-0 == 5.0.0-0
        assertTrue (y5.compareTo(p5)  < 0);     // 5.0.0-0  < 5.1.0-0
        assertTrue (o5.compareTo(y5)  < 0);     // 5.0.0-7  < 5.0.0-0
        assertTrue (p5.compareTo(p5) == 0);     // 5.1.0-0 == 5.1.0-0
        assertTrue (c5.compareTo(p5)  < 0);     // 5.1.0-6  < 5.1.0-0
        assertTrue (p5.compareTo(c5)  > 0);     // 5.1.0-0  > 5.1.0-6
        assertTrue (p5.compareTo(o5)  > 0);     // 5.1.0-0  > 5.0.0-7
        assertTrue (c5.compareTo(o5)  > 0);     // 5.1.0-6  > 5.0.0-7
        assertTrue (o5.compareTo(o5) == 0);     // 5.0.0-7  > 5.0.0-7
    }

}
