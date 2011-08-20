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

package com.android.ide.eclipse.adt.internal.editors.layout.refactoring;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.editors.AndroidXmlEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutEditor;
import com.android.ide.eclipse.adt.internal.refactorings.extractstring.ExtractStringRefactoring;
import com.android.ide.eclipse.adt.internal.refactorings.extractstring.ExtractStringWizard;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.quickassist.IQuickAssistInvocationContext;
import org.eclipse.jface.text.quickassist.IQuickAssistProcessor;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocument;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocumentRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.ITextRegion;
import org.eclipse.wst.sse.ui.StructuredTextEditor;
import org.eclipse.wst.xml.core.internal.regions.DOMRegionContext;

/**
 * QuickAssistProcessor which helps invoke refactoring operations on text elements.
 */
@SuppressWarnings("restriction") // XML model
public class RefactoringAssistant implements IQuickAssistProcessor {

    public RefactoringAssistant() {
    }

    public boolean canAssist(IQuickAssistInvocationContext invocationContext) {
        return true;
    }

    public boolean canFix(Annotation annotation) {
        return true;
    }

    public ICompletionProposal[] computeQuickAssistProposals(
            IQuickAssistInvocationContext invocationContext) {

        ISourceViewer sourceViewer = invocationContext.getSourceViewer();
        AndroidXmlEditor xmlEditor = AndroidXmlEditor.getAndroidXmlEditor(sourceViewer);
        if (xmlEditor == null) {
            return null;
        }

        IFile file = xmlEditor.getInputFile();
        int offset = invocationContext.getOffset();

        // Ensure that we are over a tag name (for element-based refactoring
        // operations) or a value (for the extract include refactoring)

        boolean isValue = false;
        boolean isTagName = false;
        boolean isAttributeName = false;
        IStructuredModel model = null;
        try {
            model = xmlEditor.getModelForRead();
            IStructuredDocument doc = model.getStructuredDocument();
            IStructuredDocumentRegion region = doc.getRegionAtCharacterOffset(offset);
            ITextRegion subRegion = region.getRegionAtCharacterOffset(offset);
            if (subRegion != null) {
                String type = subRegion.getType();
                if (type.equals(DOMRegionContext.XML_TAG_ATTRIBUTE_VALUE)) {
                    String value = region.getText(subRegion);
                    // Only extract values that aren't already resources
                    // (and value includes leading ' or ")
                    if (!value.startsWith("'@") && !value.startsWith("\"@")) { //$NON-NLS-1$ //$NON-NLS-2$
                        isValue = true;
                    }
                } else if (type.equals(DOMRegionContext.XML_TAG_NAME)
                        || type.equals(DOMRegionContext.XML_TAG_OPEN)
                        || type.equals(DOMRegionContext.XML_TAG_CLOSE)) {
                    isTagName = true;
                } else if (type.equals(DOMRegionContext.XML_TAG_ATTRIBUTE_NAME)
                        || type.equals(DOMRegionContext.XML_TAG_ATTRIBUTE_EQUALS)) {
                    isAttributeName = true;
                }
            }
        } finally {
            if (model != null) {
                model.releaseFromRead();
            }
        }

        if (isValue || isTagName || isAttributeName) {
            StructuredTextEditor structuredEditor = xmlEditor.getStructuredTextEditor();
            ISelectionProvider provider = structuredEditor.getSelectionProvider();
            ISelection selection = provider.getSelection();
            if (selection instanceof ITextSelection) {
                ITextSelection textSelection = (ITextSelection) selection;

                // These operations currently do not work on ranges
                if (textSelection.getLength() > 0) {
                    // ...except for Extract Style where the actual attributes overlapping
                    // the selection is going to be the set of eligible attributes
                    if (isAttributeName && xmlEditor instanceof LayoutEditor) {
                        LayoutEditor editor = (LayoutEditor) xmlEditor;
                        return new ICompletionProposal[] {
                                new RefactoringProposal(editor,
                                    new ExtractStyleRefactoring(file, editor, textSelection, null))
                        };
                    }
                    return null;
                }

                if (isAttributeName && xmlEditor instanceof LayoutEditor) {
                    LayoutEditor editor = (LayoutEditor) xmlEditor;
                    return new ICompletionProposal[] {
                            new RefactoringProposal(editor,
                                new ExtractStyleRefactoring(file, editor, textSelection, null)),
                    };
                } else if (isValue) {
                    if (xmlEditor instanceof LayoutEditor) {
                        LayoutEditor editor = (LayoutEditor) xmlEditor;
                        return new ICompletionProposal[] {
                                new RefactoringProposal(xmlEditor,
                                        new ExtractStringRefactoring(file, xmlEditor,
                                                textSelection)),
                                new RefactoringProposal(editor,
                                        new ExtractStyleRefactoring(file, editor,
                                                textSelection, null)),
                        };
                    } else {
                        return new ICompletionProposal[] {
                            new RefactoringProposal(xmlEditor,
                                    new ExtractStringRefactoring(file, xmlEditor, textSelection))
                        };
                    }
                } else if (xmlEditor instanceof LayoutEditor) {
                    LayoutEditor editor = (LayoutEditor) xmlEditor;
                    return new ICompletionProposal[] {
                        new RefactoringProposal(editor,
                            new WrapInRefactoring(file, editor, textSelection, null)),
                        new RefactoringProposal(editor,
                                new UnwrapRefactoring(file, editor, textSelection, null)),
                        new RefactoringProposal(editor,
                            new ChangeViewRefactoring(file, editor, textSelection, null)),
                        new RefactoringProposal(editor,
                            new ChangeLayoutRefactoring(file, editor, textSelection, null)),
                        new RefactoringProposal(editor,
                            new ExtractStyleRefactoring(file, editor, textSelection, null)),
                        new RefactoringProposal(editor,
                            new ExtractIncludeRefactoring(file, editor, textSelection, null)),
                    };
                }
            }
        }
        return null;
    }

    public String getErrorMessage() {
        return null;
    }

    private static class RefactoringProposal
            implements ICompletionProposal {
        private final AndroidXmlEditor mEditor;
        private final Refactoring mRefactoring;

        RefactoringProposal(AndroidXmlEditor editor, Refactoring refactoring) {
            super();
            mEditor = editor;
            mRefactoring = refactoring;
        }

        public void apply(IDocument document) {
            RefactoringWizard wizard = null;
            if (mRefactoring instanceof VisualRefactoring) {
                wizard = ((VisualRefactoring) mRefactoring).createWizard();
            } else if (mRefactoring instanceof ExtractStringRefactoring) {
                wizard = new ExtractStringWizard((ExtractStringRefactoring) mRefactoring,
                        mEditor.getProject());
            } else {
                throw new IllegalArgumentException();
            }

            RefactoringWizardOpenOperation op = new RefactoringWizardOpenOperation(wizard);
            try {
                IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
                op.run(window.getShell(), wizard.getDefaultPageTitle());
            } catch (InterruptedException e) {
            }
        }

        public String getAdditionalProposalInfo() {
            return "Initiates the given refactoring operation";
        }

        public IContextInformation getContextInformation() {
            return null;
        }

        public String getDisplayString() {
            return mRefactoring.getName();
        }

        public Image getImage() {
            return AdtPlugin.getAndroidLogo();
        }

        public Point getSelection(IDocument document) {
            return null;
        }
    }
}
