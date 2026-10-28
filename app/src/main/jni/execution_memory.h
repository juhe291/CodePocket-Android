#ifndef CODEPOCKET_EXECUTION_MEMORY_H
#define CODEPOCKET_EXECUTION_MEMORY_H
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <dirent.h>

/* Scudo reserves large virtual regions at startup. Count resident pages instead of
 * constraining that reservation with RLIMIT_AS. The proc root is fixed to /proc
 * by the native caller; a supplied root lets host tests exercise the exact parser. */
static int cpp_resident_bytes(const char *root, const char *pid, uint64_t page_size, uint64_t *bytes) {
    char path[512]; unsigned long long address_pages=0, resident_pages=0;
    if (snprintf(path,sizeof(path),"%s/%s/statm",root,pid) >= (int)sizeof(path)) return 0;
    FILE *file=fopen(path,"r"); if (!file) return 0;
    int valid=fscanf(file,"%llu %llu",&address_pages,&resident_pages)==2; fclose(file);
    if (!valid || !page_size) return 0;
    *bytes=resident_pages>UINT64_MAX/page_size ? UINT64_MAX : (uint64_t)resident_pages*page_size;
    return 1;
}
static int cpp_group_resident_bytes(const char *root, long group, uint64_t page_size, uint64_t *bytes) {
    *bytes=0; int samples=0; DIR *directory=opendir(root);
    if (directory) {
        struct dirent *item; unsigned int entries=0;
        while ((item=readdir(directory)) != NULL && ++entries<=8192) {
            const char *name=item->d_name; size_t length=strlen(name);
            if (!length || length>10 || strspn(name,"0123456789")!=length) continue;
            char path[512],line[4096];
            if (snprintf(path,sizeof(path),"%s/%s/stat",root,name) >= (int)sizeof(path)) continue;
            FILE *file=fopen(path,"r"); if (!file) continue;
            char *read=fgets(line,sizeof(line),file); fclose(file); if (!read) continue;
            char *end=strrchr(line,')'),state=0; long parent=0,actual_group=0;
            if (!end || sscanf(end+1," %c %ld %ld",&state,&parent,&actual_group)!=3 || actual_group!=group) continue;
            uint64_t resident=0;
            if (cpp_resident_bytes(root,name,page_size,&resident)) {
                *bytes=resident>UINT64_MAX-*bytes ? UINT64_MAX : *bytes+resident; ++samples;
            }
        }
        closedir(directory);
    }
    /* Some Android variants restrict directory enumeration but allow the known
     * owned PID. Missing/racing metrics must never be invented as an OOM event. */
    if (!samples) {
        char leader[32]; snprintf(leader,sizeof(leader),"%ld",group);
        samples=cpp_resident_bytes(root,leader,page_size,bytes);
    }
    return samples>0;
}
#endif
