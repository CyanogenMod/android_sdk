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

import static com.android.tools.lint.detector.api.LintConstants.ANDROID_MANIFEST_XML;
import static com.android.tools.lint.detector.api.LintConstants.ANDROID_URI;
import static com.android.tools.lint.detector.api.LintConstants.ATTR_EXPORTED;
import static com.android.tools.lint.detector.api.LintConstants.ATTR_PERMISSION;
import static com.android.tools.lint.detector.api.LintConstants.TAG_APPLICATION;
import static com.android.tools.lint.detector.api.LintConstants.TAG_INTENT_FILTER;
import static com.android.tools.lint.detector.api.LintConstants.TAG_SERVICE;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;

/**
 * Checks that exported services request a permission.
 */
public class ExportedServiceDetector extends Detector.XmlDetectorAdapter {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "ExportedService", //$NON-NLS-1$
            "Checks for exported services that do not require permissions",
            "Exported services (services which either set exported=true or contain " +
            "an intent-filter and do not specify exported=false) should define a " +
            "permission that an entity must have in order to launch the service " +
            "or bind to it. Without this, any application can use this service.",
            Category.SECURITY,
            5,
            Severity.WARNING,
            ExportedServiceDetector.class,
            EnumSet.of(Scope.MANIFEST));

    /** Constructs a new accessibility check */
    public ExportedServiceDetector() {
    }

    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }
    @Override
    public boolean appliesTo(Context context, File file) {
        return file.getName().equals(ANDROID_MANIFEST_XML);
    }

    // ---- Implements Detector.XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(new String[] {
            TAG_SERVICE,
        });
    }

    @Override
    public void visitElement(Context context, Element element) {
        assert element.getTagName().equals(TAG_SERVICE);

        String exportValue = element.getAttributeNS(ANDROID_URI, ATTR_EXPORTED);
        boolean exported;
        if (exportValue != null && exportValue.length() > 0) {
            exported = Boolean.valueOf(exportValue);
        } else {
            boolean haveIntentFilters = false;
            for (Element child : LintUtils.getChildren(element)) {
                if (child.getTagName().equals(TAG_INTENT_FILTER)) {
                    haveIntentFilters = true;
                    break;
                }
            }
            exported = haveIntentFilters;
        }

        if (exported) {
            // Make sure this service has a permission
            String permission = element.getAttributeNS(ANDROID_URI, ATTR_PERMISSION);
            if (permission == null || permission.length() == 0) {
                Node parent = element.getParentNode();
                if (parent.getNodeType() == Node.ELEMENT_NODE
                        && parent.getNodeName().equals(TAG_APPLICATION)) {
                    Element application = (Element) parent;
                    permission = application.getAttributeNS(ANDROID_URI, ATTR_PERMISSION);
                    if (permission == null || permission.length() == 0) {
                        // No declared permission for this exported service: complain
                        context.client.report(context, ISSUE,
                            context.getLocation(element),
                            "Exported service does not require permission", null);
                    }
                }
            }
        }
    }
}
