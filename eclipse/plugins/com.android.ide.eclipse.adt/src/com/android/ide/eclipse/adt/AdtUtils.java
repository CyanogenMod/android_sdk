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

import static com.android.tools.lint.detector.api.LintConstants.TOOLS_PREFIX;
import static com.android.tools.lint.detector.api.LintConstants.TOOLS_URI;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.eclipse.adt.internal.editors.AndroidXmlEditor;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiElementNode;
import com.android.ide.eclipse.adt.internal.project.BaseProjectHelper;
import com.android.ide.eclipse.adt.internal.project.BaseProjectHelper.IProjectFilter;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.PkgProps;
import com.android.utils.XmlUtils;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;

import org.eclipse.core.filesystem.URIUtil;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IURIEditorInput;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.wst.sse.core.internal.provisional.IndexedRegion;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;


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
    @Nullable
    public static String extractClassName(@NonNull String string) {
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
     * Strips the given suffix from the given string, provided that the string ends with
     * the suffix.
     *
     * @param string the full string to strip from
     * @param suffix the suffix to strip out
     * @return the string without the suffix at the end
     */
    public static String stripSuffix(@NonNull String string, @NonNull String suffix) {
        if (string.endsWith(suffix)) {
            return string.substring(0, string.length() - suffix.length());
        }

        return string;
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
     * Converts a CamelCase word into an underlined_word
     *
     * @param string the CamelCase version of the word
     * @return the underlined version of the word
     */
    public static String camelCaseToUnderlines(String string) {
        if (string.isEmpty()) {
            return string;
        }

        StringBuilder sb = new StringBuilder(2 * string.length());
        int n = string.length();
        boolean lastWasUpperCase = Character.isUpperCase(string.charAt(0));
        for (int i = 0; i < n; i++) {
            char c = string.charAt(i);
            boolean isUpperCase = Character.isUpperCase(c);
            if (isUpperCase && !lastWasUpperCase) {
                sb.append('_');
            }
            lastWasUpperCase = isUpperCase;
            c = Character.toLowerCase(c);
            sb.append(c);
        }

        return sb.toString();
    }

    /**
     * Converts an underlined_word into a CamelCase word
     *
     * @param string the underlined word to convert
     * @return the CamelCase version of the word
     */
    public static String underlinesToCamelCase(String string) {
        StringBuilder sb = new StringBuilder(string.length());
        int n = string.length();

        int i = 0;
        boolean upcaseNext = true;
        for (; i < n; i++) {
            char c = string.charAt(i);
            if (c == '_') {
                upcaseNext = true;
            } else {
                if (upcaseNext) {
                    c = Character.toUpperCase(c);
                }
                upcaseNext = false;
                sb.append(c);
            }
        }

        return sb.toString();
    }

    /** For use by {@link #getLineSeparator()} */
    private static String sLineSeparator;

    /**
     * Returns the default line separator to use.
     * <p>
     * NOTE: If you have an associated {@link IDocument}, it is better to call
     * {@link TextUtilities#getDefaultLineDelimiter(IDocument)} since that will
     * allow (for example) editing a \r\n-delimited document on a \n-delimited
     * platform and keep a consistent usage of delimiters in the file.
     *
     * @return the delimiter string to use
     */
    @NonNull
    public static String getLineSeparator() {
        if (sLineSeparator == null) {
            // This is guaranteed to exist:
            sLineSeparator = System.getProperty("line.separator"); //$NON-NLS-1$
        }

        return sLineSeparator;
    }

    /**
     * Returns the current editor (the currently visible and active editor), or null if
     * not found
     *
     * @return the current editor, or null
     */
    public static IEditorPart getActiveEditor() {
        IWorkbench workbench = PlatformUI.getWorkbench();
        IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
        if (window == null) {
            IWorkbenchWindow[] windows = workbench.getWorkbenchWindows();
            if (windows.length > 0) {
                window = windows[0];
            }
        }
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
    @NonNull
    public static IPath getAbsolutePath(@NonNull IResource resource) {
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
     * Converts a workspace-relative path to an absolute file path
     *
     * @param path the workspace-relative path to convert
     * @return the corresponding absolute file in the file system
     */
    @NonNull
    public static File workspacePathToFile(@NonNull IPath path) {
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        IResource res = root.findMember(path);
        if (res != null) {
            IPath location = res.getLocation();
            if (location != null) {
                return location.toFile();
            }
            return root.getLocation().append(path).toFile();
        }

        return path.toFile();
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
                // Need to make case insensitive comparison, since otherwise we can hit
                // org.eclipse.core.internal.resources.ResourceException:
                // A resource exists with a different case: '/test'.
                if (project.getName().equalsIgnoreCase(name)) {
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

    /**
     * Sets the given tools: attribute in the given XML editor document, adding
     * the tools name space declaration if necessary, formatting the affected
     * document region, and optionally comma-appending to an existing value and
     * optionally opening and revealing the attribute.
     *
     * @param editor the associated editor
     * @param element the associated element
     * @param description the description of the attribute (shown in the undo
     *            event)
     * @param name the name of the attribute
     * @param value the attribute value
     * @param reveal if true, open the editor and select the given attribute
     *            node
     * @param appendValue if true, add this value as a comma separated value to
     *            the existing attribute value, if any
     */
    @SuppressWarnings("restriction") // DOM model
    public static void setToolsAttribute(
            @NonNull final AndroidXmlEditor editor,
            @NonNull final Element element,
            @NonNull final String description,
            @NonNull final String name,
            @Nullable final String value,
            final boolean reveal,
            final boolean appendValue) {
        editor.wrapUndoEditXmlModel(description, new Runnable() {
            @Override
            public void run() {
                String prefix = XmlUtils.lookupNamespacePrefix(element, TOOLS_URI, null);
                if (prefix == null) {
                    // Add in new prefix...
                    prefix = XmlUtils.lookupNamespacePrefix(element,
                            TOOLS_URI, TOOLS_PREFIX);
                    if (value != null) {
                        // ...and ensure that the header is formatted such that
                        // the XML namespace declaration is placed in the right
                        // position and wrapping is applied etc.
                        editor.scheduleNodeReformat(editor.getUiRootNode(),
                                true /*attributesOnly*/);
                    }
                }

                String v = value;
                if (appendValue && v != null) {
                    String prev = element.getAttributeNS(TOOLS_URI, name);
                    if (prev.length() > 0) {
                        v = prev + ',' + value;
                    }
                }

                // Use the non-namespace form of set attribute since we can't
                // reference the namespace until the model has been reloaded
                if (v != null) {
                    element.setAttribute(prefix + ':' + name, v);
                } else {
                    element.removeAttribute(prefix + ':' + name);
                }

                UiElementNode rootUiNode = editor.getUiRootNode();
                if (rootUiNode != null && v != null) {
                    final UiElementNode uiNode = rootUiNode.findXmlNode(element);
                    if (uiNode != null) {
                        editor.scheduleNodeReformat(uiNode, true /*attributesOnly*/);

                        if (reveal) {
                            // Update editor selection after format
                            Display display = AdtPlugin.getDisplay();
                            if (display != null) {
                                display.asyncExec(new Runnable() {
                                    @Override
                                    public void run() {
                                        Node xmlNode = uiNode.getXmlNode();
                                        Attr attribute = ((Element) xmlNode).getAttributeNodeNS(
                                                TOOLS_URI, name);
                                        if (attribute instanceof IndexedRegion) {
                                            IndexedRegion region = (IndexedRegion) attribute;
                                            editor.getStructuredTextEditor().selectAndReveal(
                                                    region.getStartOffset(), region.getLength());
                                        }
                                    }
                                });
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * Returns the applicable build code (for
     * {@code android.os.Build.VERSION_CODES}) for the corresponding API level,
     * or null if it's unknown.
     *
     * @param api the API level to look up a version code for
     * @return the corresponding build code field name, or null
     */
    @Nullable
    public static String getBuildCodes(int api) {
        // See http://developer.android.com/reference/android/os/Build.VERSION_CODES.html
        switch (api) {
            case 1:  return "BASE"; //$NON-NLS-1$
            case 2:  return "BASE_1_1"; //$NON-NLS-1$
            case 3:  return "CUPCAKE"; //$NON-NLS-1$
            case 4:  return "DONUT"; //$NON-NLS-1$
            case 5:  return "ECLAIR"; //$NON-NLS-1$
            case 6:  return "ECLAIR_0_1"; //$NON-NLS-1$
            case 7:  return "ECLAIR_MR1"; //$NON-NLS-1$
            case 8:  return "FROYO"; //$NON-NLS-1$
            case 9:  return "GINGERBREAD"; //$NON-NLS-1$
            case 10: return "GINGERBREAD_MR1"; //$NON-NLS-1$
            case 11: return "HONEYCOMB"; //$NON-NLS-1$
            case 12: return "HONEYCOMB_MR1"; //$NON-NLS-1$
            case 13: return "HONEYCOMB_MR2"; //$NON-NLS-1$
            case 14: return "ICE_CREAM_SANDWICH"; //$NON-NLS-1$
            case 15: return "ICE_CREAM_SANDWICH_MR1"; //$NON-NLS-1$
            case 16: return "JELLY_BEAN"; //$NON-NLS-1$
        }

        return null;
    }

    /**
     * Returns the Android version and code name of the given API level
     *
     * @param api the api level
     * @return a suitable version display name
     */
    public static String getAndroidName(int api) {
        // See http://source.android.com/source/build-numbers.html
        switch (api) {
            case 1:  return "API 1: Android 1.0";
            case 2:  return "API 2: Android 1.1";
            case 3:  return "API 3: Android 1.5 (Cupcake)";
            case 4:  return "API 4: Android 1.6 (Donut)";
            case 5:  return "API 5: Android 2.0 (Eclair)";
            case 6:  return "API 6: Android 2.0.1 (Eclair)";
            case 7:  return "API 7: Android 2.1 (Eclair)";
            case 8:  return "API 8: Android 2.2 (Froyo)";
            case 9:  return "API 9: Android 2.3 (Gingerbread)";
            case 10: return "API 10: Android 2.3.3 (Gingerbread)";
            case 11: return "API 11: Android 3.0 (Honeycomb)";
            case 12: return "API 12: Android 3.1 (Honeycomb)";
            case 13: return "API 13: Android 3.2 (Honeycomb)";
            case 14: return "API 14: Android 4.0 (IceCreamSandwich)";
            case 15: return "API 15: Android 4.0.3 (IceCreamSandwich)";
            case 16: return "API 16: Android 4.1 (Jelly Bean)";
            default: {
                // Consult SDK manager to see if we know any more (later) names,
                // installed by user
                Sdk sdk = Sdk.getCurrent();
                if (sdk != null) {
                    for (IAndroidTarget target : sdk.getTargets()) {
                        if (target.isPlatform()) {
                            AndroidVersion version = target.getVersion();
                            if (version.getApiLevel() == api) {
                                String codename = target.getProperty(PkgProps.PLATFORM_CODENAME);
                                if (codename != null) {
                                    return String.format("API %1$d: Android %2$s (%3$s)", api,
                                            target.getProperty("ro.build.version.release"), //$NON-NLS-1$
                                            codename);
                                }
                                return String.format("API %1$d: Android %2$s", api,
                                        target.getProperty("ro.build.version.release")); //$NON-NLS-1$
                            }
                        }
                    }
                }

                return "API " + api;

            }
        }
    }

    /**
     * Returns the highest known API level to this version of ADT. The
     * {@link #getAndroidName(int)} method will return real names up to and
     * including this number.
     *
     * @return the highest known API number
     */
    public static int getHighestKnownApiLevel() {
        return 16;
    }

    /**
     * Returns a list of known API names
     *
     * @return a list of string API names, starting from 1 and up through the
     *         maximum known versions (with no gaps)
     */
    public static String[] getKnownVersions() {
        int max = 15;
        Sdk sdk = Sdk.getCurrent();
        if (sdk != null) {
            for (IAndroidTarget target : sdk.getTargets()) {
                if (target.isPlatform()) {
                    AndroidVersion version = target.getVersion();
                    if (!version.isPreview()) {
                        max = Math.max(max, version.getApiLevel());
                    }
                }
            }
        }

        String[] versions = new String[max];
        for (int api = 1; api <= max; api++) {
            versions[api-1] = getAndroidName(api);
        }

        return versions;
    }

    /**
     * Returns the Android project(s) that are selected or active, if any. This
     * considers the selection, the active editor, etc.
     *
     * @param selection the current selection
     * @return a list of projects, possibly empty (but never null)
     */
    @NonNull
    public static List<IProject> getSelectedProjects(@Nullable ISelection selection) {
        List<IProject> projects = new ArrayList<IProject>();

        if (selection instanceof IStructuredSelection) {
            IStructuredSelection structuredSelection = (IStructuredSelection) selection;
            // get the unique selected item.
            Iterator<?> iterator = structuredSelection.iterator();
            while (iterator.hasNext()) {
                Object element = iterator.next();

                // First look up the resource (since some adaptables
                // provide an IResource but not an IProject, and we can
                // always go from IResource to IProject)
                IResource resource = null;
                if (element instanceof IResource) { // may include IProject
                   resource = (IResource) element;
                } else if (element instanceof IAdaptable) {
                    IAdaptable adaptable = (IAdaptable)element;
                    Object adapter = adaptable.getAdapter(IResource.class);
                    resource = (IResource) adapter;
                }

                // get the project object from it.
                IProject project = null;
                if (resource != null) {
                    project = resource.getProject();
                } else if (element instanceof IAdaptable) {
                    project = (IProject) ((IAdaptable) element).getAdapter(IProject.class);
                }

                if (project != null && !projects.contains(project)) {
                    projects.add(project);
                }
            }
        }

        if (projects.isEmpty()) {
            // Try to look at the active editor instead
            IFile file = AdtUtils.getActiveFile();
            if (file != null) {
                projects.add(file.getProject());
            }
        }

        if (projects.isEmpty()) {
            // If we didn't find a default project based on the selection, check how many
            // open Android projects we can find in the current workspace. If there's only
            // one, we'll just select it by default.
            IJavaProject[] open = AdtUtils.getOpenAndroidProjects();
            for (IJavaProject project : open) {
                projects.add(project.getProject());
            }
            return projects;
        } else {
            // Make sure all the projects are Android projects
            List<IProject> androidProjects = new ArrayList<IProject>(projects.size());
            for (IProject project : projects) {
                if (BaseProjectHelper.isAndroidProject(project)) {
                    androidProjects.add(project);
                }
            }
            return androidProjects;
        }
    }

    private static Boolean sEclipse4;

    /**
     * Returns true if the running Eclipse is version 4.x or later
     *
     * @return true if the current Eclipse version is 4.x or later, false
     *         otherwise
     */
    public static boolean isEclipse4() {
        if (sEclipse4 == null) {
            sEclipse4 = Platform.getBundle("org.eclipse.e4.ui.model.workbench") != null; //$NON-NLS-1$
        }

        return sEclipse4;
    }

    /**
     * Reads the contents of an {@link IFile} and return it as a byte array
     *
     * @param file the file to be read
     * @return the String read from the file, or null if there was an error
     */
    @SuppressWarnings("resource") // Eclipse doesn't understand Closeables.closeQuietly yet
    @Nullable
    public static byte[] readData(@NonNull IFile file) {
        InputStream contents = null;
        try {
            contents = file.getContents();
            return ByteStreams.toByteArray(contents);
        } catch (Exception e) {
            // Pass -- just return null
        } finally {
            Closeables.closeQuietly(contents);
        }

        return null;
    }

    /**
     * Ensure that a given folder (and all its parents) are created
     *
     * @param container the container to ensure exists
     * @throws CoreException if an error occurs
     */
    public static void ensureExists(@Nullable IContainer container) throws CoreException {
        if (container == null || container.exists()) {
            return;
        }
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        IFolder folder = root.getFolder(container.getFullPath());
        ensureExists(folder);
    }

    private static void ensureExists(IFolder folder) throws CoreException {
        if (folder != null && !folder.exists()) {
            IContainer parent = folder.getParent();
            if (parent instanceof IFolder) {
                ensureExists((IFolder) parent);
            }
            folder.create(false, false, null);
        }
    }

    /**
     * Format the given floating value into an XML string, omitting decimals if
     * 0
     *
     * @param value the value to be formatted
     * @return the corresponding XML string for the value
     */
    public static String formatFloatAttribute(float value) {
        if (value != (int) value) {
            // Run String.format without a locale, because we don't want locale-specific
            // conversions here like separating the decimal part with a comma instead of a dot!
            return String.format((Locale) null, "%.2f", value); //$NON-NLS-1$
        } else {
            return Integer.toString((int) value);
        }
    }

    /**
     * Creates all the directories required for the given path.
     *
     * @param wsPath the path to create all the parent directories for
     * @return true if all the parent directories were created
     */
    public static boolean createWsParentDirectory(IContainer wsPath) {
        if (wsPath.getType() == IResource.FOLDER) {
            if (wsPath.exists()) {
                return true;
            }

            IFolder folder = (IFolder) wsPath;
            try {
                if (createWsParentDirectory(wsPath.getParent())) {
                    folder.create(true /* force */, true /* local */, null /* monitor */);
                    return true;
                }
            } catch (CoreException e) {
                e.printStackTrace();
            }
        }

        return false;
    }

    /**
     * Lists the files of the given directory and returns them as an array which
     * is never null. This simplifies processing file listings from for each
     * loops since {@link File#listFiles} can return null. This method simply
     * wraps it and makes sure it returns an empty array instead if necessary.
     *
     * @param dir the directory to list
     * @return the children, or empty if it has no children, is not a directory,
     *         etc.
     */
    @NonNull
    public static File[] listFiles(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            return files;
        } else {
            return new File[0];
        }
    }
}
