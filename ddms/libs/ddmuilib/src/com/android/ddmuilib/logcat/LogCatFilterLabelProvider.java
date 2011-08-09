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
package com.android.ddmuilib.logcat;

import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;

/**
 * A JFace label provider for the LogCat filters. It expects elements of type
 * {@link LogCatFilterSettings}.
 */
public final class LogCatFilterLabelProvider extends LabelProvider implements ITableLabelProvider {
    public Image getColumnImage(Object arg0, int arg1) {
        return null;
    }

    /**
     * Implements {@link ITableLabelProvider#getColumnText(Object, int)}.
     * @param element an instance of {@link LogCatFilterSettings}
     * @param index index of the column
     * @return text to use in the column
     */
    public String getColumnText(Object element, int index) {
        if (!(element instanceof LogCatFilterSettings)) {
            return null;
        }

        LogCatFilterSettings f = (LogCatFilterSettings) element;

        /* FIXME: Currently, only the name of the filter is displayed.
         * A future fix will also display the "unread count" associated
         * with each filter. */
        switch (index) {
            case 0:
                return f.getName();
            default:
                return "**";
        }
    }
}
