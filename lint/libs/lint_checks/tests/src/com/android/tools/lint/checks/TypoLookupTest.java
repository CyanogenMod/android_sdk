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

package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Detector;
import com.google.common.base.Charsets;

import java.util.Iterator;

@SuppressWarnings("javadoc")
public class TypoLookupTest extends AbstractCheckTest {

    public void test1() {
        TypoLookup db = TypoLookup.get(new TestLintClient(), "en");
        assertNull(db.getTypos("hello", 0, "hello".length()));
        assertNull(db.getTypos("this", 0, "this".length()));

        assertNotNull(db.getTypos("wiht", 0, "wiht".length()));
        assertNotNull(db.getTypos("woudl", 0, "woudl".length()));
        assertEquals("would", db.getTypos("woudl", 0, "woudl".length()).iterator().next());
        assertEquals("would", db.getTypos("  woudl  ", 2, 7).iterator().next());
        assertNotNull(db.getTypos("foo wiht bar", 4, 8));

        Iterator<String> typos = db.getTypos("throught", 0, "throught".length()).iterator();
        assertEquals("thought", typos.next());
        assertEquals("through", typos.next());
        assertEquals("throughout", typos.next());

        // Capitalization handling
        assertNotNull(db.getTypos("Woudl", 0, "Woudl".length()));
        assertNotNull(db.getTypos("Enlish", 0, "Enlish".length()));
        assertNull(db.getTypos("enlish", 0, "enlish".length()));
        assertNotNull(db.getTypos("ok", 0, "ok".length()));
        assertNotNull(db.getTypos("Ok", 0, "Ok".length()));
        assertNull(db.getTypos("OK", 0, "OK".length()));
    }

    public void test2() {
        TypoLookup db = TypoLookup.get(new TestLintClient(), "nb"); //$NON-NLS-1$
        assertNull(db.getTypos("hello", 0, "hello".length()));
        assertNull(db.getTypos("this", 0, "this".length()));

        assertNotNull(db.getTypos("altid", 0, "altid".length()));
        assertEquals("alltid", db.getTypos("altid", 0, "altid".length()).iterator().next());
        assertEquals("alltid", db.getTypos("  altid  ", 2, 7).iterator().next());
        assertNotNull(db.getTypos("foo altid bar", 4, 9));

        // Test utf-8 string which isn't ASCII
        String s = "karriære";
        byte[] sb = s.getBytes(Charsets.UTF_8);
        assertNotNull(db.getTypos(sb, 0, sb.length));

        assertEquals("karrière", db.getTypos(sb, 0, sb.length).iterator().next());
    }

    @Override
    protected Detector getDetector() {
        fail("This is not used in the TypoLookupTest");
        return null;
    }
}
