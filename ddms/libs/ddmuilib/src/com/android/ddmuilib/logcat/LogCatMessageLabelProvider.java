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
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Table;

/**
 * A JFace label provider for the LogCat log messages. It expects elements of type
 * {@link LogCatMessage}.
 */
public final class LogCatMessageLabelProvider extends LabelProvider
                    implements ITableLabelProvider, ITableColorProvider, ITableFontProvider {
    private static final int INDEX_LOGLEVEL = 0;
    private static final int INDEX_LOGTIME = 1;
    private static final int INDEX_PID = 2;
    private static final int INDEX_TAG = 3;
    private static final int INDEX_TEXT = 4;

    /* Default Colors for different log levels. */
    private static final Color INFO_MSG_COLOR =    new Color(null, 0, 127, 0);
    private static final Color DEBUG_MSG_COLOR =   new Color(null, 0, 0, 127);
    private static final Color ERROR_MSG_COLOR =   new Color(null, 255, 0, 0);
    private static final Color WARN_MSG_COLOR =    new Color(null, 255, 127, 0);
    private static final Color VERBOSE_MSG_COLOR = new Color(null, 0, 0, 0);

    /** Default width to assume for the logcat message column when not able to
     * detect it from the table itself. */
    private static final int DEFAULT_TEXT_COL_WIDTH = 1000;

    private Font mLogFont;

    private Table mTable;
    private int mWrapWidth;
    private int mColWidth = DEFAULT_TEXT_COL_WIDTH;
    private int mFontWidth;

    /**
     * Construct a label provider that will be used in rendering the given table.
     * The table argument is provided so that the label provider can figure out the width
     * of the columns, and wrap messages if necessary.
     * @param font default font to use
     * @param parentTable table for which labels are provided
     * @param table
     */
    public LogCatMessageLabelProvider(Font font, Table parentTable) {
        mLogFont = font;
        mTable = parentTable;

        addTextColumnWidthChangeListener();

        mColWidth = getColumnWidth();
        mFontWidth = getFontWidth();
        updateWrapWidth();
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
            case INDEX_LOGLEVEL:
                return Character.toString(m.getLogLevel().getPriorityLetter());
            case INDEX_LOGTIME:
                return m.getTime();
            case INDEX_PID:
                return m.getPidString();
            case INDEX_TAG:
                return m.getTag();
            case INDEX_TEXT:
                String msg = m.getMessage();
                if (msg.length() < mWrapWidth) {
                    return msg;
                }
                return wrapMessage(msg);
            default:
                return null;
        }
    }

    /**
     * Wrap logcat message into multiple lines if it exceeds the current width of the text column.
     * @param msg message string to line wrap
     * @return line with newline's inserted
     */
    private String wrapMessage(String msg) {
        StringBuffer sb = new StringBuffer();

        int offset = 0;
        int len = msg.length();

        while (len > 0) {
            if (offset != 0) {
                /* for all lines but the first one, add a newline and
                 * two spaces at the beginning. */
                sb.append("\n  ");
            }

            int copylen = Math.min(mWrapWidth, len);
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
        return mLogFont;
    }

    public void setFont(Font preferredFont) {
        if (mLogFont != null) {
            mLogFont.dispose();
        }

        mLogFont = preferredFont;
        mFontWidth = getFontWidth();
        updateWrapWidth();
    }

    private void addTextColumnWidthChangeListener() {
        mTable.getColumn(INDEX_TEXT).addControlListener(new ControlAdapter() {
            @Override
            public void controlResized(ControlEvent arg0) {
                mColWidth = getColumnWidth();
                updateWrapWidth();
            }
        });
    }

    /* # of chars in the column = width of column / width of each character in the font used */
    private void updateWrapWidth() {
        mWrapWidth = mColWidth / mFontWidth;
    }

    private int getFontWidth() {
        mTable.setFont(mLogFont);
        GC gc = new GC(mTable);
        try {
            return gc.getFontMetrics().getAverageCharWidth();
        } finally {
            gc.dispose();
        }
    }

    private int getColumnWidth() {
        int w = mTable.getColumn(INDEX_TEXT).getWidth();
        if (w != 0) {
            return w; /* return new width only if it is valid */
        } else {
            return mColWidth; /* otherwise stick to current width */
        }
    }
}
