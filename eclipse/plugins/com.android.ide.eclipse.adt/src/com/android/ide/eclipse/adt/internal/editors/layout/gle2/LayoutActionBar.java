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
package com.android.ide.eclipse.adt.internal.editors.layout.gle2;

import static com.android.ide.common.layout.LayoutConstants.ANDROID_URI;
import static com.android.ide.common.layout.LayoutConstants.ATTR_ID;

import com.android.ide.common.api.INode;
import com.android.ide.common.api.RuleAction;
import com.android.ide.common.api.RuleAction.Choices;
import com.android.ide.common.api.RuleAction.Separator;
import com.android.ide.common.api.RuleAction.Toggle;
import com.android.ide.common.layout.BaseViewRule;
import com.android.ide.eclipse.adt.internal.editors.IconFactory;
import com.android.ide.eclipse.adt.internal.editors.layout.configuration.ConfigurationComposite;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.NodeProxy;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.RulesEngine;
import com.android.ide.eclipse.adt.internal.preferences.AdtPrefs;
import com.android.sdkuilib.internal.widgets.ResolutionChooserDialog;

import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Toolbar shown at the top of the layout editor, which adds a number of context-sensitive
 * layout actions (as well as zooming controls on the right).
 */
public class LayoutActionBar extends Composite {
    private GraphicalEditorPart mEditor;
    private ToolBar mLayoutToolBar;
    private ToolBar mZoomToolBar;
    private ToolItem mZoomRealSizeButton;
    private ToolItem mZoomOutButton;
    private ToolItem mZoomResetButton;
    private ToolItem mZoomInButton;
    private ToolItem mZoomFitButton;

    /**
     * Creates a new {@link LayoutActionBar} and adds it to the given parent.
     *
     * @param parent the parent composite to add the actions bar to
     * @param style the SWT style to apply
     * @param editor the associated layout editor
     */
    public LayoutActionBar(Composite parent, int style, GraphicalEditorPart editor) {
        super(parent, style | SWT.NO_FOCUS);
        mEditor = editor;

        GridLayout layout = new GridLayout(2, false);
        setLayout(layout);

        mLayoutToolBar = new ToolBar(this, SWT.FLAT | SWT.RIGHT | SWT.HORIZONTAL);
        mLayoutToolBar.setLayoutData(new GridData(SWT.BEGINNING, SWT.BEGINNING, true, false));
        mZoomToolBar = createZoomControls();
        mZoomToolBar.setLayoutData(new GridData(SWT.END, SWT.BEGINNING, true, false));
    }

    /** Updates the layout contents based on the current selection */
    void updateSelection() {
        // Get rid of any previous children
        for (ToolItem c : mLayoutToolBar.getItems()) {
            c.dispose();
        }
        mLayoutToolBar.pack();

        NodeProxy parent = null;
        LayoutCanvas canvas = mEditor.getCanvasControl();
        SelectionManager selectionManager = canvas.getSelectionManager();
        List<SelectionItem> selections = selectionManager.getSelections();
        if (selections.size() > 0) {
            // TODO: better handle multi-selection -- maybe we should disable it or
            // something.
            // What if you select children with different parents? Of different types?
            // etc.
            NodeProxy node = selections.get(0).getNode();
            if (node != null && node.getParent() != null) {
                parent = (NodeProxy) node.getParent();
            }
        }

        if (parent == null) {
            // Show the background's properties
            CanvasViewInfo root = canvas.getViewHierarchy().getRoot();
            if (root == null) {
                return;
            }
            parent = canvas.getNodeFactory().create(root);
            selections = Collections.emptyList();
        }

        RulesEngine engine = mEditor.getRulesEngine();
        List<NodeProxy> selectedNodes = new ArrayList<NodeProxy>();
        for (SelectionItem item : selections) {
            selectedNodes.add(item.getNode());
        }
        List<RuleAction> actions = new ArrayList<RuleAction>();
        engine.callAddLayoutActions(actions, parent, selectedNodes);

        // Place actions in the correct order (the actions may come from different
        // rules and should be merged properly via sorting keys)
        Collections.sort(actions);

        // Add in actions for the child as well, if there is exactly one.
        // These are not merged into the parent list of actions; they are appended
        // at the end.
        int index = -1;
        String label = null;
        if (selectedNodes.size() == 1) {
            List<RuleAction> itemActions = new ArrayList<RuleAction>();
            NodeProxy selectedNode = selectedNodes.get(0);
            engine.callAddLayoutActions(itemActions, selectedNode, null);
            if (itemActions.size() > 0) {
                Collections.sort(itemActions);

                if (!(itemActions.get(0) instanceof RuleAction.Separator)) {
                    actions.add(RuleAction.createSeparator(0));
                }
                label = selectedNode.getStringAttr(ANDROID_URI, ATTR_ID);
                if (label != null) {
                    label = BaseViewRule.stripIdPrefix(label);
                    index = actions.size();
                }
                actions.addAll(itemActions);
            }
        }

        addActions(actions, index, label);

        mLayoutToolBar.pack();
        mLayoutToolBar.layout();
    }

    private void addActions(List<RuleAction> actions, int labelIndex, String label) {
        if (actions.size() > 0) {
            // Flag used to indicate that if there are any actions -after- this, it
            // should be separated from this current action (we don't unconditionally
            // add a separator at the end of these groups in case there are no more
            // actions at the end so that we don't have a trailing separator)
            boolean needSeparator = false;

            int index = 0;
            for (RuleAction action : actions) {
                if (index == labelIndex) {
                    final ToolItem button = new ToolItem(mLayoutToolBar, SWT.PUSH);
                    button.setText(label);
                    needSeparator = false;
                }
                index++;

                if (action instanceof Separator) {
                    addSeparator(mLayoutToolBar);
                    needSeparator = false;
                    continue;
                } else if (needSeparator) {
                    addSeparator(mLayoutToolBar);
                    needSeparator = false;
                }

                if (action instanceof RuleAction.Choices) {
                    RuleAction.Choices choices = (Choices) action;
                    if (!choices.isRadio()) {
                        addDropdown(choices);
                    } else {
                        addSeparator(mLayoutToolBar);
                        addRadio(choices);
                        needSeparator = true;
                    }
                } else if (action instanceof RuleAction.Toggle) {
                    addToggle((Toggle) action);
                } else {
                    addPlainAction(action);
                }
            }
        }
    }

    /** Add a separator to the toolbar, unless there already is one there at the end already */
    private static void addSeparator(ToolBar toolBar) {
        int n = toolBar.getItemCount();
        if (n > 0 && (toolBar.getItem(n - 1).getStyle() & SWT.SEPARATOR) == 0) {
            ToolItem separator = new ToolItem(toolBar, SWT.SEPARATOR);
            separator.setWidth(15);
        }
    }

    private void addToggle(final Toggle toggle) {
        final ToolItem button = new ToolItem(mLayoutToolBar, SWT.CHECK);

        URL iconUrl = toggle.getIconUrl();
        String title = toggle.getTitle();
        if (iconUrl != null) {
            button.setImage(IconFactory.getInstance().getIcon(iconUrl));
            button.setToolTipText(title);
        } else {
            button.setText(title);
        }

        button.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                toggle.getCallback().action(toggle, getSelectedNodes(),
                        toggle.getId(), button.getSelection());
                updateSelection();
            }
        });
        if (toggle.isChecked()) {
            button.setSelection(true);
        }
    }

    private List<INode> getSelectedNodes() {
        List<SelectionItem> selections =
                mEditor.getCanvasControl().getSelectionManager().getSelections();
        List<INode> nodes = new ArrayList<INode>(selections.size());
        for (SelectionItem item : selections) {
            nodes.add(item.getNode());
        }

        return nodes;
    }


    private void addPlainAction(final RuleAction menuAction) {
        final ToolItem button = new ToolItem(mLayoutToolBar, SWT.PUSH);

        URL iconUrl = menuAction.getIconUrl();
        String title = menuAction.getTitle();
        if (iconUrl != null) {
            button.setImage(IconFactory.getInstance().getIcon(iconUrl));
            button.setToolTipText(title);
        } else {
            button.setText(title);
        }

        button.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                menuAction.getCallback().action(menuAction, getSelectedNodes(), menuAction.getId(),
                        false);
                updateSelection();
            }
        });
    }

    private void addRadio(final RuleAction.Choices choices) {
        List<URL> icons = choices.getIconUrls();
        List<String> titles = choices.getTitles();
        List<String> ids = choices.getIds();
        String current = choices.getCurrent() != null ? choices.getCurrent() : ""; //$NON-NLS-1$

        assert icons != null;
        assert icons.size() == titles.size();

        for (int i = 0; i < icons.size(); i++) {
            URL iconUrl = icons.get(i);
            String title = titles.get(i);
            final String id = ids.get(i);
            final ToolItem item = new ToolItem(mLayoutToolBar, SWT.RADIO);
            item.setToolTipText(title);
            item.setImage(IconFactory.getInstance().getIcon(iconUrl));
            item.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    if (item.getSelection()) {
                        choices.getCallback().action(choices, getSelectedNodes(), id, null);
                        updateSelection();
                    }
                }
            });
            boolean selected = current.equals(id);
            if (selected) {
                item.setSelection(true);
            }
        }
    }

    private void addDropdown(final RuleAction.Choices choices) {
        final ToolItem combo = new ToolItem(mLayoutToolBar, SWT.DROP_DOWN);
        URL iconUrl = choices.getIconUrl();
        if (iconUrl != null) {
            combo.setImage(IconFactory.getInstance().getIcon(iconUrl));
            combo.setToolTipText(choices.getTitle());
        } else {
            combo.setText(choices.getTitle());
        }

        Listener menuListener = new Listener() {
            public void handleEvent(Event event) {
                // if (event.detail == SWT.ARROW) {
                Point point = new Point(event.x, event.y);
                point = combo.getDisplay().map(mLayoutToolBar, null, point);

                Menu menu = new Menu(mLayoutToolBar.getShell(), SWT.POP_UP);

                List<URL> icons = choices.getIconUrls();
                List<String> titles = choices.getTitles();
                List<String> ids = choices.getIds();
                String current = choices.getCurrent() != null ? choices.getCurrent() : ""; //$NON-NLS-1$

                for (int i = 0; i < titles.size(); i++) {
                    String title = titles.get(i);
                    final String id = ids.get(i);
                    URL itemIconUrl = icons != null && icons.size() > 0 ? icons.get(i) : null;
                    MenuItem item = new MenuItem(menu, SWT.CHECK);
                    item.setText(title);
                    if (itemIconUrl != null) {
                        Image itemIcon = IconFactory.getInstance().getIcon(itemIconUrl);
                        item.setImage(itemIcon);
                    }

                    boolean selected = id.equals(current);
                    if (selected) {
                        item.setSelection(true);
                    }

                    item.addSelectionListener(new SelectionAdapter() {
                        @Override
                        public void widgetSelected(SelectionEvent e) {
                            choices.getCallback().action(choices, getSelectedNodes(), id, null);
                            updateSelection();
                        }
                    });
                }

                // TODO - how do I dispose of this?

                menu.setLocation(point);
                menu.setVisible(true);
            }
        };
        combo.addListener(SWT.Selection, menuListener);
    }

    // ---- Zoom Controls ----

    private ToolBar createZoomControls() {
        ToolBar toolBar = new ToolBar(this, SWT.FLAT | SWT.RIGHT | SWT.HORIZONTAL);

        IconFactory iconFactory = IconFactory.getInstance();
        mZoomRealSizeButton = new ToolItem(toolBar, SWT.CHECK);
        mZoomRealSizeButton.setToolTipText("Emulate Real Size");
        mZoomRealSizeButton.setImage(iconFactory.getIcon("zoomreal")); //$NON-NLS-1$);
        mZoomRealSizeButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                boolean newState = mZoomRealSizeButton.getSelection();
                if (rescaleToReal(newState)) {
                    mZoomOutButton.setEnabled(!newState);
                    mZoomResetButton.setEnabled(!newState);
                    mZoomInButton.setEnabled(!newState);
                    mZoomFitButton.setEnabled(!newState);
                } else {
                    mZoomRealSizeButton.setSelection(!newState);
                }
            }
        });

        mZoomFitButton = new ToolItem(toolBar, SWT.PUSH);
        mZoomFitButton.setToolTipText("Zoom to Fit (0)");
        mZoomFitButton.setImage(iconFactory.getIcon("zoomfit")); //$NON-NLS-1$);
        mZoomFitButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                rescaleToFit(true);
            }
        });

        mZoomResetButton = new ToolItem(toolBar, SWT.PUSH);
        mZoomResetButton.setToolTipText("Reset Zoom to 100% (1)");
        mZoomResetButton.setImage(iconFactory.getIcon("zoom100")); //$NON-NLS-1$);
        mZoomResetButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                resetScale();
            }
        });

        // Group zoom in/out separately
        new ToolItem(toolBar, SWT.SEPARATOR);

        mZoomOutButton = new ToolItem(toolBar, SWT.PUSH);
        mZoomOutButton.setToolTipText("Zoom Out (-)");
        mZoomOutButton.setImage(iconFactory.getIcon("zoomminus")); //$NON-NLS-1$);
        mZoomOutButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                rescale(-1);
            }
        });

        mZoomInButton = new ToolItem(toolBar, SWT.PUSH);
        mZoomInButton.setToolTipText("Zoom In (+)");
        mZoomInButton.setImage(iconFactory.getIcon("zoomplus")); //$NON-NLS-1$);
        mZoomInButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                rescale(+1);
            }
        });

        return toolBar;
    }

    /**
     * Returns true if zooming in/out/to-fit/etc is allowed (which is not the case while
     * emulating real size)
     *
     * @return true if zooming is allowed
     */
    boolean isZoomingAllowed() {
        return mZoomInButton.isEnabled();
    }

    boolean isZoomingRealSize() {
        return mZoomRealSizeButton.getSelection();
    }

    /**
     * Rescales canvas.
     * @param direction +1 for zoom in, -1 for zoom out
     */
    void rescale(int direction) {
        LayoutCanvas canvas = mEditor.getCanvasControl();
        double s = canvas.getScale();

        if (direction > 0) {
            s = s * 1.2;
        } else {
            s = s / 1.2;
        }

        // Some operations are faster if the zoom is EXACTLY 1.0 rather than ALMOST 1.0.
        // (This is because there is a fast-path when image copying and the scale is 1.0;
        // in that case it does not have to do any scaling).
        //
        // If you zoom out 10 times and then back in 10 times, small rounding errors mean
        // that you end up with a scale=1.0000000000000004. In the cases, when you get close
        // to 1.0, just make the zoom an exact 1.0.
        if (Math.abs(s-1.0) < 0.0001) {
            s = 1.0;
        }

        canvas.setScale(s, true /*redraw*/);
    }

    /**
     * Reset the canvas scale to 100%
     */
    void resetScale() {
        mEditor.getCanvasControl().setScale(1, true /*redraw*/);
    }

    /**
     * Reset the canvas scale to best fit (so content is as large as possible without scrollbars)
     */
    void rescaleToFit(boolean onlyZoomOut) {
        mEditor.getCanvasControl().setFitScale(onlyZoomOut);
    }

    boolean rescaleToReal(boolean real) {
        if (real) {
            return computeAndSetRealScale(true /*redraw*/);
        } else {
            // reset the scale to 100%
            mEditor.getCanvasControl().setScale(1, true /*redraw*/);
            return true;
        }
    }

    boolean computeAndSetRealScale(boolean redraw) {
        // compute average dpi of X and Y
        ConfigurationComposite config = mEditor.getConfigurationComposite();
        float dpi = (config.getXDpi() + config.getYDpi()) / 2.f;

        // get the monitor dpi
        float monitor = AdtPrefs.getPrefs().getMonitorDensity();
        if (monitor == 0.f) {
            ResolutionChooserDialog dialog = new ResolutionChooserDialog(
                    config.getShell());
            if (dialog.open() == Window.OK) {
                monitor = dialog.getDensity();
                AdtPrefs.getPrefs().setMonitorDensity(monitor);
            } else {
                return false;
            }
        }

        mEditor.getCanvasControl().setScale(monitor / dpi, redraw);
        return true;
    }
}
