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

import com.android.resources.KeyboardState;
import com.android.resources.NavigationState;
import com.android.resources.ScreenOrientation;

public class State {
    boolean mDefaultState;
    String mName;
    String mDescription;
    ScreenOrientation mOrientation;
    KeyboardState mKeyState;
    NavigationState mNavState;
    Hardware mHardwareOverride;

    public boolean isDefaultState() {
        return mDefaultState;
    }

    public String getName() {
        return mName;
    }

    public String getDescription() {
        return mDescription;
    }

    public ScreenOrientation getOrientation() {
        return mOrientation;
    }

    public KeyboardState getKeyState() {
        return mKeyState;
    }

    public NavigationState getNavState() {
        return mNavState;
    }

    public Hardware getHardware() {
        return mHardwareOverride;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof State)) {
            return false;
        }
        State s = (State) o;
        return mDefaultState == s.isDefaultState()
                && mName.equals(s.getName())
                && mDescription.equals(s.getDescription())
                && mOrientation.equals(s.getOrientation())
                && mKeyState.equals(s.getKeyState())
                && mNavState.equals(s.getNavState())
                && mHardwareOverride.equals(s.getHardware());
    }

    @Override
    public int hashCode() {
        int hash = 17;
        hash = 31 * hash + (mDefaultState ? 1 : 0);
        hash = 31 * hash + mName.hashCode();
        hash = 31 * hash + mDescription.hashCode();
        hash = 31 * hash + mOrientation.hashCode();
        hash = 31 * hash + mKeyState.hashCode();
        hash = 31 * hash + mNavState.hashCode();
        hash = 31 * hash + mHardwareOverride.hashCode();
        return hash;
    }
}
