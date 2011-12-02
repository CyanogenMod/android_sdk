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

import com.android.tools.lint.client.api.IJavaParser;
import com.android.tools.lint.client.api.LintClient;

import java.io.File;
import java.util.EnumSet;

import lombok.ast.Node;

/**
 * A {@link Context} used when checking Java files.
 * <p/>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
public class JavaContext extends Context {
    /** The parse tree */
    public Node compilationUnit;
    /** The parser which produced the parse tree */
    public IJavaParser parser;

    /**
     * Constructs a {@link JavaContext} for running lint on the given file, with
     * the given scope, in the given project reporting errors to the given
     * client.
     *
     * @param client the client to report errors to
     * @param project the project to run lint on which contains the given file
     * @param main the main project if this project is a library project, or
     *            null if this is not a library project. The main project is
     *            the root project of all library projects, not necessarily the
     *            directly including project.
     * @param file the file to be analyzed
     * @param scope the scope used for analysis
     */
    public JavaContext(LintClient client, Project project, Project main, File file,
            EnumSet<Scope> scope) {
        super(client, project, main, file, scope);
    }

    /**
     * Returns a location for the given node
     *
     * @param node the AST node to get a location for
     * @return a location for the given node
     */
    public Location getLocation(Node node) {
        if (parser != null) {
            return parser.getLocation(this, node);
        }

        return new Location(file, null, null);
    }
}
