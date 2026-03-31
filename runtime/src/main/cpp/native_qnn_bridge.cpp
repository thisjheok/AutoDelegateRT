#include <jni.h>

#include <string>

namespace {

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

    const std::string detail =
        "Native bridge received QNN paths.\n"
        "Delegate: " + delegate_path + "\n"
        "Backend: " + backend_path + "\n"
        "Skel dir: " + skel_dir + "\n"
        "Attach not implemented yet.";

    return ToJString(env, detail);
}
