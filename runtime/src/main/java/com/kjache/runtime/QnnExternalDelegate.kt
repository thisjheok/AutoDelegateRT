package com.kjache.runtime

import android.util.Log
import org.tensorflow.lite.Delegate

class QnnExternalDelegate(
    private val nativeBridge: NativeQnnBridge,
    private var nativeHandle: Long
) : Delegate {
    override fun getNativeHandle(): Long = nativeHandle

    override fun close() {
        if (nativeHandle == 0L) {
            return
        }

        Log.i(TAG, "Destroying QNN external delegate handle=$nativeHandle")
        nativeBridge.destroyDelegate(nativeHandle)
        nativeHandle = 0L
    }

    companion object {
        private const val TAG = "QnnExternalDelegate"
    }
}
