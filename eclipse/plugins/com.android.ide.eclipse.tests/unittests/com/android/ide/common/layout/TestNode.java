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

import static com.android.util.XmlUtils.ANDROID_URI;
import static com.android.ide.common.layout.LayoutConstants.ATTR_ID;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.api.IAttributeInfo;
import com.android.ide.common.api.INode;
import com.android.ide.common.api.INodeHandler;
import com.android.ide.common.api.Margins;
import com.android.ide.common.api.Rect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Test/mock implementation of {@link INode} */
public class TestNode implements INode {
    private TestNode mParent;

    private final List<TestNode> mChildren = new ArrayList<TestNode>();

    private final String mFqcn;

    private Rect mBounds = new Rect(); // Invalid bounds initially

    private Map<String, IAttribute> mAttributes = new HashMap<String, IAttribute>();

    private Map<String, IAttributeInfo> mAttributeInfos = new HashMap<String, IAttributeInfo>();

    private List<String> mAttributeSources;

    public TestNode(String fqcn) {
        this.mFqcn = fqcn;
    }

    public TestNode bounds(Rect bounds) {
        this.mBounds = bounds;

        return this;
    }

    public TestNode id(String id) {
        return set(ANDROID_URI, ATTR_ID, id);
    }

    public TestNode set(String uri, String name, String value) {
        setAttribute(uri, name, value);

        return this;
    }

    public TestNode add(TestNode child) {
        mChildren.add(child);
        child.mParent = this;

        return this;
    }

    public TestNode add(TestNode... children) {
        for (TestNode child : children) {
            mChildren.add(child);
            child.mParent = this;
        }

        return this;
    }

    public static TestNode create(String fcqn) {
        return new TestNode(fcqn);
    }

    public void removeChild(int index) {
        TestNode removed = mChildren.remove(index);
        removed.mParent = null;
    }

    // ==== INODE ====

    @Override
    public @NonNull INode appendChild(@NonNull String viewFqcn) {
        return insertChildAt(viewFqcn, mChildren.size());
    }

    @Override
    public void editXml(@NonNull String undoName, @NonNull INodeHandler callback) {
        callback.handle(this);
    }

    public void putAttributeInfo(String uri, String attrName, IAttributeInfo info) {
        mAttributeInfos.put(uri + attrName, info);
    }

    @Override
    public IAttributeInfo getAttributeInfo(@Nullable String uri, @NonNull String attrName) {
        return mAttributeInfos.get(uri + attrName);
    }

    @Override
    public @NonNull Rect getBounds() {
        return mBounds;
    }

    @Override
    public @NonNull INode[] getChildren() {
        return mChildren.toArray(new INode[mChildren.size()]);
    }

    @Override
    public @NonNull IAttributeInfo[] getDeclaredAttributes() {
        return mAttributeInfos.values().toArray(new IAttributeInfo[mAttributeInfos.size()]);
    }

    @Override
    public @NonNull String getFqcn() {
        return mFqcn;
    }

    @Override
    public @NonNull IAttribute[] getLiveAttributes() {
        return mAttributes.values().toArray(new IAttribute[mAttributes.size()]);
    }

    @Override
    public INode getParent() {
        return mParent;
    }

    @Override
    public INode getRoot() {
        TestNode curr = this;
        while (curr.mParent != null) {
            curr = curr.mParent;
        }

        return curr;
    }

    @Override
    public String getStringAttr(@Nullable String uri, @NonNull String attrName) {
        IAttribute attr = mAttributes.get(uri + attrName);
        if (attr == null) {
            return null;
        }

        return attr.getValue();
    }

    @Override
    public @NonNull INode insertChildAt(@NonNull String viewFqcn, int index) {
        TestNode child = new TestNode(viewFqcn);
        if (index == -1) {
            mChildren.add(child);
        } else {
            mChildren.add(index, child);
        }
        child.mParent = this;
        return child;
    }

    @Override
    public void removeChild(@NonNull INode node) {
        int index = mChildren.indexOf(node);
        if (index != -1) {
            removeChild(index);
        }
    }

    @Override
    public boolean setAttribute(@Nullable String uri, @NonNull String localName,
            @Nullable String value) {
        mAttributes.put(uri + localName, new TestAttribute(uri, localName, value));
        return true;
    }

    @Override
    public String toString() {
        return "TestNode [fqn=" + mFqcn + ", infos=" + mAttributeInfos
                + ", attributes=" + mAttributes + ", bounds=" + mBounds + "]";
    }

    @Override
    public int getBaseline() {
        return -1;
    }

    @Override
    public @NonNull Margins getMargins() {
        return null;
    }

    @Override
    public @NonNull List<String> getAttributeSources() {
        return mAttributeSources != null ? mAttributeSources : Collections.<String>emptyList();
    }

    public void setAttributeSources(List<String> attributeSources) {
        mAttributeSources = attributeSources;
    }
}
