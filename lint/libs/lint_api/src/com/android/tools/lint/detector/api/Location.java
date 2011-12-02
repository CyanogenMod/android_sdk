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

package com.android.tools.lint.detector.api;

import java.io.File;

/**
 * Location information for a warning
 * <p/>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
public class Location {
    private final File mFile;
    private final Position mStart;
    private final Position mEnd;
    private String mMessage;
    private Location mSecondary;

    /**
     * (Private constructor, use one of the factory methods
     * {@link Location#create(File)},
     * {@link Location#create(File, Position, Position)}, or
     * {@link Location#create(File, String, int, int)}.
     * <p>
     * Constructs a new location range for the given file, from start to end. If
     * the length of the range is not known, end may be null.
     *
     * @param file the associated file (but see the documentation for
     *            {@link #getFile()} for more information on what the file
     *            represents)
     * @param start the starting position, never null
     * @param end the ending position, or null
     */
    protected Location(File file, Position start, Position end) {
        super();
        this.mFile = file;
        this.mStart = start;
        this.mEnd = end;
    }

    /**
     * Returns the file containing the warning. Note that the file *itself* may
     * not yet contain the error. When editing a file in the IDE for example,
     * the tool could generate warnings in the background even before the
     * document is saved. However, the file is used as a identifying token for
     * the document being edited, and the IDE integration can map this back to
     * error locations in the editor source code.
     *
     * @return the file handle for the location
     */
    public File getFile() {
        return mFile;
    }

    /**
     * The start position of the range
     *
     * @return the start position of the range, never null
     */
    public Position getStart() {
        return mStart;
    }

    /**
     * The end position of the range
     *
     * @return the start position of the range, may be null for an empty range
     */
    public Position getEnd() {
        return mEnd;
    }

    /**
     * Returns a secondary location associated with this location (if
     * applicable), or null.
     *
     * @return a secondary location or null
     */
    public Location getSecondary() {
        return mSecondary;
    }

    /**
     * Sets a secondary location for this location.
     *
     * @param secondary a secondary location associated with this location
     */
    public void setSecondary(Location secondary) {
        this.mSecondary = secondary;
    }

    /**
     * Sets a custom message for this location. This is typically used for
     * secondary locations, to describe the significance of this alternate
     * location. For example, for a duplicate id warning, the primary location
     * might say "This is a duplicate id", pointing to the second occurrence of
     * id declaration, and then the secondary location could point to the
     * original declaration with the custom message "Originally defined here".
     *
     * @param message the message to apply to this location
     */
    public void setMessage(String message) {
        mMessage = message;
    }

    /**
     * Returns the custom message for this location, if any. This is typically
     * used for secondary locations, to describe the significance of this
     * alternate location. For example, for a duplicate id warning, the primary
     * location might say "This is a duplicate id", pointing to the second
     * occurrence of id declaration, and then the secondary location could point
     * to the original declaration with the custom message
     * "Originally defined here".
     *
     * @return the custom message for this location, or null
     */
    public String getMessage() {
        return mMessage;
    }

    /**
     * Creates a new location for the given file
     *
     * @param file the file to create a location for
     * @return a new location
     */
    public static Location create(File file) {
        return new Location(file, null /*start*/, null /*end*/);
    }

    /**
     * Creates a new location for the given file and starting and ending
     * positions.
     *
     * @param file the file containing the positions
     * @param start the starting position
     * @param end the ending position
     * @return a new location
     */
    public static Location create(File file, Position start, Position end) {
        return new Location(file, start, end);
    }

    /**
     * Creates a new location for the given file, with the given contents, for
     * the given offset range.
     *
     * @param file the file containing the location
     * @param contents the current contents of the file
     * @param startOffset the starting offset
     * @param endOffset the ending offset
     * @return a new location
     */
    public static Location create(File file, String contents, int startOffset, int endOffset) {
        if (startOffset < 0 || endOffset < startOffset) {
            throw new IllegalArgumentException("Invalid offsets");
        }

        if (contents == null) {
            return new Location(file,
                    new DefaultPosition(-1, -1, startOffset),
                    new DefaultPosition(-1, -1, endOffset));
        }

        int size = contents.length();
        endOffset = Math.min(endOffset, size);
        startOffset = Math.min(startOffset, endOffset);
        Position start = null;
        int line = 0;
        int lineOffset = 0;
        for (int offset = 0; offset <= size; offset++) {
            if (offset == startOffset) {
                start = new DefaultPosition(line, offset - lineOffset, offset);
            }
            if (offset == endOffset) {
                Position end = new DefaultPosition(line, offset - lineOffset, offset);
                return new Location(file, start, end);
            }
            char c = contents.charAt(offset);
            if (c == '\n') {
                lineOffset = offset;
                line++;
            }
        }
        return Location.create(file);
    }

    /**
     * Creates a new location for the given file, with the given contents, for
     * the given line number.
     *
     * @param file the file containing the location
     * @param contents the current contents of the file
     * @param line the line number (0-based) for the position
     * @return a new location
     */
    public static Location create(File file, String contents, int line) {
        int currentLine = 0;
        int offset = 0;
        while (currentLine < line) {
            offset = contents.indexOf('\n', offset);
            if (offset == -1) {
                return Location.create(file);
            }
            currentLine++;
            offset++;
        }

        if (line == currentLine) {
            Position position = new DefaultPosition(line, -1, offset);
            return new Location(file, position, position);
        }

        return Location.create(file);
    }

    /**
     * A {@link Handle} is a reference to a location. The point of a location
     * handle is to be able to create them cheaply, and then resolve them into
     * actual locations later (if needed). This makes it possible to for example
     * delay looking up line numbers, for locations that are offset based.
     */
    public static interface Handle {
        /**
         * Compute a full location for the given handle
         *
         * @return create a location for this handle
         */
        Location resolve();
    }

    /** A default {@link Handle} implementation for simple file offsets */
    public static class DefaultLocationHandle implements Handle {
        private File mFile;
        private String mContents;
        private int mStartOffset;
        private int mEndOffset;

        /**
         * Constructs a new {@link DefaultLocationHandle}
         *
         * @param context the context pointing to the file and its contents
         * @param startOffset the start offset within the file
         * @param endOffset the end offset within the file
         */
        public DefaultLocationHandle(Context context, int startOffset, int endOffset) {
            mFile = context.file;
            mContents = context.getContents();
            mStartOffset = startOffset;
            mEndOffset = endOffset;
        }

        public Location resolve() {
            return Location.create(mFile, mContents, mStartOffset, mEndOffset);
        }
    }
}
