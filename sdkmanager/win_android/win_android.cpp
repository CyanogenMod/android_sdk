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

#define _CRT_SECURE_NO_WARNINGS 1

#include <direct.h>
#include <stdio.h>
#include <stdarg.h>
#include <string.h>
#include <windows.h>

// VS vs MINGW specific includes
#ifdef USE_MINGW
    #define _ASSERT(x)      // undef
#else
    #include <crtdbg.h>     // for _ASSERT
#endif

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

static bool gDebug = false;


// An array that knows its own size. Not dynamically resizable.
template <class T> class CArray {
    T* mPtr;
    int mSize;
public:
    CArray(int size) {
        mSize = size;
        mPtr = new T[size];
    }

    ~CArray() {
        if (mPtr != NULL) {
            delete[] mPtr;
            mPtr = NULL;
        }
        mSize = 0;
    }

    T& operator[](int i) {
        _ASSERT(i >= 0 && i < mSize);
        return mPtr[i];
    }

    int size() {
        return mSize;
    }
};

// A simple string class wrapper.
class CString {
protected:
    char *mStr;
public:
    CString() { mStr = NULL; }
    CString(const CString &str) { mStr = str.mStr == NULL ? NULL : _strdup(str.mStr); }
    CString(const char *str) { mStr = NULL; set(str); }
    CString(const char *start, int length) { mStr = NULL; set(start, length); }

    CString& set(const char *str) {
        _free();
        if (str != NULL) {
            mStr = _strdup(str);
        }
        return *this;
    }

    CString& set(const char *start, int length) {
        _free();
        if (start != NULL) {
            mStr = (char *)malloc(length + 1);
            strncpy(mStr, start, length);
            mStr[length] = 0;
        }
        return *this;
    }

    CString& setv(const char *str, va_list ap) {
        _free();
        // _vscprintf(str, ap) is only available with the MSVCRT, not MinGW.
        // Instead we'll iterate till we have enough space to generate the string.
        int len = strlen(str) + 1024;
        mStr = (char *)malloc(len);
        strcpy(mStr, str); // provide a default in case vsnprintf totally fails
        for (int guard = 0; guard < 10; guard++) {
            int ret = vsnprintf(mStr, len, str, ap);
            if (ret == -1) {
                // Some implementations don't give the proper size needed
                // so double the space and try again.
                len *= 2;
            } else if (ret >= len) {
                len = ret + 1;
            } else {
                // There was enough space to write.
                break;
            }
            mStr = (char *)realloc((void *)mStr, len);
            strcpy(mStr, str); // provide a default in case vsnprintf totally fails
        }
        return *this;
    }

    CString& setf(const char *str, ...) {
        _free();
        va_list ap;
        va_start(ap, str);
        setv(str, ap);
        va_end(ap);
        return *this;
    }

    virtual ~CString() { _free(); }

    // Returns the C string owned by this CString. It will be
    // invalid as soon as this CString is deleted or out of scope.
    operator const char* () {
        return mStr;
    }

    bool isEmpty() {
        return mStr == NULL || *mStr == 0;
    }

    int length() {
        return mStr == NULL ? 0 : strlen(mStr);
    }

    CString& add(const char *s) {
        if (mStr == NULL) {
            set(s);
        } else {
            mStr = (char *)realloc((void *)mStr, strlen(mStr) + strlen(s) + 1);
            strcat((char *)mStr, s);
        }
        return *this;
    }

    CArray<CString> * split(char sep) {
        if (mStr == NULL) {
            return new CArray<CString>(0);
        }
        const char *last = NULL;
        int n = 0;
        for (const char *s = mStr; *s; s++) {
            if (*s == sep && s != mStr && (last == NULL || s > last+1)) {
                n++;
                last = s;
            }
        }

        CArray<CString> *result = new CArray<CString>(n);
        last = NULL;
        n = 0;
        for (const char *s = mStr; *s; s++) {
            if (*s == sep) {
                if (s != mStr && (last == NULL || s > last+1)) {
                    const char *start = last ? last : mStr;
                    (*result)[n++].set(start, s-start);
                }
                last = s+1;
            }
        }

        return result;
    }

private:
    void _free() {
        if (mStr != NULL) {
            free((void *)mStr);
            mStr = NULL;
        }
    }

};

// A simple path class wrapper.
class CPath : public CString {
public:
    CPath()                 : CString()    { }
    CPath(const CPath &str) : CString(str) { }
    CPath(const char *str)  : CString(str) { }
    CPath(const char *start, int length) : CString(start, length) { }

    // Appends a path segment, adding a \ as necessary.
    CPath& addPath(const char *s) {
        int n = length();
        if (n > 0 && mStr[n-1] != '\\') add("\\");
        add(s);
        return *this;
    }

    // Returns true if file exist and is not a directory.
    // There's no garantee we have rights to access it.
    bool fileExists() {
        if (mStr == NULL) return false;
        DWORD attribs = GetFileAttributesA(mStr);
        return attribs != INVALID_FILE_ATTRIBUTES &&
             !(attribs & FILE_ATTRIBUTE_DIRECTORY);
    }

    // Returns true if file exist and is a directory.
    // There's no garantee we have rights to access it.
    bool dirExists() {
        if (mStr == NULL) return false;
        DWORD attribs = GetFileAttributesA(mStr);
        return attribs != INVALID_FILE_ATTRIBUTES &&
              (attribs & FILE_ATTRIBUTE_DIRECTORY) != 0;
    }

    // Returns a copy of the directory portion of the path, if any
    CPath dirName() {
        CPath result;
        if (mStr != NULL) {
            char *pos = strrchr(mStr, '\\');
            if (pos != NULL) {
                result.set(mStr, pos - mStr);
            }
        }
        return result;
    }

    // Returns a pointer to the baseName part of the path.
    // It becomes invalid if the path changes.
    const char *baseName() {
        if (mStr != NULL) {
            char *pos = strrchr(mStr, '\\');
            if (pos != NULL) {
                return pos + 1;
            }
        }
        return NULL;
    }

    // If the path ends with the given searchName, replace it by the new name
    void replaceName(const char *searchName, const char* newName) {
        if (mStr == NULL) return;
        int n = length();
        int sn = strlen(searchName);
        if (n < sn) return;
        // if mStr ends with searchName
        if (strcmp(mStr + n - sn, searchName) == 0) {
            int sn2 = strlen(newName);
            if (sn2 > sn) {
                mStr = (char *)realloc((void *)mStr, n + sn2 - sn + 1);
            }
            strcpy(mStr + n - sn, newName);
            mStr[n + sn2 - sn] = 0;
        }
    }
};

// ========= UTILITIES ==============

// Displays a message in an ok+info dialog box.
static void msgBox(const char* text, ...) {
    CString formatted;
    va_list ap;
    va_start(ap, text);
    formatted.setv(text, ap);
    va_end(ap);

    MessageBoxA(NULL, formatted, "Android SDK Manager", MB_OK | MB_ICONINFORMATION);
}

static void displayLastError(const char *description, ...) {
    CString formatted;
    va_list ap;
    va_start(ap, description);
    formatted.setv(description, ap);
    va_end(ap);

    DWORD err = GetLastError();
    LPSTR errStr;
    if (FormatMessageA(FORMAT_MESSAGE_ALLOCATE_BUFFER | /* dwFlags */
                      FORMAT_MESSAGE_FROM_SYSTEM,
                      NULL,                             /* lpSource */
                      err,                              /* dwMessageId */
                      0,                                /* dwLanguageId */
                      (LPSTR)&errStr,                   /* lpBuffer */
                      0,                                /* nSize */
                      NULL) != 0) {                     /* va_list args */
        formatted.add("\r\n");
        formatted.add(errStr);
        MessageBox(NULL, formatted, "Android SDK Manager - Error", MB_OK | MB_ICONERROR);
        LocalFree(errStr);
    }
}

// Executes the command line. Does not wait for the program to finish.
// The return code is from CreateProcess (0 means failure), not the running app.
static int execNoWait(const char *app, const char *params, const char *workDir) {
    STARTUPINFO           startup;
    PROCESS_INFORMATION   pinfo;

    ZeroMemory(&pinfo, sizeof(pinfo));

    ZeroMemory(&startup, sizeof(startup));
    startup.cb          = sizeof(startup);
    startup.dwFlags     = STARTF_USESHOWWINDOW;
    startup.wShowWindow = SW_HIDE|SW_MINIMIZE;

    int ret = CreateProcessA(
            (LPSTR) app,                                /* program path */
            (LPSTR) params,                             /* command-line */
            NULL,                  /* process handle is not inheritable */
            NULL,                   /* thread handle is not inheritable */
            TRUE,                          /* yes, inherit some handles */
            CREATE_NO_WINDOW,                /* we don't want a console */
            NULL,                     /* use parent's environment block */
            workDir,                 /* use parent's starting directory */
            &startup,                 /* startup info, i.e. std handles */
            &pinfo);

    if (ret) {
        CloseHandle(pinfo.hProcess);
        CloseHandle(pinfo.hThread);
    }

    return ret;
}


// Executes command, waits for completion and returns exit code.
// As indicated in MSDN for CreateProcess, callers should double-quote the program name
// e.g. cmd="\"c:\program files\myapp.exe\" arg1 arg2";
static int execWait(const char *cmd) {
    STARTUPINFO           startup;
    PROCESS_INFORMATION   pinfo;

    ZeroMemory(&pinfo, sizeof(pinfo));

    ZeroMemory(&startup, sizeof(startup));
    startup.cb          = sizeof(startup);
    startup.dwFlags     = STARTF_USESHOWWINDOW;
    startup.wShowWindow = SW_HIDE|SW_MINIMIZE;

    int ret = CreateProcessA(
            NULL,                                       /* program path */
            (LPSTR) cmd,                                /* command-line */
            NULL,                  /* process handle is not inheritable */
            NULL,                   /* thread handle is not inheritable */
            TRUE,                          /* yes, inherit some handles */
            CREATE_NO_WINDOW,                /* we don't want a console */
            NULL,                     /* use parent's environment block */
            NULL,                    /* use parent's starting directory */
            &startup,                 /* startup info, i.e. std handles */
            &pinfo);

    int result = -1;
    if (ret) {
        WaitForSingleObject(pinfo.hProcess, INFINITE);

        DWORD exitCode;
        if (GetExitCodeProcess(pinfo.hProcess, &exitCode)) {
            // this should not return STILL_ACTIVE (259)
            result = exitCode;
        }
        CloseHandle(pinfo.hProcess);
        CloseHandle(pinfo.hThread);
    }

    return result;
}

static bool getModuleDir(CPath *outDir) {
    CHAR programDir[MAX_PATH];
    int ret = GetModuleFileName(NULL, programDir, sizeof(programDir));
    if (ret != 0) {
        // Remove the last segment to keep only the directory.
        int pos = ret - 1;
        while (pos > 0 && programDir[pos] != '\\') {
            --pos;
        }
        outDir->set(programDir, pos);
        return true;
    }
    return false;
}

// Disable the FS redirection done by WOW64.
// Because this runs as a 32-bit app, Windows automagically remaps some
// folder under the hood (e.g. "Programs Files(x86)" is mapped as "Program Files").
// This prevents the app from correctly searching for java.exe in these folders.
// The registry is also remapped.
static PVOID disableWow64FsRedirection() {

    // The call we want to make is the following:
    //    PVOID oldWow64Value;
    //    Wow64DisableWow64FsRedirection(&oldWow64Value);
    // However that method may not exist (e.g. on non-64 systems) so
    // we must not call it directly.

    PVOID oldWow64Value = 0;

    HMODULE hmod = LoadLibrary("kernel32.dll");
    if (hmod != NULL) {
        FARPROC proc = GetProcAddress(hmod, "Wow64DisableWow64FsRedirection");
        if (proc != NULL) {
            typedef BOOL (WINAPI *disableWow64FuncType)(PVOID *);
            disableWow64FuncType funcPtr = (disableWow64FuncType)proc;
            funcPtr(&oldWow64Value);
        }

        FreeLibrary(hmod);
    }

    return oldWow64Value;
}

//Reverts the redirection disabled in disableWow64FsRedirection.
static void revertWow64FsRedirection(PVOID oldWow64Value) {

    // The call we want to make is the following:
    //    Wow64RevertWow64FsRedirection(oldWow64Value);
    // However that method may not exist (e.g. on non-64 systems) so
    // we must not call it directly.

    HMODULE hmod = LoadLibrary("kernel32.dll");
    if (hmod != NULL) {
        FARPROC proc = GetProcAddress(hmod, "Wow64RevertWow64FsRedirection");
        if (proc != NULL) {
            typedef BOOL (WINAPI *revertWow64FuncType)(PVOID);
            revertWow64FuncType funcPtr = (revertWow64FuncType)proc;
            funcPtr(oldWow64Value);
        }

        FreeLibrary(hmod);
    }
}


// =============================

// Search java.exe in the path
static bool findJavaInEnvPath(CPath *outJavaPath) {
    SetLastError(0);
    const char* envPath = getenv("PATH");
    if (!envPath) return false;

    CArray<CString> *paths = CString(envPath).split(';');
    for(int i = 0; i < paths->size(); i++) {
        CPath p((*paths)[i]);
        p.addPath("java.exe");
        if (p.fileExists()) {
            // Make sure we can actually run "java -version".
            CString cmd;
            cmd.setf("\"%s\" -version", (const char*)p);
            int code = execWait(cmd);
            if (code == 0) {
                if (gDebug) msgBox("Java found via env path: %s", (const char *)p);
                outJavaPath->set(p);
                delete paths;
                return true;
            }
        }
    }

    delete paths;
    return false;
}

static bool findJavaInRegistry(CPath *outJavaPath) {
    // TODO
    return false;
}

static bool findJavaInProgramFiles(CPath *outJavaPath) {
    // TODO
    return false;
}

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
            if (!CreateDirectoryA(path, NULL /*lpSecurityAttributes*/)) {
                displayLastError("Failed to create directory: %s", (const char *)path);
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

        HANDLE srcH = FindFirstFileA(fullGlob, &srcFindData);
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
            HANDLE destH = FindFirstFileA(destPath, &destFindData);
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
                    SetFileAttributes(destPath,
                        destFindData.dwFileAttributes ^ FILE_ATTRIBUTE_READONLY);
                }
            }

            if (!CopyFileA(srcPath, destPath, false /*bFailIfExists*/)) {
                FindClose(srcH);
                displayLastError("Failed to copy file: %s", (const char*)destPath);
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
        javawPath.baseName(), toolsDir, tmpDir, (const char *)arch, lpCmdLine);

    if (gDebug) msgBox("Executing: %s", (const char *)cmdLine);

    if (!execNoWait(javawPath, cmdLine, tmpDir)) {
        displayLastError("Failed to run %s", (const char *)cmdLine);
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

    if (!mkDirs(tmpDir, sMkDirList)) {
        return 1;
    }

    if (!copyFiles(toolsDir, tmpDir, sFilesToCopy)) {
        return 1;
    }

    if (!execSdkManager(javaPath, toolsDir, tmpDir, lpCmdLine)) {
        displayLastError("Failed to start SDK Manager: ");
        return 1;
    }

    return 0;
}
#endif /* _WIN32 */
