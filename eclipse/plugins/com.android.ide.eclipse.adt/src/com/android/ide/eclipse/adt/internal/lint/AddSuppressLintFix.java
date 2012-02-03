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

import static com.android.tools.lint.detector.api.LintConstants.FQCN_SUPPRESS_LINT;
import static com.android.tools.lint.detector.api.LintConstants.SUPPRESS_LINT;
import static org.eclipse.jdt.core.dom.ArrayInitializer.EXPRESSIONS_PROPERTY;
import static org.eclipse.jdt.core.dom.SingleMemberAnnotation.VALUE_PROPERTY;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.AdtUtils;
import com.android.ide.eclipse.adt.internal.editors.IconFactory;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.ui.IWorkingCopyManager;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.SharedASTProvider;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.graphics.Image;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IMarkerResolution;
import org.eclipse.ui.IMarkerResolution2;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;

import java.util.List;

/**
 * Marker resolution for adding {@code @SuppressLint} annotations in Java files
 */
class AddSuppressLintFix implements IMarkerResolution2 {
    private final IMarker mMarker;
    private final String mId;
    private final BodyDeclaration mNode;
    private final String mDescription;

    private AddSuppressLintFix(String id, IMarker marker, BodyDeclaration node,
            String description) {
        mId = id;
        mMarker = marker;
        mNode = node;
        mDescription = description;
    }

    @Override
    public String getLabel() {
        return mDescription;
    }

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public Image getImage() {
        return IconFactory.getInstance().getIcon("newannotation"); //$NON-NLS-1$
    }

    @Override
    public void run(IMarker marker) {
        ITextEditor textEditor = AdtUtils.getActiveTextEditor();
        IDocumentProvider provider = textEditor.getDocumentProvider();
        IEditorInput editorInput = textEditor.getEditorInput();
        IDocument document = provider.getDocument(editorInput);
        if (document == null) {
            return;
        }
        IWorkingCopyManager manager = JavaUI.getWorkingCopyManager();
        ICompilationUnit compilationUnit = manager.getWorkingCopy(editorInput);
        addAnnotation(document, compilationUnit, mNode);
    }

    @SuppressWarnings({"rawtypes"}) // Java AST API has raw types
    private void addAnnotation(IDocument document, ICompilationUnit compilationUnit,
            BodyDeclaration declaration) {
        List modifiers = declaration.modifiers();
        SingleMemberAnnotation existing = null;
        for (Object o : modifiers) {
            if (o instanceof SingleMemberAnnotation) {
                SingleMemberAnnotation annotation = (SingleMemberAnnotation) o;
                String type = annotation.getTypeName().getFullyQualifiedName();
                if (type.equals(FQCN_SUPPRESS_LINT) || type.endsWith(SUPPRESS_LINT)) {
                    existing = annotation;
                    break;
                }
            }
        }

        try {
            ImportRewrite importRewrite = ImportRewrite.create(compilationUnit, true);
            String local = importRewrite.addImport(FQCN_SUPPRESS_LINT);
            AST ast = declaration.getAST();
            ASTRewrite rewriter = ASTRewrite.create(ast);
            if (existing == null) {
                SingleMemberAnnotation newAnnotation = ast.newSingleMemberAnnotation();
                newAnnotation.setTypeName(ast.newSimpleName(local));
                StringLiteral value = ast.newStringLiteral();
                value.setLiteralValue(mId);
                newAnnotation.setValue(value);
                ListRewrite listRewrite = rewriter.getListRewrite(declaration,
                        declaration.getModifiersProperty());
                listRewrite.insertFirst(newAnnotation, null);
            } else {
                Expression existingValue = existing.getValue();
                if (existingValue instanceof StringLiteral) {
                    // Create a new array initializer holding the old string plus the new id
                    ArrayInitializer array = ast.newArrayInitializer();
                    StringLiteral old = ast.newStringLiteral();
                    StringLiteral stringLiteral = (StringLiteral) existingValue;
                    old.setLiteralValue(stringLiteral.getLiteralValue());
                    array.expressions().add(old);
                    StringLiteral value = ast.newStringLiteral();
                    value.setLiteralValue(mId);
                    array.expressions().add(value);
                    rewriter.set(existing, VALUE_PROPERTY, array, null);
                } else if (existingValue instanceof ArrayInitializer) {
                    // Existing array: just append the new string
                    ArrayInitializer array = (ArrayInitializer) existingValue;
                    StringLiteral value = ast.newStringLiteral();
                    value.setLiteralValue(mId);
                    ListRewrite listRewrite = rewriter.getListRewrite(array, EXPRESSIONS_PROPERTY);
                    listRewrite.insertLast(value, null);
                } else {
                    assert false : existingValue;
                    return;
                }
            }

            TextEdit importEdits = importRewrite.rewriteImports(new NullProgressMonitor());
            TextEdit annotationEdits = rewriter.rewriteAST(document, null);

            // Apply to the document
            MultiTextEdit edit = new MultiTextEdit();
            // Create the edit to change the imports, only if
            // anything changed
            if (importEdits.hasChildren()) {
                edit.addChild(importEdits);
            }
            edit.addChild(annotationEdits);
            edit.apply(document);

            // Remove the marker now that the suppress annotation has been added
            // (so the user doesn't have to re-run lint just to see it disappear,
            // and besides we don't want to keep offering marker resolutions on this
            // marker which could lead to duplicate annotations since the above code
            // assumes that the current id isn't in the list of values, since otherwise
            // lint shouldn't have complained here.
            mMarker.delete();
        } catch (Exception ex) {
            AdtPlugin.log(ex, "Could not add suppress annotation");
        }
    }

    /**
     * Adds any applicable suppress lint fix resolutions into the given list
     *
     * @param marker the marker to create fixes for
     * @param id the issue id
     * @param resolutions a list to add the created resolutions into, if any
     */
    public static void createFixes(IMarker marker, String id,
            List<IMarkerResolution> resolutions) {
        ITextEditor textEditor = AdtUtils.getActiveTextEditor();
        IDocumentProvider provider = textEditor.getDocumentProvider();
        IEditorInput editorInput = textEditor.getEditorInput();
        IDocument document = provider.getDocument(editorInput);
        if (document == null) {
            return;
        }
        IWorkingCopyManager manager = JavaUI.getWorkingCopyManager();
        ICompilationUnit compilationUnit = manager.getWorkingCopy(editorInput);
        int offset = 0;
        int length = 0;
        int start = marker.getAttribute(IMarker.CHAR_START, -1);
        int end = marker.getAttribute(IMarker.CHAR_END, -1);
        offset = start;
        length = end - start;
        CompilationUnit root = SharedASTProvider.getAST(compilationUnit,
                SharedASTProvider.WAIT_YES, null);
        if (root == null) {
            return;
        }
        NodeFinder nodeFinder = new NodeFinder(root, offset, length);
        ASTNode coveringNode = nodeFinder.getCoveringNode();
        ASTNode body = coveringNode;
        while (body != null) {
            if (body instanceof BodyDeclaration) {
                BodyDeclaration declaration = (BodyDeclaration) body;

                //String name = declaration.
                String target = null;
                String name = null;
                if (body instanceof MethodDeclaration) {
                    name = ((MethodDeclaration) body).getName().toString();
                    target = String.format("method %1$s", name);
                } else if (body instanceof FieldDeclaration) {
                    //name = ((FieldDeclaration) body).getName().toString();
                    target = "field";
                } else if (body instanceof AnonymousClassDeclaration) {
                    target = "anonymous class";
                } else if (body instanceof TypeDeclaration) {
                    name = ((TypeDeclaration) body).getName().toString();
                    target = String.format("class %1$s", name);
                } else {
                    target = body.getClass().getSimpleName();
                }

                String desc = String.format("Add @SuppressLint(\"%1$s\") on the %2$s", id, target);
                resolutions.add(new AddSuppressLintFix(id, marker, declaration, desc));
            }

            body = body.getParent();
        }
    }
}
