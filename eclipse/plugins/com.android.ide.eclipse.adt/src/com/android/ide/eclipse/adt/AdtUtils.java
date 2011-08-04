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
}
