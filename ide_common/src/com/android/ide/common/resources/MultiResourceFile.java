/*
 * Copyright (C) 2007 The Android Open Source Project
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
import com.android.io.IAbstractFile;
import com.android.io.StreamException;
import com.android.resources.ResourceType;

import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * Represents a resource file able to declare multiple resources, which could be of
 * different {@link ResourceType}.
 * <p/>
 * This is typically an XML file inside res/values.
 */
public final class MultiResourceFile extends ResourceFile implements IValueResourceRepository {

    private final static SAXParserFactory sParserFactory = SAXParserFactory.newInstance();

    private final Map<ResourceType, Map<String, ResourceValue>> mResourceItems =
        new EnumMap<ResourceType, Map<String, ResourceValue>>(ResourceType.class);

    private Collection<ResourceType> mResourceTypeList = null;

    public MultiResourceFile(IAbstractFile file, ResourceFolder folder) {
        super(file, folder);
    }

    @Override
    protected void load() {
        // need to parse the file and find the content.
        parseFile();

        // create new ResourceItems for the new content.
        mResourceTypeList = Collections.unmodifiableCollection(mResourceItems.keySet());

        // create/update the resource items.
        updateResourceItems();
    }

    @Override
    protected void update() {
        // remove this file from all existing ResourceItem.
        getFolder().getRepository().removeFile(mResourceTypeList, this);

        // reset current content.
        mResourceItems.clear();

        // need to parse the file and find the content.
        parseFile();

        // create new ResourceItems for the new content.
        mResourceTypeList = Collections.unmodifiableCollection(mResourceItems.keySet());

        // create/update the resource items.
        updateResourceItems();
    }

    @Override
    protected void dispose() {
        // only remove this file from all existing ResourceItem.
        getFolder().getRepository().removeFile(mResourceTypeList, this);

        // don't need to touch the content, it'll get reclaimed as this objects disappear.
        // In the mean time other objects may need to access it.
    }

    @Override
    public Collection<ResourceType> getResourceTypes() {
        return mResourceTypeList;
    }

    @Override
    public boolean hasResources(ResourceType type) {
        Map<String, ResourceValue> list = mResourceItems.get(type);
        return (list != null && list.size() > 0);
    }

    private void updateResourceItems() {
        ResourceRepository repository = getRepository();
        for (ResourceType type : mResourceTypeList) {
            Map<String, ResourceValue> list = mResourceItems.get(type);

            if (list != null) {
                Collection<ResourceValue> values = list.values();
                for (ResourceValue res : values) {
                    ResourceItem item = repository.getResourceItem(type, res.getName());

                    // add this file to the list of files generating this resource item.
                    item.add(this);
                }
            }
        }
    }

    /**
     * Parses the file and creates a list of {@link ResourceType}.
     */
    private void parseFile() {
        try {
            SAXParser parser = sParserFactory.newSAXParser();
            parser.parse(getFile().getContents(), new ValueResourceParser(this, isFramework()));
        } catch (ParserConfigurationException e) {
        } catch (SAXException e) {
        } catch (IOException e) {
        } catch (StreamException e) {
        }
    }

    /**
     * Adds a resource item to the list
     * @param resType The type of the resource
     * @param value The value of the resource.
     */
    public void addResourceValue(ResourceType resType, ResourceValue value) {
        Map<String, ResourceValue> list = mResourceItems.get(resType);

        // if the list does not exist, create it.
        if (list == null) {
            list = new HashMap<String, ResourceValue>();
            mResourceItems.put(resType, list);
        } else {
            // look for a possible value already existing.
            ResourceValue oldValue = list.get(value.getName());

            if (oldValue != null) {
                oldValue.replaceWith(value);
                return;
            }
        }

        // empty list or no match found? add the given resource
        list.put(value.getName(), value);
    }

    @Override
    public ResourceValue getValue(ResourceType type, String name) {
        // get the list for the given type
        Map<String, ResourceValue> list = mResourceItems.get(type);

        if (list != null) {
            return list.get(name);
        }

        return null;
    }
}
