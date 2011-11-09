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

import static com.android.tools.lint.detector.api.LintConstants.DOT_XML;

import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * Useful utility methods related to lint.
 */
public class LintUtils {
    /**
     * Format a list of strings, and cut of the list at {@code maxItems} if the
     * number of items are greater.
     *
     * @param strings the list of strings to print out as a comma separated list
     * @param maxItems the maximum number of items to print
     * @return a comma separated list
     */
    public static String formatList(List<String> strings, int maxItems) {
        StringBuilder sb = new StringBuilder(20 * strings.size());

        for (int i = 0, n = strings.size(); i < n; i++) {
            if (sb.length() > 0) {
                sb.append(", "); //$NON-NLS-1$
            }
            sb.append(strings.get(i));

            if (i == maxItems - 1 && n > maxItems) {
                sb.append(String.format("... (%1$d more)", n - i - 1));
                break;
            }
        }

        return sb.toString();
    }

    /**
     * Computes the set difference {@code a - b}
     *
     * @param a the set to subtract from
     * @param b the set to subtract
     * @return the elements that are in {@code a} but not in {@code b}
     */
    public static Set<String> difference(Set<String> a, Set<String> b) {
        HashSet<String> copy = new HashSet<String>(a);
        copy.removeAll(b);
        return copy;
    }

    /**
     * Computes the set intersection {@code a intersect b}
     *
     * @param a the first set to intersect
     * @param b the second set to intersect
     * @return the elements that are in {@code a} and in {@code b}
     */
    public static Set<String> intersection(Set<String> a, Set<String> b) {
        HashSet<String> intersection = new HashSet<String>(a);
        intersection.retainAll(b);
        return intersection;
    }

    /**
     * Computes the set union {@code a union b}
     *
     * @param a the first set to add
     * @param b the second set to add
     * @return the elements that are in {@code a} or in {@code b}
     */
    public static Set<String> union(Set<String> a, Set<String> b) {
        HashSet<String> union = new HashSet<String>(a);
        union.addAll(b);
        return union;
    }

    /**
     * Determine if the given type corresponds to a resource that has a unique
     * file
     *
     * @param type the resource type to check
     * @return true if the given type corresponds to a file-type resource
     */
    public static boolean isFileBasedResourceType(ResourceType type) {
        List<ResourceFolderType> folderTypes = FolderTypeRelationship.getRelatedFolders(type);
        for (ResourceFolderType folderType : folderTypes) {
            if (folderType != ResourceFolderType.VALUES) {
                if (type == ResourceType.ID) {
                    return false;
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if the given file represents an XML file
     *
     * @param file the file to be checked
     * @return true if the given file is an xml file
     */
    public static boolean isXmlFile(File file) {
        String string = file.getName();
        return string.regionMatches(true, string.length() - DOT_XML.length(),
                DOT_XML, 0, DOT_XML.length());
    }

    /**
     * Case insensitive ends with
     *
     * @param string the string to be tested whether it ends with the given
     *            suffix
     * @param suffix the suffix to check
     * @return true if {@code string} ends with {@code suffix},
     *         case-insensitively.
     */
    public static boolean endsWith(String string, String suffix) {
        return string.regionMatches(true /* ignoreCase */, string.length() - suffix.length(),
                suffix, 0, suffix.length());
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

    /**
     * Copies a file
     *
     * @param src the file to copy
     * @param target the filename to write the file into
     * @throws IOException if an I/O problem occurs
     */
    public static void copyFile(File src, File target) throws IOException {
        InputStream input = null;
        OutputStream output = null;
        try {
            input = new BufferedInputStream(new FileInputStream(src));
            output = new BufferedOutputStream(new FileOutputStream(target));
            byte[] buffer = new byte[1024];
            while (true) {
                int bytesRead = input.read(buffer);
                if (bytesRead == -1) {
                    break;
                }
                output.write(buffer, 0, bytesRead);
            }
        } finally {
            if (input != null) {
                input.close();
            }
            if (output != null) {
                output.close();
            }
        }
    }

    /**
     * Reads the file contents of the given file and returns it as a byte array.
     *
     * @param src the file to be read
     * @return the contents of the file, or null
     * @throws IOException if an I/O error occurs
     */
    public static byte[] readBytes(File src) throws IOException {
        InputStream input = null;
        try {
            int length = (int) src.length();
            if (length <= 0) {
                return null;
            }
            byte[] buffer = new byte[length];
            input = new BufferedInputStream(new FileInputStream(src));
            int offset = 0;
            while (offset < length) {
                int n = input.read(buffer, offset, length - offset);
                if (n == -1) {
                    break;
                }
                offset += n;
            }

            return buffer;
        } finally {
            if (input != null) {
                input.close();
            }
        }
    }
}
