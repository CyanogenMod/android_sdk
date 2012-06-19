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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.android.resources.Keyboard;
import com.android.resources.Navigation;
import com.android.resources.UiMode;

public class Hardware {
    Screen mScreen;
    Set<Network> mNetworking;
    Set<Sensor> mSensors;
    boolean mMic;
    List<Camera> mCameras;
    Keyboard mKeyboard;
    Navigation mNav;
    Storage mRam;
    ButtonType mButtons;
    List<Storage> mInternalStorage;
    List<Storage> mRemovableStorage;
    String mCpu;
    String mGpu;
    Set<Abi> mAbis;
    Set<UiMode> mUiModes;
    PowerType mPluggedIn;

    public Set<Network> getNetworking() {
        return mNetworking;
    }

    public Set<Sensor> getSensors() {
        return mSensors;
    }

    public boolean hasMic() {
        return mMic;
    }

    public List<Camera> getCameras() {
        return mCameras;
    }

    public Camera getCamera(int i) {
        return mCameras.get(i);
    }

    public Camera getCamera(CameraLocation location) {
        for (Camera c : mCameras) {
            if (location.equals(c.mLocation)) {
                return c;
            }
        }
        return null;
    }

    public Keyboard getKeyboard() {
        return mKeyboard;
    }

    public Navigation getNav() {
        return mNav;
    }

    public Storage getRam() {
        return mRam;
    }

    public ButtonType getButtonType() {
        return mButtons;
    }

    public List<Storage> getInternalStorage() {
        return mInternalStorage;
    }

    public List<Storage> getRemovableStorage() {
        return mRemovableStorage;
    }

    public String getCpu() {
        return mCpu;
    }

    public String getGpu() {
        return mGpu;
    }

    public Set<Abi> getSupportedAbis() {
        return mAbis;
    }

    public Set<UiMode> getSupportedUiModes() {
        return mUiModes;
    }

    public PowerType getChargeType() {
        return mPluggedIn;
    }

    public Screen getScreen() {
        return mScreen;
    }

    /**
     * Returns a copy of the object that shares no state with it,
     * but is initialized to equivalent values.
     *
     * @return A copy of the object.
     */
    public Hardware deepCopy() {
        Hardware hw = new Hardware();
        hw.mScreen = mScreen.deepCopy();
        hw.mNetworking = new HashSet<Network>(mNetworking);
        hw.mSensors = new HashSet<Sensor>(mSensors);
        // Get the constant boolean value
        hw.mMic = mMic;
        hw.mCameras = new ArrayList<Camera>();
        for (Camera c : mCameras) {
            hw.mCameras.add(c.deepCopy());
        }
        hw.mKeyboard = mKeyboard;
        hw.mNav = mNav;
        hw.mRam = mRam.deepCopy();
        hw.mButtons = mButtons;
        hw.mInternalStorage = new ArrayList<Storage>();
        for (Storage s : mInternalStorage) {
            hw.mInternalStorage.add(s.deepCopy());
        }
        hw.mRemovableStorage = new ArrayList<Storage>();
        for (Storage s : mRemovableStorage) {
            hw.mRemovableStorage.add(s.deepCopy());
        }
        hw.mCpu = mCpu;
        hw.mGpu = mGpu;
        hw.mAbis = new HashSet<Abi>(mAbis);
        hw.mUiModes = new HashSet<UiMode>(mUiModes);
        hw.mPluggedIn = mPluggedIn;
        return hw;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Hardware)) {
            return false;
        }
        Hardware hw = (Hardware) o;
        return mScreen.equals(hw.getScreen())
                && mNetworking.equals(hw.getNetworking())
                && mSensors.equals(hw.getSensors())
                && mMic == hw.hasMic()
                && mCameras.equals(hw.getCameras())
                && mKeyboard == hw.getKeyboard()
                && mNav == hw.getNav()
                && mRam.equals(hw.getRam())
                && mButtons == hw.getButtonType()
                && mInternalStorage.equals(hw.getInternalStorage())
                && mRemovableStorage.equals(hw.getRemovableStorage())
                && mCpu.equals(hw.getCpu())
                && mGpu.equals(hw.getGpu())
                && mAbis.equals(hw.getSupportedAbis())
                && mUiModes.equals(hw.getSupportedUiModes())
                && mPluggedIn == hw.getChargeType();

    }

    @Override
    public int hashCode() {
        int hash = 17;
        hash = 31 * hash + mScreen.hashCode();
        hash = 31 * hash + mNetworking.hashCode();
        hash = 31 * hash + mSensors.hashCode();
        hash = 31 * hash + (mMic ? 1 : 0);
        hash = 31 * hash + mCameras.hashCode();
        hash = 31 * hash + mKeyboard.hashCode();
        hash = 31 * hash + mNav.hashCode();
        hash = 31 * hash + mRam.hashCode();
        hash = 31 * hash + mButtons.hashCode();
        hash = 31 * hash + mInternalStorage.hashCode();
        hash = 31 * hash + mRemovableStorage.hashCode();
        hash = 31 * hash + mCpu.hashCode();
        hash = 31 * hash + mGpu.hashCode();
        hash = 31 * hash + mAbis.hashCode();
        hash = 31 * hash + mUiModes.hashCode();
        hash = 31 * hash + mPluggedIn.hashCode();
        return hash;
    }
}
