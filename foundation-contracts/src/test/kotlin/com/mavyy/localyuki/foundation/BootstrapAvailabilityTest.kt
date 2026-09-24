package com.mavyy.localyuki.foundation

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class BootstrapAvailabilityTest {
    @Test fun foundationRunsWithoutAndroidOnPlainJvm() {
        assertEquals(BootstrapAvailability.UNAVAILABLE, BootstrapAvailability.current())
        try {
            Class.forName("android.app.Activity")
            fail("Android classes must not be present on the foundation runtime classpath")
        } catch (_: ClassNotFoundException) {
            // A JVM-only foundation is expected to have no Android framework available.
        }
    }
}
