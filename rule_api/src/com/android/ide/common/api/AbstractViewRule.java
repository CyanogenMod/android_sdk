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

import java.util.List;

/**
 * Default implementation of an {@link IViewRule}. This is a convenience
 * implementation which makes it easier to supply designtime behavior for a
 * custom view and just override the methods you are interested in.
 */
public class AbstractViewRule implements IViewRule {
    public boolean onInitialize(String fqcn, IClientRulesEngine engine) {
        return true;
    }

    public void onDispose() {
    }

    public String getDisplayName() {
        // Default is to not override the selection display name.
        return null;
    }

    // ==== Selection ====

    public List<String> getSelectionHint(INode parentNode, INode childNode) {
        return null;
    }

    public void addLayoutActions(List<RuleAction> actions, INode parentNode,
            List<? extends INode> children) {
    }

    public void addContextMenuActions(List<RuleAction> actions, INode node) {
    }

    public void paintSelectionFeedback(IGraphics graphics, INode parentNode,
            List<? extends INode> childNodes, Object view) {
    }

    // ==== Drag & drop support ====

    // By default Views do not accept drag'n'drop.
    public DropFeedback onDropEnter(INode targetNode, Object targetView, IDragElement[] elements) {
        return null;
    }

    public DropFeedback onDropMove(INode targetNode, IDragElement[] elements,
            DropFeedback feedback, Point p) {
        return null;
    }

    public void onDropLeave(INode targetNode, IDragElement[] elements, DropFeedback feedback) {
        // ignore
    }

    public void onDropped(
            INode targetNode,
            IDragElement[] elements,
            DropFeedback feedback,
            Point p) {
        // ignore
    }


    public void onPaste(INode targetNode, Object targetView, IDragElement[] pastedElements) {
    }

    // ==== Create/Remove hooks ====

    public void onCreate(INode node, INode parent, InsertType insertType) {
    }

    public void onChildInserted(INode child, INode parent, InsertType insertType) {
    }

    public void onRemovingChildren(List<INode> deleted, INode parent) {
    }

    // ==== Resizing ====

    public DropFeedback onResizeBegin(INode child, INode parent, SegmentType horizontalEdge,
            SegmentType verticalEdge, Object childView, Object parentView) {
        return null;
    }

    public void onResizeUpdate(DropFeedback feedback, INode child, INode parent, Rect newBounds,
            int modifierMask) {
    }

    public void onResizeEnd(DropFeedback feedback, INode child, final INode parent,
            final Rect newBounds) {
    }
}
