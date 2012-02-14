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
import com.google.common.collect.Maps;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    /** Constructs a new {@link FieldGetterDetector} check */
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

    @SuppressWarnings("rawtypes")
    @Override
    public void checkClass(ClassContext context, ClassNode classNode) {
        List<Entry> pendingCalls = null;
        int currentLine = 0;
        List methodList = classNode.methods;
        for (Object m : methodList) {
            MethodNode method = (MethodNode) m;
            InsnList nodes = method.instructions;
            for (int i = 0, n = nodes.size(); i < n; i++) {
                AbstractInsnNode instruction = nodes.get(i);
                int type = instruction.getType();
                if (type == AbstractInsnNode.LINE) {
                    currentLine = ((LineNumberNode) instruction).line;
                } else if (type == AbstractInsnNode.METHOD_INSN) {
                    MethodInsnNode node = (MethodInsnNode) instruction;
                    String name = node.name;
                    String owner = node.owner;

                    if (((name.startsWith("get") && name.length() > 3     //$NON-NLS-1$
                            && Character.isUpperCase(name.charAt(3)))
                        || (name.startsWith("is") && name.length() > 2    //$NON-NLS-1$
                            && Character.isUpperCase(name.charAt(2))))
                            && owner.equals(classNode.name)) {
                        // Calling a potential getter method on self. We now need to
                        // investigate the method body of the getter call and make sure
                        // it's really a plain getter, not just a method which happens
                        // to have a method name like a getter, or a method which not
                        // only returns a field but possibly computes it or performs
                        // other initialization or side effects. This is done in a
                        // second pass over the bytecode, initiated by the finish()
                        // method.
                        if (pendingCalls == null) {
                            pendingCalls = new ArrayList<Entry>();
                        }

                        pendingCalls.add(new Entry(name, currentLine, method));
                    }
                }
            }
        }

        if (pendingCalls != null) {
            Set<String> names = new HashSet<String>(pendingCalls.size());
            for (Entry entry : pendingCalls) {
                names.add(entry.name);
            }

            Map<String, String> getters = checkMethods(context.getClassNode(), names);
            if (getters.size() > 0) {
                File source = context.getSourceFile();
                String contents = context.getSourceContents();
                for (String getter : getters.keySet()) {
                    for (Entry entry : pendingCalls) {
                        String name = entry.name;
                        // There can be more than one reference to the same name:
                        // one for each call site
                        if (name.equals(getter)) {
                            int line = entry.lineNumber;
                            Location location = null;
                            if (source != null) {
                                // ASM line numbers are 1-based, Lint needs 0-based
                                location = Location.create(source, contents, line - 1, name,
                                        null);
                            } else {
                                location = Location.create(context.file);
                            }
                            String fieldName = getters.get(getter);
                            if (fieldName == null) {
                                fieldName = "";
                            }
                            context.report(ISSUE, entry.method, location, String.format(
                                "Calling getter method %1$s() on self is " +
                                "slower than field access (%2$s)", getter, fieldName), fieldName);
                        }
                    }
                }
            }
        }
    }

    // Holder class for getters to be checked
    private static class Entry {
        public final String name;
        public final int lineNumber;
        public final MethodNode method;

        public Entry(String name, int lineNumber, MethodNode method) {
            super();
            this.name = name;
            this.lineNumber = lineNumber;
            this.method = method;
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
    //
    // Returns a map of valid getters as keys, and if the field name is found, the field name
    // for each getter as its value.
    private static Map<String, String> checkMethods(ClassNode classNode, Set<String> names) {
        Map<String, String> validGetters = Maps.newHashMap();
        @SuppressWarnings("rawtypes")
        List methods = classNode.methods;
        String fieldName = null;
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
                                fieldName = null;
                                mState = 2;
                            } else {
                                continue checkMethod;
                            }
                            break;
                        case Opcodes.GETFIELD:
                            if (mState == 2) {
                                FieldInsnNode field = (FieldInsnNode) curr;
                                fieldName = field.name;
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
                            if (mState == 3) {
                                validGetters.put(method.name, fieldName);
                            }
                            continue checkMethod;
                        default:
                            continue checkMethod;
                    }
                }
            }
        }

        return validGetters;
    }
}
