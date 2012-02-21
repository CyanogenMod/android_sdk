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

import static com.android.tools.lint.detector.api.LintConstants.ANDROID_URI;
import static com.android.tools.lint.detector.api.LintConstants.ATTR_NAME;
import static com.android.tools.lint.detector.api.LintConstants.ATTR_PACKAGE;
import static com.android.tools.lint.detector.api.LintConstants.TAG_ACTIVITY;
import static com.android.tools.lint.detector.api.LintConstants.TAG_PROVIDER;
import static com.android.tools.lint.detector.api.LintConstants.TAG_RECEIVER;
import static com.android.tools.lint.detector.api.LintConstants.TAG_SERVICE;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector.ClassScanner;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;
import com.android.tools.lint.detector.api.XmlContext;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.w3c.dom.Element;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map.Entry;

/**
 * Checks for missing manifest registrations for activities, services etc
 * and also makes sure that they are registered with the correct tag
 */
public class RegistrationDetector extends LayoutDetector implements ClassScanner {
    private static final String ANDROID_APP_ACTIVITY = "android/app/Activity";  //$NON-NLS-1$
    private static final String ANDROID_APP_SERVICE = "android/app/Service";    //$NON-NLS-1$
    private static final String ANDROID_CONTENT_CONTENT_PROVIDER =
            "android/content/ContentProvider";   //$NON-NLS-1$
    private static final String ANDROID_CONTENT_BROADCAST_RECEIVER =
            "android/content/BroadcastReceiver"; //$NON-NLS-1$

    /** Unregistered activities and services */
    public static final Issue ISSUE = Issue.create(
        "Registered", //$NON-NLS-1$
        "Ensures that Activities, Services and Content Providers are registered in the manifest",

        "Activities, services and content providers should be registered in the " +
        "AndroidManifext.xml file using <activity>, <service> and <provider> tags.\n" +
        "\n" +
        "If your activity is simply a parent class intended to be subclassed by other " +
        "\"real\" activities, make it an abstract class.",

        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        RegistrationDetector.class,
        EnumSet.of(Scope.MANIFEST, Scope.CLASS_FILE)).setMoreInfo(
        "http://developer.android.com/guide/topics/manifest/manifest-intro.html"); //$NON-NLS-1$

    private Multimap<String, String> mManifestRegistrations;

    /** Constructs a new {@link RegistrationDetector} */
    public RegistrationDetector() {
    }

    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }

    @Override
    public boolean appliesTo(Context context, File file) {
        return true;
    }

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(sTags);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String fqcn = getFqcn(element);
        String tag = element.getTagName();
        String frameworkClass = tagToClass(tag);
        if (frameworkClass != null) {
            String signature = ClassContext.getInternalName(fqcn);
            if (mManifestRegistrations == null) {
                mManifestRegistrations = ArrayListMultimap.create(4, 8);
            }
            mManifestRegistrations.put(frameworkClass, signature);
        }
    }

    private static String getFqcn(Element element) {
        StringBuilder sb = new StringBuilder();
        Element root = element.getOwnerDocument().getDocumentElement();
        String pkg = root.getAttribute(ATTR_PACKAGE);
        String className = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (className.startsWith(".")) { //$NON-NLS-1$
            sb.append(pkg);
        } else if (className.indexOf('.') == -1) {
            // According to the <activity> manifest element documentation, this is not
            // valid ( http://developer.android.com/guide/topics/manifest/activity-element.html )
            // but it appears in manifest files and appears to be supported by the runtime
            // so handle this in code as well:
            sb.append(pkg);
            sb.append('.');
        } // else: the class name is already a fully qualified class name
        sb.append(className);
        return sb.toString();
    }

    // ---- Implements ClassScanner ----

    @Override
    public void checkClass(ClassContext context, ClassNode classNode) {
        // Abstract classes do not need to be registered
        if ((classNode.access & Opcodes.ACC_ABSTRACT) != 0) {
            return;
        }
        String curr = classNode.name;

        int lastIndex = curr.lastIndexOf('$');
        if (lastIndex != -1 && lastIndex < curr.length() - 1) {
            if (Character.isDigit(curr.charAt(lastIndex+1))) {
                // Anonymous inner class, doesn't need to be registered
                return;
            }
        }

        while (curr != null) {
            for (String s : sClasses) {
                if (curr.equals(s)) {
                    Collection<String> registered = mManifestRegistrations != null ?
                            mManifestRegistrations.get(curr) : null;
                    if (registered == null || !registered.contains(classNode.name)) {
                        report(context, classNode, curr);
                    }

                }
            }

            curr = context.getDriver().getSuperClass(curr);
        }
    }

    private void report(ClassContext context, ClassNode classNode, String curr) {
        String tag = classToTag(curr);
        String className = ClassContext.createSignature(classNode.name, null, null);

        String wrongClass = null; // The framework class this class actually extends
        if (mManifestRegistrations != null) {
            Collection<Entry<String,String>> entries =
                    mManifestRegistrations.entries();
            for (Entry<String,String> entry : entries) {
                if (entry.getValue().equals(classNode.name)) {
                    wrongClass = entry.getKey();
                    break;
                }
            }
        }
        if (wrongClass != null) {
            Location location = context.getLocationForLine(-1, className, null);
            context.report(
                    ISSUE,
                    location,
                    String.format(
                            "%1$s is a <%2$s> but is registered in the manifest as a <%3$s>",
                            className, tag, classToTag(wrongClass)),
                    null);
        } else if (!tag.equals(TAG_RECEIVER)) { // don't need to be registered
            Location location = context.getLocationForLine(-1, className, null);
            context.report(
                    ISSUE,
                    location,
                    String.format(
                            "The <%1$s> %2$s is not registered in the manifest",
                            tag, className),
                    null);
        }
    }

    /** The manifest tags we care about */
    private static final String[] sTags = new String[] {
        TAG_ACTIVITY,
        TAG_SERVICE,
        TAG_RECEIVER,
        TAG_PROVIDER
        // Keep synchronized with {@link #sClasses}
    };

    /** The corresponding framework classes that the tags in {@link #sTags} should extend */
    private static final String[] sClasses = new String[] {
            ANDROID_APP_ACTIVITY,
            ANDROID_APP_SERVICE,
            ANDROID_CONTENT_BROADCAST_RECEIVER,
            ANDROID_CONTENT_CONTENT_PROVIDER
            // Keep synchronized with {@link #sTags}
    };

    /** Looks up the corresponding framework class a given manifest tag's class should extend */
    private static final String tagToClass(String tag) {
        for (int i = 0, n = sTags.length; i < n; i++) {
            if (sTags[i].equals(tag)) {
                return sClasses[i];
            }
        }

        return null;
    }

    /** Looks up the manifest tag a given framework class should be registered with */
    private static final String classToTag(String className) {
        for (int i = 0, n = sClasses.length; i < n; i++) {
            if (sClasses[i].equals(className)) {
                return sTags[i];
            }
        }

        return null;
    }
}
