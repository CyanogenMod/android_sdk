/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.sdkstats;

import com.android.prefs.AndroidLocation;
import com.android.prefs.AndroidLocation.AndroidLocationException;

import org.eclipse.jface.preference.PreferenceStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

/** Utility class to send "ping" usage reports to the server. */
public class DdmsPreferenceStore {

    public  final static String PING_OPT_IN = "pingOptIn";          //$NON-NLS-1$
    private final static String PING_TIME   = "pingTime";           //$NON-NLS-1$
    private final static String PING_ID     = "pingId";             //$NON-NLS-1$
    private final static String ADT_STARTUP_WIZARD = "adtStrtWzd";  //$NON-NLS-1$
    private final static String LAST_SDK_PATH = "lastSdkPath";      //$NON-NLS-1$

    /**
     * Lock for the preference store.
     */
    private static volatile Object[] sLock = new Object[0];
    /**
     * PreferenceStore for DDMS. Creation and usage must be synchronized on sLock.
     * Don't use it directly, instead retrieve it via {@link #getPreferenceStore()}.
     */
    private static volatile PreferenceStore sPrefStore;

    public DdmsPreferenceStore() {
    }

    /**
     * Returns the DDMS {@link PreferenceStore}.
     * This keeps a static reference on the store, so consequent calls will
     * return always the same store.
     */
    public PreferenceStore getPreferenceStore() {
        synchronized(sLock) {
            if (sPrefStore == null) {
                // get the location of the preferences
                String homeDir = null;
                try {
                    homeDir = AndroidLocation.getFolder();
                } catch (AndroidLocationException e1) {
                    // pass, we'll do a dummy store since homeDir is null
                }

                if (homeDir == null) {
                    sPrefStore = new PreferenceStore();
                    return sPrefStore;
                }

                assert homeDir != null;

                String rcFileName = homeDir + "ddms.cfg";                       //$NON-NLS-1$

                // also look for an old pref file in the previous location
                String oldPrefPath = System.getProperty("user.home")            //$NON-NLS-1$
                    + File.separator + ".ddmsrc";                               //$NON-NLS-1$
                File oldPrefFile = new File(oldPrefPath);
                if (oldPrefFile.isFile()) {
                    try {
                        PreferenceStore oldStore = new PreferenceStore(oldPrefPath);
                        oldStore.load();

                        oldStore.save(new FileOutputStream(rcFileName), "");    //$NON-NLS-1$
                        oldPrefFile.delete();

                        PreferenceStore newStore = new PreferenceStore(rcFileName);
                        newStore.load();
                        sPrefStore = newStore;
                    } catch (IOException e) {
                        // create a new empty store.
                        sPrefStore = new PreferenceStore(rcFileName);
                    }
                } else {
                    sPrefStore = new PreferenceStore(rcFileName);

                    try {
                        sPrefStore.load();
                    } catch (IOException e) {
                        System.err.println("Error Loading DDMS Preferences");
                    }
                }
            }

            assert sPrefStore != null;
            return sPrefStore;
        }
    }

    /**
     * Save the prefs to the config file.
     */
    public void save() {
        PreferenceStore prefs = getPreferenceStore();
        synchronized(sLock) {
            try {
                prefs.save();
            }
            catch (IOException ioe) {
                // FIXME com.android.dmmlib.Log.w("ddms", "Failed saving prefs file: " + ioe.getMessage());
            }
        }
    }

    // ---- Utility methods to access some specifis prefs ----

    /**
     * Indicates whether the ping ID is set.
     * This should be true when {@link #isPingOptIn()} is true.
     *
     * @return true if a ping ID is set, which means the user gave permission
     *              to use the ping service.
     */
    public boolean hasPingId() {
        PreferenceStore prefs = getPreferenceStore();
        synchronized(sLock) {
            return prefs != null && prefs.contains(PING_ID);
        }
    }

    /**
     * Retrieves the current ping ID, if set.
     * To know if the ping ID is set, use {@link #hasPingId()}.
     * The  value returned is 0L if there's no store or no ping ID set in the store.
     */
    public long getPingId() {
        PreferenceStore prefs = getPreferenceStore();
        synchronized(sLock) {
            return prefs == null ? 0L : prefs.getLong(PING_ID);
        }
    }

    /**
     * Generates a new random ping ID and saves it in the preference store.
     *
     * @return The new ping ID.
     */
    public long generateNewPingId() {
        PreferenceStore prefs = getPreferenceStore();

        Random rnd = new Random();
        long id = rnd.nextLong();

        // Let's try to be conservative and avoid 0L as an ID.
        // (ideally it would be nice to keep 0L as a special reserved value,
        //  to both have a default value and detect when variables aren't set
        //  properly. It's too late to enforce this, but we can still avoid
        //  generating new ones like this.)
        for (int i = 0; id == 0L && i < 10; i++) {
            id = rnd.nextLong();
        }

        synchronized(sLock) {
            prefs.setValue(PING_ID, id);
            try {
                prefs.save();
            } catch (IOException e) {
                /* ignore exceptions while saving preferences */
            }
        }

        return id;
    }

    /**
     * Returns the "ping opt in" value from the preference store.
     * This would be true if there's a valid preference store and
     * the user opted for sending ping statistics.
     */
    public boolean isPingOptIn() {
        PreferenceStore prefs = getPreferenceStore();
        synchronized(sLock) {
            return prefs != null && prefs.contains(PING_OPT_IN);
        }
    }

    /**
     * Saves the "ping opt in" value in the preference store.
     *
     * @param optIn The new user opt-in value.
     */
    public void setPingOptIn(boolean optIn) {
        PreferenceStore prefs = getPreferenceStore();

        synchronized(sLock) {
            prefs.setValue(PING_OPT_IN, optIn);
            try {
                prefs.save();
            } catch (IOException e) {
                /* ignore exceptions while saving preferences */
            }
        }
    }

    /**
     * Retrieves the ping time for the given app from the preference store.
     * Callers should use {@link System#currentTimeMillis()} for time stamps.
     *
     * @param app The app name identifier.
     * @return 0L if we don't have a preference store or there was no time
     *  recorded in the store for the requested app. Otherwise the time stamp
     *  from the store.
     */
    public long getPingTime(String app) {
        PreferenceStore prefs = getPreferenceStore();
        String timePref = PING_TIME + "." + app;  //$NON-NLS-1$
        synchronized(sLock) {
            return prefs == null ? 0 : prefs.getLong(timePref);
        }
    }

    /**
     * Sets the ping time for the given app from the preference store.
     * Callers should use {@link System#currentTimeMillis()} for time stamps.
     *
     * @param app The app name identifier.
     * @param timeStamp The time stamp from the store.
     *                   0L is a sepcial value that should not be used.
     */
    public void setPingTime(String app, long timeStamp) {
        PreferenceStore prefs = getPreferenceStore();
        String timePref = PING_TIME + "." + app;  //$NON-NLS-1$
        synchronized(sLock) {
            prefs.setValue(timePref, timeStamp);
            try {
                prefs.save();
            } catch (IOException ioe) {
                /* ignore exceptions while saving preferences */
            }
        }
    }

    /**
     * True if there's a valid preference store and the ADT startup
     * wizard has been shown once.
     */
    public boolean wasAdtStartupWizardShown() {
        PreferenceStore prefs = getPreferenceStore();
        synchronized(sLock) {
            return prefs == null ? false : prefs.getBoolean(ADT_STARTUP_WIZARD);
        }
    }

    /**
     * Sets whether the ADT startup wizard has been shown.
     */
    public void setAdtStartupWizardShown(boolean shown) {
        PreferenceStore prefs = getPreferenceStore();
        synchronized(sLock) {
            prefs.setValue(ADT_STARTUP_WIZARD, shown);
            try {
                prefs.save();
            } catch (IOException ioe) {
                /* ignore exceptions while saving preferences */
            }
        }
    }

    /**
     * Retrieves the last SDK OS path.
     * <p/>
     * This is just an information value, the path may not exist, may not
     * even be on an existing file system and/or may not point to an SDK
     * anymore.
     *
     * @return The last SDK OS path from the preference store, or null if
     *  there is no store or an empty string if it is not defined.
     */
    public String getLastSdkPath() {
        PreferenceStore prefs = getPreferenceStore();
        synchronized(sLock) {
            return prefs == null ? null : prefs.getString(LAST_SDK_PATH);
        }
    }

    /**
     * Sets the last SDK OS path.
     *
     * @param osSdkPath The SDK OS Path. Can be null or empty.
     */
    public void setLastSdkPath(String osSdkPath) {
        PreferenceStore prefs = getPreferenceStore();
        synchronized(sLock) {
            prefs.setValue(LAST_SDK_PATH, osSdkPath);
            try {
                prefs.save();
            } catch (IOException ioe) {
                /* ignore exceptions while saving preferences */
            }
        }
    }
}
