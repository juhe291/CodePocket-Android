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
#include <sys/resource.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

/* Loaded only by :cpp_run. One generation owns its PID and pipes until explicit release. */
static pthread_mutex_t execution_lock = PTHREAD_MUTEX_INITIALIZER;
static jlong generation = 0, sequence = 0;
static pid_t owned_child = 0;
static int channels[2] = {-1, -1};
static int exited = 0, exit_value = 0;

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
    if (tty && (*env)->GetArrayLength(env, terminal) != 3) {
        release_list(argv); release_list(envp); free(cwd); fail(env, "Expected three terminal descriptors"); return 0;
    }
    pthread_mutex_lock(&execution_lock);
    if (generation) {
        pthread_mutex_unlock(&execution_lock); release_list(argv); release_list(envp); free(cwd);
        fail(env, "The previous C++ process resources are still owned"); return 0;
    }
    if (tty) {
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
    struct rlimit memory = {.rlim_cur=(rlim_t)memory_mb*1024*1024, .rlim_max=(rlim_t)memory_mb*1024*1024};
    struct rlimit files = {.rlim_cur=64*1024*1024, .rlim_max=64*1024*1024};
    struct rlimit core = {.rlim_cur=0, .rlim_max=0};
    pid_t parent = getpid();
    pid_t pid = fork();
    if (pid < 0) goto failed;
    if (pid == 0) {
        if (prctl(PR_SET_PDEATHSIG, SIGKILL) != 0) _exit(126);
        if (getppid() != parent) _exit(126);
        if (setpgid(0,0) != 0) _exit(126);
        if (dup2(fds[0],0) < 0 || dup2(fds[1],1) < 0 || dup2(fds[2],2) < 0) _exit(126);
        if (setrlimit(RLIMIT_CPU,&cpu) != 0 || setrlimit(RLIMIT_AS,&memory) != 0 ||
                setrlimit(RLIMIT_FSIZE,&files) != 0 || setrlimit(RLIMIT_CORE,&core) != 0 || chdir(cwd) != 0) {
            static const char error[] = "CodePocket: cannot configure C++ process limits or directory\n";
            (void)write(2,error,sizeof(error)-1); _exit(126);
        }
        for (int fd = 3; fd < maximum; ++fd) close(fd);
        execve(argv[0],argv,envp);
        static const char error[] = "CodePocket: C++ execve failed\n";
        (void)write(2,error,sizeof(error)-1); _exit(127);
    }
    (void)setpgid(pid,pid);
    for (int i = 0; i < 3; ++i) { close(fds[i]); fds[i] = -1; }
    for (int i = 0; i < 2; ++i) {
        channels[i] = tty ? -1 : pipes[i][0];
        if (channels[i] >= 0) { int flags = fcntl(channels[i],F_GETFL); if (flags >= 0) (void)fcntl(channels[i],F_SETFL,flags|O_NONBLOCK); }
    }
    owned_child = pid; exited = 0; exit_value = 0; generation = ++sequence;
    jlong handle = generation;
    pthread_mutex_unlock(&execution_lock); release_list(argv); release_list(envp); free(cwd); return handle;
failed:
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
        } else if (observed < 0 && errno != EINTR) {
            /* If ownership was unexpectedly lost, never use the potentially reused PID. */
            owned_child = 0; exited = 1; exit_value = 1024;
        }
    }
    int result = exited ? exit_value : -1;
    pthread_mutex_unlock(&execution_lock); return result;
}

JNIEXPORT jbyteArray JNICALL Java_com_codepocket_ide_NativeExecutionBridge_read(
        JNIEnv *env, jobject self, jlong handle, jint channel, jint limit) {
    (void)self;
    if (channel < 1 || channel > 2 || limit < 1 || limit > 65536) { fail(env,"Invalid C++ output read"); return NULL; }
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
    int fds[2]; for (int i = 0; i < 2; ++i) { fds[i] = channels[i]; channels[i] = -1; }
    pthread_mutex_unlock(&execution_lock);
    if (pid > 0) {
        (void)kill(-pid,SIGKILL); (void)kill(pid,SIGKILL);
        while (waitpid(pid,NULL,0) < 0 && errno == EINTR) {}
    }
    for (int i = 0; i < 2; ++i) if (fds[i] >= 0) close(fds[i]);
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)vm; (void)reserved;
    signal(SIGPIPE,SIG_IGN); /* Only this private execution process. */
    return JNI_VERSION_1_6;
}
