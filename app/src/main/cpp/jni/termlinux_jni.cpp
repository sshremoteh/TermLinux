#include <jni.h>
#include <android/log.h>
#include <cerrno>
#include <string>
#include <vector>

#include "pty/pty_manager.h"
#include "process/process_manager.h"

#define LOG_TAG "TermLinuxNative"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

std::string jstring_to_std(JNIEnv* env, jstring jstr) {
    if (!jstr) return {};
    const char* chars = env->GetStringUTFChars(jstr, nullptr);
    if (!chars) return {};
    std::string out(chars);
    env->ReleaseStringUTFChars(jstr, chars);
    return out;
}

std::vector<std::string> jstring_array_to_vec(JNIEnv* env, jobjectArray arr) {
    std::vector<std::string> out;
    if (!arr) return out;
    jsize len = env->GetArrayLength(arr);
    out.reserve(static_cast<size_t>(len));
    for (jsize i = 0; i < len; ++i) {
        auto jstr = static_cast<jstring>(env->GetObjectArrayElement(arr, i));
        out.push_back(jstring_to_std(env, jstr));
        if (jstr) env->DeleteLocalRef(jstr);
    }
    return out;
}

} // namespace

extern "C" {

JNIEXPORT jintArray JNICALL
Java_com_termlinux_runtime_NativeBridge_nativeSpawnSession(
    JNIEnv* env,
    jobject /* thiz */,
    jstring executable,
    jobjectArray argv,
    jobjectArray envp,
    jstring cwd,
    jint cols,
    jint rows
) {
    auto pty = termlinux::pty::open_pty(cols, rows);
    if (pty.master_fd < 0) {
        LOGE("open_pty failed: errno=%d", pty.err);
        return nullptr;
    }

    std::string exec_str = jstring_to_std(env, executable);
    std::string cwd_str = jstring_to_std(env, cwd);
    auto argv_vec = jstring_array_to_vec(env, argv);
    auto envp_vec = jstring_array_to_vec(env, envp);

    int spawn_err = 0;
    pid_t pid = termlinux::process::spawn_pty_process(
        pty.master_fd,
        pty.slave_path,
        exec_str,
        argv_vec,
        envp_vec,
        cwd_str,
        &spawn_err
    );

    if (pid < 0) {
        LOGE("spawn_pty_process failed: errno=%d", spawn_err);
        termlinux::pty::close_pty(pty.master_fd);
        return nullptr;
    }

    jintArray out = env->NewIntArray(2);
    if (!out) {
        termlinux::pty::close_pty(pty.master_fd);
        termlinux::process::kill_process(pid, 9);
        return nullptr;
    }

    jint pair[2] = { pty.master_fd, static_cast<jint>(pid) };
    env->SetIntArrayRegion(out, 0, 2, pair);
    return out;
}

JNIEXPORT jint JNICALL
Java_com_termlinux_runtime_NativeBridge_nativeResizePty(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jint master_fd,
    jint cols,
    jint rows
) {
    return termlinux::pty::resize_pty(master_fd, cols, rows);
}

JNIEXPORT jint JNICALL
Java_com_termlinux_runtime_NativeBridge_nativeWaitPid(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jint pid,
    jboolean no_hang
) {
    auto res = termlinux::process::wait_pid(static_cast<pid_t>(pid), no_hang == JNI_TRUE);
    if (res.err != 0) return -res.err;
    if (res.exited) return res.exit_code;
    if (res.signaled) return 128 + res.signal;
    return -1000; // still running (when no_hang == true)
}

JNIEXPORT jint JNICALL
Java_com_termlinux_runtime_NativeBridge_nativeKillProcess(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jint pid,
    jint signal
) {
    return termlinux::process::kill_process(static_cast<pid_t>(pid), signal);
}

JNIEXPORT jboolean JNICALL
Java_com_termlinux_runtime_NativeBridge_nativeIsProcessAlive(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jint pid
) {
    return termlinux::process::is_process_alive(static_cast<pid_t>(pid)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_termlinux_runtime_NativeBridge_nativeCloseFd(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jint fd
) {
    termlinux::pty::close_pty(fd);
}

} // extern "C"
