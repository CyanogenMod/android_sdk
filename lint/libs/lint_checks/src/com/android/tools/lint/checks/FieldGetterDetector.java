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
import com.android.util.Pair;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Looks for getter calls within the same class that could be replaced by
 * direct field references instead.
 */
public class FieldGetterDetector extends Detector implements Detector.ClassScanner {
    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "FieldGetter", //$NON-NLS-1$
            "Suggests replacing uses of getters with direct field access within a class",

            "Accessing a field within the class that defines a getter for that field is " +
            "at least 3 times faster than calling the getter. For simple getters that do " +
            "nothing other than return the field, you might want to just reference the " +
            "local field directly instead.",

            Category.PERFORMANCE,
            4,
            Severity.WARNING,
            FieldGetterDetector.class,
            EnumSet.of(Scope.CLASS_FILE)).
            // This is a micro-optimization: not enabled by default
            setEnabledByDefault(false).setMoreInfo(
           "http://developer.android.com/guide/practices/design/performance.html#internal_get_set"); //$NON-NLS-1$

    /** Constructs a new accessibility check */
    public FieldGetterDetector() {
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
        classNode.accept(new Visitor(context));
    }

    private static class Visitor extends ClassVisitor {
        private final ClassContext mContext;
        private int mCurrentLine;
        private String mClass;
        private List<Pair<String, Integer>> mPendingCalls;

        public Visitor(ClassContext context) {
            super(Opcodes.ASM4);
            mContext = context;
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                String superName, String[] interfaces) {
            mClass = name;
        }

        @Override
        public void visitEnd() {
            if (mPendingCalls != null) {
                Set<String> names = new HashSet<String>(mPendingCalls.size());
                for (Pair<String, Integer> pair : mPendingCalls) {
                    String name = pair.getFirst();
                    names.add(name);
                }

                List<String> getters = checkMethods(mContext.getClassNode(), names);
                if (getters.size() > 0) {
                    File source = mContext.getSourceFile();
                    String contents = mContext.getSourceContents();
                    for (String getter : getters) {
                        for (Pair<String, Integer> pair : mPendingCalls) {
                            String name = pair.getFirst();
                            // There can be more than one reference to the same name:
                            // one for each call site
                            if (name.equals(getter)) {
                                Integer line = pair.getSecond();
                                Location location = null;
                                if (source != null) {
                                    // ASM line numbers are 1-based, Lint needs 0-based
                                    location = Location.create(source, contents, line - 1, name,
                                            null);
                                } else {
                                    location = Location.create(mContext.file);
                                }
                                mContext.report(ISSUE, location, String.format(
                                    "Calling getter method %1$s() on self is " +
                                    "slower than field access", getter), null);
                            }
                        }
                    }
                }
            }
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                String[] exceptions) {
            return new MethodVisitor(Opcodes.ASM4) {
                @SuppressWarnings("hiding")
                @Override
                public void visitMethodInsn(int opcode, String owner, String name, String desc) {
                    if (((name.startsWith("get") && name.length() > 3             //$NON-NLS-1$
                                    && Character.isUpperCase(name.charAt(3)))
                                || (name.startsWith("is") && name.length() > 2    //$NON-NLS-1$
                                    && Character.isUpperCase(name.charAt(2))))
                            && owner.equals(mClass)) {
                        // Calling a potential getter method on self. We now need to
                        // investigate the method body of the getter call and make sure
                        // it's really a plain getter, not just a method which happens
                        // to have a method name like a getter, or a method which not
                        // only returns a field but possibly computes it or performs
                        // other initialization or side effects. This is done in a
                        // second pass over the bytecode, initiated by the finish()
                        // method.
                        if (mPendingCalls == null) {
                            mPendingCalls = new ArrayList<Pair<String,Integer>>();
                        }
                        // Line numbers should be 0-based
                        mPendingCalls.add(Pair.of(name, mCurrentLine));
                    }
                }

                @Override
                public void visitLineNumber(int line, Label start) {
                    mCurrentLine = line;
                }
            };
        }
    }

    // Validate that these getter methods are really just simple field getters
    // like these int and STring getters:
    // public int getFoo();
    //   Code:
    //    0:   aload_0
    //    1:   getfield    #21; //Field mFoo:I
    //    4:   ireturn
    //
    // public java.lang.String getBar();
    //   Code:
    //    0:   aload_0
    //    1:   getfield    #25; //Field mBar:Ljava/lang/String;
    //    4:   areturn
    private static List<String> checkMethods(ClassNode classNode, Set<String> names) {
        List<String> validGetters = new ArrayList<String>();
        @SuppressWarnings("rawtypes")
        List methods = classNode.methods;
        checkMethod:
        for (Object methodObject : methods) {
            MethodNode method = (MethodNode) methodObject;
            if (names.contains(method.name)
                    && method.desc.startsWith("()")) { //$NON-NLS-1$ // (): No arguments
                InsnList instructions = method.instructions;
                int mState = 1;
                for (AbstractInsnNode curr = instructions.getFirst();
                        curr != null;
                        curr = curr.getNext()) {
                    switch (curr.getOpcode()) {
                        case -1:
                            // Skip label and line number nodes
                            continue;
                        case Opcodes.ALOAD:
                            if (mState == 1) {
                                mState = 2;
                            } else {
                                continue checkMethod;
                            }
                            break;
                        case Opcodes.GETFIELD:
                            if (mState == 2) {
                                mState = 3;
                            } else {
                                continue checkMethod;
                            }
                            break;
                        case Opcodes.ARETURN:
                        case Opcodes.FRETURN:
                        case Opcodes.IRETURN:
                        case Opcodes.DRETURN:
                        case Opcodes.LRETURN:
                        case Opcodes.RETURN:
                            validGetters.add(method.name);
                            continue checkMethod;
                        default:
                            continue checkMethod;
                    }
                    curr = curr.getNext();
                }
            }
        }

        return validGetters;
    }
}
