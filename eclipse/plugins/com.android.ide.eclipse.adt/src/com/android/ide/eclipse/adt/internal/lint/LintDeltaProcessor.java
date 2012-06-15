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

import static com.android.ide.eclipse.adt.AdtConstants.DOT_CLASS;
import static com.android.ide.eclipse.adt.AdtConstants.DOT_JAVA;

import com.android.annotations.NonNull;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.AdtUtils;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.swt.widgets.Display;

import java.util.ArrayList;
import java.util.List;

/**
 * Delta processor for Java files, which runs single-file lints if it finds that
 * the currently active file has been updated.
 */
public class LintDeltaProcessor implements Runnable {
    private List<IResource> mFiles;
    private IFile mActiveFile;

    private LintDeltaProcessor() {
    }

    /**
     * Creates a new {@link LintDeltaProcessor}
     *
     * @return a visitor
     */
    @NonNull
    public static LintDeltaProcessor create() {
        return new LintDeltaProcessor();
    }

    /**
     * Process the given delta: update lint on any Java source and class files found.
     *
     * @param delta the delta describing recently changed files
     */
    public void process(@NonNull IResourceDelta delta)  {
        // Get the active editor file, if any
        Display display = AdtPlugin.getDisplay();
        if (display == null || display.isDisposed()) {
            return;
        }
        if (display.getThread() != Thread.currentThread()) {
            display.syncExec(this);
        } else {
            run();
        }

        if (mActiveFile == null || !mActiveFile.getName().endsWith(DOT_JAVA)) {
            return;
        }

        mFiles = new ArrayList<IResource>();
        gatherFiles(delta);

        if (!mFiles.isEmpty()) {
            EclipseLintRunner.startLint(mFiles, mActiveFile, null,
                    false /*fatalOnly*/, false /*show*/);
        }
    }

    /**
     * Collect .java and .class files to be run in lint. Only collects files
     * that match the active editor.
     */
    private void gatherFiles(@NonNull IResourceDelta delta) {
        IResource resource = delta.getResource();
        String name = resource.getName();
        if (name.endsWith(DOT_JAVA)) {
            if (resource.equals(mActiveFile)) {
                mFiles.add(resource);
            }
        } else if (name.endsWith(DOT_CLASS)) {
            // Make sure this class corresponds to the .java file, meaning it has
            // the same basename, or that it is an inner class of a class that
            // matches the same basename. (We could potentially make sure the package
            // names match too, but it's unlikely that the class names match without a
            // package match, and there's no harm in including some extra classes here,
            // since lint will resolve full paths and the resource markers won't go
            // to the wrong place, we simply end up analyzing some extra files.)
            String className = mActiveFile.getName();
            if (name.regionMatches(0, className, 0, className.length() - DOT_JAVA.length())) {
                if (name.length() == className.length() - DOT_JAVA.length() + DOT_CLASS.length()
                        || name.charAt(className.length() - DOT_JAVA.length()) == '$') {
                    mFiles.add(resource);
                }
            }
        } else {
            IResourceDelta[] children = delta.getAffectedChildren();
            if (children != null && children.length > 0) {
                for (IResourceDelta d : children) {
                    gatherFiles(d);
                }
            }
        }
    }

    @Override
    public void run() {
        // Get the active file: this must be run on the GUI thread
        mActiveFile = AdtUtils.getActiveFile();
    }
}
