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

import com.android.ide.eclipse.adt.AdtConstants;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.AdtUtils;
import com.android.ide.eclipse.adt.internal.editors.AndroidXmlEditor;
import com.android.ide.eclipse.adt.internal.preferences.AdtPrefs;
import com.android.tools.lint.detector.api.Issue;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.quickassist.IQuickAssistInvocationContext;
import org.eclipse.jface.text.quickassist.IQuickAssistProcessor;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IMarkerResolution;
import org.eclipse.ui.IMarkerResolution2;
import org.eclipse.ui.IMarkerResolutionGenerator2;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

import java.util.ArrayList;
import java.util.List;

/**
 * A quickfix and marker resolution for disabling lint checks, and any
 * IDE specific implementations for fixing the warnings.
 * <p>
 * I would really like for this quickfix to show up as a light bulb on top of the error
 * icon in the editor, and I've spent a whole day trying to make it work. I did not
 * succeed, but here are the steps I tried in case I want to pick up the work again
 * later:
 * <ul>
 * <li>
 *     The WST has some support for quick fixes, and I came across some forum posts
 *     referencing the ability to show light bulbs. However, it turns out that the
 *     quickfix support for annotations in WST is hardcoded to source validation
 *     errors *only*.
 * <li>
 *     I tried defining my own editor annotations, and customizing the icon directly
 *     by either setting an icon or using the image provider. This works fine
 *     if I make my marker be a new independent marker type. However, whenever I
 *     switch the marker type back to extend the "Problem" type, then the icon reverts
 *     back to the standard error icon and it ignores my custom settings.
 *     And if I switch away from the Problems marker type, then the errors no longer
 *     show up in the Problems view. (I also tried extending the JDT marker but that
 *     still didn't work.)
 * <li>
 *     It looks like only JDT handles quickfix icons. It has a bunch of custom code
 *     to handle this, along with its own Annotation subclass used by the editor.
 *     I tried duplicating some of this by subclassing StructuredTextEditor, but
 *     it was evident that I'd have to pull in a *huge* amount of duplicated code to
 *     make this work, which seems risky given that all this is internal code that
 *     can change from one Eclipse version to the next.
 * </ul>
 * It looks like our best bet would be to reconsider whether these should show up
 * in the Problems view; perhaps we should use a custom view for these. That would also
 * make marker management more obvious.
 */
public class LintFixGenerator implements IMarkerResolutionGenerator2, IQuickAssistProcessor {
    /** Constructs a new {@link LintFixGenerator} */
    public LintFixGenerator() {
    }

    // ---- Implements IMarkerResolutionGenerator2 ----

    public boolean hasResolutions(IMarker marker) {
        try {
            assert marker.getType().equals(AdtConstants.MARKER_LINT);
        } catch (CoreException e) {
        }

        return true;
    }

    public IMarkerResolution[] getResolutions(IMarker marker) {
        String id = marker.getAttribute(LintRunner.MARKER_CHECKID_PROPERTY,
                ""); //$NON-NLS-1$
        IResource resource = marker.getResource();
        return new IMarkerResolution[] {
                new MoreInfoProposal(id, marker.getAttribute(IMarker.MESSAGE, null)),
                new SuppressProposal(id, true /* all */),
                // Not yet implemented
                //new SuppressProposal(id, false),
                new ClearMarkersProposal(resource, false /* all */),
                new ClearMarkersProposal(resource, true /* all */),
        };
    }

    // ---- Implements IQuickAssistProcessor ----

    public String getErrorMessage() {
        return "Disable Lint Error";
    }

    public boolean canFix(Annotation annotation) {
        return true;
    }

    public boolean canAssist(IQuickAssistInvocationContext invocationContext) {
        return true;
    }

    public ICompletionProposal[] computeQuickAssistProposals(
            IQuickAssistInvocationContext invocationContext) {
        ISourceViewer sourceViewer = invocationContext.getSourceViewer();
        AndroidXmlEditor editor = AndroidXmlEditor.getAndroidXmlEditor(sourceViewer);
        if (editor != null) {
            IFile file = editor.getInputFile();
            IDocument document = sourceViewer.getDocument();
            List<IMarker> markers = AdtUtils.findMarkersOnLine(AdtConstants.MARKER_LINT,
                    file, document, invocationContext.getOffset());
            List<ICompletionProposal> proposals = new ArrayList<ICompletionProposal>();
            if (markers.size() > 0) {
                for (IMarker marker : markers) {
                    String id = marker.getAttribute(LintRunner.MARKER_CHECKID_PROPERTY,
                            ""); //$NON-NLS-1$
                    // TODO: Allow for more than one fix?
                    ICompletionProposal fix = LintFix.getFix(id, marker);
                    if (fix != null) {
                        proposals.add(fix);
                    }

                    String message = marker.getAttribute(IMarker.MESSAGE, null);
                    proposals.add(new MoreInfoProposal(id, message));

                    proposals.add(new SuppressProposal(id, true /* all */));
                    // Not yet implemented
                    //proposals.add(new SuppressProposal(id, false));

                    proposals.add(new ClearMarkersProposal(file, false /* all */));
                    proposals.add(new ClearMarkersProposal(file, true /* all */));
                }
            }
            if (proposals.size() > 0) {
                return proposals.toArray(new ICompletionProposal[proposals.size()]);
            }
        }

        return null;
    }

    /**
     * Suppress the given detector, and rerun the checks on the file
     *
     * @param id the id of the detector to be suppressed
     */
    public static void suppressDetector(String id) {
        // Excluded checks
        IPreferenceStore store = AdtPlugin.getDefault().getPreferenceStore();
        String value = store.getString(AdtPrefs.PREFS_DISABLED_ISSUES);
        assert value == null || !value.contains(id);
        if (value == null || value.length() == 0) {
            value = id;
        } else {
            value = value + ',' + id;
        }
        store.setValue(AdtPrefs.PREFS_DISABLED_ISSUES, value);

        // Rerun analysis on the current file to remove this and related markers.
        // TODO: if mGlobal, rerun on whole project?
        IEditorPart activeEditor = AdtUtils.getActiveEditor();
        if (activeEditor instanceof AndroidXmlEditor) {
            AndroidXmlEditor editor = (AndroidXmlEditor) activeEditor;
            LintRunner.startLint(editor.getInputFile(), editor.getStructuredDocument());
        }
    }

    private static class SuppressProposal implements ICompletionProposal, IMarkerResolution2 {
        private final String mId;
        private final boolean mGlobal;

        public SuppressProposal(String check, boolean global) {
            super();
            mId = check;
            mGlobal = global;
        }

        private void perform() {
            suppressDetector(mId);
        }

        public String getDisplayString() {
            return mGlobal ? "Disable Check" : "Disable Check in this file only";
        }

        // ---- Implements MarkerResolution2 ----

        public String getLabel() {
            return getDisplayString();
        }

        public void run(IMarker marker) {
            perform();
        }

        public String getDescription() {
            return getAdditionalProposalInfo();
        }

        // ---- Implements ICompletionProposal ----

        public void apply(IDocument document) {
            perform();
        }

        public Point getSelection(IDocument document) {
            return null;
        }

        public String getAdditionalProposalInfo() {
            StringBuilder sb = new StringBuilder(200);
            if (mGlobal) {
                sb.append("Suppresses this type of lint warning in all files.");
            } else {
                sb.append("Suppresses this type of lint warning in the current file only.");
            }
            sb.append("<br><br>"); //$NON-NLS-1$
            sb.append("You can re-enable checks from the \"Android > Lint Error Checking\" preference page.");

            return sb.toString();
        }

        public Image getImage() {
            ISharedImages sharedImages = PlatformUI.getWorkbench().getSharedImages();
            return sharedImages.getImage(ISharedImages.IMG_OBJS_WARN_TSK);
        }

        public IContextInformation getContextInformation() {
            return null;
        }
    }

    private static class ClearMarkersProposal implements ICompletionProposal, IMarkerResolution2 {
        private final boolean mGlobal;
        private final IResource mResource;

        public ClearMarkersProposal(IResource resource, boolean global) {
            mResource = resource;
            mGlobal = global;
        }

        private void perform() {
            IResource resource = mGlobal ? mResource.getProject() : mResource;
            LintEclipseContext.clearMarkers(resource);
        }

        public String getDisplayString() {
            return mGlobal ? "Clear All Lint Markers" : "Clear Markers in This File Only";
        }

        // ---- Implements MarkerResolution2 ----

        public String getLabel() {
            return getDisplayString();
        }

        public void run(IMarker marker) {
            perform();
        }

        public String getDescription() {
            return getAdditionalProposalInfo();
        }

        // ---- Implements ICompletionProposal ----

        public void apply(IDocument document) {
            perform();
        }

        public Point getSelection(IDocument document) {
            return null;
        }

        public String getAdditionalProposalInfo() {
            StringBuilder sb = new StringBuilder(200);
            if (mGlobal) {
                sb.append("Clears all lint warning markers from the project.");
            } else {
                sb.append("Clears all lint warnings from this file.");
            }
            sb.append("<br><br>"); //$NON-NLS-1$
            sb.append("This temporarily hides the problem, but does not suppress it. " +
                    "Running Lint again can bring the error back.");
            if (AdtPrefs.getPrefs().isLintOnSave()) {
                sb.append(' ');
                sb.append("This will happen the next time the file is saved since lint-on-save " +
                        "is enabled. You can turn this off in the \"Lint Error Checking\" " +
                        "preference page.");
            }

            return sb.toString();
        }

        public Image getImage() {
            ISharedImages sharedImages = PlatformUI.getWorkbench().getSharedImages();
            return sharedImages.getImage(ISharedImages.IMG_OBJS_WARN_TSK);
        }

        public IContextInformation getContextInformation() {
            return null;
        }
    }

    private static class MoreInfoProposal implements ICompletionProposal, IMarkerResolution2 {
        private final String mId;
        private final String mMessage;

        public MoreInfoProposal(String id, String message) {
            mId = id;
            mMessage = message;
        }

        private void perform() {
            Issue issue = LintEclipseContext.getRegistry().getIssue(mId);
            assert issue != null : mId;

            StringBuilder sb = new StringBuilder(300);
            sb.append(mMessage);
            sb.append('\n').append('\n');
            sb.append("Issue Explanation:");
            sb.append('\n');
            if (issue.getExplanation() != null) {
                sb.append('\n');
                sb.append(issue.getExplanation());
            } else {
                sb.append(issue.getDescription());
            }

            if (issue.getMoreInfo() != null) {
                sb.append('\n').append('\n');
                sb.append("More Information: ");
                sb.append(issue.getMoreInfo());
            }

            MessageDialog.openInformation(AdtPlugin.getDisplay().getActiveShell(), "More Info",
                    sb.toString());
        }

        public String getDisplayString() {
            return "Explain Issue";
        }

        // ---- Implements MarkerResolution2 ----

        public String getLabel() {
            return getDisplayString();
        }

        public void run(IMarker marker) {
            perform();
        }

        public String getDescription() {
            return getAdditionalProposalInfo();
        }

        // ---- Implements ICompletionProposal ----

        public void apply(IDocument document) {
            perform();
        }

        public Point getSelection(IDocument document) {
            return null;
        }

        public String getAdditionalProposalInfo() {
            return "Provides more information about this issue";
        }

        public Image getImage() {
            ISharedImages sharedImages = PlatformUI.getWorkbench().getSharedImages();
            return sharedImages.getImage(ISharedImages.IMG_OBJS_INFO_TSK);
        }

        public IContextInformation getContextInformation() {
            return null;
        }
    }

}
