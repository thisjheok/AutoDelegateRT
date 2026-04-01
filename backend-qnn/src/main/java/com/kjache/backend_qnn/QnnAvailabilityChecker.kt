package com.kjache.backend_qnn

class QnnAvailabilityChecker(
    private val assetInstaller: QnnAssetInstaller
) {
    fun check(
        assetBaseDir: String,
        delegateLibraryName: String,
        backendLibraryName: String,
        skelAssetSubDir: String,
        preloadLibraryNames: List<String>,
        preferPackagedNativeLibraries: Boolean
    ): QnnProbeResult {
        val preparedAssets = assetInstaller.prepareAssets(
            assetBaseDir = assetBaseDir,
            delegateLibraryName = delegateLibraryName,
            backendLibraryName = backendLibraryName,
            skelAssetSubDir = skelAssetSubDir,
            preloadLibraryNames = preloadLibraryNames,
            preferPackagedNativeLibraries = preferPackagedNativeLibraries
        ) ?: return buildUnavailableResult(
            assetBaseDir = assetBaseDir,
            delegateLibraryName = delegateLibraryName,
            backendLibraryName = backendLibraryName,
            skelAssetSubDir = skelAssetSubDir,
            preloadLibraryNames = preloadLibraryNames,
            preferPackagedNativeLibraries = preferPackagedNativeLibraries
        )

        return QnnProbeResult(
            available = true,
            delegateLibraryPath = preparedAssets.delegateLibraryPath,
            backendLibraryPath = preparedAssets.backendLibraryPath,
            skelLibraryDir = preparedAssets.skelLibraryDir,
            preloadLibraryPaths = preparedAssets.preloadLibraryPaths,
            usingPackagedNativeLibraries = preparedAssets.usingPackagedNativeLibraries
        )
    }

    private fun buildUnavailableResult(
        assetBaseDir: String,
        delegateLibraryName: String,
        backendLibraryName: String,
        skelAssetSubDir: String,
        preloadLibraryNames: List<String>,
        preferPackagedNativeLibraries: Boolean
    ): QnnProbeResult {
        val delegateAssetPath = buildAssetPath(assetBaseDir, delegateLibraryName)
        val backendAssetPath = buildAssetPath(assetBaseDir, backendLibraryName)
        val skelAssetPath = buildAssetPath(assetBaseDir, skelAssetSubDir)
        val packagedDelegateExists = assetInstaller.packagedLibraryExists(delegateLibraryName)
        val packagedBackendExists = assetInstaller.packagedLibraryExists(backendLibraryName)
        val skelAssetExists = assetInstaller.assetExists(skelAssetPath)

        val reason = when {
            preferPackagedNativeLibraries && !packagedDelegateExists ->
                "Missing packaged QNN delegate library: $delegateLibraryName in APK/native libs."
            preferPackagedNativeLibraries && !packagedBackendExists ->
                "Missing packaged QNN backend library: $backendLibraryName in APK/native libs."
            !preferPackagedNativeLibraries && !assetInstaller.assetExists(delegateAssetPath) ->
                "Missing QNN delegate asset: $delegateAssetPath"
            !preferPackagedNativeLibraries && !assetInstaller.assetExists(backendAssetPath) ->
                "Missing QNN backend asset: $backendAssetPath"
            !skelAssetExists && assetInstaller.nativeLibraryDirPath() == null ->
                "Missing QNN skel assets: $skelAssetPath and nativeLibraryDir is unavailable."
            else -> "Failed to prepare QNN libraries from the configured sources."
        }

        return QnnProbeResult(
            available = false,
            reason = reason
        )
    }

    private fun buildAssetPath(base: String, child: String): String {
        return if (base.isEmpty()) child else "$base/$child"
    }
}
