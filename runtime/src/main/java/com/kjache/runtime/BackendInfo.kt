package com.kjache.runtime

data class BackendInfo(
    val selectedBackend: BackendId,
    val usedFallback: Boolean,
    val message: String
)
