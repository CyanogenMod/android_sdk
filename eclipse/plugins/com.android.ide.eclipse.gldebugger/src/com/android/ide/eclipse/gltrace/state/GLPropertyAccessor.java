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

package com.android.ide.eclipse.gltrace.state;

import java.util.ArrayList;
import java.util.List;

/**
 * GLPropertyAccessor's can be used to extract a certain property from the provided
 * OpenGL State hierarchy.
 */
public class GLPropertyAccessor {
    private List<GLPropertyExtractor> mExtractors;

    private GLPropertyAccessor(List<GLPropertyExtractor> extractors) {
        mExtractors = extractors;
    }

    public IGLProperty getProperty(IGLProperty root) {
        for (GLPropertyExtractor e : mExtractors) {
            IGLProperty successor = e.getProperty(root);
            if (successor == null) {
                return null;
            }
            root = successor;
        }

        return root;
    }

    /**
     * Factory method used to construct a {@link GLPropertyAccessor}.
     * @param accessors list of accessor's to be used to navigate the property hierarchy. The
     *                  accessors are either Integers or {@link GLStateType} objects. Integers
     *                  are assumed to be indexes in a {@link GLListProperty}, and the
     *                  GLStateType enum objects are used to query {@link GLCompositeProperty}'s.
     */
    public static GLPropertyAccessor makeAccessor(Object...accessors) {
        List<GLPropertyExtractor> extractors = new ArrayList<GLPropertyExtractor>();

        for (Object accessor : accessors) {
            if (accessor instanceof GLStateType) {
                extractors.add(new GLCompositePropertyExtractor((GLStateType) accessor));
            } else if (accessor instanceof Integer) {
                extractors.add(new GLListPropertyExtractor((Integer) accessor));
            } else {
                throw new IllegalArgumentException("Unknown property (" + accessor
                        + ") used to access members of IGLProperty");
            }
        }

        return new GLPropertyAccessor(extractors);
    }

    private interface GLPropertyExtractor {
        IGLProperty getProperty(IGLProperty p);
    }

    private static class GLCompositePropertyExtractor implements GLPropertyExtractor {
        private final GLStateType mType;

        public GLCompositePropertyExtractor(GLStateType type) {
            mType = type;
        }

        public IGLProperty getProperty(IGLProperty p) {
            if (p instanceof GLCompositeProperty) {
                return ((GLCompositeProperty) p).getProperty(mType);
            }

            return null;
        }
    }

    private static class GLListPropertyExtractor implements GLPropertyExtractor {
        private final int mIndex;

        public GLListPropertyExtractor(int index) {
            mIndex = index;
        }

        public IGLProperty getProperty(IGLProperty p) {
            if (p instanceof GLListProperty) {
                return ((GLListProperty) p).get(mIndex);
            }
            return null;
        }
    }
}
