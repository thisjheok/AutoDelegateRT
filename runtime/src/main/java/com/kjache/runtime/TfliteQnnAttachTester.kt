package com.kjache.runtime

import android.content.Context
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.tensorflow.lite.Interpreter

class TfliteQnnAttachTester(
    private val appContext: Context
) {
    fun tryAttach(
        modelAssetName: String,
        nativeBridge: NativeQnnBridge,
        nativeResult: NativeQnnSessionResult
    ): NativeQnnSessionResult {
        if (nativeResult.delegateHandle == 0L) {
            return nativeResult.copy(
                attached = false,
                failureReason = nativeResult.failureReason ?: FailureReason.DELEGATE_CREATE_FAILED,
                detail = nativeResult.detail + "\nDelegate handle was not created."
            )
        }

        val modelBuffer = runCatching { loadAssetBuffer(modelAssetName) }.getOrElse { error ->
            return nativeResult.copy(
                attached = false,
                failureReason = FailureReason.MODEL_LOAD_FAILED,
                detail = "Failed to load model asset $modelAssetName: ${error.message}"
            )
        }

        val delegate = QnnExternalDelegate(nativeBridge, nativeResult.delegateHandle)
        return try {
            val options = Interpreter.Options().addDelegate(delegate)
            Interpreter(modelBuffer, options).use { interpreter ->
                interpreter.allocateTensors()
            }
            Log.i(TAG, "TFLite interpreter created with QNN external delegate.")
            nativeResult.copy(
                attached = true,
                failureReason = null,
                detail = nativeResult.detail + "\nTFLite interpreter attach succeeded."
            )
        } catch (error: Throwable) {
            Log.w(TAG, "TFLite interpreter attach failed", error)
            nativeResult.copy(
                attached = false,
                failureReason = FailureReason.INTERPRETER_CREATE_FAILED,
                detail = nativeResult.detail + "\nInterpreter attach failed: ${error.message}"
            )
        } finally {
            delegate.close()
        }
    }

    private fun loadAssetBuffer(modelAssetName: String): ByteBuffer {
        appContext.assets.open(modelAssetName).use { input ->
            val bytes = input.readBytes()
            return ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply {
                put(bytes)
                rewind()
            }
        }
    }

    companion object {
        private const val TAG = "TfliteQnnAttach"
    }
}
