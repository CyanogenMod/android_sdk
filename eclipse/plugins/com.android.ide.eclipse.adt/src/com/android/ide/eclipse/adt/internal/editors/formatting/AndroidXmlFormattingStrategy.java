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
package com.android.ide.eclipse.adt.internal.editors.formatting;

import static org.eclipse.jface.text.formatter.FormattingContextProperties.CONTEXT_MEDIUM;
import static org.eclipse.jface.text.formatter.FormattingContextProperties.CONTEXT_PARTITION;
import static org.eclipse.jface.text.formatter.FormattingContextProperties.CONTEXT_REGION;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.DomUtilities;
import com.android.ide.eclipse.adt.internal.editors.resources.descriptors.ResourcesDescriptors;
import com.android.ide.eclipse.adt.internal.preferences.AdtPrefs;
import com.android.sdklib.SdkConstants;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.TypedPosition;
import org.eclipse.jface.text.formatter.ContextBasedFormattingStrategy;
import org.eclipse.jface.text.formatter.IFormattingContext;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.wst.sse.core.StructuredModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.sse.core.internal.provisional.IndexedRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocument;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocumentRegion;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMModel;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMNode;
import org.eclipse.wst.xml.ui.internal.XMLFormattingStrategy;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Formatter which formats XML content according to the established Android coding
 * conventions. It performs the format by computing the smallest set of DOM nodes
 * overlapping the formatted region, then it pretty-prints that XML region
 * using the {@link XmlPrettyPrinter}, and then it replaces the affected region
 * by the pretty-printed region.
 * <p>
 * This strategy is also used for delegation. If the user has chosen to use the
 * standard Eclipse XML formatter, this strategy simply delegates to the
 * default XML formatting strategy in WTP.
 */
@SuppressWarnings("restriction")
public class AndroidXmlFormattingStrategy extends ContextBasedFormattingStrategy {
    private IRegion mRegion;
    private final Queue<IDocument> mDocuments = new LinkedList<IDocument>();
    private final LinkedList<TypedPosition> mPartitions = new LinkedList<TypedPosition>();
    private ContextBasedFormattingStrategy mDelegate = null;

    public AndroidXmlFormattingStrategy() {
    }

    private ContextBasedFormattingStrategy getDelegate() {
        if (!AdtPrefs.getPrefs().getUseCustomXmlFormatter()) {
            if (mDelegate == null) {
                mDelegate = new XMLFormattingStrategy();
            }

            return mDelegate;
        }

        return null;
    }

    @Override
    public void format() {
        // Use Eclipse XML formatter instead?
        ContextBasedFormattingStrategy delegate = getDelegate();
        if (delegate != null) {
            delegate.format();
            return;
        }

        super.format();

        IDocument document = mDocuments.poll();
        TypedPosition partition = mPartitions.poll();

        if (document != null && partition != null && mRegion != null) {
            try {
                if (document instanceof IStructuredDocument) {
                    IStructuredDocument structuredDocument = (IStructuredDocument) document;
                    IModelManager modelManager = StructuredModelManager.getModelManager();
                    IStructuredModel model = modelManager.getModelForEdit(structuredDocument);
                    if (model != null) {
                        try {
                            TextEdit edit = format(model, mRegion.getOffset(),
                                    mRegion.getLength());
                            if (edit != null) {
                                try {
                                    model.aboutToChangeModel();
                                    edit.apply(document);
                                }
                                finally {
                                    model.changedModel();
                                }
                            }
                        }
                        finally {
                            model.releaseFromEdit();
                        }
                    }
                }
            }
            catch (BadLocationException e) {
                AdtPlugin.log(e, "Formatting error");
            }
        }
    }

    private TextEdit format(IStructuredModel model, int start, int length) {
        TextEdit edit = new MultiTextEdit();
        IStructuredDocument document = model.getStructuredDocument();

        Node startNode = null;
        Node endNode = null;
        Document domDocument = null;

        if (model instanceof IDOMModel) {
            IDOMModel domModel = (IDOMModel) model;
            domDocument = domModel.getDocument();
        } else {
            // This should not happen
            return edit;
        }

        IStructuredDocumentRegion currentRegion = document.getRegionAtCharacterOffset(start);
        if (currentRegion != null) {
            int startOffset = currentRegion.getStartOffset();
            IndexedRegion currentIndexedRegion = model.getIndexedRegion(startOffset);
            if (currentIndexedRegion instanceof IDOMNode) {
                IDOMNode currentDOMNode = (IDOMNode) currentIndexedRegion;
                startNode = currentDOMNode;
            }
        }

        currentRegion = document.getRegionAtCharacterOffset(start + length);
        if (currentRegion != null) {
            int endOffset = Math.max(currentRegion.getStartOffset(),
                    currentRegion.getEndOffset() - 1);
            IndexedRegion currentIndexedRegion = model.getIndexedRegion(endOffset);
            if (currentIndexedRegion instanceof IDOMNode) {
                IDOMNode currentDOMNode = (IDOMNode) currentIndexedRegion;
                endNode = currentDOMNode;
            }
        }

        Node root = null;
        int initialDepth = 0;
        int replaceStart;
        int replaceEnd;
        if (startNode == null || endNode == null) {
            // Process the entire document
            root = domDocument;
            // both document and documentElement should be <= 0
            initialDepth = -1;
            startNode = root;
            endNode = root;
            replaceStart = 0;
            replaceEnd = document.getLength();
        } else {
            root = DomUtilities.getCommonAncestor(startNode, endNode);
            initialDepth = DomUtilities.getDepth(root) - 1;
            IndexedRegion rootRegion = (IndexedRegion) root;
            replaceStart = rootRegion.getStartOffset();
            replaceEnd = rootRegion.getEndOffset();
        }


        XmlFormatStyle style = guessStyle(model, domDocument);
        XmlFormatPreferences prefs = XmlFormatPreferences.create();
        XmlPrettyPrinter printer = new XmlPrettyPrinter(prefs, style);

        StringBuilder sb = new StringBuilder(length);
        printer.prettyPrint(initialDepth, root, startNode, endNode, sb);

        String formatted = sb.toString();
        ReplaceEdit replaceEdit = createReplaceEdit(document, replaceStart, replaceEnd, formatted);
        if (replaceEdit != null) {
            edit.addChild(replaceEdit);
        }

        return edit;
    }

    /**
     * Create a {@link ReplaceEdit} which replaces the text in the given document with the
     * given new formatted content. The replaceStart and replaceEnd parameters point to
     * the equivalent unformatted text in the document, but the actual edit range may be
     * adjusted (for example to make the edit smaller if the beginning and/or end is
     * identical, and so on)
     */
    private ReplaceEdit createReplaceEdit(IStructuredDocument document, int replaceStart,
            int replaceEnd, String formatted) {
        // If replacing a node somewhere in the middle, start the replacement at the
        // beginning of the current line
        int index = replaceStart;
        try {
            while (index > 0) {
                char c = document.getChar(index - 1);
                if (c == '\n') {
                    replaceStart = index + 1;
                    break;
                } else if (!Character.isWhitespace(c)) {
                    // The replaced node does not start on its own line; in that case,
                    // remove the initial indentation in the reformatted element
                    for (int i = 0; i < formatted.length(); i++) {
                        if (!Character.isWhitespace(formatted.charAt(i))) {
                            formatted = formatted.substring(i);
                            break;
                        }
                    }
                    break;
                }
                index--;
            }
        } catch (BadLocationException e) {
            AdtPlugin.log(e, null);
        }

        // Figure out how much of the before and after strings are identical and narrow
        // the replacement scope
        boolean foundDifference = false;
        int firstDifference = 0;
        int lastDifference = formatted.length();
        try {
            for (int i = 0, j = replaceStart; i < formatted.length() && j < replaceEnd; i++, j++) {
                if (formatted.charAt(i) != document.getChar(j)) {
                    firstDifference = i;
                    foundDifference = true;
                    break;
                }
            }

            if (!foundDifference) {
                // No differences - the document is already formatted, nothing to do
                return null;
            }

            lastDifference = firstDifference + 1;
            for (int i = formatted.length() - 1, j = replaceEnd - 1;
                    i > firstDifference && j > replaceStart;
                    i--, j--) {
                if (formatted.charAt(i) != document.getChar(j)) {
                    lastDifference = i + 1;
                    break;
                }
            }
        } catch (BadLocationException e) {
            AdtPlugin.log(e, null);
        }

        replaceStart += firstDifference;
        replaceEnd -= (formatted.length() - lastDifference);
        replaceEnd = Math.max(replaceStart, replaceEnd);
        formatted = formatted.substring(firstDifference, lastDifference);

        ReplaceEdit replaceEdit = new ReplaceEdit(replaceStart, replaceEnd - replaceStart,
                formatted);
        return replaceEdit;
    }

    /**
     * Guess what style to use to edit the given document - layout, resource, manifest, ... ? */
    private XmlFormatStyle guessStyle(IStructuredModel model, Document domDocument) {
        XmlFormatStyle style = XmlFormatStyle.LAYOUT;
        if (domDocument.getDocumentElement() != null
                && ResourcesDescriptors.ROOT_ELEMENT.equals(domDocument.getDocumentElement()
                        .getTagName())) {
            style = XmlFormatStyle.RESOURCE;
        }

        String baseLocation = model.getBaseLocation();
        if (baseLocation != null) {
            if (baseLocation.endsWith(SdkConstants.FN_ANDROID_MANIFEST_XML)) {
                style = XmlFormatStyle.MANIFEST;
            } else {
                int lastSlash = baseLocation.lastIndexOf('/');
                if (lastSlash != -1) {
                    lastSlash = baseLocation.lastIndexOf('/', lastSlash - 1);
                    if (lastSlash != -1 && baseLocation.startsWith("/values", lastSlash)) { //$NON-NLS-1$
                        style = XmlFormatStyle.RESOURCE;
                    }
                }
            }
        }
        return style;
    }

    @Override
    public void formatterStarts(final IFormattingContext context) {
        // Use Eclipse XML formatter instead?
        ContextBasedFormattingStrategy delegate = getDelegate();
        if (delegate != null) {
            delegate.formatterStarts(context);

            // We also need the super implementation because it stores items into the
            // map, and we can't override the getPreferences method, so we need for
            // this delegating strategy to supply the correct values when it is called
            // instead of the delegate
            super.formatterStarts(context);

            return;
        }

        super.formatterStarts(context);
        mRegion = (IRegion) context.getProperty(CONTEXT_REGION);
        TypedPosition partition = (TypedPosition) context.getProperty(CONTEXT_PARTITION);
        IDocument document = (IDocument) context.getProperty(CONTEXT_MEDIUM);
        mPartitions.offer(partition);
        mDocuments.offer(document);
    }

    @Override
    public void formatterStops() {
        // Use Eclipse XML formatter instead?
        ContextBasedFormattingStrategy delegate = getDelegate();
        if (delegate != null) {
            delegate.formatterStops();
            // See formatterStarts for an explanation
            super.formatterStops();

            return;
        }

        super.formatterStops();
        mRegion = null;
        mDocuments.clear();
        mPartitions.clear();
    }
}