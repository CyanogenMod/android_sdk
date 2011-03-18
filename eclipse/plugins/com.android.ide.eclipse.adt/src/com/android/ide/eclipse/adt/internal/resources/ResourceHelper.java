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

import com.android.ide.common.resources.ResourceDeltaKind;
import com.android.ide.common.resources.configuration.CountryCodeQualifier;
import com.android.ide.common.resources.configuration.DockModeQualifier;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.KeyboardStateQualifier;
import com.android.ide.common.resources.configuration.LanguageQualifier;
import com.android.ide.common.resources.configuration.NavigationMethodQualifier;
import com.android.ide.common.resources.configuration.NavigationStateQualifier;
import com.android.ide.common.resources.configuration.NetworkCodeQualifier;
import com.android.ide.common.resources.configuration.NightModeQualifier;
import com.android.ide.common.resources.configuration.PixelDensityQualifier;
import com.android.ide.common.resources.configuration.RegionQualifier;
import com.android.ide.common.resources.configuration.ResourceQualifier;
import com.android.ide.common.resources.configuration.ScreenDimensionQualifier;
import com.android.ide.common.resources.configuration.ScreenOrientationQualifier;
import com.android.ide.common.resources.configuration.ScreenRatioQualifier;
import com.android.ide.common.resources.configuration.ScreenSizeQualifier;
import com.android.ide.common.resources.configuration.TextInputMethodQualifier;
import com.android.ide.common.resources.configuration.TouchScreenQualifier;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.ide.eclipse.adt.internal.editors.IconFactory;

import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.swt.graphics.Image;

import java.util.HashMap;
import java.util.Map;

/**
 * Helper class to deal with SWT specifics for the resources.
 */
public class ResourceHelper {

    private final static Map<Class<?>, Image> sIconMap = new HashMap<Class<?>, Image>(
            FolderConfiguration.getQualifierCount());

    static {
        IconFactory factory = IconFactory.getInstance();
        sIconMap.put(CountryCodeQualifier.class,       factory.getIcon("mcc")); //$NON-NLS-1$
        sIconMap.put(NetworkCodeQualifier.class,       factory.getIcon("mnc")); //$NON-NLS-1$
        sIconMap.put(LanguageQualifier.class,          factory.getIcon("language")); //$NON-NLS-1$
        sIconMap.put(RegionQualifier.class,            factory.getIcon("region")); //$NON-NLS-1$
        sIconMap.put(ScreenSizeQualifier.class,        factory.getIcon("size")); //$NON-NLS-1$
        sIconMap.put(ScreenRatioQualifier.class,       factory.getIcon("ratio")); //$NON-NLS-1$
        sIconMap.put(ScreenOrientationQualifier.class, factory.getIcon("orientation")); //$NON-NLS-1$
        sIconMap.put(DockModeQualifier.class,          factory.getIcon("dockmode")); //$NON-NLS-1$
        sIconMap.put(NightModeQualifier.class,         factory.getIcon("nightmode")); //$NON-NLS-1$
        sIconMap.put(PixelDensityQualifier.class,      factory.getIcon("dpi")); //$NON-NLS-1$
        sIconMap.put(TouchScreenQualifier.class,       factory.getIcon("touch")); //$NON-NLS-1$
        sIconMap.put(KeyboardStateQualifier.class,     factory.getIcon("keyboard")); //$NON-NLS-1$
        sIconMap.put(TextInputMethodQualifier.class,   factory.getIcon("text_input")); //$NON-NLS-1$
        sIconMap.put(NavigationStateQualifier.class,   factory.getIcon("navpad")); //$NON-NLS-1$
        sIconMap.put(NavigationMethodQualifier.class,  factory.getIcon("navpad")); //$NON-NLS-1$
        sIconMap.put(ScreenDimensionQualifier.class,   factory.getIcon("dimension")); //$NON-NLS-1$
        sIconMap.put(VersionQualifier.class,           factory.getIcon("version")); //$NON-NLS-1$
    }

    /**
     * Returns the icon for the qualifier.
     */
    public static Image getIcon(Class<? extends ResourceQualifier> theClass) {
        return sIconMap.get(theClass);
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
