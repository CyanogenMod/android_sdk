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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.Lint;
import com.google.common.annotations.Beta;

import org.objectweb.asm.tree.ClassNode;

import java.io.File;
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
    /** The class file DOM root node */
    private ClassNode mClassNode;
    /** The class file byte data */
    private byte[] mBytes;
    /** The source file, if known/found */
    private File mSourceFile;
    /** The contents of the source file, if source file is known/found */
    private String mSourceContents;
    /** Whether we've searched for the source file (used to avoid repeated failed searches) */
    private boolean mSearchedForSource;

    /**
     * Construct a new {@link ClassContext}
     *
     * @param driver the driver running through the checks
     * @param project the project containing the file being checked
     * @param main the main project if this project is a library project, or
     *            null if this is not a library project. The main project is
     *            the root project of all library projects, not necessarily the
     *            directly including project.
     * @param file the file being checked
     * @param binDir the root binary directory containing this .class file.
     * @param bytes the bytecode raw data
     * @param classNode the bytecode object model
     */
    public ClassContext(
            @NonNull Lint driver,
            @NonNull Project project,
            @Nullable Project main,
            @NonNull File file,
            @NonNull File binDir,
            @NonNull byte[] bytes,
            @NonNull ClassNode classNode) {
        super(driver, project, main, file);
        mBinDir = binDir;
        mBytes = bytes;
        mClassNode = classNode;
    }

    /**
     * Returns the raw bytecode data for this class file
     *
     * @return the byte array containing the bytecode data
     */
    @NonNull
    public byte[] getBytecode() {
        return mBytes;
    }

    /**
     * Returns the bytecode object model
     *
     * @return the bytecode object model, never null
     */
    @NonNull
    public ClassNode getClassNode() {
        return mClassNode;
    }

    /**
     * Returns the source file for this class file, if possible.
     *
     * @return the source file, or null
     */
    @Nullable
    public File getSourceFile() {
        if (mSourceFile == null && !mSearchedForSource) {
            mSearchedForSource = true;

            String source = mClassNode.sourceFile;
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
                            mSourceFile = sourceFile;
                            break;
                        }
                    }
                }
            }
        }

        return mSourceFile;
    }

    /**
     * Returns the contents of the source file for this class file, if found.
     *
     * @return the source contents, or ""
     */
    @NonNull
    public String getSourceContents() {
        if (mSourceContents == null) {
            File sourceFile = getSourceFile();
            if (sourceFile != null) {
                mSourceContents = getClient().readFile(mSourceFile);
            }

            if (mSourceContents == null) {
                mSourceContents = "";
            }
        }

        return mSourceContents;
    }

    /**
     * Returns a location for the given source line number in this class file's
     * source file, if available.
     *
     * @param line the line number (1-based, which is what ASM uses)
     * @param patternStart optional pattern to search for in the source for
     *            range start
     * @param patternEnd optional pattern to search for in the source for range
     *            end
     * @return a location, never null
     */
    @NonNull
    public Location getLocationForLine(int line, String patternStart, String patternEnd) {
        File sourceFile = getSourceFile();
        if (sourceFile != null) {
            return Location.create(sourceFile, getSourceContents(), line - 1,
                    patternStart, patternEnd);
        }

        return Location.create(file);
    }
}
