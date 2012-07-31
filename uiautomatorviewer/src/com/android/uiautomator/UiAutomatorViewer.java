/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.uiautomator;

import com.android.uiautomator.actions.ExpandAllAction;
import com.android.uiautomator.actions.OpenFilesAction;
import com.android.uiautomator.actions.ScreenshotAction;
import com.android.uiautomator.actions.ToggleNafAction;
import com.android.uiautomator.tree.AttributePair;
import com.android.uiautomator.tree.BasicTreeNode;
import com.android.uiautomator.tree.BasicTreeNodeContentProvider;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.window.ApplicationWindow;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.Transform;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Tree;

public class UiAutomatorViewer extends ApplicationWindow {

    private static final int IMG_BORDER = 2;

    private Canvas mScreenshotCanvas;
    private TreeViewer mTreeViewer;

    private Action mOpenFilesAction;
    private Action mExpandAllAction;
    private Action mScreenshotAction;
    private Action mToggleNafAction;
    private TableViewer mTableViewer;

    private float mScale = 1.0f;
    private int mDx, mDy;

    /**
     * Create the application window.
     */
    public UiAutomatorViewer() {
        super(null);
        UiAutomatorModel.createInstance(this);
        createActions();
    }

    /**
     * Create contents of the application window.
     *
     * @param parent
     */
    @Override
    protected Control createContents(Composite parent) {
        SashForm baseSash = new SashForm(parent, SWT.HORIZONTAL | SWT.NONE);
        // draw the canvas with border, so the divider area for sash form can be highlighted
        mScreenshotCanvas = new Canvas(baseSash, SWT.BORDER);
        mScreenshotCanvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseUp(MouseEvent e) {
                UiAutomatorModel.getModel().toggleExploreMode();
            }
        });
        mScreenshotCanvas.setBackground(
                getShell().getDisplay().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
        mScreenshotCanvas.addPaintListener(new PaintListener() {
            @Override
            public void paintControl(PaintEvent e) {
                Image image = UiAutomatorModel.getModel().getScreenshot();
                if (image != null) {
                    updateScreenshotTransformation();
                    // shifting the image here, so that there's a border around screen shot
                    // this makes highlighting red rectangles on the screen shot edges more visible
                    Transform t = new Transform(e.gc.getDevice());
                    t.translate(mDx, mDy);
                    t.scale(mScale, mScale);
                    e.gc.setTransform(t);
                    e.gc.drawImage(image, 0, 0);
                    // this resets the transformation to identity transform, i.e. no change
                    // we don't use transformation here because it will cause the line pattern
                    // and line width of highlight rect to be scaled, causing to appear to be blurry
                    e.gc.setTransform(null);
                    if (UiAutomatorModel.getModel().shouldShowNafNodes()) {
                        // highlight the "Not Accessibility Friendly" nodes
                        e.gc.setForeground(e.gc.getDevice().getSystemColor(SWT.COLOR_YELLOW));
                        e.gc.setBackground(e.gc.getDevice().getSystemColor(SWT.COLOR_YELLOW));
                        for (Rectangle r : UiAutomatorModel.getModel().getNafNodes()) {
                            e.gc.setAlpha(50);
                            e.gc.fillRectangle(mDx + getScaledSize(r.x), mDy + getScaledSize(r.y),
                                    getScaledSize(r.width), getScaledSize(r.height));
                            e.gc.setAlpha(255);
                            e.gc.setLineStyle(SWT.LINE_SOLID);
                            e.gc.setLineWidth(2);
                            e.gc.drawRectangle(mDx + getScaledSize(r.x), mDy + getScaledSize(r.y),
                                    getScaledSize(r.width), getScaledSize(r.height));
                        }
                    }
                    // draw the mouseover rects
                    Rectangle rect = UiAutomatorModel.getModel().getCurrentDrawingRect();
                    if (rect != null) {
                        e.gc.setForeground(e.gc.getDevice().getSystemColor(SWT.COLOR_RED));
                        if (UiAutomatorModel.getModel().isExploreMode()) {
                            // when we highlight nodes dynamically on mouse move,
                            // use dashed borders
                            e.gc.setLineStyle(SWT.LINE_DASH);
                            e.gc.setLineWidth(1);
                        } else {
                            // when highlighting nodes on tree node selection,
                            // use solid borders
                            e.gc.setLineStyle(SWT.LINE_SOLID);
                            e.gc.setLineWidth(2);
                        }
                        e.gc.drawRectangle(mDx + getScaledSize(rect.x), mDy + getScaledSize(rect.y),
                                getScaledSize(rect.width), getScaledSize(rect.height));
                    }
                }
            }
        });
        mScreenshotCanvas.addMouseMoveListener(new MouseMoveListener() {
            @Override
            public void mouseMove(MouseEvent e) {
                if (UiAutomatorModel.getModel().isExploreMode()) {
                    UiAutomatorModel.getModel().updateSelectionForCoordinates(
                            getInverseScaledSize(e.x - mDx),
                            getInverseScaledSize(e.y - mDy));
                }
            }
        });

        // right sash is split into 2 parts: upper-right and lower-right
        // both are composites with borders, so that the horizontal divider can be highlighted by
        // the borders
        SashForm rightSash = new SashForm(baseSash, SWT.VERTICAL);

        // upper-right base contains the toolbar and the tree
        Composite upperRightBase = new Composite(rightSash, SWT.BORDER);
        upperRightBase.setLayout(new GridLayout(1, false));
        ToolBarManager toolBarManager = new ToolBarManager(SWT.FLAT);
        toolBarManager.add(mOpenFilesAction);
        toolBarManager.add(mExpandAllAction);
        toolBarManager.add(mScreenshotAction);
        toolBarManager.add(mToggleNafAction);
        toolBarManager.createControl(upperRightBase);

        mTreeViewer = new TreeViewer(upperRightBase, SWT.NONE);
        mTreeViewer.setContentProvider(new BasicTreeNodeContentProvider());
        // default LabelProvider uses toString() to generate text to display
        mTreeViewer.setLabelProvider(new LabelProvider());
        mTreeViewer.addSelectionChangedListener(new ISelectionChangedListener() {
            @Override
            public void selectionChanged(SelectionChangedEvent event) {
                if (event.getSelection().isEmpty()) {
                    UiAutomatorModel.getModel().setSelectedNode(null);
                } else if (event.getSelection() instanceof IStructuredSelection) {
                    IStructuredSelection selection = (IStructuredSelection) event.getSelection();
                    Object o = selection.toArray()[0];
                    if (o instanceof BasicTreeNode) {
                        UiAutomatorModel.getModel().setSelectedNode((BasicTreeNode)o);
                    }
                }
            }
        });
        Tree tree = mTreeViewer.getTree();
        tree.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
        // move focus so that it's not on tool bar (looks weird)
        tree.setFocus();

        // lower-right base contains the detail group
        Composite lowerRightBase = new Composite(rightSash, SWT.BORDER);
        lowerRightBase.setLayout(new FillLayout());
        Group grpNodeDetail = new Group(lowerRightBase, SWT.NONE);
        grpNodeDetail.setLayout(new FillLayout(SWT.HORIZONTAL));
        grpNodeDetail.setText("Node Detail");

        Composite tableContainer = new Composite(grpNodeDetail, SWT.NONE);

        TableColumnLayout columnLayout = new TableColumnLayout();
        tableContainer.setLayout(columnLayout);

        mTableViewer = new TableViewer(tableContainer, SWT.NONE | SWT.FULL_SELECTION);
        Table table = mTableViewer.getTable();
        table.setLinesVisible(true);
        // use ArrayContentProvider here, it assumes the input to the TableViewer
        // is an array, where each element represents a row in the table
        mTableViewer.setContentProvider(new ArrayContentProvider());

        TableViewerColumn tableViewerColumnKey = new TableViewerColumn(mTableViewer, SWT.NONE);
        TableColumn tblclmnKey = tableViewerColumnKey.getColumn();
        tableViewerColumnKey.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                if (element instanceof AttributePair) {
                    // first column, shows the attribute name
                    return ((AttributePair)element).key;
                }
                return super.getText(element);
            }
        });
        columnLayout.setColumnData(tblclmnKey,
                new ColumnWeightData(1, ColumnWeightData.MINIMUM_WIDTH, true));

        TableViewerColumn tableViewerColumnValue = new TableViewerColumn(mTableViewer, SWT.NONE);
        tableViewerColumnValue.setEditingSupport(new AttributeTableEditingSupport(mTableViewer));
        TableColumn tblclmnValue = tableViewerColumnValue.getColumn();
        columnLayout.setColumnData(tblclmnValue,
                new ColumnWeightData(2, ColumnWeightData.MINIMUM_WIDTH, true));
        tableViewerColumnValue.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                if (element instanceof AttributePair) {
                    // second column, shows the attribute value
                    return ((AttributePair)element).value;
                }
                return super.getText(element);
            }
        });
        // sets the ratio of the vertical split: left 5 vs right 3
        baseSash.setWeights(new int[]{5, 3});
        return baseSash;
    }

    /**
     * Create the actions.
     */
    private void createActions() {
        mOpenFilesAction = new OpenFilesAction(this);
        mExpandAllAction = new ExpandAllAction(this);
        mScreenshotAction = new ScreenshotAction(this);
        mToggleNafAction = new ToggleNafAction();
    }

    /**
     * Launch the application.
     *
     * @param args
     */
    public static void main(String args[]) {
        try {
            UiAutomatorViewer window = new UiAutomatorViewer();
            window.setBlockOnOpen(true);
            window.open();
            UiAutomatorModel.getModel().cleanUp();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Configure the shell.
     *
     * @param newShell
     */
    @Override
    protected void configureShell(Shell newShell) {
        super.configureShell(newShell);
        newShell.setText("UI Automator Viewer");
    }


    /**
     * Asks the Model for screenshot and xml tree data, then populates the screenshot
     * area and tree view accordingly
     */
    public void loadScreenshotAndXml() {
        mScreenshotCanvas.redraw();
        // load xml into tree
        BasicTreeNode wrapper = new BasicTreeNode();
        // putting another root node on top of existing root node
        // because Tree seems to like to hide the root node
        wrapper.addChild(UiAutomatorModel.getModel().getXmlRootNode());
        mTreeViewer.setInput(wrapper);
        mTreeViewer.getTree().setFocus();
    }

    /*
     * Causes a redraw of the canvas.
     *
     * The drawing code of canvas will handle highlighted nodes and etc based on data
     * retrieved from Model
     */
    public void updateScreenshot() {
        mScreenshotCanvas.redraw();
    }

    public void expandAll() {
        mTreeViewer.expandAll();
    }

    public void updateTreeSelection(BasicTreeNode node) {
        mTreeViewer.setSelection(new StructuredSelection(node), true);
    }

    public void loadAttributeTable() {
        // udpate the lower right corner table to show the attributes of the node
        mTableViewer.setInput(
                UiAutomatorModel.getModel().getSelectedNode().getAttributesArray());
    }

    @Override
    protected Point getInitialSize() {
        return new Point(800, 600);
    }

    private void updateScreenshotTransformation() {
        Rectangle canvas = mScreenshotCanvas.getBounds();
        Rectangle image = UiAutomatorModel.getModel().getScreenshot().getBounds();
        float scaleX = (canvas.width - 2 * IMG_BORDER - 1) / (float)image.width;
        float scaleY = (canvas.height - 2 * IMG_BORDER - 1) / (float)image.height;
        // use the smaller scale here so that we can fit the entire screenshot
        mScale = Math.min(scaleX, scaleY);
        // calculate translation values to center the image on the canvas
        mDx = (canvas.width - getScaledSize(image.width) - IMG_BORDER * 2) / 2 + IMG_BORDER;
        mDy = (canvas.height - getScaledSize(image.height) - IMG_BORDER * 2) / 2 + IMG_BORDER;
    }

    private int getScaledSize(int size) {
        if (mScale == 1.0f) {
            return size;
        } else {
            return new Double(Math.floor((size * mScale))).intValue();
        }
    }

    private int getInverseScaledSize(int size) {
        if (mScale == 1.0f) {
            return size;
        } else {
            return new Double(Math.floor((size / mScale))).intValue();
        }
    }

    private class AttributeTableEditingSupport extends EditingSupport {

        private TableViewer mViewer;

        public AttributeTableEditingSupport(TableViewer viewer) {
            super(viewer);
            mViewer = viewer;
        }

        @Override
        protected boolean canEdit(Object arg0) {
            return true;
        }

        @Override
        protected CellEditor getCellEditor(Object arg0) {
            return new TextCellEditor(mViewer.getTable());
        }

        @Override
        protected Object getValue(Object o) {
            return ((AttributePair)o).value;
        }

        @Override
        protected void setValue(Object arg0, Object arg1) {
        }

    }
}
