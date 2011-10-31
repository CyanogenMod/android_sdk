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

package com.android.tools.lint.checks;

/**
 * Constants used by the various detectors, defined in one place
 */
public class LintConstants {
    // Tags
    public static final String TAG_SERVICE = "service";                //$NON-NLS-1$
    public static final String TAG_USES_PERMISSION = "uses-permission";//$NON-NLS-1$
    public static final String TAG_APPLICATION = "application";        //$NON-NLS-1$
    public static final String TAG_INTENT_FILTER = " intent-filter";   //$NON-NLS-1$

    // Attributes
    public static final String ATTR_EXPORTED = "exported";             //$NON-NLS-1$
    public static final String ATTR_PERMISSION = "permission";         //$NON-NLS-1$

    // Filenames
    public static final String ANDROID_MANIFEST_XML = "AndroidManifest.xml"; //$NON-NLS-1$

}
