package com.kjache.backend_qnn

import android.content.Context
import java.io.File

class QnnAssetInstaller(
    private val appContext: Context
) {
    fun prepareAssets(
        assetBaseDir: String,
        delegateLibraryName: String,
        backendLibraryName: String,
        skelAssetSubDir: String
    ): QnnPreparedAssets? {
        val delegateAssetPath = buildAssetPath(assetBaseDir, delegateLibraryName)
        val backendAssetPath = buildAssetPath(assetBaseDir, backendLibraryName)
        val skelAssetPath = buildAssetPath(assetBaseDir, skelAssetSubDir)

        if (!assetExists(delegateAssetPath)) {
            return null
        }
        if (!assetExists(backendAssetPath)) {
            return null
        }

        val installRoot = File(appContext.filesDir, assetBaseDir)
        if (!installRoot.exists()) {
            installRoot.mkdirs()
        }

        val delegateFile = copyAssetToFile(delegateAssetPath, File(installRoot, delegateLibraryName))
        val backendFile = copyAssetToFile(backendAssetPath, File(installRoot, backendLibraryName))
        val skelDir = File(installRoot, skelAssetSubDir)
        copyAssetDirectory(skelAssetPath, skelDir)

        return QnnPreparedAssets(
            delegateLibraryPath = delegateFile.absolutePath,
            backendLibraryPath = backendFile.absolutePath,
            skelLibraryDir = skelDir.absolutePath
        )
    }

    fun assetExists(assetPath: String): Boolean {
        return runCatching { appContext.assets.open(assetPath).close() }.isSuccess ||
            runCatching { appContext.assets.list(assetPath)?.isNotEmpty() == true }.getOrDefault(false)
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
}

data class QnnPreparedAssets(
    val delegateLibraryPath: String,
    val backendLibraryPath: String,
    val skelLibraryDir: String
)
