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

package com.android.ide.eclipse.gltrace.state;

import com.android.sdklib.util.SparseArray;

import java.util.ArrayList;
import java.util.List;

public class GLSparseArrayProperty implements IGLProperty {
    private final GLStateType mType;
    private IGLProperty mDefaultValue;
    private IGLProperty mParent;
    private final SparseArray<IGLProperty> mSparseArray;

    public GLSparseArrayProperty(GLStateType type, IGLProperty defaultValue) {
        mType = type;
        mDefaultValue = defaultValue;
        mSparseArray = new SparseArray<IGLProperty>(20);
    }

    private GLSparseArrayProperty(GLStateType type, IGLProperty defaultValue,
            SparseArray<IGLProperty> contents) {
        mType = type;
        mDefaultValue = defaultValue;
        mSparseArray = contents;
    }

    public List<IGLProperty> getValues() {
        List<IGLProperty> values = new ArrayList<IGLProperty>(mSparseArray.size());

        for (int i = 0; i < mSparseArray.size(); i++) {
            values.add(mSparseArray.valueAt(i));
        }

        return values;
    }

    public IGLProperty getProperty(int key) {
        return mSparseArray.get(key);
    }

    public int keyFor(IGLProperty element) {
        int index = mSparseArray.indexOfValue(element);
        return mSparseArray.keyAt(index);
    }

    public void put(int key, IGLProperty element) {
        element.setParent(this);
        mSparseArray.put(key, element);
    }

    public void add(int key) {
        IGLProperty prop = mDefaultValue.clone();
        prop.setParent(this);
        mSparseArray.put(key, prop);
    }

    public void delete(int key) {
        mSparseArray.delete(key);
    }

    @Override
    public GLStateType getType() {
        return mType;
    }

    @Override
    public boolean isComposite() {
        return true;
    }

    @Override
    public boolean isDefault() {
        return false;
    }

    @Override
    public IGLProperty getParent() {
        return mParent;
    }

    @Override
    public void setParent(IGLProperty parent) {
        mParent = parent;
    }

    @Override
    public GLSparseArrayProperty clone() {
        SparseArray<IGLProperty> copy = new SparseArray<IGLProperty>(mSparseArray.size());
        for (int i = 0; i < mSparseArray.size(); i++) {
            int key = mSparseArray.keyAt(i);
            IGLProperty value = mSparseArray.get(key);
            copy.put(key, value);
        }

        return new GLSparseArrayProperty(mType, mDefaultValue, copy);
    }

    @Override
    public String getStringValue() {
        // This method is called for displaying objects in the UI.
        // We do not display any values for composites in the UI as they are only intermediate
        // nodes in the tree.
        return "";
    }

    @Override
    public void setValue(Object value) {
        throw new UnsupportedOperationException(
                "Values cannot be set for composite properties."); //$NON-NLS-1$
    }

    @Override
    public Object getValue() {
        throw new UnsupportedOperationException(
                "Values cannot be obtained for composite properties."); //$NON-NLS-1$
    }
}
