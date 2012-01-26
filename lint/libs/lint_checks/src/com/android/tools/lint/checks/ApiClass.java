/*
 * Copyright (C) 2012 The Android Open Source Project
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
 * Represents a class and its methods/fields.
 *
 * {@link #getSince()} gives the API level it was introduced.
 *
 * {@link #getMethod(String)} returns when the method was introduced.
 * {@link #getField(String)} returns when the field was introduced.
 *
 *
 */
public interface ApiClass {

    /**
     * Returns the name of the class.
     * @return the name of the class
     */
    String getName();

    /**
     * Returns when the class was introduced.
     * @return the api level the class was introduced.
     */
    int getSince();

    /**
     * Returns when a field was added, or null if it doesn't exist.
     * @param name the name of the field.
     * @return
     */
    Integer getField(String name, Api info);

    /**
     * Returns when a method was added, or null if it doesn't exist. This goes through the super
     * class to find method only present there.
     * @param methodSignature the method signature
     * @return
     */
    int getMethod(String methodSignature, Api info);
}
