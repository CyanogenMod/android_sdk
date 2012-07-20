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

import static com.android.tools.lint.detector.api.LintUtils.getNextOpcode;
import static com.android.tools.lint.detector.api.LintUtils.getPrevOpcode;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Arrays;
import java.util.List;

/**
 * Looks for usages of {@link java.lang.Math} methods which can be replaced with
 * {@code android.util.FloatMath} methods to avoid casting.
 */
public class MathDetector extends Detector implements Detector.ClassScanner {
    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "FloatMath", //$NON-NLS-1$
            "Suggests replacing java.lang.Math calls with android.util.FloatMath to " +
            "avoid conversions",

            "On modern hardware, \"double\" is just as fast as \"float\" though of course " +
            "it takes more memory. However, if you are using floats and you need to compute " +
            "the sine, cosine or square root, then it is better to use the " +
            "android.util.FloatMath class instead of java.lang.Math since you can call methods " +
            "written to operate on floats, so you avoid conversions back and forth to double.",

            Category.PERFORMANCE,
            3,
            Severity.WARNING,
            MathDetector.class,
            Scope.CLASS_FILE_SCOPE).setMoreInfo(
               //"http://developer.android.com/reference/android/util/FloatMath.html"); //$NON-NLS-1$
               "http://developer.android.com/guide/practices/design/performance.html#avoidfloat"); //$NON-NLS-1$

    /** Constructs a new {@link MathDetector} check */
    public MathDetector() {
    }

    @Override
    public @NonNull Speed getSpeed() {
        return Speed.FAST;
    }

    // ---- Implements ClassScanner ----

    @Override
    @Nullable
    public List<String> getApplicableCallNames() {
        return Arrays.asList(
                "sin",   //$NON-NLS-1$
                "cos",   //$NON-NLS-1$
                "ceil",  //$NON-NLS-1$
                "sqrt",  //$NON-NLS-1$
                "floor"  //$NON-NLS-1$
        );
    }

    @Override
    public void checkCall(@NonNull ClassContext context, @NonNull ClassNode classNode,
            @NonNull MethodNode method, @NonNull MethodInsnNode call) {
        String owner = call.owner;

        if (owner.equals("java/lang/Math")) { //$NON-NLS-1$
            String name = call.name;
            boolean paramFromFloat = getPrevOpcode(call) == Opcodes.F2D;
            boolean returnToFloat = getNextOpcode(call) == Opcodes.D2F;
            if (paramFromFloat || returnToFloat) {
                String message;
                if (paramFromFloat) {
                    message = String.format(
                            "Use android.util.FloatMath#%1$s() instead of " +
                            "java.lang.Math#%1$s to avoid argument float to " +
                            "double conversion", name);
                } else {
                    message = String.format(
                            "Use android.util.FloatMath#%1$s() instead of " +
                            "java.lang.Math#%1$s to avoid double to float return " +
                            "value conversion", name);
                }
                context.report(ISSUE, method, context.getLocation(call), message, null /*data*/);
            }
        }
    }
}
