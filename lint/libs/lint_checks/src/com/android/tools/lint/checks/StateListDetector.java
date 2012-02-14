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

import static com.android.tools.lint.detector.api.LintConstants.ANDROID_URI;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import java.util.List;

/**
 * Checks for unreachable states in an Android state list definition
 */
public class StateListDetector extends ResourceXmlDetector {
    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "StateListReachable", //$NON-NLS-1$
            "Looks for unreachable states in a <selector>",
            "In a selector, only the last child in the state list should omit a " +
            "state qualifier. If not, all subsequent items in the list will be ignored " +
            "since the given item will match all.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            StateListDetector.class,
            Scope.RESOURCE_FILE_SCOPE);

    /** Constructs a new {@link StateListDetector} */
    public StateListDetector() {
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        // TODO: Look for views that don't specify
        // Display the error token somewhere so it can be suppressed
        // Emit warning at the end "run with --help to learn how to suppress types of errors/checks";
        // ("...and this message.")

        Element root = document.getDocumentElement();
        if (root != null && root.getTagName().equals("selector")) { //$NON-NLS-1$
            List<Element> children = LintUtils.getChildren(root);
            for (int i = 0; i < children.size() - 1; i++) {
                Element child = children.get(i);
                boolean hasState = false;
                NamedNodeMap attributes = child.getAttributes();
                for (int j = 0; j < attributes.getLength(); j++) {
                    Attr attribute = (Attr) attributes.item(j);
                    if (attribute.getLocalName() == null) {
                        continue;
                    }
                    if (attribute.getLocalName().startsWith("state_")) {
                        hasState = true;
                        break;
                    } else {
                        String namespaceUri = attribute.getNamespaceURI();
                        if (namespaceUri != null && namespaceUri.length() > 0 &&
                                !ANDROID_URI.equals(namespaceUri)) {
                            // There is a custom attribute on this item.
                            // This could be a state, see
                            //   http://code.google.com/p/android/issues/detail?id=22339
                            // so don't flag this one.
                            hasState = true;
                        }
                    }
                }
                if (!hasState) {
                    context.report(ISSUE, child, context.getLocation(child),
                        String.format("No android:state_ attribute found on <item> %1$d, later states not reachable",
                                i), null);
                }
            }
        }
    }
}
