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

import com.android.ide.common.rendering.api.DensityBasedResourceValue;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ValueResourceParser.IValueResourceRepository;
import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.io.IAbstractFile;
import com.android.io.StreamException;
import com.android.resources.ResourceType;

import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * Represents a resource file that also generates ID resources.
 * <p/>
 * This is typically an XML file in res/layout or res/menu
 */
public final class IdGeneratingResourceFile extends ResourceFile
                                            implements IValueResourceRepository {

    private final static SAXParserFactory sParserFactory = SAXParserFactory.newInstance();
    static {
        sParserFactory.setNamespaceAware(true);
    }

    private final Map<String, ResourceValue> mIdResources =
        new HashMap<String, ResourceValue>();

    private final Collection<ResourceType> mResourceTypeList;

    private final String mFileName;

    private final ResourceType mFileType;

    private final ResourceValue mFileValue;

    public IdGeneratingResourceFile(IAbstractFile file, ResourceFolder folder, ResourceType type) {
        super(file, folder);

        mFileType = type;

        // Set up our resource types
        mResourceTypeList = new HashSet<ResourceType>();
        mResourceTypeList.add(mFileType);
        mResourceTypeList.add(ResourceType.ID);

        // compute the resource name
        mFileName = getFileName(type);

        // Get the resource value of this file as a whole layout
        mFileValue = getFileValue(file,folder);
    }

    @Override
    protected void load() {
        // Parse the file and look for @+id/ entries
        parseFileForIds();

        // create the resource items in the repository
        updateResourceItems();
    }

    @Override
    protected void update() {
        // remove this file from all existing ResourceItem.
        getFolder().getRepository().removeFile(mResourceTypeList, this);

        // reset current content.
        mIdResources.clear();

        // need to parse the file and find the IDs.
        parseFileForIds();

        // Notify the repository about any changes
        updateResourceItems();
    }

    @Override
    protected void dispose() {
        // Remove declarations from this file from the repository
        getFolder().getRepository().removeFile(mResourceTypeList, this);
    }

    @Override
    public Collection<ResourceType> getResourceTypes() {
        return mResourceTypeList;
    }

    @Override
    public boolean hasResources(ResourceType type) {
        return (type == mFileType) || (type == ResourceType.ID && !mIdResources.isEmpty());
    }

    @Override
    public ResourceValue getValue(ResourceType type, String name) {
        // Check to see if they're asking for one of the right types:
        if (type != mFileType && type != ResourceType.ID) {
            return null;
        }

        // If they're looking for a resource of this type with this name give them the whole file
        if (type == mFileType && name.equals(mFileName)) {
            return mFileValue;
        } else {
            // Otherwise try to return them an ID
            // the map will return null if it's not found
            return mIdResources.get(name);
        }
    }

    /**
     * Looks through the file represented for Ids and adds them to
     * our id repository
     */
    private void parseFileForIds() {
        try {
            SAXParser parser = sParserFactory.newSAXParser();
            parser.parse(getFile().getContents(), new IdResourceParser(this, isFramework()));
        } catch (ParserConfigurationException e) {
        } catch (SAXException e) {
        } catch (IOException e) {
        } catch (StreamException e) {
        }
    }

    /**
     * Add the resources represented by this file to the repository
     */
    private void updateResourceItems() {
        ResourceRepository repository = getRepository();

        // First add this as a layout file
        ResourceItem item = repository.getResourceItem(mFileType, mFileName);
        item.add(this);

        // Now iterate through our IDs and add
        for (String idName : mIdResources.keySet()) {
            item = repository.getResourceItem(ResourceType.ID, idName);
            // add this file to the list of files generating ID resources.
            item.add(this);
        }
    }

    /**
     * Returns the resource value associated with this whole file as a layout resource
     * @param file the file handler that represents this file
     * @param folder the folder this file is under
     * @return a resource value associated with this layout
     */
    private ResourceValue getFileValue(IAbstractFile file, ResourceFolder folder) {
        // test if there's a density qualifier associated with the resource
        DensityQualifier qualifier = folder.getConfiguration().getDensityQualifier();

        ResourceValue value;
        if (qualifier == null) {
            value = new ResourceValue(mFileType, mFileName,
                    file.getOsLocation(), isFramework());
        } else {
            value = new DensityBasedResourceValue(
                    mFileType, mFileName,
                    file.getOsLocation(),
                    qualifier.getValue(),
                    isFramework());
        }
        return value;
    }


    /**
     * Returns the name of this resource.
     */
    private String getFileName(ResourceType type) {
        // get the name from the filename.
        String name = getFile().getName();

        int pos = name.indexOf('.');
        if (pos != -1) {
            name = name.substring(0, pos);
        }

        return name;
    }

    public void addResourceValue(ResourceValue value) {
        // Just overwrite collisions. We're only interested in the unique
        // IDs declared
        mIdResources.put(value.getName(), value);
    }
}
