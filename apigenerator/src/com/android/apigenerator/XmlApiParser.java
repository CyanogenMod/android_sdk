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

package com.android.apigenerator;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.Map;

/**
 * Parser for the old, XML-based format of platform API files.
 */
class XmlApiParser extends DefaultHandler {

    private final static String NODE_API = "api";
    private final static String NODE_PACKAGE = "package";
    private final static String NODE_CLASS = "class";
    private final static String NODE_INTERFACE = "interface";
    private final static String NODE_IMPLEMENTS = "implements";
    private final static String NODE_FIELD = "field";
    private final static String NODE_CONSTRUCTOR = "constructor";
    private final static String NODE_METHOD = "method";
    private final static String NODE_PARAMETER = "parameter";

    private final static String ATTR_NAME = "name";
    private final static String ATTR_TYPE = "type";
    private final static String ATTR_RETURN = "return";
    private final static String ATTR_EXTENDS = "extends";

    private final ParserState mParserState;

    XmlApiParser(Map<String, ApiClass> map, int apiLevel) {
        mParserState = new ParserState(map, apiLevel);
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes)
            throws SAXException {

        if (localName == null || localName.length() == 0) {
            localName = qName;
        }

        try {

            if (NODE_API.equals(localName)) {
            } else if (NODE_PACKAGE.equals(localName)) {
                mParserState.addPackage(attributes.getValue(ATTR_NAME));

            } else if (NODE_CLASS.equals(localName) || NODE_INTERFACE.equals(localName)) {
                mParserState.addClass(attributes.getValue(ATTR_NAME));

                String extendsAttr = attributes.getValue(ATTR_EXTENDS);
                if (extendsAttr != null) {
                    mParserState.addSuperClass(extendsAttr);
                }

            } else if (NODE_IMPLEMENTS.equals(localName)) {
                mParserState.addInterface(attributes.getValue(ATTR_NAME));

            } else if (NODE_FIELD.equals(localName)) {
                mParserState.addField(attributes.getValue(ATTR_NAME));

            } else if (NODE_CONSTRUCTOR.equals(localName)) {
                parseConstructor(attributes);

            } else if (NODE_METHOD.equals(localName)) {
                parseMethod(attributes);

            } else if (NODE_PARAMETER.equals(localName)) {
                parseParameter(attributes);
            }

        } finally {
            super.startElement(uri, localName, qName, attributes);
        }
    }

    private void parseConstructor(Attributes attributes) {
        mParserState.startNewConstructor();
    }

    private void parseMethod(Attributes attributes) {
        mParserState.startNewMethod(attributes.getValue(ATTR_NAME),
                attributes.getValue(ATTR_RETURN));
    }

    private void parseParameter(Attributes attributes) {
        mParserState.addMethodParameter(attributes.getValue(ATTR_TYPE));
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (localName == null || localName.length() == 0) {
            localName = qName;
        }

        try {

            if (NODE_METHOD.equals(localName) || NODE_CONSTRUCTOR.equals(localName)) {
                mParserState.finishMethod();

            } else if (NODE_API.equals(localName)) {
                mParserState.done();
            }

        } finally {
            super.endElement(uri, localName, qName);
        }
    }
}