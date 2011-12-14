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
import static com.android.tools.lint.detector.api.LintConstants.ID_RESOURCE_PREFIX;
import static com.android.tools.lint.detector.api.LintConstants.NEW_ID_RESOURCE_PREFIX;

import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.google.common.annotations.Beta;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


/**
 * Useful utility methods related to lint.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
@Beta
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
     * Returns the basename of the given filename, unless it's a dot-file such as ".svn".
     *
     * @param fileName the file name to extract the basename from
     * @return the basename (the filename without the file extension)
     */
    public static String getBaseName(String fileName) {
        int extension = fileName.indexOf('.');
        if (extension > 0) {
            return fileName.substring(0, extension);
        } else {
            return fileName;
        }
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
     * Returns the given id without an {@code @id/} or {@code @+id} prefix
     *
     * @param id the id to strip
     * @return the stripped id, never null
     */
    public static String stripIdPrefix(String id) {
        if (id == null) {
            return "";
        } else if (id.startsWith(NEW_ID_RESOURCE_PREFIX)) {
            return id.substring(NEW_ID_RESOURCE_PREFIX.length());
        } else if (id.startsWith(ID_RESOURCE_PREFIX)) {
            return id.substring(ID_RESOURCE_PREFIX.length());
        }

        return id;
    }

    /**
     * Computes the edit distance (number of insertions, deletions or substitutions
     * to edit one string into the other) between two strings. In particular,
     * this will compute the Levenshtein distance.
     * <p>
     * See http://en.wikipedia.org/wiki/Levenshtein_distance for details.
     *
     * @param s the first string to compare
     * @param t the second string to compare
     * @return the edit distance between the two strings
     */
    public static int editDistance(String s, String t) {
        int m = s.length();
        int n = t.length();
        int[][] d = new int[m + 1][n + 1];
        for (int i = 0; i <= m; i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= n; j++) {
            d[0][j] = j;
        }
        for (int j = 1; j <= n; j++) {
            for (int i = 1; i <= m; i++) {
                if (s.charAt(i - 1) == t.charAt(j - 1)) {
                    d[i][j] = d[i - 1][j - 1];
                } else {
                    int deletion = d[i - 1][j] + 1;
                    int insertion = d[i][j - 1] + 1;
                    int substitution = d[i - 1][j - 1] + 1;
                    d[i][j] = Math.min(deletion, Math.min(insertion, substitution));
                }
            }
        }

        return d[m][n];
    }

    /**
     * Returns true if assertions are enabled
     *
     * @return true if assertions are enabled
     */
    @SuppressWarnings("all")
    public static boolean assertionsEnabled() {
        boolean assertionsEnabled = false;
        assert assertionsEnabled = true; // Intentional side-effect
        return assertionsEnabled;
    }
}
