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
package com.android.ide.common.layout;

import static com.android.ide.common.layout.GravityHelper.GRAVITY_BOTTOM;
import static com.android.ide.common.layout.GravityHelper.GRAVITY_CENTER_HORIZ;
import static com.android.ide.common.layout.GravityHelper.GRAVITY_CENTER_VERT;
import static com.android.ide.common.layout.GravityHelper.GRAVITY_LEFT;
import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public class GravityHelperTest extends TestCase {
    public void testGravity() throws Exception {
        assertEquals(GRAVITY_BOTTOM, GravityHelper.getGravity("bottom", 0));
        assertEquals(GRAVITY_BOTTOM | GRAVITY_LEFT, GravityHelper.getGravity("bottom|left", 0));
        assertEquals(GRAVITY_CENTER_HORIZ | GRAVITY_CENTER_VERT,
                GravityHelper.getGravity("center", 0));
    }
}
