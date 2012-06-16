/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.ide.eclipse.adt.internal.assetstudio;

import com.android.assetstudiolib.GraphicGenerator.Shape;

import org.eclipse.core.resources.IProject;
import org.eclipse.swt.graphics.RGB;

import java.io.File;

/**
 * Value object for the AssetStudio wizard. These values are both set by the
 * wizard as well as read by the wizard initially, so passing in a configured
 * {@link CreateAssetSetWizardState} to the icon generator is possible.
 */
public class CreateAssetSetWizardState {
    /**
     * The type of asset being created. This field is static such that when you
     * bring up the wizard repeatedly (for example to create multiple
     * notification icons) you don't have to keep selecting the same type over
     * and over.
     */
    public static AssetType sLastType = AssetType.LAUNCHER;

    /** The type of asset to be created */
    public AssetType type = sLastType;

    /** The base name to use for the created icons */
    public String outputName;

    /** The minimum SDK being targeted */
    public int minSdk = -1;

    /** The project to create the icons into */
    public IProject project;

    /** Whether empty space around the source image should be trimmed */
    public boolean trim;

    /** The type of source the icon is being created from */
    public SourceType sourceType = SourceType.TEXT;

    /** If {@link #sourceType} is a {@link SourceType#CLIPART}, the name of the clipart image */
    public String clipartName;

    /** If {@link #sourceType} is a {@link SourceType#IMAGE}, the path to the input image */
    public File imagePath;

    /** If {@link #sourceType} is a {@link SourceType#TEXT}, the text to render */
    public String text = "aA";

    /** The amount of padding to add around the source image */
    public int padding = 15;

    /** The background shape */
    public Shape shape = Shape.SQUARE;

    /** Whether the image should be cropped */
    public boolean crop;

    /** The background color to use for the shape (unless the shape is {@link Shape#NONE} */
    public RGB background = new RGB(0xff, 0x00, 0x00);

    /** The background color to use for the text or clipart (unless shape is {@link Shape#NONE} */
    public RGB foreground = new RGB(0x00, 0x00, 0x00);

    /** Types of sources that the asset studio can use to generate icons from */
    public enum SourceType {
        /** Generate the icon using the image pointed to by {@link #imagePath} */
        IMAGE,

        /** Generate the icon using the clipart named by {@link #clipartName} */
        CLIPART,

        /** Generate the icon using the text in {@link #text} */
        TEXT
    }
}
