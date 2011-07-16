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

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * A {@link GraphicGenerator} that generates Android "launcher" icons.
 */
public class LauncherIconGenerator extends GraphicGenerator {
    private static final Rectangle BASE_IMAGE_RECT = new Rectangle(0, 0, 48, 48);
    private static final Rectangle BASE_TARGET_RECT = new Rectangle(4, 4, 40, 40);

    private Options mOptions;
    private BufferedImage mBackImage;
    private BufferedImage mMaskImage;
    private BufferedImage mForeImage;

    public LauncherIconGenerator(GraphicGeneratorContext context, Options options) {
        mOptions = options;
        mBackImage = context.loadImageResource("/images/launcher_stencil/"
                + options.shape.id + "/" + options.density.getResourceValue() + "/back.png");
        mForeImage = context.loadImageResource("/images/launcher_stencil/"
                + options.shape.id + "/" + options.density.getResourceValue() + "/"
                + options.style.id + ".png");
        mMaskImage = context.loadImageResource("/images/launcher_stencil/"
                + options.shape.id + "/" + options.density.getResourceValue() + "/mask.png");
    }

    public BufferedImage generate() {
        final float scaleFactor = GraphicGenerator.getScaleFactor(mOptions.density);
        Rectangle imageRect = Util.scaleRectangle(BASE_IMAGE_RECT, scaleFactor);
        Rectangle targetRect = Util.scaleRectangle(BASE_TARGET_RECT, scaleFactor);

        BufferedImage outImage = Util.newArgbBufferedImage(imageRect.width, imageRect.height);
        Graphics2D g = (Graphics2D) outImage.getGraphics();
        g.drawImage(mBackImage, 0, 0, null);

        {
            BufferedImage tempImage = Util.newArgbBufferedImage(imageRect.width, imageRect.height);
            Graphics2D g2 = (Graphics2D) tempImage.getGraphics();
            g2.drawImage(mMaskImage, 0, 0, null);
            g2.setComposite(AlphaComposite.SrcAtop);
            g2.setPaint(new Color(mOptions.backgroundColor));
            g2.fillRect(0, 0, imageRect.width, imageRect.height);

            if (mOptions.crop) {
                Util.drawCenterCrop(g2, mOptions.sourceImage, targetRect);
            } else {
                Util.drawCenterInside(g2, mOptions.sourceImage, targetRect);
            }

            g.drawImage(tempImage, 0, 0, null);
        }

        g.drawImage(mForeImage, 0, 0, null);
        return outImage;
    }

    public static class Options {
        public BufferedImage sourceImage;
        public int backgroundColor = 0;
        public boolean crop = true;
        public Shape shape = Shape.SQUARE;
        public Style style = Style.SIMPLE;
        public Density density = Density.XHIGH;

        public static enum Shape {
            CIRCLE("circle"), SQUARE("square");

            public String id;

            Shape(String id) {
                this.id = id;
            }
        }

        public static enum Style {
            SIMPLE("fore1"), FANCY("fore2"), GLOSSY("fore3");

            public String id;

            Style(String id) {
                this.id = id;
            }
        }
    }
}
