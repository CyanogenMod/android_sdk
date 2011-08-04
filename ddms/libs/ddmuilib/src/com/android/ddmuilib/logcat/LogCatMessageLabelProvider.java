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

import com.android.ddmlib.Log.LogLevel;

import org.eclipse.jface.viewers.ITableColorProvider;
import org.eclipse.jface.viewers.ITableFontProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;

/**
 * A JFace label provider for the LogCat log messages. It expects elements of type
 * {@link LogCatMessage}.
 */
public final class LogCatMessageLabelProvider extends LabelProvider
                    implements ITableLabelProvider, ITableColorProvider, ITableFontProvider {
    /* Default Colors for different log levels. */
    private static final Color INFO_MSG_COLOR =    new Color(null, 0, 127, 0);
    private static final Color DEBUG_MSG_COLOR =   new Color(null, 0, 0, 127);
    private static final Color ERROR_MSG_COLOR =   new Color(null, 255, 0, 0);
    private static final Color WARN_MSG_COLOR =    new Color(null, 255, 127, 0);
    private static final Color VERBOSE_MSG_COLOR = new Color(null, 0, 0, 0);

    private int mWrapWidth;
    private Font mLogFont;

    /**
     * Construct a label provider that will wrap lines of length > wrapWidth.
     * @param wrapWidth width at which to wrap lines
     */
    public LogCatMessageLabelProvider(int wrapWidth) {
        mWrapWidth = wrapWidth;
    }

    public Image getColumnImage(Object element, int index) {
        return null;
    }

    /**
     * Obtain the correct text for the given column index. The index ordering
     * should match the ordering of the columns in the table created in {@link LogCatPanel}.
     * @return text to be used for specific column index
     */
    public String getColumnText(Object element, int index) {
        if (!(element instanceof LogCatMessage)) {
            return null;
        }

        LogCatMessage m = (LogCatMessage) element;
        switch (index) {
            case 0:
                return Character.toString(m.getLogLevel().getPriorityLetter());
            case 1:
                return m.getTime();
            case 2:
                return m.getPidString();
            case 3:
                return m.getTag();
            case 4:
                String msg = m.getMessage();
                if (msg.length() < mWrapWidth) {
                    return msg;
                }
                return wrapMessage(msg, mWrapWidth);
            default:
                return null;
        }
    }

    /**
     * Wrap a string into multiple lines if it exceeds a certain width.
     * @param msg message string to line wrap
     * @param width width at which to wrap
     * @return line with newline's inserted
     */
    private String wrapMessage(String msg, int width) {
        StringBuffer sb = new StringBuffer();

        int offset = 0;
        int len = msg.length();

        while (len > 0) {
            if (offset != 0) {
                /* for all lines but the first one, add a newline and
                 * two spaces at the beginning. */
                sb.append("\n  ");
            }

            int copylen = Math.min(width, len);
            sb.append(msg.substring(offset, offset + copylen));

            offset += copylen;
            len -= copylen;
        }

        return sb.toString();
    }

    public Color getBackground(Object element, int index) {
        return null;
    }

    /**
     * Get the foreground text color for given table item and column. The color
     * depends only on the log level, and the same color is used in all columns.
     */
    public Color getForeground(Object element, int index) {
        if (!(element instanceof LogCatMessage)) {
            return null;
        }

        LogCatMessage m = (LogCatMessage) element;
        LogLevel l = m.getLogLevel();

        if (l.equals(LogLevel.VERBOSE)) {
            return VERBOSE_MSG_COLOR;
        } else if (l.equals(LogLevel.INFO)) {
            return INFO_MSG_COLOR;
        } else if (l.equals(LogLevel.DEBUG)) {
            return DEBUG_MSG_COLOR;
        } else if (l.equals(LogLevel.ERROR)) {
            return ERROR_MSG_COLOR;
        } else if (l.equals(LogLevel.WARN)) {
            return WARN_MSG_COLOR;
        }

        return null;
    }

    public Font getFont(Object element, int index) {
        if (mLogFont == null) {
            /* FIXME: this should be obtained from preference settings. */
            mLogFont = new Font(Display.getDefault(),
                    new FontData("Courier New", 10, SWT.NORMAL));
        }
        return mLogFont;
    }
}
