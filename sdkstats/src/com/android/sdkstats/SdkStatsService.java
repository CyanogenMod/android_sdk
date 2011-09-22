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

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Utility class to send "ping" usage reports to the server. */
public class SdkStatsService {

    /** Minimum interval between ping, in milliseconds. */
    private static final long PING_INTERVAL_MSEC = 86400 * 1000;  // 1 day

    private DdmsPreferenceStore mStore = new DdmsPreferenceStore();

    public SdkStatsService() {
    }

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
    public void ping(String app, String version) {
        doPing(app, version);
    }

    /**
     * Display a dialog to the user providing information about the ping service,
     * and whether they'd like to opt-out of it.
     *
     * Once the dialog has been shown, it sets a preference internally indicating that the user has
     * viewed this dialog. This setting can be queried using {@link #pingPermissionsSet()}.
     */
    public void checkUserPermissionForPing(Shell parent) {
        if (!mStore.hasPingId()) {
            askUserPermissionForPing(parent);
            mStore.generateNewPingId();
        }
    }

    /**
     * Prompt the user for whether they want to opt out of reporting, and save the user
     * input in preferences.
     */
    private void askUserPermissionForPing(final Shell parent) {
        final Display display = parent.getDisplay();
        display.syncExec(new Runnable() {
            public void run() {
                SdkStatsPermissionDialog dialog = new SdkStatsPermissionDialog(parent);
                dialog.open();
                mStore.setPingOptIn(dialog.getPingUserPreference());
            }
        });
    }

    // -------

    /**
     * Pings the usage stats server, as long as the prefs contain the opt-in boolean
     *
     * @param app name to report in the ping
     * @param version to report in the ping
     */
    private void doPing(final String app, String version) {
        // Validate the application and version input.
        final String normalVersion = normalizeVersion(app, version);

        // If the user has not opted in, do nothing and quietly return.
        if (!mStore.isPingOptIn()) {
            // user opted out.
            return;
        }

        // If the last ping *for this app* was too recent, do nothing.
        long now = System.currentTimeMillis();
        long then = mStore.getPingTime(app);
        if (now - then < PING_INTERVAL_MSEC) {
            // too soon after a ping.
            return;
        }

        // Record the time of the attempt, whether or not it succeeds.
        mStore.setPingTime(app, now);

        // Send the ping itself in the background (don't block if the
        // network is down or slow or confused).
        final long id = mStore.getPingId();
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
