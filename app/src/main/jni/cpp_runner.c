/* Installed native executable. Generated code is a private read-only shared object,
 * never an execve target in app-writable storage. Runs only in the owned child. */
#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif
#include <stdio.h>
#include <stdlib.h>
#include <signal.h>
#ifdef _WIN32
#include <windows.h>
#else
#include <dlfcn.h>
#include <sys/stat.h>
#include <unistd.h>
#include "cpp_fault.h"
#endif

int main(int argc, char **argv) {
    if (argc != 2) { fputs("CodePocket: expected one program path\n", stderr); return 125; }
#ifndef _WIN32
    signal(SIGPIPE, SIG_DFL);
    if (cpp_fault_install() != 0) { fputs("CodePocket: cannot configure fault reporting\n",stderr); return 125; }
#endif
    setvbuf(stdout, NULL, _IONBF, 0);
    setvbuf(stderr, NULL, _IONBF, 0);
#ifdef _WIN32
    /* Same entry/lifetime path is exercised by host tests, using a host DLL. */
    wchar_t path[32768];
    if (!MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, argv[1], -1, path, 32768)) return 125;
    HMODULE library = LoadLibraryW(path);
    if (!library) { fprintf(stderr, "CodePocket: load failed %lu\n", GetLastError()); return 126; }
    int (*entry)(int, char **) = (int (*)(int, char **))GetProcAddress(library, "main");
#else
    struct stat info;
    if (argv[1][0] != '/' || lstat(argv[1], &info) != 0 || !S_ISREG(info.st_mode) ||
            info.st_uid != getuid() || (info.st_mode & 0222)) {
        fputs("CodePocket: program must be an owned read-only regular file\n", stderr); return 125;
    }
    void *library = dlopen(argv[1], RTLD_NOW | RTLD_LOCAL);
    if (!library) { fprintf(stderr, "CodePocket: %s\n", dlerror()); return 126; }
    int (*entry)(int, char **) = (int (*)(int, char **))dlsym(library, "main");
#endif
    if (!entry) { fputs("CodePocket: missing main entry\n", stderr); return 127; }
    char *arguments[] = {"program", NULL};
    int result = entry(1, arguments);
    /* dlclose runs global destructors before returning a normal exit code. */
#ifdef _WIN32
    FreeLibrary(library);
#else
    dlclose(library);
#endif
    return result;
}
