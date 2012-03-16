/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.tools.sdkcontroller.activities;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.android.tools.sdkcontroller.R;
import com.android.tools.sdkcontroller.handlers.BaseHandler.HandlerType;
import com.android.tools.sdkcontroller.handlers.BaseHandler.UiListener;
import com.android.tools.sdkcontroller.handlers.SensorsHandler;
import com.android.tools.sdkcontroller.handlers.SensorsHandler.MonitoredSensor;
import com.android.tools.sdkcontroller.service.ControllerService.ControllerBinder;
import com.android.tools.sdkcontroller.service.ControllerService.ControllerListener;

/**
 * Activity that displays and controls the sensors from {@link SensorsHandler}.
 * For each sensor it displays a checkbox that is enabled if the sensor is supported
 * by the emulator. The user can select whether the sensor is active. It also displays
 * data from the sensor when available.
 */
public class SensorActivity extends BaseBindingActivity {

    @SuppressWarnings("hiding")
    public static String TAG = SensorActivity.class.getSimpleName();
    @SuppressWarnings("unused")
    private static boolean DEBUG = true;

    private TableLayout mTableLayout;
    private SensorsHandler mSensorHandler;
    private final OurUiListener mUiListener = new OurUiListener();
    private final Map<MonitoredSensor, DisplayInfo> mDisplayedSensors =
        new HashMap<SensorsHandler.MonitoredSensor, SensorActivity.DisplayInfo>();

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sensors);
        mTableLayout = (TableLayout) findViewById(R.id.tableLayout);

    }

    @Override
    protected void onResume() {
        // BaseBindingActivity.onResume will bind to the service.
        super.onResume();
    }

    @Override
    protected void onPause() {
        // BaseBindingActivity.onResume will unbind from (but not stop) the service.
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        removeSensorUi();
    }

    // ----------

    @Override
    protected void onServiceConnected() {
        createSensorUi();
    }

    @Override
    protected void onServiceDisconnected() {
        removeSensorUi();
    }

    @Override
    protected ControllerListener createControllerListener() {
        return new SensorsControllerListener();
    }

    // ----------

    private class SensorsControllerListener implements ControllerListener {
        @Override
        public void onErrorChanged() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //--updateError();
                }
            });
        }

        @Override
        public void onStatusChanged() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ControllerBinder binder = getServiceBinder();
                    mTableLayout.setEnabled(binder.isEmuConnected());
                }
            });
        }
    }

    private void createSensorUi() {
        final LayoutInflater inflater = getLayoutInflater();

        if (!mDisplayedSensors.isEmpty()) {
            removeSensorUi();
        }

        mSensorHandler = (SensorsHandler) getServiceBinder().getHandler(HandlerType.Sensor);
        if (mSensorHandler != null) {
            mSensorHandler.addUiListener(mUiListener);

            assert mDisplayedSensors.isEmpty();
            List<MonitoredSensor> sensors = mSensorHandler.getSensors();
            for (MonitoredSensor sensor : sensors) {
                final TableRow row = (TableRow) inflater.inflate(R.layout.sensor_row,
                                                                 mTableLayout,
                                                                 false);
                mTableLayout.addView(row);
                mDisplayedSensors.put(sensor, new DisplayInfo(sensor, row));
            }
        }
    }

    private void removeSensorUi() {
        mTableLayout.removeAllViews();
        mSensorHandler.removeUiListener(mUiListener);
        mSensorHandler = null;
        for (DisplayInfo info : mDisplayedSensors.values()) {
            info.release();
        }
        mDisplayedSensors.clear();
    }

    private class DisplayInfo implements CompoundButton.OnCheckedChangeListener {
        private MonitoredSensor mSensor;
        private CheckBox mChk;
        private TextView mVal;

        public DisplayInfo(MonitoredSensor sensor, TableRow row) {
            mSensor = sensor;

            // Initialize displayed checkbox for this sensor, and register
            // checked state listener for it.
            mChk = (CheckBox) row.findViewById(R.id.row_checkbox);
            mChk.setText(sensor.getUiName());
            mChk.setEnabled(sensor.isEnabledByEmulator());
            mChk.setChecked(sensor.isEnabledByUser());
            mChk.setOnCheckedChangeListener(this);

            // Initialize displayed text box for this sensor.
            mVal = (TextView) row.findViewById(R.id.row_textview);
            mVal.setText(sensor.getValue());
        }

        /**
         * Handles checked state change for the associated CheckBox. If check
         * box is checked we will register sensor change listener. If it is
         * unchecked, we will unregister sensor change listener.
         */
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (mSensor != null) {
                mSensor.onCheckedChanged(isChecked);
            }
        }

        public void release() {
            mChk = null;
            mVal = null;
            mSensor = null;

        }

        public void updateState() {
            if (mChk != null && mSensor != null) {
                mChk.setEnabled(mSensor.isEnabledByEmulator());
                mChk.setChecked(mSensor.isEnabledByUser());
            }
        }

        public void updateValue() {
            if (mVal != null && mSensor != null) {
                mVal.setText(mSensor.getValue());
            }
        }
    }

    private class OurUiListener implements UiListener {
        @Override
        public void onHandlerEvent(final int event, final Object... params) {
            // This is invoked from the emulator connection thread.
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    DisplayInfo info = null;
                    switch(event) {
                    case SensorsHandler.SENSOR_STATE_CHANGED:
                        info = mDisplayedSensors.get(params[0]);
                        if (info != null) {
                            info.updateState();
                        }
                        break;
                    case SensorsHandler.SENSOR_DISPLAY_MODIFIED:
                        info = mDisplayedSensors.get(params[0]);
                        if (info != null) {
                            info.updateValue();
                        }
                        break;
                    }
                }
            });
        }
    }
}
