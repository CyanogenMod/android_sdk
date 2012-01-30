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

package com.android.tools.lint;

import static com.android.tools.lint.detector.api.LintConstants.DOT_9PNG;
import static com.android.tools.lint.detector.api.LintConstants.DOT_PNG;
import static com.android.tools.lint.detector.api.LintUtils.endsWith;

import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A reporter is an output generator for lint warnings */
abstract class Reporter {
    protected final Main mClient;
    protected final File mOutput;
    protected String mTitle = "Lint Report";
    protected boolean mSimpleFormat;
    protected boolean mBundleResources;
    protected Map<String, String> mUrlMap;
    protected File mResources;
    protected Map<File, String> mResourceUrl = new HashMap<File, String>();
    protected Map<String, File> mNameToFile = new HashMap<String, File>();

    abstract void write(int errorCount, int warningCount, List<Warning> issues) throws IOException;

    protected Reporter(Main client, File output) {
        mClient = client;
        mOutput = output;
    }

    /**
     * Sets the report title
     *
     * @param title the title of the report
     */
    void setTitle(String title) {
        mTitle = title;
    }

    /** @return the title of the report */
    String getTitle() {
        return mTitle;
    }

    /**
     * Sets whether the report should bundle up resources along with the HTML report.
     * This implies a non-simple format (see {@link #setSimpleFormat(boolean)}).
     *
     * @param bundleResources if true, copy images into a directory relative to
     *            the report
     */
    void setBundleResources(boolean bundleResources) {
        mBundleResources = bundleResources;
        mSimpleFormat = false;
    }

    /**
     * Sets whether the report should use simple formatting (meaning no JavaScript,
     * embedded images, etc).
     *
     * @param simpleFormat whether the formatting should be simple
     */
    void setSimpleFormat(boolean simpleFormat) {
        mSimpleFormat = simpleFormat;
    }

    /**
     * Returns whether the report should use simple formatting (meaning no JavaScript,
     * embedded images, etc).
     *
     * @return whether the report should use simple formatting
     */
    boolean isSimpleFormat() {
        return mSimpleFormat;
    }


    String getUrl(File file) {
        if (mBundleResources && !mSimpleFormat) {
            String url = getRelativeResourceUrl(file);
            if (url != null) {
                return url;
            }
        }

        if (mUrlMap != null) {
            String path = file.getAbsolutePath();
            try {
                // Perform the comparison using URLs such that we properly escape spaces etc.
                String pathUrl = URLEncoder.encode(path, "UTF-8");         //$NON-NLS-1$
                for (Map.Entry<String, String> entry : mUrlMap.entrySet()) {
                    String prefix = entry.getKey();
                    String prefixUrl = URLEncoder.encode(prefix, "UTF-8"); //$NON-NLS-1$
                    if (pathUrl.startsWith(prefixUrl)) {
                        String relative = pathUrl.substring(prefixUrl.length());
                        return entry.getValue()
                                + relative.replace("%2F", "/"); //$NON-NLS-1$ //$NON-NLS-2$
                    }
                }
            } catch (UnsupportedEncodingException e) {
                // This shouldn't happen for UTF-8
                System.err.println("Invalid URL map specification - " + e.getLocalizedMessage());
            }
        }

        return null;
    }

    /** Encodes the given String as a safe URL substring, escaping spaces etc */
    static String encodeUrl(String url) {
        try {
            return URLEncoder.encode(url, "UTF-8");         //$NON-NLS-1$
        } catch (UnsupportedEncodingException e) {
            // This shouldn't happen for UTF-8
            System.err.println("Invalid string " + e.getLocalizedMessage());
            return url;
        }
    }

    /** Set mapping of path prefixes to corresponding URLs in the HTML report */
    void setUrlMap(Map<String, String> urlMap) {
        mUrlMap = urlMap;
    }

    /** Gets a pointer to the local resource directory, if any */
    File getResourceDir() {
        if (mResources == null && mBundleResources) {
            String fileName = mOutput.getName();
            int dot = fileName.indexOf('.');
            if (dot != -1) {
                fileName = fileName.substring(0, dot);
            }

            mResources = new File(mOutput.getParentFile(), fileName + "_files"); //$NON-NLS-1$
            if (!mResources.mkdir()) {
                mResources = null;
                mBundleResources = false;
            }
        }

        return mResources;
    }

    /** Returns a URL to a local copy of the given file, or null */
    protected String getRelativeResourceUrl(File file) {
        String resource = mResourceUrl.get(file);
        if (resource != null) {
            return resource;
        }

        String name = file.getName();
        if (!endsWith(name, DOT_PNG) || endsWith(name, DOT_9PNG)) {
            return null;
        }

        // Attempt to make local copy
        File resourceDir = getResourceDir();
        if (resourceDir != null) {
            String base = file.getName();

            File path = mNameToFile.get(base);
            if (path != null && !path.equals(file)) {
                // That filename already exists and is associated with a different path:
                // make a new unique version
                for (int i = 0; i < 100; i++) {
                    base = '_' + base;
                    path = mNameToFile.get(base);
                    if (path == null || path.equals(file)) {
                        break;
                    }
                }
            }

            File target = new File(resourceDir, base);
            try {
                Files.copy(file, target);
            } catch (IOException e) {
                return null;
            }
            return resourceDir.getName() + '/' + encodeUrl(base);
        }
        return null;
    }
}