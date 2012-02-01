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


import com.android.apigenerator.enumfix.AndroidJarReader;

import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * Main class for command line command to convert the existing API XML/TXT files into diff-based
 * simple text files.
 *
 */
public class Main {

    /**
     * @param args
     */
    public static void main(String[] args) {
        if (args.length < 2 || args.length > 3) {
            printUsage();
        }

        if (args.length == 3) {
            if (args[0].equals("enum")) {
                AndroidJarReader reader = new AndroidJarReader(args[1]);
                Map<String, ApiClass> classes = reader.getEnumClasses();
                createApiFile(new File(args[2]), classes);
            } else {
                printUsage();
            }
        } else {
            Map<String, ApiClass> classes = parsePlatformApiFiles(new File(args[0]));
            createApiFile(new File(args[1]), classes);
        }

    }

    private static void printUsage() {
        System.err.println("Convert API files into a more manageable file\n");
        System.err.println("Usage\n");
        System.err.println("\tApiCheck [enum] FOLDER OUTFILE\n");
        System.exit(1);
    }


    /**
     * Parses platform API files.
     * @param apiFolder the folder containing the files.
     * @return a top level {@link ApiInfo} object for the highest available API level.
     */
    private static Map<String, ApiClass> parsePlatformApiFiles(File apiFolder) {
        int apiLevel = 1;

        Map<String, ApiClass> map = new HashMap<String, ApiClass>();

        InputStream stream = Main.class.getResourceAsStream(
                "enums.xml");
        if (stream != null) {
            map = EnumParser.parseApi(stream);
        }

        if (map == null) {
            map = new HashMap<String, ApiClass>();
        }

        while (true) {
            File file = new File(apiFolder, Integer.toString(apiLevel) + ".xml");
            if (file.exists()) {
                parseXmlApiFile(file, apiLevel, map);
                apiLevel++;
            } else {
                file = new File(apiFolder, Integer.toString(apiLevel) + ".txt");
                if (file.exists()) {
                    parseTxtApiFile(file, apiLevel, map);
                    apiLevel++;

                } else {
                    break;
                }
            }
        }

        return map;
    }

    private static void parseTxtApiFile(File apiFile, int api, Map<String, ApiClass> map) {
        try {
            NewApiParser.parseApi(apiFile.getName(), new FileInputStream(apiFile), map, api);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (ApiParseException e) {
            e.printStackTrace();
        }
    }

    private static void parseXmlApiFile(File apiFile, int apiLevel,
            Map<String, ApiClass> map) {
        try {
            SAXParserFactory parserFactory = SAXParserFactory.newInstance();
            SAXParser parser = parserFactory.newSAXParser();
            parser.parse(new FileInputStream(apiFile), new XmlApiParser(map, apiLevel));
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates the simplified diff-based API level.
     * @param outFolder the out folder.
     * @param classes
     */
    private static void createApiFile(File outFile, Map<String, ApiClass> classes) {

        PrintStream ps = null;
        try {
            ps = new PrintStream(outFile);
            ps.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
            ps.println("<api version=\"1\">");
            TreeMap<String, ApiClass> map = new TreeMap<String, ApiClass>(classes);
            for (ApiClass theClass : map.values()) {
                (theClass).print(ps);
            }
            ps.println("</api>");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            if (ps != null) {
                ps.close();
            }
        }
    }
}
