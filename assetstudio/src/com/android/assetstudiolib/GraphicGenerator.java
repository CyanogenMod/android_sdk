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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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

    /**
     * Returns the icon (32x32) for a given clip art image.
     *
     * @param name the name of the image to be loaded (which can be looked up via
     *            {@link #getClipartNames()})
     * @return the icon image
     * @throws IOException if the image cannot be loaded
     */
    public static BufferedImage getClipartIcon(String name) throws IOException {
        InputStream is = GraphicGenerator.class.getResourceAsStream(
                "/images/clipart/small/" + name);
        return ImageIO.read(is);
    }

    /**
     * Returns the full size clip art image for a given image name.
     *
     * @param name the name of the image to be loaded (which can be looked up via
     *            {@link #getClipartNames()})
     * @return the clip art image
     * @throws IOException if the image cannot be loaded
     */
    public static BufferedImage getClipartImage(String name) throws IOException {
        InputStream is = GraphicGenerator.class.getResourceAsStream(
                "/images/clipart/big/" + name);
        return ImageIO.read(is);
    }

    /**
     * Returns the names of available clip art images which can be obtained by passing the
     * name to {@link #getClipartIcon(String)} or
     * {@link GraphicGenerator#getClipartImage(String)}
     *
     * @return an iterator for the available image names
     */
    public static Iterator<String> getClipartNames() {
        List<String> names = new ArrayList<String>(80);
        try {
            String pathPrefix = "images/clipart/big/"; //$NON-NLS-1$
            ProtectionDomain protectionDomain = GraphicGenerator.class.getProtectionDomain();
            URL url = protectionDomain.getCodeSource().getLocation();
            File file;
            try {
                file = new File(url.toURI());
            } catch (URISyntaxException e) {
                file = new File(url.getPath());
            }
            final ZipFile zipFile = new JarFile(file);
            Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry zipEntry = enumeration.nextElement();
                String name = zipEntry.getName();
                if (!name.startsWith(pathPrefix) || !name.endsWith(".png")) { //$NON-NLS-1$
                    continue;
                }

                int lastSlash = name.lastIndexOf('/');
                if (lastSlash != -1) {
                    name = name.substring(lastSlash + 1);
                }
                names.add(name);
            }
        } catch (final Exception e) {
            e.printStackTrace();
        }

        return names.iterator();
    }
}
