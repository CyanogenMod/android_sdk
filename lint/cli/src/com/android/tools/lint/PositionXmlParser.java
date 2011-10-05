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

import com.android.tools.lint.api.IDomParser;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Position;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.XMLFilterImpl;
import org.xml.sax.helpers.XMLReaderFactory;

import java.io.StringReader;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.sax.SAXSource;

/** A simple XML parser which can store and retrieve line:column information for the nodes */
public class PositionXmlParser implements IDomParser {
    private static final String ATTR_LOCATION = "location";                     //$NON-NLS-1$
    private static final String PRIVATE_NAMESPACE = "http://tools.android.com"; //$NON-NLS-1$
    private static final String PRIVATE_PREFIX = "temp";                        //$NON-NLS-1$

    public Document parse(Context context) {
        InputSource input = new InputSource(new StringReader(context.getContents()));
        try {
            Filter filter = new Filter(XMLReaderFactory.createXMLReader());
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            DOMResult result = new DOMResult();
            transformer.transform(new SAXSource(filter, input), result);
            return (Document) result.getNode();
        } catch (SAXException e) {
            // The file doesn't parse: not an exception. Infrastructure will log a warning
            // that this file was not analyzed.
            return null;
        } catch (TransformerConfigurationException e) {
            context.toolContext.log(e, null);
        } catch (TransformerException e) {
            context.toolContext.log(e, null);
        }

        return null;
    }

    private static class Filter extends XMLFilterImpl {
        private Locator mLocator;

        Filter(XMLReader reader) {
            super(reader);
        }

        @Override
        public void setDocumentLocator(Locator locator) {
            super.setDocumentLocator(locator);
            this.mLocator = locator;
        }

        @Override
        public void startElement(String uri, String localName, String qualifiedName,
                Attributes attributes) throws SAXException {
            int lineno = mLocator.getLineNumber();
            int column = mLocator.getColumnNumber();
            String location = Integer.toString(lineno) + ':' + Integer.toString(column);

            // Modify attributes parameter to a copy that includes our private attribute
            AttributesImpl wrapper = new AttributesImpl(attributes);
            wrapper.addAttribute(PRIVATE_NAMESPACE, ATTR_LOCATION,
                    PRIVATE_PREFIX + ':' + ATTR_LOCATION, "CDATA", location); //$NON-NLS-1$

            super.startElement(uri, localName, qualifiedName, wrapper);
        }
    }

    public Position getStartPosition(Context context, Node node) {
        if (node instanceof Attr) {
            Attr attr = (Attr) node;
            node = attr.getOwnerElement();
        }
        if (node instanceof Element) {
            Attr attribute = ((Element) node).getAttributeNodeNS(PRIVATE_NAMESPACE, ATTR_LOCATION);
            if (attribute != null) {
                String position = attribute.getValue();
                int separator = position.indexOf(':');
                int line = Integer.parseInt(position.substring(0, separator));
                int column = Integer.parseInt(position.substring(separator + 1));
                return new OffsetPosition(line, column, -1);
            }
        }

        return null;
    }

    public Position getEndPosition(Context context, Node node) {
        // TODO: Currently unused
        return null;
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
}
