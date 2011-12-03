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

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

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
            EnumSet.of(Scope.CLASS_FILE)).setMoreInfo(
               //"http://developer.android.com/reference/android/util/FloatMath.html"); //$NON-NLS-1$
               "http://developer.android.com/guide/practices/design/performance.html#avoidfloat"); //$NON-NLS-1$

    /** Constructs a new accessibility check */
    public MathDetector() {
    }

    @Override
    public boolean appliesTo(Context context, File file) {
        return true;
    }

    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }

    // ---- Implements ClassScanner ----

    @Override
    public void checkClass(ClassContext context, ClassNode classNode) {
        classNode.accept(new MathCallFinder(context));
    }

    /** Methods on java.lang.Math that we want to find and suggest replacements for */
    private static final Set<String> sFloatMethods = new HashSet<String>();
    static {
        sFloatMethods.add("sin");   //$NON-NLS-1$
        sFloatMethods.add("cos");   //$NON-NLS-1$
        sFloatMethods.add("ceil");  //$NON-NLS-1$
        sFloatMethods.add("sqrt");  //$NON-NLS-1$
        sFloatMethods.add("floor"); //$NON-NLS-1$
    }

    private static class MathCallFinder extends ClassVisitor {
        private final ClassContext mContext;
        private String mCurrentSource;

        public MathCallFinder(ClassContext context) {
            super(Opcodes.ASM4);
            mContext = context;
        }

        @Override
        public void visitSource(String source, String debug) {
            mCurrentSource = source;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                String[] exceptions) {
            return new MyMethodVisitor(mContext, mCurrentSource);
        }
    }

    private static class MyMethodVisitor extends MethodVisitor {
        private final ClassContext mContext;
        private int mCurrentLine;
        private String mCurrentSource;
        private int mLastInsn;
        private String mPendingMethod;

        public MyMethodVisitor(ClassContext context, String currentSource) {
            super(Opcodes.ASM4);
            mContext = context;
            mCurrentSource = currentSource;
        }

        private Location getCurrentLocation() {
            // Make utility method!
            File file = mContext.file; // The binary .class file. Try to find source instead.
            int line = 0;

            // Determine package
            File source = mContext.findSourceFile(mCurrentSource);
            if (source != null) {
                file = source;
                line = mCurrentLine;

                if (line > 0) {
                    String contents = mContext.getClient().readFile(file);
                    if (contents != null) {
                        // bytecode line numbers are 1-based
                        return Location.create(file, contents, line - 1);
                    }
                }
            }

            return Location.create(file);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            if (sFloatMethods.contains(name) && owner.equals("java/lang/Math")) { //$NON-NLS-1$
                if (mLastInsn != Opcodes.F2D) {
                    mPendingMethod = name;
                    return;
                }
                String message = String.format(
                        "Use android.util.FloatMath#%1$s() instead of java.lang.Math#%1$s to " +
                        "avoid argument float to double conversion", name);
                mContext.report(ISSUE, getCurrentLocation(), message, null /*data*/);
            }
        }

        @Override
        public void visitInsn(int opcode) {
            if (opcode == Opcodes.D2F) {
                if (mPendingMethod != null) {
                    String message = String.format(
                            "Use android.util.FloatMath#%1$s() instead of java.lang.Math#%1$s " +
                            "to avoid double to float return value conversion", mPendingMethod);
                    mContext.report(ISSUE, getCurrentLocation(), message, null /*data*/);

                }
                mPendingMethod = null;
            }
            mLastInsn = opcode;
        }

        @Override
        public void visitLineNumber(int line, Label start) {
            mCurrentLine = line;
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            mLastInsn = opcode;
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            mLastInsn = opcode;
        }

        @Override
        public void visitJumpInsn(int opcode, Label label) {
            mLastInsn = opcode;
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            mLastInsn = opcode;
        }

        @Override
        public void visitVarInsn(int opcode, int var) {
            mLastInsn = opcode;
        }
    }
}
