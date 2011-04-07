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

import com.android.ide.common.api.DrawingStyle;
import com.android.ide.common.api.IGraphics;
import com.android.ide.common.api.INode;
import com.android.ide.common.api.Rect;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.NodeProxy;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.RulesEngine;

import org.eclipse.swt.graphics.GC;

import java.util.List;

/**
 * The {@link SelectionOverlay} paints the current selection as an overlay.
 */
public class SelectionOverlay extends Overlay {
    private final LayoutCanvas mCanvas;

    /**
     * Constructs a new {@link SelectionOverlay} tied to the given canvas.
     *
     * @param canvas the associated canvas
     */
    public SelectionOverlay(LayoutCanvas canvas) {
        mCanvas = canvas;
    }

    /**
     * Paints the selection.
     *
     * @param selectionManager The {@link SelectionManager} holding the
     *            selection.
     * @param gcWrapper The graphics context wrapper for the layout rules to use.
     * @param gc The SWT graphics object
     * @param rulesEngine The {@link RulesEngine} holding the rules.
     */
    public void paint(SelectionManager selectionManager, GCWrapper gcWrapper,
            GC gc, RulesEngine rulesEngine) {
        List<SelectionItem> selections = selectionManager.getSelections();
        int n = selections.size();
        if (n > 0) {
            boolean isMultipleSelection = n > 1;
            for (SelectionItem s : selections) {
                if (s.isRoot()) {
                    // The root selection is never painted
                    continue;
                }

                NodeProxy node = s.getNode();
                if (node != null) {
                    paintSelection(gcWrapper, gc, s, isMultipleSelection);
                }
            }

            if (n == 1) {
                NodeProxy node = selections.get(0).getNode();
                if (node != null) {
                    paintHints(gcWrapper, node, rulesEngine);
                }
            }
        }
    }

    /** Paint hint for current selection */
    private void paintHints(GCWrapper gcWrapper, NodeProxy node, RulesEngine rulesEngine) {
        INode parent = node.getParent();
        if (parent instanceof NodeProxy) {
            NodeProxy parentNode = (NodeProxy) parent;
            List<String> infos = rulesEngine.callGetSelectionHint(parentNode, node);
            if (infos != null && infos.size() > 0) {
                gcWrapper.useStyle(DrawingStyle.HELP);

                Rect b = mCanvas.getImageOverlay().getImageBounds();
                if (b == null) {
                    return;
                }

                // Compute the location to display the help. This is done in
                // layout coordinates, so we need to apply the scale in reverse
                // when making pixel margins
                // TODO: We could take the Canvas dimensions into account to see
                // where there is more room.
                // TODO: The scrollbars should take the presence of hint text
                // into account.
                double scale = mCanvas.getScale();
                int x, y;
                if (b.w > b.h) {
                    x = (int) (b.x + 3 / scale);
                    y = (int) (b.y + b.h + 6 / scale);
                } else {
                    x = (int) (b.x + b.w + 6 / scale);
                    y = (int) (b.y + 3 / scale);
                }
                gcWrapper.drawBoxedStrings(x, y, infos);
            }
        }
    }

    /** Called by the canvas when a view is being selected. */
    private void paintSelection(IGraphics gc, GC swtGc, SelectionItem item,
            boolean isMultipleSelection) {
        NodeProxy selectedNode = item.getNode();
        Rect r = selectedNode.getBounds();
        if (!r.isValid()) {
            return;
        }

        gc.useStyle(DrawingStyle.SELECTION);
        gc.drawRect(r);

        // Paint sibling rectangles, if applicable
        CanvasViewInfo view = item.getViewInfo();
        List<CanvasViewInfo> siblings = view.getNodeSiblings();
        if (siblings != null) {
            for (CanvasViewInfo sibling : siblings) {
                if (sibling != view) {
                    r = SwtUtils.toRect(sibling.getSelectionRect());
                    gc.fillRect(r);
                    gc.drawRect(r);
                }
            }
        }

        // Paint selection handles. These are painted in control coordinates on the
        // real SWT GC object rather than in layout coordinates on the GCWrapper,
        // since we want them to have a fixed size that is independent of the
        // screen zoom.
        CanvasTransform horizontalTransform = mCanvas.getHorizontalTransform();
        CanvasTransform verticalTransform = mCanvas.getVerticalTransform();
        int radius = SelectionHandle.PIXEL_RADIUS;
        int doubleRadius = 2 * radius;
        for (SelectionHandle handle : item.getSelectionHandles()) {
            int cx = horizontalTransform.translate(handle.centerX);
            int cy = verticalTransform.translate(handle.centerY);

            SwtDrawingStyle style = SwtDrawingStyle.of(DrawingStyle.SELECTION);
            gc.setAlpha(style.getStrokeAlpha());
            swtGc.fillRectangle(cx - radius, cy - radius, doubleRadius, doubleRadius);
        }
    }
}
