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

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;
import com.android.tools.lint.detector.api.XmlContext;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.w3c.dom.Element;

import java.io.File;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

/**
 * Looks for usages of APIs that are not supported in all the versions targeted
 * by this application (according to its minimum API requirement in the manifest).
 */
public class ApiDetector extends LayoutDetector implements Detector.ClassScanner {
    /** Accessing an unsupported API */
    public static final Issue MISSING = Issue.create("NewApi", //$NON-NLS-1$
            "Finds API accesses to APIs that are not supported in all targeted API versions",

            "This check scans through all the Android API calls in the application and " +
            "warns about any calls that are not available on *all* versions targeted " +
            "by this application (according to its minimum SDK attribute in the manifest).",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            ApiDetector.class,
            EnumSet.of(Scope.CLASS_FILE, Scope.JAVA_LIBRARIES, Scope.RESOURCE_FILE));

    private ApiLookup mApiDatabase;
    private int mMinApi = -1;

    /** Constructs a new accessibility check */
    public ApiDetector() {
    }

    @Override
    public boolean appliesTo(Context context, File file) {
        return true;
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Speed getSpeed() {
        return Speed.SLOW;
    }

    @Override
    public void beforeCheckProject(Context context) {
        mApiDatabase = ApiLookup.get(context.getClient());
        // We can't look up the minimum API required by the project here:
        // The manifest file hasn't been processed yet in the -before- project hook.
        // For now it's initialized lazily in getMinSdk(Context), but the
        // lint infrastructure should be fixed to parse manifest file up front.
    }

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Check widgets to make sure they're available in this version of the SDK.
        String tag = element.getTagName();
        if (mApiDatabase == null || tag.indexOf('.') != -1) {
            // Custom views aren't in the index
            return;
        }
        // TODO: Consider other widgets outside of android.widget.*
        int api = mApiDatabase.getCallVersion("android/widget/" + tag,  //$NON-NLS-1$
                "<init>", //$NON-NLS-1$
                // Not all views provided this constructor right away, for example,
                // LinearLayout added it in API 11 yet LinearLayout is much older:
                // "(Landroid/content/Context;Landroid/util/AttributeSet;I)V"); //$NON-NLS-1$
                "(Landroid/content/Context;)"); //$NON-NLS-1$
        int minSdk = getMinSdk(context);
        if (api > minSdk) {
            Location location = context.getLocation(element);
            String message = String.format(
                    "View requires API level %1$d (current min is %2$d): <%3$s>",
                    api, minSdk, tag);
            context.report(MISSING, location, message, null);
        }
    }

    private int getMinSdk(Context context) {
        if (mMinApi == -1) {
            mMinApi = context.getProject().getMinSdk();
        }

        return mMinApi;
    }

    // ---- Implements ClassScanner ----

    @SuppressWarnings({"rawtypes","unchecked"})
    @Override
    public void checkClass(final ClassContext context, ClassNode classNode) {
        if (mApiDatabase == null) {
            return;
        }

        // Workaround for the fact that beforeCheckProject is too early
        int minSdk = getMinSdk(context);

        List methodList = classNode.methods;
        for (Object m : methodList) {
            MethodNode method = (MethodNode) m;
            InsnList nodes = method.instructions;

            // Check types in parameter list and types of local variables
            List localVariables = method.localVariables;
            if (localVariables != null) {
                for (Object v : localVariables) {
                    LocalVariableNode var = (LocalVariableNode) v;
                    String desc = var.desc;
                    if (desc.charAt(0) == 'L') {
                        // "Lpackage/Class;" => "package/Bar"
                        String className = desc.substring(1, desc.length() - 1);
                        int api = mApiDatabase.getClassVersion(className);
                        if (api > minSdk) {
                            String fqcn = className.replace('/', '.').replace('$', '.');
                            String message = String.format(
                                "Class requires API level %1$d (current min is %2$d): %3$s",
                                api, minSdk, fqcn);
                            report(context, message, var.start,
                                    className.substring(className.lastIndexOf('/') + 1), null);
                        }

                    }
                }
            }

            // Check return type
            // The parameter types are already handled as local variables so we can skip
            // right to the return type.
            // Check types in parameter list
            String signature = method.desc;
            if (signature != null) {
                int args = signature.indexOf(')');
                if (args != -1 && signature.charAt(args + 1) == 'L') {
                    String type = signature.substring(args + 2, signature.length() - 1);
                    int api = mApiDatabase.getClassVersion(type);
                    if (api > minSdk) {
                        String fqcn = type.replace('/', '.').replace('$', '.');
                        String message = String.format(
                            "Class requires API level %1$d (current min is %2$d): %3$s",
                            api, minSdk, fqcn);
                        AbstractInsnNode first = nodes.size() > 0 ? nodes.get(0) : null;
                        report(context, message, first, null, null);
                    }
                }
            }

            for (int i = 0, n = nodes.size(); i < n; i++) {
                AbstractInsnNode instruction = nodes.get(i);
                int type = instruction.getType();
                if (type == AbstractInsnNode.METHOD_INSN) {
                    MethodInsnNode node = (MethodInsnNode) instruction;
                    String name = node.name;
                    String owner = node.owner;
                    String desc = node.desc;

                    // Handle inherited methods. This does not work if there are multiple
                    // local classes in the inheritance chain. To solve this in general we'll
                    // need to make two passes: the first one to gather inheritance information
                    // for local classes, and then perform the check here resolving up to
                    // the API classes.
                    if (node.getOpcode() == Opcodes.INVOKEVIRTUAL
                            && owner.equals(classNode.name)) {
                        owner = classNode.superName;
                    }

                    int api = mApiDatabase.getCallVersion(owner, name, desc);
                    if (api > minSdk) {
                        String fqcn = owner.replace('/', '.') + '#' + name;
                        String message = String.format(
                                "Call requires API level %1$d (current min is %2$d): %3$s",
                                api, minSdk, fqcn);
                        report(context, message, node, name, null);
                    }
                } else if (type == AbstractInsnNode.FIELD_INSN) {
                    FieldInsnNode node = (FieldInsnNode) instruction;
                    String name = node.name;
                    String owner = node.owner;
                    int api = mApiDatabase.getFieldVersion(owner, name);
                    if (api > minSdk) {
                        String fqcn = owner.replace('/', '.') + '#' + name;
                        String message = String.format(
                                "Field requires API level %1$d (current min is %2$d): %3$s",
                                api, minSdk, fqcn);
                        report(context, message, node, name, null);
                    }
                } else if (type == AbstractInsnNode.LDC_INSN) {
                    LdcInsnNode node = (LdcInsnNode) instruction;
                    if (node.cst instanceof Type) {
                        Type t = (Type) node.cst;
                        String className = t.getInternalName();

                        int api = mApiDatabase.getClassVersion(className);
                        if (api > minSdk) {
                            String fqcn = className.replace('/', '.');
                            String message = String.format(
                                    "Class requires API level %1$d (current min is %2$d): %3$s",
                                    api, minSdk, fqcn);
                            report(context, message, node,
                                    className.substring(className.lastIndexOf('/') + 1), null);
                        }
                    }
                }
            }
        }
    }

    private static int findLineNumber(AbstractInsnNode node) {
        AbstractInsnNode curr = node;

        // First search backwards
        while (curr != null) {
            if (curr.getType() == AbstractInsnNode.LINE) {
                return ((LineNumberNode) curr).line;
            }
            curr = curr.getPrevious();
        }

        // Then search forwards
        curr = node;
        while (curr != null) {
            if (curr.getType() == AbstractInsnNode.LINE) {
                return ((LineNumberNode) curr).line;
            }
            curr = curr.getNext();
        }

        return -1;
    }

    private void report(final ClassContext context, String message, AbstractInsnNode node,
            String patternStart, String patternEnd) {
        int lineNumber = node != null ? findLineNumber(node) : -1;
        Location location = context.getLocationForLine(lineNumber, patternStart, patternEnd);
        context.report(MISSING, location, message, null);
    }
}
