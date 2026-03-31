package com.kjache.runtime

class NativeQnnBridge {
    fun bridgeStatus(): String = nativeBridgeStatus()

    fun prepareQnnSession(
        delegateLibraryPath: String,
        backendLibraryPath: String,
        skelLibraryDir: String
    ): NativeQnnSessionResult {
        val detail = nativePrepareQnnSession(
            delegateLibraryPath = delegateLibraryPath,
            backendLibraryPath = backendLibraryPath,
            skelLibraryDir = skelLibraryDir
        )

        return NativeQnnSessionResult(
            attempted = true,
            attached = false,
            detail = detail
        )
    }

    private external fun nativeBridgeStatus(): String

    private external fun nativePrepareQnnSession(
        delegateLibraryPath: String,
        backendLibraryPath: String,
        skelLibraryDir: String
    ): String

    companion object {
        init {
            System.loadLibrary("runtime_native")
        }
    }
}
