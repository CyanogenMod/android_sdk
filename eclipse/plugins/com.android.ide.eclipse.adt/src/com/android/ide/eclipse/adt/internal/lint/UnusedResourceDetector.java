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

import com.android.ide.eclipse.adt.AdtUtils;
import com.android.ide.eclipse.adt.internal.project.BaseProjectHelper;
import com.android.tools.lint.detector.api.Context;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds unused resources, optimized for Eclipse to more effectively and
 * accurately pull out usage information for R fields
 */
class UnusedResourceDetector extends com.android.tools.lint.checks.UnusedResourceDetector {
    private static final String R_PREFIX = "R."; //$NON-NLS-1$

    public UnusedResourceDetector() {
    }

    @Override
    public void checkJavaSources(Context context, List<File> sourceFolders) {
        IProject project = getProject(context);
        if (project == null) {
            return;
        }
        IJavaProject javaProject;
        try {
            javaProject = BaseProjectHelper.getJavaProject(project);
        } catch (CoreException e) {
            context.client.log(e, null);
            return;
        }
        if (javaProject == null) {
            return;
        }

        // Scan Java code in project for R.field references
        try {
            IPackageFragment[] packages = javaProject.getPackageFragments();
            for (IPackageFragment pkg : packages) {
                if (pkg.getKind() == IPackageFragmentRoot.K_SOURCE) {
                    for (ICompilationUnit unit : pkg.getCompilationUnits()) {

                        ASTParser parser = ASTParser.newParser(AST.JLS3);
                        parser.setKind(ASTParser.K_COMPILATION_UNIT);
                        parser.setSource(unit);
                        parser.setResolveBindings(true);
                        CompilationUnit parse = (CompilationUnit) parser.createAST(null); // parse

                        // In the R file, look for known declarations
                        if ("R.java".equals(unit.getResource().getName())) { //$NON-NLS-1$
                            ResourceDeclarationVisitor visitor = new ResourceDeclarationVisitor();
                            parse.accept(visitor);
                            mDeclarations.addAll(visitor.getDeclarations());
                        } else {
                            ResourceRefCollector visitor = new ResourceRefCollector();
                            parse.accept(visitor);
                            List<String> refs = visitor.getRefs();
                            mReferences.addAll(refs);
                        }
                    }
                }
            }
        } catch (CoreException e) {
            context.client.log(e, null);
        }
    }

    private IProject getProject(Context context) {
        // Look up project
        IResource file = AdtUtils.fileToResource(context.file);
        if (file != null) {
            return file.getProject();
        }

        return null;
    }

    private static class ResourceRefCollector extends ASTVisitor {
        private List<String> mRefs = new ArrayList<String>();

        @Override
        public boolean visit(QualifiedName node) {
            if (node.getQualifier().toString().startsWith(R_PREFIX)) {
                mRefs.add(node.getFullyQualifiedName());
            }
            return super.visit(node);
        }

        public List<String> getRefs() {
            return mRefs;
        }
    }

    private static class ResourceDeclarationVisitor extends ASTVisitor {
        private List<String> mDeclarations = new ArrayList<String>();

        @Override
        public boolean visit(FieldDeclaration node) {
            @SuppressWarnings("rawtypes")
            List fragments = node.fragments();
            for (int i = 0, n = fragments.size(); i < n; i++) {
                Object f = fragments.get(i);
                if (f instanceof VariableDeclarationFragment) {
                    VariableDeclarationFragment fragment = (VariableDeclarationFragment) f;
                    String name = fragment.getName().toString();
                    if (node.getParent() instanceof TypeDeclaration) {
                        TypeDeclaration parent = (TypeDeclaration) node.getParent();
                        String type = parent.getName().toString();
                        mDeclarations.add(R_PREFIX + type + '.' + name);
                    }
                }
            }
            return super.visit(node);
        }

        // TODO: Check for reflection: check Strings as well?

        public List<String> getDeclarations() {
            return mDeclarations;
        }
    }
}
