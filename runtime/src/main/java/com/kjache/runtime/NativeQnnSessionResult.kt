package com.kjache.runtime

data class NativeQnnSessionResult(
    val attempted: Boolean,
    val attached: Boolean,
    val detail: String
)
