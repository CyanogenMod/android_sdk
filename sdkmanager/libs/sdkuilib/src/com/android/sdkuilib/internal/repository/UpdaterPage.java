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

package com.android.sdkuilib.internal.repository;

import com.android.sdklib.ISdkLog;

import org.eclipse.swt.widgets.Composite;

import java.lang.reflect.Constructor;



/**
 * Base class for pages shown in the updater.
 */
public abstract class UpdaterPage extends Composite {

    public enum Purpose {
        /** A generic page with is neither of the other specific purposes. */
        GENERIC,
        /** A page that displays the about box for the SDK Manager. */
        ABOUT_BOX,
        /** A page that displays the settings for the SDK Manager. */
        SETTINGS
    }

    public UpdaterPage(Composite parent, int swtStyle) {
        super(parent, swtStyle);
    }

    /**
     * The title of the page. Default is null.
     * <p/>
     * Useful for SdkManager1 when it displays a list of pages using
     * a vertical page selector.
     * Default implement for SdkManager2 is to return null.
     */
    public String getPageTitle() {
        return null;
    }

    public static UpdaterPage newInstance(
            Class<? extends UpdaterPage> clazz,
            Composite parent,
            int swtStyle,
            ISdkLog log) {

        try {
            Constructor<? extends UpdaterPage> cons =
                clazz.getConstructor(new Class<?>[] { Composite.class, int.class });

            return cons.newInstance(new Object[] { parent, swtStyle });

        } catch (NoSuchMethodException e) {
            // There is no such constructor.
            log.error(e,
                    "Failed to instanciate page %1$s. Constructor args must be (Composite,int).",
                    clazz.getSimpleName());

        } catch (Exception e) {
            // Log this instead of crashing the whole app.
            log.error(e,
                    "Failed to instanciate page %1$s.",
                    clazz.getSimpleName());
        }

        return null;
    }
}
