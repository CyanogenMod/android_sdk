/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.lint.detector.api;

import static com.android.tools.lint.detector.api.LintConstants.DOT_CLASS;
import static com.android.tools.lint.detector.api.LintConstants.DOT_JAVA;

import com.android.tools.lint.client.api.LintClient;
import com.google.common.annotations.Beta;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.util.EnumSet;
import java.util.List;

/**
 * A {@link Context} used when checking .class files.
 * <p/>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
@Beta
public class ClassContext extends Context {
    private final File mBinDir;
    private ClassNode mClassNode;
    private byte[] mBytes;

    /**
     * Construct a new {@link ClassContext}
     *
     * @param client the client requesting a lint check
     * @param project the project containing the file being checked
     * @param file the file being checked
     * @param scope the scope for the lint job
     * @param binDir the root binary directory containing this .class file.
     * @param bytes the bytecode raw data
     * @param classNode the bytecode object model
     */
    public ClassContext(LintClient client, Project project, File file,
            EnumSet<Scope> scope, File binDir, byte[] bytes, ClassNode classNode) {
        super(client, project, file, scope);
        mBinDir = binDir;
        mBytes = bytes;
        mClassNode = classNode;
    }

    /**
     * Returns the raw bytecode data for this class file
     *
     * @return the byte array containing the bytecode data, or null
     */
    public byte[] getBytecode() {
        return mBytes;
    }

    /**
     * Returns the bytecode object model
     *
     * @return the bytecode object model
     */
    public ClassNode getClassNode() {
        return mClassNode;
    }

    /**
     * Finds the corresponding source file for the current class file.
     *
     * @param source the name of the source file, if known (which is often
     *            stored in the bytecode and provided by the
     *            {@link ClassVisitor#visitSource(String, String)} method). If
     *            not known, this method will guess by stripping out inner-class
     *            suffixes like $1.
     * @return the source file corresponding to the {@link #file} field.
     */
    public File findSourceFile(String source) {
        if (source == null) {
            source = file.getName();
            if (source.endsWith(DOT_CLASS)) {
                source = source.substring(0, source.length() - DOT_CLASS.length()) + DOT_JAVA;
            }
            int index = source.indexOf('$');
            if (index != -1) {
                source = source.substring(0, index) + DOT_JAVA;
            }
        }
        if (source != null) {
            // Determine package
            String topPath = mBinDir.getPath();
            String parentPath = file.getParentFile().getPath();
            if (parentPath.startsWith(topPath)) {
                String relative = parentPath.substring(topPath.length() + 1);
                List<File> sources = getProject().getJavaSourceFolders();
                for (File dir : sources) {
                    File sourceFile = new File(dir, relative + File.separator + source);
                    if (sourceFile.exists()) {
                        return sourceFile;
                    }
                }
            }
        }

        return null;
    }
}
