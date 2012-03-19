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

import com.android.tools.sdkcontroller.handlers.MultitouchHandler;

import android.app.Activity;
import android.os.Bundle;

/**
 * Activity that controls and displays the {@link MultitouchHandler}.
 */
public class MultitouchActivity extends Activity {

    public static String TAG = MultitouchActivity.class.getSimpleName();
    @SuppressWarnings("unused")
    private static boolean DEBUG = true;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //TODO setContentView(R.layout.multitouch);
    }
}
