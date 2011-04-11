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

import static com.android.ide.common.layout.LayoutConstants.FQCN_TABLE_ROW;

import com.android.ide.common.api.IClientRulesEngine;
import com.android.ide.common.api.IMenuCallback;
import com.android.ide.common.api.INode;
import com.android.ide.common.api.INodeHandler;
import com.android.ide.common.api.IViewRule;
import com.android.ide.common.api.InsertType;
import com.android.ide.common.api.MenuAction;

import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * An {@link IViewRule} for android.widget.TableLayout.
 */
public class TableLayoutRule extends LinearLayoutRule {
    // A table is a linear layout, but with a few differences:
    // the default is vertical, not horizontal
    // The fill of all children should be wrap_content

    private static final String ACTION_ADD_ROW = "_addrow"; //$NON-NLS-1$
    private static final String ACTION_REMOVE_ROW = "_removerow"; //$NON-NLS-1$
    private static final URL ICON_ADD_ROW =
        TableLayoutRule.class.getResource("addrow.png"); //$NON-NLS-1$
    private static final URL ICON_REMOVE_ROW =
        TableLayoutRule.class.getResource("removerow.png"); //$NON-NLS-1$

    @Override
    protected boolean isVertical(INode node) {
        // Tables are always vertical
        return true;
    }

    @Override
    protected boolean supportsOrientation() {
        return false;
    }

    @Override
    public void onChildInserted(INode child, INode parent, InsertType insertType) {
        // Overridden to inhibit the setting of layout_width/layout_height since
        // it should always be match_parent
    }

    /**
     * Add an explicit "Add Row" action to the context menu
     */
    @Override
   public List<MenuAction> getContextMenu(final INode selectedNode) {
        IMenuCallback addTab = new IMenuCallback() {
            public void action(MenuAction action, final String valueId, Boolean newValue) {
                final INode node = selectedNode;
                INode newRow = node.appendChild(FQCN_TABLE_ROW);
                mRulesEngine.select(Collections.singletonList(newRow));
            }
        };
        return concatenate(super.getContextMenu(selectedNode),
            new MenuAction.Action("_addrow", "Add Row", //$NON-NLS-1$
                    null, addTab));
    }

    @Override
    public void addLayoutActions(List<MenuAction> actions, final INode parentNode,
            final List<? extends INode> children) {
        super.addLayoutActions(actions, parentNode, children);
        addTableLayoutActions(mRulesEngine, actions, parentNode, children);
    }

    /**
     * Adds layout actions to add and remove toolbar items
     */
    static void addTableLayoutActions(final IClientRulesEngine rulesEngine,
            List<MenuAction> actions, final INode parentNode,
            final List<? extends INode> children) {
        IMenuCallback actionCallback = new IMenuCallback() {
            public void action(final MenuAction action, final String valueId,
                    final Boolean newValue) {
                parentNode.editXml("Add/Remove Table Row", new INodeHandler() {
                    public void handle(INode n) {
                        if (action.getId().equals(ACTION_ADD_ROW)) {
                            // Determine the index of the selection, if any; if there is
                            // a selection, insert the row before the current row, otherwise
                            // append it to the table.
                            int index = -1;
                            INode[] rows = parentNode.getChildren();
                            if (children != null) {
                                findTableIndex:
                                for (INode child : children) {
                                    // Find direct child of table layout
                                    while (child != null && child.getParent() != parentNode) {
                                        child = child.getParent();
                                    }
                                    if (child != null) {
                                        // Compute index of direct child of table layout
                                        for (int i = 0; i < rows.length; i++) {
                                            if (rows[i] == child) {
                                                index = i;
                                                break findTableIndex;
                                            }
                                        }
                                    }
                                }
                            }
                            INode newRow;
                            if (index == -1) {
                                newRow = parentNode.appendChild(FQCN_TABLE_ROW);
                            } else {
                                newRow = parentNode.insertChildAt(FQCN_TABLE_ROW, index);
                            }
                            rulesEngine.select(Collections.singletonList(newRow));
                        } else if (action.getId().equals(ACTION_REMOVE_ROW)) {
                            // Find the direct children of the TableLayout to delete;
                            // this is necessary since TableRow might also use
                            // this implementation, so the parentNode is the true
                            // TableLayout but the children might be grand children.
                            Set<INode> targets = new HashSet<INode>();
                            for (INode child : children) {
                                while (child != null && child.getParent() != parentNode) {
                                    child = child.getParent();
                                }
                                if (child != null) {
                                    targets.add(child);
                                }
                            }
                            for (INode target : targets) {
                                parentNode.removeChild(target);
                            }
                        }
                    }
                });
            }
        };

        // Add Row
        actions.add(MenuAction.createSeparator(150));
        actions.add(MenuAction.createAction(ACTION_ADD_ROW, "Add Table Row", null, actionCallback,
                ICON_ADD_ROW, 160));

        // Remove Row (if something is selected)
        if (children != null && children.size() > 0) {
            actions.add(MenuAction.createAction(ACTION_REMOVE_ROW, "Remove Table Row", null,
                    actionCallback, ICON_REMOVE_ROW, 170));
        }
    }

    @Override
    public void onCreate(INode node, INode parent, InsertType insertType) {
        super.onCreate(node, parent, insertType);

        if (insertType.isCreate()) {
            // Start the table with 4 rows
            for (int i = 0; i < 4; i++) {
                node.appendChild(FQCN_TABLE_ROW);
            }
        }
    }
}
