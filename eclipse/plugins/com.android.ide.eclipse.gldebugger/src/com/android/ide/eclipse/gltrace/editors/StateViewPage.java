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

package com.android.ide.eclipse.gltrace.editors;

import com.android.ide.eclipse.gltrace.model.GLCall;
import com.android.ide.eclipse.gltrace.model.GLTrace;
import com.android.ide.eclipse.gltrace.state.GLState;
import com.android.ide.eclipse.gltrace.state.IGLProperty;
import com.android.ide.eclipse.gltrace.state.transforms.IStateTransform;

import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.part.IPageSite;
import org.eclipse.ui.part.Page;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A tree view of the OpenGL state. It listens to the current GLCall that is selected
 * in the Function Trace view, and updates its view to reflect the state as of the selected call.
 */
public class StateViewPage extends Page implements ISelectionListener {
    public static final String ID = "com.android.ide.eclipse.gltrace.views.GLState"; //$NON-NLS-1$

    private final GLTrace mTrace;
    private final List<GLCall> mGLCalls;

    /** OpenGL State as of call {@link #mCurrentStateIndex}. */
    private final IGLProperty mState;
    private int mCurrentStateIndex;

    private String[] TREE_PROPERTIES = { "Name", "Value" };
    private TreeViewer mTreeViewer;
    private StateLabelProvider mLabelProvider;

    public StateViewPage(GLTrace trace) {
        mTrace = trace;
        mGLCalls = trace.getGLCalls();

        mState = GLState.createDefaultState();
        mCurrentStateIndex = -1;
    }

    @Override
    public void createControl(Composite parent) {
        final Tree tree = new Tree(parent, SWT.VIRTUAL | SWT.H_SCROLL | SWT.V_SCROLL);
        tree.setHeaderVisible(true);
        tree.setLinesVisible(true);
        tree.setLayoutData(new GridData(GridData.FILL_BOTH));

        TreeColumn col1 = new TreeColumn(tree, SWT.LEFT);
        col1.setText(TREE_PROPERTIES[0]);
        col1.setWidth(200);

        TreeColumn col2 = new TreeColumn(tree, SWT.LEFT);
        col2.setText(TREE_PROPERTIES[1]);
        col2.setWidth(200);

        mTreeViewer = new TreeViewer(tree);
        mTreeViewer.setContentProvider(new StateContentProvider());
        mLabelProvider = new StateLabelProvider();
        mTreeViewer.setLabelProvider(mLabelProvider);
        mTreeViewer.setInput(mState);
        mTreeViewer.refresh();
    }

    @Override
    public void init(IPageSite pageSite) {
        super.init(pageSite);
        pageSite.getPage().addSelectionListener(this);
    }

    @Override
    public void dispose() {
        getSite().getPage().removeSelectionListener(this);
        super.dispose();
    }

    @Override
    public void selectionChanged(IWorkbenchPart part, ISelection selection) {
        if (!(part instanceof GLFunctionTraceViewer)) {
            return;
        }

        if (((GLFunctionTraceViewer) part).getTrace() != mTrace) {
            return;
        }

        if (!(selection instanceof IStructuredSelection)) {
            return;
        }

        GLCall selectedCall = null;

        IStructuredSelection ssel = (IStructuredSelection) selection;
        if (ssel.toList().size() > 0) {
            Object data = ssel.toList().get(0);
            if (data instanceof GLCall) {
                selectedCall = (GLCall) data;
            }
        }

        if (selectedCall == null) {
            return;
        }

        if (selectedCall.getIndex() != mCurrentStateIndex) {
            final Set<IGLProperty> changedProperties = updateState(mCurrentStateIndex,
                    selectedCall.getIndex());
            mCurrentStateIndex = selectedCall.getIndex();

            mLabelProvider.setChangedProperties(changedProperties);
            Display.getDefault().syncExec(new Runnable() {
                @Override
                public void run() {
                    if (!mTreeViewer.getTree().isDisposed()) {
                        mTreeViewer.refresh();
                    }
                }
            });
        }
    }

    @Override
    public Control getControl() {
        if (mTreeViewer == null) {
            return null;
        }

        return mTreeViewer.getControl();
    }

    @Override
    public void setFocus() {
    }

    /**
     * Update GL state from GL call at fromIndex to the call at toIndex.
     * If fromIndex < toIndex, the GL state will be updated by applying all the transformations
     * corresponding to calls from (fromIndex + 1) to toIndex (inclusive).
     * If fromIndex > toIndex, the GL state will be updated by reverting all the calls from
     * fromIndex (inclusive) to (toIndex + 1).
     * @return GL state properties that changed as a result of this update.
     */
    private Set<IGLProperty> updateState(int fromIndex, int toIndex) {
        assert fromIndex >= -1 && fromIndex < mGLCalls.size();
        assert toIndex >= 0 && toIndex < mGLCalls.size();

        if (fromIndex < toIndex) {
            return applyTransformations(fromIndex, toIndex);
        } else if (fromIndex > toIndex) {
            return revertTransformations(fromIndex, toIndex);
        } else {
            return Collections.emptySet();
        }
    }

    private Set<IGLProperty> applyTransformations(int fromIndex, int toIndex) {
        int setSizeHint = 3 * (toIndex - fromIndex) + 10;
        Set<IGLProperty> changedProperties = new HashSet<IGLProperty>(setSizeHint);

        for (int i = fromIndex + 1; i <= toIndex; i++) {
            for (IStateTransform f : mGLCalls.get(i).getStateTransformations()) {
                f.apply(mState);

                IGLProperty changedProperty = f.getChangedProperty(mState);
                if (changedProperty != null) {
                    changedProperties.addAll(getHierarchy(changedProperty));
                }
            }
        }

        return changedProperties;
    }

    private Set<IGLProperty> revertTransformations(int fromIndex, int toIndex) {
        int setSizeHint = 3 * (fromIndex - toIndex) + 10;
        Set<IGLProperty> changedProperties = new HashSet<IGLProperty>(setSizeHint);

        for (int i = fromIndex; i > toIndex; i--) {
            for (IStateTransform f : mGLCalls.get(i).getStateTransformations()) {
                f.revert(mState);

                IGLProperty changedProperty = f.getChangedProperty(mState);
                if (changedProperty != null) {
                    changedProperties.addAll(getHierarchy(changedProperty));
                }
            }
        }

        return changedProperties;
    }

    /**
     * Obtain the list of properties starting from the provided property up to
     * the root of GL state.
     */
    private List<IGLProperty> getHierarchy(IGLProperty changedProperty) {
        List<IGLProperty> changedProperties = new ArrayList<IGLProperty>(5);
        changedProperties.add(changedProperty);

        // add the entire parent chain until we reach the root
        IGLProperty prop = changedProperty;
        while ((prop = prop.getParent()) != null) {
            changedProperties.add(prop);
        }

        return changedProperties;
    }
}
