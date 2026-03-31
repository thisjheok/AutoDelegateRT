package com.kjache.runtime

data class SessionOptions(
    val preferQnn: Boolean = true,
    val allowCpuFallback: Boolean = true,
    val qnnConfig: QnnConfig? = null
)
