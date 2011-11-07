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

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public class MainTest extends TestCase {
    public void testWrap() {
        String s =
            "Hardcoding text attributes directly in layout files is bad for several reasons:\n" +
            "\n" +
            "* When creating configuration variations (for example for landscape or portrait)" +
            "you have to repeat the actual text (and keep it up to date when making changes)\n" +
            "\n" +
            "* The application cannot be translated to other languages by just adding new " +
            "translations for existing string resources.";
        String wrapped = Main.wrap(s, 70, "");
        assertEquals(
            "Hardcoding text attributes directly in layout files is bad for several\n" +
            "reasons:\n" +
            "\n" +
            "* When creating configuration variations (for example for landscape or\n" +
            "portrait)you have to repeat the actual text (and keep it up to date\n" +
            "when making changes)\n" +
            "\n" +
            "* The application cannot be translated to other languages by just\n" +
            "adding new translations for existing string resources.\n",
            wrapped);
    }

    public void testWrapPrefix() {
        String s =
            "Hardcoding text attributes directly in layout files is bad for several reasons:\n" +
            "\n" +
            "* When creating configuration variations (for example for landscape or portrait)" +
            "you have to repeat the actual text (and keep it up to date when making changes)\n" +
            "\n" +
            "* The application cannot be translated to other languages by just adding new " +
            "translations for existing string resources.";
        String wrapped = Main.wrap(s, 70, "    ");
        assertEquals(
            "Hardcoding text attributes directly in layout files is bad for several\n" +
            "    reasons:\n" +
            "    \n" +
            "    * When creating configuration variations (for example for\n" +
            "    landscape or portrait)you have to repeat the actual text (and keep\n" +
            "    it up to date when making changes)\n" +
            "    \n" +
            "    * The application cannot be translated to other languages by just\n" +
            "    adding new translations for existing string resources.\n",
            wrapped);
    }
}
