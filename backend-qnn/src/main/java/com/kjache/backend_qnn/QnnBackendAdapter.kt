package com.kjache.backend_qnn

class QnnBackendAdapter(
    private val availabilityChecker: QnnAvailabilityChecker = QnnAvailabilityChecker()
) {
    fun probe(): QnnProbeResult {
        val available = availabilityChecker.isQnnAvailable()
        if (available) {
            return QnnProbeResult(available = true)
        }

        return QnnProbeResult(
            available = false,
            reason = availabilityChecker.unavailableReason()
        )
    }
}
