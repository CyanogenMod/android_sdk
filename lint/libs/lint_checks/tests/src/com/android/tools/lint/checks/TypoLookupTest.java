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

import java.util.Iterator;

@SuppressWarnings("javadoc")
public class TypoLookupTest extends AbstractCheckTest {
    private final TypoLookup mDb = TypoLookup.get(new TestLintClient());

    public void test1() {
        assertNull(mDb.getTypos("hello", 0, "hello".length()));
        assertNull(mDb.getTypos("this", 0, "this".length()));

        assertNotNull(mDb.getTypos("wiht", 0, "wiht".length()));
        assertNotNull(mDb.getTypos("woudl", 0, "woudl".length()));
        assertEquals("would", mDb.getTypos("woudl", 0, "woudl".length()).iterator().next());
        assertEquals("would", mDb.getTypos("  woudl  ", 2, 7).iterator().next());
        assertNotNull(mDb.getTypos("foo wiht bar", 4, 8));

        Iterator<String> typos = mDb.getTypos("throught", 0, "throught".length()).iterator();
        assertEquals("thought", typos.next());
        assertEquals("through", typos.next());
        assertEquals("throughout", typos.next());

        // Capitalization handling
        assertNotNull(mDb.getTypos("Woudl", 0, "Woudl".length()));
        assertNotNull(mDb.getTypos("Enlish", 0, "Enlish".length()));
        assertNull(mDb.getTypos("enlish", 0, "enlish".length()));
        assertNotNull(mDb.getTypos("ok", 0, "ok".length()));
        assertNotNull(mDb.getTypos("Ok", 0, "Ok".length()));
        assertNull(mDb.getTypos("OK", 0, "OK".length()));
    }

    @Override
    protected Detector getDetector() {
        fail("This is not used in the TypoLookupTest");
        return null;
    }
}
