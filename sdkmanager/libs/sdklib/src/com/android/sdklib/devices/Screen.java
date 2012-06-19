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

    /**
     * Returns a copy of the object that shares no state with it,
     * but is initialized to equivalent values.
     *
     * @return A copy of the object.
     */
    public Screen deepCopy() {
        Screen s = new Screen();
        s.mScreenSize = mScreenSize;
        s.mDiagonalLength = mDiagonalLength;
        s.mPixelDensity = mPixelDensity;
        s.mScreenRatio = mScreenRatio;
        s.mXDimension = mXDimension;
        s.mYDimension = mYDimension;
        s.mXdpi = mXdpi;
        s.mYdpi = mYdpi;
        s.mMultitouch = mMultitouch;
        s.mMechanism = mMechanism;
        s.mScreenType = mScreenType;
        return s;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Screen)) {
            return false;
        }
        Screen s = (Screen) o;
        return s.mScreenSize == mScreenSize
                && s.mDiagonalLength == mDiagonalLength
                && s.mPixelDensity == mPixelDensity
                && s.mScreenRatio == mScreenRatio
                && s.mXDimension == mXDimension
                && s.mYDimension == mYDimension
                && s.mXdpi == mXdpi
                && s.mYdpi == mYdpi
                && s.mMultitouch == mMultitouch
                && s.mMechanism == mMechanism
                && s.mScreenType == mScreenType;
    }

    @Override
    public int hashCode() {
        int hash = 17;
        hash = 31 * hash + mScreenSize.hashCode();
        long f = Double.doubleToLongBits(mDiagonalLength);
        hash = 31 * hash + (int) (f ^ (f >>> 32));
        hash = 31 * hash + mPixelDensity.hashCode();
        hash = 31 * hash + mScreenRatio.hashCode();
        hash = 31 * hash + mXDimension;
        hash = 31 * hash + mYDimension;
        f = Double.doubleToLongBits(mXdpi);
        hash = 31 * hash + (int) (f ^ (f >>> 32));
        f = Double.doubleToLongBits(mYdpi);
        hash = 31 * hash + (int) (f ^ (f >>> 32));
        hash = 31 * hash + mMultitouch.hashCode();
        hash = 31 * hash + mMechanism.hashCode();
        hash = 31 * hash + mScreenType.hashCode();
        return hash;
    }
}
