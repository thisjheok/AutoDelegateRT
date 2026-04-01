package com.kjache.backend_qnn

import android.content.Context
import android.os.Build
import java.io.File
import java.util.zip.ZipFile

class QnnAssetInstaller(
    private val appContext: Context
) {
    fun prepareAssets(
        assetBaseDir: String,
        delegateLibraryName: String,
        backendLibraryName: String,
        skelAssetSubDir: String,
        preloadLibraryNames: List<String>,
        preferPackagedNativeLibraries: Boolean
    ): QnnPreparedAssets? {
        val skelAssetPath = buildAssetPath(assetBaseDir, skelAssetSubDir)
        val nativeLibraryDir = nativeLibraryDirPath()?.let(::File)

        val delegateTarget = if (preferPackagedNativeLibraries) {
            resolvePackagedLibrary(delegateLibraryName)
        } else {
            val delegateAssetPath = buildAssetPath(assetBaseDir, delegateLibraryName)
            if (!assetExists(delegateAssetPath)) {
                return null
            }
            val installRoot = File(appContext.filesDir, assetBaseDir)
            if (!installRoot.exists()) {
                installRoot.mkdirs()
            }
            PackagedLibraryTarget(
                loadTarget = copyAssetToFile(
                    delegateAssetPath,
                    File(installRoot, delegateLibraryName)
                ).absolutePath
            )
        } ?: return null

        val backendTarget = if (preferPackagedNativeLibraries) {
            resolvePackagedLibrary(backendLibraryName)
        } else {
            val backendAssetPath = buildAssetPath(assetBaseDir, backendLibraryName)
            if (!assetExists(backendAssetPath)) {
                return null
            }
            val installRoot = File(appContext.filesDir, assetBaseDir)
            if (!installRoot.exists()) {
                installRoot.mkdirs()
            }
            PackagedLibraryTarget(
                loadTarget = copyAssetToFile(
                    backendAssetPath,
                    File(installRoot, backendLibraryName)
                ).absolutePath
            )
        } ?: return null

        val skelDir = if (!preferPackagedNativeLibraries && assetExists(skelAssetPath)) {
            val installRoot = File(appContext.filesDir, assetBaseDir)
            if (!installRoot.exists()) {
                installRoot.mkdirs()
            }
            File(installRoot, skelAssetSubDir).also { copyAssetDirectory(skelAssetPath, it) }
        } else {
            nativeLibraryDir
        } ?: return null

        val preloadLibraryPaths = preloadLibraryNames.mapNotNull { libraryName ->
            resolveOptionalLibrary(
                assetBaseDir = assetBaseDir,
                libraryName = libraryName,
                preferPackagedNativeLibraries = preferPackagedNativeLibraries
            )
        }

        return QnnPreparedAssets(
            delegateLibraryPath = delegateTarget.loadTarget,
            backendLibraryPath = backendTarget.loadTarget,
            skelLibraryDir = skelDir.absolutePath,
            preloadLibraryPaths = preloadLibraryPaths,
            usingPackagedNativeLibraries = preferPackagedNativeLibraries
        )
    }

    fun assetExists(assetPath: String): Boolean {
        return runCatching { appContext.assets.open(assetPath).close() }.isSuccess ||
            runCatching { appContext.assets.list(assetPath)?.isNotEmpty() == true }.getOrDefault(false)
    }

    fun packagedLibraryExists(libraryName: String): Boolean {
        return resolvePackagedLibrary(libraryName) != null
    }

    fun nativeLibraryDirPath(): String? {
        return appContext.applicationInfo.nativeLibraryDir
    }

    private fun copyAssetDirectory(assetDir: String, targetDir: File) {
        val children = appContext.assets.list(assetDir).orEmpty()
        if (children.isEmpty()) {
            targetDir.mkdirs()
            return
        }

        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        children.forEach { child ->
            val childAssetPath = buildAssetPath(assetDir, child)
            val childTarget = File(targetDir, child)
            val grandChildren = appContext.assets.list(childAssetPath).orEmpty()
            if (grandChildren.isEmpty()) {
                copyAssetToFile(childAssetPath, childTarget)
            } else {
                copyAssetDirectory(childAssetPath, childTarget)
            }
        }
    }

    private fun copyAssetToFile(assetPath: String, targetFile: File): File {
        targetFile.parentFile?.mkdirs()
        appContext.assets.open(assetPath).use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return targetFile
    }

    private fun buildAssetPath(base: String, child: String): String {
        return if (base.isEmpty()) child else "$base/$child"
    }

    private fun resolveOptionalLibrary(
        assetBaseDir: String,
        libraryName: String,
        preferPackagedNativeLibraries: Boolean
    ): String? {
        if (preferPackagedNativeLibraries) {
            return resolvePackagedLibrary(libraryName)?.loadTarget ?: libraryName
        }

        val candidateAssetPaths = listOf(
            buildAssetPath(assetBaseDir, libraryName),
            libraryName
        )
        val selectedAssetPath = candidateAssetPaths.firstOrNull(::assetExists) ?: return null
        val installRoot = File(appContext.filesDir, assetBaseDir)
        if (!installRoot.exists()) {
            installRoot.mkdirs()
        }
        return copyAssetToFile(
            selectedAssetPath,
            File(installRoot, libraryName)
        ).absolutePath
    }

    private fun resolvePackagedLibrary(libraryName: String): PackagedLibraryTarget? {
        val nativeLibraryDir = nativeLibraryDirPath()?.let(::File)
        val extractedFile = nativeLibraryDir?.let { File(it, libraryName) }?.takeIf { it.exists() }
        if (extractedFile != null) {
            return PackagedLibraryTarget(
                loadTarget = extractedFile.absolutePath
            )
        }

        val apkContainsLibrary = packagedLibraryEntries(libraryName).isNotEmpty()
        if (!apkContainsLibrary) {
            return null
        }

        return PackagedLibraryTarget(
            loadTarget = libraryName
        )
    }

    private fun packagedLibraryEntries(libraryName: String): List<String> {
        val packageCodePaths = buildList {
            add(appContext.applicationInfo.sourceDir)
            appContext.applicationInfo.splitSourceDirs?.let(::addAll)
        }.filterNotNull()

        val candidateEntries = supportedAbis().map { abi -> "lib/$abi/$libraryName" }.toSet()
        val matches = mutableListOf<String>()

        packageCodePaths.forEach { apkPath ->
            runCatching {
                ZipFile(apkPath).use { zip ->
                    candidateEntries.forEach { entry ->
                        if (zip.getEntry(entry) != null) {
                            matches += "$apkPath!/$entry"
                        }
                    }
                }
            }
        }

        return matches
    }

    private fun supportedAbis(): List<String> {
        return Build.SUPPORTED_ABIS?.filterNotNull()?.filter { it.isNotBlank() }.orEmpty()
    }
}

data class QnnPreparedAssets(
    val delegateLibraryPath: String,
    val backendLibraryPath: String,
    val skelLibraryDir: String,
    val preloadLibraryPaths: List<String>,
    val usingPackagedNativeLibraries: Boolean
)

private data class PackagedLibraryTarget(
    val loadTarget: String
)
