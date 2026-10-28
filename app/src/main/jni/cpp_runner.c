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
#include <sys/stat.h>
#else
#include <dlfcn.h>
#include <sys/stat.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/resource.h>
#include "cpp_fault.h"
#endif

/* Batch cases are finite UTF-8 byte files, not keystrokes or a shell redirection.
 * The service creates these three owned files before launching the installed runner. */
static int case_stream(const char *path, int channel) {
#ifdef _WIN32
    wchar_t wide[32768]; struct _stat64 info;
    if (!MultiByteToWideChar(CP_UTF8,MB_ERR_INVALID_CHARS,path,-1,wide,32768) ||
            _wstat64(wide,&info) != 0 || !(info.st_mode & _S_IFREG) || info.st_nlink != 1) return -1;
    return _wfreopen(wide,channel == 0 ? L"rb" : L"wb",channel == 0 ? stdin : channel == 1 ? stdout : stderr) ? 0 : -1;
#else
    if (path[0] != '/') return -1;
    int fd=open(path,(channel == 0 ? O_RDONLY : O_WRONLY)|O_CLOEXEC|O_NOFOLLOW);
    if (fd < 0) return -1;
    struct stat info; int result=-1;
    if (fstat(fd,&info) == 0 && S_ISREG(info.st_mode) && info.st_uid == getuid() && info.st_nlink == 1 &&
            (channel == 0 || ftruncate(fd,0) == 0) && dup2(fd,channel) >= 0) result=0;
    close(fd); return result;
#endif
}

int main(int argc, char **argv) {
    if (argc != 2 && argc != 5) { fputs("CodePocket: expected program path and optional case input/output/error files\n", stderr); return 125; }
    if (argc == 5) {
        for (int channel=0;channel<3;++channel) if (case_stream(argv[channel+2],channel) != 0) {
            fputs("CodePocket: cannot open owned case streams\n",stderr);return 125;
        }
#ifndef _WIN32
        struct rlimit limit={220000,220000};
        if (setrlimit(RLIMIT_FSIZE,&limit) != 0) { fputs("CodePocket: cannot limit case output\n",stderr);return 125; }
#endif
    }
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
