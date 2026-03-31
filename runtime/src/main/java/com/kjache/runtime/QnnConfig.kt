package com.kjache.runtime

data class QnnConfig(
    val assetBaseDir: String = "qnn",
    val delegateLibraryName: String = "libQnnTFLiteDelegate.so",
    val backendLibraryName: String = "libQnnHtp.so",
    val skelAssetSubDir: String = "adsp"
)
