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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.util.PositionXmlParser;
import com.google.common.annotations.Beta;
import com.google.common.io.Files;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
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
    @NonNull
    public static String formatList(@NonNull List<String> strings, int maxItems) {
        StringBuilder sb = new StringBuilder(20 * strings.size());

        for (int i = 0, n = strings.size(); i < n; i++) {
            if (sb.length() > 0) {
                sb.append(", "); //$NON-NLS-1$
            }
            sb.append(strings.get(i));

            if (maxItems > 0 && i == maxItems - 1 && n > maxItems) {
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
    public static boolean isFileBasedResourceType(@NonNull ResourceType type) {
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
    public static boolean isXmlFile(@NonNull File file) {
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
    public static boolean endsWith(@NonNull String string, @NonNull String suffix) {
        return string.regionMatches(true /* ignoreCase */, string.length() - suffix.length(),
                suffix, 0, suffix.length());
    }

    /**
     * Case insensitive starts with
     *
     * @param string the string to be tested whether it starts with the given prefix
     * @param prefix the prefix to check
     * @param offset the offset to start checking with
     * @return true if {@code string} starts with {@code prefix},
     *         case-insensitively.
     */
    public static boolean startsWith(@NonNull String string, @NonNull String prefix, int offset) {
        return string.regionMatches(true /* ignoreCase */, offset, prefix, 0, prefix.length());
    }

    /**
     * Returns the basename of the given filename, unless it's a dot-file such as ".svn".
     *
     * @param fileName the file name to extract the basename from
     * @return the basename (the filename without the file extension)
     */
    public static String getBaseName(@NonNull String fileName) {
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
    @NonNull
    public static List<Element> getChildren(@NonNull Node node) {
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
    public static int getChildCount(@NonNull Node node) {
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
     * Returns true if the given element is the root element of its document
     *
     * @param element the element to test
     * @return true if the element is the root element
     */
    public static boolean isRootElement(Element element) {
        return element == element.getOwnerDocument().getDocumentElement();
    }

    /**
     * Returns the given id without an {@code @id/} or {@code @+id} prefix
     *
     * @param id the id to strip
     * @return the stripped id, never null
     */
    @NonNull
    public static String stripIdPrefix(@Nullable String id) {
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
     * Returns true if the given two id references match. This is similar to
     * String equality, but it also considers "{@code @+id/foo == @id/foo}.
     *
     * @param id1 the first id to compare
     * @param id2 the second id to compare
     * @return true if the two id references refer to the same id
     */
    public static boolean idReferencesMatch(String id1, String id2) {
        if (id1.startsWith(NEW_ID_RESOURCE_PREFIX)) {
            if (id2.startsWith(NEW_ID_RESOURCE_PREFIX)) {
                return id1.equals(id2);
            } else {
                assert id2.startsWith(ID_RESOURCE_PREFIX);
                return ((id1.length() - id2.length())
                            == (NEW_ID_RESOURCE_PREFIX.length() - ID_RESOURCE_PREFIX.length()))
                        && id1.regionMatches(NEW_ID_RESOURCE_PREFIX.length(), id2,
                                ID_RESOURCE_PREFIX.length(),
                                id2.length() - ID_RESOURCE_PREFIX.length());
            }
        } else {
            assert id1.startsWith(ID_RESOURCE_PREFIX);
            if (id2.startsWith(ID_RESOURCE_PREFIX)) {
                return id1.equals(id2);
            } else {
                assert id2.startsWith(NEW_ID_RESOURCE_PREFIX);
                return (id2.length() - id1.length()
                            == (NEW_ID_RESOURCE_PREFIX.length() - ID_RESOURCE_PREFIX.length()))
                        && id2.regionMatches(NEW_ID_RESOURCE_PREFIX.length(), id1,
                                ID_RESOURCE_PREFIX.length(),
                                id1.length() - ID_RESOURCE_PREFIX.length());
            }
        }
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
    public static int editDistance(@NonNull String s, @NonNull String t) {
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

    /**
     * Returns the layout resource name for the given layout file
     *
     * @param layoutFile the file pointing to the layout
     * @return the layout resource name, not including the {@code @layout}
     *         prefix
     */
    public static String getLayoutName(File layoutFile) {
        String name = layoutFile.getName();
        int dotIndex = name.indexOf('.');
        if (dotIndex != -1) {
            name = name.substring(0, dotIndex);
        }
        return name;
    }

    /**
     * Computes the shared parent among a set of files (which may be null).
     *
     * @param files the set of files to be checked
     * @return the closest common ancestor file, or null if none was found
     */
    @Nullable
    public static File getCommonParent(@NonNull List<File> files) {
        int fileCount = files.size();
        if (fileCount == 0) {
            return null;
        } else if (fileCount == 1) {
            return files.get(0);
        } else if (fileCount == 2) {
            return getCommonParent(files.get(0), files.get(1));
        } else {
            File common = files.get(0);
            for (int i = 1; i < fileCount; i++) {
                common = getCommonParent(common, files.get(i));
                if (common == null) {
                    return null;
                }
            }

            return common;
        }
    }

    /**
     * Computes the closest common parent path between two files.
     *
     * @param file1 the first file to be compared
     * @param file2 the second file to be compared
     * @return the closest common ancestor file, or null if the two files have
     *         no common parent
     */
    @Nullable
    public static File getCommonParent(@NonNull File file1, @NonNull File file2) {
        if (file1.equals(file2)) {
            return file1;
        } else if (file1.getPath().startsWith(file2.getPath())) {
            return file2;
        } else if (file2.getPath().startsWith(file1.getPath())) {
            return file1;
        } else {
            // Dumb and simple implementation
            File first = file1.getParentFile();
            while (first != null) {
                File second = file2.getParentFile();
                while (second != null) {
                    if (first.equals(second)) {
                        return first;
                    }
                    second = second.getParentFile();
                }

                first = first.getParentFile();
            }
        }
        return null;
    }

    private static final String UTF_8 = "UTF-8";                 //$NON-NLS-1$
    private static final String UTF_16 = "UTF_16";               //$NON-NLS-1$
    private static final String UTF_16LE = "UTF_16LE";           //$NON-NLS-1$

    /**
     * Returns the encoded String for the given file. This is usually the
     * same as {@code Files.toString(file, Charsets.UTF8}, but if there's a UTF byte order mark
     * (for UTF8, UTF_16 or UTF_16LE), use that instead.
     *
     * @param file the file to read from
     * @return the string
     * @throws IOException if the file cannot be read properly
     */
    public static String getEncodedString(File file) throws IOException {
        byte[] bytes = Files.toByteArray(file);
        if (endsWith(file.getName(), DOT_XML)) {
            return PositionXmlParser.getXmlString(bytes);
        }

        return LintUtils.getEncodedString(bytes);
    }

    /**
     * Returns the String corresponding to the given data. This is usually the
     * same as {@code new String(data)}, but if there's a UTF byte order mark
     * (for UTF8, UTF_16 or UTF_16LE), use that instead.
     * <p>
     * NOTE: For XML files, there is the additional complication that there
     * could be a {@code encoding=} attribute in the prologue. For those files,
     * use {@link PositionXmlParser#getXmlString(byte[])} instead.
     *
     * @param data the byte array to construct the string from
     * @return the string
     */
    public static String getEncodedString(byte[] data) {
        if (data == null) {
            return "";
        }

        int offset = 0;
        String defaultCharset = UTF_8;
        String charset = null;
        // Look for the byte order mark, to see if we need to remove bytes from
        // the input stream (and to determine whether files are big endian or little endian) etc
        // for files which do not specify the encoding.
        // See http://unicode.org/faq/utf_bom.html#BOM for more.
        if (data.length > 4) {
            if (data[0] == (byte)0xef && data[1] == (byte)0xbb && data[2] == (byte)0xbf) {
                // UTF-8
                defaultCharset = charset = UTF_8;
                offset += 3;
            } else if (data[0] == (byte)0xfe && data[1] == (byte)0xff) {
                //  UTF-16, big-endian
                defaultCharset = charset = UTF_16;
                offset += 2;
            } else if (data[0] == (byte)0x0 && data[1] == (byte)0x0
                    && data[2] == (byte)0xfe && data[3] == (byte)0xff) {
                // UTF-32, big-endian
                defaultCharset = charset = "UTF_32";    //$NON-NLS-1$
                offset += 4;
            } else if (data[0] == (byte)0xff && data[1] == (byte)0xfe
                    && data[2] == (byte)0x0 && data[3] == (byte)0x0) {
                // UTF-32, little-endian. We must check for this *before* looking for
                // UTF_16LE since UTF_32LE has the same prefix!
                defaultCharset = charset = "UTF_32LE";  //$NON-NLS-1$
                offset += 4;
            } else if (data[0] == (byte)0xff && data[1] == (byte)0xfe) {
                //  UTF-16, little-endian
                defaultCharset = charset = UTF_16LE;
                offset += 2;
            }
        }
        int length = data.length - offset;

        // Guess encoding by searching for an encoding= entry in the first line.
        boolean seenOddZero = false;
        boolean seenEvenZero = false;
        for (int lineEnd = offset; lineEnd < data.length; lineEnd++) {
            if (data[lineEnd] == 0) {
                if ((lineEnd - offset) % 1 == 0) {
                    seenEvenZero = true;
                } else {
                    seenOddZero = true;
                }
            } else if (data[lineEnd] == '\n' || data[lineEnd] == '\r') {
                break;
            }
        }

        if (charset == null) {
            charset = seenOddZero ? UTF_16 : seenEvenZero ? UTF_16LE : UTF_8;
        }

        String text = null;
        try {
            text = new String(data, offset, length, charset);
        } catch (UnsupportedEncodingException e) {
            try {
                if (charset != defaultCharset) {
                    text = new String(data, offset, length, defaultCharset);
                }
            } catch (UnsupportedEncodingException u) {
                // Just use the default encoding below
            }
        }
        if (text == null) {
            text = new String(data, offset, length);
        }
        return text;
    }
}
