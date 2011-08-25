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

import static com.android.ide.common.layout.LayoutConstants.ANDROID_URI;
import static com.android.ide.common.layout.LayoutConstants.ATTR_GRAVITY;
import static com.android.ide.common.layout.LayoutConstants.ATTR_ID;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_ABOVE;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_ALIGN_BASELINE;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_ALIGN_BOTTOM;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_ALIGN_LEFT;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_ALIGN_PARENT_BOTTOM;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_ALIGN_PARENT_LEFT;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_ALIGN_PARENT_RIGHT;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_ALIGN_PARENT_TOP;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_ALIGN_RIGHT;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_ALIGN_TOP;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_ALIGN_WITH_PARENT_MISSING;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_BELOW;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_CENTER_HORIZONTAL;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_CENTER_IN_PARENT;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_CENTER_VERTICAL;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_PREFIX;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_TO_LEFT_OF;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_TO_RIGHT_OF;
import static com.android.ide.common.layout.LayoutConstants.ID_PREFIX;
import static com.android.ide.common.layout.LayoutConstants.NEW_ID_PREFIX;
import static com.android.ide.common.layout.LayoutConstants.VALUE_TRUE;

import com.android.ide.common.api.DropFeedback;
import com.android.ide.common.api.IDragElement;
import com.android.ide.common.api.IGraphics;
import com.android.ide.common.api.IMenuCallback;
import com.android.ide.common.api.INode;
import com.android.ide.common.api.INode.IAttribute;
import com.android.ide.common.api.INodeHandler;
import com.android.ide.common.api.IViewRule;
import com.android.ide.common.api.InsertType;
import com.android.ide.common.api.Point;
import com.android.ide.common.api.Rect;
import com.android.ide.common.api.RuleAction;
import com.android.ide.common.api.SegmentType;
import com.android.ide.common.layout.relative.ConstraintPainter;
import com.android.ide.common.layout.relative.GuidelinePainter;
import com.android.ide.common.layout.relative.MoveHandler;
import com.android.ide.common.layout.relative.ResizeHandler;
import com.android.util.Pair;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An {@link IViewRule} for android.widget.RelativeLayout and all its derived
 * classes.
 */
public class RelativeLayoutRule extends BaseLayoutRule {
    private static final String ACTION_SHOW_STRUCTURE = "_structure"; //$NON-NLS-1$
    private static final String ACTION_SHOW_CONSTRAINTS = "_constraints"; //$NON-NLS-1$
    private static final String ACTION_CENTER_VERTICAL = "_centerVert"; //$NON-NLS-1$
    private static final String ACTION_CENTER_HORIZONTAL = "_centerHoriz"; //$NON-NLS-1$
    private static final URL ICON_CENTER_VERTICALLY =
        RelativeLayoutRule.class.getResource("centerVertically.png"); //$NON-NLS-1$
    private static final URL ICON_CENTER_HORIZONTALLY =
        RelativeLayoutRule.class.getResource("centerHorizontally.png"); //$NON-NLS-1$
    private static final URL ICON_SHOW_STRUCTURE =
        BaseLayoutRule.class.getResource("structure.png"); //$NON-NLS-1$
    private static final URL ICON_SHOW_CONSTRAINTS =
        BaseLayoutRule.class.getResource("constraints.png"); //$NON-NLS-1$

    public static boolean sShowStructure = false;
    public static boolean sShowConstraints = true;

    // ==== Selection ====

    @Override
    public List<String> getSelectionHint(INode parentNode, INode childNode) {
        List<String> infos = new ArrayList<String>(18);
        addAttr(ATTR_LAYOUT_ABOVE, childNode, infos);
        addAttr(ATTR_LAYOUT_BELOW, childNode, infos);
        addAttr(ATTR_LAYOUT_TO_LEFT_OF, childNode, infos);
        addAttr(ATTR_LAYOUT_TO_RIGHT_OF, childNode, infos);
        addAttr(ATTR_LAYOUT_ALIGN_BASELINE, childNode, infos);
        addAttr(ATTR_LAYOUT_ALIGN_TOP, childNode, infos);
        addAttr(ATTR_LAYOUT_ALIGN_BOTTOM, childNode, infos);
        addAttr(ATTR_LAYOUT_ALIGN_LEFT, childNode, infos);
        addAttr(ATTR_LAYOUT_ALIGN_RIGHT, childNode, infos);
        addAttr(ATTR_LAYOUT_ALIGN_PARENT_TOP, childNode, infos);
        addAttr(ATTR_LAYOUT_ALIGN_PARENT_BOTTOM, childNode, infos);
        addAttr(ATTR_LAYOUT_ALIGN_PARENT_LEFT, childNode, infos);
        addAttr(ATTR_LAYOUT_ALIGN_PARENT_RIGHT, childNode, infos);
        addAttr(ATTR_LAYOUT_ALIGN_WITH_PARENT_MISSING, childNode, infos);
        addAttr(ATTR_LAYOUT_CENTER_HORIZONTAL, childNode, infos);
        addAttr(ATTR_LAYOUT_CENTER_IN_PARENT, childNode, infos);
        addAttr(ATTR_LAYOUT_CENTER_VERTICAL, childNode, infos);

        return infos;
    }

    private void addAttr(String propertyName, INode childNode, List<String> infos) {
        String a = childNode.getStringAttr(ANDROID_URI, propertyName);
        if (a != null && a.length() > 0) {
            // Display the layout parameters without the leading layout_ prefix
            // and id references without the @+id/ prefix
            if (propertyName.startsWith(ATTR_LAYOUT_PREFIX)) {
                propertyName = propertyName.substring(ATTR_LAYOUT_PREFIX.length());
            }
            a = stripIdPrefix(a);
            String s = propertyName + ": " + a;
            infos.add(s);
        }
    }

    @Override
    public void paintSelectionFeedback(IGraphics graphics, INode parentNode,
            List<? extends INode> childNodes, Object view) {
        super.paintSelectionFeedback(graphics, parentNode, childNodes, view);

        boolean showDependents = true;
        if (sShowStructure) {
            childNodes = Arrays.asList(parentNode.getChildren());
            // Avoid painting twice - both as incoming and outgoing
            showDependents = false;
        } else if (!sShowConstraints) {
            return;
        }

        ConstraintPainter.paintSelectionFeedback(graphics, parentNode, childNodes, showDependents);
    }

    // ==== Drag'n'drop support ====

    @Override
    public DropFeedback onDropEnter(INode targetNode, Object targetView, IDragElement[] elements) {
        return new DropFeedback(new MoveHandler(targetNode, elements, mRulesEngine),
                new GuidelinePainter());
    }

    @Override
    public DropFeedback onDropMove(INode targetNode, IDragElement[] elements,
            DropFeedback feedback, Point p) {
        if (elements == null || elements.length == 0) {
            return null;
        }

        MoveHandler state = (MoveHandler) feedback.userData;
        int offsetX = p.x + (feedback.dragBounds != null ? feedback.dragBounds.x : 0);
        int offsetY = p.y + (feedback.dragBounds != null ? feedback.dragBounds.y : 0);
        state.updateMove(feedback, elements, offsetX, offsetY, feedback.modifierMask);

        // Or maybe only do this if the results changed...
        feedback.requestPaint = true;

        return feedback;
    }

    @Override
    public void onDropLeave(INode targetNode, IDragElement[] elements, DropFeedback feedback) {
    }

    @Override
    public void onDropped(final INode targetNode, final IDragElement[] elements,
            final DropFeedback feedback, final Point p) {
        final MoveHandler state = (MoveHandler) feedback.userData;

        final Map<String, Pair<String, String>> idMap = getDropIdMap(targetNode, elements,
                feedback.isCopy || !feedback.sameCanvas);

        targetNode.editXml("Dropped", new INodeHandler() {
            public void handle(INode n) {
                int index = -1;

                // Remove cycles
                state.removeCycles();

                // Now write the new elements.
                INode previous = null;
                for (IDragElement element : elements) {
                    String fqcn = element.getFqcn();

                    // index==-1 means to insert at the end.
                    // Otherwise increment the insertion position.
                    if (index >= 0) {
                        index++;
                    }

                    INode newChild = targetNode.insertChildAt(fqcn, index);

                    // Copy all the attributes, modifying them as needed.
                    addAttributes(newChild, element, idMap, BaseLayoutRule.DEFAULT_ATTR_FILTER);
                    addInnerElements(newChild, element, idMap);

                    if (previous == null) {
                        state.applyConstraints(newChild);
                        previous = newChild;
                    } else {
                        // Arrange the nodes next to each other, depending on which
                        // edge we are attaching to. For example, if attaching to the
                        // top edge, arrange the subsequent nodes in a column below it.
                        //
                        // TODO: Try to do something smarter here where we detect
                        // constraints between the dragged edges, and we preserve these.
                        // We have to do this carefully though because if the
                        // constraints go through some other nodes not part of the
                        // selection, this doesn't work right, and you might be
                        // dragging several connected components, which we'd then
                        // need to stitch together such that they are all visible.

                        state.attachPrevious(previous, newChild);
                        previous = newChild;
                    }
                }
            }
        });
    }

    @Override
    public void onChildInserted(INode node, INode parent, InsertType insertType) {
        // TODO: Handle more generically some way to ensure that widgets with no
        // intrinsic size get some minimum size until they are attached on multiple
        // opposing sides.
        //String fqcn = node.getFqcn();
        //if (fqcn.equals(FQCN_EDIT_TEXT)) {
        //    node.setAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH, "100dp"); //$NON-NLS-1$
        //}
    }

    @Override
    public void onRemovingChildren(List<INode> deleted, INode parent) {
        super.onRemovingChildren(deleted, parent);

        // Remove any attachments pointing to the deleted nodes.

        // Produce set of attribute values that we want to delete if
        // present in a layout attribute
        Set<String> removeValues = new HashSet<String>(deleted.size() * 2);
        for (INode node : deleted) {
            String id = node.getStringAttr(ANDROID_URI, ATTR_ID);
            if (id != null) {
                removeValues.add(id);
                if (id.startsWith(NEW_ID_PREFIX)) {
                    removeValues.add(ID_PREFIX + stripIdPrefix(id));
                } else {
                    removeValues.add(NEW_ID_PREFIX + stripIdPrefix(id));
                }
            }
        }

        for (INode child : parent.getChildren()) {
            if (deleted.contains(child)) {
                continue;
            }
            for (IAttribute attribute : child.getLiveAttributes()) {
                if (attribute.getName().startsWith(ATTR_LAYOUT_PREFIX) &&
                        ANDROID_URI.equals(attribute.getUri())) {
                    String value = attribute.getValue();
                    if (removeValues.contains(value)) {
                        // Unset this reference to a deleted widget.
                        child.setAttribute(ANDROID_URI, attribute.getName(), null);
                    }
                }
            }
        }
    }

    // ==== Resize Support ====

    @Override
    public DropFeedback onResizeBegin(INode child, INode parent,
            SegmentType horizontalEdgeType, SegmentType verticalEdgeType,
            Object childView, Object parentView) {
        ResizeHandler state = new ResizeHandler(parent, child, mRulesEngine,
                horizontalEdgeType, verticalEdgeType);
        return new DropFeedback(state, new GuidelinePainter());
    }

    @Override
    public void onResizeUpdate(DropFeedback feedback, INode child, INode parent, Rect newBounds,
            int modifierMask) {
        ResizeHandler state = (ResizeHandler) feedback.userData;
        state.updateResize(feedback, child, newBounds, modifierMask);
    }

    @Override
    public void onResizeEnd(DropFeedback feedback, INode child, INode parent,
            final Rect newBounds) {
        final ResizeHandler state = (ResizeHandler) feedback.userData;

        child.editXml("Resize", new INodeHandler() {
            public void handle(INode n) {
                state.removeCycles();
                state.applyConstraints(n);
            }
        });
    }

    // ==== Layout Actions Bar ====

    @Override
    public void addLayoutActions(List<RuleAction> actions, final INode parentNode,
            final List<? extends INode> children) {
        super.addLayoutActions(actions, parentNode, children);

        actions.add(createGravityAction(Collections.<INode>singletonList(parentNode),
                ATTR_GRAVITY));
        actions.add(RuleAction.createSeparator(25));
        actions.add(createMarginAction(parentNode, children));

        IMenuCallback callback = new IMenuCallback() {
            public void action(RuleAction action, List<? extends INode> selectedNodes,
                    final String valueId, final Boolean newValue) {
                final String id = action.getId();
                if (id.equals(ACTION_CENTER_VERTICAL)|| id.equals(ACTION_CENTER_HORIZONTAL)) {
                    parentNode.editXml("Center", new INodeHandler() {
                        public void handle(INode n) {
                            if (id.equals(ACTION_CENTER_VERTICAL)) {
                                for (INode child : children) {
                                    centerVertically(child);
                                }
                            } else if (id.equals(ACTION_CENTER_HORIZONTAL)) {
                                for (INode child : children) {
                                    centerHorizontally(child);
                                }
                            }
                            mRulesEngine.redraw();
                        }

                    });
                } else if (id.equals(ACTION_SHOW_CONSTRAINTS)) {
                    sShowConstraints = !sShowConstraints;
                    mRulesEngine.redraw();
                } else {
                    assert id.equals(ACTION_SHOW_STRUCTURE);
                    sShowStructure = !sShowStructure;
                    mRulesEngine.redraw();
                }
            }
        };

        // Centering actions
        if (children != null && children.size() > 0) {
                        actions.add(RuleAction.createSeparator(150));
            actions.add(RuleAction.createAction(ACTION_CENTER_VERTICAL, "Center Vertically",
                    callback, ICON_CENTER_VERTICALLY, 160, false));
            actions.add(RuleAction.createAction(ACTION_CENTER_HORIZONTAL, "Center Horizontally",
                    callback, ICON_CENTER_HORIZONTALLY, 170, false));
        }

        actions.add(RuleAction.createSeparator(80));
        actions.add(RuleAction.createToggle(ACTION_SHOW_CONSTRAINTS, "Show Constraints",
                sShowConstraints, callback, ICON_SHOW_CONSTRAINTS, 180, false));
        actions.add(RuleAction.createToggle(ACTION_SHOW_STRUCTURE, "Show All Relationships",
                sShowStructure, callback, ICON_SHOW_STRUCTURE, 190, false));
    }

    private void centerHorizontally(INode node) {
        // Clear horizontal-oriented attributes from the node
        node.setAttribute(ANDROID_URI, ATTR_LAYOUT_ALIGN_PARENT_LEFT, null);
        node.setAttribute(ANDROID_URI, ATTR_LAYOUT_ALIGN_LEFT, null);
        node.setAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF, null);
        node.setAttribute(ANDROID_URI, ATTR_LAYOUT_CENTER_HORIZONTAL, null);
        node.setAttribute(ANDROID_URI, ATTR_LAYOUT_ALIGN_PARENT_RIGHT, null);
        node.setAttribute(ANDROID_URI, ATTR_LAYOUT_ALIGN_RIGHT, null);
        node.setAttribute(ANDROID_URI, ATTR_LAYOUT_TO_LEFT_OF, null);
        node.setAttribute(ANDROID_URI, ATTR_LAYOUT_CENTER_HORIZONTAL, null);

        if (VALUE_TRUE.equals(node.getStringAttr(ANDROID_URI, ATTR_LAYOUT_CENTER_IN_PARENT))) {
            // Already done
        } else if (VALUE_TRUE.equals(node.getStringAttr(ANDROID_URI,
                ATTR_LAYOUT_CENTER_VERTICAL))) {
            node.setAttribute(ANDROID_URI, ATTR_LAYOUT_CENTER_VERTICAL, null);
            node.setAttribute(ANDROID_URI, ATTR_LAYOUT_CENTER_IN_PARENT, VALUE_TRUE);
        } else {
            node.setAttribute(ANDROID_URI, ATTR_LAYOUT_CENTER_HORIZONTAL, VALUE_TRUE);
        }
    }

    private void centerVertically(INode node) {
        // Clear vertical-oriented attributes from the node
        node.setAttribute(ANDROID_URI, ATTR_LAYOUT_ALIGN_PARENT_TOP, null);
        node.setAttribute(ANDROID_URI, ATTR_LAYOUT_ALIGN_TOP, null);
        node.setAttribute(ANDROID_URI, ATTR_LAYOUT_BELOW, null);
        node.setAttribute(ANDROID_URI, ATTR_LAYOUT_ALIGN_PARENT_BOTTOM, null);
        node.setAttribute(ANDROID_URI, ATTR_LAYOUT_ALIGN_BOTTOM, null);
        node.setAttribute(ANDROID_URI, ATTR_LAYOUT_ABOVE, null);
        node.setAttribute(ANDROID_URI, ATTR_LAYOUT_CENTER_VERTICAL, null);
        node.setAttribute(ANDROID_URI, ATTR_LAYOUT_ALIGN_BASELINE, null);

        // Center vertically
        if (VALUE_TRUE.equals(node.getStringAttr(ANDROID_URI, ATTR_LAYOUT_CENTER_IN_PARENT))) {
            // ALready done
        } else if (VALUE_TRUE.equals(node.getStringAttr(ANDROID_URI,
                ATTR_LAYOUT_CENTER_HORIZONTAL))) {
            node.setAttribute(ANDROID_URI, ATTR_LAYOUT_CENTER_HORIZONTAL, null);
            node.setAttribute(ANDROID_URI, ATTR_LAYOUT_CENTER_IN_PARENT, VALUE_TRUE);
        } else {
            node.setAttribute(ANDROID_URI, ATTR_LAYOUT_CENTER_VERTICAL, VALUE_TRUE);
        }
    }
}
