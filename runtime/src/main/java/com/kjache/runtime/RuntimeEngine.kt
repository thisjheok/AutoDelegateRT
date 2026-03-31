package com.kjache.runtime

import android.content.Context
import com.kjache.backend_qnn.QnnBackendAdapter

class RuntimeEngine(
    private val appContext: Context
) {
    private val qnnBackendAdapter = QnnBackendAdapter(appContext)
    private val nativeQnnBridge = NativeQnnBridge()

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
                nativeAttachAttempted = false,
                nativeAttachSucceeded = false,
                message = "CPU session created without attempting QNN.\nBridge: ${nativeQnnBridge.bridgeStatus()}"
            )
        }

        val qnnConfig = options.qnnConfig
        if (qnnConfig == null) {
            return createUnavailableBackendInfo(
                options = options,
                message = "QNN config is missing. Configure assets before attempting QNN.",
                qnnPrepared = false,
                failureReason = FailureReason.QNN_NOT_AVAILABLE,
                nativeAttachAttempted = false,
                nativeAttachSucceeded = false
            )
        }

        val probeResult = qnnBackendAdapter.probe(
            assetBaseDir = qnnConfig.assetBaseDir,
            delegateLibraryName = qnnConfig.delegateLibraryName,
            backendLibraryName = qnnConfig.backendLibraryName,
            skelAssetSubDir = qnnConfig.skelAssetSubDir
        )
        if (probeResult.available) {
            val nativeResult = nativeQnnBridge.prepareQnnSession(
                delegateLibraryPath = requireNotNull(probeResult.delegateLibraryPath),
                backendLibraryPath = requireNotNull(probeResult.backendLibraryPath),
                skelLibraryDir = requireNotNull(probeResult.skelLibraryDir)
            )

            if (nativeResult.attached) {
                return BackendInfo(
                    selectedBackend = BackendId.QNN_HTP,
                    usedFallback = false,
                    attemptedBackend = BackendId.QNN_HTP,
                    qnnPrepared = true,
                    nativeAttachAttempted = nativeResult.attempted,
                    nativeAttachSucceeded = true,
                    message = buildPreparedMessage(probeResult) + "\n" + nativeResult.detail
                )
            }

            return createUnavailableBackendInfo(
                options = options,
                message = buildPreparedMessage(probeResult) + "\n" + nativeResult.detail,
                qnnPrepared = true,
                failureReason = FailureReason.DELEGATE_ATTACH_FAILED,
                nativeAttachAttempted = nativeResult.attempted,
                nativeAttachSucceeded = false
            )
        }

        return createUnavailableBackendInfo(
            options = options,
            message = probeResult.reason
                ?: "QNN delegate attempt failed, so the session fell back to CPU.",
            qnnPrepared = false,
            failureReason = FailureReason.QNN_NOT_AVAILABLE,
            nativeAttachAttempted = false,
            nativeAttachSucceeded = false
        )
    }

    private fun createUnavailableBackendInfo(
        options: SessionOptions,
        message: String,
        qnnPrepared: Boolean,
        failureReason: FailureReason,
        nativeAttachAttempted: Boolean,
        nativeAttachSucceeded: Boolean
    ): BackendInfo {
        if (options.allowCpuFallback) {
            return BackendInfo(
                selectedBackend = BackendId.CPU,
                usedFallback = true,
                attemptedBackend = BackendId.QNN_HTP,
                failureReason = failureReason,
                qnnPrepared = qnnPrepared,
                nativeAttachAttempted = nativeAttachAttempted,
                nativeAttachSucceeded = nativeAttachSucceeded,
                message = message
            )
        }

        return BackendInfo(
            selectedBackend = BackendId.QNN_HTP,
            usedFallback = false,
            attemptedBackend = BackendId.QNN_HTP,
            failureReason = failureReason,
            qnnPrepared = qnnPrepared,
            nativeAttachAttempted = nativeAttachAttempted,
            nativeAttachSucceeded = nativeAttachSucceeded,
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
