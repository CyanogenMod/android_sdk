/*
 * Copyright (C) 2012 The Android Open Source Project
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

import static com.android.tools.lint.detector.api.LintConstants.ATTR_IGNORE;
import static com.android.tools.lint.detector.api.LintConstants.DOT_XML;
import static com.android.tools.lint.detector.api.LintConstants.TOOLS_PREFIX;
import static com.android.tools.lint.detector.api.LintConstants.TOOLS_URI;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.editors.AndroidXmlEditor;
import com.android.ide.eclipse.adt.internal.editors.IconFactory;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.DomUtilities;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiElementNode;
import com.android.tools.lint.checks.ArraySizeDetector;
import com.android.tools.lint.checks.DuplicateIdDetector;
import com.android.tools.lint.checks.MergeRootFrameLayoutDetector;
import com.android.tools.lint.checks.ObsoleteLayoutParamsDetector;
import com.android.tools.lint.checks.OverdrawDetector;
import com.android.tools.lint.checks.StringFormatDetector;
import com.android.tools.lint.checks.TranslationDetector;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Fix for adding {@code tools:ignore="id"} attributes in XML files.
 */
class AddSuppressAttribute implements ICompletionProposal {
    private final AndroidXmlEditor mEditor;
    private final String mId;
    private final IMarker mMarker;
    private final Element mElement;
    private final String mDescription;

    private AddSuppressAttribute(AndroidXmlEditor editor, String id, IMarker marker,
            Element element, String description) {
        mEditor = editor;
        mId = id;
        mMarker = marker;
        mElement = element;
        mDescription = description;
    }

    @Override
    public Point getSelection(IDocument document) {
        return null;
    }

    @Override
    public String getAdditionalProposalInfo() {
        return null;
    }

    @Override
    public String getDisplayString() {
        return mDescription;
    }

    @Override
    public IContextInformation getContextInformation() {
        return null;
    }

    @Override
    public Image getImage() {
        return IconFactory.getInstance().getIcon("newannotation"); //$NON-NLS-1$
    }

    @Override
    public void apply(IDocument document) {
        mEditor.wrapUndoEditXmlModel("Suppress Lint Warning", new Runnable() {
            @Override
            public void run() {
                String prefix = UiElementNode.lookupNamespacePrefix(mElement,
                        TOOLS_URI, null);
                if (prefix == null) {
                    // Add in new prefix...
                    prefix = UiElementNode.lookupNamespacePrefix(mElement,
                            TOOLS_URI, TOOLS_PREFIX);
                    // ...and ensure that the header is formatted such that
                    // the XML namespace declaration is placed in the right
                    // position and wrapping is applied etc.
                    mEditor.scheduleNodeReformat(mEditor.getUiRootNode(),
                            true /*attributesOnly*/);
                }

                String ignore = mElement.getAttributeNS(TOOLS_URI, ATTR_IGNORE);
                if (ignore.length() > 0) {
                    ignore = ignore + ',' + mId;
                } else {
                    ignore = mId;
                }

                // Use the non-namespace form of set attribute since we can't
                // reference the namespace until the model has been reloaded
                mElement.setAttribute(prefix + ':' + ATTR_IGNORE, mId);

                UiElementNode rootUiNode = mEditor.getUiRootNode();
                if (rootUiNode != null) {
                    UiElementNode uiNode = rootUiNode.findXmlNode(mElement);
                    if (uiNode != null) {
                        mEditor.scheduleNodeReformat(uiNode, true /*attributesOnly*/);
                    }
                }
            }
        });

        try {
            // Remove the marker now that the suppress attribute has been added
            // (so the user doesn't have to re-run lint just to see it disappear)
            mMarker.delete();
        } catch (CoreException e) {
            AdtPlugin.log(e, "Could not add suppress annotation");
        }
    }

    /**
     * Adds any applicable suppress lint fix resolutions into the given list
     *
     * @param editor the associated editor containing the marker
     * @param marker the marker to create fixes for
     * @param id the issue id
     * @return a fix for this marker, or null if unable
     */
    @Nullable
    public static AddSuppressAttribute createFix(
            @NonNull AndroidXmlEditor editor,
            @NonNull IMarker marker,
            @NonNull String id) {
        // This only applies to XML files:
        String fileName = marker.getResource().getName();
        if (!fileName.endsWith(DOT_XML)) {
            return null;
        }

        int offset = marker.getAttribute(IMarker.CHAR_START, -1);
        Node node = DomUtilities.getNode(editor.getStructuredDocument(), offset);
        if (node == null) {
            return null;
        }
        Document document = node.getOwnerDocument();
        while (node != null && node.getNodeType() != Node.ELEMENT_NODE) {
            node = node.getParentNode();
        }

        // Some issues cannot find a specific node scope associated with the error
        // (for example because it involves cross-file analysis and at the end of
        // the project scan when the warnings are computed the DOM model is no longer
        // available). Until that's resolved, we need to filter these out such that
        // we don't add misleading annotations on individual elements; the fallback
        // path is the DOM document itself instead.
        if (id.equals(ArraySizeDetector.INCONSISTENT.getId())
                || id.equals(DuplicateIdDetector.CROSS_LAYOUT.getId())
                || id.equals(MergeRootFrameLayoutDetector.ISSUE.getId())
                || id.equals(ObsoleteLayoutParamsDetector.ISSUE.getId())
                || id.equals(OverdrawDetector.ISSUE.getId())
                || id.equals(StringFormatDetector.ARG_TYPES.getId())
                || id.equals(StringFormatDetector.ARG_COUNT.getId())
                || id.equals(TranslationDetector.MISSING.getId())
                || id.equals(TranslationDetector.EXTRA.getId())) {
            node = document.getDocumentElement();
        }

        if (node == null) {
            node = document.getDocumentElement();
            if (node == null) {
                return null;
            }
        }

        String desc = String.format("Add ignore '%1$s\' to element", id);
        Element element = (Element) node;
        return new AddSuppressAttribute(editor, id, marker, element, desc);
    }
}
