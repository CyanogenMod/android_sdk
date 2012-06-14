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
package com.android.ide.eclipse.adt.internal.wizards.templates;

import static com.android.ide.eclipse.adt.internal.wizards.templates.TemplateHandler.ATTR_DESCRIPTION;
import static com.android.ide.eclipse.adt.internal.wizards.templates.TemplateHandler.ATTR_FORMAT;
import static com.android.ide.eclipse.adt.internal.wizards.templates.TemplateHandler.ATTR_NAME;
import static com.android.ide.eclipse.adt.internal.wizards.templates.TemplateHandler.TAG_PARAMETER;
import static com.android.ide.eclipse.adt.internal.wizards.templates.TemplateHandler.TAG_THUMB;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.eclipse.adt.AdtPlugin;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** An ADT template along with metadata */
class TemplateMetadata {
    private final Document mDocument;
    private final List<Parameter> mParameters;
    private final Map<String, Parameter> mParameterMap;

    TemplateMetadata(@NonNull Document document) {
        mDocument = document;

        NodeList parameters = mDocument.getElementsByTagName(TAG_PARAMETER);
        mParameters = new ArrayList<Parameter>(parameters.getLength());
        mParameterMap = new HashMap<String, Parameter>(parameters.getLength());
        for (int index = 0, max = parameters.getLength(); index < max; index++) {
            Element element = (Element) parameters.item(index);
            Parameter parameter = new Parameter(element);
            mParameters.add(parameter);
            if (parameter.id != null) {
                mParameterMap.put(parameter.id, parameter);
            }
        }
    }

    public boolean isSupported() {
        String versionString = mDocument.getDocumentElement().getAttribute(ATTR_FORMAT);
        if (versionString != null && !versionString.isEmpty()) {
            try {
                int version = Integer.parseInt(versionString);
                return version <= TemplateHandler.CURRENT_FORMAT;
            } catch (NumberFormatException nufe) {
                return false;
            }
        }

        // Older templates without version specified: supported
        return true;
    }

    @Nullable
    String getTitle() {
        String name = mDocument.getDocumentElement().getAttribute(ATTR_NAME);
        if (name != null && !name.isEmpty()) {
            return name;
        }

        return null;
    }

    @Nullable
    String getDescription() {
        String description = mDocument.getDocumentElement().getAttribute(ATTR_DESCRIPTION);
        if (description != null && !description.isEmpty()) {
            return description;
        }

        return null;
    }

    @Nullable
    String getThumbnailPath() {
        // Apply selector logic. Pick the thumb first thumb that satisfies the largest number
        // of conditions.
        NodeList thumbs = mDocument.getElementsByTagName(TAG_THUMB);
        if (thumbs.getLength() == 0) {
            return null;
        }


        int bestMatchCount = 0;
        Element bestMatch = null;

        for (int i = 0, n = thumbs.getLength(); i < n; i++) {
            Element thumb = (Element) thumbs.item(i);

            NamedNodeMap attributes = thumb.getAttributes();
            if (bestMatch == null && attributes.getLength() == 0) {
                bestMatch = thumb;
            } else if (attributes.getLength() <= bestMatchCount) {
                // Already have a match with this number of attributes, no point checking
                continue;
            } else {
                boolean match = true;
                for (int j = 0, max = attributes.getLength(); j < max; j++) {
                    Attr attribute = (Attr) attributes.item(j);
                    Parameter parameter = mParameterMap.get(attribute.getName());
                    if (parameter == null) {
                        AdtPlugin.log(null, "Unexpected parameter in template thumbnail: %1$s",
                                attribute.getName());
                        continue;
                    }
                    String thumbNailValue = attribute.getValue();
                    String editedValue = parameter.value != null ? parameter.value.toString() : "";
                    if (!thumbNailValue.equals(editedValue)) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    bestMatch = thumb;
                    bestMatchCount = attributes.getLength();
                }
            }
        }

        if (bestMatch != null) {
            NodeList children = bestMatch.getChildNodes();
            for (int i = 0, n = children.getLength(); i < n; i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.TEXT_NODE) {
                    return child.getNodeValue().trim();
                }
            }
        }

        return null;
    }

    /** Returns the list of available parameters */
    @NonNull
    List<Parameter> getParameters() {
        return mParameters;
    }
}
