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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.eclipse.adt.internal.project.BaseProjectHelper;
import com.android.ide.eclipse.adt.internal.project.BaseProjectHelper.IProjectFilter;

import org.eclipse.core.filesystem.URIUtil;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IURIEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;


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
     * Returns true if the given string starts with the given prefix, using a
     * case-insensitive comparison.
     *
     * @param string the full string to be checked
     * @param prefix the prefix to be checked for
     * @return true if the string case-insensitively starts with the given prefix
     */
    public static boolean startsWithIgnoreCase(String string, String prefix) {
        return string.regionMatches(true /* ignoreCase */, 0, prefix, 0, prefix.length());
    }

    /**
     * Returns true if the given string starts at the given offset with the
     * given prefix, case insensitively.
     *
     * @param string the full string to be checked
     * @param offset the offset in the string to start looking
     * @param prefix the prefix to be checked for
     * @return true if the string case-insensitively starts at the given offset
     *         with the given prefix
     */
    public static boolean startsWith(String string, int offset, String prefix) {
        return string.regionMatches(true /* ignoreCase */, offset, prefix, 0, prefix.length());
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
     * Attempts to convert the given {@link URL} into a {@link File}.
     *
     * @param url the {@link URL} to be converted
     * @return the corresponding {@link File}, which may not exist
     */
    @NonNull
    public static File getFile(@NonNull URL url) {
        try {
            // First try URL.toURI(): this will work for URLs that contain %20 for spaces etc.
            // Unfortunately, it *doesn't* work for "broken" URLs where the URL contains
            // spaces, which is often the case.
            return new File(url.toURI());
        } catch (URISyntaxException e) {
            // ...so as a fallback, go to the old url.getPath() method, which handles space paths.
            return new File(url.getPath());
        }
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
        IPath location = resource.getRawLocation();
        if (location != null) {
            return location.makeAbsolute();
        } else {
            IWorkspace workspace = ResourcesPlugin.getWorkspace();
            IWorkspaceRoot root = workspace.getRoot();
            IPath workspacePath = root.getLocation();
            return workspacePath.append(resource.getFullPath());
        }
    }

    /**
     * Converts a {@link File} to an {@link IFile}, if possible.
     *
     * @param file a file to be converted
     * @return the corresponding {@link IFile}, or null
     */
    public static IFile fileToIFile(File file) {
        if (!file.isAbsolute()) {
            file = file.getAbsoluteFile();
        }

        IWorkspaceRoot workspace = ResourcesPlugin.getWorkspace().getRoot();
        IFile[] files = workspace.findFilesForLocationURI(file.toURI());
        if (files.length > 0) {
            return files[0];
        }

        IPath filePath = new Path(file.getPath());
        return pathToIFile(filePath);
    }

    /**
     * Converts a {@link File} to an {@link IResource}, if possible.
     *
     * @param file a file to be converted
     * @return the corresponding {@link IResource}, or null
     */
    public static IResource fileToResource(File file) {
        if (!file.isAbsolute()) {
            file = file.getAbsoluteFile();
        }

        IWorkspaceRoot workspace = ResourcesPlugin.getWorkspace().getRoot();
        IFile[] files = workspace.findFilesForLocationURI(file.toURI());
        if (files.length > 0) {
            return files[0];
        }

        IPath filePath = new Path(file.getPath());
        return pathToResource(filePath);
    }

    /**
     * Converts a {@link IPath} to an {@link IFile}, if possible.
     *
     * @param path a path to be converted
     * @return the corresponding {@link IFile}, or null
     */
    public static IFile pathToIFile(IPath path) {
        IWorkspaceRoot workspace = ResourcesPlugin.getWorkspace().getRoot();

        IFile[] files = workspace.findFilesForLocationURI(URIUtil.toURI(path.makeAbsolute()));
        if (files.length > 0) {
            return files[0];
        }

        IPath workspacePath = workspace.getLocation();
        if (workspacePath.isPrefixOf(path)) {
            IPath relativePath = path.makeRelativeTo(workspacePath);
            IResource member = workspace.findMember(relativePath);
            if (member instanceof IFile) {
                return (IFile) member;
            }
        } else if (path.isAbsolute()) {
            return workspace.getFileForLocation(path);
        }

        return null;
    }

    /**
     * Converts a {@link IPath} to an {@link IResource}, if possible.
     *
     * @param path a path to be converted
     * @return the corresponding {@link IResource}, or null
     */
    public static IResource pathToResource(IPath path) {
        IWorkspaceRoot workspace = ResourcesPlugin.getWorkspace().getRoot();

        IFile[] files = workspace.findFilesForLocationURI(URIUtil.toURI(path.makeAbsolute()));
        if (files.length > 0) {
            return files[0];
        }

        IPath workspacePath = workspace.getLocation();
        if (workspacePath.isPrefixOf(path)) {
            IPath relativePath = path.makeRelativeTo(workspacePath);
            return workspace.findMember(relativePath);
        } else if (path.isAbsolute()) {
            return workspace.getFileForLocation(path);
        }

        return null;
    }

    /**
     * Returns all markers in a file/document that fit on the same line as the given offset
     *
     * @param markerType the marker type
     * @param file the file containing the markers
     * @param document the document showing the markers
     * @param offset the offset to be checked
     * @return a list (possibly empty but never null) of matching markers
     */
    @NonNull
    public static List<IMarker> findMarkersOnLine(
            @NonNull String markerType,
            @NonNull IResource file,
            @NonNull IDocument document,
            int offset) {
        List<IMarker> matchingMarkers = new ArrayList<IMarker>(2);
        try {
            IMarker[] markers = file.findMarkers(markerType, true, IResource.DEPTH_ZERO);

            // Look for a match on the same line as the caret.
            IRegion lineInfo = document.getLineInformationOfOffset(offset);
            int lineStart = lineInfo.getOffset();
            int lineEnd = lineStart + lineInfo.getLength();
            int offsetLine = document.getLineOfOffset(offset);


            for (IMarker marker : markers) {
                int start = marker.getAttribute(IMarker.CHAR_START, -1);
                int end = marker.getAttribute(IMarker.CHAR_END, -1);
                if (start >= lineStart && start <= lineEnd && end > start) {
                    matchingMarkers.add(marker);
                } else if (start == -1 && end == -1) {
                    // Some markers don't set character range, they only set the line
                    int line = marker.getAttribute(IMarker.LINE_NUMBER, -1);
                    if (line == offsetLine + 1) {
                        matchingMarkers.add(marker);
                    }
                }
            }
        } catch (CoreException e) {
            AdtPlugin.log(e, null);
        } catch (BadLocationException e) {
            AdtPlugin.log(e, null);
        }

        return matchingMarkers;
    }

    /**
     * Returns the available and open Android projects
     *
     * @return the available and open Android projects, never null
     */
    @NonNull
    public static IJavaProject[] getOpenAndroidProjects() {
        return BaseProjectHelper.getAndroidProjects(new IProjectFilter() {
            @Override
            public boolean accept(IProject project) {
                return project.isAccessible();
            }
        });
    }

    /**
     * Returns a unique project name, based on the given {@code base} file name
     * possibly with a {@code conjunction} and a new number behind it to ensure
     * that the project name is unique. For example,
     * {@code getUniqueProjectName("project", "_")} will return
     * {@code "project"} if that name does not already exist, and if it does, it
     * will return {@code "project_2"}.
     *
     * @param base the base name to use, such as "foo"
     * @param conjunction a string to insert between the base name and the
     *            number.
     * @return a unique project name based on the given base and conjunction
     */
    public static String getUniqueProjectName(String base, String conjunction) {
        // We're using all workspace projects here rather than just open Android project
        // via getOpenAndroidProjects because the name cannot conflict with non-Android
        // or closed projects either
        IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
        IProject[] projects = workspaceRoot.getProjects();

        for (int i = 1; i < 1000; i++) {
            String name = i == 1 ? base : base + conjunction + Integer.toString(i);
            boolean found = false;
            for (IProject project : projects) {
                if (project.getName().equals(name)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return name;
            }
        }

        return base;
    }

    /**
     * Returns the name of the parent folder for the given editor input
     *
     * @param editorInput the editor input to check
     * @return the parent folder, which is never null but may be ""
     */
    @NonNull
    public static String getParentFolderName(@Nullable IEditorInput editorInput) {
        if (editorInput instanceof IFileEditorInput) {
             IFile file = ((IFileEditorInput) editorInput).getFile();
             return file.getParent().getName();
        }

        if (editorInput instanceof IURIEditorInput) {
            IURIEditorInput urlEditorInput = (IURIEditorInput) editorInput;
            String path = urlEditorInput.getURI().toString();
            int lastIndex = path.lastIndexOf('/');
            if (lastIndex != -1) {
                int lastLastIndex = path.lastIndexOf('/', lastIndex - 1);
                if (lastLastIndex != -1) {
                    return path.substring(lastLastIndex + 1, lastIndex);
                }
            }
        }

        return "";
    }
}
