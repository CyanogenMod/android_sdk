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
package com.android.ide.common.layout.grid;

import static com.android.ide.common.layout.LayoutConstants.ANDROID_URI;
import static com.android.ide.common.layout.LayoutConstants.ATTR_COLUMN_COUNT;
import static com.android.ide.common.layout.LayoutConstants.FQCN_BUTTON;

import com.android.ide.common.api.Rect;
import com.android.ide.common.layout.LayoutTestBase;
import com.android.ide.common.layout.TestNode;


public class GridModelTest extends LayoutTestBase {
    public void testRemoveFlag() {
        assertEquals("left", GridModel.removeFlag("top", "top|left"));
        assertEquals("left", GridModel.removeFlag("top", "top | left"));
        assertEquals("top", GridModel.removeFlag("left", "top|left"));
        assertEquals("top", GridModel.removeFlag("left", "top | left"));
        assertEquals("left | center", GridModel.removeFlag("top", "top | left | center"));
        assertEquals(null, GridModel.removeFlag("top", "top"));
    }

    public void testReadModel1() {
        TestNode targetNode = TestNode.create("android.widget.GridLayout").id("@+id/GridLayout1")
                .bounds(new Rect(0, 0, 240, 480)).set(ANDROID_URI, ATTR_COLUMN_COUNT, "3");

        GridModel model = new GridModel(null, targetNode, null);
        assertEquals(3, model.declaredColumnCount);
        assertEquals(1, model.actualColumnCount);
        assertEquals(1, model.actualRowCount);

        targetNode.add(TestNode.create(FQCN_BUTTON).id("@+id/Button1"));
        targetNode.add(TestNode.create(FQCN_BUTTON).id("@+id/Button2"));
        targetNode.add(TestNode.create(FQCN_BUTTON).id("@+id/Button3"));
        targetNode.add(TestNode.create(FQCN_BUTTON).id("@+id/Button4"));

        model = new GridModel(null, targetNode, null);
        assertEquals(3, model.declaredColumnCount);
        assertEquals(3, model.actualColumnCount);
        assertEquals(2, model.actualRowCount);
    }
}
