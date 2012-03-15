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

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

/**
 * The background service of the SdkController.
 * There can be only one instance of this.
 */
public class ControllerService extends Service {

    /*
     * Implementation reference:
     * http://developer.android.com/reference/android/app/Service.html#LocalServiceSample
     */

    public static String TAG = ControllerService.class.getSimpleName();
    private static boolean DEBUG = true;

    private static int NOTIF_ID = 'S' << 24 + 'd' << 16 + 'k' << 8 + 'C' << 0;

    private final IBinder mBinder = new ControllerBinder();

    private List<ControllerListener> mListeners = new ArrayList<ControllerListener>();

    /**
     * Whether the service is running. Set to true in onCreate, false in onDestroy.
     */
    private static volatile boolean gServiceIsRunning = false;

    /** Internal error reported by the service. */
    private String mSensorError = "";

    /**
     * Interface that the service uses to notify binded activities.
     * <p/>
     * As a design rule, implementations of this listener should be aware that most calls
     * will NOT happen on the UI thread. Any access to the UI should be properly protected
     * by using {@link Activity#runOnUiThread(Runnable)}.
     */
    public interface ControllerListener {
        /**
         * The error string reported by the service has changed. <br/>
         * Note this may be called from a thread different than the UI thread.
         * @param error The new error string.
         */
        void onErrorChanged(String error);
    }

    /** Interface that callers can use to access the service. */
    public class ControllerBinder extends Binder {

        /**
         * Adds a new listener that will be notified when the service state changes.
         *
         * @param listener A non-null listener. Ignored if already listed.
         */
        public void addListener(ControllerListener listener) {
            if (listener != null) {
                synchronized(mListeners) {
                    if (!mListeners.contains(listener)) {
                        mListeners.add(listener);
                    }
                }
            }
        }

        /**
         * Removes a listener.
         *
         * @param listener A listener to remove. Can be null.
         */
        public void removeListener(ControllerListener listener) {
            synchronized(mListeners) {
                mListeners.remove(listener);
            }
        }

        public String getSensorErrors() {
            return mSensorError;
        }
    }

    /**
     * Whether the service is running. Set to true in onCreate, false in onDestroy.
     */
    public static boolean isServiceIsRunning() {
        return gServiceIsRunning;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) Log.d(TAG, "Service onCreate");
        gServiceIsRunning = true;
        showNotification();
        onServiceStarted();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        if (DEBUG) Log.d(TAG, "Service onStartCommand");
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (DEBUG) Log.d(TAG, "Service onBind");
        return mBinder;
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "Service onDestroy");
        gServiceIsRunning = false;
        removeNotification();
        resetError();
        onServiceStopped();
        super.onDestroy();
    }

    // ------

    /**
     * Called when the service has been created.
     */
    private void onServiceStarted() {
        // TODO: add stuff to do when the service starts (e.g. activate sensors?)

        // Hack: just do see if this is working, change the error field for a little while.
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 1; i <= 5 && gServiceIsRunning; i++) {
                    SystemClock.sleep(1000); // 1s
                    resetError();
                    addError("Test msg from service thread " + i);
                }
                resetError();
            }
        });
        t.start();
    }

    /**
     * Called when the service is being destroyed.
     */
    private void onServiceStopped() {

        // TODO: add stuff to do when the service stops (e.g. release sensors?)
    }

    /**
     * Resets the error string and notify listeners.
     */
    private void resetError() {
        mSensorError = "";

        synchronized(mListeners) {
            for (ControllerListener listener : mListeners) {
                listener.onErrorChanged(mSensorError);
            }
        }
    }

    /**
     * An internal utility method to add a line to the error string and notify listeners.
     * @param error A non-null non-empty error line. \n will be added automatically.
     */
    private void addError(String error) {
        Log.e(TAG, error);
        if (mSensorError.length() > 0) {
            mSensorError += "\n";
        }
        mSensorError += error;

        synchronized(mListeners) {
            for (ControllerListener listener : mListeners) {
                listener.onErrorChanged(mSensorError);
            }
        }
    }

    /**
     * Displays a notification showing that the service is running.
     * When the user touches the notification, it opens the main activity
     * which allows the user to stop this service.
     */
    @SuppressWarnings("deprecated")
    private void showNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        String text = getString(R.string.service_notif_title);

        // Note: Notification is marked as deprecated -- in API 11+ there's a new Builder class
        // but we need to have API 7 compatibility so we ignore that warning.

        Notification n = new Notification(R.drawable.ic_launcher, text, System.currentTimeMillis());
        n.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                this,     //context
                0,        //requestCode
                intent,   //intent
                0         // pending intent flags
                );
        n.setLatestEventInfo(this, text, text, pi);

        nm.notify(NOTIF_ID, n);
    }

    private void removeNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.cancel(NOTIF_ID);
    }
}
