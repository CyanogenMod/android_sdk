/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.assetstudiolib;

import com.android.assetstudiolib.Util.Effect;
import com.android.assetstudiolib.Util.FillEffect;
import com.android.assetstudiolib.Util.ShadowEffect;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * Generate icons for the action bar
 */
public class ActionBarIconGenerator extends GraphicGenerator {

    /** Creates a new {@link ActionBarIconGenerator} */
    public ActionBarIconGenerator() {
    }

    @Override
    public BufferedImage generate(GraphicGeneratorContext context, Options options) {
        Rectangle iconSizeHdpi = new Rectangle(0, 0, 48, 48);
        Rectangle targetRectHdpi = new Rectangle(6, 6, 36, 36);
        final float scaleFactor = GraphicGenerator.getHdpiScaleFactor(options.density);
        Rectangle imageRect = Util.scaleRectangle(iconSizeHdpi, scaleFactor);
        Rectangle targetRect = Util.scaleRectangle(targetRectHdpi, scaleFactor);
        BufferedImage outImage = Util.newArgbBufferedImage(imageRect.width, imageRect.height);
        Graphics2D g = (Graphics2D) outImage.getGraphics();

        BufferedImage tempImage = Util.newArgbBufferedImage(
                imageRect.width, imageRect.height);
        Graphics2D g2 = (Graphics2D) tempImage.getGraphics();
        Util.drawCenterInside(g2, options.sourceImage, targetRect);

        ActionBarOptions actionBarOptions = (ActionBarOptions) options;
        if (actionBarOptions.theme == Theme.HOLO_LIGHT) {
            Util.drawEffects(g, tempImage, 0, 0, new Effect[] {
                    new FillEffect(new Color(0x898989)),
            });
        } else {
            assert actionBarOptions.theme == Theme.HOLO_DARK;
            Util.drawEffects(g, tempImage, 0, 0, new Effect[] {
                    // TODO: should be white @ 60% opacity, but
                    // the fill then blends with the drop shadow
                    new FillEffect(new Color(0x909090)),
                    new ShadowEffect(
                            0,
                            0,
                            3 * scaleFactor,
                            Color.BLACK,
                            0.85,
                            false),
            });
        }

        g.dispose();
        g2.dispose();

        return outImage;
    }

    /** Options specific to generating action bar icons */
    public static class ActionBarOptions extends GraphicGenerator.Options {
        /** The theme to generate icons for */
        public Theme theme = Theme.HOLO_LIGHT;
    }

    /** The themes to generate action bar icons for */
    public enum Theme {
        /** Theme.Holo - a dark (and default) version of the Honeycomb theme */
        HOLO_DARK,

        /** Theme.HoloLight - a light version of the Honeycomb theme */
        HOLO_LIGHT;
    }
}
