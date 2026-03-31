package com.kjache.backend_qnn

class QnnAvailabilityChecker {
    fun isQnnAvailable(): Boolean {
        // Placeholder until real device probing and library checks are implemented.
        return false
    }

    fun unavailableReason(): String {
        return "QNN runtime libraries are not configured yet."
    }
}
