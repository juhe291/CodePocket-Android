#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <pthread.h>
#include <signal.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/ioctl.h>
#include <sys/resource.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>
#include <termios.h>
#include <stdio.h>
#include <time.h>
#include "execution_memory.h"

/* Loaded only by :cpp_run. One generation owns its PID and pipes until explicit release. */
static pthread_mutex_t execution_lock = PTHREAD_MUTEX_INITIALIZER;
static jlong generation = 0, sequence = 0;
static pid_t owned_child = 0;
static int channels[3] = {-1, -1, -1};
static int exited = 0, exit_value = 0;
static int private_terminal = 0;
static uint64_t resident_limit = 0, last_memory_check = 0;
static int memory_exceeded = 0;
static uint64_t monotonic_nanos(void) {
    struct timespec now; if (clock_gettime(CLOCK_MONOTONIC,&now) != 0) return 0;
    return (uint64_t)now.tv_sec*1000000000ULL+(uint64_t)now.tv_nsec;
}

static void fail(JNIEnv *env, const char *message) {
    jclass type = (*env)->FindClass(env, "java/io/IOException");
    if (type && !(*env)->ExceptionCheck(env)) (*env)->ThrowNew(env, type, message);
}
static char *text_bytes(JNIEnv *env, jbyteArray array) {
    if (!array) return NULL;
    jsize length = (*env)->GetArrayLength(env, array);
    if (length <= 0 || length > 16384) return NULL;
    char *out = calloc((size_t)length + 1, 1);
    if (!out) return NULL;
    (*env)->GetByteArrayRegion(env, array, 0, length, (jbyte *)out);
    if ((*env)->ExceptionCheck(env) || memchr(out, 0, (size_t)length)) { free(out); return NULL; }
    return out;
}
static void release_list(char **items) {
    if (items) { for (int i = 0; items[i]; ++i) free(items[i]); free(items); }
}
static char **text_list(JNIEnv *env, jobjectArray array) {
    if (!array) return NULL;
    jsize count = (*env)->GetArrayLength(env, array);
    if (count <= 0 || count > 256) return NULL;
    char **items = calloc((size_t)count + 1, sizeof(char *));
    if (!items) return NULL;
    for (jsize i = 0; i < count; ++i) {
        jbyteArray value = (jbyteArray)(*env)->GetObjectArrayElement(env, array, i);
        items[i] = text_bytes(env, value);
        if (value) (*env)->DeleteLocalRef(env, value);
        if (!items[i]) { release_list(items); return NULL; }
    }
    return items;
}

JNIEXPORT jlong JNICALL Java_com_codepocket_ide_NativeExecutionBridge_spawn(
        JNIEnv *env, jobject self, jobjectArray arguments, jbyteArray directory,
        jobjectArray environment, jintArray terminal, jint cpu_seconds, jint memory_mb) {
    (void)self;
    char **argv = text_list(env, arguments), **envp = text_list(env, environment), *cwd = text_bytes(env, directory);
    long maximum = sysconf(_SC_OPEN_MAX);
    if (!argv || argv[0][0] != '/' || !envp || !cwd || cwd[0] != '/' ||
            maximum < 3 || maximum > 1048576 || cpu_seconds < 1 || cpu_seconds > 120 || memory_mb < 32 || memory_mb > 2048) {
        release_list(argv); release_list(envp); free(cwd); fail(env, "Invalid C++ process arguments"); return 0;
    }
    int fds[3] = {-1,-1,-1}, pipes[2][2] = {{-1,-1},{-1,-1}};
    int tty = terminal != NULL;
    int pty = tty && (*env)->GetArrayLength(env, terminal) == 2;
    int master = -1, fault_pipe[2] = {-1,-1};
    if (tty && !pty && (*env)->GetArrayLength(env, terminal) != 3) {
        release_list(argv); release_list(envp); free(cwd); fail(env, "Expected three terminal descriptors"); return 0;
    }
    pthread_mutex_lock(&execution_lock);
    if (generation) {
        pthread_mutex_unlock(&execution_lock); release_list(argv); release_list(envp); free(cwd);
        fail(env, "The previous C++ process resources are still owned"); return 0;
    }
    if (pty) {
        jint dimensions[2]; (*env)->GetIntArrayRegion(env,terminal,0,2,dimensions);
        if ((*env)->ExceptionCheck(env) || dimensions[0] < 1 || dimensions[0] > 500 || dimensions[1] < 1 || dimensions[1] > 1000) goto failed;
        master = posix_openpt(O_RDWR | O_NOCTTY | O_CLOEXEC | O_NONBLOCK);
        if (master < 0 || grantpt(master) != 0 || unlockpt(master) != 0) goto failed;
        char name[256]; if (ptsname_r(master,name,sizeof(name)) != 0) goto failed;
        fds[0] = open(name,O_RDWR | O_NOCTTY | O_CLOEXEC); if (fds[0] < 0) goto failed;
        fds[1] = fcntl(fds[0],F_DUPFD_CLOEXEC,3); fds[2] = fcntl(fds[0],F_DUPFD_CLOEXEC,3);
        if (fds[1] < 0 || fds[2] < 0) goto failed;
        struct termios mode; if (tcgetattr(fds[0],&mode) != 0) goto failed;
        /* The outer terminal handles line editing/echo. Raw private input provides
         * backpressure instead of silently discarding a long prefilled input line. */
        cfmakeraw(&mode); if (tcsetattr(fds[0],TCSANOW,&mode) != 0) goto failed;
        struct winsize size = {.ws_row=(unsigned short)dimensions[0],.ws_col=(unsigned short)dimensions[1]};
        if (ioctl(fds[0],TIOCSWINSZ,&size) != 0) goto failed;
        if (pipe2(fault_pipe,O_CLOEXEC|O_NONBLOCK) != 0) goto failed;
    } else if (tty) {
        jint values[3]; (*env)->GetIntArrayRegion(env, terminal, 0, 3, values);
        if ((*env)->ExceptionCheck(env)) goto failed;
        for (int i = 0; i < 3; ++i) {
            if (!isatty(values[i])) goto failed;
            fds[i] = fcntl(values[i], F_DUPFD_CLOEXEC, 3); if (fds[i] < 0) goto failed;
        }
    } else {
        fds[0] = open("/dev/null", O_RDONLY | O_CLOEXEC); if (fds[0] < 0) goto failed;
        for (int i = 0; i < 2; ++i) {
            if (pipe2(pipes[i], O_CLOEXEC) != 0) goto failed;
            fds[i+1] = pipes[i][1];
        }
    }
    struct rlimit cpu = {.rlim_cur=(rlim_t)cpu_seconds, .rlim_max=(rlim_t)cpu_seconds+1};
    struct rlimit files = {.rlim_cur=64*1024*1024, .rlim_max=64*1024*1024};
    struct rlimit core = {.rlim_cur=0, .rlim_max=0};
    pid_t parent = getpid();
    pid_t pid = fork();
    if (pid < 0) goto failed;
    if (pid == 0) {
        if (prctl(PR_SET_PDEATHSIG, SIGKILL) != 0) _exit(126);
        if (getppid() != parent) _exit(126);
        if (pty) { if (setsid() < 0 || ioctl(fds[0],TIOCSCTTY,0) != 0) _exit(126); }
        else if (setpgid(0,0) != 0) _exit(126);
        if (dup2(fds[0],0) < 0 || dup2(fds[1],1) < 0 || dup2(fds[2],2) < 0) _exit(126);
        if (pty && dup2(fault_pipe[1],3) < 0) _exit(126);
        if (setrlimit(RLIMIT_CPU,&cpu) != 0 ||
                setrlimit(RLIMIT_FSIZE,&files) != 0 || setrlimit(RLIMIT_CORE,&core) != 0 || chdir(cwd) != 0) {
            static const char error[] = "CodePocket: cannot configure C++ process limits or directory\n";
            (void)write(2,error,sizeof(error)-1); _exit(126);
        }
        for (int fd = pty ? 4 : 3; fd < maximum; ++fd) close(fd);
        execve(argv[0],argv,envp);
        static const char error[] = "CodePocket: C++ execve failed\n";
        (void)write(2,error,sizeof(error)-1); _exit(127);
    }
    if (!pty) (void)setpgid(pid,pid);
    if (fault_pipe[1] >= 0) close(fault_pipe[1]);
    for (int i = 0; i < 3; ++i) { close(fds[i]); fds[i] = -1; }
    for (int i = 0; i < 2; ++i) {
        channels[i] = pty ? (i == 0 ? master : -1) : tty ? -1 : pipes[i][0];
        if (channels[i] >= 0) { int flags = fcntl(channels[i],F_GETFL); if (flags >= 0) (void)fcntl(channels[i],F_SETFL,flags|O_NONBLOCK); }
    }
    channels[2] = fault_pipe[0];
    owned_child = pid; exited = 0; exit_value = 0; private_terminal = pty; generation = ++sequence;
    resident_limit=(uint64_t)memory_mb*1024*1024;memory_exceeded=0;last_memory_check=monotonic_nanos();
    jlong handle = generation;
    pthread_mutex_unlock(&execution_lock); release_list(argv); release_list(envp); free(cwd); return handle;
failed:
    for (int i=0;i<2;++i) if (fault_pipe[i] >= 0) close(fault_pipe[i]);
    if (master >= 0) close(master);
    /* fds[1/2] alias pipe write ends in capture mode. Close each descriptor only once. */
    if (tty) { for (int i = 0; i < 3; ++i) if (fds[i] >= 0) close(fds[i]); }
    else {
        if (fds[0] >= 0) close(fds[0]);
        for (int i = 0; i < 2; ++i) for (int j = 0; j < 2; ++j) if (pipes[i][j] >= 0) close(pipes[i][j]);
    }
    pthread_mutex_unlock(&execution_lock); release_list(argv); release_list(envp); free(cwd);
    fail(env, "Cannot create owned C++ process"); return 0;
}

JNIEXPORT jint JNICALL Java_com_codepocket_ide_NativeExecutionBridge_status(JNIEnv *env, jobject self, jlong handle) {
    (void)self;
    pthread_mutex_lock(&execution_lock);
    if (!generation || handle != generation) { pthread_mutex_unlock(&execution_lock); fail(env,"C++ process handle closed"); return -2; }
    if (!exited) {
        siginfo_t info; memset(&info,0,sizeof(info));
        int observed = waitid(P_PID,(id_t)owned_child,&info,WEXITED|WNOHANG|WNOWAIT);
        if (observed == 0 && info.si_pid == owned_child) {
            /* Observe without reaping: the reserved leader PID cannot be reused while we
             * still need to terminate same-group descendants and drain output. Only stop()
             * reaps it, after killing the owned group. */
            exited = 1;
            exit_value = info.si_code == CLD_EXITED ? info.si_status :
                (info.si_code == CLD_KILLED || info.si_code == CLD_DUMPED) ? 256+info.si_status : 1024;
            if (memory_exceeded) exit_value=512; /* Explicit parent-observed RSS event, never inferred from SIGKILL. */
        } else if (observed < 0 && errno != EINTR) {
            /* If ownership was unexpectedly lost, never use the potentially reused PID. */
            owned_child = 0; exited = 1; exit_value = 1024;
        }
        if (!exited && owned_child>0 && !memory_exceeded) {
            uint64_t now=monotonic_nanos();
            if (now>=last_memory_check && now-last_memory_check>=100000000ULL) {
                last_memory_check=now;uint64_t resident=0;long page_size=sysconf(_SC_PAGESIZE);
                if (page_size>0 && cpp_group_resident_bytes("/proc",(long)owned_child,(uint64_t)page_size,&resident) && resident>resident_limit) {
                    (void)kill(-owned_child,SIGKILL);
                    if (kill(owned_child,SIGKILL)==0) memory_exceeded=1;
                }
            }
        }
    }
    int result = exited ? exit_value : -1;
    pthread_mutex_unlock(&execution_lock); return result;
}

JNIEXPORT jbyteArray JNICALL Java_com_codepocket_ide_NativeExecutionBridge_read(
        JNIEnv *env, jobject self, jlong handle, jint channel, jint limit) {
    (void)self;
    if (channel < 1 || channel > 3 || limit < 1 || limit > 65536) { fail(env,"Invalid C++ output read"); return NULL; }
    pthread_mutex_lock(&execution_lock);
    int fd = handle == generation && generation && channels[channel-1] >= 0 ? fcntl(channels[channel-1],F_DUPFD_CLOEXEC,3) : -1;
    pthread_mutex_unlock(&execution_lock);
    if (fd < 0) return NULL;
    struct pollfd waiting = {.fd=fd, .events=POLLIN}; int ready = poll(&waiting,1,100);
    unsigned char buffer[65536]; ssize_t count = ready > 0 ? read(fd,buffer,(size_t)limit) : -1;
    int saved = errno; close(fd);
    if (ready == 0 || (ready < 0 && saved == EINTR) || (count < 0 && (saved == EAGAIN || saved == EINTR))) return (*env)->NewByteArray(env,0);
    if (count <= 0) return NULL;
    jbyteArray bytes = (*env)->NewByteArray(env,(jsize)count);
    if (bytes) (*env)->SetByteArrayRegion(env,bytes,0,(jsize)count,(jbyte*)buffer);
    return bytes;
}

JNIEXPORT void JNICALL Java_com_codepocket_ide_NativeExecutionBridge_stop(JNIEnv *env, jobject self, jlong handle) {
    (void)env; (void)self;
    pthread_mutex_lock(&execution_lock);
    if (!generation || handle != generation) { pthread_mutex_unlock(&execution_lock); return; }
    pid_t pid = owned_child; owned_child = 0; generation = 0;
    resident_limit=0;memory_exceeded=0;last_memory_check=0;
    int fds[3]; for (int i = 0; i < 3; ++i) { fds[i] = channels[i]; channels[i] = -1; }
    pthread_mutex_unlock(&execution_lock);
    if (pid > 0) {
        (void)kill(-pid,SIGKILL); (void)kill(pid,SIGKILL);
        while (waitpid(pid,NULL,0) < 0 && errno == EINTR) {}
    }
    for (int i = 0; i < 3; ++i) if (fds[i] >= 0) close(fds[i]);
}

JNIEXPORT jbyteArray JNICALL Java_com_codepocket_ide_NativeExecutionBridge_faultMapsAndResume(
        JNIEnv *env, jobject self, jlong handle) {
    (void)self;
    /* Keep ownership throughout wait/open/read/resume. close() cannot reap this PID,
     * nor can an older generation resume a later request. No ptrace is used. */
    pthread_mutex_lock(&execution_lock);
    if (!generation || handle != generation || owned_child <= 0 || !private_terminal) {
        pthread_mutex_unlock(&execution_lock); return NULL;
    }
    int stopped=0;
    for (int attempt=0; attempt<100; ++attempt) {
        siginfo_t state; memset(&state,0,sizeof(state));
        if (waitid(P_PID,(id_t)owned_child,&state,WSTOPPED|WEXITED|WNOHANG|WNOWAIT) < 0 && errno != EINTR) break;
        if (state.si_pid == owned_child) { stopped=state.si_code==CLD_STOPPED; break; }
        struct timespec pause={.tv_sec=0,.tv_nsec=5000000}; nanosleep(&pause,NULL);
    }
    unsigned char *buffer=NULL; size_t count=0;
    if (stopped) {
        char path[64]; snprintf(path,sizeof(path),"/proc/%ld/maps",(long)owned_child);
        int fd=open(path,O_RDONLY|O_CLOEXEC);
        if (fd >= 0) {
            buffer=malloc(262145);
            if (buffer) {
                while (count<262145) {
                    ssize_t n=read(fd,buffer+count,262145-count);
                    if (n > 0) count+=(size_t)n;
                    else if (n < 0 && errno==EINTR) continue;
                    else break;
                }
            }
            close(fd);
        }
    }
    /* Always release a reporting child even when maps are unavailable. */
    /* If no stop was observed by the bounded deadline, prevent a late SIGSTOP
     * from outliving its reader. Do not send an early SIGCONT and then hang. */
    (void)kill(owned_child,stopped ? SIGCONT : SIGKILL);
    pthread_mutex_unlock(&execution_lock);
    jbyteArray result=NULL;
    if (buffer && count<=262144) {
        result=(*env)->NewByteArray(env,(jsize)count);
        if (result) (*env)->SetByteArrayRegion(env,result,0,(jsize)count,(jbyte*)buffer);
    }
    free(buffer); return result;
}

JNIEXPORT jint JNICALL Java_com_codepocket_ide_NativeExecutionBridge_write(
        JNIEnv *env, jobject self, jlong handle, jbyteArray bytes, jint offset, jint length) {
    (void)self;
    if (!bytes || offset < 0 || length < 1 || length > 8192 || offset > (*env)->GetArrayLength(env,bytes)-length) {
        fail(env,"Invalid private PTY input"); return -1;
    }
    jbyte buffer[8192]; (*env)->GetByteArrayRegion(env,bytes,offset,length,buffer);
    if ((*env)->ExceptionCheck(env)) return -1;
    pthread_mutex_lock(&execution_lock);
    int fd = handle == generation && generation && private_terminal ? fcntl(channels[0],F_DUPFD_CLOEXEC,3) : -1;
    pthread_mutex_unlock(&execution_lock);
    if (fd < 0) return -1;
    struct pollfd ready = {.fd=fd,.events=POLLOUT}; int polled = poll(&ready,1,100);
    ssize_t result = polled > 0 ? write(fd,buffer,(size_t)length) : -1;
    int error = errno; close(fd);
    if (polled == 0 || (polled < 0 && error == EINTR) || (result < 0 && (error == EAGAIN || error == EINTR))) return 0;
    return result < 0 ? -1 : (jint)result;
}

JNIEXPORT void JNICALL Java_com_codepocket_ide_NativeExecutionBridge_eof(JNIEnv *env, jobject self, jlong handle) {
    (void)self;
    pthread_mutex_lock(&execution_lock);
    int fd = handle == generation && generation && private_terminal ? fcntl(channels[0],F_DUPFD_CLOEXEC,3) : -1;
    pthread_mutex_unlock(&execution_lock);
    if (fd < 0) { fail(env,"Private PTY is closed"); return; }
    struct termios mode;
    int result = tcgetattr(fd,&mode);
    if (result == 0) { mode.c_lflag |= ICANON; mode.c_lflag &= ~ECHO; mode.c_cc[VEOF] = 4; result = tcsetattr(fd,TCSANOW,&mode); }
    close(fd); if (result != 0) fail(env,"Cannot deliver private PTY EOF");
    /* The caller subsequently sends the VEOF byte through the same bounded write path. */
}

JNIEXPORT void JNICALL Java_com_codepocket_ide_NativeExecutionBridge_resize(JNIEnv *env, jobject self, jlong handle, jint rows, jint columns) {
    (void)env; (void)self;
    if (rows < 1 || rows > 500 || columns < 1 || columns > 1000) return;
    pthread_mutex_lock(&execution_lock);
    if (handle == generation && generation && private_terminal) {
        struct winsize size = {.ws_row=(unsigned short)rows,.ws_col=(unsigned short)columns};
        (void)ioctl(channels[0],TIOCSWINSZ,&size);
    }
    pthread_mutex_unlock(&execution_lock);
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)vm; (void)reserved;
    signal(SIGPIPE,SIG_IGN); /* Only this private execution process. */
    return JNI_VERSION_1_6;
}
