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

package com.android.apigenerator.enumfix;

import com.android.apigenerator.ApiClass;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * This codes looks at all the android.jar in an SDK and use ASM to figure out when enums
 * where introduced. This is a one time thing that creates the file
 * /com/android/apichecker/generator/enums.xml which is then used to create the final API file.
 *
 */
public class AndroidJarReader {

    // this the last API until we switched to a new API format that included enum values.
    private final static int MAX_API = 13;
    private static final byte[] BUFFER = new byte[65535];

    private final String mSdkFolder;

    public AndroidJarReader(String sdkFolder) {
        mSdkFolder = sdkFolder;
    }

    public Map<String, ApiClass> getEnumClasses() {
        HashMap<String, ApiClass> map = new HashMap<String, ApiClass>();

        // Get all the android.jar. They are in platforms-#
        for (int apiLevel = 1 ; apiLevel <= MAX_API ; apiLevel++) {
            try {
                File jar = new File(mSdkFolder, "platforms/android-" + apiLevel + "/android.jar");
                if (jar.exists() == false) {
                    System.err.println("Missing android.jar for API level " + apiLevel);
                    continue;
                }

                FileInputStream fis = new FileInputStream(jar);
                ZipInputStream zis = new ZipInputStream(fis);
                ZipEntry entry = zis.getNextEntry();
                while (entry != null) {
                    String name = entry.getName();

                    if (name.endsWith(".class")) {

                        int index = 0;
                        do {
                            int size = zis.read(BUFFER, index, BUFFER.length - index);
                            if (size >= 0) {
                                index += size;
                            } else {
                                break;
                            }
                        } while (true);

                        byte[] b = new byte[index];
                        System.arraycopy(BUFFER, 0, b, 0, index);

                        ClassReader reader = new ClassReader(b);
                        ClassNode classNode = new ClassNode();
                        reader.accept(classNode, 0 /*flags*/);

                        if (classNode != null && classNode.superName != null &&
                                classNode.superName.equals("java/lang/Enum")) {

                            ApiClass theClass = addClass(map, classNode.name, apiLevel);
                            theClass.addSuperClass("java/lang/Enum", apiLevel);

                            List fields = classNode.fields;
                            for (Object f : fields) {
                                FieldNode fnode = (FieldNode) f;
                                if (fnode.desc.substring(1, fnode.desc.length() - 1).equals(classNode.name)) {
                                    theClass.addField(fnode.name, apiLevel);
                                }
                            }
                        }
                    }
                    entry = zis.getNextEntry();
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {

            }
        }

        return map;
    }

    private ApiClass addClass(HashMap<String, ApiClass> classes, String name, int apiLevel) {
        ApiClass theClass = classes.get(name);
        if (theClass == null) {
            theClass = new ApiClass(name, apiLevel);
            classes.put(name, theClass);
        }

        return theClass;
    }

}
