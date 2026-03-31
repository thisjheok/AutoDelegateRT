package com.kjache.runtime

class InferenceSession(
    private val backendInfo: BackendInfo
) {
    fun backendInfo(): BackendInfo = backendInfo
}
