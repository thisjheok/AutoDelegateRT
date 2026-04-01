package com.kjache.backend_qnn

data class QnnProbeResult(
    val available: Boolean,
    val reason: String? = null,
    val delegateLibraryPath: String? = null,
    val backendLibraryPath: String? = null,
    val skelLibraryDir: String? = null,
    val preloadLibraryPaths: List<String> = emptyList(),
    val usingPackagedNativeLibraries: Boolean = false
)
