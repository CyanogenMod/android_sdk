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

import static com.android.ide.eclipse.adt.internal.wizards.templates.NewProjectWizard.ATTR_MIN_API;
import static com.android.ide.eclipse.adt.internal.wizards.templates.NewProjectWizard.ATTR_MIN_API_LEVEL;
import static com.android.ide.eclipse.adt.internal.wizards.templates.NewProjectWizard.ATTR_PACKAGE_NAME;
import static com.android.ide.eclipse.adt.internal.wizards.templates.NewProjectWizard.ATTR_TARGET_API;
import static com.android.ide.eclipse.adt.internal.wizards.templates.NewTemplateWizard.BLANK_ACTIVITY;

import com.android.annotations.NonNull;
import com.android.ide.eclipse.adt.internal.editors.manifest.ManifestInfo;

import org.eclipse.core.resources.IProject;
import org.eclipse.ltk.core.refactoring.Change;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Value object which holds the current state of the wizard pages for the
 * {@link NewTemplateWizard}
 */
public class NewTemplateWizardState {
    /** Template handler responsible for instantiating templates and reading resources */
    private TemplateHandler mTemplateHandler;

    /** Configured parameters, by id */
    public final Map<String, Object> parameters = new HashMap<String, Object>();

    /** Configured defaults for the parameters, by id */
    public final Map<String, String> defaults = new HashMap<String, String>();

    /** Ids for parameters which should be hidden (because the client wizard already
     * has information for these parameters) */
    public final Set<String> hidden = new HashSet<String>();

    /**
     * The chosen project (which may be null if the wizard page is being
     * embedded in the new project wizard)
     */
    public IProject project;

    /** The minimum API level to use for this template */
    public int minSdkLevel;

    /** The build API level to use for this template */
// TODO: Populate
    public int buildApiLevel;

    /** Location of the template being created */
    private File mTemplateLocation;

    /**
     * Create a new state object for use by the {@link NewTemplatePage}
     */
    public NewTemplateWizardState() {
    }

    @NonNull
    TemplateHandler getTemplateHandler() {
        if (mTemplateHandler == null) {
            File inputPath;
            if (mTemplateLocation != null) {
                inputPath = mTemplateLocation;
            } else {
                // Default
                inputPath = TemplateManager.getTemplateLocation(BLANK_ACTIVITY);
            }
            mTemplateHandler = TemplateHandler.createFromPath(inputPath);
        }

        return mTemplateHandler;
    }

    /** Sets the current template */
    void setTemplateLocation(File file) {
        if (!file.equals(mTemplateLocation)) {
            mTemplateLocation = file;
            mTemplateHandler = null;
        }
    }

    /** Returns the current template */
    File getTemplateLocation() {
        return mTemplateLocation;
    }

    /** Returns the min SDK version to use */
    int getMinSdk() {
        if (project == null) {
            return -1;
        }
        ManifestInfo manifest = ManifestInfo.get(project);
        return manifest.getMinSdkVersion();
    }

    /** Returns the min SDK version to use */
    int getBuildApi() {
        if (project == null) {
            return -1;
        }
        ManifestInfo manifest = ManifestInfo.get(project);
        return manifest.getMinSdkVersion();
    }

    /** Computes the changes this wizard will make */
    @NonNull
    List<Change> computeChanges() {
        if (project == null) {
            return Collections.emptyList();
        }

        ManifestInfo manifest = ManifestInfo.get(project);
        parameters.put(ATTR_PACKAGE_NAME, manifest.getPackage());
        parameters.put(ATTR_MIN_API, manifest.getMinSdkVersion());
        parameters.put(ATTR_MIN_API_LEVEL, manifest.getMinSdkName());
        parameters.put(ATTR_TARGET_API, manifest.getTargetSdkVersion());
        parameters.put(NewProjectWizard.ATTR_BUILD_API, getBuildApi());

        return getTemplateHandler().render(project, parameters);
    }
}
