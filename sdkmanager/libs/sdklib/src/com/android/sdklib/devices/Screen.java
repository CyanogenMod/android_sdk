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

package com.android.sdklib.devices;

import com.android.resources.Density;
import com.android.resources.ScreenRatio;
import com.android.resources.ScreenSize;
import com.android.resources.TouchScreen;


public class Screen {
    ScreenSize mScreenSize;
    double mDiagonalLength;
    Density mPixelDensity;
    ScreenRatio mScreenRatio;
    int mXDimension;
    int mYDimension;
    double mXdpi;
    double mYdpi;
    Multitouch mMultitouch;
    TouchScreen mMechanism;
    ScreenType mScreenType;
    
    public ScreenSize getSize() {
        return mScreenSize;
    }
    public double getDiagonalLength() {
        return mDiagonalLength;
    }
    public Density getPixelDensity() {
        return mPixelDensity;
    }
    public ScreenRatio getRatio() {
        return mScreenRatio;
    }
    public int getXDimension() {
        return mXDimension;
    }
    
    public int getYDimension() {
        return mYDimension;
    }
    
    public double getXdpi() {
        return mXdpi;
    }
    public double getYdpi() {
        return mYdpi;
    }
    public Multitouch getMultitouch() {
        return mMultitouch;
    }
    public TouchScreen getMechanism() {
        return mMechanism;
    }
    public ScreenType getScreenType() {
        return mScreenType;
    }
}