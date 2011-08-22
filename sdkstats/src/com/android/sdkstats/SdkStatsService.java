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
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Utility class to send "ping" usage reports to the server. */
public class SdkStatsService {

    /** Minimum interval between ping, in milliseconds. */
    private static final long PING_INTERVAL_MSEC = 86400 * 1000;  // 1 day

    public final static String PING_OPT_IN = "pingOptIn"; //$NON-NLS-1$
    public final static String PING_TIME = "pingTime"; //$NON-NLS-1$
    public final static String PING_ID = "pingId"; //$NON-NLS-1$

    private static PreferenceStore sPrefStore;

    /**
     * Send a "ping" to the Google toolbar server, if enough time has
     * elapsed since the last ping, and if the user has not opted out.<br>
     *
     * The ping will not be sent if the user opt out dialog has not been shown yet.
     * Use {@link #getUserPermissionForPing(Shell)} to display the dialog requesting
     * user permissions.<br>
     *
     * Note: The actual ping (if any) is sent in a <i>non-daemon</i> background thread.
     *
     * @param app name to report in the ping
     * @param version to report in the ping
     */
    public static void ping(final String app, final String version) {
        doPing(app, version, getPreferenceStore());
    }

    /**
     * Find out if user has already set permissions for the ping service.
     * @return true if user has already set the permissions for the ping service. This could've
     * happened only if the user has already viewed the dialog displayed by
     * {@link #getUserPermissionForPing(Shell)}.
     */
    public static boolean pingPermissionsSet() {
        PreferenceStore prefs = getPreferenceStore();
        return prefs != null && prefs.contains(PING_ID);
    }

    /**
     * Display a dialog to the user providing information about the ping service,
     * and whether they'd like to opt-out of it.
     *
     * Once the dialog has been shown, it sets a preference internally indicating that the user has
     * viewed this dialog. This setting can be queried using {@link #pingPermissionsSet()}.
     */
    public static void getUserPermissionForPing(Shell parent) {
        PreferenceStore prefs = getPreferenceStore();
        getUserPermissionForPing(prefs, parent);

        // First time: make up a new ID.  TODO: Use something more random?
        prefs.setValue(PING_ID, new Random().nextLong());
        try {
            prefs.save();
        } catch (IOException e) {
            /* ignore exceptions while saving preferences */
        }
    }

    /**
     * Returns the DDMS {@link PreferenceStore}.
     */
    public static synchronized PreferenceStore getPreferenceStore() {
        if (sPrefStore == null) {
            // get the location of the preferences
            String homeDir = null;
            try {
                homeDir = AndroidLocation.getFolder();
            } catch (AndroidLocationException e1) {
                // pass, we'll do a dummy store since homeDir is null
            }

            if (homeDir != null) {
                String rcFileName = homeDir + "ddms.cfg"; //$NON-NLS-1$

                // also look for an old pref file in the previous location
                String oldPrefPath = System.getProperty("user.home") //$NON-NLS-1$
                    + File.separator + ".ddmsrc"; //$NON-NLS-1$
                File oldPrefFile = new File(oldPrefPath);
                if (oldPrefFile.isFile()) {
                    try {
                        PreferenceStore oldStore = new PreferenceStore(oldPrefPath);
                        oldStore.load();

                        oldStore.save(new FileOutputStream(rcFileName), "");
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
                        System.err.println("Error Loading Preferences");
                    }
                }
            } else {
                sPrefStore = new PreferenceStore();
            }
        }

        return sPrefStore;
    }

    /**
     * Pings the usage stats server, as long as the prefs contain the opt-in boolean
     *
     * @param app name to report in the ping
     * @param version to report in the ping
     * @param prefs the preference store where the opt-in value and ping times are store
     */
    private static void doPing(final String app, String version, PreferenceStore prefs) {
        // Validate the application and version input.
        final String normalVersion = normalizeVersion(app, version);

        // If the user has not opted in, do nothing and quietly return.
        if (!prefs.getBoolean(PING_OPT_IN)) {
            // user opted out.
            return;
        }

        // If the last ping *for this app* was too recent, do nothing.
        String timePref = PING_TIME + "." + app;  //$NON-NLS-1$
        long now = System.currentTimeMillis();
        long then = prefs.getLong(timePref);
        if (now - then < PING_INTERVAL_MSEC) {
            // too soon after a ping.
            return;
        }

        // Record the time of the attempt, whether or not it succeeds.
        prefs.setValue(timePref, now);
        try {
            prefs.save();
        }
        catch (IOException ioe) {
        }

        // Send the ping itself in the background (don't block if the
        // network is down or slow or confused).
        final long id = prefs.getLong(PING_ID);
        new Thread() {
            @Override
            public void run() {
                try {
                    actuallySendPing(app, normalVersion, id);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }


    /**
     * Unconditionally send a "ping" request to the Google toolbar server.
     *
     * @param app name to report in the ping
     * @param version to report in the ping (dotted numbers, no more than four)
     * @param id of the local installation
     * @throws IOException if the ping failed
     */
    @SuppressWarnings("deprecation")
    private static void actuallySendPing(String app, String version, long id)
                throws IOException {
        // Detect and report the host OS.
        String os = System.getProperty("os.name");          //$NON-NLS-1$
        if (os.startsWith("Mac OS")) {                      //$NON-NLS-1$
            os = "mac";                                     //$NON-NLS-1$
            String osVers = getVersion();
            if (osVers != null) {
                os = os + "-" + osVers;                     //$NON-NLS-1$
            }
        } else if (os.startsWith("Windows")) {              //$NON-NLS-1$
            os = "win";                                     //$NON-NLS-1$
            String osVers = getVersion();
            if (osVers != null) {
                os = os + "-" + osVers;                     //$NON-NLS-1$
            }
        } else if (os.startsWith("Linux")) {                //$NON-NLS-1$
            os = "linux";                                   //$NON-NLS-1$
        } else {
            // Unknown -- surprising -- send it verbatim so we can see it.
            os = URLEncoder.encode(os);
        }

        // Include the application's name as part of the as= value.
        // Share the user ID for all apps, to allow unified activity reports.

        URL url = new URL(
            "http",                                         //$NON-NLS-1$
            "tools.google.com",                             //$NON-NLS-1$
            "/service/update?as=androidsdk_" + app +        //$NON-NLS-1$
                "&id=" + Long.toHexString(id) +             //$NON-NLS-1$
                "&version=" + version +                     //$NON-NLS-1$
                "&os=" + os);                               //$NON-NLS-1$

        // Discard the actual response, but make sure it reads OK
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        // Believe it or not, a 404 response indicates success:
        // the ping was logged, but no update is configured.
        if (conn.getResponseCode() != HttpURLConnection.HTTP_OK &&
            conn.getResponseCode() != HttpURLConnection.HTTP_NOT_FOUND) {
            throw new IOException(
                conn.getResponseMessage() + ": " + url);    //$NON-NLS-1$
        }
    }

    /**
     * Returns the version of the os if it is defined as X.Y, or null otherwise.
     * <p/>
     * Example of returned versions can be found at http://lopica.sourceforge.net/os.html
     * <p/>
     * This method removes any exiting micro versions.
     */
    private static String getVersion() {
        Pattern p = Pattern.compile("(\\d+)\\.(\\d+).*"); //$NON-NLS-1$
        String osVers = System.getProperty("os.version"); //$NON-NLS-1$
        Matcher m = p.matcher(osVers);
        if (m.matches()) {
            return m.group(1) + "." + m.group(2);         //$NON-NLS-1$
        }

        return null;
    }

    /**
     * Prompt the user for whether they want to opt out of reporting, and save the user
     * input in preferences.
     */
    private static void getUserPermissionForPing(final PreferenceStore prefs, final Shell parent) {
        final Display display = parent.getDisplay();
        display.syncExec(new Runnable() {
            public void run() {
                SdkStatsPermissionDialog dialog = new SdkStatsPermissionDialog(parent);
                dialog.open();
                prefs.setValue(PING_OPT_IN, dialog.getPingUserPreference());
            }
        });
    }

    /**
     * Validate the supplied application version, and normalize the version.
     * @param app to report
     * @param version supplied by caller
     * @return normalized dotted quad version
     */
    private static String normalizeVersion(String app, String version) {
        // Application name must contain only word characters (no punctuation)
        if (!app.matches("\\w+")) {
            throw new IllegalArgumentException("Bad app name: " + app);
        }

        // Version must be between 1 and 4 dotted numbers
        String[] numbers = version.split("\\.");
        if (numbers.length > 4) {
            throw new IllegalArgumentException("Bad version: " + version);
        }
        for (String part: numbers) {
            if (!part.matches("\\d+")) {
                throw new IllegalArgumentException("Bad version: " + version);
            }
        }

        // Always output 4 numbers, even if fewer were supplied (pad with .0)
        StringBuffer normal = new StringBuffer(numbers[0]);
        for (int i = 1; i < 4; i++) {
            normal.append(".").append(i < numbers.length ? numbers[i] : "0");
        }
        return normal.toString();
    }
}
