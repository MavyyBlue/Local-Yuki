// Host-only regression fixture. No Android target links this library.
#include <jni.h>
#include <linux/filter.h>
#include <linux/seccomp.h>
#include <sys/prctl.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <fcntl.h>
#include <cerrno>
#include <cstddef>
#include <cstdio>

static void fail(JNIEnv * env, const char * text) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), text);
}
extern "C" JNIEXPORT void JNICALL Java_com_mavyy_localyuki_inference_NativeDescriptorRegression_denyPathOpens(JNIEnv * env, jclass) {
    // Deny file pathname opens on this thread and its future worker threads while
    // preserving read/pread/lseek/mmap/fstat/fcntl on already-authorized descriptors.
    const sock_filter code[] = {
        BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, nr)),
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_openat, 0, 1),
        BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ERRNO | EACCES),
#ifdef __NR_open
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_open, 0, 1),
        BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ERRNO | EACCES),
#endif
#ifdef __NR_openat2
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_openat2, 0, 1),
        BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ERRNO | EACCES),
#endif
        BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW),
    };
    const sock_fprog program{static_cast<unsigned short>(sizeof(code) / sizeof(code[0])), const_cast<sock_filter *>(code)};
    if (prctl(PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0) || prctl(PR_SET_SECCOMP, SECCOMP_MODE_FILTER, &program)) {
        fail(env, "seccomp restriction could not be installed");
    }
}
extern "C" JNIEXPORT jboolean JNICALL Java_com_mavyy_localyuki_inference_NativeDescriptorRegression_reopenIsDenied(JNIEnv *, jclass, jint fd) {
    char path[64];snprintf(path, sizeof(path), "/proc/self/fd/%d", fd);
    const int reopened = open(path, O_RDONLY);
    if (reopened >= 0) { close(reopened);return false; }
    return errno == EACCES;
}
extern "C" JNIEXPORT jint JNICALL Java_com_mavyy_localyuki_inference_NativeDescriptorRegression_openDescriptorCount(JNIEnv *, jclass) {
    int count = 0;
    for (int fd = 0; fd < 4096; ++fd) if (fcntl(fd, F_GETFD) >= 0) ++count;
    return count;
}
