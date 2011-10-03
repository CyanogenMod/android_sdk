/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ide.eclipse.adt;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;


/** Utility methods for ADT */
public class AdtUtils {
    /**
     * Returns true if the given string ends with the given suffix, using a
     * case-insensitive comparison.
     *
     * @param string the full string to be checked
     * @param suffix the suffix to be checked for
     * @return true if the string case-insensitively ends with the given suffix
     */
    public static boolean endsWithIgnoreCase(String string, String suffix) {
        return string.regionMatches(true /* ignoreCase */, string.length() - suffix.length(),
                suffix, 0, suffix.length());
    }

    /**
     * Returns true if the given sequence ends with the given suffix (case
     * sensitive).
     *
     * @param sequence the character sequence to be checked
     * @param suffix the suffix to look for
     * @return true if the given sequence ends with the given suffix
     */
    public static boolean endsWith(CharSequence sequence, CharSequence suffix) {
        return endsWith(sequence, sequence.length(), suffix);
    }

    /**
     * Returns true if the given sequence ends at the given offset with the given suffix (case
     * sensitive)
     *
     * @param sequence the character sequence to be checked
     * @param endOffset the offset at which the sequence is considered to end
     * @param suffix the suffix to look for
     * @return true if the given sequence ends with the given suffix
     */
    public static boolean endsWith(CharSequence sequence, int endOffset, CharSequence suffix) {
        if (endOffset < suffix.length()) {
            return false;
        }

        for (int i = endOffset - 1, j = suffix.length() - 1; j >= 0; i--, j--) {
            if (sequence.charAt(i) != suffix.charAt(j)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Strips the whitespace from the given string
     *
     * @param string the string to be cleaned up
     * @return the string, without whitespace
     */
    public static String stripWhitespace(String string) {
        StringBuilder sb = new StringBuilder(string.length());
        for (int i = 0, n = string.length(); i < n; i++) {
            char c = string.charAt(i);
            if (!Character.isWhitespace(c)) {
                sb.append(c);
            }
        }

        return sb.toString();
    }

    /**
     * Creates a Java class name out of the given string, if possible. For
     * example, "My Project" becomes "MyProject", "hello" becomes "Hello",
     * "Java's" becomes "Java", and so on.
     *
     * @param string the string to be massaged into a Java class
     * @return the string as a Java class, or null if a class name could not be
     *         extracted
     */
    public static String extractClassName(String string) {
        StringBuilder sb = new StringBuilder(string.length());
        int n = string.length();

        int i = 0;
        for (; i < n; i++) {
            char c = Character.toUpperCase(string.charAt(i));
            if (Character.isJavaIdentifierStart(c)) {
                sb.append(c);
                i++;
                break;
            }
        }
        if (sb.length() > 0) {
            for (; i < n; i++) {
                char c = string.charAt(i);
                if (Character.isJavaIdentifierPart(c)) {
                    sb.append(c);
                }
            }

            return sb.toString();
        }

        return null;
    }

    /**
     * Strips off the last file extension from the given filename, e.g.
     * "foo.backup.diff" will be turned into "foo.backup".
     * <p>
     * Note that dot files (e.g. ".profile") will be left alone.
     *
     * @param filename the filename to be stripped
     * @return the filename without the last file extension.
     */
    public static String stripLastExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0) { // > 0 instead of != -1: Treat dot files (e.g. .profile) differently
            return filename.substring(0, dotIndex);
        } else {
            return filename;
        }
    }

    /**
     * Strips off all extensions from the given filename, e.g. "foo.9.png" will
     * be turned into "foo".
     * <p>
     * Note that dot files (e.g. ".profile") will be left alone.
     *
     * @param filename the filename to be stripped
     * @return the filename without any file extensions
     */
    public static String stripAllExtensions(String filename) {
        int dotIndex = filename.indexOf('.');
        if (dotIndex > 0) { // > 0 instead of != -1: Treat dot files (e.g. .profile) differently
            return filename.substring(0, dotIndex);
        } else {
            return filename;
        }
    }

    /**
     * Capitalizes the string, i.e. transforms the initial [a-z] into [A-Z].
     * Returns the string unmodified if the first character is not [a-z].
     *
     * @param str The string to capitalize.
     * @return The capitalized string
     */
    public static String capitalize(String str) {
        if (str == null || str.length() < 1 || Character.isUpperCase(str.charAt(0))) {
            return str;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(Character.toUpperCase(str.charAt(0)));
        sb.append(str.substring(1));
        return sb.toString();
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
     * Returns the current editor (the currently visible and active editor), or null if
     * not found
     *
     * @return the current editor, or null
     */
    public static IEditorPart getActiveEditor() {
        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (window != null) {
            IWorkbenchPage page = window.getActivePage();
            if (page != null) {
                return page.getActiveEditor();
            }
        }

        return null;
    }

    /**
     * Returns the current text editor (the currently visible and active editor), or null
     * if not found.
     *
     * @return the current text editor, or null
     */
    public static ITextEditor getActiveTextEditor() {
        IEditorPart editor = getActiveEditor();
        if (editor != null) {
            if (editor instanceof ITextEditor) {
                return (ITextEditor) editor;
            } else {
                return (ITextEditor) editor.getAdapter(ITextEditor.class);
            }
        }

        return null;
    }

    /**
     * Returns the file for the current editor, if any.
     *
     * @return the file for the current editor, or null if none
     */
    public static IFile getActiveFile() {
        IEditorPart editor = getActiveEditor();
        if (editor != null) {
            IEditorInput input = editor.getEditorInput();
            if (input instanceof IFileEditorInput) {
                IFileEditorInput fileInput = (IFileEditorInput) input;
                return fileInput.getFile();
            }
        }

        return null;
    }

    /**
     * Returns an absolute path to the given resource
     *
     * @param resource the resource to look up a path for
     * @return an absolute file system path to the resource
     */
    public static IPath getAbsolutePath(IResource resource) {
        IWorkspaceRoot workspace = ResourcesPlugin.getWorkspace().getRoot();
        IPath workspacePath = workspace.getLocation();
        return workspacePath.append(resource.getFullPath());
    }
}
