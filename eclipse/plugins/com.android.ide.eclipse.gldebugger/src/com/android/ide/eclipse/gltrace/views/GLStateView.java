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

import com.android.ide.eclipse.gltrace.editors.GLFunctionTraceViewer;
import com.android.ide.eclipse.gltrace.model.GLCall;
import com.android.ide.eclipse.gltrace.model.GLTrace;
import com.android.ide.eclipse.gltrace.state.IGLProperty;

import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.part.ViewPart;

import java.util.Set;

/**
 * A tree view of the OpenGL state. It listens to the current GLCall that is selected
 * in the Function Trace view, and updates its view to reflect the state as of the selected call.
 */
public class GLStateView extends ViewPart implements ISelectionListener {
    private TreeViewer mTreeViewer;

    private IGLProperty mState;
    private GLCall mCurrentGLCall;

    private Set<IGLProperty> mChangedProperties;
    private boolean mStateChanged;

    private String []TREE_PROPERTIES = { "Name", "Value" };

    public GLStateView() {
        mState = null;
        mCurrentGLCall = null;
    }

    @Override
    public void createPartControl(Composite parent) {
        Composite c = new Composite(parent, SWT.NONE);
        c.setLayout(new GridLayout(1, false));
        GridData gd = new GridData(GridData.FILL_BOTH);
        c.setLayoutData(gd);

        createStateTreeViewer(c);

        ISelectionService selectionService = getSite().getWorkbenchWindow().getSelectionService();
        selectionService.addPostSelectionListener(this);
    }

    private void createStateTreeViewer(Composite parent) {
        final Tree tree = new Tree(parent, SWT.VIRTUAL);
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
        mTreeViewer.setContentProvider(new GLStateContentProvider());
        mTreeViewer.setLabelProvider(new GLStateLabelProvider());
    }

    @Override
    public void dispose() {
        ISelectionService selectionService = getSite().getWorkbenchWindow().getSelectionService();
        selectionService.removePostSelectionListener(this);
        super.dispose();
    }

    @Override
    public void setFocus() {
    }

    public void selectionChanged(IWorkbenchPart part, ISelection selection) {
        if (!(part instanceof GLFunctionTraceViewer)) {
            return;
        }

        if (!(selection instanceof IStructuredSelection)) {
            return;
        }

        GLFunctionTraceViewer traceViewer = (GLFunctionTraceViewer) part;
        GLCall selectedCall = null;

        IStructuredSelection ssel = (IStructuredSelection) selection;
        if (ssel.toList().size() > 0) {
            Object data = ssel.toList().get(0);
            if (data instanceof GLCall) {
                selectedCall = (GLCall) data;
            }
        }

        GLTrace trace = traceViewer.getTrace();
        if (trace == null) {
            return;
        }

        if (selectedCall != mCurrentGLCall) {
            IGLProperty nextState = trace.getStateAt(selectedCall);
            if (nextState != mState) {
                mState = nextState;
                mStateChanged = true;
            } else {
                mStateChanged = false;
            }

            mChangedProperties = trace.getChangedProperties(mCurrentGLCall, selectedCall,
                    mState);

            refreshUI();
            mCurrentGLCall = selectedCall;
        }
    }

    private void refreshUI() {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                if (mTreeViewer == null) {
                    return;
                }

                if (mStateChanged) {
                    // if the state has changed, provide the new input and refresh the entire tree
                    mTreeViewer.setInput(mState);
                    mTreeViewer.refresh();
                } else {
                    if (mChangedProperties == null) {
                        // refresh the entire tree if we cannot figure out exactly which properties
                        // have changed
                        mTreeViewer.refresh();
                    } else {
                        // only refresh the nodes that have changed
                        mTreeViewer.update(mChangedProperties.toArray(), TREE_PROPERTIES);
                    }
                }
            }
        });
    }
}
