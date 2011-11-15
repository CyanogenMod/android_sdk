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

import static com.android.ide.common.layout.LayoutConstants.ANDROID_URI;
import static com.android.ide.common.layout.LayoutConstants.ATTR_CONTENT_DESCRIPTION;
import static com.android.ide.common.layout.LayoutConstants.ATTR_INPUT_TYPE;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_WIDTH;
import static com.android.ide.common.layout.LayoutConstants.ATTR_ORIENTATION;
import static com.android.ide.common.layout.LayoutConstants.VALUE_FALSE;
import static com.android.ide.common.layout.LayoutConstants.VALUE_N_DP;
import static com.android.ide.common.layout.LayoutConstants.VALUE_VERTICAL;
import static com.android.ide.common.layout.LayoutConstants.VALUE_WRAP_CONTENT;
import static com.android.ide.common.layout.LayoutConstants.VALUE_ZERO_DP;
import static com.android.tools.lint.detector.api.LintConstants.HORIZONTAL_SCROLL_VIEW;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.AdtUtils;
import com.android.ide.eclipse.adt.internal.editors.AndroidXmlEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.DomUtilities;
import com.android.ide.eclipse.adt.internal.editors.layout.refactoring.UnwrapRefactoring;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiElementNode;
import com.android.ide.eclipse.adt.internal.refactorings.extractstring.ExtractStringRefactoring;
import com.android.ide.eclipse.adt.internal.refactorings.extractstring.ExtractStringWizard;
import com.android.tools.lint.checks.AccessibilityDetector;
import com.android.tools.lint.checks.DetectMissingPrefix;
import com.android.tools.lint.checks.ExportedServiceDetector;
import com.android.tools.lint.checks.HardcodedValuesDetector;
import com.android.tools.lint.checks.InefficientWeightDetector;
import com.android.tools.lint.checks.PxUsageDetector;
import com.android.tools.lint.checks.ScrollViewChildDetector;
import com.android.tools.lint.checks.TextFieldDetector;
import com.android.tools.lint.checks.UselessViewDetector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintConstants;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.window.Window;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.wst.sse.core.StructuredModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.sse.core.internal.provisional.IndexedRegion;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("restriction") // DOM model
abstract class LintFix implements ICompletionProposal {
    protected final IMarker mMarker;
    protected final String mId;

    protected LintFix(String id, IMarker marker) {
        mId = id;
        mMarker = marker;
    }

    /**
     * Returns true if this fix needs focus (which means that when the fix is
     * performed from a {@link LintListDialog}'s Fix button
     *
     * @return true if this fix needs focus after being applied
     */
    public boolean needsFocus() {
        return true;
    }

    /**
     * Returns true if this fix can be performed along side other fixes
     *
     * @return true if this fix can be performed in a bulk operation with other
     *         fixes
     */
    public boolean isBulkCapable() {
        return false;
    }

    /**
     * Returns true if this fix can be cancelled once it's invoked. This is the case
     * for fixes which shows a confirmation dialog (such as the Extract String etc).
     * This will be used to determine whether the marker can be deleted immediately
     * (for non-cancelable fixes) or if it should be left alone and detected fix
     * on the next save.
     *
     * @return true if the
     */
    public boolean isCancelable() {
        return true;
    }

    // ---- Implements ICompletionProposal ----

    public String getDisplayString() {
        return null;
    }

    public String getAdditionalProposalInfo() {
        Issue issue = EclipseLintClient.getRegistry().getIssue(mId);
        if (issue != null) {
            return issue.getExplanation().replace("\n", "<br>"); //$NON-NLS-1$ //$NON-NLS-2$
        }

        return null;
    }

    public void deleteMarker() {
        try {
            mMarker.delete();
        } catch (PartInitException e) {
            AdtPlugin.log(e, null);
        } catch (CoreException e) {
            AdtPlugin.log(e, null);
        }
    }

    public Point getSelection(IDocument document) {
        return null;
    }

    public Image getImage() {
        ISharedImages sharedImages = PlatformUI.getWorkbench().getSharedImages();
        return sharedImages.getImage(ISharedImages.IMG_OBJS_WARN_TSK);
    }

    public IContextInformation getContextInformation() {
        return null;
    }

    // --- Access to available fixes ---

    private static final Map<String, Class<? extends LintFix>> sFixes =
            new HashMap<String, Class<? extends LintFix>>();
    static {
        sFixes.put(InefficientWeightDetector.INEFFICIENT_WEIGHT.getId(),
                LinearLayoutWeightFix.class);
        sFixes.put(AccessibilityDetector.ISSUE.getId(), SetAttributeFix.class);
        sFixes.put(InefficientWeightDetector.BASELINE_WEIGHTS.getId(), SetAttributeFix.class);
        sFixes.put(HardcodedValuesDetector.ISSUE.getId(), ExtractStringFix.class);
        sFixes.put(UselessViewDetector.USELESS_LEAF.getId(), RemoveUselessViewFix.class);
        sFixes.put(UselessViewDetector.USELESS_PARENT.getId(), RemoveUselessViewFix.class);
        sFixes.put(PxUsageDetector.ISSUE.getId(), ConvertToDpFix.class);
        sFixes.put(TextFieldDetector.ISSUE.getId(), SetAttributeFix.class);
        sFixes.put(ExportedServiceDetector.ISSUE.getId(), SetAttributeFix.class);
        sFixes.put(DetectMissingPrefix.MISSING_NAMESPACE.getId(), AddPrefixFix.class);
        sFixes.put(ScrollViewChildDetector.ISSUE.getId(), SetScrollViewSizeFix.class);
    }

    public static boolean hasFix(String id) {
        return sFixes.containsKey(id);
    }

    /**
     * Returns a fix for the given issue, or null if no fix is available
     *
     * @param id the id o the issue to obtain a fix for (see {@link Issue#getId()})
     * @param marker the marker corresponding to the error
     * @return a fix, or null
     */
    public static LintFix getFix(String id, final IMarker marker) {
        Class<? extends LintFix> clazz = sFixes.get(id);
        if (clazz != null) {
            try {
                Constructor<? extends LintFix> constructor = clazz.getDeclaredConstructor(
                        String.class, IMarker.class);
                constructor.setAccessible(true);
                return constructor.newInstance(id, marker);
            } catch (Throwable t) {
                AdtPlugin.log(t, null);
            }
        }

        return null;
    }

    private abstract static class DocumentFix extends LintFix {

        protected DocumentFix(String id, IMarker marker) {
            super(id, marker);
        }

        protected abstract void apply(IDocument document, IStructuredModel model, Node node,
                int start, int end);

        public void apply(IDocument document) {
            int start = mMarker.getAttribute(IMarker.CHAR_START, -1);
            int end = mMarker.getAttribute(IMarker.CHAR_END, -1);
            if (start != -1 && end != -1) {
                Node node = DomUtilities.getNode(document, start);
                IModelManager manager = StructuredModelManager.getModelManager();
                IStructuredModel model = manager.getExistingModelForEdit(document);
                try {
                    apply(document, model, node, start, end);
                } finally {
                    model.releaseFromEdit();
                }

                if (!isCancelable()) {
                    deleteMarker();
                }
            }
        }
    }

    private abstract static class SetPropertyFix extends DocumentFix {
        private Region mSelect;

        private SetPropertyFix(String id, IMarker marker) {
            super(id, marker);
        }

        /** Attribute to be added */
        protected abstract String getAttribute();

        protected String getProposal() {
            return invokeCodeCompletion() ? "" : "TODO"; //$NON-NLS-1$
        }

        protected boolean invokeCodeCompletion() {
            return false;
        }

        @Override
        public boolean isCancelable() {
            return false;
        }

        @Override
        protected void apply(IDocument document, IStructuredModel model, Node node, int start,
                int end) {
            mSelect = null;

            if (node instanceof Element) {
                Element element = (Element) node;
                String proposal = getProposal();
                String localAttribute = getAttribute();
                String prefix = UiElementNode.lookupNamespacePrefix(node, ANDROID_URI);
                String attribute = prefix != null ? prefix + ':' + localAttribute : localAttribute;

                // This does not work even though it should: it does not include the prefix
                //element.setAttributeNS(ANDROID_URI, localAttribute, proposal);
                // So workaround instead:
                element.setAttribute(attribute, proposal);

                Attr attr = element.getAttributeNodeNS(ANDROID_URI, localAttribute);
                if (attr instanceof IndexedRegion) {
                    IndexedRegion region = (IndexedRegion) attr;
                    int offset = region.getStartOffset();
                    // We only want to select the value part inside the quotes,
                    // so skip the attribute and =" parts added by WST:
                    offset += attribute.length() + 2;
                    mSelect = new Region(offset, proposal.length());
                }
            }
        }

        @Override
        public void apply(IDocument document) {
            try {
                IFile file = (IFile) mMarker.getResource();
                super.apply(document);
                AdtPlugin.openFile(file, mSelect, true);
            } catch (PartInitException e) {
                AdtPlugin.log(e, null);
            }

            // Invoke code assist
            if (invokeCodeCompletion()) {
                IEditorPart editor = AdtUtils.getActiveEditor();
                if (editor instanceof AndroidXmlEditor) {
                    ((AndroidXmlEditor) editor).invokeContentAssist(-1);
                }
            }
        }

        @Override
        public boolean needsFocus() {
            // Because we need to show the editor with text selected
            return true;
        }

        @Override
        public Image getImage() {
            ISharedImages sharedImages = PlatformUI.getWorkbench().getSharedImages();
            return sharedImages.getImage(ISharedImages.IMG_OBJ_ADD);
        }
    }

    /** Shared fix class for various builtin attributes */
    private static final class SetAttributeFix extends SetPropertyFix {
        private SetAttributeFix(String id, IMarker marker) {
            super(id, marker);
        }

        @Override
        protected String getAttribute() {
            if (mId.equals(AccessibilityDetector.ISSUE.getId())) {
                return ATTR_CONTENT_DESCRIPTION;
            } else if (mId.equals(InefficientWeightDetector.BASELINE_WEIGHTS.getId())) {
                return LintConstants.ATTR_BASELINE_ALIGNED;
            } else if (mId.equals(ExportedServiceDetector.ISSUE.getId())) {
                return LintConstants.ATTR_PERMISSION;
            } else if (mId.equals(TextFieldDetector.ISSUE.getId())) {
                return ATTR_INPUT_TYPE;
            } else {
                assert false : mId;
                return "";
            }
        }

        @Override
        public String getDisplayString() {
            if (mId.equals(AccessibilityDetector.ISSUE.getId())) {
                return "Add content description attribute";
            } else if (mId.equals(InefficientWeightDetector.BASELINE_WEIGHTS.getId())) {
                return "Set baseline attribute";
            } else if (mId.equals(TextFieldDetector.ISSUE.getId())) {
                return "Set input type";
            } else if (mId.equals(ExportedServiceDetector.ISSUE.getId())) {
                return "Add permission attribute";
            } else {
                assert false : mId;
                return "";
            }
        }

        @Override
        protected boolean invokeCodeCompletion() {
            return mId.equals(ExportedServiceDetector.ISSUE.getId())
                    || mId.equals(TextFieldDetector.ISSUE.getId());
        }

        @Override
        protected String getProposal() {
            if (mId.equals(InefficientWeightDetector.BASELINE_WEIGHTS.getId())) {
                return VALUE_FALSE;
            }

            return super.getProposal();
        }

    }

    private static final class LinearLayoutWeightFix extends DocumentFix {
        private LinearLayoutWeightFix(String id, IMarker marker) {
            super(id, marker);
        }

        @Override
        public boolean needsFocus() {
            return false;
        }

        @Override
        public boolean isCancelable() {
            return false;
        }

        @Override
        protected void apply(IDocument document, IStructuredModel model, Node node, int start,
                int end) {
            if (node instanceof Element && node.getParentNode() instanceof Element) {
                Element element = (Element) node;
                Element parent = (Element) node.getParentNode();
                String dimension;
                if (VALUE_VERTICAL.equals(parent.getAttributeNS(ANDROID_URI,
                        ATTR_ORIENTATION))) {
                    dimension = ATTR_LAYOUT_HEIGHT;
                } else {
                    dimension = ATTR_LAYOUT_WIDTH;
                }
                element.setAttributeNS(ANDROID_URI, dimension, VALUE_ZERO_DP);
            }
        }

        @Override
        public String getDisplayString() {
            return "Replace size attribute with 0dp";
        }

        @Override
        public Image getImage() {
            ISharedImages sharedImages = PlatformUI.getWorkbench().getSharedImages();
            // TODO: Need a better icon here
            return sharedImages.getImage(ISharedImages.IMG_OBJ_ELEMENT);
        }
    }

    private static final class SetScrollViewSizeFix extends DocumentFix {
        private SetScrollViewSizeFix(String id, IMarker marker) {
            super(id, marker);
        }

        @Override
        public boolean needsFocus() {
            return false;
        }

        @Override
        public boolean isCancelable() {
            return false;
        }

        @Override
        protected void apply(IDocument document, IStructuredModel model, Node node, int start,
                int end) {
            if (node instanceof Element && node.getParentNode() instanceof Element) {
                Element element = (Element) node;
                Element parent = (Element) node.getParentNode();

                boolean isHorizontal = HORIZONTAL_SCROLL_VIEW.equals(parent.getTagName());
                String attributeName = isHorizontal ? ATTR_LAYOUT_WIDTH : ATTR_LAYOUT_HEIGHT;
                element.setAttributeNS(ANDROID_URI, attributeName, VALUE_WRAP_CONTENT);
            }
        }

        @Override
        public String getDisplayString() {
            return "Replace size attribute with wrap_content";
        }

        @Override
        public Image getImage() {
            ISharedImages sharedImages = PlatformUI.getWorkbench().getSharedImages();
            // TODO: Need a better icon here
            return sharedImages.getImage(ISharedImages.IMG_OBJ_ELEMENT);
        }
    }

    private static final class RemoveUselessViewFix extends DocumentFix {
        private RemoveUselessViewFix(String id, IMarker marker) {
            super(id, marker);
        }

        @Override
        public boolean needsFocus() {
            return isCancelable();
        }

        @Override
        public boolean isCancelable() {
            return mId.equals(mId.equals(UselessViewDetector.USELESS_PARENT.getId()));
        }

        @Override
        protected void apply(IDocument document, IStructuredModel model, Node node, int start,
                int end) {
            if (node instanceof Element && node.getParentNode() instanceof Element) {
                Element element = (Element) node;
                Element parent = (Element) node.getParentNode();

                if (mId.equals(UselessViewDetector.USELESS_LEAF.getId())) {
                    parent.removeChild(element);
                } else {
                    assert mId.equals(UselessViewDetector.USELESS_PARENT.getId());
                    // Invoke refactoring
                    IEditorPart editor = AdtUtils.getActiveEditor();
                    if (editor instanceof LayoutEditor) {
                        LayoutEditor layout = (LayoutEditor) editor;
                        IFile file = (IFile) mMarker.getResource();
                        ITextSelection textSelection = new TextSelection(start,
                                end - start);
                        UnwrapRefactoring refactoring =
                                new UnwrapRefactoring(file, layout, textSelection, null);
                        RefactoringWizard wizard = refactoring.createWizard();
                        RefactoringWizardOpenOperation op =
                                new RefactoringWizardOpenOperation(wizard);
                        try {
                            IWorkbenchWindow window = PlatformUI.getWorkbench().
                                    getActiveWorkbenchWindow();
                            op.run(window.getShell(), wizard.getDefaultPageTitle());
                        } catch (InterruptedException e) {
                        }
                    }
                }
            }
        }

        @Override
        public String getDisplayString() {
            return "Remove unnecessary view";
        }

        @Override
        public Image getImage() {
            ISharedImages sharedImages = PlatformUI.getWorkbench().getSharedImages();
            return sharedImages.getImage(ISharedImages.IMG_ETOOL_DELETE);
        }
    }

    /**
     * Fix for extracting strings.
     * <p>
     * TODO: Look for existing string values, and if it matches one of the
     * existing Strings offer to just replace it with the given string!
     */
    private static final class ExtractStringFix extends DocumentFix {
        private ExtractStringFix(String id, IMarker marker) {
            super(id, marker);
        }

        @Override
        public boolean needsFocus() {
            return true;
        }

        @Override
        public boolean isCancelable() {
            return true;
        }

        @Override
        protected void apply(IDocument document, IStructuredModel model, Node node, int start,
                int end) {
            // Invoke refactoring
            IEditorPart editor = AdtUtils.getActiveEditor();
            if (editor instanceof LayoutEditor) {
                IFile file = (IFile) mMarker.getResource();
                ITextSelection selection = new TextSelection(start,
                        end - start);

                ExtractStringRefactoring refactoring = new ExtractStringRefactoring(file,
                        editor,
                        selection);

                RefactoringWizard wizard = new ExtractStringWizard(refactoring,
                        file.getProject());
                RefactoringWizardOpenOperation op = new RefactoringWizardOpenOperation(wizard);
                try {
                    IWorkbenchWindow window = PlatformUI.getWorkbench().
                            getActiveWorkbenchWindow();
                    op.run(window.getShell(), wizard.getDefaultPageTitle());
                } catch (InterruptedException e) {
                }
            }
        }

        @Override
        public String getDisplayString() {
            return "Extract String";
        }

        @Override
        public Image getImage() {
            ISharedImages sharedImages = PlatformUI.getWorkbench().getSharedImages();
            return sharedImages.getImage(ISharedImages.IMG_OBJ_ADD);
        }
    }

    private static final class ConvertToDpFix extends DocumentFix implements IInputValidator {
        private ConvertToDpFix(String id, IMarker marker) {
            super(id, marker);
        }

        @Override
        public boolean needsFocus() {
            return false;
        }

        @Override
        public boolean isCancelable() {
            return true;
        }

        @Override
        protected void apply(IDocument document, IStructuredModel model, Node node, int start,
                int end) {
            Shell shell = AdtPlugin.getDisplay().getActiveShell();
            InputDensityDialog densityDialog = new InputDensityDialog(shell);
            if (densityDialog.open() == Window.OK) {
                int dpi = densityDialog.getDensity();
                Element element = (Element) node;
                Pattern pattern = Pattern.compile("(\\d+)px"); //$NON-NLS-1$
                NamedNodeMap attributes = element.getAttributes();
                for (int i = 0, n = attributes.getLength(); i < n; i++) {
                    Attr attribute = (Attr) attributes.item(i);
                    String value = attribute.getValue();
                    if (value.endsWith("px")) {
                        Matcher matcher = pattern.matcher(value);
                        if (matcher.matches()) {
                            String numberString = matcher.group(1);
                            try {
                                int px = Integer.parseInt(numberString);
                                int dp = px * 160 / dpi;
                                String newValue = String.format(VALUE_N_DP, dp);
                                attribute.setNodeValue(newValue);
                            } catch (NumberFormatException nufe) {
                                AdtPlugin.log(nufe, null);
                            }
                        }
                    }
                }
            }
        }

        @Override
        public String getDisplayString() {
            return "Convert to \"dp\"...";
        }

        @Override
        public Image getImage() {
            return AdtPlugin.getAndroidLogo();
        }

        // ---- Implements IInputValidator ----

        public String isValid(String input) {
            if (input == null || input.length() == 0)
                return " "; //$NON-NLS-1$

            try {
                int i = Integer.parseInt(input);
                if (i <= 0 || i > 1000) {
                    return "Invalid range";
                }
            } catch (NumberFormatException x) {
                return "Enter a valid number";
            }

            return null;
        }
    }

    private static final class AddPrefixFix extends DocumentFix {
        private AddPrefixFix(String id, IMarker marker) {
            super(id, marker);
        }

        @Override
        public boolean needsFocus() {
            return false;
        }

        @Override
        public boolean isCancelable() {
            return false;
        }

        @Override
        protected void apply(IDocument document, IStructuredModel model, Node node, int start,
                int end) {
            String prefix = UiElementNode.lookupNamespacePrefix(node, ANDROID_URI);
            try {
                document.replace(start, 0, prefix + ':');
            } catch (BadLocationException e) {
                AdtPlugin.log(e, null);
            }
        }

        @Override
        public String getDisplayString() {
            return "Add in an Android namespace prefix";
        }

        @Override
        public Image getImage() {
            ISharedImages sharedImages = PlatformUI.getWorkbench().getSharedImages();
            return sharedImages.getImage(ISharedImages.IMG_OBJ_ADD);
        }
    }
}
