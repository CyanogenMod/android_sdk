/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.hierarchyviewerlib.device;

import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;

public class HvDeviceFactory {
    private static final boolean ALWAYS_USE_VIEWSERVER = false; // for debugging

    public static IHvDevice create(IDevice device) {
        if (ALWAYS_USE_VIEWSERVER) {
            return new ViewServerDevice(device);
        }

        boolean ddmViewHierarchy = false;

        // see if any of the clients on the device support view hierarchy via DDMS
        for (Client c : device.getClients()) {
            ClientData cd = c.getClientData();
            if (cd != null && cd.hasFeature(ClientData.FEATURE_VIEW_HIERARCHY)) {
                ddmViewHierarchy = true;
                break;
            }
        }

        return ddmViewHierarchy ? new DdmViewDebugDevice(device) : new ViewServerDevice(device);
    }
}
