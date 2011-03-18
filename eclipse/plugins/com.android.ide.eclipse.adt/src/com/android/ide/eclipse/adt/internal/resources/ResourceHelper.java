/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ide.eclipse.adt.internal.resources;

import com.android.ide.eclipse.adt.internal.editors.IconFactory;
import com.android.ide.eclipse.adt.internal.resources.configurations.CountryCodeQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.DockModeQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.KeyboardStateQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.LanguageQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.NavigationMethodQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.NavigationStateQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.NetworkCodeQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.NightModeQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.PixelDensityQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.RegionQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.ResourceQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.ScreenDimensionQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.ScreenOrientationQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.ScreenRatioQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.ScreenSizeQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.TextInputMethodQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.TouchScreenQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.VersionQualifier;

import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.swt.graphics.Image;

import java.util.HashMap;
import java.util.Map;

/**
 * Helper class to deal with SWT specifics for the resources.
 */
public class ResourceHelper {

    private final static Map<Class<?>, Image> ICON_MAP = new HashMap<Class<?>, Image>(20);

    /**
     * Returns the icon for the qualifier.
     */
    public static Image getIcon(Class<? extends ResourceQualifier> theClass) {
        Image image = ICON_MAP.get(theClass);
        if (image == null) {
            image = computeImage(theClass);
            ICON_MAP.put(theClass, image);
        }

        return image;
    }

    private static Image computeImage(Class<? extends ResourceQualifier> theClass) {
        if (theClass == CountryCodeQualifier.class) {
            return IconFactory.getInstance().getIcon("mcc"); //$NON-NLS-1$
        } else if (theClass == NetworkCodeQualifier.class) {
            return IconFactory.getInstance().getIcon("mnc"); //$NON-NLS-1$
        } else if (theClass == LanguageQualifier.class) {
            return IconFactory.getInstance().getIcon("language"); //$NON-NLS-1$
        } else if (theClass == RegionQualifier.class) {
            return IconFactory.getInstance().getIcon("region"); //$NON-NLS-1$
        } else if (theClass == ScreenSizeQualifier.class) {
            return IconFactory.getInstance().getIcon("size"); //$NON-NLS-1$
        } else if (theClass == ScreenRatioQualifier.class) {
            return IconFactory.getInstance().getIcon("ratio"); //$NON-NLS-1$
        } else if (theClass == ScreenOrientationQualifier.class) {
            return IconFactory.getInstance().getIcon("orientation"); //$NON-NLS-1$
        } else if (theClass == DockModeQualifier.class) {
            return IconFactory.getInstance().getIcon("dockmode"); //$NON-NLS-1$
        } else if (theClass == NightModeQualifier.class) {
            return IconFactory.getInstance().getIcon("nightmode"); //$NON-NLS-1$
        } else if (theClass == PixelDensityQualifier.class) {
            return IconFactory.getInstance().getIcon("dpi"); //$NON-NLS-1$
        } else if (theClass == TouchScreenQualifier.class) {
            return IconFactory.getInstance().getIcon("touch"); //$NON-NLS-1$
        } else if (theClass == KeyboardStateQualifier.class) {
            return IconFactory.getInstance().getIcon("keyboard"); //$NON-NLS-1$
        } else if (theClass == TextInputMethodQualifier.class) {
            return IconFactory.getInstance().getIcon("text_input"); //$NON-NLS-1$
        } else if (theClass == NavigationStateQualifier.class) {
            return IconFactory.getInstance().getIcon("navpad"); //$NON-NLS-1$
        } else if (theClass == NavigationMethodQualifier.class) {
            return IconFactory.getInstance().getIcon("navpad"); //$NON-NLS-1$
        } else if (theClass == ScreenDimensionQualifier.class) {
            return IconFactory.getInstance().getIcon("dimension"); //$NON-NLS-1$
        } else if (theClass == VersionQualifier.class) {
            return IconFactory.getInstance().getIcon("version"); //$NON-NLS-1$
        }

        // this can only happen if we forget to add a class above.
        return null;
    }

    /**
     * Returns a {@link ResourceDeltaKind} from an {@link IResourceDelta} value.
     * @param kind a {@link IResourceDelta} integer constant.
     * @return a matching {@link ResourceDeltaKind} or null.
     *
     * @see IResourceDelta#ADDED
     * @see IResourceDelta#REMOVED
     * @see IResourceDelta#CHANGED
     */
    public static ResourceDeltaKind getResourceDeltaKind(int kind) {
        switch (kind) {
            case IResourceDelta.ADDED:
                return ResourceDeltaKind.ADDED;
            case IResourceDelta.REMOVED:
                return ResourceDeltaKind.REMOVED;
            case IResourceDelta.CHANGED:
                return ResourceDeltaKind.CHANGED;
        }

        return null;
    }
}
