package com.mavyy.localyuki.foundation

import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.interoception.*
import org.junit.Assert.*
import org.junit.Test

class InteroceptionContractTest {
    @Test fun noProducerMeansExplicitlyUnavailableNotFabricatedNormalState() {
        assertEquals(FoundationResult.Unavailable(UnavailableReason.NOT_IMPLEMENTED), UnavailableInteroceptionReader.read())
        assertEquals(setOf(ResourcePressure.NORMAL, ResourcePressure.WARM, ResourcePressure.CONSTRAINED), ResourcePressure.entries.toSet())
        assertEquals(setOf(MemoryCertainty.CLEAR, MemoryCertainty.UNCERTAIN), MemoryCertainty.entries.toSet())
        assertEquals(setOf(ScreenAvailability.AVAILABLE, ScreenAvailability.UNAVAILABLE), ScreenAvailability.entries.toSet())
        assertEquals(setOf("resourcePressure", "memoryCertainty", "screenAvailability"),
            InteroceptiveSnapshot::class.java.declaredFields.filterNot { it.isSynthetic }.map { it.name }.toSet())
    }
}
