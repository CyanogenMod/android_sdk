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



public abstract class BaseHandler {

    public enum HandlerType {
        MultiTouch,
        Sensor
    }

    public abstract HandlerType getType();

    public abstract int getPort();

    public abstract void onStart(EmulatorConnection connection, Context context);

    public abstract void onStop();

    // ------------
    // Interaction from the emulator connection towards the handler

    /**
     * Uses the first non-null replies to this query.
     * @see EmulatorListener#onEmulatorQuery(String, String)
     */
    public abstract String onEmulatorQuery(String query, String param);

    /**
     * Uses the first non-null replies to this query.
     * @see EmulatorListener#onEmulatorBlobQuery(byte[])
     */
    public abstract String onEmulatorBlobQuery(byte[] array);

    // ------------
    // Interaction from handler towards listening UI

    public interface UiListener {
        public void onHandlerEvent(int event, Object...params);
    }

    private final List<UiListener> mUiListeners = new ArrayList<UiListener>();

    public void addUiListener(UiListener listener) {
        assert listener != null;
        if (listener != null) {
            if (!mUiListeners.contains(listener)) {
                mUiListeners.add(listener);
            }
        }
    }

    public void removeUiListener(UiListener listener) {
        assert listener != null;
        mUiListeners.remove(listener);
    }

    protected void notifyUi(int event, Object...params) {
        for (UiListener listener : mUiListeners) {
            listener.onHandlerEvent(event, params);
        }
    }

}
