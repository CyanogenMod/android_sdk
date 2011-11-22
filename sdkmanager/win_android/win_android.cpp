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

/*
 * "win_android.exe", for Windows only. Replaces the previous android.bat.
 * In the final SDK, this is what becomes tools\android.exe and it runs
 * the UI for the SDK Manager or the AVD Manager.
 *
 * Implementation details:
 * - We don't have access to ATL or MFC.
 * - We don't want to pull in things like STL.
 * - No Unicode/MBCS support for now.
 */

#ifdef _WIN32

#include "utils.h"
#include "find_java.h"


// A NULL-terminated list of directory to create in the temp folder.
static const char * sMkDirList[] = {
    "lib",
    "lib\\x86",
    "lib\\x86_64",
    NULL,
};

// A NULL-terminated list of file patterns to copy in the temp folder.
// The folders must be listed in sMkDirList
static const char * sFilesToCopy[] = {
    "lib\\x86\\swt.jar",
    "lib\\x86_64\\swt.jar",
    "lib\\androidprefs.jar",
    "lib\\org.eclipse.*",
    "lib\\sdk*",
    "lib\\common.jar",
    "lib\\commons-compress*",
    "lib\\swtmenubar.jar",
    "lib\\commons-logging*",
    "lib\\commons-codec*",
    "lib\\httpclient*",
    "lib\\httpcore*",
    "lib\\httpmime*",
    NULL,
};


// Creates a directory named dirLeafName in the TEMP directory.
// Returns the path in outDir on success.
static bool mkTempDir(const char *dirLeafName, CPath *outDir) {
    SetLastError(0);
    char tempPath[MAX_PATH + 1] = "";
    DWORD len = GetTempPathA(MAX_PATH, tempPath);
    if (len > 0 && len <= MAX_PATH) {
        _ASSERT(tempPath[len-1] == '\\');
        _ASSERT(len + strlen(dirLeafName) < MAX_PATH);
        if (len + strlen(dirLeafName) >= MAX_PATH) {
            displayLastError("TEMP path too long to create a temporary directory: %s", tempPath);
            return false;
        }
        strcat(tempPath, dirLeafName);
        outDir->set(tempPath);

        if (outDir->dirExists() ||
            CreateDirectoryA(tempPath, NULL /*lpSecurityAttributes*/) != 0) {
            return true;
        }
    }
    displayLastError("Failed to create a temporary directory: %s", tempPath);
    return false;
}

// Creates all the directories from sMkDirList in the specified base tmpDir.
static bool mkDirs(const char *tmpDir, const char * dirList[]) {
    SetLastError(0);
    for (const char **dir = dirList; *dir != NULL; dir++) {
        CPath path(tmpDir);
        path.addPath(*dir);
        if (!path.dirExists()) {
            if (!CreateDirectoryA(path.cstr(), NULL /*lpSecurityAttributes*/)) {
                displayLastError("Failed to create directory: %s", path.cstr());
                return false;
            }
        }
    }
    return true;
}

static bool copyFiles(const char *toolsDir, const char *tmpDir, const char *globList[]) {
    SetLastError(0);
    WIN32_FIND_DATAA srcFindData;
    WIN32_FIND_DATAA destFindData;
    for (const char **glob = globList; *glob != NULL; glob++) {
        CPath globDir = CPath(*glob).dirName();

        CPath fullGlob(toolsDir);
        fullGlob.addPath(*glob);

        HANDLE srcH = FindFirstFileA(fullGlob.cstr(), &srcFindData);
        if (srcH == INVALID_HANDLE_VALUE) {
            displayLastError("Failed to list files: %s", *glob);
            return false;
        }
        do {
            // Skip directories
            if ((srcFindData.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) != 0) {
                continue;
            }
            CPath srcPath(toolsDir);
            srcPath.addPath(globDir).addPath(srcFindData.cFileName);

            CPath destPath(tmpDir);
            destPath.addPath(globDir).addPath(srcFindData.cFileName);

            // Skip copy if files are likely to not have changed.
            HANDLE destH = FindFirstFileA(destPath.cstr(), &destFindData);
            if (destH != INVALID_HANDLE_VALUE) {
                // Size must be same for us to skip it.
                if (srcFindData.nFileSizeHigh == destFindData.nFileSizeHigh &&
                    srcFindData.nFileSizeLow  == destFindData.nFileSizeLow) {
                    // Creation & access times can differ. However if the dest write time
                    // is >= than the source write time, it should be the same file.
                    LARGE_INTEGER srcWriteTime;
                    LARGE_INTEGER dstWriteTime;
                    srcWriteTime.HighPart = srcFindData.ftLastWriteTime.dwHighDateTime;
                    srcWriteTime.LowPart  = srcFindData.ftLastWriteTime.dwLowDateTime;
                    dstWriteTime.HighPart = destFindData.ftLastWriteTime.dwHighDateTime;
                    dstWriteTime.LowPart  = destFindData.ftLastWriteTime.dwLowDateTime;
                    if (dstWriteTime.QuadPart >= srcWriteTime.QuadPart) {
                        FindClose(destH);
                        continue;
                    }
                }

                FindClose(destH);

                // CopyFile copies some attributes. It's common for tools to be unzipped
                // as read-only so we need to remove any r-o attribute on existing
                // files if we want a recopy to succeed.
                if ((destFindData.dwFileAttributes && FILE_ATTRIBUTE_READONLY) != 0) {
                    SetFileAttributes(destPath.cstr(),
                        destFindData.dwFileAttributes ^ FILE_ATTRIBUTE_READONLY);
                }
            }

            if (!CopyFileA(srcPath.cstr(), destPath.cstr(), false /*bFailIfExists*/)) {
                FindClose(srcH);
                displayLastError("Failed to copy file: %s", destPath.cstr());
                return false;
            }
        } while (FindNextFileA(srcH, &srcFindData) != 0);
        FindClose(srcH);
    }
    return true;
}

static bool execSdkManager(const char *javaPath,
                           const char *toolsDir,
                           const char *tmpDir,
                           const char *lpCmdLine) {
    SetLastError(0);

    CPath javawPath(javaPath);
    javawPath.replaceName("java.exe", "javaw.exe");
    // Only accept it if we can actually find the exec
    PVOID oldWow64Value = disableWow64FsRedirection();
    if (!javawPath.fileExists()) {
        javawPath.set(javaPath);
    }
    revertWow64FsRedirection(&oldWow64Value);

    // Check whether the underlying system is x86 or x86_64.
    // We use GetSystemInfo which will see the one masqueraded by Wow64.
    // (to get the real info, we would use GetNativeSystemInfo instead.)
    SYSTEM_INFO sysInfo;
    GetSystemInfo(&sysInfo);

    CString arch("x86");
    if (sysInfo.wProcessorArchitecture == PROCESSOR_ARCHITECTURE_AMD64) {
        arch.set("x86_64");
    } else if (sysInfo.wProcessorArchitecture == PROCESSOR_ARCHITECTURE_INTEL) {
        // Skip this. We'll just assume x86 and let it fail later.
        // displayLastError("Unknown Processor Architecture: %d", sysInfo.wProcessorArchitecture);
        // return false;
    }

    // Now build the command line.
    // Note that we pass the absolute javawPath to execNoWait
    // and the first parameter is just for the show.
    // Important: for the classpath to be able to contain "lib\\sdkmanager.jar", etc.,
    // we need to set the toolsDir as the *temp* directory in execNoWait.
    // It's important to not use toolsDir otherwise it would lock that diretory.

    // Tip: to connect the Java debugging to a running process, add this to the Java command line:
    // "-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8000"

    CString cmdLine;
    cmdLine.setf("\"%s\" "                                   // javaPath
                 "-Dcom.android.sdkmanager.toolsdir=\"%s\" " // toolsDir
                 "-Dcom.android.sdkmanager.workdir=\"%s\" "  // workDir==toolsdir
                 "-classpath \"lib\\sdkmanager.jar;lib\\swtmenubar.jar;lib\\%s\\swt.jar\" " // arch
                 "com.android.sdkmanager.Main "
                 "%s",                                       // extra parameters
        javawPath.baseName(), toolsDir, tmpDir, arch.cstr(), lpCmdLine);

    if (gDebug) msgBox("Executing: %s", cmdLine.cstr());

    if (!execNoWait(javawPath.cstr(), cmdLine.cstr(), tmpDir)) {
        displayLastError("Failed to run %s", cmdLine.cstr());
        return false;
    }

    return true;
}

int APIENTRY WinMain(HINSTANCE hInstance,
                     HINSTANCE hPrevInstance,
                     LPTSTR    lpCmdLine,
                     int       nCmdShow) {

    gDebug = (getenv("ANDROID_SDKMAN_DEBUG") != NULL);

    PVOID oldWow64Value = disableWow64FsRedirection();

    CPath javaPath;
    if (!findJavaInEnvPath(&javaPath) &&
        !findJavaInRegistry(&javaPath) &&
        !findJavaInProgramFiles(&javaPath)) {
        msgBox("Failed to find Java on your system. Please reinstall it.");
        return 2;
    }
    _ASSERT(!javaPath.isEmpty());

    revertWow64FsRedirection(oldWow64Value);

    // For debugging it's more convenient to be able to override the tools directory location
    CPath toolsDir(getenv("ANDROID_SDKMAN_TOOLS_DIR"));
    if (toolsDir.isEmpty()) {
        if (!getModuleDir(&toolsDir)) {
            displayLastError("Failed to get program's filename: ");
            return 1;
        }
    }
    _ASSERT(!toolsDir.isEmpty());

    CPath tmpDir;
    if (!mkTempDir("temp-android-tool", &tmpDir)) {
        return 1;
    }
    _ASSERT(!tmpDir.isEmpty());

    if (!mkDirs(tmpDir.cstr(), sMkDirList)) {
        return 1;
    }

    if (!copyFiles(toolsDir.cstr(), tmpDir.cstr(), sFilesToCopy)) {
        return 1;
    }

    if (!execSdkManager(javaPath.cstr(), toolsDir.cstr(), tmpDir.cstr(), lpCmdLine)) {
        displayLastError("Failed to start SDK Manager: ");
        return 1;
    }

    return 0;
}
#endif /* _WIN32 */
