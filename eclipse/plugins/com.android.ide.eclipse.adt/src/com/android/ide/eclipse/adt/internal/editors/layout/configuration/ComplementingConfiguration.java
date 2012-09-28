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
import com.android.ide.eclipse.adt.internal.editors.manifest.ManifestInfo;
import com.android.resources.NightMode;
import com.android.resources.UiMode;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.Hardware;
import com.android.sdklib.devices.Screen;
import com.android.sdklib.devices.State;

import java.util.List;

/**
 * An {@linkplain ComplementingConfiguration} is a {@link Configuration} which
 * inherits all of its values from a different configuration, except for one or
 * more attributes where it overrides a custom value, and the overridden value
 * will always <b>differ</b> from the inherited value!
 * <p>
 * For example, a {@linkplain ComplementingConfiguration} may state that it
 * overrides the locale, and if the inherited locale is "en", then the returned
 * locale from the {@linkplain ComplementingConfiguration} may be for example "nb",
 * but never "en".
 * <p>
 * The configuration will attempt to make its changed inherited value to be as
 * different as possible from the inherited value. Thus, a configuration which
 * overrides the device will probably return a phone-sized screen if the
 * inherited device is a tablet, or vice versa.
 */
public class ComplementingConfiguration extends NestedConfiguration {
    /**
     * If non zero, keep the display name up to date with the label for the
     * given overridden attribute, according to the flag constants in
     * {@link ConfigurationClient}
     */
    private int mUpdateDisplayName;

    /** Variation version; see {@link #setVariation(int)} */
    private int mVariation;

    /** Variation version count; see {@link #setVariationCount(int)} */
    private int mVariationCount;

    /**
     * Constructs a new {@linkplain ComplementingConfiguration}.
     * Construct via
     *
     * @param chooser the associated chooser
     * @param configuration the configuration to inherit from
     */
    private ComplementingConfiguration(
            @NonNull ConfigurationChooser chooser,
            @NonNull Configuration configuration) {
        super(chooser, configuration);
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
    public static ComplementingConfiguration create(@NonNull ConfigurationChooser chooser,
            @NonNull Configuration parent) {
        return new ComplementingConfiguration(chooser, parent);
    }

    /**
     * Sets the variation version for this
     * {@linkplain ComplementingConfiguration}. There might be multiple
     * {@linkplain ComplementingConfiguration} instances inheriting from a
     * {@link Configuration}. The variation version allows them to choose
     * different complementing values, so they don't all flip to the same other
     * (out of multiple choices) value. The {@link #setVariationCount(int)}
     * value can be used to determine how to partition the buckets of values.
     * Also updates the variation count if necessary.
     *
     * @param variation variation version
     */
    public void setVariation(int variation) {
        mVariation = variation;
        mVariationCount = Math.max(mVariationCount, variation + 1);
    }

    /**
     * Sets the number of {@link ComplementingConfiguration} variations mapped
     * to the same parent configuration as this one. See
     * {@link #setVariation(int)} for details.
     *
     * @param count the total number of variation versions
     */
    public void setVariationCount(int count) {
        mVariationCount = count;
    }

    @Override
    public void setOverrideDevice(boolean override) {
        mUpdateDisplayName |= ConfigurationClient.CHANGED_DEVICE;
        super.setOverrideDevice(override);
    }

    @Override
    public void setOverrideDeviceState(boolean override) {
        mUpdateDisplayName |= ConfigurationClient.CHANGED_DEVICE_CONFIG;
        super.setOverrideDeviceState(override);
    }

    @Override
    public void setOverrideLocale(boolean override) {
        mUpdateDisplayName |= ConfigurationClient.CHANGED_LOCALE;
        super.setOverrideLocale(override);
    }

    @Override
    public void setOverrideTarget(boolean override) {
        mUpdateDisplayName |= ConfigurationClient.CHANGED_RENDER_TARGET;
        super.setOverrideTarget(override);
    }

    @Override
    public void setOverrideNightMode(boolean override) {
        mUpdateDisplayName |= ConfigurationClient.CHANGED_NIGHT_MODE;
        super.setOverrideNightMode(override);
    }

    @Override
    public void setOverrideUiMode(boolean override) {
        mUpdateDisplayName |= ConfigurationClient.CHANGED_UI_MODE;
        super.setOverrideUiMode(override);
    }

    @Override
    @NonNull
    public Locale getLocale() {
        Locale locale = mParent.getLocale();
        if (mOverrideLocale && locale != null) {
            List<Locale> locales = mConfigChooser.getLocaleList();
            for (Locale l : locales) {
                // TODO: Try to be smarter about which one we pick; for example, try
                // to pick a language that is substantially different from the inherited
                // language, such as either with the strings of the largest or shortest
                // length, or perhaps based on some geography or population metrics
                if (!l.equals(locale)) {
                    locale = l;
                    break;
                }
            }

            if ((mUpdateDisplayName & ConfigurationClient.CHANGED_LOCALE) != 0) {
                setDisplayName(ConfigurationChooser.getLocaleLabel(mConfigChooser, locale, false));
            }
        }

        return locale;
    }

    @Override
    @Nullable
    public IAndroidTarget getTarget() {
        IAndroidTarget target = mParent.getTarget();
        if (mOverrideTarget && target != null) {
            List<IAndroidTarget> targets = mConfigChooser.getTargetList();
            if (!targets.isEmpty()) {
                // Pick a different target: if you're showing the most recent render target,
                // then pick the lowest supported target, and vice versa
                IAndroidTarget mostRecent = targets.get(targets.size() - 1);
                if (target.equals(mostRecent)) {
                    // Find oldest supported
                    ManifestInfo info = ManifestInfo.get(mConfigChooser.getProject());
                    int minSdkVersion = info.getMinSdkVersion();
                    for (IAndroidTarget t : targets) {
                        if (t.getVersion().getApiLevel() >= minSdkVersion) {
                            target = t;
                            break;
                        }
                    }
                } else {
                    target = mostRecent;
                }
            }

            if ((mUpdateDisplayName & ConfigurationClient.CHANGED_RENDER_TARGET) != 0) {
                setDisplayName(ConfigurationChooser.getRenderingTargetLabel(target, false));
            }
        }

        return target;
    }

    @Override
    @Nullable
    public Device getDevice() {
        Device device = mParent.getDevice();
        if (mOverrideDevice && device != null) {
            // Pick a different device
            List<Device> devices = mConfigChooser.getDeviceList();


            // Divide up the available devices into {@link #mVariationCount} + 1 buckets
            // (the + 1 is for the bucket now taken up by the inherited value).
            // Then assign buckets to each {@link #mVariation} version, and pick one
            // from the bucket assigned to this current configuration's variation version.

            // I could just divide up the device list count, but that would treat a lot of
            // very similar phones as having the same kind of variety as the 7" and 10"
            // tablets which are sitting right next to each other in the device list.
            // Instead, do this by screen size.


            double smallest = 100;
            double biggest = 1;
            for (Device d : devices) {
                double size = getScreenSize(d);
                if (size < 0) {
                    continue; // no data
                }
                if (size >= biggest) {
                    biggest = size;
                }
                if (size <= smallest) {
                    smallest = size;
                }
            }

            int bucketCount = mVariationCount + 1;
            double inchesPerBucket = (biggest - smallest) / bucketCount;

            double overriddenSize = getScreenSize(device);
            int overriddenBucket = (int) ((overriddenSize - smallest) / inchesPerBucket);
            int bucket = (mVariation < overriddenBucket) ? mVariation : mVariation + 1;
            double from = inchesPerBucket * bucket + smallest;
            double to = from + inchesPerBucket;
            if (biggest - to < 0.1) {
                to = biggest + 0.1;
            }

            for (Device d : devices) {
                double size = getScreenSize(d);
                if (size >= from && size < to) {
                    device = d;
                    break;
                }
            }

            if ((mUpdateDisplayName & ConfigurationClient.CHANGED_DEVICE) != 0) {
                setDisplayName(ConfigurationChooser.getDeviceLabel(device, true));
            }
        }

        return device;
    }
    private static double getScreenSize(@NonNull Device device) {
        Hardware hardware = device.getDefaultHardware();
        if (hardware != null) {
            Screen screen = hardware.getScreen();
            if (screen != null) {
                return screen.getDiagonalLength();
            }
        }

        return -1;
    }

    @Override
    @Nullable
    public State getDeviceState() {
        State state = mParent.getDeviceState();
        if (mOverrideDeviceState && state != null) {
            State alternate = getNextDeviceState(state);

            if ((mUpdateDisplayName & ConfigurationClient.CHANGED_DEVICE_CONFIG) != 0) {
                if (alternate != null) {
                    setDisplayName(alternate.getName());
                }
            }

            return alternate;
        } else {
            if (mOverrideDevice && state != null) {
                // If the device differs, I need to look up a suitable equivalent state
                // on our device
                Device device = getDevice();
                if (device != null) {
                    return device.getState(state.getName());
                }
            }

            return state;
        }
    }

    @Override
    @NonNull
    public NightMode getNightMode() {
        NightMode nightMode = mParent.getNightMode();
        if (mOverrideNightMode && nightMode != null) {
            nightMode = nightMode == NightMode.NIGHT ? NightMode.NOTNIGHT : NightMode.NIGHT;
            if ((mUpdateDisplayName & ConfigurationClient.CHANGED_NIGHT_MODE) != 0) {
                setDisplayName(nightMode.getLongDisplayValue());
            }
            return nightMode;
        } else {
            return nightMode;
        }
    }

    @Override
    @NonNull
    public UiMode getUiMode() {
        UiMode uiMode = mParent.getUiMode();
        if (mOverrideUiMode && uiMode != null) {
            // TODO: Use manifest's supports screen to decide which are most relevant
            // (as well as which available configuration qualifiers are present in the
            // layout)
            UiMode[] values = UiMode.values();
            uiMode = values[(uiMode.ordinal() + 1) % values.length];
            if ((mUpdateDisplayName & ConfigurationClient.CHANGED_UI_MODE) != 0) {
                setDisplayName(uiMode.getLongDisplayValue());
            }
            return uiMode;
        } else {
            return uiMode;
        }
    }
}