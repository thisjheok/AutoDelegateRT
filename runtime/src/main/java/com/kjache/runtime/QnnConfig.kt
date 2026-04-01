package com.kjache.runtime

data class QnnConfig(
    val assetBaseDir: String = "qnn",
    val delegateLibraryName: String = "libQnnTFLiteDelegate.so",
    val backendLibraryName: String = "libQnnHtp.so",
    val skelAssetSubDir: String = "adsp",
    val preferPackagedNativeLibraries: Boolean = true,
    val preloadLibraryNames: List<String> = listOf(
        "libbase.so",
        "libcutils.so",
        "libutils.so",
        "libvndksupport.so",
        "libdl_android.so",
        "libhidlbase.so",
        "libhardware.so",
        "libdmabufheap.so",
        "libvmmem.so",
        "libcdsprpc.so",
        "libadsprpc.so",
        "libsdsprpc.so",
        "vendor.qti.hardware.dsp@1.0.so"
    ),
    val testModelAssetName: String = "yolov8n_seg_int8.tflite"
)
