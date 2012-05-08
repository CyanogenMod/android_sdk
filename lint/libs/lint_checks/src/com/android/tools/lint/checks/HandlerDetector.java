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

package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.ClassScanner;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.util.EnumSet;
import java.util.List;

/**
 * Checks that Handler implementations are top level classes or static.
 * See the corresponding check in the android.os.Handler source code.
 */
public class HandlerDetector extends Detector implements ClassScanner {

    /** Potentially leaking handlers */
    public static final Issue ISSUE = Issue.create(
        "HandlerLeak", //$NON-NLS-1$
        "Ensures that Handler classes do not hold on to a reference to an outer class",

        "In Android, Handler classes should be static or leaks might occur. " +
        "Messages enqueued on the application thread's MessageQueue also retain their " +
        "target Handler. If the Handler is an inner class, its outer class will be " +
        "retained as well. To avoid leaking the outer class, declare the Handler as a " +
        "static nested class with a WeakReference to its outer class.",

        Category.PERFORMANCE,
        4,
        Severity.WARNING,
        HandlerDetector.class,
        EnumSet.of(Scope.CLASS_FILE));

    /** Constructs a new {@link HandlerDetector} */
    public HandlerDetector() {
    }

    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }

    @Override
    public boolean appliesTo(Context context, File file) {
        return true;
    }

    // ---- Implements ClassScanner ----

    @Override
    public void checkClass(ClassContext context, ClassNode classNode) {
        if (classNode.name.indexOf('$') == -1) {
            return;
        }

        // Note: We can't just filter out static inner classes like this:
        //     (classNode.access & Opcodes.ACC_STATIC) != 0
        // because the static flag only appears on methods and fields in the class
        // file. Instead, look for the synthetic this pointer.

        LintDriver driver = context.getDriver();
        String name = classNode.name;
        while (name != null) {
            if (name.equals("android/os/Handler")) { //$NON-NLS-1$
                if (isStaticInnerClass(classNode)) {
                    return;
                }

                // Attempt to find a proper location for this class. This is tricky
                // since classes do not have line number entries in the class file; we need
                // to find a method, look up the corresponding line number then search
                // around it for a suitable tag, such as the class name.
                String pattern;
                if (isAnonymousClass(classNode.name)) {
                    pattern = "Handler"; //$NON-NLS-1$
                } else {
                    pattern = classNode.name.substring(classNode.name.lastIndexOf('$') + 1);
                }

                int firstLineNo = -1;
                if (classNode.methods != null && !classNode.methods.isEmpty()) {
                    MethodNode firstMethod = getFirstRealMethod(classNode);
                    if (firstMethod != null) {
                        firstLineNo = ClassContext.findLineNumber(firstMethod);
                    }
                }

                Location location = context.getLocationForLine(firstLineNo, pattern, null);
                context.report(ISSUE, location, String.format(
                        "This Handler class should be static or leaks might occur (%1$s)",
                            ClassContext.createSignature(classNode.name, null, null)),
                        null);
                return;
            }
            name = driver.getSuperClass(name);
        }
    }

    @Nullable
    private MethodNode getFirstRealMethod(@NonNull ClassNode classNode) {
        // Return the first method in the class for line number purposes. Skip <init>,
        // since it's typically not located near the real source of the method.
        if (classNode.methods != null) {
            @SuppressWarnings("rawtypes") // ASM API
            List methods = classNode.methods;
            for (Object m : methods) {
                MethodNode method = (MethodNode) m;
                if (method.name.charAt(0) != '<') {
                    return method;
                }
            }

            if (classNode.methods.size() > 0) {
                return (MethodNode) classNode.methods.get(0);
            }
        }

        return null;
    }

    private boolean isStaticInnerClass(@NonNull ClassNode classNode) {
        @SuppressWarnings("rawtypes") // ASM API
        List fieldList = classNode.fields;
        for (Object f : fieldList) {
            FieldNode field = (FieldNode) f;
            if (field.name.startsWith("this$") && (field.access & Opcodes.ACC_SYNTHETIC) != 0) {
                return false;
            }
        }

        return true;
    }

    private boolean isAnonymousClass(@NonNull String fqcn) {
        int lastIndex = fqcn.lastIndexOf('$');
        if (lastIndex != -1 && lastIndex < fqcn.length() - 1) {
            if (Character.isDigit(fqcn.charAt(lastIndex + 1))) {
                return true;
            }
        }
        return false;
    }
}
