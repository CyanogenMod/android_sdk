/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.ide.eclipse.adt.internal.resources.manager;

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.eclipse.adt.internal.resources.configurations.FolderConfiguration;
import com.android.ide.eclipse.adt.internal.sdk.ProjectState;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.resources.ResourceType;
import com.android.util.Pair;

import org.eclipse.core.resources.IProject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Represents the resources of a project.
 * On top of the regular {@link ResourceRepository} features it provides:
 *<ul>
 *<li>configured resources contain the resources coming from the libraries.</li>
 *<li>resolution to and from resource integer (compiled value in R.java).</li>
 *<li>handles resource integer for non existing values of type ID. This is used when rendering.</li>
 *<li>layouts that have no been saved yet. This is handled by generating dynamic IDs
 *       on the fly.</li>
 *</ul>
 */
public class ProjectResources extends ResourceRepository {
    private final static int DYNAMIC_ID_SEED_START = 0; // this should not conflict with any
                                                        // project IDs that start at a much higher
                                                        // value

    /** Map of (name, id) for resources of type {@link ResourceType#ID} coming from R.java */
    private Map<ResourceType, Map<String, Integer>> mResourceValueMap;
    /** Map of (id, [name, resType]) for all resources coming from R.java */
    private Map<Integer, Pair<ResourceType, String>> mResIdValueToNameMap;
    /** Map of (int[], name) for styleable resources coming from R.java */
    private Map<IntArrayWrapper, String> mStyleableValueToNameMap;

    /**
     * This list is used by {@link #getResourceValue(String, String)} when the resource
     * query is an ID that doesn't exist (for example for ID automatically generated in
     * layout files that are not saved yet).
     */
    private final Map<String, Integer> mDynamicIds = new HashMap<String, Integer>();
    private int mDynamicSeed = DYNAMIC_ID_SEED_START;

    private final IProject mProject;


    /**
     * Makes a ProjectResources for a given <var>project</var>.
     * @param project the project.
     */
    public ProjectResources(IProject project) {
        super(false /*isFrameworkRepository*/);
        mProject = project;
    }

    /**
     * Returns the resources values matching a given {@link FolderConfiguration}, this will
     * include library dependency.
     *
     * @param referenceConfig the configuration that each value must match.
     * @return a map with guaranteed to contain an entry for each {@link ResourceType}
     */
    @Override
    public Map<ResourceType, Map<String, ResourceValue>> getConfiguredResources(
            FolderConfiguration referenceConfig) {

        Map<ResourceType, Map<String, ResourceValue>> resultMap =
            new EnumMap<ResourceType, Map<String, ResourceValue>>(ResourceType.class);

        // if the project contains libraries, we need to add the libraries resources here
        // so that they are accessible to the layout rendering.
        if (mProject != null) {
            ProjectState state = Sdk.getProjectState(mProject);
            if (state != null) {
                List<IProject> libraries = state.getFullLibraryProjects();

                ResourceManager resMgr = ResourceManager.getInstance();

                // because aapt put all the library in their order in this array, the first
                // one will have priority over the 2nd one. So it's better to loop in the inverse
                // order and fill the map with resources that will be overwritten by higher
                // priority resources
                for (int i = libraries.size() - 1 ; i >= 0 ; i--) {
                    IProject library = libraries.get(i);

                    ProjectResources libRes = resMgr.getProjectResources(library);
                    if (libRes != null) {
                        // get the library resources, and only the library, not the dependencies
                        // so call doGetConfiguredResources() directly.
                        Map<ResourceType, Map<String, ResourceValue>> libMap =
                                libRes.doGetConfiguredResources(referenceConfig);

                        // we don't want to simply replace the whole map, but instead merge the
                        // content of any sub-map
                        for (Entry<ResourceType, Map<String, ResourceValue>> libEntry :
                                libMap.entrySet()) {

                            // get the map currently in the result map for this resource type
                            Map<String, ResourceValue> tempMap = resultMap.get(libEntry.getKey());
                            if (tempMap == null) {
                                // since there's no current map for this type, just add the map
                                // directly coming from the library resources
                                resultMap.put(libEntry.getKey(), libEntry.getValue());
                            } else {
                                // already a map for this type. add the resources from the
                                // library, this will override existing value, which is why
                                // we loop in a specific library order.
                                tempMap.putAll(libEntry.getValue());
                            }
                        }
                    }
                }
            }
        }

        // now the project resources themselves.
        Map<ResourceType, Map<String, ResourceValue>> thisProjectMap =
                doGetConfiguredResources(referenceConfig);

        // now merge the maps.
        for (Entry<ResourceType, Map<String, ResourceValue>> entry : thisProjectMap.entrySet()) {
            ResourceType type = entry.getKey();
            Map<String, ResourceValue> typeMap = resultMap.get(type);
            if (typeMap == null) {
                resultMap.put(type, entry.getValue());
            } else {
                typeMap.putAll(entry.getValue());
            }
        }

        return resultMap;
    }

    /**
     * Resolves a compiled resource id into the resource name and type
     * @param id
     * @return an array of 2 strings { name, type } or null if the id could not be resolved
     */
    public Pair<ResourceType, String> resolveResourceValue(int id) {
        if (mResIdValueToNameMap != null) {
            return mResIdValueToNameMap.get(id);
        }

        return null;
    }

    /**
     * Resolves a compiled resource id of type int[] into the resource name.
     */
    public String resolveResourceValue(int[] id) {
        if (mStyleableValueToNameMap != null) {
            mWrapper.set(id);
            return mStyleableValueToNameMap.get(mWrapper);
        }

        return null;
    }

    /**
     * Returns the value of a resource by its type and name.
     * <p/>If the resource is of type {@link ResourceType#ID} and does not exist in the
     * internal map, then new id values are dynamically generated (and stored so that queries
     * with the same names will return the same value).
     */
    public Integer getResourceValue(ResourceType type, String name) {
        if (mResourceValueMap != null) {
            Map<String, Integer> map = mResourceValueMap.get(type);
            if (map != null) {
                Integer value = map.get(name);

                // if no value
                if (value == null && ResourceType.ID == type) {
                    return getDynamicId(name);
                }

                return value;
            } else if (ResourceType.ID == type) {
                return getDynamicId(name);
            }
        }

        return null;
    }

    /**
     * Resets the list of dynamic Ids. This list is used by
     * {@link #getResourceValue(String, String)} when the resource query is an ID that doesn't
     * exist (for example for ID automatically generated in layout files that are not saved yet.)
     * <p/>This method resets those dynamic ID and must be called whenever the actual list of IDs
     * change.
     */
    public void resetDynamicIds() {
        synchronized (mDynamicIds) {
            mDynamicIds.clear();
            mDynamicSeed = DYNAMIC_ID_SEED_START;
        }
    }

    @Override
    protected ResourceItem createResourceItem(String name) {
        return new ResourceItem(name);
    }

    /**
     * Returns a dynamic integer for the given resource name, creating it if it doesn't
     * already exist.
     *
     * @param name the name of the resource
     * @return an integer.
     *
     * @see #resetDynamicIds()
     */
    private Integer getDynamicId(String name) {
        synchronized (mDynamicIds) {
            Integer value = mDynamicIds.get(name);
            if (value == null) {
                value = new Integer(++mDynamicSeed);
                mDynamicIds.put(name, value);
            }

            return value;
        }
    }

    /**
     * Sets compiled resource information.
     * @param resIdValueToNameMap a map of compiled resource id to resource name.
     *  The map is acquired by the {@link ProjectResources} object.
     * @param styleableValueMap
     * @param resourceValueMap a map of (name, id) for resources of type {@link ResourceType#ID}.
     * The list is acquired by the {@link ProjectResources} object.
     */
    void setCompiledResources(Map<Integer, Pair<ResourceType, String>> resIdValueToNameMap,
            Map<IntArrayWrapper, String> styleableValueMap,
            Map<ResourceType, Map<String, Integer>> resourceValueMap) {
        mResourceValueMap = resourceValueMap;
        mResIdValueToNameMap = resIdValueToNameMap;
        mStyleableValueToNameMap = styleableValueMap;
        mergeIdResources();
    }

    @Override
    protected void postUpdate() {
        super.postUpdate();
        mergeIdResources();
    }

    /**
     * Merges the list of ID resource coming from R.java and the list of ID resources
     * coming from XML declaration into the cached list {@link #mIdResourceList}.
     */
    void mergeIdResources() {
        // get the current ID values
        List<ResourceItem> resources = mResourceMap.get(ResourceType.ID);

        // get the ID values coming from the R class.
        Map<String, Integer> rResources = mResourceValueMap.get(ResourceType.ID);

        if (rResources != null) {
            Map<String, Integer> copy;

            if (resources == null) {
                resources = new ArrayList<ResourceItem>(rResources.entrySet().size());
                mResourceMap.put(ResourceType.ID, resources);
                copy = rResources;
            } else {
                // make a copy of the compiled Resources.
                // As we loop on the full resources, we'll check with this copy map and remove
                // from it all the resources we find in the full list.
                // At the end, whatever is in the copy of the compile list is not in the full map,
                // and should be added as inlined resource items.
                copy = new HashMap<String, Integer>(rResources);

                for (int i = 0 ; i < resources.size(); ) {
                    ResourceItem item = resources.get(i);
                    String name = item.getName();
                    if (item.isDeclaredInline()) {
                        // This ID is declared inline in the full resource map.
                        // Check if it's also in the compiled version, in which case we can keep it.
                        // Otherwise, if it doesn't exist in the compiled map, remove it from the
                        // full map.
                        // Since we're going to remove it from the copy map either way, we can use
                        // remove to test if it's there
                        if (copy.remove(name) != null) {
                            // there is a match in the compiled list, do nothing, keep current one.
                            i++;
                        } else {
                            // the ID is now gone, remove it from the list
                            resources.remove(i);
                        }
                    } else {
                        // not an inline item, remove it from the copy.
                        copy.remove(name);
                        i++;
                    }
                }
            }

            // now add what's left in copy to the list
            for (String name : copy.keySet()) {
                resources.add(new InlineResourceItem(name));
            }
        }
    }
}
