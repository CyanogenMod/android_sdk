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

#ifdef _WIN32

#include "find_java.h"

#define _CRT_SECURE_NO_WARNINGS 1

extern bool gDebug;

// Search java.exe in the path
bool findJavaInEnvPath(CPath *outJavaPath) {
    SetLastError(0);
    const char* envPath = getenv("PATH");
    if (!envPath) return false;

    CArray<CString> *paths = CString(envPath).split(';');
    for(int i = 0; i < paths->size(); i++) {
        CPath p((*paths)[i].cstr());
        p.addPath("java.exe");
        if (p.fileExists()) {
            // Make sure we can actually run "java -version".
            CString cmd;
            cmd.setf("\"%s\" -version", p.cstr());
            int code = execWait(cmd.cstr());
            if (code == 0) {
                if (gDebug) msgBox("Java found via env path: %s", p.cstr());
                outJavaPath->set(p.cstr());
                delete paths;
                return true;
            }
        }
    }

    delete paths;
    return false;
}

bool findJavaInRegistry(CPath *outJavaPath) {
    // TODO
    return false;
}

bool findJavaInProgramFiles(CPath *outJavaPath) {
    // TODO
    return false;
}

#endif /* _WIN32 */
