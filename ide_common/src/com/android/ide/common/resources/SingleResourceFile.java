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

import com.android.ide.common.rendering.api.DensityBasedResourceValue;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.io.IAbstractFile;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceType;

import java.util.Collection;
import java.util.List;

import javax.xml.parsers.SAXParserFactory;

/**
 * Represents a resource file describing a single resource.
 * <p/>
 * This is typically an XML file inside res/anim, res/layout, or res/menu or an image file
 * under res/drawable.
 */
public class SingleResourceFile extends ResourceFile {

    private final static SAXParserFactory sParserFactory = SAXParserFactory.newInstance();
    static {
        sParserFactory.setNamespaceAware(true);
    }

    private final String mResourceName;
    private final ResourceType mType;
    private ResourceValue mValue;

    public SingleResourceFile(IAbstractFile file, ResourceFolder folder) {
        super(file, folder);

        // we need to infer the type of the resource from the folder type.
        // This is easy since this is a single Resource file.
        List<ResourceType> types = FolderTypeRelationship.getRelatedResourceTypes(folder.getType());
        mType = types.get(0);

        // compute the resource name
        mResourceName = getResourceName(mType);

        // test if there's a density qualifier associated with the resource
        DensityQualifier qualifier = folder.getConfiguration().getDensityQualifier();

        if (qualifier == null) {
            mValue = new ResourceValue(mType, getResourceName(mType),
                    file.getOsLocation(), isFramework());
        } else {
            mValue = new DensityBasedResourceValue(
                    mType,
                    getResourceName(mType),
                    file.getOsLocation(),
                    qualifier.getValue(),
                    isFramework());
        }
    }

    @Override
    protected void load() {
        // get a resource item matching the given type and name
        ResourceItem item = getRepository().getResourceItem(mType, mResourceName);

        // add this file to the list of files generating this resource item.
        item.add(this);

        // Ask for an ID refresh since we're adding an item that will generate an ID
        getRepository().markForIdRefresh();
    }

    @Override
    protected void update() {
        // when this happens, nothing needs to be done since the file only generates
        // a single resources that doesn't actually change (its content is the file path)
    }

    @Override
    protected void dispose() {
        // only remove this file from the existing ResourceItem.
        getFolder().getRepository().removeFile(mType, this);

        // Ask for an ID refresh since we're removing an item that previously generated an ID
        getRepository().markForIdRefresh();

        // don't need to touch the content, it'll get reclaimed as this objects disappear.
        // In the mean time other objects may need to access it.
    }

    @Override
    public Collection<ResourceType> getResourceTypes() {
        return FolderTypeRelationship.getRelatedResourceTypes(getFolder().getType());
    }

    @Override
    public boolean hasResources(ResourceType type) {
        return FolderTypeRelationship.match(type, getFolder().getType());
    }

    /*
     * (non-Javadoc)
     * @see com.android.ide.eclipse.editors.resources.manager.ResourceFile#getValue(com.android.ide.eclipse.common.resources.ResourceType, java.lang.String)
     *
     * This particular implementation does not care about the type or name since a
     * SingleResourceFile represents a file generating only one resource.
     * The value returned is the full absolute path of the file in OS form.
     */
    @Override
    public ResourceValue getValue(ResourceType type, String name) {
        return mValue;
    }

    /**
     * Returns the name of the resources.
     */
    private String getResourceName(ResourceType type) {
        // get the name from the filename.
        String name = getFile().getName();

        int pos = name.indexOf('.');
        if (pos != -1) {
            name = name.substring(0, pos);
        }

        return name;
    }
}
