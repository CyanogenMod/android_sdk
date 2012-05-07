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

import com.android.ide.eclipse.adt.internal.assetstudio.CreateAssetSetWizardState;
import com.android.sdklib.IAndroidTarget;

import java.io.File;

/**
 * Value object which holds the current state of the wizard pages for the
 * {@link NewProjectWizard}
 */
public class NewProjectWizardState {
    private static final String TEMPLATE_NAME = "NewAndroidApplication"; //$NON-NLS-1$

    /** Creates a new {@link NewProjectWizardState} */
    public NewProjectWizardState() {
        File inputPath = new File(TemplateHandler.getTemplatePath(TEMPLATE_NAME));
        template = TemplateHandler.createFromPath(inputPath);
    }

    /** The template handler instantiating the project */
    public final TemplateHandler template;

    /** The name of the project */
    public String projectName;

    /** The derived name of the activity, if any */
    public String activityName;

    /** The derived title of the activity, if any */
    public String activityTitle;

    /** The application name */
    public String applicationName;

    /** The package name */
    public String packageName;

    /** Whether the project name has been edited by the user */
    public boolean projectModified;

    /** Whether the package name has been edited by the user */
    public boolean packageModified;

    /** Whether the activity name has been edited by the user */
    public boolean activityNameModified;

    /** Whether the activity title has been edited by the user */
    public boolean activityTitleModified;

    /** Whether the application name has been edited by the user */
    public boolean applicationModified;

    /** The compilation target to use for this project */
    public IAndroidTarget target;

    /** The minimum SDK API level to use */
    public String minSdk;

    /** The minimum API level, as a string (if the API is a preview release with a codename) */
    public int minSdkLevel;

    /** Whether to create an application skeleton */
    public boolean createAppSkeleton = true;

    /** Whether to create an activity (if so, the activity state is stored in
     * {@link #activityValues}) */
    public boolean createActivity = true;

    /** Whether to target phones */
    public boolean phone = true;

    /** Whether to target tablets */
    public boolean tablet = true;

    /** Whether a custom icon should be created instead of just reusing the default (if so,
     * the icon wizard state is stored in {@link #iconState}) */
    public boolean createIcon = true;

    // Delegated wizards

    /** State for the asset studio wizard, used to create custom icons */
    public CreateAssetSetWizardState iconState = new CreateAssetSetWizardState();

    /** State for the template wizard, used to embed an activity template */
    public NewTemplateWizardState activityValues = new NewTemplateWizardState();
}
