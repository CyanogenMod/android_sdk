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

/** A segment type describes the different roles or positions a segment can have in a node */
public enum SegmentType {
    LEFT, TOP, RIGHT, BOTTOM, BASELINE, CENTER_VERTICAL, CENTER_HORIZONTAL, UNKNOWN;

    public boolean isHorizontal() {
        return this == TOP || this == BOTTOM || this == BASELINE || this == CENTER_HORIZONTAL;
    }

    /**
     * Returns the X coordinate for an edge of this type given its bounds
     *
     * @param node the node containing the edge
     * @param bounds the bounds of the node
     * @return the X coordinate for an edge of this type given its bounds
     */
    public int getX(INode node, Rect bounds) {
        // We pass in the bounds rather than look it up via node.getBounds() because
        // during a resize or move operation, we call this method to look up proposed
        // bounds rather than actual bounds
        switch (this) {
            case RIGHT:
                return bounds.x + bounds.w;
            case TOP:
            case BOTTOM:
            case CENTER_VERTICAL:
                return bounds.x + bounds.w / 2;
            case UNKNOWN:
                assert false;
                return bounds.x;
            case LEFT:
            case BASELINE:
            default:
                return bounds.x;
        }
    }

    /**
     * Returns the Y coordinate for an edge of this type given its bounds
     *
     * @param node the node containing the edge
     * @param bounds the bounds of the node
     * @return the Y coordinate for an edge of this type given its bounds
     */
    public int getY(INode node, Rect bounds) {
        switch (this) {
            case TOP:
                return bounds.y;
            case BOTTOM:
                return bounds.y + bounds.h;
            case BASELINE: {
                int baseline = node != null ? node.getBaseline() : -1;
                if (node == null) {
                    // This happens when you are dragging an element and we don't have
                    // a node (only an IDragElement) such as on a palette drag.
                    // For now just hack it.
                    baseline = (int) (bounds.h * 0.8f); // HACK
                }
                return bounds.y + baseline;
            }
            case UNKNOWN:
                assert false;
                return bounds.y;
            case RIGHT:
            case LEFT:
            case CENTER_HORIZONTAL:
            default:
                return bounds.y + bounds.h / 2;
        }
    }

    @Override
    public String toString() {
        return name();
    }
}
