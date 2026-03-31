package com.kjache.runtime

class RuntimeEngine {
    fun createSession(options: SessionOptions = SessionOptions()): InferenceSession {
        val backendInfo = if (options.allowCpuFallback) {
            BackendInfo(
                selectedBackend = BackendId.CPU,
                usedFallback = false,
                message = "CPU baseline session created."
            )
        } else {
            BackendInfo(
                selectedBackend = BackendId.QNN_HTP,
                usedFallback = false,
                message = "QNN-only mode placeholder session created."
            )
        }

        return InferenceSession(backendInfo)
    }
}
