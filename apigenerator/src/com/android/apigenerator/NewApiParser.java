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

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Parser for the new format of platform API files. This is adapted from the Doclava code.
 *
 */
class NewApiParser {

    public static void parseApi(String filename, InputStream stream,
            Map<String, ApiClass> classes, int api) throws ApiParseException {
        final int CHUNK = 1024 * 1024;
        int hint = 0;
        try {
            hint = stream.available() + CHUNK;
        } catch (IOException ex) {
        }

        if (hint < CHUNK) {
            hint = CHUNK;
        }

        byte[] buf = new byte[hint];
        int size = 0;

        try {
            while (true) {
                if (size == buf.length) {
                    byte[] tmp = new byte[buf.length + CHUNK];
                    System.arraycopy(buf, 0, tmp, 0, buf.length);
                    buf = tmp;
                }
                int amt = stream.read(buf, size, (buf.length - size));
                if (amt < 0) {
                    break;
                } else {
                    size += amt;
                }
            }
        } catch (IOException ex) {
            throw new ApiParseException("Error reading API file", ex);
        }

        final Tokenizer tokenizer = new Tokenizer(filename,
                (new String(buf, 0, size)).toCharArray());

        final ParserState state = new ParserState(classes, api);

        while (true) {
            String token = tokenizer.getToken();
            if (token == null) {
                break;
            }
            if ("package".equals(token)) {
                parsePackage(state, tokenizer);
            } else {
                throw new ApiParseException("expected package got " + token, tokenizer.getLine());
            }
        }
    }

    private static void parsePackage(ParserState state, Tokenizer tokenizer)
            throws ApiParseException {
        String token;
        String name;

        token = tokenizer.requireToken();
        assertIdent(tokenizer, token);
        name = token;

        state.addPackage(name);

        token = tokenizer.requireToken();
        if (!"{".equals(token)) {
            throw new ApiParseException("expected '{' got " + token, tokenizer.getLine());
        }
        while (true) {
            token = tokenizer.requireToken();
            if ("}".equals(token)) {
                break;
            } else {
                parseClass(state, tokenizer, token);
            }
        }

        state.finishPackage();
    }

    private static void parseClass(ParserState state, Tokenizer tokenizer, String token)
            throws ApiParseException {
        boolean pub = false;
        boolean prot = false;
        boolean pkgpriv = false;
        boolean stat = false;
        boolean fin = false;
        boolean abs = false;
        boolean dep = false;
        boolean iface;
        String name;
        String qname;

        // even though we don't care about all those parameters, we keep this parsing logic
        // to make sure we go through all the tokens.

        if ("public".equals(token)) {
            pub = true;
            token = tokenizer.requireToken();
        } else if ("protected".equals(token)) {
            prot = true;
            token = tokenizer.requireToken();
        } else {
            pkgpriv = true;
        }
        if ("static".equals(token)) {
            stat = true;
            token = tokenizer.requireToken();
        }
        if ("final".equals(token)) {
            fin = true;
            token = tokenizer.requireToken();
        }
        if ("abstract".equals(token)) {
            abs = true;
            token = tokenizer.requireToken();
        }
        if ("deprecated".equals(token)) {
            dep = true;
            token = tokenizer.requireToken();
        }
        if ("class".equals(token)) {
            iface = false;
            token = tokenizer.requireToken();
        } else if ("interface".equals(token)) {
            iface = true;
            token = tokenizer.requireToken();
        } else {
            throw new ApiParseException("missing class or interface. got: " + token,
                    tokenizer.getLine());
        }
        assertIdent(tokenizer, token);
        name = token;
        token = tokenizer.requireToken();

        state.addClass(name);

        // even though we don't care about all those parameters, we keep this parsing logic
        // to make sure we go through all the tokens.


        if ("extends".equals(token)) {
            token = tokenizer.requireToken();
            assertIdent(tokenizer, token);
            state.addSuperClass(token);
            token = tokenizer.requireToken();
        }

        // Resolve superclass after done parsing
        if ("implements".equals(token)) {
            while (true) {
                token = tokenizer.requireToken();
                if ("{".equals(token)) {
                    break;
                } else {
                    if (!",".equals(token)) {
                        state.addInterface(token);
                    }
                }
            }
        }

        if (!"{".equals(token)) {
            throw new ApiParseException("expected {", tokenizer.getLine());
        }

        token = tokenizer.requireToken();
        while (true) {
            if ("}".equals(token)) {
                break;
            } else if ("ctor".equals(token)) {
                token = tokenizer.requireToken();
                parseConstructor(tokenizer, state, token);
            } else if ("method".equals(token)) {
                token = tokenizer.requireToken();
                parseMethod(tokenizer, state, token);
            } else if ("field".equals(token)) {
                token = tokenizer.requireToken();
                parseField(tokenizer, state, token, false);
            } else if ("enum_constant".equals(token)) {
                token = tokenizer.requireToken();
                parseField(tokenizer, state, token, true);
            } else {
                throw new ApiParseException("expected ctor, enum_constant, field or method",
                        tokenizer.getLine());
            }
            token = tokenizer.requireToken();
        }

        state.finishClass();
    }

    private static void parseConstructor(Tokenizer tokenizer, ParserState state, String token)
            throws ApiParseException {
        boolean pub = false;
        boolean prot = false;
        boolean pkgpriv = false;
        boolean dep = false;
        String name;

        if ("public".equals(token)) {
            pub = true;
            token = tokenizer.requireToken();
        } else if ("protected".equals(token)) {
            prot = true;
            token = tokenizer.requireToken();
        } else {
            pkgpriv = true;
        }
        if ("deprecated".equals(token)) {
            dep = true;
            token = tokenizer.requireToken();
        }
        assertIdent(tokenizer, token);
        name = token;
        token = tokenizer.requireToken();
        if (!"(".equals(token)) {
            throw new ApiParseException("expected (", tokenizer.getLine());
        }

        state.startNewConstructor();

        token = tokenizer.requireToken();
        parseParameterList(tokenizer, state, token);
        token = tokenizer.requireToken();
        if ("throws".equals(token)) {
            token = parseThrows(tokenizer, state);
        }
        if (!";".equals(token)) {
            throw new ApiParseException("expected ; found " + token, tokenizer.getLine());
        }

        state.finishMethod();
    }

    private static void parseMethod(Tokenizer tokenizer, ParserState state, String token)
            throws ApiParseException {
        boolean pub = false;
        boolean prot = false;
        boolean pkgpriv = false;
        boolean stat = false;
        boolean fin = false;
        boolean abs = false;
        boolean dep = false;
        boolean syn = false;
        String type;
        String name;
        String ext = null;

        if ("public".equals(token)) {
            pub = true;
            token = tokenizer.requireToken();
        } else if ("protected".equals(token)) {
            prot = true;
            token = tokenizer.requireToken();
        } else {
            pkgpriv = true;
        }
        if ("static".equals(token)) {
            stat = true;
            token = tokenizer.requireToken();
        }
        if ("final".equals(token)) {
            fin = true;
            token = tokenizer.requireToken();
        }
        if ("abstract".equals(token)) {
            abs = true;
            token = tokenizer.requireToken();
        }
        if ("deprecated".equals(token)) {
            dep = true;
            token = tokenizer.requireToken();
        }
        if ("synchronized".equals(token)) {
            syn = true;
            token = tokenizer.requireToken();
        }
        assertIdent(tokenizer, token);
        type = token;
        token = tokenizer.requireToken();
        assertIdent(tokenizer, token);
        name = token;

        state.startNewMethod(name, type);

        token = tokenizer.requireToken();
        if (!"(".equals(token)) {
            throw new ApiParseException("expected (", tokenizer.getLine());
        }
        token = tokenizer.requireToken();
        parseParameterList(tokenizer, state, token);
        token = tokenizer.requireToken();
        if ("throws".equals(token)) {
            token = parseThrows(tokenizer, state);
        }
        if (!";".equals(token)) {
            throw new ApiParseException("expected ; found " + token, tokenizer.getLine());
        }

        state.finishMethod();
    }

    private static void parseField(Tokenizer tokenizer, ParserState state, String token, boolean isEnum)
            throws ApiParseException {
        boolean pub = false;
        boolean prot = false;
        boolean pkgpriv = false;
        boolean stat = false;
        boolean fin = false;
        boolean dep = false;
        boolean trans = false;
        boolean vol = false;
        String type;
        String name;
        String val = null;
        Object v;

        if ("public".equals(token)) {
            pub = true;
            token = tokenizer.requireToken();
        } else if ("protected".equals(token)) {
            prot = true;
            token = tokenizer.requireToken();
        } else {
            pkgpriv = true;
        }
        if ("static".equals(token)) {
            stat = true;
            token = tokenizer.requireToken();
        }
        if ("final".equals(token)) {
            fin = true;
            token = tokenizer.requireToken();
        }
        if ("deprecated".equals(token)) {
            dep = true;
            token = tokenizer.requireToken();
        }
        if ("transient".equals(token)) {
            trans = true;
            token = tokenizer.requireToken();
        }
        if ("volatile".equals(token)) {
            vol = true;
            token = tokenizer.requireToken();
        }
        assertIdent(tokenizer, token);
        type = token;
        token = tokenizer.requireToken();
        assertIdent(tokenizer, token);
        name = token;
        token = tokenizer.requireToken();
        if ("=".equals(token)) {
            token = tokenizer.requireToken(false);
            val = token;
            token = tokenizer.requireToken();
        }
        if (!";".equals(token)) {
            throw new ApiParseException("expected ; found " + token, tokenizer.getLine());
        }

        if (isEnum) {
            state.addField(name);
        } else {
            state.addField(name);
        }
    }

    private static void parseParameterList(Tokenizer tokenizer, ParserState state,
            String token) throws ApiParseException {
        while (true) {
            if (")".equals(token)) {
                return;
            }

            String type = token;
            String name = null;
            token = tokenizer.requireToken();
            if (isIdent(token)) {
                name = token;
                token = tokenizer.requireToken();
            }
            if (",".equals(token)) {
                token = tokenizer.requireToken();
            } else if (")".equals(token)) {
            } else {
                throw new ApiParseException("expected , found " + token, tokenizer.getLine());
            }
            state.addMethodParameter(type);
//            method.addParameter(new ParameterInfo(name, type, Converter.obtainTypeFromString(type),
//                    type.endsWith("..."), tokenizer.pos()));
        }
    }

    private static String parseThrows(Tokenizer tokenizer, ParserState state)
            throws ApiParseException {
        String token = tokenizer.requireToken();
        boolean comma = true;
        while (true) {
            if (";".equals(token)) {
                return token;
            } else if (",".equals(token)) {
                if (comma) {
                    throw new ApiParseException("Expected exception, got ','", tokenizer.getLine());
                }
                comma = true;
            } else {
                if (!comma) {
                    throw new ApiParseException("Expected ',' or ';' got " + token,
                            tokenizer.getLine());
                }
                comma = false;
            }
            token = tokenizer.requireToken();
        }
    }

//    private static String qualifiedName(String pkg, String className, ClassInfo parent) {
//        String parentQName = (parent != null) ? (parent.qualifiedName() + ".") : "";
//        return pkg + "." + parentQName + className;
//    }

    private static boolean isIdent(String token) {
        return isident(token.charAt(0));
    }

    private static void assertIdent(Tokenizer tokenizer, String token) throws ApiParseException {
        if (!isident(token.charAt(0))) {
            throw new ApiParseException("Expected identifier: " + token, tokenizer.getLine());
        }
    }

    static class Tokenizer {
        char[] mBuf;

        String mFilename;

        int mPos;

        int mLine = 1;

        Tokenizer(String filename, char[] buf) {
            mFilename = filename;
            mBuf = buf;
        }

        public int getLine() {
            return mLine;
        }

        boolean eatWhitespace() {
            boolean ate = false;
            while (mPos < mBuf.length && isspace(mBuf[mPos])) {
                if (mBuf[mPos] == '\n') {
                    mLine++;
                }
                mPos++;
                ate = true;
            }
            return ate;
        }

        boolean eatComment() {
            if (mPos + 1 < mBuf.length) {
                if (mBuf[mPos] == '/' && mBuf[mPos + 1] == '/') {
                    mPos += 2;
                    while (mPos < mBuf.length && !isnewline(mBuf[mPos])) {
                        mPos++;
                    }
                    return true;
                }
            }
            return false;
        }

        void eatWhitespaceAndComments() {
            while (eatWhitespace() || eatComment()) {
            }
        }

        public String requireToken() throws ApiParseException {
            return requireToken(true);
        }

        public String requireToken(boolean parenIsSep) throws ApiParseException {
            final String token = getToken(parenIsSep);
            if (token != null) {
                return token;
            } else {
                throw new ApiParseException("Unexpected end of file", mLine);
            }
        }

        public String getToken() throws ApiParseException {
            return getToken(true);
        }

        public String getToken(boolean parenIsSep) throws ApiParseException {
            eatWhitespaceAndComments();
            if (mPos >= mBuf.length) {
                return null;
            }
            final int line = mLine;
            final char c = mBuf[mPos];
            final int start = mPos;
            mPos++;
            if (c == '"') {
                final int STATE_BEGIN = 0;
                final int STATE_ESCAPE = 1;
                int state = STATE_BEGIN;
                while (true) {
                    if (mPos >= mBuf.length) {
                        throw new ApiParseException("Unexpected end of file for \" starting at "
                                + line, mLine);
                    }
                    final char k = mBuf[mPos];
                    if (k == '\n' || k == '\r') {
                        throw new ApiParseException(
                                "Unexpected newline for \" starting at " + line, mLine);
                    }
                    mPos++;
                    switch (state) {
                        case STATE_BEGIN:
                            switch (k) {
                                case '\\':
                                    state = STATE_ESCAPE;
                                    mPos++;
                                    break;
                                case '"':
                                    return new String(mBuf, start, mPos - start);
                            }
                        case STATE_ESCAPE:
                            state = STATE_BEGIN;
                            break;
                    }
                }
            } else if (issep(c, parenIsSep)) {
                return "" + c;
            } else {
                int genericDepth = 0;
                do {
                    while (mPos < mBuf.length && !isspace(mBuf[mPos])
                            && !issep(mBuf[mPos], parenIsSep)) {
                        mPos++;
                    }
                    if (mPos < mBuf.length) {
                        if (mBuf[mPos] == '<') {
                            genericDepth++;
                            mPos++;
                        } else if (mBuf[mPos] == '>') {
                            genericDepth--;
                            mPos++;
                        } else if (genericDepth != 0) {
                            mPos++;
                        }
                    }
                } while (mPos < mBuf.length
                        && ((!isspace(mBuf[mPos]) && !issep(mBuf[mPos], parenIsSep)) || genericDepth != 0));
                if (mPos >= mBuf.length) {
                    throw new ApiParseException(
                            "Unexpected end of file for \" starting at " + line, mLine);
                }
                return new String(mBuf, start, mPos - start);
            }
        }
    }

    static boolean isspace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }

    static boolean isnewline(char c) {
        return c == '\n' || c == '\r';
    }

    static boolean issep(char c, boolean parenIsSep) {
        if (parenIsSep) {
            if (c == '(' || c == ')') {
                return true;
            }
        }
        return c == '{' || c == '}' || c == ',' || c == ';' || c == '<' || c == '>';
    }

    static boolean isident(char c) {
        if (c == '"' || issep(c, true)) {
            return false;
        }
        return true;
    }
}
