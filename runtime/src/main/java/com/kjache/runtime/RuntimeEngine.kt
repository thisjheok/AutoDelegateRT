package com.kjache.runtime

import android.content.Context
import com.kjache.backend_qnn.QnnBackendAdapter

class RuntimeEngine(
    private val appContext: Context
) {
    private val qnnBackendAdapter = QnnBackendAdapter(appContext)

    fun createSession(options: SessionOptions = SessionOptions()): InferenceSession {
        val backendInfo = createBackendInfo(options)

        return InferenceSession(backendInfo)
    }

    private fun createBackendInfo(options: SessionOptions): BackendInfo {
        if (!options.preferQnn) {
            return BackendInfo(
                selectedBackend = BackendId.CPU,
                usedFallback = false,
                qnnPrepared = false,
                message = "CPU session created without attempting QNN."
            )
        }

        val qnnConfig = options.qnnConfig
        if (qnnConfig == null) {
            return createUnavailableBackendInfo(
                options = options,
                message = "QNN config is missing. Configure assets before attempting QNN."
            )
        }

        val probeResult = qnnBackendAdapter.probe(
            assetBaseDir = qnnConfig.assetBaseDir,
            delegateLibraryName = qnnConfig.delegateLibraryName,
            backendLibraryName = qnnConfig.backendLibraryName,
            skelAssetSubDir = qnnConfig.skelAssetSubDir
        )
        if (probeResult.available) {
            return BackendInfo(
                selectedBackend = BackendId.QNN_HTP,
                usedFallback = false,
                attemptedBackend = BackendId.QNN_HTP,
                qnnPrepared = true,
                message = buildPreparedMessage(probeResult)
            )
        }

        return createUnavailableBackendInfo(
            options = options,
            message = probeResult.reason
                ?: "QNN delegate attempt failed, so the session fell back to CPU."
        )
    }

    private fun createUnavailableBackendInfo(
        options: SessionOptions,
        message: String
    ): BackendInfo {
        if (options.allowCpuFallback) {
            return BackendInfo(
                selectedBackend = BackendId.CPU,
                usedFallback = true,
                attemptedBackend = BackendId.QNN_HTP,
                failureReason = FailureReason.QNN_NOT_AVAILABLE,
                qnnPrepared = false,
                message = message
            )
        }

        return BackendInfo(
            selectedBackend = BackendId.QNN_HTP,
            usedFallback = false,
            attemptedBackend = BackendId.QNN_HTP,
            failureReason = FailureReason.QNN_NOT_AVAILABLE,
            qnnPrepared = false,
            message = message
        )
    }

    private fun buildPreparedMessage(probeResult: com.kjache.backend_qnn.QnnProbeResult): String {
        return "QNN assets prepared.\n" +
            "Delegate: ${probeResult.delegateLibraryPath}\n" +
            "Backend: ${probeResult.backendLibraryPath}\n" +
            "Skel dir: ${probeResult.skelLibraryDir}"
    }
}
