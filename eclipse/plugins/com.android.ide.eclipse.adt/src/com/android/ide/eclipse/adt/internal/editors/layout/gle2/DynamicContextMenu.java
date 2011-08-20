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

package com.android.ide.eclipse.adt.internal.editors.layout.gle2;

import static com.android.ide.common.layout.LayoutConstants.ANDROID_URI;
import static com.android.ide.common.layout.LayoutConstants.ATTR_ID;
import static com.android.ide.common.layout.LayoutConstants.EXPANDABLE_LIST_VIEW;
import static com.android.ide.common.layout.LayoutConstants.FQCN_GESTURE_OVERLAY_VIEW;
import static com.android.ide.common.layout.LayoutConstants.GRID_VIEW;
import static com.android.ide.common.layout.LayoutConstants.LIST_VIEW;
import static com.android.ide.common.layout.LayoutConstants.SPINNER;
import static com.android.ide.eclipse.adt.internal.editors.layout.descriptors.LayoutDescriptors.VIEW_FRAGMENT;

import com.android.ide.common.api.INode;
import com.android.ide.common.api.IViewRule;
import com.android.ide.common.api.RuleAction;
import com.android.ide.common.api.RuleAction.Choices;
import com.android.ide.common.api.RuleAction.NestedAction;
import com.android.ide.common.api.RuleAction.Toggle;
import com.android.ide.common.layout.BaseViewRule;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.NodeFactory;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.NodeProxy;
import com.android.ide.eclipse.adt.internal.editors.layout.refactoring.ChangeLayoutAction;
import com.android.ide.eclipse.adt.internal.editors.layout.refactoring.ChangeViewAction;
import com.android.ide.eclipse.adt.internal.editors.layout.refactoring.ExtractIncludeAction;
import com.android.ide.eclipse.adt.internal.editors.layout.refactoring.ExtractStyleAction;
import com.android.ide.eclipse.adt.internal.editors.layout.refactoring.UnwrapAction;
import com.android.ide.eclipse.adt.internal.editors.layout.refactoring.WrapInAction;
import com.android.ide.eclipse.adt.internal.editors.layout.uimodel.UiViewElementNode;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.ContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Helper class that is responsible for adding and managing the dynamic menu items
 * contributed by the {@link IViewRule} instances, based on the current selection
 * on the {@link LayoutCanvas}.
 * <p/>
 * This class is tied to a specific {@link LayoutCanvas} instance and a root {@link MenuManager}.
 * <p/>
 * Two instances of this are used: one created by {@link LayoutCanvas} and the other one
 * created by {@link OutlinePage}. Different root {@link MenuManager}s are populated, however
 * they are both linked to the current selection state of the {@link LayoutCanvas}.
 */
class DynamicContextMenu {

    /** The XML layout editor that contains the canvas that uses this menu. */
    private final LayoutEditor mEditor;

    /** The layout canvas that displays this context menu. */
    private final LayoutCanvas mCanvas;

    /** The root menu manager of the context menu. */
    private final MenuManager mMenuManager;

    /**
     * Creates a new helper responsible for adding and managing the dynamic menu items
     * contributed by the {@link IViewRule} instances, based on the current selection
     * on the {@link LayoutCanvas}.
     * @param editor the editor owning the menu
     * @param canvas The {@link LayoutCanvas} providing the selection, the node factory and
     *   the rules engine.
     * @param rootMenu The root of the context menu displayed. In practice this may be the
     *   context menu manager of the {@link LayoutCanvas} or the one from {@link OutlinePage}.
     */
    public DynamicContextMenu(LayoutEditor editor, LayoutCanvas canvas, MenuManager rootMenu) {
        mEditor = editor;
        mCanvas = canvas;
        mMenuManager = rootMenu;

        setupDynamicMenuActions();
    }

    /**
     * Setups the menu manager to receive dynamic menu contributions from the {@link IViewRule}s
     * when it's about to be shown.
     */
    private void setupDynamicMenuActions() {
        // Remember how many static actions we have. Then each time the menu is
        // shown, find dynamic contributions based on the current selection and insert
        // them at the beginning of the menu.
        final int numStaticActions = mMenuManager.getSize();
        mMenuManager.addMenuListener(new IMenuListener() {
            public void menuAboutToShow(IMenuManager manager) {

                // Remove any previous dynamic contributions to keep only the
                // default static items.
                int n = mMenuManager.getSize() - numStaticActions;
                if (n > 0) {
                    IContributionItem[] items = mMenuManager.getItems();
                    for (int i = 0; i < n; i++) {
                        mMenuManager.remove(items[i]);
                    }
                }

                // Now add all the dynamic menu actions depending on the current selection.
                populateDynamicContextMenu();
            }
        });

    }

    /**
     * This method is invoked by <code>menuAboutToShow</code> on {@link #mMenuManager}.
     * All previous dynamic menu actions have been removed and this method can now insert
     * any new actions that depend on the current selection.
     */
    private void populateDynamicContextMenu() {
        //  Create the actual menu contributions
        String endId = mMenuManager.getItems()[0].getId();

        Separator sep = new Separator();
        sep.setId("-dyn-gle-sep");  //$NON-NLS-1$
        mMenuManager.insertBefore(endId, sep);
        endId = sep.getId();

        List<SelectionItem> selections = mCanvas.getSelectionManager().getSelections();
        if (selections.size() == 0) {
            return;
        }
        List<INode> nodes = new ArrayList<INode>(selections.size());
        for (SelectionItem item : selections) {
            nodes.add(item.getNode());
        }

        List<IContributionItem> menuItems = getMenuItems(nodes);
        for (IContributionItem menuItem : menuItems) {
            mMenuManager.insertBefore(endId, menuItem);
        }

        insertTagSpecificMenus(endId);
        insertVisualRefactorings(endId);
        insertParentItems(endId);
    }

    /**
     * Returns the list of node-specific actions applicable to the given
     * collection of nodes
     *
     * @param nodes the collection of nodes to look up actions for
     * @return a list of contribution items applicable for all the nodes
     */
    private List<IContributionItem> getMenuItems(List<INode> nodes) {
        Map<INode, List<RuleAction>> allActions = new HashMap<INode, List<RuleAction>>();
        for (INode node : nodes) {
            List<RuleAction> actionList = getMenuActions((NodeProxy) node);
            allActions.put(node, actionList);
        }

        Set<String> availableIds = computeApplicableActionIds(allActions);

        // +10: Make room for separators too
        List<IContributionItem> items = new ArrayList<IContributionItem>(availableIds.size() + 10);

        // We'll use the actions returned by the first node. Even when there
        // are multiple items selected, we'll use the first action, but pass
        // the set of all selected nodes to that first action. Actions are required
        // to work this way to facilitate multi selection and actions which apply
        // to multiple nodes.
        List<RuleAction> firstSelectedActions = allActions.get(nodes.get(0));

        for (RuleAction action : firstSelectedActions) {
            if (!availableIds.contains(action.getId())
                    && !(action instanceof RuleAction.Separator)) {
                // This action isn't supported by all selected items.
                continue;
            }

            items.add(createContributionItem(action, nodes));
        }

        return items;
    }

    private void insertParentItems(String endId) {
        List<SelectionItem> selection = mCanvas.getSelectionManager().getSelections();
        if (selection.size() == 1) {
            mMenuManager.insertBefore(endId, new Separator());
            INode parent = selection.get(0).getNode().getParent();
            while (parent != null) {
                String id = parent.getStringAttr(ANDROID_URI, ATTR_ID);
                String label;
                if (id != null && id.length() > 0) {
                    label = BaseViewRule.stripIdPrefix(id);
                } else {
                    // Use the view name, such as "Button", as the label
                    label = parent.getFqcn();
                    // Strip off package
                    label = label.substring(label.lastIndexOf('.') + 1);
                }
                mMenuManager.insertBefore(endId, new NestedParentMenu(label, parent));
                parent = parent.getParent();
            }
            mMenuManager.insertBefore(endId, new Separator());
        }
    }

    private void insertVisualRefactorings(String endId) {
        // Extract As <include> refactoring, Wrap In Refactoring, etc.
        List<SelectionItem> selection = mCanvas.getSelectionManager().getSelections();
        if (selection.size() == 0) {
            return;
        }
        // Only include the menu item if you are not right clicking on a root,
        // or on an included view, or on a non-contiguous selection
        mMenuManager.insertBefore(endId, new Separator());
        mMenuManager.insertBefore(endId, ExtractIncludeAction.create(mEditor));
        mMenuManager.insertBefore(endId, ExtractStyleAction.create(mEditor));
        mMenuManager.insertBefore(endId, WrapInAction.create(mEditor));
        if (selection.size() == 1 && !(selection.get(0).isRoot())) {
            mMenuManager.insertBefore(endId, UnwrapAction.create(mEditor));
        }
        if (selection.size() == 1 && (selection.get(0).isLayout() ||
                selection.get(0).getViewInfo().getName().equals(FQCN_GESTURE_OVERLAY_VIEW))) {
            mMenuManager.insertBefore(endId, ChangeLayoutAction.create(mEditor));
        } else {
            mMenuManager.insertBefore(endId, ChangeViewAction.create(mEditor));
        }
        mMenuManager.insertBefore(endId, new Separator());
    }

    /** "Preview List Content" pull-right menu for lists, "Preview Fragment" for fragments, etc. */
    private void insertTagSpecificMenus(String endId) {

        List<SelectionItem> selection = mCanvas.getSelectionManager().getSelections();
        if (selection.size() == 0) {
            return;
        }
        for (SelectionItem item : selection) {
            UiViewElementNode node = item.getViewInfo().getUiViewNode();
            String name = node.getDescriptor().getXmlLocalName();
            boolean isGrid = name.equals(GRID_VIEW);
            boolean isSpinner = name.equals(SPINNER);
            if (name.equals(LIST_VIEW) || name.equals(EXPANDABLE_LIST_VIEW)
                    || isGrid || isSpinner) {
                mMenuManager.insertBefore(endId, new Separator());
                mMenuManager.insertBefore(endId, new ListViewTypeMenu(mCanvas, isGrid, isSpinner));
                return;
            } else if (name.equals(VIEW_FRAGMENT) && selection.size() == 1) {
                mMenuManager.insertBefore(endId, new Separator());
                mMenuManager.insertBefore(endId, new FragmentMenu(mCanvas));
                return;
            }
        }
    }

    /**
     * Given a map from selection items to list of applicable actions (produced
     * by {@link #computeApplicableActions()}) this method computes the set of
     * common actions and returns the action ids of these actions.
     *
     * @param actions a map from selection item to list of actions applicable to
     *            that selection item
     * @return set of action ids for the actions that are present in the action
     *         lists for all selected items
     */
    private Set<String> computeApplicableActionIds(Map<INode, List<RuleAction>> actions) {
        if (actions.size() > 1) {
            // More than one view is selected, so we have to filter down the available
            // actions such that only those actions that are defined for all the views
            // are shown
            Map<String, Integer> idCounts = new HashMap<String, Integer>();
            for (Map.Entry<INode, List<RuleAction>> entry : actions.entrySet()) {
                List<RuleAction> actionList = entry.getValue();
                for (RuleAction action : actionList) {
                    if (!action.supportsMultipleNodes()) {
                        continue;
                    }
                    String id = action.getId();
                    if (id != null) {
                        assert id != null : action;
                        Integer count = idCounts.get(id);
                        if (count == null) {
                            idCounts.put(id, Integer.valueOf(1));
                        } else {
                            idCounts.put(id, count + 1);
                        }
                    }
                }
            }
            Integer selectionCount = Integer.valueOf(actions.size());
            Set<String> validIds = new HashSet<String>(idCounts.size());
            for (Map.Entry<String, Integer> entry : idCounts.entrySet()) {
                Integer count = entry.getValue();
                if (selectionCount.equals(count)) {
                    String id = entry.getKey();
                    validIds.add(id);
                }
            }
            return validIds;
        } else {
            List<RuleAction> actionList = actions.values().iterator().next();
            Set<String> validIds = new HashSet<String>(actionList.size());
            for (RuleAction action : actionList) {
                String id = action.getId();
                validIds.add(id);
            }
            return validIds;
        }
    }

    /**
     * Returns the menu actions computed by the rule associated with this node.
     *
     * @param node the canvas node we need menu actions for
     * @return a list of {@link RuleAction} objects applicable to the node
     */
    private List<RuleAction> getMenuActions(NodeProxy node) {
        List<RuleAction> actions = mCanvas.getRulesEngine().callGetContextMenu(node);
        if (actions == null || actions.size() == 0) {
            return null;
        }

        return actions;
    }

    /**
     * Creates a {@link ContributionItem} for the given {@link RuleAction}.
     *
     * @param action the action to create a {@link ContributionItem} for
     * @param nodes the set of nodes the action should be applied to
     * @return a new {@link ContributionItem} which implements the given action
     *         on the given nodes
     */
    private ContributionItem createContributionItem(final RuleAction action,
            final List<INode> nodes) {
        if (action instanceof RuleAction.Separator) {
            return new Separator();
        } else if (action instanceof NestedAction) {
            NestedAction parentAction = (NestedAction) action;
            return new ActionContributionItem(new NestedActionMenu(parentAction, nodes));
        } else if (action instanceof Choices) {
            Choices parentAction = (Choices) action;
            return new ActionContributionItem(new NestedChoiceMenu(parentAction, nodes));
        } else if (action instanceof Toggle) {
            return new ActionContributionItem(createToggleAction(action, nodes));
        } else {
            return new ActionContributionItem(createPlainAction(action, nodes));
        }
    }

    private Action createToggleAction(final RuleAction action, final List<INode> nodes) {
        Toggle toggleAction = (Toggle) action;
        final boolean isChecked = toggleAction.isChecked();
        Action a = new Action(action.getTitle(), IAction.AS_CHECK_BOX) {
            @Override
            public void run() {
                String label = createActionLabel(action, nodes);
                mEditor.wrapUndoEditXmlModel(label, new Runnable() {
                    public void run() {
                        action.getCallback().action(action, nodes,
                                null/* no valueId for a toggle */, !isChecked);
                        applyPendingChanges();
                    }
                });
            }
        };
        a.setId(action.getId());
        a.setChecked(isChecked);
        return a;
    }

    private IAction createPlainAction(final RuleAction action, final List<INode> nodes) {
        IAction a = new Action(action.getTitle(), IAction.AS_PUSH_BUTTON) {
            @Override
            public void run() {
                String label = createActionLabel(action, nodes);
                mEditor.wrapUndoEditXmlModel(label, new Runnable() {
                    public void run() {
                        action.getCallback().action(action, nodes, null,
                                Boolean.TRUE);
                        applyPendingChanges();
                    }
                });
            }
        };
        a.setId(action.getId());
        return a;
    }

    private static String createActionLabel(final RuleAction action, final List<INode> nodes) {
        String label = action.getTitle();
        if (nodes.size() > 1) {
            label += String.format(" (%d elements)", nodes.size());
        }
        return label;
    }

    /**
     * The {@link NestedParentMenu} provides submenu content which adds actions
     * available on one of the selected node's parent nodes. This will be
     * similar to the menu content for the selected node, except the parent
     * menus will not be embedded within the nested menu.
     */
    private class NestedParentMenu extends SubmenuAction {
        INode mParent;

        NestedParentMenu(String title, INode parent) {
            super(title);
            mParent = parent;
        }

        @Override
        protected void addMenuItems(Menu menu) {
            List<SelectionItem> selection = mCanvas.getSelectionManager().getSelections();
            if (selection.size() == 0) {
                return;
            }

            List<IContributionItem> menuItems = getMenuItems(Collections.singletonList(mParent));
            for (IContributionItem menuItem : menuItems) {
                menuItem.fill(menu, -1);
            }
        }
    }

    /**
     * The {@link NestedActionMenu} creates a lazily populated pull-right menu
     * where the children are {@link RuleAction}'s themselves.
     */
    private class NestedActionMenu extends SubmenuAction {
        private final NestedAction mParentAction;
        private final List<INode> mNodes;

        NestedActionMenu(NestedAction parentAction, List<INode> nodes) {
            super(parentAction.getTitle());
            mParentAction = parentAction;
            mNodes = nodes;

            assert mNodes.size() > 0;
        }

        @Override
        protected void addMenuItems(Menu menu) {
            Map<INode, List<RuleAction>> allActions = new HashMap<INode, List<RuleAction>>();
            for (INode node : mNodes) {
                List<RuleAction> actionList = mParentAction.getNestedActions(node);
                allActions.put(node, actionList);
            }

            Set<String> availableIds = computeApplicableActionIds(allActions);
            List<RuleAction> firstSelectedActions = allActions.get(mNodes.get(0));

            int count = 0;
            for (RuleAction firstAction : firstSelectedActions) {
                if (!availableIds.contains(firstAction.getId())
                        && !(firstAction instanceof RuleAction.Separator)) {
                    // This action isn't supported by all selected items.
                    continue;
                }

                createContributionItem(firstAction, mNodes).fill(menu, -1);
                count++;
            }

            if (count == 0) {
                addDisabledMessageItem("<Empty>");
            }
        }
    }

    private void applyPendingChanges() {
        LayoutCanvas canvas = mEditor.getGraphicalEditor().getCanvasControl();
        CanvasViewInfo root = canvas.getViewHierarchy().getRoot();
        if (root != null) {
            UiViewElementNode uiViewNode = root.getUiViewNode();
            NodeFactory nodeFactory = canvas.getNodeFactory();
            NodeProxy rootNode = nodeFactory.create(uiViewNode);
            if (rootNode != null) {
                rootNode.applyPendingChanges();
            }
        }
    }

    /**
     * The {@link NestedChoiceMenu} creates a lazily populated pull-right menu
     * where the items in the menu are strings
     */
    private class NestedChoiceMenu extends SubmenuAction {
        private final Choices mParentAction;
        private final List<INode> mNodes;

        NestedChoiceMenu(Choices parentAction, List<INode> nodes) {
            super(parentAction.getTitle());
            mParentAction = parentAction;
            mNodes = nodes;
        }

        @Override
        protected void addMenuItems(Menu menu) {
            List<String> titles = mParentAction.getTitles();
            List<String> ids = mParentAction.getIds();
            String current = mParentAction.getCurrent();
            assert titles.size() == ids.size();
            String[] currentValues = current != null
                    && current.indexOf(RuleAction.CHOICE_SEP) != -1 ?
                    current.split(RuleAction.CHOICE_SEP_PATTERN) : null;
            for (int i = 0, n = Math.min(titles.size(), ids.size()); i < n; i++) {
                final String id = ids.get(i);
                if (id == null || id.equals(RuleAction.SEPARATOR)) {
                    new Separator().fill(menu, -1);
                    continue;
                }

                // Find out whether this item is selected
                boolean select = false;
                if (current != null) {
                    // The current choice has a separator, so it's a flag with
                    // multiple values selected. Compare keys with the split
                    // values.
                    if (currentValues != null) {
                        if (current.indexOf(id) >= 0) {
                            for (String value : currentValues) {
                                if (id.equals(value)) {
                                    select = true;
                                    break;
                                }
                            }
                        }
                    } else {
                        // current choice has no separator, simply compare to the key
                        select = id.equals(current);
                    }
                }

                String title = titles.get(i);
                IAction a = new Action(title,
                        current != null ? IAction.AS_CHECK_BOX : IAction.AS_PUSH_BUTTON) {
                    @Override
                    public void runWithEvent(Event event) {
                        run();
                    }
                    @Override
                    public void run() {
                        String label = createActionLabel(mParentAction, mNodes);
                        mEditor.wrapUndoEditXmlModel(label, new Runnable() {
                            public void run() {
                                mParentAction.getCallback().action(mParentAction, mNodes, id,
                                        Boolean.TRUE);
                                applyPendingChanges();
                            }
                        });
                    }
                };
                a.setId(id);
                a.setEnabled(true);
                if (select) {
                    a.setChecked(true);
                }

                new ActionContributionItem(a).fill(menu, -1);
            }
        }
    }
}
