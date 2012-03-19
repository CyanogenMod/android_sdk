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

package com.android.tools.sdkcontroller.handlers;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;

import com.android.tools.sdkcontroller.lib.EmulatorConnection;
import com.android.tools.sdkcontroller.lib.EmulatorListener;
import com.android.tools.sdkcontroller.service.ControllerService;


/**
 * An abstract base class for all "action handlers".
 * <p/>
 * The {@link ControllerService} can deal with several handlers, each have a specific
 * purpose as described by {@link HandlerType}.
 */
public abstract class BaseHandler {

    /**
     * The type of action that this handler manages.
     */
    public enum HandlerType {
        /** A handler to send multitouch events from the device to the emulator and display
         *  the emulator screen on the device. */
        MultiTouch,
        /** A handler to send sensor events from the device to the emulaotr. */
        Sensor
    }

    /**
     * Returns the type of this handler.
     * @return One of the {@link HandlerType} values.
     */
    public abstract HandlerType getType();

    /**
     * The communication port used by this handler to communicate with the emulator.
     * <p/>
     * Note that right now we have 2 handlers that each use their own port. The goal is
     * to move to a single-connection mechanism where all the handlers' data will be
     * multiplexed on top of a single {@link EmulatorConnection}.
     *
     * @return A non-null port value.
     */
    public abstract int getPort();

    /**
     * Called once the {@link EmulatorConnection} has been successfully initialized.
     * <p/>
     * Note that this will <em>not</em> be called if the {@link EmulatorConnection}
     * fails to bind to the underlying socket.
     *
     * @param connection The connection that has just been created.
     *   A handler might want to use this to send data to the emulator via
     *   {@link EmulatorConnection#sendNotification(String)}. However handlers
     *   need to be particularly careful in <em>not</em> sending network data
     *   from the main UI thread.
     * @param context The controller service' context.
     */
    public abstract void onStart(EmulatorConnection connection, Context context);

    /**
     * Called once the {@link EmulatorConnection} is being disconnected.
     */
    public abstract void onStop();

    // ------------
    // Interaction from the emulator connection towards the handler

    /**
     * Emulator query being forwarded to the handler.
     *
     * @see EmulatorListener#onEmulatorQuery(String, String)
     */
    public abstract String onEmulatorQuery(String query, String param);

    /**
     * Emulator blob query being forwarded to the handler.
     *
     * @see EmulatorListener#onEmulatorBlobQuery(byte[])
     */
    public abstract String onEmulatorBlobQuery(byte[] array);

    // ------------
    // Interaction from handler towards listening UI

    /**
     * Interface to be implemented by activities that use this handler.
     * This is used by the handler to send event data to any UI.
     */
    public interface UiListener {
        public void onHandlerEvent(int event, Object...params);
    }

    private final List<UiListener> mUiListeners = new ArrayList<UiListener>();

    /**
     * Registers a new UI listener.
     *
     * @param listener A non-null UI listener to register.
     *   Ignored if the listener is null or already registered.
     */
    public void addUiListener(UiListener listener) {
        assert listener != null;
        if (listener != null) {
            if (!mUiListeners.contains(listener)) {
                mUiListeners.add(listener);
            }
        }
    }


    /**
     * Unregisters an UI listener.
     *
     * @param listener A non-null UI listener to unregister.
     *   Ignored if the listener is null or already registered.
     */
    public void removeUiListener(UiListener listener) {
        assert listener != null;
        mUiListeners.remove(listener);
    }

    /**
     * Protected method to be used by handlers to send an event to any listening UI.
     *
     * @param event The event code. To be defined by the handler itself.
     * @param params Any parameters that the handler defines for this event.
     */
    protected void notifyUi(int event, Object...params) {
        for (UiListener listener : mUiListeners) {
            listener.onHandlerEvent(event, params);
        }
    }

}
