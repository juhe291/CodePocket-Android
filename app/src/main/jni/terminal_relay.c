#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <stdio.h>
#include <sys/ioctl.h>
#include <unistd.h>

JNIEXPORT jint JNICALL Java_com_codepocket_ide_NativeTerminalIO_open(JNIEnv *env,jobject self,jint fd,jboolean input) {
    (void)env; (void)self;
    if (fd < 0 || !isatty(fd)) return -1;
    char path[64]; snprintf(path,sizeof(path),"/proc/self/fd/%d",fd);
    /* A fresh open-file description keeps O_NONBLOCK local to this relay. The
     * proc magic link refers to the held file, rather than a recyclable pts name. */
    int copy = open(path,(input ? O_RDONLY : O_WRONLY) | O_NONBLOCK | O_CLOEXEC | O_NOCTTY);
    if (copy >= 0 && !isatty(copy)) { close(copy);return -1; }
    return copy;
}
JNIEXPORT void JNICALL Java_com_codepocket_ide_NativeTerminalIO_close(JNIEnv *env,jobject self,jint fd) {
    (void)env; (void)self; if (fd >= 0) close(fd);
}

/* Descriptors belong to the execution service's active request. Never change the
 * flags or termios of the existing Shell terminal; its foreground bridge owns it. */
JNIEXPORT jintArray JNICALL Java_com_codepocket_ide_NativeTerminalIO_dimensions(JNIEnv *env,jobject self,jint fd) {
    (void)self; struct winsize size;
    if (fd < 0 || !isatty(fd) || ioctl(fd,TIOCGWINSZ,&size) != 0 || size.ws_row < 1 || size.ws_col < 1) return NULL;
    jint values[2] = {size.ws_row,size.ws_col}; jintArray result = (*env)->NewIntArray(env,2);
    if (result) (*env)->SetIntArrayRegion(env,result,0,2,values); return result;
}
JNIEXPORT jbyteArray JNICALL Java_com_codepocket_ide_NativeTerminalIO_read(JNIEnv *env,jobject self,jint fd) {
    (void)self; if (fd < 0 || !isatty(fd)) return NULL;
    struct pollfd item = {.fd=fd,.events=POLLIN}; int ready = poll(&item,1,100);
    if (ready == 0 || (ready < 0 && errno == EINTR)) return (*env)->NewByteArray(env,0);
    if (ready < 0) return NULL;
    jbyte buffer[8192]; ssize_t count = read(fd,buffer,sizeof(buffer));
    if (count < 0 && (errno == EAGAIN || errno == EINTR)) return (*env)->NewByteArray(env,0);
    if (count <= 0) return NULL;
    jbyteArray result = (*env)->NewByteArray(env,(jsize)count);
    if (result) (*env)->SetByteArrayRegion(env,result,0,(jsize)count,buffer); return result;
}
JNIEXPORT jint JNICALL Java_com_codepocket_ide_NativeTerminalIO_write(JNIEnv *env,jobject self,jint fd,jbyteArray bytes,jint offset,jint length) {
    (void)self;
    if (fd < 0 || !isatty(fd) || !bytes || offset < 0 || length < 1 || length > 256 || offset > (*env)->GetArrayLength(env,bytes)-length) return -1;
    jbyte buffer[256]; (*env)->GetByteArrayRegion(env,bytes,offset,length,buffer);
    if ((*env)->ExceptionCheck(env)) return -1;
    struct pollfd item = {.fd=fd,.events=POLLOUT}; int ready = poll(&item,1,100);
    if (ready == 0 || (ready < 0 && errno == EINTR)) return 0;
    if (ready < 0) return -1;
    ssize_t count = write(fd,buffer,(size_t)length);
    return count < 0 && (errno == EAGAIN || errno == EINTR) ? 0 : (jint)count;
}
