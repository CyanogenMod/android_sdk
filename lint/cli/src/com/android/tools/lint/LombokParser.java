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
package com.android.tools.lint;

import com.android.tools.lint.client.api.IJavaParser;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Location.Handle;
import com.android.tools.lint.detector.api.XmlContext;

import java.io.File;
import java.util.List;

import lombok.ast.CompilationUnit;
import lombok.ast.Node;
import lombok.ast.Position;
import lombok.ast.grammar.ParseProblem;
import lombok.ast.grammar.Source;

/**
 * Java parser which uses the Lombok parser directly. This is a pretty slow parser
 * (2.5 times slower than javac, which in turn is about 3 times slower than EJC for
 * some benchmarks).
 */
public class LombokParser implements IJavaParser {

    @Override
    public Node parseJava(JavaContext context) {
        try {
            Source source = new Source(context.getContents(), context.file.getName());
            List<Node> nodes = source.getNodes();

            // Don't analyze files containing errors
            List<ParseProblem> problems = source.getProblems();
            if (problems != null && problems.size() > 0) {
                for (ParseProblem problem : problems) {
                    Position position = problem.getPosition();
                    Location location = Location.create(context.file,
                            context.getContents(), position.getStart(), position.getEnd());
                    // Sanitize the message?
                    // See http://code.google.com/p/projectlombok/issues/detail?id=313
                    String message = problem.getMessage();
                    context.report(
                            IssueRegistry.PARSER_ERROR, location,
                            message,
                            null);

                }
                return null;
            }

            // There could be more than one node when there are errors; pick out the
            // compilation unit node
            for (Node node : nodes) {
                if (node instanceof CompilationUnit) {
                    return node;
                }
            }
            return null;
        } catch (Throwable e) {
            context.report(
                    IssueRegistry.PARSER_ERROR, Location.create(context.file),
                    e.getCause() != null ? e.getCause().getLocalizedMessage() :
                        e.getLocalizedMessage(),
                    null);

            return null;
        }
    }

    @Override
    public Location getLocation(JavaContext context, lombok.ast.Node node) {
        lombok.ast.Position position = node.getPosition();
        return Location.create(context.file, context.getContents(),
                position.getStart(), position.getEnd());
    }

    @Override
    public Handle createLocationHandle(XmlContext context, Node node) {
        return new LocationHandle(context.file, node);
    }

    @Override
    public void dispose(JavaContext context, Node compilationUnit) {
    }

    /* Handle for creating positions cheaply and returning full fledged locations later */
    private class LocationHandle implements Handle {
        private File mFile;
        private Node mNode;

        public LocationHandle(File file, Node node) {
            mFile = file;
            mNode = node;
        }

        @Override
        public Location resolve() {
            Position pos = mNode.getPosition();
            return Location.create(mFile, null /*contents*/, pos.getStart(), pos.getEnd());
        }
    }
}
