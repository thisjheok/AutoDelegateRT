package com.kjache.backend_qnn

import android.content.Context

class QnnBackendAdapter(
    appContext: Context
) {
    private val availabilityChecker = QnnAvailabilityChecker(QnnAssetInstaller(appContext))

    fun probe(
        assetBaseDir: String,
        delegateLibraryName: String,
        backendLibraryName: String,
        skelAssetSubDir: String,
        preloadLibraryNames: List<String>,
        preferPackagedNativeLibraries: Boolean
    ): QnnProbeResult {
        return availabilityChecker.check(
            assetBaseDir = assetBaseDir,
            delegateLibraryName = delegateLibraryName,
            backendLibraryName = backendLibraryName,
            skelAssetSubDir = skelAssetSubDir,
            preloadLibraryNames = preloadLibraryNames,
            preferPackagedNativeLibraries = preferPackagedNativeLibraries
        )
    }
}
