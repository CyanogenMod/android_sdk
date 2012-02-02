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
import com.android.tools.lint.client.api.LintDriver;
import com.google.common.annotations.Beta;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

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
    /** If the file is a relative path within a jar file, this is the jar file, otherwise null */
    private final File mJarFile;

    /**
     * Construct a new {@link ClassContext}
     *
     * @param driver the driver running through the checks
     * @param project the project containing the file being checked
     * @param main the main project if this project is a library project, or null if this
     *            is not a library project. The main project is the root project of all
     *            library projects, not necessarily the directly including project.
     * @param file the file being checked
     * @param jarFile If the file is a relative path within a jar file, this is the jar
     *            file, otherwise null
     * @param binDir the root binary directory containing this .class file.
     * @param bytes the bytecode raw data
     * @param classNode the bytecode object model
     */
    public ClassContext(
            @NonNull LintDriver driver,
            @NonNull Project project,
            @Nullable Project main,
            @NonNull File file,
            @Nullable File jarFile,
            @NonNull File binDir,
            @NonNull byte[] bytes,
            @NonNull ClassNode classNode) {
        super(driver, project, main, file);
        mJarFile = jarFile;
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
     * Returns the jar file, if any. If this is null, the .class file is a real file
     * on disk, otherwise it represents a relative path within the jar file.
     *
     * @return the jar file, or null
     */
    @Nullable
    public File getJarFile() {
        return mJarFile;
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
                if (mJarFile != null) {
                    String relative = file.getParent() + File.separator + source;
                    List<File> sources = getProject().getJavaSourceFolders();
                    for (File dir : sources) {
                        File sourceFile = new File(dir, relative);
                        if (sourceFile.exists()) {
                            mSourceFile = sourceFile;
                            break;
                        }
                    }
                } else {
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
            // ASM line numbers are 1-based, and lint line numbers are 0-based
            return Location.create(sourceFile, getSourceContents(), line - 1,
                    patternStart, patternEnd);
        }

        return Location.create(file);
    }

    /**
     * Reports an issue.
     * <p>
     * Detectors should only call this method if an error applies to the whole class
     * scope and there is no specific method or field that applies to the error.
     * If so, use
     * {@link #report(Issue, MethodNode, Location, String, Object)} or
     * {@link #report(Issue, FieldNode, Location, String, Object)}, such that
     * suppress annotations are checked.
     *
     * @param issue the issue to report
     * @param location the location of the issue, or null if not known
     * @param message the message for this warning
     * @param data any associated data, or null
     */
    @Override
    public void report(Issue issue, Location location, String message, Object data) {
        if (mDriver.isSuppressed(issue, mClassNode)) {
            return;
        }
        super.report(issue, location, message, data);
    }

    // Unfortunately, ASMs nodes do not extend a common DOM node type with parent
    // pointers, so we have to have multiple methods which pass in each type
    // of node (class, method, field) to be checked.

    /**
     * Reports an issue applicable to a given method node.
     *
     * @param issue the issue to report
     * @param method the method scope the error applies to. The lint infrastructure
     *    will check whether there are suppress annotations on this method (or its enclosing
     *    class) and if so suppress the warning without involving the client.
     * @param location the location of the issue, or null if not known
     * @param message the message for this warning
     * @param data any associated data, or null
     */
    public void report(
            @NonNull Issue issue,
            @Nullable MethodNode method,
            @Nullable Location location,
            @NonNull String message,
            @Nullable Object data) {
        if (method != null && mDriver.isSuppressed(issue, method)) {
            return;
        }
        report(issue, location, message, data); // also checks the class node
    }

    /**
     * Reports an issue applicable to a given method node.
     *
     * @param issue the issue to report
     * @param field the scope the error applies to. The lint infrastructure
     *    will check whether there are suppress annotations on this field (or its enclosing
     *    class) and if so suppress the warning without involving the client.
     * @param location the location of the issue, or null if not known
     * @param message the message for this warning
     * @param data any associated data, or null
     */
    public void report(
            @NonNull Issue issue,
            @Nullable FieldNode field,
            @Nullable Location location,
            @NonNull String message,
            @Nullable Object data) {
        if (field != null && mDriver.isSuppressed(issue, field)) {
            return;
        }
        report(issue, location, message, data); // also checks the class node
    }
}
