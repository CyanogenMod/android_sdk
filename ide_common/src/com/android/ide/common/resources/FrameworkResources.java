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

import static com.android.AndroidConstants.FD_RES_VALUES;

import com.android.ide.common.log.ILogger;
import com.android.io.IAbstractFile;
import com.android.io.IAbstractFolder;
import com.android.resources.ResourceType;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Framework resources repository.
 *
 * This behaves the same as {@link ResourceRepository} except that it differentiates between
 * resources that are public and non public.
 * {@link #getResources(ResourceType)} and {@link #hasResourcesOfType(ResourceType)} only return
 * public resources. This is typically used to display resource lists in the UI.
 *
 * {@link #getConfiguredResources(com.android.ide.eclipse.adt.internal.resources.configurations.FolderConfiguration)}
 * returns all resources, even the non public ones so that this can be used for rendering.
 */
public class FrameworkResources extends ResourceRepository {

    /**
     * Map of {@link ResourceType} to list of items. It is guaranteed to contain a list for all
     * possible values of ResourceType.
     */
    protected final Map<ResourceType, List<ResourceItem>> mPublicResourceMap =
        new EnumMap<ResourceType, List<ResourceItem>>(ResourceType.class);

    public FrameworkResources() {
        super(true /*isFrameworkRepository*/);
    }

    /**
     * Returns a {@link Collection} (always non null, but can be empty) of <b>public</b>
     * {@link ResourceItem} matching a given {@link ResourceType}.
     *
     * @param type the type of the resources to return
     * @return a collection of items, possible empty.
     */
    @Override
    public List<ResourceItem> getResourceItemsOfType(ResourceType type) {
        return mPublicResourceMap.get(type);
    }

    /**
     * Returns whether the repository has <b>public</b> resources of a given {@link ResourceType}.
     * @param type the type of resource to check.
     * @return true if the repository contains resources of the given type, false otherwise.
     */
    @Override
    public boolean hasResourcesOfType(ResourceType type) {
        return mPublicResourceMap.get(type).size() > 0;
    }

    @Override
    protected ResourceItem createResourceItem(String name) {
        return new FrameworkResourceItem(name);
    }

    /**
     * Reads the public.xml file in data/res/values/ for a given resource folder and builds up
     * a map of public resources.
     *
     * This map is a subset of the full resource map that only contains framework resources
     * that are public.
     *
     * @param osFrameworkResourcePath The root folder of the resources
     */
    public void loadPublicResources(IAbstractFolder resFolder, ILogger logger) {
        IAbstractFolder valueFolder = resFolder.getFolder(FD_RES_VALUES);
        if (valueFolder.exists() == false) {
            return;
        }

        IAbstractFile publicXmlFile = valueFolder.getFile("public.xml"); //$NON-NLS-1$
        if (publicXmlFile.exists()) {
            Document document = null;
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            Reader reader = null;
            try {
                reader = new BufferedReader(new InputStreamReader(publicXmlFile.getContents()));
                InputSource is = new InputSource(reader);
                factory.setNamespaceAware(true);
                factory.setValidating(false);
                DocumentBuilder builder = factory.newDocumentBuilder();
                document = builder.parse(is);

                ResourceType lastType = null;
                String lastTypeName = "";

                NodeList children = document.getDocumentElement().getChildNodes();
                for (int i = 0, n = children.getLength(); i < n; i++) {
                    Node node = children.item(i);
                    if (node.getNodeType() == Node.ELEMENT_NODE) {
                        Element element = (Element) node;
                        String name = element.getAttribute("name"); //$NON-NLS-1$
                        if (name.length() > 0) {
                            String typeName = element.getAttribute("type"); //$NON-NLS-1$
                            ResourceType type = null;
                            if (typeName.equals(lastTypeName)) {
                                type = lastType;
                            } else {
                                type = ResourceType.getEnum(typeName);
                                lastType = type;
                                lastTypeName = typeName;
                            }
                            if (type != null) {
                                List<ResourceItem> typeList = mResourceMap.get(type);

                                ResourceItem match = null;
                                if (typeList != null) {
                                    for (ResourceItem item : typeList) {
                                        if (name.equals(item.getName())) {
                                            match = item;
                                            break;
                                        }
                                    }
                                }

                                if (match != null) {
                                    List<ResourceItem> publicList = mPublicResourceMap.get(type);
                                    if (publicList == null) {
                                        publicList = new ArrayList<ResourceItem>();
                                        mPublicResourceMap.put(type, publicList);
                                    }

                                    publicList.add(match);
                                } else {
                                    // log that there's a public resource that doesn't actually
                                    // exist?
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                if (logger != null) {
                    logger.error(e, "Can't read and parse public attribute list");
                }
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        // Nothing to be done here - we don't care if it closed or not.
                    }
                }
            }
        }

        // put unmodifiable list for all res type in the public resource map
        // this will simplify access
        for (ResourceType type : ResourceType.values()) {
            List<ResourceItem> list = mPublicResourceMap.get(type);
            if (list == null) {
                list = Collections.emptyList();
            } else {
                list = Collections.unmodifiableList(list);
            }

            // put the new list in the map
            mPublicResourceMap.put(type, list);
        }
    }
}
