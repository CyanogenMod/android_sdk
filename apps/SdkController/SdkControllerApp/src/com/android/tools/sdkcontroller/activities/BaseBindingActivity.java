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

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import com.android.tools.sdkcontroller.service.ControllerService;
import com.android.tools.sdkcontroller.service.ControllerService.ControllerBinder;
import com.android.tools.sdkcontroller.service.ControllerService.ControllerListener;

/**
 * Base activity class that knows how to bind and unbind from the
 * {@link ControllerService}.
 */
public abstract class BaseBindingActivity extends Activity {

    public static String TAG = BaseBindingActivity.class.getSimpleName();
    private static boolean DEBUG = true;
    private ServiceConnection mServiceConnection;
    private ControllerBinder mServiceBinder;

    public ControllerBinder getServiceBinder() {
        return mServiceBinder;
    }

    @Override
    protected void onResume() {
        if (DEBUG) Log.d(TAG, "onResume");
        super.onResume();
        bindToService();
    }

    @Override
    protected void onPause() {
        if (DEBUG) Log.d(TAG, "onPause");
        super.onPause();
        unbindFromService();
    }

    // ----------

    protected abstract ControllerListener createControllerListener();
    protected abstract void onServiceConnected();
    protected abstract void onServiceDisconnected();

    /**
     * Starts the service and binds to it.
     */
    protected void bindToService() {
        if (mServiceConnection == null) {
            final ControllerListener listener = createControllerListener();

            mServiceConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    if (DEBUG) Log.d(TAG, "Activity connected to service");
                    mServiceBinder = (ControllerBinder) service;
                    mServiceBinder.addListener(listener);
                    BaseBindingActivity.this.onServiceConnected();
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    if (DEBUG) Log.d(TAG, "Activity disconnected from service");
                    mServiceBinder = null;
                    BaseBindingActivity.this.onServiceDisconnected();
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
    protected void unbindFromService() {
        if (mServiceConnection != null) {
            if (DEBUG) Log.d(TAG, "unbind service");
            unbindService(mServiceConnection);
            mServiceConnection = null;
        }
    }
}