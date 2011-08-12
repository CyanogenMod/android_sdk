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

package com.android.sdklauncher;

import java.awt.*;
import java.awt.event.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * Main class for the sdklauncher application.
 * <p/>
 * Right now this is just experimental.
 * This is designed to eventually replace the current sdklauncher.exe
 * that is written in C++, which merely runs the android.bat.
 * <p/>
 * The new workflow is to have:
 * - $SDK/tools/lib/sdkmanager.jar
 * - $SDK/SDK Manager.exe
 * - $SDK/AVD Manager.exe
 *
 * The 2 exe in the root of the $SDK are created using jsmooth
 * and directly *embed* (not call) the equivalent of sdklauncher.jar.
 * The launcher copies all the JARs to run to the TEMP dir and loads
 * them from there, thus ideally removing any lock to the tools
 * directory. In case of tools update, the sdkmanager.jar sends a
 * signal to the launcher to restart itself after updating the temp copy.
 */
public class Main {

    /**
     * Title shown in the AWT message box in case of error.
     */
    private static final String AWT_MSG_BOX_TITLE = "Android SDK Error";

    /**
     * The main class of the SdkManager
     */
    private static final String SDK_MAN_MAIN_CLASS = "com.android.sdkmanager.Main";

    // these must match the Main from Sdk Manager

    /** Java property that defines the location of the sdk/tools directory. */
    private final static String TOOLSDIR = "com.android.sdkmanager.toolsdir";
    /** Java property that defines the working directory. On Windows the current working directory
     *  is actually the tools dir, in which case this is used to get the original CWD. */
    private final static String WORKDIR = "com.android.sdkmanager.workdir";



    public static void main(String[] args) {
        new Main().run(args);
    }

    /**
     * Runs the sdk manager app.
     * <p/>
     * Command-line arguments will be directly given to the SDK Manager.jar.
     * Tyicall this would be just "sdk" or "avd" to display the corresponding manager.
     */
    private void run(String[] args) {
        // Get current directory, which should be where this app is running from
        File currDir = new File(".");
        try {
            currDir = new File(System.getProperty("user.dir"));

            // Set the tools dir and working dir properties
            System.setProperty(TOOLSDIR, mkPath(currDir, "tools").getAbsolutePath());
            System.setProperty(WORKDIR, currDir.getAbsolutePath());

            // Load sdkmanager in a custom class loader with the following
            // class path, in that specific order:
            // - $SDK/tools/lib/sdkmanager.jar
            // - $SDK/tools/lib/*
            // - $SDK/tools/lib/$ARCH/swt.jar

            File libDir    = mkPath(currDir, "tools", "lib");
            File sdkManJar = mkPath(libDir,  "sdkmanager.jar");
            File archDir   = mkPath(libDir,  archQuery());
            File swtJar    = mkPath(archDir, "swt.jar");

            if (!sdkManJar.exists()) {
                // Probably not started from an SDK directory...
                throw new FileNotFoundException(sdkManJar.getAbsolutePath());
            }
            if (!swtJar.exists()) {
                // Could the architecture be unsupported?
                throw new FileNotFoundException(swtJar.getAbsolutePath());
            }

            URL[] urls = {
                    sdkManJar.toURI().toURL(),
                    libDir.toURI().toURL(),
                    swtJar.toURI().toURL()
                    };
            URLClassLoader loader = new URLClassLoader(urls);

            Class<?> clazz = loader.loadClass(SDK_MAN_MAIN_CLASS);
            Method m = clazz.getMethod("main", new Class[] { String[].class });
            m.invoke(null, new Object[] { args });

            System.exit(0);

        } catch(Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            sw.flush();

            showAwtDialog(sw.toString());

            System.exit(1);
        }
    }

    /**
     * Appends one or more path components to a base directory.
     */
    private File mkPath(File baseDir, String...paths) {
        for (String path : paths) {
            baseDir = new File(baseDir, path);
        }
        return baseDir;
    }

    /**
     * Returns the current architecture string, e.g. "x86" or "x86_64".
     * This matches the folder name at $SDK/tools/lib/$ARCH.
     *
     * This code is extracted from sdk/archquery/src/.../Main.java
     */
    private String archQuery() {
        // Values listed from http://lopica.sourceforge.net/os.html
        String arch = System.getProperty("os.arch");

        if (arch.equalsIgnoreCase("x86_64") || arch.equalsIgnoreCase("amd64")) {
            return "x86_64";

        } else if (arch.equalsIgnoreCase("x86")
                || arch.equalsIgnoreCase("i386")
                || arch.equalsIgnoreCase("i686")) {
            return "x86";

        } else if (arch.equalsIgnoreCase("ppc") || arch.equalsIgnoreCase("PowerPC")) {
            return "ppc";

        } else {
            return arch;
        }
    }

    /**
     * Displays the given text content in a message box-like AWT window.
     * We surely don't have SWT or Swing or anything fancy in case of error so AWT will have to do.
     */
    private void showAwtDialog(String content) {
        Frame f = new Frame();
        AwtErrorDialog mb = new AwtErrorDialog(f, content);
        mb.dispose();
    }

    /**
     * A simple AWT window with one "Close" button that displays whatever
     * error we might have to show the user. It uses a TextArea and so can
     * display multi-line content with scrollbars.
     */
    private static class AwtErrorDialog extends Dialog {

        public AwtErrorDialog(Frame f, String content) {
            super(f, AWT_MSG_BOX_TITLE, true /*modal*/);
            setLayout(new BorderLayout());
            add(new TextArea(content), BorderLayout.CENTER);

            Panel bottom = new Panel();
            add(bottom, BorderLayout.SOUTH);
            Button close = new Button("Close");
            bottom.add(close);
            close.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    setVisible(false);
                }
            });

            this.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    setVisible(false);
                }
            });

            pack();
            setVisible(true);
        }
    }


}
