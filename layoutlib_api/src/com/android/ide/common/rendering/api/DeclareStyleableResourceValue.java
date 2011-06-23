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

package com.android.ide.common.rendering.api;

import com.android.resources.ResourceType;

import java.util.HashMap;
import java.util.Map;

/**
 * A Resource value representing a declare-styleable resource.
 *
 * {@link #getValue()} will return null, instead use {@link #getAttributeValues(String)} to
 * get the enum/flag value associated with an attribute defined in the declare-styleable.
 *
 */
public class DeclareStyleableResourceValue extends ResourceValue {

    private Map<String, Map<String, Integer>> mEnumMap;

    public DeclareStyleableResourceValue(ResourceType type, String name, boolean isFramework) {
        super(type, name, isFramework);

    }

    /**
     * Return the enum/flag integer value for a given attribute.
     * @param name the name of the attribute
     * @return the map of (name, integer) values.
     */
    public Map<String, Integer> getAttributeValues(String name) {
        if (mEnumMap != null) {
            return mEnumMap.get(name);
        }

        return null;
    }

    public Map<String, Map<String, Integer>> getAllAttributes() {
        return mEnumMap;
    }

    public void addValue(String attribute, String name, Integer value) {
        Map<String, Integer> map;

        if (mEnumMap == null) {
            mEnumMap = new HashMap<String, Map<String,Integer>>();

            map = new HashMap<String, Integer>();
            mEnumMap.put(attribute, map);
        } else {
            map = mEnumMap.get(attribute);
            if (map == null) {
                map = new HashMap<String, Integer>();
                mEnumMap.put(attribute, map);
            }
        }

        map.put(name, value);
    }
}
