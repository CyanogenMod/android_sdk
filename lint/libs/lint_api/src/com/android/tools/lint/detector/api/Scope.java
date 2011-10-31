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

package com.android.tools.lint.detector.api;

import java.util.EnumSet;

/**
 * The scope of a detector is the set of files a detector must consider when
 * performing its analysis. This can be used to determine when issues are
 * potentially obsolete, whether a detector should re-run on a file save, etc.
 */
public enum Scope {
    /**
     * The analysis only considers a single XML resource file at a time.
     * <p>
     * Issues which are only affected by a single resource file can be checked
     * for incrementally when a file is edited.
     */
    RESOURCE_FILE,

    /**
     * The analysis considers <b>all</b> the resource file. This scope must not
     * be used in conjunction with {@link #RESOURCE_FILE}; an issue scope is
     * either considering just a single resource file or all the resources, not
     * both.
     */
    ALL_RESOURCE_FILES,

    /**
     * The analysis only considers a single Java source file at a time.
     * <p>
     * Issues which are only affected by a single Java source file can be
     * checked for incrementally when a Java source file is edited.
     */
    JAVA_FILE,

    /**
     * The analysis considers <b>all</b> the Java source files together.
     * <p>
     * This flag is mutually exclusive with {@link #JAVA_FILE}.
     */
    ALL_JAVA_FILES,

    /**
     * The analysis only considers a single Java class file at a time.
     * <p>
     * Issues which are only affected by a single Java class file can be checked
     * for incrementally when a Java source file is edited and then recompiled.
     */
    CLASS_FILE,

    /** The analysis considers the manifest file */
    MANIFEST,

    /** The analysis considers the Proguard configuration file */
    PROGUARD,

    /**
     * The analysis considers classes in the libraries for this project.
     */
    JAVA_LIBRARIES;

    /** All scopes: running lint on a project will check these scopes */
    public static final EnumSet<Scope> ALL = EnumSet.allOf(Scope.class);
    /** Scope-set used for detectors which are affected by a single resource file */
    public static final EnumSet<Scope> RESOURCE_FILE_SCOPE = EnumSet.of(RESOURCE_FILE);
    /** Scope-set used for detectors which scan all resources */
    public static final EnumSet<Scope> ALL_RESOURCES_SCOPE = EnumSet.of(ALL_RESOURCE_FILES);
    /** Scope-set used for detectors which are affected by a single Java source file */
    public static final EnumSet<Scope> JAVA_FILE_SCOPE = EnumSet.of(RESOURCE_FILE);
}
