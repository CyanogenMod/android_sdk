/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.ide.eclipse.adt.internal.lint;

import static com.android.ide.common.layout.LayoutConstants.ATTR_CONTENT_DESCRIPTION;
import static com.android.ide.common.layout.LayoutConstants.ATTR_INPUT_TYPE;
import static com.android.ide.common.layout.LayoutConstants.VALUE_FALSE;

import com.android.tools.lint.checks.AccessibilityDetector;
import com.android.tools.lint.checks.InefficientWeightDetector;
import com.android.tools.lint.checks.SecurityDetector;
import com.android.tools.lint.checks.TextFieldDetector;
import com.android.tools.lint.detector.api.LintConstants;

import org.eclipse.core.resources.IMarker;

/** Shared fix class for various builtin attributes */
@SuppressWarnings("restriction") // DOM model
final class SetAttributeFix extends SetPropertyFix {
    private SetAttributeFix(String id, IMarker marker) {
        super(id, marker);
    }

    @Override
    protected String getAttribute() {
        if (mId.equals(AccessibilityDetector.ISSUE.getId())) {
            return ATTR_CONTENT_DESCRIPTION;
        } else if (mId.equals(InefficientWeightDetector.BASELINE_WEIGHTS.getId())) {
            return LintConstants.ATTR_BASELINE_ALIGNED;
        } else if (mId.equals(SecurityDetector.EXPORTED_SERVICE.getId())) {
            return LintConstants.ATTR_PERMISSION;
        } else if (mId.equals(TextFieldDetector.ISSUE.getId())) {
            return ATTR_INPUT_TYPE;
        } else {
            assert false : mId;
            return "";
        }
    }

    @Override
    public String getDisplayString() {
        if (mId.equals(AccessibilityDetector.ISSUE.getId())) {
            return "Add content description attribute";
        } else if (mId.equals(InefficientWeightDetector.BASELINE_WEIGHTS.getId())) {
            return "Set baseline attribute";
        } else if (mId.equals(TextFieldDetector.ISSUE.getId())) {
            return "Set input type";
        } else if (mId.equals(SecurityDetector.EXPORTED_SERVICE.getId())) {
            return "Add permission attribute";
        } else {
            assert false : mId;
            return "";
        }
    }

    @Override
    protected boolean invokeCodeCompletion() {
        return mId.equals(SecurityDetector.EXPORTED_SERVICE.getId())
                || mId.equals(TextFieldDetector.ISSUE.getId());
    }

    @Override
    protected String getProposal() {
        if (mId.equals(InefficientWeightDetector.BASELINE_WEIGHTS.getId())) {
            return VALUE_FALSE;
        }

        return super.getProposal();
    }

}