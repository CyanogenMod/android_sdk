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

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * A {@link GraphicGenerator} that generates Android "menu" icons.
 */
public class MenuIconGenerator extends GraphicGenerator {
    private static final Rectangle BASE_IMAGE_RECT = new Rectangle(0, 0, 48, 48);
    private static final Rectangle BASE_TARGET_RECT = new Rectangle(8, 8, 32, 32);

    private Options mOptions;

    public MenuIconGenerator(GraphicGeneratorContext context, Options options) {
        mOptions = options;
    }

    public BufferedImage generate() {
        final float scaleFactor = GraphicGenerator.getScaleFactor(mOptions.density);
        Rectangle imageRect = Util.scaleRectangle(BASE_IMAGE_RECT, scaleFactor);
        Rectangle targetRect = Util.scaleRectangle(BASE_TARGET_RECT, scaleFactor);

        BufferedImage outImage = Util.newArgbBufferedImage(imageRect.width, imageRect.height);
        Graphics2D g = (Graphics2D) outImage.getGraphics();

        {
            BufferedImage tempImage = Util.newArgbBufferedImage(
                    imageRect.width, imageRect.height);
            Graphics2D g2 = (Graphics2D) tempImage.getGraphics();
            Util.drawCenterInside(g2, mOptions.sourceImage, targetRect);

            Util.drawEffects(g, tempImage, 0, 0, new Util.Effect[]{
                    new Util.FillEffect(
                            new GradientPaint(
                                    0, 0,
                                    new Color(0xa3a3a3),
                                    0, imageRect.height,
                                    new Color(0x787878))),
                    new Util.ShadowEffect(
                            0,
                            3 * scaleFactor,
                            3 * scaleFactor,
                            Color.black,
                            0.2,
                            true),
                    new Util.ShadowEffect(
                            0,
                            1,
                            0,
                            Color.black,
                            0.35,
                            true),
                    new Util.ShadowEffect(
                            0,
                            -1,
                            0,
                            Color.white,
                            0.35,
                            true),
            });
        }

        return outImage;
    }

    public static class Options {
        public BufferedImage sourceImage;
        public Density density = Density.XHIGH;
    }
}
