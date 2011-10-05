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

/**
 * The scope of a detector is the set of files a detector must consider when
 * performing its analysis. This can be used to determine when issues are
 * potentially obsolete, whether a detector should re-run on a file save, etc.
 */
public enum Scope {
    /** The analysis only considers a single file at a time */
    SINGLE_FILE,

    /** The analysis considers more than one file but only resource files */
    RESOURCES,

    /** The analysis considers more than one file but only the Java code */
    JAVA_CODE,

    /**
     * The analysis considers both the Java code in the project and any
     * libraries
     */
    JAVA,

    /** The analysis considers the full project */
    PROJECT;

    /**
     * Returns true if this scope is within the given scope. For example a file
     * scope is within a project scope, but a project scope is not within a file
     * scope.
     *
     * @param scope the scope to compare with
     * @return true if this scope is within the other scope
     */
    public boolean within(Scope scope) {
        if (this == scope) {
            return true;
        }
        if (scope == PROJECT) {
            // Everything is within a project
            return true;
        }

        if (this == SINGLE_FILE) {
            // A single file is within everything else
            return true;
        }

        if (this == JAVA_CODE) {
            return scope == JAVA; // or scope == PROJECT but that's handled above
        }

        return false;
    }
}
