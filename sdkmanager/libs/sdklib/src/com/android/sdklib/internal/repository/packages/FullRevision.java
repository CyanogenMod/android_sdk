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

package com.android.sdklib.internal.repository.packages;


/**
 * Package multi-part revision number composed of a tuple
 * (major.minor.micro) and an optional preview revision
 * (the lack of a preview number indicates it's not a preview
 *  but a final package.)
 *
 *  @see MajorRevision
 */
public class FullRevision implements Comparable<FullRevision> {

    public static final int IMPLICIT_MINOR_REV = 0;
    public static final int IMPLICIT_MICRO_REV = 0;
    public static final int NOT_A_PREVIEW      = 0;

    private final int mMajor;
    private final int mMinor;
    private final int mMicro;
    private final int mPreview;

    public FullRevision(int major) {
        this(major, 0, 0);
    }

    public FullRevision(int major, int minor, int micro) {
        this(major, minor, micro, NOT_A_PREVIEW);
    }

    public FullRevision(int major, int minor, int micro, int preview) {
        mMajor = major;
        mMinor = minor;
        mMicro = micro;
        mPreview = preview;
    }

    public int getMajor() {
        return mMajor;
    }

    public int getMinor() {
        return mMinor;
    }

    public int getMicro() {
        return mMicro;
    }

    public boolean isPreview() {
        return mPreview > NOT_A_PREVIEW;
    }

    public int getPreview() {
        return mPreview;
    }

    /**
     * Returns the version in a fixed format major.minor.micro
     * with an optional "rc preview#". For example it would
     * return "18.0.0", "18.1.0" or "18.1.2 rc5".
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(mMajor)
          .append('.').append(mMinor)
          .append('.').append(mMicro);

        if (mPreview != NOT_A_PREVIEW) {
            sb.append(" rc").append(mPreview);
        }

        return sb.toString();
    }

    /**
     * Returns the version in a dynamic format "major.minor.micro rc#".
     * This is similar to {@link #toString()} except it omits minor, micro
     * or preview versions when they are zero.
     * For example it would return "18 rc1" instead of "18.0.0 rc1",
     * or "18.1 rc2" instead of "18.1.0 rc2".
     */
    public String toShortString() {
        StringBuilder sb = new StringBuilder();
        sb.append(mMajor);
        if (mMinor > 0 || mMicro > 0) {
            sb.append('.').append(mMinor);
        }
        if (mMicro > 0) {
            sb.append('.').append(mMicro);
        }
        if (mPreview != NOT_A_PREVIEW) {
            sb.append(" rc").append(mPreview);
        }

        return sb.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + mMajor;
        result = prime * result + mMinor;
        result = prime * result + mMicro;
        result = prime * result + mPreview;
        return result;
    }

    @Override
    public boolean equals(Object rhs) {
        if (this == rhs) {
            return true;
        }
        if (rhs == null) {
            return false;
        }
        if (!(rhs instanceof FullRevision)) {
            return false;
        }
        FullRevision other = (FullRevision) rhs;
        if (mMajor != other.mMajor) {
            return false;
        }
        if (mMinor != other.mMinor) {
            return false;
        }
        if (mMicro != other.mMicro) {
            return false;
        }
        if (mPreview != other.mPreview) {
            return false;
        }
        return true;
    }

    /**
     * Trivial comparison of a version, e.g 17.1.2 < 18.0.0.
     *
     * Note that preview/release candidate are released before their final version,
     * so "18.0.0 rc1" comes below "18.0.0". The best way to think of it as if the
     * lack of preview number was "+inf":
     * "18.1.2 rc5" => "18.1.2.5" so its less than "18.1.2.+INF" but more than "18.1.1.0"
     * and more than "18.1.2.4"
     */
    @Override
    public int compareTo(FullRevision rhs) {
        int delta = mMajor - rhs.mMajor;
        if (delta != 0) {
            return delta;
        }

        delta = mMinor - rhs.mMinor;
        if (delta != 0) {
            return delta;
        }

        delta = mMicro - rhs.mMicro;
        if (delta != 0) {
            return delta;
        }

        int p1 = mPreview == NOT_A_PREVIEW ? Integer.MAX_VALUE : mPreview;
        int p2 = rhs.mPreview == NOT_A_PREVIEW ? Integer.MAX_VALUE : rhs.mPreview;
        delta = p1 - p2;
        return delta;
    }


}
