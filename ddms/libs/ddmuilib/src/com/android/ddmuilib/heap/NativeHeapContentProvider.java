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

package com.android.ddmuilib.heap;

import org.eclipse.jface.viewers.ILazyTreeContentProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;

import java.util.List;

/**
 * Content Provider for the native heap tree viewer in {@link NativeHeapPanel}.
 * It expects a list of {@link NativeAllocationInfo} objects as input.
 */
public final class NativeHeapContentProvider implements ILazyTreeContentProvider {
    private TreeViewer mViewer;
    private List<?> mCurrentAllocations;

    public NativeHeapContentProvider(TreeViewer viewer) {
        mViewer = viewer;
    }

    public void dispose() {
    }

    public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
        mCurrentAllocations = (List <?>) newInput;
    }

    public Object getParent(Object arg0) {
        return null;
    }

    public void updateChildCount(Object element, int currentChildCount) {
        int childCount = 0;

        if (element == mCurrentAllocations) { // root element
            childCount = mCurrentAllocations.size();
        }

        mViewer.setChildCount(element, childCount);
    }

    public void updateElement(Object parent, int index) {
        Object item = null;

        if (parent == mCurrentAllocations) { // root element
            item = mCurrentAllocations.get(index);
        }

        mViewer.replace(parent, index, item);
        mViewer.setChildCount(item, 0);
    }
}
