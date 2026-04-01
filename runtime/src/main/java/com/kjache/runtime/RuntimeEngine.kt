package com.kjache.runtime

import android.content.Context
import android.util.Log
import com.kjache.backend_qnn.QnnBackendAdapter

class RuntimeEngine(
    private val appContext: Context
) {
    private val qnnBackendAdapter = QnnBackendAdapter(appContext)
    private val nativeQnnBridge = NativeQnnBridge()
    private val tfliteQnnAttachTester = TfliteQnnAttachTester(appContext)

    fun createSession(options: SessionOptions = SessionOptions()): InferenceSession {
        val backendInfo = createBackendInfo(options)

        return InferenceSession(backendInfo)
    }

    private fun createBackendInfo(options: SessionOptions): BackendInfo {
        if (!options.preferQnn) {
            Log.i(TAG, "QNN preference disabled. Creating CPU-only session.")
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
            Log.w(TAG, "QNN config missing. Falling back according to session options.")
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
            skelAssetSubDir = qnnConfig.skelAssetSubDir,
            preferPackagedNativeLibraries = qnnConfig.preferPackagedNativeLibraries
        )
        Log.i(
            TAG,
            "QNN probe result available=${probeResult.available} " +
                "delegate=${probeResult.delegateLibraryPath} backend=${probeResult.backendLibraryPath} " +
                "skelDir=${probeResult.skelLibraryDir} reason=${probeResult.reason}"
        )
        if (probeResult.available) {
            val preparedNativeResult = nativeQnnBridge.prepareQnnSession(
                delegateLibraryPath = requireNotNull(probeResult.delegateLibraryPath),
                backendLibraryPath = requireNotNull(probeResult.backendLibraryPath),
                skelLibraryDir = requireNotNull(probeResult.skelLibraryDir)
            )
            val nativeResult = tfliteQnnAttachTester.tryAttach(
                modelAssetName = qnnConfig.testModelAssetName,
                nativeBridge = nativeQnnBridge,
                nativeResult = preparedNativeResult
            )
            Log.i(
                TAG,
                "Native attach outcome attached=${nativeResult.attached} " +
                    "failureReason=${nativeResult.failureReason} delegateHandle=${nativeResult.delegateHandle}"
            )

            if (nativeResult.attached) {
                return BackendInfo(
                    selectedBackend = BackendId.QNN_HTP,
                    usedFallback = false,
                    attemptedBackend = BackendId.QNN_HTP,
                    qnnPrepared = true,
                    nativeAttachAttempted = nativeResult.attempted,
                    nativeAttachSucceeded = true,
                    message = buildPreparedMessage(probeResult, qnnConfig.testModelAssetName) + "\n" + nativeResult.detail
                )
            }

            return createUnavailableBackendInfo(
                options = options,
                message = buildPreparedMessage(probeResult, qnnConfig.testModelAssetName) + "\n" + nativeResult.detail,
                qnnPrepared = true,
                failureReason = nativeResult.failureReason ?: FailureReason.DELEGATE_ATTACH_FAILED,
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

    private fun buildPreparedMessage(
        probeResult: com.kjache.backend_qnn.QnnProbeResult,
        modelAssetName: String
    ): String {
        val librarySource = if (probeResult.usingPackagedNativeLibraries) {
            "packaged jniLibs/APK native libs"
        } else {
            "copied assets"
        }
        return "QNN libraries prepared.\n" +
            "Library source: $librarySource\n" +
            "Delegate: ${probeResult.delegateLibraryPath}\n" +
            "Backend: ${probeResult.backendLibraryPath}\n" +
            "Skel dir: ${probeResult.skelLibraryDir}\n" +
            "Model asset: $modelAssetName"
    }

    companion object {
        private const val TAG = "RuntimeEngine"
    }
}
