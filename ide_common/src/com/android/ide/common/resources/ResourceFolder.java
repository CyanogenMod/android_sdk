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

import com.android.annotations.VisibleForTesting;
import com.android.annotations.VisibleForTesting.Visibility;
import com.android.ide.common.resources.configuration.Configurable;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.io.IAbstractFile;
import com.android.io.IAbstractFolder;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Resource Folder class. Contains list of {@link ResourceFile}s,
 * the {@link FolderConfiguration}, and a link to the {@link IAbstractFolder} object.
 */
public final class ResourceFolder implements Configurable {
    final ResourceFolderType mType;
    final FolderConfiguration mConfiguration;
    IAbstractFolder mFolder;
    ArrayList<ResourceFile> mFiles = null;
    private final ResourceRepository mRepository;


    /**
     * Creates a new {@link ResourceFolder}
     * @param type The type of the folder
     * @param config The configuration of the folder
     * @param folder The associated {@link IAbstractFolder} object.
     * @param repository The associated {@link ResourceRepository}
     */
    protected ResourceFolder(ResourceFolderType type, FolderConfiguration config,
            IAbstractFolder folder, ResourceRepository repository) {
        mType = type;
        mConfiguration = config;
        mFolder = folder;
        mRepository = repository;
    }

    /**
     * Processes a file and adds it to its parent folder resource.
     *
     * @param file the underlying resource file.
     * @param kind the file change kind.
     * @param context a context object with state for the current update, such
     *            as a place to stash errors encountered
     * @return the {@link ResourceFile} that was created.
     */
    public ResourceFile processFile(IAbstractFile file, ResourceDeltaKind kind,
            ScanningContext context) {
        // look for this file if it's already been created
        ResourceFile resFile = getFile(file);

        if (resFile == null) {
            if (kind != ResourceDeltaKind.REMOVED) {
                // create a ResourceFile for it.

                // check if that's a single or multi resource type folder. For now we define this by
                // the number of possible resource type output by files in the folder.
                // We have a special case for layout/menu folders which can also generate IDs.
                // This does
                // not make the difference between several resource types from a single file or
                // the ability to have 2 files in the same folder generating 2 different types of
                // resource. The former is handled by MultiResourceFile properly while we don't
                // handle the latter. If we were to add this behavior we'd have to change this call.
                List<ResourceType> types = FolderTypeRelationship.getRelatedResourceTypes(mType);

                if (types.size() == 1) {
                    resFile = new SingleResourceFile(file, this);
                } else if (types.contains(ResourceType.LAYOUT)) {
                    resFile = new IdGeneratingResourceFile(file, this, ResourceType.LAYOUT);
                } else if (types.contains(ResourceType.MENU)) {
                    resFile = new IdGeneratingResourceFile(file, this, ResourceType.MENU);
                } else {
                    resFile = new MultiResourceFile(file, this);
                }

                resFile.load(context);

                // add it to the folder
                addFile(resFile);
            }
        } else {
            if (kind == ResourceDeltaKind.REMOVED) {
                removeFile(resFile, context);
            } else {
                resFile.update(context);
            }
        }

        return resFile;
    }


    /**
     * Adds a {@link ResourceFile} to the folder.
     * @param file The {@link ResourceFile}.
     */
    @VisibleForTesting(visibility=Visibility.PROTECTED)
    public void addFile(ResourceFile file) {
        if (mFiles == null) {
            mFiles = new ArrayList<ResourceFile>();
        }

        mFiles.add(file);
    }

    protected void removeFile(ResourceFile file, ScanningContext context) {
        file.dispose(context);
        mFiles.remove(file);
    }

    protected void dispose(ScanningContext context) {
        if (mFiles != null) {
            for (ResourceFile file : mFiles) {
                file.dispose(context);
            }

            mFiles.clear();
        }
    }

    /**
     * Returns the {@link IAbstractFolder} associated with this object.
     */
    public IAbstractFolder getFolder() {
        return mFolder;
    }

    /**
     * Returns the {@link ResourceFolderType} of this object.
     */
    public ResourceFolderType getType() {
        return mType;
    }

    public ResourceRepository getRepository() {
        return mRepository;
    }

    /**
     * Returns the list of {@link ResourceType}s generated by the files inside this folder.
     */
    public Collection<ResourceType> getResourceTypes() {
        ArrayList<ResourceType> list = new ArrayList<ResourceType>();

        if (mFiles != null) {
            for (ResourceFile file : mFiles) {
                Collection<ResourceType> types = file.getResourceTypes();

                // loop through those and add them to the main list,
                // if they are not already present
                for (ResourceType resType : types) {
                    if (list.indexOf(resType) == -1) {
                        list.add(resType);
                    }
                }
            }
        }

        return list;
    }

    public FolderConfiguration getConfiguration() {
        return mConfiguration;
    }

    /**
     * Returns whether the folder contains a file with the given name.
     * @param name the name of the file.
     */
    public boolean hasFile(String name) {
        return mFolder.hasFile(name);
    }

    /**
     * Returns the {@link ResourceFile} matching a {@link IAbstractFile} object.
     * @param file The {@link IAbstractFile} object.
     * @return the {@link ResourceFile} or null if no match was found.
     */
    private ResourceFile getFile(IAbstractFile file) {
        if (mFiles != null) {
            for (ResourceFile f : mFiles) {
                if (f.getFile().equals(file)) {
                    return f;
                }
            }
        }
        return null;
    }

    /**
     * Returns the {@link ResourceFile} matching a given name.
     * @param filename The name of the file to return.
     * @return the {@link ResourceFile} or <code>null</code> if no match was found.
     */
    public ResourceFile getFile(String filename) {
        if (mFiles != null) {
            for (ResourceFile f : mFiles) {
                if (f.getFile().getName().equals(filename)) {
                    return f;
                }
            }
        }
        return null;
    }

    /**
     * Returns whether a file in the folder is generating a resource of a specified type.
     * @param type The {@link ResourceType} being looked up.
     */
    public boolean hasResources(ResourceType type) {
        // Check if the folder type is able to generate resource of the type that was asked.
        // this is a first check to avoid going through the files.
        List<ResourceFolderType> folderTypes = FolderTypeRelationship.getRelatedFolders(type);

        boolean valid = false;
        for (ResourceFolderType rft : folderTypes) {
            if (rft == mType) {
                valid = true;
                break;
            }
        }

        if (valid) {
            if (mFiles != null) {
                for (ResourceFile f : mFiles) {
                    if (f.hasResources(type)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return mFolder.toString();
    }
}
