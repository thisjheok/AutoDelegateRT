package com.kjache.runtime

data class BackendInfo(
    val selectedBackend: BackendId,
    val usedFallback: Boolean,
    val attemptedBackend: BackendId? = null,
    val failureReason: FailureReason? = null,
    val qnnPrepared: Boolean = false,
    val nativeAttachAttempted: Boolean = false,
    val nativeAttachSucceeded: Boolean = false,
    val message: String
)
