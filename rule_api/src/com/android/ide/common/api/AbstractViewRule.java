/*
 * Copyright (C) 2011 The Android Open Source Project
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
package com.android.ide.common.api;

import com.android.annotations.Nullable;
import com.google.common.annotations.Beta;

import java.util.List;

/**
 * Default implementation of an {@link IViewRule}. This is a convenience
 * implementation which makes it easier to supply designtime behavior for a
 * custom view and just override the methods you are interested in.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
@Beta
public class AbstractViewRule implements IViewRule {
    @Override
    public boolean onInitialize(String fqcn, IClientRulesEngine engine) {
        return true;
    }

    @Override
    public void onDispose() {
    }

    @Override
    @Nullable
    public String getDisplayName() {
        // Default is to not override the selection display name.
        return null;
    }

    // ==== Selection ====

    @Override
    @Nullable
    public List<String> getSelectionHint(INode parentNode, INode childNode) {
        return null;
    }

    @Override
    public void addLayoutActions(List<RuleAction> actions, INode parentNode,
            List<? extends INode> children) {
    }

    @Override
    public void addContextMenuActions(List<RuleAction> actions, INode node) {
    }

    @Override
    public void paintSelectionFeedback(IGraphics graphics, INode parentNode,
            List<? extends INode> childNodes, Object view) {
    }

    // ==== Drag & drop support ====

    // By default Views do not accept drag'n'drop.
    @Override
    @Nullable
    public DropFeedback onDropEnter(INode targetNode, Object targetView, IDragElement[] elements) {
        return null;
    }

    @Override
    @Nullable
    public DropFeedback onDropMove(INode targetNode, IDragElement[] elements,
            DropFeedback feedback, Point p) {
        return null;
    }

    @Override
    public void onDropLeave(INode targetNode, IDragElement[] elements, DropFeedback feedback) {
        // ignore
    }

    @Override
    public void onDropped(
            INode targetNode,
            IDragElement[] elements,
            DropFeedback feedback,
            Point p) {
        // ignore
    }


    @Override
    public void onPaste(INode targetNode, Object targetView, IDragElement[] pastedElements) {
    }

    // ==== Create/Remove hooks ====

    @Override
    public void onCreate(INode node, INode parent, InsertType insertType) {
    }

    @Override
    public void onChildInserted(INode child, INode parent, InsertType insertType) {
    }

    @Override
    public void onRemovingChildren(List<INode> deleted, INode parent) {
    }

    // ==== Resizing ====

    @Override
    @Nullable
    public DropFeedback onResizeBegin(INode child, INode parent, SegmentType horizontalEdge,
            SegmentType verticalEdge, Object childView, Object parentView) {
        return null;
    }

    @Override
    public void onResizeUpdate(DropFeedback feedback, INode child, INode parent, Rect newBounds,
            int modifierMask) {
    }

    @Override
    public void onResizeEnd(DropFeedback feedback, INode child, final INode parent,
            final Rect newBounds) {
    }
}
