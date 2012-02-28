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

import static com.android.ide.eclipse.adt.AdtConstants.DOT_XML;
import static com.android.ide.eclipse.adt.AdtConstants.MARKER_LINT;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.AdtUtils;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutEditorDelegate;
import com.android.ide.eclipse.adt.internal.preferences.AdtPrefs;
import com.android.ide.eclipse.adt.internal.project.BaseProjectHelper;
import com.android.tools.lint.checks.BuiltinIssueRegistry;
import com.android.tools.lint.client.api.Configuration;
import com.android.tools.lint.client.api.IDomParser;
import com.android.tools.lint.client.api.IJavaParser;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.DefaultPosition;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Location.Handle;
import com.android.tools.lint.detector.api.Position;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.util.Pair;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.internal.compiler.CompilationResult;
import org.eclipse.jdt.internal.compiler.DefaultErrorHandlingPolicies;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.batch.CompilationUnit;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.parser.Parser;
import org.eclipse.jdt.internal.compiler.problem.AbortCompilation;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblemFactory;
import org.eclipse.jdt.internal.compiler.problem.ProblemReporter;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.editors.text.TextFileDocumentProvider;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.wst.sse.core.StructuredModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.sse.core.internal.provisional.IndexedRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocument;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMModel;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import lombok.ast.ecj.EcjTreeConverter;
import lombok.ast.grammar.ParseProblem;
import lombok.ast.grammar.Source;

/**
 * Eclipse implementation for running lint on workspace files and projects.
 */
@SuppressWarnings("restriction") // DOM model
public class EclipseLintClient extends LintClient implements IDomParser {
    static final String MARKER_CHECKID_PROPERTY = "checkid";    //$NON-NLS-1$
    private static final String MODEL_PROPERTY = "model";       //$NON-NLS-1$
    private final List<? extends IResource> mResources;
    private final IDocument mDocument;
    private boolean mWasFatal;
    private boolean mFatalOnly;
    private EclipseJavaParser mJavaParser;

    /**
     * Creates a new {@link EclipseLintClient}.
     *
     * @param registry the associated detector registry
     * @param resources the associated resources (project, file or null)
     * @param document the associated document, or null if the {@code resource}
     *            param is not a file
     * @param fatalOnly whether only fatal issues should be reported (and therefore checked)
     */
    public EclipseLintClient(IssueRegistry registry, List<? extends IResource> resources,
            IDocument document, boolean fatalOnly) {
        mResources = resources;
        mDocument = document;
        mFatalOnly = fatalOnly;
    }

    // ----- Extends LintClient -----

    @Override
    public void log(Severity severity, Throwable exception, String format, Object... args) {
        if (exception == null) {
            AdtPlugin.log(IStatus.WARNING, format, args);
        } else {
            AdtPlugin.log(exception, format, args);
        }
    }

    @Override
    public IDomParser getDomParser() {
        return this;
    }

    @Override
    public IJavaParser getJavaParser() {
        if (mJavaParser == null) {
            mJavaParser = new EclipseJavaParser();
        }

        return mJavaParser;
    }

    // ----- Implements IDomParser -----

    @Override
    public Document parseXml(XmlContext context) {
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
                context.setProperty(MODEL_PROPERTY, model);
                IDOMModel domModel = (IDOMModel) model;
                return domModel.getDocument();
            }
        } catch (IOException e) {
            AdtPlugin.log(e, "Cannot read XML file");
        } catch (CoreException e) {
            AdtPlugin.log(e, null);
        }

        return null;
    }

    private IProject getProject(Project project) {
        if (mResources != null) {
            if (mResources.size() == 1) {
                return mResources.get(0).getProject();
            }

            for (IResource resource : mResources) {
                IProject p = resource.getProject();
                if (project.getDir().equals(AdtUtils.getAbsolutePath(p).toFile())) {
                    return p;
                }
            }
        }
        return null;
    }

    @Override
    public Configuration getConfiguration(Project project) {
        if (project != null) {
            IProject eclipseProject = getProject(project);
            if (eclipseProject != null) {
                return ProjectLintConfiguration.get(this, eclipseProject, mFatalOnly);
            }
        }

        return GlobalLintConfiguration.get();
    }

    @Override
    public void report(Context context, Issue issue, Severity s, Location location,
            String message, Object data) {
        int severity = getMarkerSeverity(s);
        IMarker marker = null;
        if (location != null) {
            Position startPosition = location.getStart();
            if (startPosition == null) {
                if (location.getFile() != null) {
                    IResource resource = AdtUtils.fileToResource(location.getFile());
                    if (resource != null && resource.isAccessible()) {
                        marker = BaseProjectHelper.markResource(resource, MARKER_LINT,
                                message, 0, severity);
                    }
                }
            } else {
                Position endPosition = location.getEnd();
                int line = startPosition.getLine() + 1; // Marker API is 1-based
                IFile file = AdtUtils.fileToIFile(location.getFile());
                if (file != null && file.isAccessible()) {
                    Pair<Integer, Integer> r = getRange(file, mDocument,
                            startPosition, endPosition);
                    int startOffset = r.getFirst();
                    int endOffset = r.getSecond();
                    marker = BaseProjectHelper.markResource(file, MARKER_LINT,
                            message, line, startOffset, endOffset, severity);
                }
            }
        }

        if (marker == null) {
            marker = BaseProjectHelper.markResource(mResources.get(0), MARKER_LINT,
                        message, 0, severity);
        }

        if (marker != null) {
            // Store marker id such that we can recognize it from the suppress quickfix
            try {
                marker.setAttribute(MARKER_CHECKID_PROPERTY, issue.getId());
            } catch (CoreException e) {
                AdtPlugin.log(e, null);
            }
        }

        if (s == Severity.FATAL) {
            mWasFatal = true;
        }
    }

    @Override
    @Nullable
    public File findResource(@NonNull String relativePath) {
        // Look within the $ANDROID_SDK
        String sdkFolder = AdtPrefs.getPrefs().getOsSdkFolder();
        if (sdkFolder != null) {
            File file = new File(sdkFolder, relativePath);
            if (file.exists()) {
                return file;
            }
        }

        return null;
    }

    /** Clears any lint markers from the given resource (project, folder or file) */
    static void clearMarkers(IResource resource) {
        clearMarkers(Collections.singletonList(resource));
    }

    /** Clears any lint markers from the given list of resource (project, folder or file) */
    static void clearMarkers(List<? extends IResource> resources) {
        for (IResource resource : resources) {
            try {
                if (resource.isAccessible()) {
                    resource.deleteMarkers(MARKER_LINT, false, IResource.DEPTH_INFINITE);
                }
            } catch (CoreException e) {
                AdtPlugin.log(e, null);
            }
        }

        LayoutEditorDelegate delegate = LayoutEditorDelegate.fromEditor(AdtUtils.getActiveEditor());
        if (delegate != null) {
            delegate.getGraphicalEditor().getLayoutActionBar().updateErrorIndicator();
        }
    }

    /**
     * Removes all markers of the given id from the given resource.
     *
     * @param resource the resource to remove markers from (file or project, or
     *            null for all open projects)
     * @param id the id for the issue whose markers should be deleted
     */
    public static void removeMarkers(IResource resource, String id) {
        if (resource == null) {
            IJavaProject[] androidProjects = BaseProjectHelper.getAndroidProjects(null);
            for (IJavaProject project : androidProjects) {
                IProject p = project.getProject();
                if (p != null) {
                    // Recurse, but with a different parameter so it will not continue recursing
                    removeMarkers(p, id);
                }
            }
            return;
        }
        IMarker[] markers = getMarkers(resource);
        for (IMarker marker : markers) {
            if (id.equals(getId(marker))) {
                try {
                    marker.delete();
                } catch (CoreException e) {
                    AdtPlugin.log(e, null);
                }
            }
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
            if (resource.isAccessible()) {
                return resource.findMarkers(MARKER_LINT, false, IResource.DEPTH_INFINITE);
            }
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
            case FATAL:
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
                        return adjustOffsets(doc, startOffset, endOffset);
                    }
                } catch (Exception e) {
                    AdtPlugin.log(e, "Can't find range information for %1$s", file.getName());
                } finally {
                    provider.disconnect(file);
                }
            } else {
                return adjustOffsets(doc, startOffset, endOffset);
            }
        }

        return Pair.of(startOffset, startOffset);
    }

    /**
     * Trim off any trailing space on the given offset range in the given
     * document, and don't span multiple lines on ranges since it makes (for
     * example) the XML editor just glow with yellow underlines for all the
     * attributes etc. Highlighting just the element beginning gets the point
     * across. It also makes it more obvious where there are warnings on both
     * the overall element and on individual attributes since without this the
     * warnings on attributes would just overlap with the whole-element
     * highlighting.
     */
    private static Pair<Integer, Integer> adjustOffsets(IDocument doc, int startOffset,
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

            // Also don't span lines
            int lineEnd = startOffset;
            while (lineEnd < endOffset) {
                try {
                    char c = doc.getChar(lineEnd);
                    if (c == '\n' || c == '\r') {
                        endOffset = lineEnd;
                        break;
                    }
                } catch (BadLocationException e) {
                    // Pass - we've already validated offset range above
                    break;
                }
                lineEnd++;
            }
        }

        return Pair.of(startOffset, endOffset);
    }

    /**
     * Returns true if a fatal error was encountered
     *
     * @return true if a fatal error was encountered
     */
    public boolean hasFatalErrors() {
        return mWasFatal;
    }

    /**
     * Describe the issue for the given marker
     *
     * @param marker the marker to look up
     * @return a full description of the corresponding issue, never null
     */
    public static String describe(IMarker marker) {
        IssueRegistry registry = getRegistry();
        String markerId = getId(marker);
        Issue issue = registry.getIssue(markerId);
        if (issue == null) {
            return "";
        }

        String summary = issue.getDescription();
        String explanation = issue.getExplanation();

        StringBuilder sb = new StringBuilder(summary.length() + explanation.length() + 20);
        try {
            sb.append((String) marker.getAttribute(IMarker.MESSAGE));
            sb.append('\n').append('\n');
        } catch (CoreException e) {
        }
        sb.append("Issue: ");
        sb.append(summary);
        sb.append('\n');
        sb.append("Id: ");
        sb.append(issue.getId());
        sb.append('\n').append('\n');
        sb.append(explanation);

        if (issue.getMoreInfo() != null) {
            sb.append('\n').append('\n');
            sb.append(issue.getMoreInfo());
        }

        return sb.toString();
    }

    /**
     * Returns the id for the given marker
     *
     * @param marker the marker to look up
     * @return the corresponding issue id, or null
     */
    public static String getId(IMarker marker) {
        try {
            return (String) marker.getAttribute(MARKER_CHECKID_PROPERTY);
        } catch (CoreException e) {
            return null;
        }
    }

    /**
     * Shows the given marker in the editor
     *
     * @param marker the marker to be shown
     */
    public static void showMarker(IMarker marker) {
        IRegion region = null;
        try {
            int start = marker.getAttribute(IMarker.CHAR_START, -1);
            int end = marker.getAttribute(IMarker.CHAR_END, -1);
            if (start >= 0 && end >= 0) {
                region = new org.eclipse.jface.text.Region(start, end - start);
            }

            IResource resource = marker.getResource();
            if (resource instanceof IFile) {
                IEditorPart editor =
                        AdtPlugin.openFile((IFile) resource, region, true /* showEditorTab */);
                if (editor != null) {
                    IDE.gotoMarker(editor, marker);
                }
            }
        } catch (PartInitException ex) {
            AdtPlugin.log(ex, null);
        }
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

    @Override
    public String readFile(File f) {
        // Map File to IFile
        IFile file = AdtUtils.fileToIFile(f);
        if (file == null || !file.exists()) {
            String path = f.getPath();
            AdtPlugin.log(IStatus.ERROR, "Can't find file %1$s in workspace", path);
            return readPlainFile(f);
        }

        if (AdtUtils.endsWithIgnoreCase(file.getName(), DOT_XML)) {
            IStructuredModel model = null;
            try {
                IModelManager modelManager = StructuredModelManager.getModelManager();
                model = modelManager.getModelForRead(file);
                return model.getStructuredDocument().get();
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
        }

        return readPlainFile(f);
    }

    private String readPlainFile(File file) {
        try {
            return LintUtils.getEncodedString(file);
        } catch (IOException e) {
            return ""; //$NON-NLS-1$
        }
    }

    @Override
    public Location getLocation(XmlContext context, Node node) {
        IStructuredModel model = (IStructuredModel) context.getProperty(MODEL_PROPERTY);
        return new LazyLocation(context.file, model.getStructuredDocument(), (IndexedRegion) node);
    }

    @Override
    public Handle createLocationHandle(final XmlContext context, final Node node) {
        IStructuredModel model = (IStructuredModel) context.getProperty(MODEL_PROPERTY);
        return new LazyLocation(context.file, model.getStructuredDocument(), (IndexedRegion) node);
    }

    /**
     * Returns the registry of issues to check from within Eclipse.
     *
     * @return the issue registry to use to access detectors and issues
     */
    public static IssueRegistry getRegistry() {
        return new BuiltinIssueRegistry();
    }

    @Override
    public Class<? extends Detector> replaceDetector(Class<? extends Detector> detectorClass) {
        return detectorClass;
    }

    @Override
    public void dispose(XmlContext context, Document document) {
        IStructuredModel model = (IStructuredModel) context.getProperty(MODEL_PROPERTY);
        assert model != null : context.file;
        if (model != null) {
            model.releaseFromRead();
        }
    }

    private static class LazyLocation extends Location implements Location.Handle {
        private final IStructuredDocument mDocument;
        private final IndexedRegion mRegion;
        private Position mStart;
        private Position mEnd;

        public LazyLocation(File file, IStructuredDocument document, IndexedRegion region) {
            super(file, null /*start*/, null /*end*/);
            mDocument = document;
            mRegion = region;
        }

        @Override
        public Position getStart() {
            if (mStart == null) {
                int line = -1;
                int column = -1;
                int offset = mRegion.getStartOffset();

                if (mRegion instanceof org.w3c.dom.Text && mDocument != null) {
                    // For text nodes, skip whitespace prefix, if any
                    for (int i = offset;
                            i < mRegion.getEndOffset() && i < mDocument.getLength(); i++) {
                        try {
                            char c = mDocument.getChar(i);
                            if (!Character.isWhitespace(c)) {
                                offset = i;
                                break;
                            }
                        } catch (BadLocationException e) {
                            break;
                        }
                    }
                }

                if (mDocument != null && offset < mDocument.getLength()) {
                    line = mDocument.getLineOfOffset(offset);
                    column = -1;
                    try {
                        int lineOffset = mDocument.getLineOffset(line);
                        column = offset - lineOffset;
                    } catch (BadLocationException e) {
                        AdtPlugin.log(e, null);
                    }
                }

                mStart = new DefaultPosition(line, column, offset);
            }

            return mStart;
        }

        @Override
        public Position getEnd() {
            if (mEnd == null) {
                mEnd = new DefaultPosition(-1, -1, mRegion.getEndOffset());
            }

            return mEnd;
        }

        @Override
        public Location resolve() {
            return this;
        }
    }

    private static class EclipseJavaParser implements IJavaParser {
        private static final boolean USE_ECLIPSE_PARSER = true;
        private final Parser mParser;

        EclipseJavaParser() {
            if (USE_ECLIPSE_PARSER) {
                CompilerOptions options = new CompilerOptions();
                // Read settings from project? Note that this doesn't really matter because
                // we will only be parsing, not actually compiling.
                options.complianceLevel = ClassFileConstants.JDK1_6;
                options.sourceLevel = ClassFileConstants.JDK1_6;
                options.targetJDK = ClassFileConstants.JDK1_6;
                options.parseLiteralExpressionsAsConstants = true;
                ProblemReporter problemReporter = new ProblemReporter(
                        DefaultErrorHandlingPolicies.exitOnFirstError(),
                        options,
                        new DefaultProblemFactory());
                mParser = new Parser(problemReporter, options.parseLiteralExpressionsAsConstants);
                mParser.javadocParser.checkDocComment = false;
            } else {
                mParser = null;
            }
        }

        @Override
        public lombok.ast.Node parseJava(JavaContext context) {
            if (USE_ECLIPSE_PARSER) {
                // Use Eclipse's compiler
                EcjTreeConverter converter = new EcjTreeConverter();
                String code = context.getContents();

                CompilationUnit sourceUnit = new CompilationUnit(code.toCharArray(),
                        context.file.getName(), "UTF-8"); //$NON-NLS-1$
                CompilationResult compilationResult = new CompilationResult(sourceUnit, 0, 0, 0);
                CompilationUnitDeclaration unit = null;
                try {
                    unit = mParser.parse(sourceUnit, compilationResult);
                } catch (AbortCompilation e) {

                    String message;
                    Location location;
                    if (e.problem != null) {
                        CategorizedProblem problem = e.problem;
                        message = problem.getMessage();
                        location = Location.create(context.file,
                                new DefaultPosition(problem.getSourceLineNumber() - 1, -1,
                                        problem.getSourceStart()),
                                new DefaultPosition(problem.getSourceLineNumber() - 1, -1,
                                        problem.getSourceEnd()));
                    } else {
                        location = Location.create(context.file);
                        message = e.getCause() != null ? e.getCause().getLocalizedMessage() :
                            e.getLocalizedMessage();
                    }

                    context.report(IssueRegistry.PARSER_ERROR, location, message, null);
                    return null;
                }
                if (unit == null) {
                    return null;
                }

                try {
                    converter.visit(code, unit);
                    List<? extends lombok.ast.Node> nodes = converter.getAll();

                    // There could be more than one node when there are errors; pick out the
                    // compilation unit node
                    for (lombok.ast.Node node : nodes) {
                        if (node instanceof lombok.ast.CompilationUnit) {
                            return node;
                        }
                    }

                    return null;
                } catch (Throwable t) {
                    AdtPlugin.log(t, "Failed converting ECJ parse tree to Lombok for file %1$s",
                            context.file.getPath());
                    return null;
                }
            } else {
                // Use Lombok for now
                Source source = new Source(context.getContents(), context.file.getName());
                List<lombok.ast.Node> nodes = source.getNodes();

                // Don't analyze files containing errors
                List<ParseProblem> problems = source.getProblems();
                if (problems != null && problems.size() > 0) {
                    /* Silently ignore the errors. There are still some bugs in Lombok/Parboiled
                     * (triggered if you run lint on the AOSP framework directory for example),
                     * and having these show up as fatal errors when it's really a tool bug
                     * is bad. To make matters worse, the error messages aren't clear:
                     * http://code.google.com/p/projectlombok/issues/detail?id=313
                    for (ParseProblem problem : problems) {
                        lombok.ast.Position position = problem.getPosition();
                        Location location = Location.create(context.file,
                                context.getContents(), position.getStart(), position.getEnd());
                        String message = problem.getMessage();
                        context.report(
                                IssueRegistry.PARSER_ERROR, location,
                                message,
                                null);

                    }
                    */
                    return null;
                }

                // There could be more than one node when there are errors; pick out the
                // compilation unit node
                for (lombok.ast.Node node : nodes) {
                    if (node instanceof lombok.ast.CompilationUnit) {
                        return node;
                    }
                }
                return null;
            }
        }

        @Override
        public Location getLocation(JavaContext context, lombok.ast.Node node) {
            lombok.ast.Position position = node.getPosition();
            return Location.create(context.file, context.getContents(),
                    position.getStart(), position.getEnd());
        }

        @Override
        public Handle createLocationHandle(JavaContext context, lombok.ast.Node node) {
            return new LocationHandle(context.file, node);
        }

        @Override
        public void dispose(JavaContext context, lombok.ast.Node compilationUnit) {
        }

        /* Handle for creating positions cheaply and returning full fledged locations later */
        private class LocationHandle implements Handle {
            private File mFile;
            private lombok.ast.Node mNode;
            private Object mClientData;

            public LocationHandle(File file, lombok.ast.Node node) {
                mFile = file;
                mNode = node;
            }

            @Override
            public Location resolve() {
                lombok.ast.Position pos = mNode.getPosition();
                return Location.create(mFile, null /*contents*/, pos.getStart(), pos.getEnd());
            }

            @Override
            public void setClientData(@Nullable Object clientData) {
                mClientData = clientData;
            }

            @Override
            @Nullable
            public Object getClientData() {
                return mClientData;
            }
        }
    }
}

