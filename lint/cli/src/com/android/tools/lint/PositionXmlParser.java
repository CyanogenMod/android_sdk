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

import com.android.tools.lint.client.api.IDomParser;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Position;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * A simple DOM XML parser which can retrieve exact beginning and end offsets
 * (and line and column numbers) for element nodes as well as attribute nodes.
 */
public class PositionXmlParser implements IDomParser {
    private static final String CONTENT_KEY = "contents";     //$NON-NLS-1$
    private final static String POS_KEY = "offsets";          //$NON-NLS-1$
    private static final String NAMESPACE_PREFIX_FEATURE =
            "http://xml.org/sax/features/namespace-prefixes"; //$NON-NLS-1$
    private static final String NAMESPACE_FEATURE =
            "http://xml.org/sax/features/namespaces";         //$NON-NLS-1$

    // ---- Implements IDomParser ----

    public Document parse(Context context) {
        return parse(context, context.getContents(), true);
    }

    private Document parse(Context context, String xml, boolean checkBom) {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setFeature(NAMESPACE_FEATURE, true);
            factory.setFeature(NAMESPACE_PREFIX_FEATURE, true);
            SAXParser parser = factory.newSAXParser();

            InputSource input = new InputSource(new StringReader(xml));
            DomBuilder handler = new DomBuilder(xml);
            parser.parse(input, handler);
            return handler.getDocument();
        } catch (ParserConfigurationException e) {
            context.client.log(e, null);
        } catch (SAXException e) {
            if (checkBom && e.getMessage().contains("Content is not allowed in prolog")) {
                // Byte order mark in the string? Skip it. There are many markers
                // (see http://en.wikipedia.org/wiki/Byte_order_mark) so here we'll
                // just skip those up to the XML prolog beginning character, <
                xml = xml.replaceFirst("^([\\W]+)<","<");  //$NON-NLS-1$ //$NON-NLS-2$
                return parse(context, xml, false);
            }
            context.client.report(
                    context,
                    // Must provide an issue since API guarantees that the issue parameter
                    // is valid
                    IssueRegistry.PARSER_ERROR,
                    new Location(context.file, null, null),
                    e.getCause() != null ? e.getCause().getLocalizedMessage() :
                        e.getLocalizedMessage(),
                    null);
        } catch (Throwable t) {
            context.client.log(t, null);
        }
        return null;
    }

    public Position getStartPosition(Context context, Node node) {
        // Look up the position information stored while parsing for the given node.
        // Note however that we only store position information for elements (because
        // there is no SAX callback for individual attributes).
        // Therefore, this method special cases this:
        //  -- First, it looks at the owner element and uses its position
        //     information as a first approximation.
        //  -- Second, it uses that, as well as the original XML text, to search
        //     within the node range for an exact text match on the attribute name
        //     and if found uses that as the exact node offsets instead.
        if (node instanceof Attr) {
            Attr attr = (Attr) node;
            OffsetPosition pos = (OffsetPosition) attr.getOwnerElement().getUserData(POS_KEY);
            if (pos != null) {
                int startOffset = pos.getOffset();
                int endOffset = pos.next.getOffset();

                // Find attribute in the text
                String contents = (String) node.getOwnerDocument().getUserData(CONTENT_KEY);
                if (contents == null) {
                    return null;
                }

                // Locate the name=value attribute in the source text
                // Fast string check first for the common occurrence
                String name = attr.getName();
                Pattern pattern = Pattern.compile(
                        String.format("%1$s\\s*=\\s*[\"'].*[\"']", name)); //$NON-NLS-1$
                Matcher matcher = pattern.matcher(contents);
                if (matcher.find(startOffset) && matcher.start() <= endOffset) {
                    int index = matcher.start();
                    // Adjust the line and column to this new offset
                    int line = pos.getLine();
                    int column = pos.getColumn();
                    for (int offset = pos.getOffset(); offset < index; offset++) {
                        char t = contents.charAt(offset);
                        if (t == '\n') {
                            line++;
                            column = 0;
                        }
                        column++;
                    }

                    OffsetPosition attributePosition = new OffsetPosition(line, column, index);
                    // Also set end range for retrieval in getEndPosition
                    attributePosition.next = new OffsetPosition(line, column, matcher.end());
                    return attributePosition;
                } else {
                    // No regexp match either: just fall back to element position
                    return pos;
                }
            }
        }

        return (OffsetPosition) node.getUserData(POS_KEY);
    }

    public Position getEndPosition(Context context, Node node) {
        OffsetPosition pos = (OffsetPosition) getStartPosition(context, node);
        if (pos != null && pos.next != null) {
            return pos.next;
        }

        return null;
    }

    public Location getLocation(Context context, Node node) {
        return new Location(context.file, getStartPosition(context, node),
                getEndPosition(context, node));
    }

    /**
     * SAX parser handler which incrementally builds up a DOM document as we go
     * along, and updates position information along the way. Position
     * information is attached to the DOM nodes by setting user data with the
     * {@link POS_KEY} key.
     */
    private static final class DomBuilder extends DefaultHandler {
        private final String mXml;
        private final Document mDocument;
        private Locator mLocator;
        private int mCurrentLine = 0;
        private int mCurrentOffset;
        private int mCurrentColumn;
        private final List<Element> mStack = new ArrayList<Element>();
        private final StringBuilder mPendingText = new StringBuilder();

        private DomBuilder(String xml) throws ParserConfigurationException {
            mXml = xml;

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setValidating(false);
            DocumentBuilder docBuilder = factory.newDocumentBuilder();
            mDocument = docBuilder.newDocument();
            mDocument.setUserData(CONTENT_KEY, xml, null);
        }

        /** Returns the document parsed by the handler */
        Document getDocument() {
            return mDocument;
        }

        @Override
        public void setDocumentLocator(Locator locator) {
            this.mLocator = locator;
        }

        @Override
        public void startElement(String uri, String localName, String qName,
                Attributes attributes) throws SAXException {
            flushText();
            Element element = mDocument.createElement(qName);
            for (int i = 0; i < attributes.getLength(); i++) {
                if (attributes.getURI(i) != null && attributes.getURI(i).length() > 0) {
                    Attr attr = mDocument.createAttributeNS(attributes.getURI(i),
                            attributes.getQName(i));
                    attr.setValue(attributes.getValue(i));
                    element.setAttributeNodeNS(attr);
                    assert attr.getOwnerElement() == element;
                } else {
                    Attr attr = mDocument.createAttribute(attributes.getQName(i));
                    attr.setValue(attributes.getValue(i));
                    element.setAttributeNode(attr);
                    assert attr.getOwnerElement() == element;
                }
            }

            OffsetPosition pos = getCurrentPosition();

            // The starting position reported to us by SAX is really the END of the
            // open tag in an element, when all the attributes have been processed.
            // We have to scan backwards to find the real beginning. We'll do that
            // by scanning backwards.
            // -1: Make sure that when we have <foo></foo> we don't consider </foo>
            // the beginning since pos.offset will typically point to the first character
            // AFTER the element open tag, which could be a closing tag or a child open
            // tag

            for (int offset = pos.getOffset() - 1; offset >= 0; offset--) {
                char c = mXml.charAt(offset);
                // < cannot appear in attribute values or anywhere else within
                // an element open tag, so we know the first occurrence is the real
                // element start
                if (c == '<') {
                    // Adjust line position
                    int line = pos.getLine();
                    for (int i = offset, n = pos.getOffset(); i < n; i++) {
                        if (mXml.charAt(i) == '\n') {
                            line--;
                        }
                    }

                    // Compute new column position
                    int column = 0;
                    for (int i = offset; i >= 0; i--, column++) {
                        if (mXml.charAt(i) == '\n') {
                            break;
                        }
                    }

                    pos = new OffsetPosition(line, column, offset);
                    break;
                }
            }

            element.setUserData(POS_KEY, pos, null);
            mStack.add(element);
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            flushText();
            Element element = mStack.remove(mStack.size() - 1);

            OffsetPosition pos = (OffsetPosition) element.getUserData(POS_KEY);
            assert pos != null;
            pos.next = getCurrentPosition();

            if (mStack.isEmpty()) {
                mDocument.appendChild(element);
            } else {
                Element parent = mStack.get(mStack.size() - 1);
                parent.appendChild(element);
            }
        }

        /**
         * Returns a position holder for the current position. The most
         * important part of this function is to incrementally compute the
         * offset as well, by counting forwards until it reaches the new line
         * number and column position of the XML parser, counting characters as
         * it goes along.
         */
        private OffsetPosition getCurrentPosition() {
            int line = mLocator.getLineNumber() - 1;
            int column = mLocator.getColumnNumber() - 1;

            // Compute offset incrementally now that we have the new line and column
            // numbers
            while (mCurrentLine < line) {
                char c = mXml.charAt(mCurrentOffset);
                if (c == '\n') {
                    mCurrentLine++;
                    mCurrentColumn = 0;
                } else {
                    mCurrentColumn++;
                }
                mCurrentOffset++;
            }

            mCurrentOffset += column - mCurrentColumn;
            mCurrentColumn = column;

            return new OffsetPosition(mCurrentLine, mCurrentColumn, mCurrentOffset);
        }

        @Override
        public void characters(char c[], int start, int length) throws SAXException {
            mPendingText.append(c, start, length);
        }

        private void flushText() {
            if (mPendingText.length() > 0 && !mStack.isEmpty()) {
                Element element = mStack.get(mStack.size() - 1);
                Node textNode = mDocument.createTextNode(mPendingText.toString());
                element.appendChild(textNode);
                mPendingText.setLength(0);
            }
        }
    }

    private static class OffsetPosition extends Position {
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
        public OffsetPosition next;

        /**
         * Creates a new {@link Position}
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
    }

    public void dispose(Context context) {
    }
}
