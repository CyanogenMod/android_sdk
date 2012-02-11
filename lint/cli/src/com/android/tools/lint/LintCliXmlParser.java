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

import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.IDomParser;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Location.Handle;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.util.PositionXmlParser;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.UnsupportedEncodingException;

/**
 * A customization of the {@link PositionXmlParser} which creates position
 * objects that directly extend the lint
 * {@link com.android.tools.lint.detector.api.Position} class.
 * <p>
 * It also catches and reports parser errors as lint errors.
 */
public class LintCliXmlParser extends PositionXmlParser implements IDomParser {
    @Override
    public Document parseXml(XmlContext context) {
        try {
            // Do we need to provide an input stream for encoding?
            String xml = context.getContents();
            if (xml != null) {
                return super.parse(xml);
            }
        } catch (UnsupportedEncodingException e) {
            context.report(
                    // Must provide an issue since API guarantees that the issue parameter
                    // is valid
                    IssueRegistry.PARSER_ERROR, Location.create(context.file),
                    e.getCause() != null ? e.getCause().getLocalizedMessage() :
                        e.getLocalizedMessage(),
                    null);
        } catch (SAXException e) {
            context.report(
                    // Must provide an issue since API guarantees that the issue parameter
                    // is valid
                    IssueRegistry.PARSER_ERROR, Location.create(context.file),
                    e.getCause() != null ? e.getCause().getLocalizedMessage() :
                        e.getLocalizedMessage(),
                    null);
        } catch (Throwable t) {
            context.log(t, null);
        }
        return null;
    }

    @Override
    public Location getLocation(XmlContext context, Node node) {
        OffsetPosition pos = (OffsetPosition) getPosition(node);
        if (pos != null) {
            return Location.create(context.file, pos, (OffsetPosition) pos.getEnd());
        }

        return null;
    }

    @Override
    public Handle createLocationHandle(XmlContext context, Node node) {
        return new LocationHandle(context.file, node);
    }

    @Override
    protected OffsetPosition createPosition(int line, int column, int offset) {
        return new OffsetPosition(line, column, offset);
    }

    private static class OffsetPosition extends com.android.tools.lint.detector.api.Position
            implements PositionXmlParser.Position {
        /** The line number (0-based where the first line is line 0) */
        private final int mLine;

        /**
         * The column number (where the first character on the line is 0), or -1 if
         * unknown
         */
        private final int mColumn;

        /** The character offset */
        private final int mOffset;

        /**
         * Linked position: for a begin offset this will point to the end
         * offset, and for an end offset this will be null
         */
        private com.android.util.PositionXmlParser.Position mEnd;

        /**
         * Creates a new {@link OffsetPosition}
         *
         * @param line the 0-based line number, or -1 if unknown
         * @param column the 0-based column number, or -1 if unknown
         * @param offset the offset, or -1 if unknown
         */
        public OffsetPosition(int line, int column, int offset) {
            this.mLine = line;
            this.mColumn = column;
            this.mOffset = offset;
        }

        @Override
        public int getLine() {
            return mLine;
        }

        @Override
        public int getOffset() {
            return mOffset;
        }

        @Override
        public int getColumn() {
            return mColumn;
        }

        @Override
        public com.android.util.PositionXmlParser.Position getEnd() {
            return mEnd;
        }

        @Override
        public void setEnd(com.android.util.PositionXmlParser.Position end) {
            mEnd = end;
        }

        @Override
        public String toString() {
            return "OffsetPosition [line=" + mLine + ", column=" + mColumn + ", offset="
                    + mOffset + ", end=" + mEnd + "]";
        }
    }

    @Override
    public void dispose(XmlContext context, Document document) {
    }

    /* Handle for creating DOM positions cheaply and returning full fledged locations later */
    private class LocationHandle implements Handle {
        private File mFile;
        private Node mNode;
        private Object mClientData;

        public LocationHandle(File file, Node node) {
            mFile = file;
            mNode = node;
        }

        @Override
        public Location resolve() {
            OffsetPosition pos = (OffsetPosition) getPosition(mNode);
            if (pos != null) {
                return Location.create(mFile, pos, (OffsetPosition) pos.getEnd());
            }

            return null;
        }

        @Override
        public void setClientData(@Nullable Object clientData) {
            mClientData = clientData;
        }

        @Override
        @Nullable
        public Object getClientData() {
            return mClientData;
        }
    }
}
