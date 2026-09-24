package com.mavyy.localyuki.foundation.interoception

import com.mavyy.localyuki.foundation.contracts.FoundationResult
import com.mavyy.localyuki.foundation.contracts.UnavailableReason

enum class ResourcePressure { NORMAL, WARM, CONSTRAINED }
enum class MemoryCertainty { CLEAR, UNCERTAIN }
enum class ScreenAvailability { AVAILABLE, UNAVAILABLE }

/** Future grounded producers may populate this; no measurements are present in Phase 2A. */
data class InteroceptiveSnapshot(
    val resourcePressure: ResourcePressure,
    val memoryCertainty: MemoryCertainty,
    val screenAvailability: ScreenAvailability
)

fun interface InteroceptionReader {
    fun read(): FoundationResult<InteroceptiveSnapshot>
}

object UnavailableInteroceptionReader : InteroceptionReader {
    override fun read(): FoundationResult<InteroceptiveSnapshot> =
        FoundationResult.Unavailable(UnavailableReason.NOT_IMPLEMENTED)
}
