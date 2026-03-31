package com.kjache.runtime

data class NativeQnnSessionResult(
    val attempted: Boolean,
    val attached: Boolean,
    val failureReason: FailureReason? = null,
    val delegateHandle: Long = 0L,
    val detail: String
)
