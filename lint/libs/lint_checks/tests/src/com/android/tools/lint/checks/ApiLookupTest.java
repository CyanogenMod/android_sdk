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

@SuppressWarnings("javadoc")
public class ApiLookupTest extends AbstractCheckTest {
    private final ApiLookup mDb = ApiLookup.get(new TestLintClient());

    public void test1() {
        assertEquals(5, mDb.getFieldVersion("android/Manifest$permission", "AUTHENTICATE_ACCOUNTS"));
        assertTrue(mDb.getFieldVersion("android/R$attr", "absListViewStyle") <= 1);
        assertEquals(11, mDb.getFieldVersion("android/R$attr", "actionMenuTextAppearance"));
        assertEquals(5, mDb.getCallVersion("android/graphics/drawable/BitmapDrawable",
                "<init>", "(Landroid/content/res/Resources;Ljava/lang/String;)V"));
        assertEquals(4, mDb.getCallVersion("android/graphics/drawable/BitmapDrawable",
                "setTargetDensity", "(Landroid/util/DisplayMetrics;)V"));
        assertEquals(7, mDb.getClassVersion("android/app/WallpaperInfo"));
        assertEquals(11, mDb.getClassVersion("android/widget/StackView"));
        assertTrue(mDb.getClassVersion("ava/text/ChoiceFormat") <= 1);

        // Class lookup: Unknown class
        assertEquals(-1, mDb.getClassVersion("foo/Bar"));
        // Field lookup: Unknown class
        assertEquals(-1, mDb.getFieldVersion("foo/Bar", "FOOBAR"));
        // Field lookup: Unknown field
        assertEquals(-1, mDb.getFieldVersion("android/Manifest$permission", "FOOBAR"));
        // Method lookup: Unknown class
        assertEquals(-1, mDb.getCallVersion("foo/Bar",
                "<init>", "(Landroid/content/res/Resources;Ljava/lang/String;)V"));
        // Method lookup: Unknown name
        assertEquals(-1, mDb.getCallVersion("android/graphics/drawable/BitmapDrawable",
                "foo", "(Landroid/content/res/Resources;Ljava/lang/String;)V"));
        // Method lookup: Unknown argument list
        assertEquals(-1, mDb.getCallVersion("android/graphics/drawable/BitmapDrawable",
                "<init>", "(I)V"));
    }

    public void test2() {
        // Regression test:
        // This used to return 11 because of some wildcard syntax in the signature
        assertTrue(mDb.getCallVersion("java/lang/Object", "getClass", "()") <= 1);
    }

    public void testIssue26467() {
        assertTrue(mDb.getCallVersion("java/nio/ByteBuffer", "array", "()") <= 1);
        assertEquals(9, mDb.getCallVersion("java/nio/Buffer", "array", "()"));
    }

    @Override
    protected Detector getDetector() {
        fail("This is not used in the ApiDatabase test");
        return null;
    }
}
