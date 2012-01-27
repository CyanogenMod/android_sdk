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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.WindowManager;

import com.android.tools.sdkcontroller.lib.Emulator;
import com.android.tools.sdkcontroller.lib.Emulator.EmulatorConnectionType;
import com.android.tools.sdkcontroller.lib.OnEmulatorListener;

/**
 * Encapsulates an application that monitors multi-touch activities on a device,
 * and reports them to an Android Emulator application running on the host
 * machine. This application is used to provide a realistic multi-touch
 * emulation in Android Emulator.
 */
public class SdkControllerMultitouchActivity extends Activity implements OnEmulatorListener {
    /** Tag for logging messages. */
    private static final String TAG = "SdkControllerMultitouch";
    /** Received frame is JPEG image. */
    private static final int FRAME_JPEG = 1;
    /** Received frame is RGB565 bitmap. */
    private static final int FRAME_RGB565 = 2;
    /** Received frame is RGB888 bitmap. */
    private static final int FRAME_RGB888 = 3;

    /** TCP over USB connection to the emulator. */
    private Emulator mEmulator;
    /** View for this application. */
    private MultiTouchView mView;
    /** Listener to touch events. */
    private TouchListener mTouchListener;
    /** Width of the emulator's display. */
    private int mEmulatorWidth = 0;
    /** Height of the emulator's display. */
    private int mEmulatorHeight = 0;
    /** Bitmap storage. */
    private int[] mColors;

    /**
     * Implements OnTouchListener interface that receives touch screen events,
     * and reports them to the emulator application.
     */
    class TouchListener implements OnTouchListener {
        /**
         * Touch screen event handler.
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
                        mView.constructEventMessage(sb, event, n);
                    }
                    break;
                case MotionEvent.ACTION_DOWN:
                    sb.append("action=down");
                    mView.constructEventMessage(sb, event, action_pid_index);
                    break;
                case MotionEvent.ACTION_UP:
                    sb.append("action=up pid=").append(event.getPointerId(action_pid_index));
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    sb.append("action=pdown");
                    mView.constructEventMessage(sb, event, action_pid_index);
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
    } // TouchListener

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mView = (MultiTouchView) findViewById(R.id.imageView);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Instantiate emulator connector.
        try {
            mEmulator = new Emulator(Emulator.MULTITOUCH_PORT,
                    EmulatorConnectionType.SYNC_CONNECTION, this);
        } catch (IOException e) {
            Loge("Exception while creating server socket: " + e.getMessage());
            finish();
        }

        // Create listener for touch events.
        mTouchListener = new TouchListener();
    }

    /**
     * Updates application's screen accordingly to the emulator screen.
     *
     * @param e_width Width of the emulator screen.
     * @param e_height Height of the emulator screen.
     */
    private void updateDisplay(int e_width, int e_height) {
        if (e_width != mEmulatorWidth || e_height != mEmulatorHeight) {
            mEmulatorWidth = e_width;
            mEmulatorHeight = e_height;

            boolean rotateDisplay = false;
            int w = mView.getWidth();
            int h = mView.getHeight();
            if (w > h != e_width > e_height) {
                rotateDisplay = true;
                int tmp = w;
                w = h;
                h = tmp;
            }

            float dx = (float) w / (float) e_width;
            float dy = (float) h / (float) e_height;
            mView.setDxDy(dx, dy, rotateDisplay);
            Logv("Dispay updated: " + e_width + " x " + e_height +
                    " -> " + w + " x " + h + " ratio: " +
                    dx + " x " + dy);
        }
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
        Logv("Emulator is connected");
    }

    /**
     * Called when emulator is disconnected.
     */
    @Override
    public void onEmulatorDisconnected() {
        Logv("Emulator is disconnected.");
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
            Loge("Exception while recreating server socket: " + e.getMessage());
            finish();
        }
    }

    /**
     * Called when a query is received from the emulator. NOTE: This method is
     * called from the I/O loop.
     *
     * @param query Name of the query received from the emulator. The allowed
     *            queries are: - 'start' - Starts delivering touch screen events
     *            to the emulator. - 'stop' - Stops delivering touch screen
     *            events to the emulator.
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

    /**
     * Called when a BLOB query is received from the emulator.
     * <p/>
     * This query is used to deliver framebuffer updates in the emulator. The
     * blob contains an update header, followed by the bitmap containing updated
     * rectangle. The header is defined as MTFrameHeader structure in
     * external/qemu/android/multitouch-port.h
     * <p/>
     * NOTE: This method is called from the I/O loop, so all communication with
     * the emulator will be "on hold" until this method returns.
     *
     * @param array contains BLOB data for the query.
     * @return Empty string: this query doesn't require any response.
     */
    @Override
    public String onEmulatorBlobQuery(byte[] array) {
        final ByteBuffer bb = ByteBuffer.wrap(array);
        bb.order(ByteOrder.LITTLE_ENDIAN);

        // Read frame header.
        final int header_size = bb.getInt();
        final int disp_width = bb.getInt();
        final int disp_height = bb.getInt();
        final int x = bb.getInt();
        final int y = bb.getInt();
        final int w = bb.getInt();
        final int h = bb.getInt();
        final int bpl = bb.getInt();
        final int bpp = bb.getInt();
        final int format = bb.getInt();

        // Update application display.
        updateDisplay(disp_width, disp_height);

        mView.post(new Runnable() {
            @Override
            public void run() {
                if (format == FRAME_JPEG) {
                    /*
                     * Framebuffer is in JPEG format.
                     */

                    final ByteArrayInputStream jpg = new ByteArrayInputStream(bb.array());
                    // Advance input stream to JPEG image.
                    jpg.skip(header_size);
                    // Draw the image.
                    mView.drawJpeg(x, y, w, h, jpg);
                } else {
                    /*
                     * Framebuffer is in a raw RGB format.
                     */

                    final int pixel_num = h * w;
                    // Advance stream to the beginning of framebuffer data.
                    bb.position(header_size);

                    // Make sure that mColors is large enough to contain the
                    // update bitmap.
                    if (mColors == null || mColors.length < pixel_num) {
                        mColors = new int[pixel_num];
                    }

                    // Convert the blob bitmap into bitmap that we will display.
                    if (format == FRAME_RGB565) {
                        for (int n = 0; n < pixel_num; n++) {
                            // Blob bitmap is in RGB565 format.
                            final int color = bb.getShort();
                            final int r = ((color & 0xf800) >> 8) | ((color & 0xf800) >> 14);
                            final int g = ((color & 0x7e0) >> 3) | ((color & 0x7e0) >> 9);
                            final int b = ((color & 0x1f) << 3) | ((color & 0x1f) >> 2);
                            mColors[n] = Color.rgb(r, g, b);
                        }
                    } else if (format == FRAME_RGB888) {
                        for (int n = 0; n < pixel_num; n++) {
                            // Blob bitmap is in RGB565 format.
                            final int r = bb.getChar();
                            final int g = bb.getChar();
                            final int b = bb.getChar();
                            mColors[n] = Color.rgb(r, g, b);
                        }
                    } else {
                        Logw("Invalid framebuffer format: " + format);
                        return;
                    }
                    mView.drawBitmap(x, y, w, h, mColors);
                }
            }
        });

        return "";
    }

    /***************************************************************************
     * Emulator query handlers
     **************************************************************************/

    /**
     * Handles 'start' query.
     *
     * @return 'ok:<WidthxHeight> on success, or 'ko:<reason>' on failure. Width
     *         and height returned on success represent width and height of the
     *         application view.
     */
    private String onQueryStart(String param) {
        // Lets see if query has parameters.
        int sep = param.indexOf('x');
        if (sep != -1) {
            final String dx = param.substring(0, sep);
            final String dy = param.substring(sep + 1);
            final int x = Integer.parseInt(dx);
            final int y = Integer.parseInt(dy);

            updateDisplay(x, y);
        }
        onStartEvents();
        return "ok:" + mView.getWidth() + "x" + mView.getHeight() + "\0";
    }

    /**
     * Handles 'stop' query.
     *
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
