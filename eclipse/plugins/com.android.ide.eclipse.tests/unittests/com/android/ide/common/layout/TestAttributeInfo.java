/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.android.ide.common.layout;

import com.android.ide.common.api.IAttributeInfo;

/** Test/mock implementation of {@link IAttributeInfo} */
public class TestAttributeInfo implements IAttributeInfo {
    private final String mName;
    private final Format[] mFormats;
    private final String mDefinedBy;
    private final String[] mEnumValues;
    private final String[] mFlagValues;
    private final String mJavadoc;

    public TestAttributeInfo(String name) {
        this(name, null, null, null, null, null);
    }

    public TestAttributeInfo(String name, Format[] formats, String definedBy,
            String[] enumValues, String[] flagValues, String javadoc) {
        super();
        this.mName = name;
        this.mFormats = formats;
        this.mDefinedBy = definedBy;
        this.mEnumValues = enumValues;
        this.mFlagValues = flagValues;
        this.mJavadoc = javadoc;
    }

    public String getDeprecatedDoc() {
        return null;
    }

    public String[] getEnumValues() {
        return mEnumValues;
    }

    public String[] getFlagValues() {
        return mFlagValues;
    }

    public Format[] getFormats() {
        return mFormats;
    }

    public String getJavaDoc() {
        return mJavadoc;
    }

    public String getName() {
        return mName;
    }

    public String getDefinedBy() {
        return mDefinedBy;
    }
}