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

package com.android.tools.lint.detector.api;

import com.android.resources.ResourceFolderType;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Specialized detector intended for XML resources. Detectors that apply to XML
 * resources should extend this detector instead since it provides special
 * iteration hooks that are more efficient.
 * <p/>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
public abstract class ResourceXmlDetector extends Detector {
    private static final String XML_SUFFIX = ".xml"; //$NON-NLS-1$

    /**
     * Special marker collection returned by {@link #getApplicableElements()} or
     * {@link #getApplicableAttributes()} to indicate that the check should be
     * invoked on all elements or all attributes
     */
    public static final List<String> ALL = new ArrayList<String>(0);

    protected final static String ANDROID_URI =
            "http://schemas.android.com/apk/res/android";                   //$NON-NLS-1$

    @Override
    public boolean appliesTo(Context context, File file) {
        return isXmlFile(file);
    }

    /**
     * Returns whether this detector applies to the given folder type. This
     * allows the detectors to be pruned from iteration, so for example when we
     * are analyzing a string value file we don't need to look up detectors
     * related to layout.
     *
     * @param folderType the folder type to be visited
     * @return true if this detector can apply to resources in folders of the
     *         given type
     */
    public boolean appliesTo(ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void run(Context context) {
        // The infrastructure should never call this method on an xml detector since
        // it will run the various visitors instead
        assert false;
    }

    /**
     * Visit the given document. The detector is responsible for its own iteration
     * through the document.
     * @param context information about the document being analyzed
     * @param document the document to examine
     */
    public void visitDocument(Context context, Document document) {
        // Only called if getApplicableElements() *and* getApplicableAttributes() returned null
        throw new IllegalArgumentException(this.getClass() + " must override visitDocument");
    }

    /**
     * Visit the given element.
     * @param context information about the document being analyzed
     * @param element the element to examine
     */
    public void visitElement(Context context, Element element) {
        // Only called if getApplicableElements() returned non-null
        throw new IllegalArgumentException(this.getClass() + " must override visitElement");
    }

    /**
     * Visit the given element after its children have been analyzed.
     * @param context information about the document being analyzed
     * @param element the element to examine
     */
    public void visitElementAfter(Context context, Element element) {
        // Optional
    }

    /**
     * Visit the given attribute.
     * @param context information about the document being analyzed
     * @param attribute the attribute node to examine
     */
    public void visitAttribute(Context context, Attr attribute) {
        // Only called if getApplicableAttributes() returned non-null
        throw new IllegalArgumentException(this.getClass() + " must override visitAttribute");
    }

    /**
     * Returns the list of elements that this detector wants to analyze. If non
     * null, this detector will be called (specifically, the
     * {@link #visitElement} method) for each matching element in the document.
     * <p>
     * If this method returns null, and {@link #getApplicableAttributes()} also returns
     * null, then the {@link #visitDocument} method will be called instead.
     *
     * @return a collection of elements, or null, or the special
     *         {@link ResourceXmlDetector#ALL} marker to indicate that every single
     *         element should be analyzed.
     */
    public Collection<String> getApplicableElements() {
        return null;
    }

    /**
     * Returns the list of attributes that this detector wants to analyze. If non
     * null, this detector will be called (specifically, the
     * {@link #visitAttribute} method) for each matching attribute in the document.
     * <p>
     * If this method returns null, and {@link #getApplicableElements()} also returns
     * null, then the {@link #visitDocument} method will be called instead.
     *
     * @return a collection of attributes, or null, or the special
     *         {@link ResourceXmlDetector#ALL} marker to indicate that every single
     *         attribute should be analyzed.
     */
    public Collection<String> getApplicableAttributes() {
        return null;
    }

    /**
     * Returns true if the given file represents an XML file
     *
     * @param file the file to be checked
     * @return true if the given file is an xml file
     */
    public static boolean isXmlFile(File file) {
        String string = file.getName();
        return string.regionMatches(true, string.length() - XML_SUFFIX.length(),
                XML_SUFFIX, 0, XML_SUFFIX.length());
    }

    /**
     * Returns the children elements of the given node
     *
     * @param node the parent node
     * @return a list of element children, never null
     */
    public static List<Element> getChildren(Node node) {
        NodeList childNodes = node.getChildNodes();
        List<Element> children = new ArrayList<Element>(childNodes.getLength());
        for (int i = 0, n = childNodes.getLength(); i < n; i++) {
            Node child = childNodes.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                children.add((Element) child);
            }
        }

        return children;
    }

    /**
     * Returns the <b>number</b> of children of the given node
     *
     * @param node the parent node
     * @return the count of element children
     */
    public static int getChildCount(Node node) {
        NodeList childNodes = node.getChildNodes();
        int childCount = 0;
        for (int i = 0, n = childNodes.getLength(); i < n; i++) {
            Node child = childNodes.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                childCount++;
            }
        }

        return childCount;
    }
}
