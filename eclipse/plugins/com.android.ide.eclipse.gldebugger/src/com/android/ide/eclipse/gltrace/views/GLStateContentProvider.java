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

package com.android.ide.eclipse.gltrace.views;

import com.android.ide.eclipse.gltrace.state.GLCompositeProperty;
import com.android.ide.eclipse.gltrace.state.GLListProperty;
import com.android.ide.eclipse.gltrace.state.IGLProperty;

import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;

public class GLStateContentProvider implements ITreeContentProvider {
    public void dispose() {
    }

    public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
    }

    public Object[] getElements(Object inputElement) {
        return getChildren(inputElement);
    }

    public Object[] getChildren(Object parentElement) {
        if (parentElement instanceof GLListProperty) {
            return ((GLListProperty) parentElement).getList().toArray();
        }

        if (parentElement instanceof GLCompositeProperty) {
            return ((GLCompositeProperty) parentElement).getProperties().toArray();
        }

        return null;
    }

    public Object getParent(Object element) {
        if (element instanceof IGLProperty) {
            return ((IGLProperty) element).getParent();
        }

        return null;
    }

    public boolean hasChildren(Object element) {
        if (element instanceof IGLProperty) {
            return ((IGLProperty) element).isComposite();
        }

        return false;
    }
}
