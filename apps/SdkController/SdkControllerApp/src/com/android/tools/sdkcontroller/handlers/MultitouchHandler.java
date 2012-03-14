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

import android.content.Context;

import com.android.tools.sdkcontroller.lib.EmulatorConnection;


public class MultitouchHandler extends BaseHandler {

    @Override
    public HandlerType getType() {
        return HandlerType.MultiTouch;
    }

    @Override
    public int getPort() {
        return EmulatorConnection.MULTITOUCH_PORT;
    }

    @Override
    public void onStart(EmulatorConnection connection, Context context) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onStop() {
        // TODO Auto-generated method stub

    }

    @Override
    public String onEmulatorQuery(String query, String param) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String onEmulatorBlobQuery(byte[] array) {
        // TODO Auto-generated method stub
        return null;
    }

}
