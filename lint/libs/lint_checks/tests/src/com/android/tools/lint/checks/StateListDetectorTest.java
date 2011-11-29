/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Detector;

@SuppressWarnings("javadoc")
public class StateListDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new StateListDetector();
    }

    public void testStates() throws Exception {
        assertEquals(
                "states.xml:2: Warning: No android:state_ attribute found on <item> 0, " +
                        "later states not reachable",
                lintProject("res/drawable/states.xml"));
    }

    public void testCustomStates() throws Exception {
        assertEquals(
                "No warnings.",
                lintProject("res/drawable/states2.xml"));
    }
}
