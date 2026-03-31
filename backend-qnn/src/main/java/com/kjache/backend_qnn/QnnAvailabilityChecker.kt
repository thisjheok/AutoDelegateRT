package com.kjache.backend_qnn

class QnnAvailabilityChecker(
    private val assetInstaller: QnnAssetInstaller
) {
    fun check(
        assetBaseDir: String,
        delegateLibraryName: String,
        backendLibraryName: String,
        skelAssetSubDir: String
    ): QnnProbeResult {
        val delegateAssetPath = buildAssetPath(assetBaseDir, delegateLibraryName)
        val backendAssetPath = buildAssetPath(assetBaseDir, backendLibraryName)
        val skelAssetPath = buildAssetPath(assetBaseDir, skelAssetSubDir)

        if (!assetInstaller.assetExists(delegateAssetPath)) {
            return QnnProbeResult(
                available = false,
                reason = "Missing QNN delegate asset: $delegateAssetPath"
            )
        }
        if (!assetInstaller.assetExists(backendAssetPath)) {
            return QnnProbeResult(
                available = false,
                reason = "Missing QNN backend asset: $backendAssetPath"
            )
        }
        if (!assetInstaller.assetExists(skelAssetPath)) {
            return QnnProbeResult(
                available = false,
                reason = "Missing QNN skel asset directory: $skelAssetPath"
            )
        }

        val preparedAssets = assetInstaller.prepareAssets(
            assetBaseDir = assetBaseDir,
            delegateLibraryName = delegateLibraryName,
            backendLibraryName = backendLibraryName,
            skelAssetSubDir = skelAssetSubDir
        ) ?: return QnnProbeResult(
            available = false,
            reason = "Failed to prepare QNN assets in app storage."
        )

        return QnnProbeResult(
            available = true,
            delegateLibraryPath = preparedAssets.delegateLibraryPath,
            backendLibraryPath = preparedAssets.backendLibraryPath,
            skelLibraryDir = preparedAssets.skelLibraryDir
        )
    }

    private fun buildAssetPath(base: String, child: String): String {
        return if (base.isEmpty()) child else "$base/$child"
    }
}
