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
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

/* One owned child per isolated :cpp_analysis process. Handles never expose arbitrary PID control. */
static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;
static jlong generation = 0, sequence = 0;
static pid_t child = 0;
static int channels[3] = {-1, -1, -1}; /* stdin write, stdout read, stderr read */

static void failure(JNIEnv *env, const char *message) {
    jclass type = (*env)->FindClass(env, "java/io/IOException");
    if (type) (*env)->ThrowNew(env, type, message);
}

static char *bytes(JNIEnv *env, jbyteArray array) {
    if (!array) return NULL;
    jsize size = (*env)->GetArrayLength(env, array);
    if (size < 0 || size > 16384) return NULL;
    char *out = calloc((size_t)size + 1, 1);
    if (!out) return NULL;
    (*env)->GetByteArrayRegion(env, array, 0, size, (jbyte *)out);
    if ((*env)->ExceptionCheck(env) || memchr(out, 0, (size_t)size)) { free(out); return NULL; }
    return out;
}

static void free_list(char **list) {
    if (list) { for (int i = 0; list[i]; ++i) free(list[i]); free(list); }
}

static char **list(JNIEnv *env, jobjectArray array) {
    if (!array) return NULL;
    jsize count = (*env)->GetArrayLength(env, array);
    if (count <= 0 || count > 64) return NULL;
    char **out = calloc((size_t)count + 1, sizeof(char *));
    if (!out) return NULL;
    for (jsize i = 0; i < count; ++i) {
        jbyteArray entry = (jbyteArray)(*env)->GetObjectArrayElement(env, array, i);
        out[i] = bytes(env, entry);
        if (entry) (*env)->DeleteLocalRef(env, entry);
        if (!out[i]) { free_list(out); return NULL; }
    }
    return out;
}

JNIEXPORT jlong JNICALL Java_com_codepocket_ide_NativeAnalysisBridge_spawn(
        JNIEnv *env, jobject self, jobjectArray arguments, jbyteArray directory, jobjectArray environment) {
    (void)self;
    char **argv = list(env, arguments), **envp = list(env, environment), *cwd = bytes(env, directory);
    long max_fds = sysconf(_SC_OPEN_MAX);
    if (!argv || !argv[0][0] || !envp || !cwd || !cwd[0] || max_fds < 3 || max_fds > 1048576) {
        free_list(argv); free_list(envp); free(cwd); failure(env, "Invalid analysis launch arguments"); return 0;
    }
    int pipes[3][2] = {{-1,-1},{-1,-1},{-1,-1}};
    pthread_mutex_lock(&lock);
    if (child) { pthread_mutex_unlock(&lock); free_list(argv); free_list(envp); free(cwd); failure(env, "Analysis child already running"); return 0; }
    for (int i = 0; i < 3; ++i) {
        if (pipe2(pipes[i], O_CLOEXEC) != 0) goto failed;
    }
    pid_t parent = getpid();
    pid_t pid = fork();
    if (pid < 0) goto failed;
    if (pid == 0) {
        /* Only async-signal-safe operations after fork from the VM; allocations/JNI were done above. */
        prctl(PR_SET_PDEATHSIG, SIGKILL);
        if (getppid() != parent) _exit(126);
        setpgid(0, 0);
        if (dup2(pipes[0][0], STDIN_FILENO) < 0 || dup2(pipes[1][1], STDOUT_FILENO) < 0 ||
                dup2(pipes[2][1], STDERR_FILENO) < 0 || chdir(cwd) != 0) _exit(126);
        for (int fd = 3; fd < max_fds; ++fd) close(fd); /* Do not inherit unrelated VM/Binder descriptors. */
        execve(argv[0], argv, envp);
        static const char message[] = "CodePocket clangd execve failed\n";
        (void)write(STDERR_FILENO, message, sizeof(message) - 1);
        _exit(127);
    }
    (void)setpgid(pid, pid);
    close(pipes[0][0]); close(pipes[1][1]); close(pipes[2][1]);
    channels[0] = pipes[0][1]; channels[1] = pipes[1][0]; channels[2] = pipes[2][0];
    for (int i = 0; i < 3; ++i) { int flags = fcntl(channels[i], F_GETFL); if (flags >= 0) fcntl(channels[i], F_SETFL, flags | O_NONBLOCK); }
    child = pid; generation = ++sequence;
    jlong result = generation;
    pthread_mutex_unlock(&lock);
    free_list(argv); free_list(envp); free(cwd);
    return result;
failed:
    for (int i = 0; i < 3; ++i) for (int j = 0; j < 2; ++j) if (pipes[i][j] >= 0) close(pipes[i][j]);
    pthread_mutex_unlock(&lock);
    free_list(argv); free_list(envp); free(cwd); failure(env, "Cannot create analysis pipes or child"); return 0;
}

/* Duplicate under the generation lock: closing/restarting cannot redirect an old read to a reused FD. */
static int owned_fd(jlong handle, int channel) {
    pthread_mutex_lock(&lock);
    int fd = (handle == generation && child && channel >= 0 && channel < 3) ? fcntl(channels[channel], F_DUPFD_CLOEXEC, 3) : -1;
    pthread_mutex_unlock(&lock); return fd;
}

JNIEXPORT jbyteArray JNICALL Java_com_codepocket_ide_NativeAnalysisBridge_read(
        JNIEnv *env, jobject self, jlong handle, jint channel, jint limit) {
    (void)self;
    if (channel < 1 || channel > 2 || limit <= 0 || limit > 65536) { failure(env, "Invalid analysis read"); return NULL; }
    int fd = owned_fd(handle, channel); if (fd < 0) return NULL;
    struct pollfd pollfd = {.fd=fd, .events=POLLIN};
    int ready = poll(&pollfd, 1, 100);
    unsigned char buffer[65536]; ssize_t count = -1;
    if (ready > 0) count = read(fd, buffer, (size_t)limit);
    int saved = errno; close(fd);
    if (ready == 0 || (ready < 0 && saved == EINTR) || (count < 0 && saved == EAGAIN)) return (*env)->NewByteArray(env, 0);
    if (count <= 0) return NULL;
    jbyteArray result = (*env)->NewByteArray(env, (jsize)count);
    if (result) (*env)->SetByteArrayRegion(env, result, 0, (jsize)count, (jbyte *)buffer);
    return result;
}

JNIEXPORT jint JNICALL Java_com_codepocket_ide_NativeAnalysisBridge_write(
        JNIEnv *env, jobject self, jlong handle, jbyteArray data, jint offset, jint count) {
    (void)self;
    if (!data || offset < 0 || count < 0 || count > 65536 || offset > (*env)->GetArrayLength(env, data) - count) { failure(env, "Invalid analysis write"); return -1; }
    int fd = owned_fd(handle, 0); if (fd < 0) { failure(env, "Analysis child closed"); return -1; }
    struct pollfd pollfd = {.fd=fd, .events=POLLOUT}; int ready = poll(&pollfd, 1, 100);
    if (ready <= 0) { int saved = errno; close(fd); if (ready == 0 || saved == EINTR) return 0; failure(env, "Analysis pipe unavailable"); return -1; }
    unsigned char buffer[65536]; (*env)->GetByteArrayRegion(env, data, offset, count, (jbyte *)buffer);
    if ((*env)->ExceptionCheck(env)) { close(fd); return -1; }
    ssize_t wrote = write(fd, buffer, (size_t)count); int saved = errno; close(fd);
    if (wrote < 0 && (saved == EINTR || saved == EAGAIN)) return 0;
    if (wrote < 0) { failure(env, "Analysis pipe write failed"); return -1; }
    return (jint)wrote;
}

JNIEXPORT void JNICALL Java_com_codepocket_ide_NativeAnalysisBridge_stop(JNIEnv *env, jobject self, jlong handle) {
    (void)env; (void)self;
    pthread_mutex_lock(&lock);
    if (handle != generation || !child) { pthread_mutex_unlock(&lock); return; }
    pid_t pid = child; child = 0; generation = 0;
    int fds[3]; for (int i = 0; i < 3; ++i) { fds[i] = channels[i]; channels[i] = -1; }
    pthread_mutex_unlock(&lock);
    (void)kill(-pid, SIGKILL); (void)kill(pid, SIGKILL);
    for (int i = 0; i < 3; ++i) if (fds[i] >= 0) close(fds[i]);
    while (waitpid(pid, NULL, 0) < 0 && errno == EINTR) {}
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)vm; (void)reserved;
    signal(SIGPIPE, SIG_IGN); /* Only loaded in :cpp_analysis, never the editor/Python/PTY process. */
    return JNI_VERSION_1_6;
}
