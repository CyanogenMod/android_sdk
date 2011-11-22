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

package com.android.ide.eclipse.gltrace.state;

/** Properties that hold boolean values. */
public final class GLBooleanProperty extends GLAbstractAtomicProperty implements IGLProperty {
    private final Boolean mDefaultValue;
    private Boolean mCurrentValue;

    public GLBooleanProperty(GLStateType name, Boolean defaultValue) {
        super(name);

        mDefaultValue = mCurrentValue = defaultValue;
    }

    public boolean isDefault() {
        return mDefaultValue == mCurrentValue;
    }

    public void setValue(Boolean newValue) {
        mCurrentValue = newValue;
    }

    public String getStringValue() {
        return mCurrentValue.toString();
    }

    @Override
    public String toString() {
        return getType() + "=" + getStringValue(); //$NON-NLS-1$
    }

    public void setValue(Object value) {
        if (value instanceof Boolean) {
            mCurrentValue = (Boolean) value;
        } else {
            throw new IllegalArgumentException("Attempt to set non-boolean value for " //$NON-NLS-1$
                    + getType());
        }
    }

    public Object getValue() {
        return mCurrentValue;
    }
}
