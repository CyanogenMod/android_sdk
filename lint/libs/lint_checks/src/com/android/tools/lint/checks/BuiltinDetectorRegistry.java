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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Registry which provides a list of checks to be performed on an Android project */
public class BuiltinDetectorRegistry extends com.android.tools.lint.api.DetectorRegistry {
    private static final List<Detector> sDetectors;
    static {
        // TODO: Maybe just store class names here instead such that
        // each invocation can have its own context with fields without
        // worrying about having to clear state in beforeCheckProject or beforeCheckFile?
        List<Detector> detectors = new ArrayList<Detector>();
        detectors.add(new AccessibilityDetector());
        detectors.add(new DuplicateIdDetector());
        detectors.add(new StateListDetector());
        detectors.add(new InefficientWeightDetector());
        detectors.add(new ScrollViewChildDetector());
        detectors.add(new MergeRootFrameLayoutDetector());
        detectors.add(new NestedScrollingWidgetDetector());
        detectors.add(new ChildCountDetector());
        detectors.add(new UseCompoundDrawableDetector());
        detectors.add(new UselessViewDetector());
        detectors.add(new TooManyViewsDetector());
        detectors.add(new GridLayoutDetector());
        detectors.add(new TranslationDetector());
        detectors.add(new HardcodedValuesDetector());
        detectors.add(new ProguardDetector());
        detectors.add(new PxUsageDetector());
        detectors.add(new TextFieldDetector());


        // TODO: Populate dynamically somehow?

        sDetectors = Collections.unmodifiableList(detectors);
    }

    /**
     * Constructs a new {@link BuiltinDetectorRegistry}
     */
    public BuiltinDetectorRegistry() {
    }

    @Override
    public List<? extends Detector> getDetectors() {
        return sDetectors;
    }
}
