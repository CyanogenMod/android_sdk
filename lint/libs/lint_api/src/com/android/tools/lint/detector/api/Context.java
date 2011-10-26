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

import com.android.tools.lint.api.IDomParser;
import com.android.tools.lint.api.ToolContext;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Context passed to the detectors during an analysis run. It provides
 * information about the file being analyzed, it allows shared properties (so
 * the detectors can share results), it contains the current location in the
 * document, etc.
 * <p>
 * TODO: This needs some cleanup. Perhaps we should split up into a FileContext
 * and a ProjectContext.
 * <p/>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
public class Context {
    public final File projectDir;
    public final File file;
    public final ToolContext toolContext;
    public final Scope scope;
    public Document document;
    public Location location;
    public Element element;
    public IDomParser parser;
    private String contents;

    /**
     * Slow-running detectors should check this flag via
     * {@link AtomicBoolean#get()} and abort if canceled
     */
    public final AtomicBoolean canceled = new AtomicBoolean();

    private Map<String, Object> properties;

    public Context(ToolContext toolContext, File projectDir, File file, Scope scope) {
        this.toolContext = toolContext;
        this.projectDir = projectDir;
        this.file = file;
        this.scope = scope;
    }

    public Location getLocation(Node node) {
        if (parser != null) {
            return new Location(file,
                    parser.getStartPosition(this, node),
                    parser.getEndPosition(this, node));
        }

        return location;
    }

    public Location getLocation(Context context) {
        if (location == null && element != null && parser != null) {
            return getLocation(element);
        }
        return location;
    }

    // TODO: This should be delegated to the tool context!
    public String getContents() {
        if (contents == null) {
            contents = toolContext.readFile(file);
        }

        return contents;
    }

    public Object getProperty(String name) {
        if (properties == null) {
            return null;
        }

        return properties.get(name);
    }

    public void setProperty(String name, Object value) {
        if (properties == null) {
            properties = new HashMap<String, Object>();
        }

        properties.put(name, value);
    }
}
