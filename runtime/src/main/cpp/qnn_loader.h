#ifndef AUTO_DELEGATE_RT_QNN_LOADER_H_
#define AUTO_DELEGATE_RT_QNN_LOADER_H_

#include <string>

struct QnnLoadResult {
    bool attached = false;
    std::string failure_reason = "NONE";
    long long delegate_handle = 0;
    std::string detail;
};

QnnLoadResult TryLoadQnnLibraries(
    const std::string& delegate_path,
    const std::string& backend_path,
    const std::string& skel_dir);

void DestroyDelegateHandle(long long delegate_handle);

#endif  // AUTO_DELEGATE_RT_QNN_LOADER_H_
