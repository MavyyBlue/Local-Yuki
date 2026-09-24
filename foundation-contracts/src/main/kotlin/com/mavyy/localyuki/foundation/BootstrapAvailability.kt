package com.mavyy.localyuki.foundation

/** Only the bootstrap boundary exists in Phase 1A. This is not cognitive state. */
enum class BootstrapAvailability {
    UNAVAILABLE;

    companion object {
        /** Explicit result for subsystems that have not been implemented. */
        fun current(): BootstrapAvailability = UNAVAILABLE
    }
}
