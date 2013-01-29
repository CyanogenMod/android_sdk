/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.hierarchyviewerlib.ui;

import com.android.ddmuilib.ImageLoader;
import com.android.hierarchyviewerlib.HierarchyViewerDirector;
import com.android.hierarchyviewerlib.device.IHvDevice;
import com.android.hierarchyviewerlib.models.TreeViewModel;
import com.android.hierarchyviewerlib.models.TreeViewModel.ITreeChangeListener;
import com.android.hierarchyviewerlib.models.ViewNode;
import com.android.hierarchyviewerlib.models.ViewNode.Property;
import com.android.hierarchyviewerlib.ui.DevicePropertyEditingSupport.PropertyType;
import com.android.hierarchyviewerlib.ui.util.DrawableViewNode;
import com.android.hierarchyviewerlib.ui.util.TreeColumnResizer;

import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnViewer;
import org.eclipse.jface.viewers.ComboBoxCellEditor;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;

import java.util.ArrayList;
import java.util.Collection;

public class PropertyViewer extends Composite implements ITreeChangeListener {
    private TreeViewModel mModel;

    private TreeViewer mTreeViewer;
    private Tree mTree;
    private TreeViewerColumn mValueColumn;
    private PropertyValueEditingSupport mPropertyValueEditingSupport;

    private Image mImage;

    private DrawableViewNode mSelectedNode;

    private class ContentProvider implements ITreeContentProvider, ITableLabelProvider {

        @Override
        public Object[] getChildren(Object parentElement) {
            synchronized (PropertyViewer.this) {
                if (mSelectedNode != null && parentElement instanceof String) {
                    String category = (String) parentElement;
                    ArrayList<Property> returnValue = new ArrayList<Property>();
                    for (Property property : mSelectedNode.viewNode.properties) {
                        if (category.equals(ViewNode.MISCELLANIOUS)) {
                            if (property.name.indexOf(':') == -1) {
                                returnValue.add(property);
                            }
                        } else {
                            if (property.name.startsWith(((String) parentElement) + ":")) {
                                returnValue.add(property);
                            }
                        }
                    }
                    return returnValue.toArray(new Property[returnValue.size()]);
                }
                return new Object[0];
            }
        }

        @Override
        public Object getParent(Object element) {
            synchronized (PropertyViewer.this) {
                if (mSelectedNode != null && element instanceof Property) {
                    if (mSelectedNode.viewNode.categories.size() == 0) {
                        return null;
                    }
                    String name = ((Property) element).name;
                    int index = name.indexOf(':');
                    if (index == -1) {
                        return ViewNode.MISCELLANIOUS;
                    }
                    return name.substring(0, index);
                }
                return null;
            }
        }

        @Override
        public boolean hasChildren(Object element) {
            synchronized (PropertyViewer.this) {
                if (mSelectedNode != null && element instanceof String) {
                    String category = (String) element;
                    for (String name : mSelectedNode.viewNode.namedProperties.keySet()) {
                        if (category.equals(ViewNode.MISCELLANIOUS)) {
                            if (name.indexOf(':') == -1) {
                                return true;
                            }
                        } else {
                            if (name.startsWith(((String) element) + ":")) {
                                return true;
                            }
                        }
                    }
                }
                return false;
            }
        }

        @Override
        public Object[] getElements(Object inputElement) {
            synchronized (PropertyViewer.this) {
                if (mSelectedNode != null && inputElement instanceof TreeViewModel) {
                    if (mSelectedNode.viewNode.categories.size() == 0) {
                        return mSelectedNode.viewNode.properties
                                .toArray(new Property[mSelectedNode.viewNode.properties.size()]);
                    } else {
                        return mSelectedNode.viewNode.categories
                                .toArray(new String[mSelectedNode.viewNode.categories.size()]);
                    }
                }
                return new Object[0];
            }
        }

        @Override
        public void dispose() {
            // pass
        }

        @Override
        public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
            // pass
        }

        @Override
        public Image getColumnImage(Object element, int column) {
            if (mSelectedNode == null) {
                return null;
            }
            if (column == 1 && mPropertyValueEditingSupport.canEdit(element)) {
                return mImage;
            }

            return null;
        }

        @Override
        public String getColumnText(Object element, int column) {
            synchronized (PropertyViewer.this) {
                if (mSelectedNode != null) {
                    if (element instanceof String && column == 0) {
                        String category = (String) element;
                        return Character.toUpperCase(category.charAt(0)) + category.substring(1);
                    } else if (element instanceof Property) {
                        if (column == 0) {
                            String returnValue = ((Property) element).name;
                            int index = returnValue.indexOf(':');
                            if (index != -1) {
                                return returnValue.substring(index + 1);
                            }
                            return returnValue;
                        } else if (column == 1) {
                            return ((Property) element).value;
                        }
                    }
                }
                return "";
            }
        }

        @Override
        public void addListener(ILabelProviderListener listener) {
            // pass
        }

        @Override
        public boolean isLabelProperty(Object element, String property) {
            // pass
            return false;
        }

        @Override
        public void removeListener(ILabelProviderListener listener) {
            // pass
        }
    }

    private class PropertyValueEditingSupport extends EditingSupport {
        private DevicePropertyEditingSupport mDevicePropertyEditingSupport =
                new DevicePropertyEditingSupport();

        public PropertyValueEditingSupport(ColumnViewer viewer) {
            super(viewer);
        }

        @Override
        protected boolean canEdit(Object element) {
            if (mSelectedNode == null) {
                return false;
            }

            return element instanceof Property
                    && mSelectedNode.viewNode.window.getHvDevice().isViewUpdateEnabled()
                    && mDevicePropertyEditingSupport.canEdit((Property) element);
        }

        @Override
        protected CellEditor getCellEditor(Object element) {
            Property p = (Property) element;
            PropertyType type = mDevicePropertyEditingSupport.getPropertyType(p);
            Composite parent = (Composite) getViewer().getControl();

            switch (type) {
                case INTEGER:
                case INTEGER_OR_CONSTANT:
                    return new TextCellEditor(parent);
                case ENUM:
                    String[] items = mDevicePropertyEditingSupport.getPropertyRange(p);
                    return new ComboBoxCellEditor(parent, items, SWT.READ_ONLY);
            }

            return null;
        }

        @Override
        protected Object getValue(Object element) {
            Property p = (Property) element;
            PropertyType type = mDevicePropertyEditingSupport.getPropertyType(p);

            if (type == PropertyType.ENUM) {
                // for enums, return the index of the current value in the list of possible values
                String[] items = mDevicePropertyEditingSupport.getPropertyRange(p);
                return Integer.valueOf(indexOf(p.value, items));
            }

            return ((Property) element).value;
        }

        private int indexOf(String item, String[] items) {
            for (int i = 0; i < items.length; i++) {
                if (items[i].equals(item)) {
                    return i;
                }
            }

            return -1;
        }

        @Override
        protected void setValue(Object element, Object newValue) {
            Property p = (Property) element;
            IHvDevice device = mSelectedNode.viewNode.window.getHvDevice();
            Collection<Property> properties = mSelectedNode.viewNode.namedProperties.values();
            if (mDevicePropertyEditingSupport.setValue(properties, p, newValue,
                    mSelectedNode.viewNode, device)) {
                doRefresh();
            }
        }
    }

    public PropertyViewer(Composite parent) {
        super(parent, SWT.NONE);
        setLayout(new FillLayout());
        mTreeViewer = new TreeViewer(this, SWT.NONE);

        mTree = mTreeViewer.getTree();
        mTree.setLinesVisible(true);
        mTree.setHeaderVisible(true);

        TreeColumn propertyColumn = new TreeColumn(mTree, SWT.NONE);
        propertyColumn.setText("Property");
        TreeColumn valueColumn = new TreeColumn(mTree, SWT.NONE);
        valueColumn.setText("Value");

        mValueColumn = new TreeViewerColumn(mTreeViewer, valueColumn);
        mPropertyValueEditingSupport = new PropertyValueEditingSupport(mTreeViewer);
        mValueColumn.setEditingSupport(mPropertyValueEditingSupport);

        mModel = TreeViewModel.getModel();
        ContentProvider contentProvider = new ContentProvider();
        mTreeViewer.setContentProvider(contentProvider);
        mTreeViewer.setLabelProvider(contentProvider);
        mTreeViewer.setInput(mModel);
        mModel.addTreeChangeListener(this);

        addDisposeListener(mDisposeListener);

        @SuppressWarnings("unused")
        TreeColumnResizer resizer = new TreeColumnResizer(this, propertyColumn, valueColumn);

        addControlListener(mControlListener);

        ImageLoader imageLoader = ImageLoader.getLoader(HierarchyViewerDirector.class);
        mImage = imageLoader.loadImage("picker.png", Display.getDefault()); //$NON-NLS-1$

        treeChanged();
    }

    private DisposeListener mDisposeListener = new DisposeListener() {
        @Override
        public void widgetDisposed(DisposeEvent e) {
            mModel.removeTreeChangeListener(PropertyViewer.this);
        }
    };

    // If the window gets too small, hide the data, otherwise SWT throws an
    // ERROR.

    private ControlListener mControlListener = new ControlAdapter() {
        private boolean noInput = false;

        private boolean noHeader = false;

        @Override
        public void controlResized(ControlEvent e) {
            if (getBounds().height <= 20) {
                mTree.setHeaderVisible(false);
                noHeader = true;
            } else if (noHeader) {
                mTree.setHeaderVisible(true);
                noHeader = false;
            }
            if (getBounds().height <= 38) {
                mTreeViewer.setInput(null);
                noInput = true;
            } else if (noInput) {
                mTreeViewer.setInput(mModel);
                noInput = false;
            }
        }
    };

    @Override
    public void selectionChanged() {
        synchronized (this) {
            mSelectedNode = mModel.getSelection();
        }
        doRefresh();
    }

    @Override
    public void treeChanged() {
        synchronized (this) {
            mSelectedNode = mModel.getSelection();
        }
        doRefresh();
    }

    @Override
    public void viewportChanged() {
        // pass
    }

    @Override
    public void zoomChanged() {
        // pass
    }

    private void doRefresh() {
        Display.getDefault().syncExec(new Runnable() {
            @Override
            public void run() {
                mTreeViewer.refresh();
            }
        });
    }
}
