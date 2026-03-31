package com.kjache.runtime

data class BackendInfo(
    val selectedBackend: BackendId,
    val usedFallback: Boolean,
    val attemptedBackend: BackendId? = null,
    val failureReason: FailureReason? = null,
    val message: String
)
