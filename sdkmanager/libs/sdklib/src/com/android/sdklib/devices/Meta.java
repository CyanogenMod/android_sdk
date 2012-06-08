/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.sdklib.devices;

import java.awt.Point;
import java.io.File;

public class Meta {
    File mIconSixtyFour;
    File mIconSixteen;
    File mFrame;
    Point mFrameOffsetLandscape;
    Point mFrameOffsetPortrait;

    public File getIconSixtyFour() {
        return mIconSixtyFour;
    }

    public boolean hasIconSixtyFour() {
        if (mIconSixtyFour != null && mIconSixtyFour.isFile()) {
            return true;
        } else {
            return false;
        }
    }

    public File getIconSixteen() {
        return mIconSixteen;
    }

    public boolean hasIconSixteen() {
        if (mIconSixteen != null && mIconSixteen.isFile()) {
            return true;
        } else {
            return false;
        }
    }

    public File getFrame() {
        return mFrame;
    }

    public boolean hasFrame() {
        if (mFrame != null && mFrame.isFile()) {
            return true;
        } else {
            return false;
        }
    }

    public Point getFrameOffsetLandscape() {
        return mFrameOffsetLandscape;
    }

    public Point getFrameOffsetPortrait() {
        return mFrameOffsetPortrait;
    }
}
