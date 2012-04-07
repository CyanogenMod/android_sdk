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

import static com.android.tools.lint.detector.api.LintConstants.ANDROID_RESOURCE_PREFIX;
import static com.android.tools.lint.detector.api.LintConstants.TARGET_API;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;
import com.android.tools.lint.detector.api.XmlContext;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

/**
 * Looks for usages of APIs that are not supported in all the versions targeted
 * by this application (according to its minimum API requirement in the manifest).
 */
public class ApiDetector extends ResourceXmlDetector implements Detector.ClassScanner {
    private static final boolean AOSP_BUILD = System.getenv("ANDROID_BUILD_TOP") != null; //$NON-NLS-1$
    private static final String TARGET_API_VMSIG = '/' + TARGET_API + ';';

    /** Accessing an unsupported API */
    public static final Issue UNSUPPORTED = Issue.create("NewApi", //$NON-NLS-1$
            "Finds API accesses to APIs that are not supported in all targeted API versions",

            "This check scans through all the Android API calls in the application and " +
            "warns about any calls that are not available on *all* versions targeted " +
            "by this application (according to its minimum SDK attribute in the manifest).\n" +
            "\n" +
            "If your code is *deliberately* accessing newer APIs, and you have ensured " +
            "(e.g. with conditional execution) that this code will only ever be called on a " +
            "supported platform, then you can annotate your class or method with the " +
            "@TargetApi annotation specifying the local minimum SDK to apply, such as" +
            "@TargetApi(11), such that this check considers 11 rather than your manifest " +
            "file's minimum SDK as the required API level.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            ApiDetector.class,
            EnumSet.of(Scope.CLASS_FILE, Scope.RESOURCE_FILE));

    private ApiLookup mApiDatabase;
    private int mMinApi = -1;

    /** Constructs a new API check */
    public ApiDetector() {
    }

    @Override
    public boolean appliesTo(Context context, File file) {
        return true;
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
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return ALL;
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (mApiDatabase == null) {
            return;
        }

        String value = attribute.getValue();

        if (value.startsWith(ANDROID_RESOURCE_PREFIX)) {
            // Convert @android:type/foo into android/R$type and "foo"
            int index = value.indexOf('/', ANDROID_RESOURCE_PREFIX.length());
            if (index != -1) {
                String owner = "android/R$"    //$NON-NLS-1$
                        + value.substring(ANDROID_RESOURCE_PREFIX.length(), index);
                String name = value.substring(index + 1);
                int api = mApiDatabase.getFieldVersion(owner, name);
                int minSdk = getMinSdk(context);
                if (api > minSdk && api > context.getFolderVersion()) {
                    Location location = context.getLocation(attribute);
                    String message = String.format(
                            "%1$s requires API level %2$d (current min is %3$d)",
                            value, api, minSdk);
                    context.report(UNSUPPORTED, attribute, location, message, null);
                }
            }
        }
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (mApiDatabase == null) {
            return;
        }

        String tag = element.getTagName();

        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != ResourceFolderType.LAYOUT) {
            if (element.getParentNode().getNodeType() != Node.ELEMENT_NODE) {
                // Root node
                return;
            }
            NodeList childNodes = element.getChildNodes();
            for (int i = 0, n = childNodes.getLength(); i < n; i++) {
                Node textNode = childNodes.item(i);
                if (textNode.getNodeType() == Node.TEXT_NODE) {
                    String text = textNode.getNodeValue();
                    if (text.indexOf(ANDROID_RESOURCE_PREFIX) != -1) {
                        text = text.trim();
                        // Convert @android:type/foo into android/R$type and "foo"
                        int index = text.indexOf('/', ANDROID_RESOURCE_PREFIX.length());
                        if (index != -1) {
                            String owner = "android/R$"    //$NON-NLS-1$
                                    + text.substring(ANDROID_RESOURCE_PREFIX.length(), index);
                            String name = text.substring(index + 1);
                            int api = mApiDatabase.getFieldVersion(owner, name);
                            int minSdk = getMinSdk(context);
                            if (api > minSdk && api > context.getFolderVersion()) {
                                Location location = context.getLocation(textNode);
                                String message = String.format(
                                        "%1$s requires API level %2$d (current min is %3$d)",
                                        text, api, minSdk);
                                context.report(UNSUPPORTED, element, location, message, null);
                            }
                        }
                    }
                }
            }
        } else if (folderType == ResourceFolderType.LAYOUT) {
            // Check widgets to make sure they're available in this version of the SDK.
            if (tag.indexOf('.') != -1 ||
                    folderType != ResourceFolderType.LAYOUT) {
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
            if (api > minSdk && api > context.getFolderVersion()) {
                Location location = context.getLocation(element);
                String message = String.format(
                        "View requires API level %1$d (current min is %2$d): <%3$s>",
                        api, minSdk, tag);
                context.report(UNSUPPORTED, element, location, message, null);
            }
        }
    }

    private int getMinSdk(Context context) {
        if (mMinApi == -1) {
            mMinApi = context.getMainProject().getMinSdk();
        }

        return mMinApi;
    }

    // ---- Implements ClassScanner ----

    @SuppressWarnings("rawtypes")
    @Override
    public void checkClass(final ClassContext context, ClassNode classNode) {
        if (mApiDatabase == null) {
            return;
        }

        if (AOSP_BUILD && classNode.name.startsWith("android/support/")) { //$NON-NLS-1$
            return;
        }

        int classMinSdk = getClassMinSdk(context, classNode);
        if (classMinSdk == -1) {
            classMinSdk = getMinSdk(context);
        }

        List methodList = classNode.methods;
        for (Object m : methodList) {
            MethodNode method = (MethodNode) m;

            int minSdk = getLocalMinSdk(method.invisibleAnnotations);
            if (minSdk == -1) {
                minSdk = classMinSdk;
            }

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
                            report(context, message, var.start, method,
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
                        report(context, message, first, method, null, null);
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

                    // No need to check methods in this local class; we know they
                    // won't be an API match
                    if (node.getOpcode() == Opcodes.INVOKEVIRTUAL
                            && owner.equals(classNode.name)) {
                        owner = classNode.superName;
                    }

                    do {
                        int api = mApiDatabase.getCallVersion(owner, name, desc);
                        if (api > minSdk) {
                            String fqcn = owner.replace('/', '.') + '#' + name;
                            String message = String.format(
                                    "Call requires API level %1$d (current min is %2$d): %3$s",
                                    api, minSdk, fqcn);
                            report(context, message, node, method, name, null);
                        }

                        // For virtual dispatch, walk up the inheritance chain checking
                        // each inherited method
                        if (node.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                            owner = context.getDriver().getSuperClass(owner);
                        } else if (node.getOpcode() == Opcodes.INVOKESTATIC && api == -1) {
                            // Inherit through static classes as well
                            owner = context.getDriver().getSuperClass(owner);
                        } else {
                            owner = null;
                        }
                    } while (owner != null);
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
                        report(context, message, node, method, name, null);
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
                            report(context, message, node, method,
                                    className.substring(className.lastIndexOf('/') + 1), null);
                        }
                    }
                }
            }
        }
    }

    /**
     * Return the {@code @TargeTApi} level to use for the given {@code classNode};
     * this will be the {@code @TargetApi} annotation on the class, or any outer
     * methods (for anonymous inner classes) or outer classes (for inner classes)
     * of the given class.
     */
    private int getClassMinSdk(ClassContext context, ClassNode classNode) {
        int classMinSdk = getLocalMinSdk(classNode.invisibleAnnotations);
        if (classMinSdk != -1) {
            return classMinSdk;
        }

        LintDriver driver = context.getDriver();
        while (classNode != null) {
            ClassNode prev = classNode;
            classNode = driver.getOuterClassNode(classNode);
            if (classNode != null) {
                // TODO: Should this be "curr" instead?
                if (prev.outerMethod != null) {
                    @SuppressWarnings("rawtypes") // ASM API
                    List methods = classNode.methods;
                    for (Object m : methods) {
                        MethodNode method = (MethodNode) m;
                        if (method.name.equals(prev.outerMethod)
                                && method.desc.equals(prev.outerMethodDesc)) {
                            // Found the outer method for this anonymous class; check method
                            // annotations on it, then continue up the class hierarchy
                            int methodMinSdk = getLocalMinSdk(method.invisibleAnnotations);
                            if (methodMinSdk != -1) {
                                return methodMinSdk;
                            }

                            break;
                        }
                    }
                }

                classMinSdk = getLocalMinSdk(classNode.invisibleAnnotations);
                if (classMinSdk != -1) {
                    return classMinSdk;
                }
            }
        }

        return -1;
    }

    /**
     * Returns the minimum SDK to use according to the given annotation list, or
     * -1 if no annotation was found.
     *
     * @param annotations a list of annotation nodes from ASM
     * @return the API level to use for this node, or -1
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private int getLocalMinSdk(List annotations) {
        if (annotations != null) {
            for (AnnotationNode annotation : (List<AnnotationNode>)annotations) {
                String desc = annotation.desc;
                if (desc.endsWith(TARGET_API_VMSIG)) {
                    if (annotation.values != null) {
                        for (int i = 0, n = annotation.values.size(); i < n; i += 2) {
                            String key = (String) annotation.values.get(i);
                            if (key.equals("value")) {  //$NON-NLS-1$
                                Object value = annotation.values.get(i + 1);
                                if (value instanceof Integer) {
                                    return ((Integer) value).intValue();
                                } else if (value instanceof List) {
                                    List list = (List) value;
                                    for (Object v : list) {
                                        if (v instanceof Integer) {
                                            return ((Integer) value).intValue();
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return -1;
    }

    private void report(final ClassContext context, String message, AbstractInsnNode node,
            MethodNode method, String patternStart, String patternEnd) {
        int lineNumber = node != null ? ClassContext.findLineNumber(node) : -1;
        Location location = context.getLocationForLine(lineNumber, patternStart, patternEnd);
        context.report(UNSUPPORTED, method, location, message, null);
    }
}
