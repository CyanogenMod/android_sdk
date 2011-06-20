
/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.chimpchat;

import com.android.chimpchat.adb.AdbBackend;
import com.android.chimpchat.core.IChimpBackend;

import java.util.Map;

/**
 * ChimpChat is a host-side library that provides an API for communication with
 * an instance of Monkey on a device. This class provides an entry point to
 * setting up communication with a device. Currently it only supports communciation
 * over ADB, however.
 */
public class ChimpChat {
    private final IChimpBackend mBackend;

    private ChimpChat(IChimpBackend backend) {
        this.mBackend = backend;
    }

    /**
     * Generates a new instance of ChimpChat based on the options passed.
     * @param options a map of settings for the new ChimpChat instance
     * @return a new instance of ChimpChat or null if there was an issue setting up the backend
     */
    public static ChimpChat getInstance(Map<String, String> options) {
        IChimpBackend backend = ChimpChat.createBackendByName(options.get("backend"));
        if (backend == null) {
            return null;
        }
        ChimpChat chimpchat = new ChimpChat(backend);
        return chimpchat;
    }


    public static IChimpBackend createBackendByName(String backendName) {
        if ("adb".equals(backendName)) {
            return new AdbBackend();
        } else {
            return null;
        }
    }
}
