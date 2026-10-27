/* Feasibility fixture only. Not integrated into the App and not executed on Android. */
#include <stdio.h>
#ifdef _WIN32
#include <windows.h>
#else
#include <dlfcn.h>
#endif
int main(int argc, char** argv) {
    if (argc != 2) { fprintf(stderr, "runner: expected one library path\n"); return 125; }
#ifdef _WIN32
    HMODULE library = LoadLibraryA(argv[1]);
    if (!library) { fprintf(stderr, "runner: load failed %lu\n", GetLastError()); return 126; }
    int (*entry)(int,char**) = (int (*)(int,char**))GetProcAddress(library, "main");
#else
    void* library = dlopen(argv[1], RTLD_NOW | RTLD_LOCAL);
    if (!library) { fprintf(stderr, "runner: %s\n", dlerror()); return 126; }
    int (*entry)(int,char**) = (int (*)(int,char**))dlsym(library, "main");
#endif
    if (!entry) { fprintf(stderr, "runner: missing main entry\n"); return 127; }
    char* arguments[] = {"program", NULL};
    int result = entry(1, arguments);
#ifdef _WIN32
    FreeLibrary(library);
#else
    dlclose(library);
#endif
    return result;
}
