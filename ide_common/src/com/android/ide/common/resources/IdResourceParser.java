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

package com.android.ide.common.resources;

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ValueResourceParser.IValueResourceRepository;
import com.android.resources.ResourceType;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class IdResourceParser extends DefaultHandler {

    private final IValueResourceRepository mRepository;
    private final boolean mIsFramework;

    public IdResourceParser(IValueResourceRepository repository, boolean isFramework) {
        super();
        mRepository = repository;
        mIsFramework = isFramework;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes)
            throws SAXException {
        for (int i = 0; i < attributes.getLength(); ++i) {
            // Let's look up the value to look for the @+*id/ pattern
            String candidate = attributes.getValue(i);
            // Right now the only things that start with the @+ pattern are IDs. If this changes
            // in the future we'll have to change this line
            if (candidate != null && candidate.startsWith("@+")) {
                // Strip out the @+id/ or @+android:id/ section
                String id = candidate.substring(candidate.indexOf('/') + 1);
                ResourceValue newId = new ResourceValue(ResourceType.ID, id, mIsFramework);
                mRepository.addResourceValue(newId);
            }
        }
    }
}
