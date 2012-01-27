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

/*
 * "find_java.exe", for Windows only.
 * Tries to find a Java binary in a variety of places and prints the
 * first one found on STDOUT and returns 0.
 *
 * If not found, returns error 1 with no message
 * (unless ANDROID_SDKMAN_DEBUG or -d if set, in which case there's a message on STDERR).
 *
 * Implementation details:
 * - We don't have access to ATL or MFC.
 * - We don't want to pull in things like STL.
 * - No Unicode/MBCS support for now.
 *
 * TODO for later version:
 * - provide an env variable to let users override which version is being used.
 * - if there's more than one java.exe found, enumerate them all.
 * - and in that case take the one with the highest Java version number.
 * - since that operation is expensive, do it only once and cache the result
 *   in a temp file. If the temp file is not found or the java binary no
 *   longer exists, re-run the enumaration.
 */

#ifdef _WIN32

#include "utils.h"
#include "find_java.h"
#include <io.h>
#include <fcntl.h>

static void testFindJava() {

    CPath javaPath("<not found>");
    bool ok = findJavaInEnvPath(&javaPath);
    printf("findJavaInEnvPath: [%s] %s\n", ok ? "OK" : "FAIL", javaPath.cstr());

    javaPath.set("<not found>");
    ok = findJavaInRegistry(&javaPath);
    printf("findJavaInRegistry [%s] %s\n", ok ? "OK" : "FAIL", javaPath.cstr());

    javaPath.set("<not found>");
    ok = findJavaInProgramFiles(&javaPath);
    printf("findJavaInProgramFiles [%s] %s\n", ok ? "OK" : "FAIL", javaPath.cstr());
}


int main(int argc, char* argv[]) {

    gIsConsole = true; // tell utils to to print errors to stderr
    gIsDebug = (getenv("ANDROID_SDKMAN_DEBUG") != NULL);
    bool doShortPath = false;
    bool doVersion = false;

    for (int i = 1; i < argc; i++) {
        if (strncmp(argv[i], "-t", 2) == 0) {
            testFindJava();
            return 0;

        } else if (strncmp(argv[i], "-d", 2) == 0) {
            gIsDebug = true;

        } else if (strncmp(argv[i], "-s", 2) == 0) {
            doShortPath = true;

        } else if (strncmp(argv[i], "-v", 2) == 0) {
            doVersion = true;

        } else {
            printf(
                "Outputs the path of the first Java.exe found on the local system.\n"
                "Returns code 0 when found, 1 when not found.\n"
                "Options:\n"
                "-h / -help   : This help.\n"
                "-t / -test   : Internal test.\n"
                "-s / -short  : Print path in short DOS form.\n"
                "-v / -version: Only prints the Java version found.\n"
                );
            return 2;
        }
    }

    CPath javaPath;
    if (!findJavaInEnvPath(&javaPath) &&
        !findJavaInRegistry(&javaPath) &&
        !findJavaInProgramFiles(&javaPath)) {
            if (gIsDebug) {
                fprintf(stderr, "Failed to find Java on your system.\n");
            }
            return 1;
    }
    _ASSERT(!javaPath.isEmpty());

    if (doShortPath) {
        if (!javaPath.toShortPath(&javaPath)) {
            fprintf(stderr,
                "Failed to convert path to a short DOS path: %s\n",
                javaPath.cstr());
            return 1;
        }
    }

    if (doVersion) {
        // Print version found
        CString version;
        if (getJavaVersion(javaPath, &version)) {
            printf("%s", version.cstr());
            return 0;
        } else {
            fprintf(stderr, "Failed to get version of %s\n", javaPath.cstr());
        }
    }

    // Print java.exe path found
    printf("%s", javaPath.cstr());
    return 0;
}

#endif /* _WIN32 */