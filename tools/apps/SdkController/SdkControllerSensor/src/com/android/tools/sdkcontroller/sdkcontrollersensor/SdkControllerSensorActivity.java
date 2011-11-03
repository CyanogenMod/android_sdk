/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.tools.sdkcontroller.sdkcontrollersensor;

import java.util.ArrayList;
import java.util.List;
import java.io.IOException;

import android.app.Activity;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.Sensor;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.CompoundButton;
import android.view.LayoutInflater;
import android.util.Log;

import com.android.tools.sdkcontroller.lib.Emulator;
import com.android.tools.sdkcontroller.lib.Emulator.EmulatorConnectionType;
import com.android.tools.sdkcontroller.lib.OnEmulatorListener;

/**
 * Encapsulates an application that monitors all sensors available on a device,
 * and sends acquired sensor values to an Android Emulator application running
 * on the host machine. This application is used to provide a realistic sensor
 * emulation in Android Emulator.
 */
public class SdkControllerSensorActivity extends Activity implements OnEmulatorListener {
    /** Tag for logging messages. */
    private static final String TAG = "SdkControllerSensor";

    /** TCP over USB connection to the emulator. */
    private Emulator mEmulator;
    /** Array containing monitored sensors. */
    private List<MonitoredSensor> mSensors;
    /** Controls displayed list of sensors. */
    private TableLayout mTableLayout;

    /**
     * Encapsulates a sensor that is being monitored.
     *
     * To monitor sensor changes each monitored sensor registers with sensor
     * manager as a sensor listener.
     *
     * To control sensor monitoring from the UI, each monitored sensor has two
     * UI controls associated with it:
     *   - A check box (named after sensor) that can be used to enable,
     *     or disable listening to the sensor changes.
     *   - A text view where current sensor value is displayed.
     */
    private class MonitoredSensor implements SensorEventListener,
            CompoundButton.OnCheckedChangeListener {
        /** Sensor to monitor. */
        private final Sensor mSensor;
        /** Check box representing the sensor on the screen. */
        private final CheckBox mChk;
        /** Text view displaying the value of the sensor. */
        private final TextView mVal;
        /** Emulator-friendly name for the sensor. */
        private String mEmulatorFriendlyName;
        /** Formats string to show in the TextView. */
        private String mTextFmt;
        /** Formats string to send to the emulator. */
        private String mMsgFmt;
        /**
         * Enabled state. This state is controlled by the emulator, that
         * maintains its own list of sensors. So, if a sensor is missing, or is
         * disabled in the emulator, it should be disabled in this application.
         */
        private boolean mEnabled = false;
        /** Checked state. */
        private boolean mChecked = true;

        /**
         * Constructs MonitoredSensor instance, and register the listeners.
         *
         * @param sensor Sensor to monitor.
         */
        MonitoredSensor(Sensor sensor) {
            mSensor = sensor;
            mChecked = true;

            // Add a row representing this sensor on the display
            final LayoutInflater inflater = getLayoutInflater();
            final TableRow row = (TableRow) inflater.inflate(R.layout.one_row, mTableLayout, false);
            mTableLayout.addView(row);

            // Initialize displayed checkbox for this sensor, and register
            // checked state listener for it.
            mChk = (CheckBox) row.findViewById(R.id.row_checkbox);
            mChk.setChecked(true);
            mChk.setOnCheckedChangeListener(this);

            // Initialize displayed text box for this sensor.
            mVal = (TextView) row.findViewById(R.id.row_textview);
            mVal.setText("");

            // Set appropriate sensor name depending on the type. Unfortunately,
            // we can't really use sensor.getName() here, since the value it
            // returns (although resembles the purpose) is a bit vaguer than it
            // should be. Also choose an appropriate format for the strings that
            // display sensor's value, and strings that are sent to the
            // emulator.
            switch (sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    mChk.setText("Accelerometer");
                    // 3 floats.
                    mTextFmt = "%+.2f %+.2f %+.2f";
                    mEmulatorFriendlyName = "acceleration";
                    mMsgFmt = mEmulatorFriendlyName + ":%g:%g:%g\0";
                    break;
                case 9: // Sensor.TYPE_GRAVITY is missing in API 7
                    // 3 floats.
                    mChk.setText("Gravity");
                    mTextFmt = "%+.2f %+.2f %+.2f";
                    mEmulatorFriendlyName = "gravity";
                    mMsgFmt = mEmulatorFriendlyName + ":%g:%g:%g\0";
                    break;
                case Sensor.TYPE_GYROSCOPE:
                    mChk.setText("Gyroscope");
                    // 3 floats.
                    mTextFmt = "%+.2f %+.2f %+.2f";
                    mEmulatorFriendlyName = "gyroscope";
                    mMsgFmt = mEmulatorFriendlyName + ":%g:%g:%g\0";
                    break;
                case Sensor.TYPE_LIGHT:
                    mChk.setText("Light");
                    // 1 integer.
                    mTextFmt = "%.0f";
                    mEmulatorFriendlyName = "light";
                    mMsgFmt = mEmulatorFriendlyName + ":%g\0";
                    break;
                case 10: // Sensor.TYPE_LINEAR_ACCELERATION is missing in API 7
                    mChk.setText("Linear acceleration");
                    // 3 floats.
                    mTextFmt = "%+.2f %+.2f %+.2f";
                    mEmulatorFriendlyName = "linear-acceleration";
                    mMsgFmt = mEmulatorFriendlyName + ":%g:%g:%g\0";
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD:
                    mChk.setText("Magnetic field");
                    // 3 floats.
                    mTextFmt = "%+.2f %+.2f %+.2f";
                    mEmulatorFriendlyName = "magnetic-field";
                    mMsgFmt = mEmulatorFriendlyName + ":%g:%g:%g\0";
                    break;
                case Sensor.TYPE_ORIENTATION:
                    mChk.setText("Orientation");
                    // 3 integers.
                    mTextFmt = "%+03.0f %+03.0f %+03.0f";
                    mEmulatorFriendlyName = "orientation";
                    mMsgFmt = mEmulatorFriendlyName + ":%g:%g:%g\0";
                    break;
                case Sensor.TYPE_PRESSURE:
                    mChk.setText("Pressure");
                    // 1 integer.
                    mTextFmt = "%.0f";
                    mEmulatorFriendlyName = "pressure";
                    mMsgFmt = mEmulatorFriendlyName + ":%g\0";
                    break;
                case Sensor.TYPE_PROXIMITY:
                    mChk.setText("Proximity");
                    // 1 integer.
                    mTextFmt = "%.0f";
                    mEmulatorFriendlyName = "proximity";
                    mMsgFmt = mEmulatorFriendlyName + ":%g\0";
                    break;
                case 11: // Sensor.TYPE_ROTATION_VECTOR is missing in API 7
                    mChk.setText("Rotation");
                    // 3 floats.
                    mTextFmt = "%+.2f %+.2f %+.2f";
                    mEmulatorFriendlyName = "rotation";
                    mMsgFmt = mEmulatorFriendlyName + ":%g:%g:%g\0";
                    break;
                case Sensor.TYPE_TEMPERATURE:
                    mChk.setText("Temperature");
                    // 1 integer.
                    mTextFmt = "%.0f";
                    mEmulatorFriendlyName = "tempterature";
                    mMsgFmt = mEmulatorFriendlyName + ":%g\0";
                    break;
                default:
                    mChk.setText("<Unknown>");
                    mTextFmt = "N/A";
                    mEmulatorFriendlyName = "unknown";
                    mMsgFmt = mEmulatorFriendlyName + "\0";
                    Loge("Unknown sensor type " + mSensor.getType() + " for sensor "
                            + mSensor.getName());
                    break;
            }
        }

        /**
         * Gets sensor type.
         *
         * @return Sensor type as one of the Sensor.TYPE_XXX constants.
         */
        private int getType() {
            return mSensor.getType();
        }

        /**
         * Gets sensor's emulator-friendly name.
         *
         * @return Sensor's emulator-friendly name.
         */
        private String getEmulatorFriendlyName() {
            return mEmulatorFriendlyName;
        }

        /**
         * Starts monitoring the sensor. NOTE: This method is called from
         * outside of the UI thread.
         */
        private void startListening() {
            if (mEnabled && mChecked) {
                Logv("+++ Sensor " + getEmulatorFriendlyName() + " is started.");
                SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
                sm.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_UI);
            }
        }

        /**
         * Stops monitoring the sensor. NOTE: This method is called from outside
         * of the UI thread.
         */
        private void stopListening() {
            Logv("--- Sensor " + getEmulatorFriendlyName() + " is stopped.");
            SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
            sm.unregisterListener(this);
        }

        /**
         * Enables sensor events. NOTE: This method is called from outside of
         * the UI thread.
         */
        private void enableSensor() {
            Logv(">>> Sensor " + getEmulatorFriendlyName() + " is enabled.");
            mEnabled = true;
            mChk.post(new Runnable() {
                @Override
                public void run() {
                    mChk.setEnabled(true);
                    mVal.setText("");
                    mVal.setEnabled(true);
                }
            });
        }

        /**
         * Disables sensor events. NOTE: This method is called from outside of
         * the UI thread.
         */
        private void disableSensor() {
            Logv("<<< Sensor " + getEmulatorFriendlyName() + " is disabled.");
            mEnabled = false;
            mChk.post(new Runnable() {
                @Override
                public void run() {
                    mChk.setEnabled(false);
                    mVal.setText("Disabled");
                    mVal.setEnabled(false);
                }
            });
        }

        /**
         * Handles checked state change for the associated CheckBox. If check
         * box is checked we will register sensor change listener. If it is
         * unchecked, we will unregister sensor change listener.
         */
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            mChecked = isChecked;
            if (isChecked) {
                startListening();
            } else {
                stopListening();
            }
        }

        /**
         * Handles "sensor changed" event. This is an implementation of the
         * SensorEventListener interface.
         */
        @Override
        public void onSensorChanged(SensorEvent event) {
            // Display current sensor value, and format message that will be
            // sent to the emulator.
            final int nArgs = event.values.length;
            String msg;
            String val;
            if (nArgs == 3) {
                val = String.format(mTextFmt, event.values[0], event.values[1], event.values[2]);
                msg = String.format(mMsgFmt, event.values[0], event.values[1], event.values[2]);
            } else if (nArgs == 2) {
                val = String.format(mTextFmt, event.values[0], event.values[1]);
                msg = String.format(mMsgFmt, event.values[0], event.values[1]);
            } else if (nArgs == 1) {
                val = String.format(mTextFmt, event.values[0]);
                msg = String.format(mMsgFmt, event.values[0]);
            } else {
                Loge("Unexpected number of values " + event.values.length
                        + " in onSensorChanged for sensor " + mSensor.getName());
                return;
            }
            mVal.setText(val);
            sendSensorEvent(msg);
        }

        /**
         * Handles "sensor accuracy changed" event. This is an implementation of
         * the SensorEventListener interface.
         */
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    } // MonitoredSensor

    /***************************************************************************
     * SdkControllerSensor implementation
     **************************************************************************/

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mTableLayout = (TableLayout) findViewById(R.id.tableLayout);

        // Iterate through the available sensors, adding them to the array.
        mSensors = new ArrayList<MonitoredSensor>();
        SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        List<Sensor> sensors = sm.getSensorList(Sensor.TYPE_ALL);
        int cur_index = 0;
        for (int n = 0; n < sensors.size(); n++) {
            Sensor avail_sensor = sensors.get(n);

            // There can be multiple sensors of the same type. We need only one.
            if (!isSensorTypeAlreadyMonitored(avail_sensor.getType())) {
                // The first sensor we've got for the given type is not
                // necessarily the right one. So, use the default sensor
                // for the given type.
                Sensor def_sens = sm.getDefaultSensor(avail_sensor.getType());
                MonitoredSensor to_add = new MonitoredSensor(def_sens);
                cur_index++;
                mSensors.add(to_add);
                Logv(String.format("Monitoring sensor #%02d: Name = '%s', Type = 0x%x",
                        cur_index, def_sens.getName(), def_sens.getType()));
            }
        }

        // Instantiate emulator connector.
        try {
            // Sensor emulator starts very early during emulator startup. So, as
            // discussed in comments to Emulator class, we must use synchronous
            // type of connection with the emulator.
            mEmulator = new Emulator(Emulator.SENSORS_PORT, EmulatorConnectionType.SYNC_CONNECTION,
                    this);
        } catch (IOException e) {
            Loge("Exception while creating server socket: " + e.getMessage());
            finish();
        }
    }

    /**
     * Sends sensor's event to the emulator.
     *
     * @param msg Sensor's event message.
     */
    public void sendSensorEvent(String msg) {
        mEmulator.sendNotification(msg);
    }

    /***************************************************************************
     * OnEmulatorListener implementation
     **************************************************************************/

    /**
     * Called when emulator is connected. NOTE: This method is called from the
     * I/O loop, so all communication with the emulator will be "on hold" until
     * this method returns.
     */
    @Override
    public void onEmulatorConnected() {
    }

    /**
     * Called when emulator is disconnected.
     */
    @Override
    public void onEmulatorDisconnected() {
        // Stop listening to sensors, and let it cool for a sec...
        stopSensors();
        try {
            Thread.sleep(500);
        } catch (Exception e) {
        }

        // Instantiate emulator connector for the next client.
        try {
            mEmulator = new Emulator(Emulator.SENSORS_PORT, EmulatorConnectionType.SYNC_CONNECTION,
                    this);
        } catch (IOException e) {
            Loge("Exception while creating server socket: " + e.getMessage());
            finish();
        }
    }

    /**
     * Called when a query is received from the emulator. NOTE: This method is
     * called from the I/O loop.
     *
     * @param query Name of the query received from the emulator. The allowed
     *            queries are: 'list' - Lists sensors that are monitored by this
     *            application. The application replies to this command with a
     *            string: 'List:<name1>\n<name2>\n...<nameN>\n\0" 'start' -
     *            Starts monitoring sensors. There is no reply for this command.
     *            'stop' - Stops monitoring sensors. There is no reply for this
     *            command. 'enable:<sensor|all> - Enables notifications for a
     *            sensor / all sensors. 'disable:<sensor|all> - Disables
     *            notifications for a sensor / all sensors.
     * @param param Query parameters.
     * @return Zero-terminated reply string. String must be formatted as such:
     *         "ok|ko[:reply data]"
     */
    @Override
    public String onEmulatorQuery(String query, String param) {
        if (query.contentEquals("list")) {
            return onQueryList();
        } else if (query.contentEquals("start")) {
            return onQueryStart();
        } else if (query.contentEquals("stop")) {
            return onQueryStop();
        } else if (query.contentEquals("enable")) {
            return onQueryEnable(param);
        } else if (query.contentEquals("disable")) {
            return onQueryDisable(param);
        } else {
            Loge("Unknown query " + query + "(" + param + ")");
            return "ko:Query is unknown\0";
        }
    }

    /***************************************************************************
     * Query handlers
     **************************************************************************/

    /**
     * Handles 'list' query.
     *
     * @return List of emulator-friendly names for sensors that are available on
     *         the device.
     */
    private String onQueryList() {
        // List monitored sensors.
        String list = "ok:";
        for (int n = 0; n < mSensors.size(); n++) {
            list += mSensors.get(n).getEmulatorFriendlyName();
            list += "\n";
        }
        list += '\0'; // Response must end with zero-terminator.
        return list;
    }

    /**
     * Handles 'start' query.
     *
     * @return Empty string. This is a "command" query that doesn't assume any
     *         response.
     */
    private String onQueryStart() {
        startSensors();
        return "ok\0";
    }

    /**
     * Handles 'stop' query.
     *
     * @return Empty string. This is a "command" query that doesn't assume any
     *         response.
     */
    private String onQueryStop() {
        stopSensors();
        return "ok\0";
    }

    /**
     * Handles 'enable' query.
     *
     * @param param Sensor selector: - all Enables all available sensors, or -
     *            <name> Emulator-friendly name of a sensor to enable.
     * @return "ok" / "ko": success / failure.
     */
    private String onQueryEnable(String param) {
        if (param.contentEquals("all")) {
            // Enable all sensors.
            for (int n = 0; n < mSensors.size(); n++) {
                mSensors.get(n).enableSensor();
            }
            return "ok\0";
        }

        // Lookup sensor by emulator-friendly name.
        MonitoredSensor sensor = getSensorByEFN(param);
        if (sensor != null) {
            sensor.enableSensor();
            return "ok\0";
        } else {
            return "ko:Sensor not found\0";
        }
    }

    /**
     * Handles 'disable' query.
     *
     * @param param Sensor selector: - all Disables all available sensors, or -
     *            <name> Emulator-friendly name of a sensor to disable.
     * @return "ok" / "ko": success / failure.
     */
    private String onQueryDisable(String param) {
        if (param.contentEquals("all")) {
            // Disable all sensors.
            for (int n = 0; n < mSensors.size(); n++) {
                mSensors.get(n).disableSensor();
            }
            return "ok\0";
        }

        // Lookup sensor by emulator-friendly name.
        MonitoredSensor sensor = getSensorByEFN(param);
        if (sensor != null) {
            sensor.disableSensor();
            return "ok\0";
        } else {
            return "ko:Sensor not found\0";
        }
    }

    /***************************************************************************
     * Internals
     **************************************************************************/

    /**
     * Start listening to all monitored sensors.
     */
    private void startSensors() {
        for (int n = 0; n < mSensors.size(); n++) {
            mSensors.get(n).startListening();
        }
    }

    /**
     * Stop listening to all monitored sensors.
     */
    private void stopSensors() {
        for (int n = 0; n < mSensors.size(); n++) {
            mSensors.get(n).stopListening();
        }
    }

    /**
     * Checks if a sensor for the given type is already monitored.
     *
     * @param type Sensor type (one of the Sensor.TYPE_XXX constants)
     * @return true if a sensor for the given type is already monitored, or
     *         false if the sensor is not monitored.
     */
    private boolean isSensorTypeAlreadyMonitored(int type) {
        for (int n = 0; n < mSensors.size(); n++) {
            if (mSensors.get(n).getType() == type) {
                return true;
            }
        }
        return false;
    }

    /**
     * Looks up a monitored sensor by its emulator-friendly name.
     *
     * @param name Emulator-friendly name to look up the monitored sensor for.
     * @return Monitored sensor for the fiven name, or null if sensor was not
     *         found.
     */
    private MonitoredSensor getSensorByEFN(String name) {
        for (int n = 0; n < mSensors.size(); n++) {
            MonitoredSensor sensor = mSensors.get(n);
            if (sensor.mEmulatorFriendlyName.contentEquals(name)) {
                return sensor;
            }
        }
        return null;
    }

    /***************************************************************************
     * Logging wrappers
     **************************************************************************/

    private void Loge(String log) {
        Log.e(TAG, log);
    }

    private void Logv(String log) {
        Log.v(TAG, log);
    }

} // SdkControllerSensor
