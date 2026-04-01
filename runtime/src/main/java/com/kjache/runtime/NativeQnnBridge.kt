package com.kjache.runtime

import android.util.Log

class NativeQnnBridge {
    fun bridgeStatus(): String = nativeBridgeStatus()

    fun prepareQnnSession(
        delegateLibraryPath: String,
        backendLibraryPath: String,
        skelLibraryDir: String,
        preloadLibraryPaths: List<String>
    ): NativeQnnSessionResult {
        Log.i(
            TAG,
            "Attempting native QNN session setup " +
                "delegate=$delegateLibraryPath backend=$backendLibraryPath skelDir=$skelLibraryDir " +
                "preload=$preloadLibraryPaths"
        )
        val detail = nativePrepareQnnSession(
            delegateLibraryPath = delegateLibraryPath,
            backendLibraryPath = backendLibraryPath,
            skelLibraryDir = skelLibraryDir,
            preloadLibraryPaths = preloadLibraryPaths.toTypedArray()
        )

        val parts = detail.split("|", limit = 4)
        val attached = parts.getOrNull(0)?.toBooleanStrictOrNull() ?: false
        val failureReason = parts.getOrNull(1)
            ?.takeIf { it.isNotBlank() && it != "NONE" }
            ?.let(FailureReason::valueOf)
        val delegateHandle = parts.getOrNull(2)?.toLongOrNull() ?: 0L
        val message = parts.getOrNull(3) ?: detail

        return NativeQnnSessionResult(
            attempted = true,
            attached = attached,
            failureReason = failureReason,
            delegateHandle = delegateHandle,
            detail = message
        ).also { result ->
            Log.i(
                TAG,
                "Native QNN session result attempted=${result.attempted} " +
                    "attached=${result.attached} failureReason=${result.failureReason} " +
                    "delegateHandle=${result.delegateHandle} detail=${result.detail}"
            )
        }
    }

    fun destroyDelegate(delegateHandle: Long) {
        nativeDestroyDelegate(delegateHandle)
    }

    private external fun nativeBridgeStatus(): String

    private external fun nativePrepareQnnSession(
        delegateLibraryPath: String,
        backendLibraryPath: String,
        skelLibraryDir: String,
        preloadLibraryPaths: Array<String>
    ): String

    private external fun nativeDestroyDelegate(delegateHandle: Long)

    companion object {
        private const val TAG = "NativeQnnBridge"

        init {
            System.loadLibrary("runtime_native")
        }
    }
}
