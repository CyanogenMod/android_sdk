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

package com.android.assetstudiolib;

/**
 * The base Generator class.
 */
public class GraphicGenerator {
    /**
     * Options used for all generators.
     */
    public static class Options {
        /**
         * The screen density of the icon.
         */
        public static enum Density {
            LDPI("ldpi", 120), MDPI("mdpi", 160), HDPI("hdpi", 240), XHDPI("xhdpi", 320);

            public String id;
            public int dpi;

            Density(String id, int dpi) {
                this.id = id;
                this.dpi = dpi;
            }

            public float scaleFactor() {
                return (float) this.dpi / 160;
            }
        }
    }
}
