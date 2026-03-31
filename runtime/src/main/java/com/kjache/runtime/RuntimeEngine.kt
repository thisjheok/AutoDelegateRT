package com.kjache.runtime

import com.kjache.backend_qnn.QnnBackendAdapter

class RuntimeEngine {
    private val qnnBackendAdapter = QnnBackendAdapter()

    fun createSession(options: SessionOptions = SessionOptions()): InferenceSession {
        val backendInfo = createBackendInfo(options)

        return InferenceSession(backendInfo)
    }

    private fun createBackendInfo(options: SessionOptions): BackendInfo {
        if (!options.preferQnn) {
            return BackendInfo(
                selectedBackend = BackendId.CPU,
                usedFallback = false,
                message = "CPU session created without attempting QNN."
            )
        }

        val probeResult = qnnBackendAdapter.probe()
        if (probeResult.available) {
            return BackendInfo(
                selectedBackend = BackendId.QNN_HTP,
                usedFallback = false,
                attemptedBackend = BackendId.QNN_HTP,
                message = "QNN backend is available for session creation."
            )
        }

        if (options.allowCpuFallback) {
            return BackendInfo(
                selectedBackend = BackendId.CPU,
                usedFallback = true,
                attemptedBackend = BackendId.QNN_HTP,
                failureReason = FailureReason.QNN_NOT_AVAILABLE,
                message = probeResult.reason
                    ?: "QNN delegate attempt failed, so the session fell back to CPU."
            )
        }

        return BackendInfo(
            selectedBackend = BackendId.QNN_HTP,
            usedFallback = false,
            attemptedBackend = BackendId.QNN_HTP,
            failureReason = FailureReason.QNN_NOT_AVAILABLE,
            message = probeResult.reason
                ?: "QNN-only mode requested, but the delegate could not be attached."
        )
    }
}
