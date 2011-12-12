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

package com.android.tools.sdkcontroller.sdkcontrollermultitouch;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;

import com.android.tools.sdkcontroller.lib.Emulator;
import com.android.tools.sdkcontroller.lib.Emulator.EmulatorConnectionType;
import com.android.tools.sdkcontroller.lib.OnEmulatorListener;

import java.io.IOException;

/**
 * Encapsulates an application that monitors multi-touch activities on a device,
 * and reports them to an Android Emulator application running on the host
 * machine. This application is used to provide a realistic multi-touch emulation
 * in Android Emulator.
 */
public class SdkControllerMultitouchActivity extends Activity implements OnEmulatorListener {
    /** Tag for logging messages. */
    private static final String TAG = "SdkControllerMultitouch";

    /** TCP over USB connection to the emulator. */
    private Emulator mEmulator;
    /** View for this application. */
    private View mView;
    /** Listener to touch events. */
    private TouchListener mTouchListener;
    /** Multiplier for an X coordinate of a pointer. */
    private float mDx = 1;
    /** Multiplier for a Y coordinate of a pointer. */
    private float mDy = 1;

    /**
     * Implements OnTouchListener interface that receives touch screen events,
     * and reports them to the emulator application.
     */
    class TouchListener implements OnTouchListener {
        /**
         * Touchscreen event handler.
         */
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            StringBuilder sb = new StringBuilder();
            final int action = event.getAction();
            final int action_code = action & MotionEvent.ACTION_MASK;
            final int action_pid_index = action >> MotionEvent.ACTION_POINTER_ID_SHIFT;

            /*
             * Build message for the emulator.
             */

            switch (action_code) {
                case MotionEvent.ACTION_MOVE:
                    sb.append("action=move");
                    for (int n = 0; n < event.getPointerCount(); n++) {
                        constructEventMessage(sb, event, n);
                    }
                    break;
                case MotionEvent.ACTION_DOWN:
                    sb.append("action=down");
                    constructEventMessage(sb, event, action_pid_index);
                    break;
                case MotionEvent.ACTION_UP:
                    sb.append("action=up pid=").append(event.getPointerId(action_pid_index));
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    sb.append("action=pdown");
                    constructEventMessage(sb, event, action_pid_index);
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                    sb.append("action=pup pid=").append(event.getPointerId(action_pid_index));
                    break;
                default:
                    Logw("Unknown action type: " + action_code);
                    return true;
            }

            Logv(sb.toString());
            mEmulator.sendNotification(sb.toString() + '\0');
            return true;
        }

        /**
         * Constructs touch event message to be send to emulator.
         *<p/>
         * @param sb String builder where to construct the message.
         * @param event Event for which to construct the message.
         * @param ptr_index Index of the motion pointer for which to construct
         *            the message.
         */
        private void constructEventMessage(StringBuilder sb, MotionEvent event, int ptr_index) {
            sb.append(" pid=").append(event.getPointerId(ptr_index));
            sb.append(" x=").append((int) (mDx * event.getX(ptr_index)));
            sb.append(" y=").append((int) (mDy * event.getY(ptr_index)));
            sb.append(" pressure=").append((int) event.getPressure(ptr_index));
        }
    } // TouchListener

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // Instantiate emulator connector.
        try {
            mEmulator = new Emulator(Emulator.MULTITOUCH_PORT,
                    EmulatorConnectionType.SYNC_CONNECTION, this);
        } catch (IOException e) {
            Loge("Exception while creating server socket: " + e.getMessage());
            finish();
        }

        // Create listener for touch events.
        mView = findViewById(R.id.imageView);
        mTouchListener = new TouchListener();
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
        // Stop listening on events, and let it cool for a sec...
        onStopEvents();
        try {
            Thread.sleep(500);
        } catch (Exception e) {
        }

        // Instantiate emulator connector for the next client.
        try {
            mEmulator = new Emulator(Emulator.MULTITOUCH_PORT,
                    EmulatorConnectionType.SYNC_CONNECTION, this);
        } catch (IOException e) {
            Loge("Exception while creating server socket: " + e.getMessage());
            finish();
        }
    }

    /**
     * Called when a query is received from the emulator. NOTE: This method is
     * called from the I/O loop.
     *<p/>
     * @param query Name of the query received from the emulator. The allowed
     *            queries are:
     *          - 'start' - Starts delivering touch screen events to the emulator.
     *          - 'stop' - Stops delivering touch screen events to the emulator.
     * @param param Query parameters.
     * @return Zero-terminated reply string. String must be formatted as such:
     *         "ok|ko[:reply data]"
     */
    @Override
    public String onEmulatorQuery(String query, String param) {
        if (query.contentEquals("start")) {
            return onQueryStart(param);
        } else if (query.contentEquals("stop")) {
            return onQueryStop();
        } else {
            Loge("Unknown query " + query + "(" + param + ")");
            return "ko:Unknown query\0";
        }
    }

    /***************************************************************************
     * Emulator query handlers
     **************************************************************************/

    /**
     * Handles 'start' query.
     *<p/>
     * @return 'ok:<WidthxHeight> on success, or 'ko:<reason>' on failure. Width
     *         and height returned on success represent width and height of the
     *         application view.
     */
    private String onQueryStart(String param) {
        // Lets see if query has parameters.
        int sep = param.indexOf('x');
        if (sep != -1) {
            String dx = param.substring(0, sep);
            String dy = param.substring(sep + 1);
            int x = Integer.parseInt(dx);
            int y = Integer.parseInt(dy);
            mDy = (float) y / (float) mView.getHeight();
            mDx = (float) x / (float) mView.getWidth();
            Logv("Emulator: " + x + "x" + y +
                    " to screen: " + mView.getWidth() + "x" + mView.getHeight() + " ratio: " +
                    mDx + "x" + mDy);
        }
        onStartEvents();
        return "ok:" + mView.getWidth() + "x" + mView.getHeight() + "\0";
    }

    /**
     * Handles 'stop' query.
     *<p/>
     * @return 'ok'.
     */
    private String onQueryStop() {
        onStopEvents();
        return "ok\0";
    }

    /***************************************************************************
     * Internals
     **************************************************************************/

    /**
     * Registers touch screen event listener, and starts receiving touch screen
     * events.
     */
    private void onStartEvents() {
        mView.post(new Runnable() {
            @Override
            public void run() {
                mView.setOnTouchListener(mTouchListener);
            }
        });
    }

    /**
     * Unregisters touch screen event listener, and stops receiving touch screen
     * events.
     */
    private void onStopEvents() {
        mView.post(new Runnable() {
            @Override
            public void run() {
                mView.setOnTouchListener(null);
            }
        });
    }

    /***************************************************************************
     * Logging wrappers
     **************************************************************************/

    private void Loge(String log) {
        Log.e(TAG, log);
    }

    private void Logw(String log) {
        Log.w(TAG, log);
    }

    private void Logv(String log) {
        Log.v(TAG, log);
    }
}
