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

import com.android.resources.Density;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

/**
 * The base Generator class.
 */
public class GraphicGenerator {
    /**
     * Options used for all generators.
     */
    public static class Options {
    }

    public static float getScaleFactor(Density density) {
        return density.getDpiValue() / (float) Density.DEFAULT_DENSITY;
    }

    /**
     * Returns one of the built in stencil images, or null
     *
     * @param relativePath stencil path such as "launcher-stencil/square/web/back.png"
     * @return the image, or null
     * @throws IOException if an unexpected I/O error occurs
     */
    public static BufferedImage getStencilImage(String relativePath) throws IOException {
        InputStream is = GraphicGenerator.class.getResourceAsStream(relativePath);
        return ImageIO.read(is);
    }
}
