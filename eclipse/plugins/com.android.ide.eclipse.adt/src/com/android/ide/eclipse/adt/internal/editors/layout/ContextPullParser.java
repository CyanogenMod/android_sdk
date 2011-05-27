/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.ide.eclipse.adt.internal.editors.layout;

import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_WIDTH;
import static com.android.ide.common.layout.LayoutConstants.VALUE_FILL_PARENT;
import static com.android.ide.common.layout.LayoutConstants.VALUE_MATCH_PARENT;
import static com.android.ide.eclipse.adt.internal.editors.layout.descriptors.LayoutDescriptors.ATTR_LAYOUT;
import static com.android.ide.eclipse.adt.internal.editors.layout.descriptors.LayoutDescriptors.VIEW_FRAGMENT;
import static com.android.ide.eclipse.adt.internal.editors.layout.descriptors.LayoutDescriptors.VIEW_INCLUDE;
import static com.android.ide.eclipse.adt.internal.editors.layout.gle2.LayoutMetadata.KEY_FRAGMENT_LAYOUT;

import com.android.ide.common.rendering.api.ILayoutPullParser;
import com.android.ide.common.rendering.api.IProjectCallback;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.LayoutMetadata;
import com.android.sdklib.SdkConstants;

import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;

/**
 * Modified {@link KXmlParser} that adds the methods of {@link ILayoutPullParser}, and
 * performs other layout-specific parser behavior like translating fragment tags into
 * include tags.
 * <p/>
 * It will return a given parser when queried for one through
 * {@link ILayoutPullParser#getParser(String)} for a given name.
 *
 */
public class ContextPullParser extends KXmlParser implements ILayoutPullParser {
    private static final String COMMENT_PREFIX = "<!--"; //$NON-NLS-1$
    private static final String COMMENT_SUFFIX = "-->"; //$NON-NLS-1$
    /** The callback to request parsers from */
    private final IProjectCallback mProjectCallback;
    /** The {@link File} for the layout currently being parsed */
    private File mFile;
    /** The layout to be shown for the current {@code <fragment>} tag. Usually null. */
    private String mFragmentLayout = null;

    public ContextPullParser(IProjectCallback projectCallback, File file) {
        super();
        mProjectCallback = projectCallback;
        mFile = file;
    }

    // --- Layout lib API methods

    /**
     * this is deprecated but must still be implemented for older layout libraries.
     * @deprecated use {@link IProjectCallback#getParser(String)}.
     */
    @Deprecated
    public ILayoutPullParser getParser(String layoutName) {
        return mProjectCallback.getParser(layoutName);
    }

    public Object getViewCookie() {
        return null; // never any key to return
    }

    // --- KXMLParser override

    @Override
    public String getName() {
        String name = super.getName();

        // At designtime, replace fragments with includes.
        if (name.equals(VIEW_FRAGMENT)) {
            mFragmentLayout = findFragmentLayout();
            if (mFragmentLayout != null) {
                return VIEW_INCLUDE;
            }
        } else {
            mFragmentLayout = null;
        }

        return name;
    }

    @Override
    public String getAttributeValue(String namespace, String localName) {
        if (localName.equals(ATTR_LAYOUT) && mFragmentLayout != null) {
            return mFragmentLayout;
        }

        String value = super.getAttributeValue(namespace, localName);

        // on the fly convert match_parent to fill_parent for compatibility with older
        // platforms.
        if (VALUE_MATCH_PARENT.equals(value) &&
                (ATTR_LAYOUT_WIDTH.equals(localName) ||
                        ATTR_LAYOUT_HEIGHT.equals(localName)) &&
                SdkConstants.NS_RESOURCES.equals(namespace)) {
            return VALUE_FILL_PARENT;
        }

        return value;
    }

    /**
     * This method determines whether the {@code <fragment>} tag in the current parsing
     * context has been configured with a layout to render at designtime. If so,
     * it returns the resource name of the layout, and if not, returns null.
     */
    private String findFragmentLayout() {
        try {
            if (!isEmptyElementTag()) {
                // We need to look inside the <fragment> tag to see
                // if it contains a comment which indicates a fragment
                // to be rendered.
                String file = AdtPlugin.readFile(mFile);

                int line = getLineNumber() - 1;
                int column = getColumnNumber() - 1;
                int offset = 0;
                int currentLine = 0;
                int length = file.length();
                while (currentLine < line && offset < length) {
                    int next = file.indexOf('\n', offset);
                    if (next == -1) {
                        break;
                    }

                    currentLine++;
                    offset = next + 1;
                }
                if (currentLine == line) {
                    offset += column;
                    if (offset < length) {
                        offset = file.indexOf('<', offset);
                        if (offset != -1 && file.startsWith(COMMENT_PREFIX, offset)) {
                            // The fragment tag contains a comment
                            int end = file.indexOf(COMMENT_SUFFIX, offset);
                            if (end != -1) {
                                String commentText = file.substring(
                                        offset + COMMENT_PREFIX.length(), end);
                                String l = LayoutMetadata.getProperty(KEY_FRAGMENT_LAYOUT,
                                        commentText);
                                if (l != null) {
                                    return l;
                                }
                            }
                        }
                    }
                }
            }
        } catch (XmlPullParserException e) {
            AdtPlugin.log(e, null);
        }

        return null;
    }
}
