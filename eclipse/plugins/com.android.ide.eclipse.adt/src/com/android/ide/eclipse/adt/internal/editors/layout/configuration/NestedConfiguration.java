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
package com.android.ide.eclipse.adt.internal.editors.layout.configuration;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.NightMode;
import com.android.resources.UiMode;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;

/**
 * An {@linkplain NestedConfiguration} is a {@link Configuration} which inherits
 * all of its values from a different configuration, except for one or more
 * attributes where it overrides a custom value.
 * <p>
 * Unlike a {@link ComplementingConfiguration}, a {@linkplain NestedConfiguration}
 * will always return the same overridden value, regardless of the inherited
 * value.
 * <p>
 * For example, an {@linkplain NestedConfiguration} may fix the locale to always
 * be "en", but otherwise inherit everything else.
 */
public class NestedConfiguration extends Configuration {
    protected final Configuration mParent;
    protected boolean mOverrideLocale;
    protected boolean mOverrideTarget;
    protected boolean mOverrideDevice;
    protected boolean mOverrideDeviceState;
    protected boolean mOverrideNightMode;
    protected boolean mOverrideUiMode;

    /**
     * Constructs a new {@linkplain NestedConfiguration}.
     * Construct via
     *
     * @param chooser the associated chooser
     * @param configuration the configuration to inherit from
     */
    protected NestedConfiguration(
            @NonNull ConfigurationChooser chooser,
            @NonNull Configuration configuration) {
        super(chooser);
        mParent = configuration;

        mFullConfig.set(mParent.mFullConfig);
        if (mParent.getEditedConfig() != null) {
            mEditedConfig = new FolderConfiguration();
            mEditedConfig.set(mParent.mEditedConfig);
        }
    }

    /**
     * Creates a new {@linkplain Configuration} which inherits values from the
     * given parent {@linkplain Configuration}, possibly overriding some as
     * well.
     *
     * @param chooser the associated chooser
     * @param parent the configuration to inherit values from
     * @return a new configuration
     */
    @NonNull
    public static NestedConfiguration create(@NonNull ConfigurationChooser chooser,
            @NonNull Configuration parent) {
        return new NestedConfiguration(chooser, parent);
    }

    @Override
    @Nullable
    public String getTheme() {
        // Never overridden: this is a static attribute of a layout, not something which
        // varies by configuration or at runtime
        return mParent.getTheme();
    }

    @Override
    public void setTheme(String theme) {
        // Never overridden
        mParent.setTheme(theme);
    }

    /**
     * Sets whether the locale should be overridden by this configuration
     *
     * @param override if true, override the inherited value
     */
    public void setOverrideLocale(boolean override) {
        mOverrideLocale = override;
    }

    /**
     * Returns true if the locale is overridden
     *
     * @return true if the locale is overridden
     */
    public boolean isOverridingLocale() {
        return mOverrideLocale;
    }

    @Override
    @NonNull
    public Locale getLocale() {
        if (mOverrideLocale) {
            return super.getLocale();
        } else {
            return mParent.getLocale();
        }
    }

    @Override
    public void setLocale(@NonNull Locale locale, boolean skipSync) {
        if (mOverrideLocale) {
            super.setLocale(locale, skipSync);
        } else {
            mParent.setLocale(locale, skipSync);
        }
    }

    /**
     * Sets whether the rendering target should be overridden by this configuration
     *
     * @param override if true, override the inherited value
     */
    public void setOverrideTarget(boolean override) {
        mOverrideTarget = override;
    }

    @Override
    @Nullable
    public IAndroidTarget getTarget() {
        if (mOverrideTarget) {
            return super.getTarget();
        } else {
            return mParent.getTarget();
        }
    }

    @Override
    public void setTarget(IAndroidTarget target, boolean skipSync) {
        if (mOverrideTarget) {
            super.setTarget(target, skipSync);
        } else {
            mParent.setTarget(target, skipSync);
        }
    }

    /**
     * Sets whether the device should be overridden by this configuration
     *
     * @param override if true, override the inherited value
     */
    public void setOverrideDevice(boolean override) {
        mOverrideDevice = override;
    }

    @Override
    @Nullable
    public Device getDevice() {
        if (mOverrideDevice) {
            return super.getDevice();
        } else {
            return mParent.getDevice();
        }
    }

    @Override
    public void setDevice(Device device, boolean skipSync) {
        if (mOverrideDevice) {
            super.setDevice(device, skipSync);
        } else {
            mParent.setDevice(device, skipSync);
        }
    }

    /**
     * Sets whether the device state should be overridden by this configuration
     *
     * @param override if true, override the inherited value
     */
    public void setOverrideDeviceState(boolean override) {
        mOverrideDeviceState = override;
    }

    @Override
    @Nullable
    public State getDeviceState() {
        if (mOverrideDeviceState) {
            return super.getDeviceState();
        } else {
            State state = mParent.getDeviceState();
            if (mOverrideDevice) {
                // If the device differs, I need to look up a suitable equivalent state
                // on our device
                if (state != null) {
                    Device device = super.getDevice();
                    if (device != null) {
                        return device.getState(state.getName());
                    }
                }
            }

            return state;
        }
    }

    @Override
    public void setDeviceState(State state, boolean skipSync) {
        if (mOverrideDeviceState) {
            super.setDeviceState(state, skipSync);
        } else {
            if (mOverrideDevice) {
                Device device = super.getDevice();
                if (device != null) {
                    State equivalentState = device.getState(state.getName());
                    if (equivalentState != null) {
                        state = equivalentState;
                    }
                }
            }
            mParent.setDeviceState(state, skipSync);
        }
    }

    /**
     * Sets whether the night mode should be overridden by this configuration
     *
     * @param override if true, override the inherited value
     */
    public void setOverrideNightMode(boolean override) {
        mOverrideNightMode = override;
    }

    @Override
    @NonNull
    public NightMode getNightMode() {
        if (mOverrideNightMode) {
            return super.getNightMode();
        } else {
            return mParent.getNightMode();
        }
    }

    @Override
    public void setNightMode(@NonNull NightMode night, boolean skipSync) {
        if (mOverrideNightMode) {
            super.setNightMode(night, skipSync);
        } else {
            mParent.setNightMode(night, skipSync);
        }
    }

    /**
     * Sets whether the UI mode should be overridden by this configuration
     *
     * @param override if true, override the inherited value
     */
    public void setOverrideUiMode(boolean override) {
        mOverrideUiMode = override;
    }

    @Override
    @NonNull
    public UiMode getUiMode() {
        if (mOverrideUiMode) {
            return super.getUiMode();
        } else {
            return mParent.getUiMode();
        }
    }

    @Override
    public void setUiMode(@NonNull UiMode uiMode, boolean skipSync) {
        if (mOverrideUiMode) {
            super.setUiMode(uiMode, skipSync);
        } else {
            mParent.setUiMode(uiMode, skipSync);
        }
    }

    /**
     * Returns the configuration this {@linkplain NestedConfiguration} is
     * inheriting from
     *
     * @return the configuration this configuration is inheriting from
     */
    @NonNull
    public Configuration getParent() {
        return mParent;
    }
}