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

package com.android.ide.eclipse.gltrace;

import com.android.ide.eclipse.gldebugger.GLEnum;
import com.android.ide.eclipse.gltrace.Glcall.GLCall;
import com.android.ide.eclipse.gltrace.Glcall.GLCall.DataType;
import com.android.ide.eclipse.gltrace.Glcall.GLCall.DataType.Type;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GLCallFormatter is used to format and create a string representation for a protobuf encoded
 * {@link GLCall}. It reads in the GL API definitions from a file to know about all the GL calls,
 * their return types and their arguments. Using this information, each GLCall is
 * parsed and formatted appropriately for display.
 */
public class GLCallFormatter {
    private static final String GLES2_ENTRIES_HEADER_V1 =
            "# com.android.ide.eclipse.gltrace.glentries, v1";

    private Map<String, GLAPISpec> mAPISpecs = new HashMap<String, GLCallFormatter.GLAPISpec>(400);

    /**
     * Initialize the GL Function Call formatter
     * @param specs OpenGL API specs
     */
    public GLCallFormatter(InputStream specs) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(specs));

        try{
            String header = reader.readLine().trim();
            assert header.equals(GLES2_ENTRIES_HEADER_V1);

            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }

                // strip away the comments
                int commentPos = line.indexOf('#');
                if (commentPos > 0) {
                    line = line.substring(0, commentPos);
                }
                line = line.trim();

                // parse non empty lines
                if (line.length() > 0) {
                    parseLine(line);
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Unexpected IO Exception while parsing GL specs", e);
        }

        try {
            specs.close();
        } catch (IOException e) {
            // ignore exception while closing stream
        }
    }

    private void parseLine(String line) {
        // Every line is of the following format:
        // <ret_type> <func_name> <args>
        // where <args> = void | [const] <arg_type> <arg_name>
        List<String> words = Arrays.asList(line.split(","));

        String retType = words.get(0).trim();
        String func = words.get(1).trim();
        List<String> argDefinitions = words.subList(2, words.size());

        List<GLArgumentSpec> glArgs = new ArrayList<GLArgumentSpec>(argDefinitions.size()/2);
        for (String argDefn: argDefinitions) {
            // an argDefn is something like: "const GLvoid* data"
            argDefn = argDefn.trim();
            int lastSeparator = argDefn.lastIndexOf(' ');
            if (lastSeparator == -1) {
                // no space => a void type with no argument name
                glArgs.add(new GLArgumentSpec(argDefn, null));
            } else {
                // everything upto the last space is the type, and the last word is the variable
                // name
                glArgs.add(new GLArgumentSpec(argDefn.substring(0, lastSeparator),
                        argDefn.substring(lastSeparator + 1)));
            }
        }

        mAPISpecs.put(func, new GLAPISpec(func, retType, glArgs));
    }

    public String formatGLCall(GLCall glCall) {
        GLAPISpec apiSpec = mAPISpecs.get(glCall.getFunction().toString());
        if (apiSpec == null) {
            return null;
        }

        return String.format("%s %s(%s)", apiSpec.getReturnType(),
                apiSpec.getFunction(),
                formatArgs(glCall, apiSpec.getArgs()));
    }

    private String formatArgs(GLCall glCall, List<GLArgumentSpec> argSpecs) {
        StringBuffer sb = new StringBuffer();

        for (int i = 0; i < argSpecs.size(); i++) {
            GLArgumentSpec argSpec = argSpecs.get(i);

            Type typeSpec = argSpec.getArgType();
            if (typeSpec == Type.VOID) {
                sb.append("void");
            } else {
                sb.append(argSpec.getArgName());
                sb.append(" = ");
                sb.append(formatArgValue(glCall.getArgs(i), typeSpec));
            }

            if (i < argSpecs.size() - 1) {
                sb.append(", ");
            }
        }

        return sb.toString();
    }

    private String formatArgValue(DataType arg, Type typeSpec) {
        if (arg.getIsArray()) {
            return formatPointer(arg);
        }

        switch (typeSpec) {
            case VOID:
                return "";
            case BOOLEAN:
                return Boolean.toString(arg.getBoolValue(0));
            case FLOAT:
                return String.format("%f", arg.getFloatValue(0));
            case INT:
                return Integer.toString(arg.getIntValue(0));
            case ENUM:
                return GLEnum.valueOf(arg.getIntValue(0)).toString();
            default:
                return "(unknown type)";
        }
    }

    private String formatPointer(DataType args) {
        // Display as array if possible
        switch (args.getType()) {
            case BOOLEAN:
                return args.getBoolValueList().toString();
            case FLOAT:
                return args.getFloatValueList().toString();
            case INT:
                return args.getIntValueList().toString();
            case CHAR:
                return args.getCharValueList().get(0).toStringUtf8();
        }

        // We have a pointer, but we don't have the data pointed to.
        // Just format and return the pointer (points to device memory)
        if (args.getIntValue(0) == 0) {
            return "NULL";
        } else {
            return String.format("0x%x", args.getIntValue(0));
        }
    }

    private static class GLAPISpec {
        private final String mGLFunction;
        private final String mReturnType;
        private final List<GLArgumentSpec> mArgs;

        public GLAPISpec(String glFunction, String returnType, List<GLArgumentSpec> args) {
            mGLFunction = glFunction;
            mReturnType = returnType;
            mArgs = args;
        }

        public String getFunction() {
            return mGLFunction;
        }

        public String getReturnType() {
            return mReturnType;
        }

        public List<GLArgumentSpec> getArgs() {
            return mArgs;
        }
    }

    private static class GLArgumentSpec {
        private final Type mType;
        private final String mName;

        public GLArgumentSpec(String type, String name) {
            mType = getType(type);
            mName = name;
        }

        private Type getType(String type) {
            type = type.toLowerCase();

            // We use type.contains() rather than type.equals since we are matching against
            // the type name along with qualifiers. e.g. "void", "GLvoid" and "void*" should
            // all be assigned the same type.
            if (type.contains("boolean")) {
                return Type.BOOLEAN;
            } else if (type.contains("enum")) {
                return Type.ENUM;
            } else if (type.contains("float") || type.contains("clampf")) {
                return Type.FLOAT;
            } else if (type.contains("void")) {
                return Type.VOID;
            } else if (type.contains("char")) {
                return Type.CHAR;
            } else {
                // Matches all of the following types:
                // glclampx, gluint, glint, glshort, glsizei, glfixed,
                // glsizeiptr, glintptr, glbitfield, glfixed, glubyte.
                // We might do custom formatting for these types in the future.
                return Type.INT;
            }
        }

        public Type getArgType() {
            return mType;
        }

        public String getArgName() {
            return mName;
        }
    }
}
