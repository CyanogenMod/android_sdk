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
    private Location mSecondary;

    /**
     * Constructs a new location range for the given file, from start to end. If
     * the length of the range is not known, end may be null.
     *
     * @param file the associated file (but see the documentation for
     *            {@link #getFile()} for more information on what the file
     *            represents)
     * @param start the starting position, never null
     * @param end the ending position, or null
     */
    public Location(File file, Position start, Position end) {
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
}
