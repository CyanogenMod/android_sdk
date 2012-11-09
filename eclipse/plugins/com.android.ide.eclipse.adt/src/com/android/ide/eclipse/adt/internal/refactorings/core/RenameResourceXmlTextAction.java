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

package com.android.ide.eclipse.adt.internal.refactorings.core;

import static com.android.SdkConstants.ANDROID_PREFIX;
import static com.android.SdkConstants.ANDROID_THEME_PREFIX;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_TYPE;
import static com.android.SdkConstants.TAG_ITEM;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.editors.Hyperlinks;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.DomUtilities;
import com.android.resources.ResourceType;
import com.android.utils.Pair;

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.ITextEditorExtension;
import org.eclipse.ui.texteditor.ITextEditorExtension2;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Text action for XML files to invoke resource renaming
 * <p>
 * TODO: Handle other types of renaming: invoking class renaming when editing
 * class names in layout files and manifest files, renaming attribute names when
 * editing a styleable attribute, etc.
 */
public final class RenameResourceXmlTextAction extends Action {
    private final ITextEditor mEditor;

    /**
     * Creates a new {@linkplain RenameResourceXmlTextAction}
     *
     * @param editor the associated editor
     */
    public RenameResourceXmlTextAction(@NonNull ITextEditor editor) {
        super("Rename");
        mEditor = editor;
    }

    @Override
    public void run() {
        if (!validateEditorInputState()) {
            return;
        }
        IDocument document = getDocument();
        if (document == null) {
            return;
        }
        ITextSelection selection = getSelection();
        if (selection == null) {
            return;
        }

        Pair<ResourceType, String> resource = findResource(document, selection.getOffset());

        if (resource == null) {
            resource = findItemDefinition(document, selection.getOffset());
        }

        if (resource != null) {
            ResourceType type = resource.getFirst();
            String name = resource.getSecond();
            Shell shell = mEditor.getSite().getShell();
            boolean canClear = false;

            IEditorInput input = mEditor.getEditorInput();
            if (input instanceof IFileEditorInput) {
                IFileEditorInput fileInput = (IFileEditorInput) input;
                IProject project = fileInput.getFile().getProject();
                RenameResourceWizard.renameResource(shell, project, type, name, null, canClear);
                return;
            }
        }

        // Fallback: tell user the cursor isn't in the right place
        MessageDialog.openInformation(mEditor.getSite().getShell(),
                "Rename",
                "Operation unavailable on the current selection.\n"
                        + "Select an Android resource name.");
    }

    private boolean validateEditorInputState() {
        if (mEditor instanceof ITextEditorExtension2)
            return ((ITextEditorExtension2) mEditor).validateEditorInputState();
        else if (mEditor instanceof ITextEditorExtension)
            return !((ITextEditorExtension) mEditor).isEditorInputReadOnly();
        else if (mEditor != null)
            return mEditor.isEditable();
        else
            return false;
    }

    /**
     * Searches for a resource URL around the caret, such as {@code @string/foo}
     *
     * @param document the document to search in
     * @param offset the offset to search at
     * @return a resource pair, or null if not found
     */
    @Nullable
    public static Pair<ResourceType,String> findResource(@NonNull IDocument document, int offset) {
        try {
            int max = document.getLength();
            if (offset >= max) {
                offset = max - 1;
            } else if (offset < 0) {
                offset = 0;
            } else if (offset > 0) {
                // If the caret is right after a resource name (meaning getChar(offset) points
                // to the following character), back up
                char c = document.getChar(offset);
                if (!isValidResourceNameChar(c)) {
                    offset--;
                }
            }

            int start = offset;
            boolean valid = true;
            for (; start >= 0; start--) {
                char c = document.getChar(start);
                if (c == '@' || c == '?') {
                    break;
                } else if (!isValidResourceNameChar(c)) {
                    valid = false;
                    break;
                }
            }
            if (valid) {
                // Search forwards for the end
                int end = start + 1;
                for (; end < max; end++) {
                    char c = document.getChar(end);
                    if (!isValidResourceNameChar(c)) {
                        break;
                    }
                }
                if (end > start + 1) {
                    String url = document.get(start, end - start);

                    // Don't allow renaming framework resources -- @android:string/ok etc
                    if (url.startsWith(ANDROID_PREFIX) || url.startsWith(ANDROID_THEME_PREFIX)) {
                        return null;
                    }

                    return Hyperlinks.parseResource(url);
                }
            }
        } catch (BadLocationException e) {
            AdtPlugin.log(e, null);
        }

        return null;
    }

    private static boolean isValidResourceNameChar(char c) {
        return c == '@' || c == '?' || c == '/' || c == '+' || Character.isJavaIdentifierPart(c);
    }

    /**
     * Searches for an item definition around the caret, such as
     * {@code   <string name="foo">My String</string>}
     */
    private Pair<ResourceType, String> findItemDefinition(IDocument document, int offset) {
        Node node = DomUtilities.getNode(document, offset);
        if (node == null) {
            return null;
        }
        if (node.getNodeType() == Node.TEXT_NODE) {
            node = node.getParentNode();
        }
        if (node == null || node.getNodeType() != Node.ELEMENT_NODE) {
            return null;
        }

        Element element = (Element) node;
        String name = element.getAttribute(ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return null;
        }
        String typeString = element.getTagName();
        if (TAG_ITEM.equals(typeString)) {
            typeString = element.getAttribute(ATTR_TYPE);
            if (typeString == null || typeString.isEmpty()) {
                return null;
            }
        }
        ResourceType type = ResourceType.getEnum(typeString);
        if (type != null) {
            return Pair.of(type, name);
        }

        return null;
    }

    private ITextSelection getSelection() {
        ISelectionProvider selectionProvider = mEditor.getSelectionProvider();
        if (selectionProvider == null) {
            return null;
        }
        ISelection selection = selectionProvider.getSelection();
        if (!(selection instanceof ITextSelection)) {
            return null;
        }
        return (ITextSelection) selection;
    }

    private IDocument getDocument() {
        IDocumentProvider documentProvider = mEditor.getDocumentProvider();
        if (documentProvider == null) {
            return null;
        }
        IDocument document = documentProvider.getDocument(mEditor.getEditorInput());
        if (document == null) {
            return null;
        }
        return document;
    }
}
