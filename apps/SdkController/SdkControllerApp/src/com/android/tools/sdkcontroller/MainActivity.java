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

package com.android.tools.sdkcontroller;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.android.tools.sdkcontroller.ControllerService.ControllerBinder;
import com.android.tools.sdkcontroller.ControllerService.ControllerListener;

public class MainActivity extends Activity {

    public static String TAG = MainActivity.class.getSimpleName();
    private static boolean DEBUG = true;
    private Button mBtnOpenMultitouch;
    private Button mBtnOpenSensors;
    private ToggleButton mBtnToggleService;
    private ServiceConnection mServiceConnection;
    protected ControllerBinder mServiceBinder;
    private TextView mTextError;
    private TextView mTextStatus;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mTextError  = (TextView) findViewById(R.id.textError);
        mTextStatus = (TextView) findViewById(R.id.textStatus);

        setupButtons();
    }

    @Override
    protected void onResume() {
        if (DEBUG) Log.d(TAG, "onResume");
        super.onResume();
        bindToService();
        updateError();
    }

    @Override
    protected void onPause() {
        if (DEBUG) Log.d(TAG, "onPause");
        super.onPause();
        // On pause we unbind but don't stop -- this is the case when the users goes home
        // or invokes any other activity, including our owns.
        boolean isRunning = mServiceBinder != null;
        unbindFromService();
    }

    @Override
    public void onBackPressed() {
        if (DEBUG) Log.d(TAG, "onBackPressed");
        // If back is pressed, we stop the service automatically. It seems more intuitive that way.
        stopService();
        super.onBackPressed();
    }

    // ----------

    private void setupButtons() {

        mBtnOpenMultitouch = (Button) findViewById(R.id.btnOpenMultitouch);
        mBtnOpenSensors    = (Button) findViewById(R.id.btnOpenSensors);

        mBtnOpenMultitouch.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // Open the multi-touch activity.
                Intent i = new Intent(MainActivity.this, MultitouchActivity.class);
                startActivity(i);
            }
        });

        mBtnOpenSensors.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // Open the sensor activity.
                Intent i = new Intent(MainActivity.this, SensorActivity.class);
                startActivity(i);
            }
        });

        mBtnToggleService = (ToggleButton) findViewById(R.id.toggleService);

        // set initial state
        updateButtons();

        mBtnToggleService.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    bindToService();
                } else {
                    stopService();
                }
            }
        });

    }

    private void updateButtons() {
        boolean running = ControllerService.isServiceIsRunning();
        mBtnOpenMultitouch.setEnabled(running);
        mBtnOpenSensors.setEnabled(running);
        mBtnToggleService.setChecked(running);

        mTextStatus.setText(
                getText(running ? R.string.main_service_status_running
                                : R.string.main_service_status_stopped));
    }

    /**
     * Starts the service and binds to it.
     */
    private void bindToService() {
        if (mServiceConnection == null) {
            final ControllerListener listener = new OurControllerListener();

            mServiceConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    if (DEBUG) Log.d(TAG, "Activity connected to service");
                    mServiceBinder = (ControllerBinder) service;
                    mServiceBinder.addListener(listener);
                    updateButtons();
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    if (DEBUG) Log.d(TAG, "Activity disconnected from service");
                    mServiceBinder = null;
                    updateButtons();
                }
            };
        }

        // Start service so that it doesn't stop when we unbind
        if (DEBUG) Log.d(TAG, "start requested & bind service");
        Intent service = new Intent(this, ControllerService.class);
        startService(service);
        bindService(service,
                mServiceConnection,
                Context.BIND_AUTO_CREATE);
    }

    /**
     * Unbinds from the service but does not actually stop the service.
     * This lets us have it run in the background even if this isn't the active app.
     */
    private void unbindFromService() {
        if (mServiceConnection != null) {
            if (DEBUG) Log.d(TAG, "unbind service");
            unbindService(mServiceConnection);
            mServiceConnection = null;
        }
    }

    /**
     * Unbind and then actually stops the service.
     */
    private void stopService() {
        Intent service = new Intent(this, ControllerService.class);
        unbindFromService();
        if (DEBUG) Log.d(TAG, "stop service requested");
        stopService(service);
    }

    private class OurControllerListener implements ControllerListener {
        @Override
        public void onErrorChanged(String error) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateError();
                }
            });
        }
    }

    private void updateError() {
        String error = mServiceBinder == null ? "" : mServiceBinder.getSensorErrors();
        if (error == null) {
            error = "";
        }

        mTextError.setVisibility(error.length() == 0 ? View.GONE : View.VISIBLE);
        mTextError.setText(error);
    }
}