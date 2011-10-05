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

package com.android.tools.lint.api;

import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Position;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * A wrapper for XML parser. This allows tools integrating lint to map directly
 * to builtin services, such as already-parsed data structures in XML editors.
 * <p/>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
public interface IDomParser {
    /**
     * Parse the file pointed to by the given context and return as a Document
     *
     * @param context the context pointing to the file to be parsed, typically
     *            via {@link Context#getContents()} but the file handle (
     *            {@link Context#file} can also be used to map to an existing
     *            editor buffer in the surrounding tool, etc)
     * @return the parsed DOM document, or null if parsing fails
     */
    public Document parse(Context context);

    /**
     * Returns the starting position of the given DOM node (which may not be
     * just an element but can for example also be an {@link Attr} node). The
     * node will *always* be from the same DOM document that was returned by
     * this parser.
     *
     * @param context information about the file being parsed
     * @param node the node to look up a starting position for
     * @return the position of the beginning of the node
     */
    public Position getStartPosition(Context context, Node node);

    /**
     * Returns the ending position of the given DOM node.
     *
     * @param context information about the file being parsed
     * @param node the node to look up a ending position for
     * @return the position of the end of the node
     */
    public Position getEndPosition(Context context, Node node);
}
