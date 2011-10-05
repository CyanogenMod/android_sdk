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
package com.android.ide.eclipse.adt.internal.lint;

import static com.android.ide.eclipse.adt.AdtConstants.MARKER_LINT;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.AdtUtils;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutEditor;
import com.android.ide.eclipse.adt.internal.preferences.AdtPrefs;
import com.android.ide.eclipse.adt.internal.project.BaseProjectHelper;
import com.android.tools.lint.api.DetectorRegistry;
import com.android.tools.lint.api.IDomParser;
import com.android.tools.lint.api.ToolContext;
import com.android.tools.lint.checks.BuiltinDetectorRegistry;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Position;
import com.android.tools.lint.detector.api.Severity;
import com.android.util.Pair;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.editors.text.TextFileDocumentProvider;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.wst.sse.core.StructuredModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.sse.core.internal.provisional.IndexedRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocument;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMModel;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Eclipse implementation for running lint on workspace files and projects.
 */
@SuppressWarnings("restriction") // DOM model
public class LintEclipseContext implements ToolContext, IDomParser {
    static final String MARKER_CHECKID_PROPERTY = "checkid";    //$NON-NLS-1$
    private static final String DOCUMENT_PROPERTY = "document"; //$NON-NLS-1$
    private final DetectorRegistry mRegistry;
    private final IResource mResource;
    private final IDocument mDocument;
    private boolean mFatal;
    private Map<Issue, Severity> mSeverities;
    private String mDisabledIdsList; // String version of map: used to detect when to replace map
    private Set<String> mDisabledIds;


    /**
     * Creates a new {@link LintEclipseContext}.
     *
     * @param registry the associated detector registry
     * @param resource the associated resource (project, file or null)
     * @param document the associated document, or null if the {@code resource}
     *            param is not a file
     */
    public LintEclipseContext(DetectorRegistry registry, IResource resource, IDocument document) {
        mRegistry = registry;
        mResource = resource;
        mDocument = document;
    }

    // ----- Implements ToolContext -----

    public void log(Throwable exception, String format, Object... args) {
        if (exception == null) {
            AdtPlugin.log(IStatus.WARNING, format, args);
        } else {
            AdtPlugin.log(exception, format, args);
        }
    }

    public IDomParser getParser() {
        return this;
    }

    public boolean isEnabled(Issue issue) {
        IPreferenceStore store = AdtPlugin.getDefault().getPreferenceStore();
        String idList = store.getString(AdtPrefs.PREFS_DISABLED_ISSUES);
        if (idList == null) {
            idList = ""; //$NON-NLS-1$
        }
        if (mDisabledIds == null || !mDisabledIdsList.equals(idList)) {
            mDisabledIdsList = idList;
            if (idList.length() > 0) {
                String[] ids = idList.split(","); //$NON-NLS-1$
                mDisabledIds = new HashSet<String>(ids.length);
                for (String s : ids) {
                    mDisabledIds.add(s);
                }
            } else {
                mDisabledIds = Collections.emptySet();
            }
        }

        return !mDisabledIds.contains(issue.getId());
    }

    // ----- Implements IDomParser -----
    public Document parse(Context context) {
        // Map File to IFile
        IFile file = AdtUtils.fileToIFile(context.file);
        if (file == null || !file.exists()) {
            String path = context.file.getPath();
            AdtPlugin.log(IStatus.ERROR, "Can't find file %1$s in workspace", path);
            return null;
        }

        IStructuredModel model = null;
        try {
            IModelManager modelManager = StructuredModelManager.getModelManager();
            model = modelManager.getModelForRead(file);
            if (model instanceof IDOMModel) {
                context.setProperty(DOCUMENT_PROPERTY, model.getStructuredDocument());
                IDOMModel domModel = (IDOMModel) model;
                return domModel.getDocument();
            }
        } catch (IOException e) {
            AdtPlugin.log(e, "Cannot read XML file");
        } catch (CoreException e) {
            AdtPlugin.log(e, null);
        } finally {
            if (model != null) {
                // TODO: This may be too early...
                model.releaseFromRead();
            }
        }

        return null;
    }

    private static Position getPosition(Context context, int offset) {
        IStructuredDocument doc = (IStructuredDocument) context.getProperty(DOCUMENT_PROPERTY);
        if (doc != null && offset < doc.getLength()) {
            int line = doc.getLineOfOffset(offset);
            int column = -1;
            try {
                int lineOffset = doc.getLineOffset(line);
                column = offset - lineOffset;
            } catch (BadLocationException e) {
                AdtPlugin.log(e, null);
            }
            return new OffsetPosition(line, column, offset);
        }

        return null;
    }

    public Position getStartPosition(Context context, Node node) {
        if (node instanceof IndexedRegion) {
            IndexedRegion region = (IndexedRegion) node;
            return getPosition(context, region.getStartOffset());
        }

        return null;
    }

    public Position getEndPosition(Context context, Node node) {
        if (node instanceof IndexedRegion) {
            IndexedRegion region = (IndexedRegion) node;
            return getPosition(context, region.getEndOffset());
        }

        return null;
    }

    public Severity getSeverity(Issue issue) {
        if (mSeverities == null) {
            mSeverities = new HashMap<Issue, Severity>();
            IPreferenceStore store = AdtPlugin.getDefault().getPreferenceStore();
            String assignments = store.getString(AdtPrefs.PREFS_LINT_SEVERITIES);
            if (assignments != null && assignments.length() > 0) {
                for (String assignment : assignments.split(",")) { //$NON-NLS-1$
                    String[] s = assignment.split("="); //$NON-NLS-1$
                    if (s.length == 2) {
                        Issue d = mRegistry.getIssue(s[0]);
                        if (d != null) {
                            Severity severity = Severity.valueOf(s[1]);
                            if (severity != null) {
                                mSeverities.put(d, severity);
                            }
                        }
                    }
                }
            }
        }

        Severity severity = mSeverities.get(issue);
        if (severity != null) {
            return severity;
        }

        return issue.getDefaultSeverity();
    }

    /**
     * Sets the custom severity for the given detector to the given new severity
     *
     * @param severities a map from detector to severity to use from now on
     */
    public void setSeverities(Map<Issue, Severity> severities) {
        mSeverities = null;

        IPreferenceStore store = AdtPlugin.getDefault().getPreferenceStore();
        if (severities.size() == 0) {
            store.setToDefault(AdtPrefs.PREFS_LINT_SEVERITIES);
            return;
        }
        List<Issue> sortedKeys = new ArrayList<Issue>(severities.keySet());
        Collections.sort(sortedKeys);

        StringBuilder sb = new StringBuilder(severities.size() * 20);
        for (Issue issue : sortedKeys) {
            Severity severity = severities.get(issue);
            if (severity != issue.getDefaultSeverity()) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(issue.getId());
                sb.append('=');
                sb.append(severity.name());
            }
        }

        if (sb.length() > 0) {
            store.setValue(AdtPrefs.PREFS_LINT_SEVERITIES, sb.toString());
        } else {
            store.setToDefault(AdtPrefs.PREFS_LINT_SEVERITIES);
        }
    }

    public void report(Issue issue, Location location, String message) {
        if (!isEnabled(issue)) {
            return;
        }

        Severity s = getSeverity(issue);
        if (s == Severity.IGNORE) {
            return;
        }

        int severity = getMarkerSeverity(s);
        IMarker marker = null;
        if (location == null) {
            marker = BaseProjectHelper.markResource(mResource, MARKER_LINT,
                    message, 0, severity);
        } else {
            Position startPosition = location.getStart();
            if (startPosition == null) {
                marker = BaseProjectHelper.markResource(mResource, MARKER_LINT,
                        message, 0, severity);
            } else {
                Position endPosition = location.getEnd();
                int line = startPosition.getLine() + 1; // Marker API is 1-based
                IFile file = AdtUtils.fileToIFile(location.getFile());
                if (file != null) {
                    Pair<Integer, Integer> r = getRange(file, mDocument,
                            startPosition, endPosition);
                    int startOffset = r.getFirst();
                    int endOffset = r.getSecond();

                    marker = BaseProjectHelper.markResource(file, MARKER_LINT,
                            message, line, startOffset, endOffset, severity);
                }
            }
        }

        if (marker != null) {
            // Store marker id such that we can recognize it from the suppress quickfix
            try {
                marker.setAttribute(MARKER_CHECKID_PROPERTY, issue.getId());
            } catch (CoreException e) {
                AdtPlugin.log(e, null);
            }
        }

        if (s == Severity.ERROR) {
            mFatal = true;
        }
    }

    public boolean isSuppressed(Issue issue, Location range, String message, Severity severity) {
        // Not yet supported
        return false;
    }

    /** Clears any lint markers from the given resource (project, folder or file) */
    static void clearMarkers(IResource resource) {
        try {
            resource.deleteMarkers(MARKER_LINT, false, IResource.DEPTH_INFINITE);
        } catch (CoreException e) {
            AdtPlugin.log(e, null);
        }

        IEditorPart active = AdtUtils.getActiveEditor();
        if (active instanceof LayoutEditor) {
            LayoutEditor editor = (LayoutEditor) active;
            editor.getGraphicalEditor().getLayoutActionBar().updateErrorIndicator();
        }
    }

    /**
     * Returns whether the given resource has one or more lint markers
     *
     * @param resource the resource to be checked, typically a source file
     * @return true if the given resource has one or more lint markers
     */
    public static boolean hasMarkers(IResource resource) {
        return getMarkers(resource).length > 0;
    }

    /**
     * Returns the lint marker for the given resource (which may be a project, folder or file)
     *
     * @param resource the resource to be checked, typically a source file
     * @return an array of markers, possibly empty but never null
     */
    public static IMarker[] getMarkers(IResource resource) {
        try {
            return resource.findMarkers(MARKER_LINT, false, IResource.DEPTH_INFINITE);
        } catch (CoreException e) {
            AdtPlugin.log(e, null);
        }

        return new IMarker[0];
    }

    private static int getMarkerSeverity(Severity severity) {
        switch (severity) {
            case INFORMATIONAL:
                return IMarker.SEVERITY_INFO;
            case WARNING:
                return IMarker.SEVERITY_WARNING;
            case ERROR:
            default:
                return IMarker.SEVERITY_ERROR;
        }
    }

    private static Pair<Integer, Integer> getRange(IFile file, IDocument doc,
            Position startPosition, Position endPosition) {
        int startOffset = startPosition.getOffset();
        int endOffset = endPosition != null ? endPosition.getOffset() : -1;
        if (endOffset != -1) {
            // Attribute ranges often include trailing whitespace; trim this up
            if (doc == null) {
                IDocumentProvider provider = new TextFileDocumentProvider();
                try {
                    provider.connect(file);
                    doc = provider.getDocument(file);
                    if (doc != null) {
                        return trimTrailingSpace(doc, startOffset, endOffset);
                    }
                } catch (Exception e) {
                    AdtPlugin.log(e, "Can't find range information for %1$s", file.getName());
                } finally {
                    provider.disconnect(file);
                }
            } else {
                return trimTrailingSpace(doc, startOffset, endOffset);
            }
        }

        return Pair.of(startOffset, startOffset);
    }

    /** Trim off any trailing space on the given offset range in the given document */
    private static Pair<Integer, Integer> trimTrailingSpace(IDocument doc, int startOffset,
            int endOffset) {
        if (doc != null) {
            while (endOffset > startOffset && endOffset < doc.getLength()) {
                try {
                    if (!Character.isWhitespace(doc.getChar(endOffset - 1))) {
                        break;
                    } else {
                        endOffset--;
                    }
                } catch (BadLocationException e) {
                    // Pass - we've already validated offset range above
                    break;
                }
            }
        }

        return Pair.of(startOffset, endOffset);
    }

    /**
     * Returns true if a fatal error was encountered
     *
     * @return true if a fatal error was encountered
     */
    public boolean isFatal() {
        return mFatal;
    }

    /**
     * Show a dialog with errors for the given file
     *
     * @param shell the parent shell to attach the dialog to
     * @param file the file to show the errors for
     */
    public static void showErrors(Shell shell, final IFile file) {
        LintListDialog dialog = new LintListDialog(shell, file);
        dialog.open();
    }

    /**
     * Returns the registry of detectors to use from within Eclipse. This method
     * should be used rather than calling
     * {@link BuiltinDetectorRegistry#BuiltinDetectorRegistry} directly since it can replace
     * some detectors with Eclipse-optimized replacements.
     *
     * @return the detector registry to use to access detectors and issues
     */
    public static DetectorRegistry getRegistry() {
        return new BuiltinDetectorRegistry();
    }

    private static class OffsetPosition extends Position {
        /** The line number (0-based where the first line is line 0) */
        private final int mLine;

        /**
         * The column number (where the first character on the line is 0), or -1 if
         * unknown
         */
        private final int mColumn;

        /** The character offset */
        private final int mOffset;

        /**
         * Creates a new {@link Position}
         *
         * @param line the 0-based line number, or -1 if unknown
         * @param column the 0-based column number, or -1 if unknown
         * @param offset the offset, or -1 if unknown
         */
        public OffsetPosition(int line, int column, int offset) {
            super();
            this.mLine = line;
            this.mColumn = column;
            this.mOffset = offset;
        }

        /**
         * Returns the line number (0-based where the first line is line 0)
         *
         * @return the 0-based line number
         */
        @Override
        public int getLine() {
            return mLine;
        }

        @Override
        public int getOffset() {
            return mOffset;
        }

        /**
         * Returns the column number (where the first character on the line is 0),
         * or -1 if unknown
         *
         * @return the 0-based column number
         */
        @Override
        public int getColumn() {
            return mColumn;
        }
    }
}
