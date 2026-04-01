#include "qnn_loader.h"

#include <android/log.h>
#include <dlfcn.h>
#include <sys/stat.h>

#include <cstdlib>
#include <cstring>
#include <unordered_map>
#include <sstream>
#include <string>

namespace {

constexpr char kLogTag[] = "QnnLoader";

using CreateDelegateFn = void* (*)(
    char** options_keys,
    char** options_values,
    size_t num_options,
    void (*report_error)(const char*));
using DestroyDelegateFn = void (*)(void* delegate);

struct DelegateRegistryEntry {
    void* library_handle = nullptr;
    void* backend_library_handle = nullptr;
    void* delegate_handle = nullptr;
    DestroyDelegateFn destroy_fn = nullptr;
};

std::unordered_map<long long, DelegateRegistryEntry>& DelegateRegistry() {
    static std::unordered_map<long long, DelegateRegistryEntry> registry;
    return registry;
}

bool PathExists(const std::string& path) {
    struct stat info {};
    return stat(path.c_str(), &info) == 0;
}

bool DirectoryHasEntries(const std::string& path) {
    struct stat info {};
    return stat(path.c_str(), &info) == 0 && (info.st_mode & S_IFDIR) != 0;
}

bool IsLibraryNameOnly(const std::string& value) {
    return value.find('/') == std::string::npos;
}

std::string DlopenErrorOrFallback(const std::string& fallback) {
    const char* error = dlerror();
    if (error == nullptr) {
        return fallback;
    }
    return std::string(error);
}

void ReportDelegateError(const char* message) {
    if (message != nullptr) {
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "Delegate error: %s", message);
    }
}

std::string ParentDirectory(const std::string& path) {
    const std::size_t position = path.find_last_of('/');
    if (position == std::string::npos) {
        return {};
    }
    return path.substr(0, position);
}

void ConfigureRuntimeEnvironment(const std::string& backend_path, const std::string& skel_dir) {
    if (!skel_dir.empty()) {
        setenv("ADSP_LIBRARY_PATH", skel_dir.c_str(), 1);
        __android_log_print(
            ANDROID_LOG_INFO,
            kLogTag,
            "Set ADSP_LIBRARY_PATH=%s",
            skel_dir.c_str());
    }

    const std::string backend_dir = ParentDirectory(backend_path);
    const char* current_ld = getenv("LD_LIBRARY_PATH");
    std::ostringstream ld_path;
    if (!backend_dir.empty()) {
        ld_path << backend_dir;
    }
    if (!backend_dir.empty() && backend_dir != skel_dir) {
        ld_path << ":" << skel_dir;
    }
    if (current_ld != nullptr && std::strlen(current_ld) > 0) {
        if (ld_path.tellp() > 0) {
            ld_path << ":";
        }
        ld_path << current_ld;
    }

    const std::string ld_path_value = ld_path.str();
    setenv("LD_LIBRARY_PATH", ld_path_value.c_str(), 1);
    __android_log_print(
        ANDROID_LOG_INFO,
        kLogTag,
        "Set LD_LIBRARY_PATH=%s",
        ld_path_value.c_str());
}

}  // namespace

QnnLoadResult TryLoadQnnLibraries(
    const std::string& delegate_path,
    const std::string& backend_path,
    const std::string& skel_dir) {
    QnnLoadResult result;

    __android_log_print(
        ANDROID_LOG_INFO,
        kLogTag,
        "TryLoadQnnLibraries delegate=%s backend=%s skelDir=%s packaged=%d",
        delegate_path.c_str(),
        backend_path.c_str(),
        skel_dir.c_str(),
        IsLibraryNameOnly(delegate_path) && IsLibraryNameOnly(backend_path) ? 1 : 0);

    const bool packaged_libraries =
        IsLibraryNameOnly(delegate_path) && IsLibraryNameOnly(backend_path);

    if (!packaged_libraries && !DirectoryHasEntries(skel_dir)) {
        result.failure_reason = "SKELETON_LIBRARY_MISSING";
        result.detail = "Skel directory is missing or inaccessible: " + skel_dir;
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s", result.detail.c_str());
        return result;
    }

    if (!packaged_libraries && !PathExists(delegate_path)) {
        result.failure_reason = "DELEGATE_LIBRARY_LOAD_FAILED";
        result.detail = "Delegate library path does not exist: " + delegate_path;
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s", result.detail.c_str());
        return result;
    }

    if (!packaged_libraries && !PathExists(backend_path)) {
        result.failure_reason = "BACKEND_LIBRARY_LOAD_FAILED";
        result.detail = "Backend library path does not exist: " + backend_path;
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s", result.detail.c_str());
        return result;
    }

    ConfigureRuntimeEnvironment(backend_path, skel_dir);

    dlerror();
    void* backend_handle = dlopen(backend_path.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (backend_handle == nullptr) {
        result.failure_reason = "BACKEND_LIBRARY_LOAD_FAILED";
        result.detail = "Failed to load backend library: " + DlopenErrorOrFallback(backend_path);
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s", result.detail.c_str());
        return result;
    }

    dlerror();
    void* delegate_handle = dlopen(delegate_path.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (delegate_handle == nullptr) {
        result.failure_reason = "DELEGATE_LIBRARY_LOAD_FAILED";
        result.detail = "Failed to load delegate library: " + DlopenErrorOrFallback(delegate_path);
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s", result.detail.c_str());
        dlclose(backend_handle);
        return result;
    }

    dlerror();
    auto create_delegate = reinterpret_cast<CreateDelegateFn>(
        dlsym(delegate_handle, "tflite_plugin_create_delegate"));
    if (create_delegate == nullptr) {
        result.failure_reason = "DELEGATE_CREATE_FAILED";
        result.detail = "Delegate create symbol not found: " + DlopenErrorOrFallback("tflite_plugin_create_delegate");
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s", result.detail.c_str());
        dlclose(delegate_handle);
        dlclose(backend_handle);
        return result;
    }

    dlerror();
    auto destroy_delegate = reinterpret_cast<DestroyDelegateFn>(
        dlsym(delegate_handle, "tflite_plugin_destroy_delegate"));
    if (destroy_delegate == nullptr) {
        result.failure_reason = "DELEGATE_CREATE_FAILED";
        result.detail = "Delegate destroy symbol not found: " + DlopenErrorOrFallback("tflite_plugin_destroy_delegate");
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s", result.detail.c_str());
        dlclose(delegate_handle);
        dlclose(backend_handle);
        return result;
    }

    const char* keys[] = {"backend_type", "library_path", "skel_library_dir"};
    char backend_type_value[] = "htp";
    std::string backend_path_mutable = backend_path;
    std::string skel_dir_mutable = skel_dir;
    char* values[] = {
        backend_type_value,
        backend_path_mutable.data(),
        skel_dir_mutable.data()
    };

    void* created_delegate = create_delegate(values ? const_cast<char**>(keys) : nullptr, values, 3, ReportDelegateError);
    if (created_delegate == nullptr) {
        result.failure_reason = "DELEGATE_CREATE_FAILED";
        result.detail = "QNN delegate creation returned null.";
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s", result.detail.c_str());
        dlclose(delegate_handle);
        dlclose(backend_handle);
        return result;
    }

    std::ostringstream stream;
    stream << "Loaded QNN libraries in native layer and created delegate.\n"
           << "Delegate: " << delegate_path << "\n"
           << "Backend: " << backend_path << "\n"
           << "Skel dir: " << skel_dir << "\n"
           << "Delegate handle: " << created_delegate;

    result.attached = false;
    result.failure_reason = "NONE";
    result.delegate_handle = reinterpret_cast<long long>(created_delegate);
    result.detail = stream.str();
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "%s", result.detail.c_str());

    DelegateRegistry()[result.delegate_handle] = DelegateRegistryEntry{
        .library_handle = delegate_handle,
        .backend_library_handle = backend_handle,
        .delegate_handle = created_delegate,
        .destroy_fn = destroy_delegate
    };
    return result;
}

void DestroyDelegateHandle(long long delegate_handle) {
    auto& registry = DelegateRegistry();
    auto found = registry.find(delegate_handle);
    if (found == registry.end()) {
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "Delegate handle not found for destroy: %lld", delegate_handle);
        return;
    }

    if (found->second.destroy_fn != nullptr && found->second.delegate_handle != nullptr) {
        found->second.destroy_fn(found->second.delegate_handle);
    }
    if (found->second.library_handle != nullptr) {
        dlclose(found->second.library_handle);
    }
    if (found->second.backend_library_handle != nullptr) {
        dlclose(found->second.backend_library_handle);
    }
    registry.erase(found);
}
