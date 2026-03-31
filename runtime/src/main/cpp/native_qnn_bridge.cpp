#include <jni.h>

#include <android/log.h>
#include <string>

#include "qnn_loader.h"

namespace {

constexpr char kLogTag[] = "NativeQnnBridge";

jstring ToJString(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}

std::string FromJString(JNIEnv* env, jstring value) {
    if (value == nullptr) {
        return {};
    }

    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) {
        return {};
    }

    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_kjache_runtime_NativeQnnBridge_nativeBridgeStatus(
    JNIEnv* env,
    jobject /* this */) {
    return ToJString(env, "native-ready");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_kjache_runtime_NativeQnnBridge_nativePrepareQnnSession(
    JNIEnv* env,
    jobject /* this */,
    jstring delegate_library_path,
    jstring backend_library_path,
    jstring skel_library_dir) {
    const std::string delegate_path = FromJString(env, delegate_library_path);
    const std::string backend_path = FromJString(env, backend_library_path);
    const std::string skel_dir = FromJString(env, skel_library_dir);

    __android_log_print(
        ANDROID_LOG_INFO,
        kLogTag,
        "nativePrepareQnnSession delegate=%s backend=%s skelDir=%s",
        delegate_path.c_str(),
        backend_path.c_str(),
        skel_dir.c_str());

    const QnnLoadResult result = TryLoadQnnLibraries(delegate_path, backend_path, skel_dir);
    __android_log_print(
        ANDROID_LOG_INFO,
        kLogTag,
        "nativePrepareQnnSession attached=%d failureReason=%s delegateHandle=%lld detail=%s",
        result.attached ? 1 : 0,
        result.failure_reason.c_str(),
        result.delegate_handle,
        result.detail.c_str());
    const std::string payload =
        std::string(result.attached ? "true" : "false") + "|" +
        result.failure_reason + "|" +
        std::to_string(result.delegate_handle) + "|" +
        result.detail;

    return ToJString(env, payload);
}

extern "C" JNIEXPORT void JNICALL
Java_com_kjache_runtime_NativeQnnBridge_nativeDestroyDelegate(
    JNIEnv* /* env */,
    jobject /* this */,
    jlong delegate_handle) {
    DestroyDelegateHandle(static_cast<long long>(delegate_handle));
}
