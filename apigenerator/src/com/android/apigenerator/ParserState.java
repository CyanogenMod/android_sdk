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

import java.util.Map;

/**
 * Parser state used during parsing of the platform API files.
 *
 */
class ParserState {

    private final int mApiLevel;

    private final Map<String, ApiClass> mClasses;

    private String mCurrentPackage;
    private ApiClass mCurrentClass;

    private String mMethodName;
    private StringBuilder mMethodParams = new StringBuilder();
    private String mMethodReturnType;

    ParserState(Map<String, ApiClass> classes, int apiLevel) {
        mClasses = classes;
        mApiLevel = apiLevel;
    }

    Map<String, ApiClass> getClasses() {
        return mClasses;
    }

    void addPackage(String packageName) {
        mCurrentPackage = packageName;
    }

    void addClass(String className) {
        String fqcn = makeJavaClass(mCurrentPackage + "." + className);
        mCurrentClass = addClass(fqcn, mApiLevel);
    }

    void addSuperClass(String superClass) {
        mCurrentClass.addSuperClass(makeJavaClass(superClass), mApiLevel);
    }

    void addInterface(String interfaceClass) {
        mCurrentClass.addInterface(makeJavaClass(interfaceClass), mApiLevel);
    }

    void startNewConstructor() {
        mMethodParams.setLength(0);
        mMethodName = "<init>";
        mMethodReturnType = "V";
    }

    void startNewMethod(String name, String returnType) {
        mMethodParams.setLength(0);
        mMethodName = name;
        mMethodReturnType = parseType(returnType);
    }

    void addMethodParameter(String parameter) {
        mMethodParams.append(parseType(parameter));
    }

    void finishMethod() {
        addMethod(mMethodName + "(" + mMethodParams.toString() + ")" +
                (mMethodReturnType != null ? mMethodReturnType : ""));
    }

    void addMethod(String methodSignature) {
        mCurrentClass.addMethod(methodSignature, mApiLevel);
    }

    void addField(String fieldName) {
        mCurrentClass.addField(fieldName, mApiLevel);
    }

    void finishClass() {
        mCurrentClass = null;
    }

    void finishPackage() {
        finishClass();
        mCurrentPackage = null;
    }

    void done() {
        finishPackage();
    }

    private ApiClass addClass(String name, int apiLevel) {
        ApiClass theClass = mClasses.get(name);
        if (theClass == null) {
            theClass = new ApiClass(name, apiLevel);
            mClasses.put(name, theClass);
        }

        return theClass;
    }


    private String makeJavaClass(String fqcn) {
        final int length = fqcn.length();

        StringBuilder sb = new StringBuilder(length);

        boolean isClass = Character.isUpperCase(fqcn.charAt(0));
        for (int i = 0 ; i < length ; i++) {
            if (fqcn.charAt(i) == '.') {
                if (isClass) {
                    sb.append('$');
                } else {
                    sb.append('/');
                }

                if (i < length -1 ) {
                    isClass = Character.isUpperCase(fqcn.charAt(i+1));
                }
            } else {
                if (fqcn.charAt(i) == '<') {
                    break;
                }

                sb.append(fqcn.charAt(i));
            }
        }

        return sb.toString();
    }

    private String parseType(String type) {
        StringBuilder result = new StringBuilder();

        if (type.endsWith("...")) {
            result.append('[');
            type = type.substring(0, type.length() - 3);
        }

        while (type.endsWith("[]")) {
            result.append('[');
            type = type.substring(0, type.length() - 2);
        }

        if ("byte".equals(type))         result.append('B');
        else if ("char".equals(type))    result.append('C');
        else if ("double".equals(type))  result.append('D');
        else if ("float".equals(type))   result.append('F');
        else if ("int".equals(type))     result.append('I');
        else if ("long".equals(type))    result.append('J');
        else if ("short".equals(type))   result.append('S');
        else if ("void".equals(type))    result.append('V');
        else if ("boolean".equals(type)) result.append('Z');
        else {
            result.append('L').append(makeJavaClass(type)).append(';');
        }

        return result.toString();
    }
}
