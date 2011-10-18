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

package com.android.tools.lint.api;

import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Severity;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;

/**
 * Specialized visitor for running detectors on an XML document.
 * It operates in two phases:
 * <ol>
 *   <li> First, it computes a set of maps where it generates a map from each
 *        significant element name, and each significant attribute name, to a list
 *        of detectors to consult for that element or attribute name.
 *        The set of element names or attribute names (or both) that a detector
 *        is interested in is provided by the detectors themselves.
 *   <li> Second, it iterates over the document a single time. For each element and
 *        attribute it looks up the list of interested detectors, and runs them.
 * </ol>
 * It also notifies all the detectors before and after the document is processed
 * such that they can do pre- and post-processing.
 */
class XmlVisitor {
    private final Map<String, List<ResourceXmlDetector>> mElementToCheck =
            new HashMap<String, List<ResourceXmlDetector>>();
    private final Map<String, List<ResourceXmlDetector>> mAttributeToCheck =
            new HashMap<String, List<ResourceXmlDetector>>();
    private final List<ResourceXmlDetector> mDocumentDetectors =
            new ArrayList<ResourceXmlDetector>();
    private final List<ResourceXmlDetector> mAllElementDetectors =
            new ArrayList<ResourceXmlDetector>();
    private final List<ResourceXmlDetector> mAllAttributeDetectors =
            new ArrayList<ResourceXmlDetector>();
    private final List<ResourceXmlDetector> mAllDetectors;
    private final IDomParser mParser;

    XmlVisitor(IDomParser parser, List<ResourceXmlDetector> detectors) {
        mParser = parser;
        mAllDetectors = detectors;

        // TODO: Check appliesTo() for files, and find a quick way to enable/disable
        // rules when running through a full project!
        for (ResourceXmlDetector detector : detectors) {
            Collection<String> attributes = detector.getApplicableAttributes();
            if (attributes == ResourceXmlDetector.ALL) {
                mAllAttributeDetectors.add(detector);
            }  else if (attributes != null) {
                for (String attribute : attributes) {
                    List<ResourceXmlDetector> list = mAttributeToCheck.get(attribute);
                    if (list == null) {
                        list = new ArrayList<ResourceXmlDetector>();
                        mAttributeToCheck.put(attribute, list);
                    }
                    list.add(detector);
                }
            }
            Collection<String> elements = detector.getApplicableElements();
            if (elements == ResourceXmlDetector.ALL) {
                mAllElementDetectors.add(detector);
            } else if (elements != null) {
                for (String element : elements) {
                    List<ResourceXmlDetector> list = mElementToCheck.get(element);
                    if (list == null) {
                        list = new ArrayList<ResourceXmlDetector>();
                        mElementToCheck.put(element, list);
                    }
                    list.add(detector);
                }
            }

            if ((attributes == null || (attributes.size() == 0
                    && attributes != ResourceXmlDetector.ALL))
                  && (elements == null || (elements.size() == 0
                  && elements != ResourceXmlDetector.ALL))) {
                mDocumentDetectors.add(detector);
            }
        }
    }

    void visitFile(Context context, File file) {
        assert ResourceXmlDetector.isXmlFile(file);

        context.location = null;
        context.parser = mParser;

        if (context.document == null) {
            context.document = mParser.parse(context);
            if (context.document == null) {
                context.toolContext.report(
                        // Must provide an issue since API guarantees that the issue parameter
                        // is valid
                        Issue.create("dummy", "", "", "", 0, Severity.ERROR), //$NON-NLS-1$
                        new Location(file, null, null),
                        "Skipped file because it contains parsing errors");
                return;
            }
            if (context.document.getDocumentElement() == null) {
                // Ignore empty documents
                return;
            }
        }

        for (ResourceXmlDetector check : mAllDetectors) {
            check.beforeCheckFile(context);
        }

        for (ResourceXmlDetector check : mDocumentDetectors) {
            check.visitDocument(context, context.document);
        }

        if (mElementToCheck.size() > 0 || mAttributeToCheck.size() > 0
                || mAllAttributeDetectors.size() > 0 || mAllElementDetectors.size() > 0) {
            visitElement(context, context.document.getDocumentElement());
        }

        for (ResourceXmlDetector check : mAllDetectors) {
            check.afterCheckFile(context);
        }
    }

    private void visitElement(Context context, Element element) {
        context.element = element;

        List<ResourceXmlDetector> elementChecks = mElementToCheck.get(element.getTagName());
        if (elementChecks != null) {
            assert elementChecks instanceof RandomAccess;
            for (int i = 0, n = elementChecks.size(); i < n; i++) {
                ResourceXmlDetector check = elementChecks.get(i);
                check.visitElement(context, element);
            }
        }
        if (mAllElementDetectors.size() > 0) {
            for (int i = 0, n = mAllElementDetectors.size(); i < n; i++) {
                ResourceXmlDetector check = mAllElementDetectors.get(i);
                check.visitElement(context, element);
            }
        }

        if (mAttributeToCheck.size() > 0 || mAllAttributeDetectors.size() > 0) {
            NamedNodeMap attributes = element.getAttributes();
            for (int i = 0, n = attributes.getLength(); i < n; i++) {
                Attr attribute = (Attr) attributes.item(i);
                List<ResourceXmlDetector> list = mAttributeToCheck.get(attribute.getLocalName());
                if (list != null) {
                    for (int j = 0, max = list.size(); j < max; j++) {
                        ResourceXmlDetector check = list.get(j);
                        check.visitAttribute(context, attribute);
                    }
                }
                if (mAllAttributeDetectors.size() > 0) {
                    for (int j = 0, max = mAllAttributeDetectors.size(); j < max; j++) {
                        ResourceXmlDetector check = mAllAttributeDetectors.get(j);
                        check.visitAttribute(context, attribute);
                    }
                }
            }
        }

        // Visit children
        NodeList childNodes = element.getChildNodes();
        for (int i = 0, n = childNodes.getLength(); i < n; i++) {
            Node child = childNodes.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                visitElement(context, (Element) child);
            }
        }

        // Post hooks
        if (elementChecks != null) {
            for (int i = 0, n = elementChecks.size(); i < n; i++) {
                ResourceXmlDetector check = elementChecks.get(i);
                check.visitElementAfter(context, element);
            }
        }
        if (mAllElementDetectors.size() > 0) {
            for (int i = 0, n = mAllElementDetectors.size(); i < n; i++) {
                ResourceXmlDetector check = mAllElementDetectors.get(i);
                check.visitElementAfter(context, element);
            }
        }
    }
}
