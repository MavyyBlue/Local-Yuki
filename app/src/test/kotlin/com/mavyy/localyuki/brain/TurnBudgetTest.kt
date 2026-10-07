package com.mavyy.localyuki.brain

import com.mavyy.localyuki.admission.*
import com.mavyy.localyuki.foundation.resource.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class TurnBudgetTest {
    private val profile=SafeRuntimeProfile(4096,256,4,64,4,30000,1024*ResourceGovernor.MIB,OperatingMode.INTERACTIVE)
    private val receipt=JSONObject().put("context",4096).put("output",99).put("threads",4).put("batch",64)
        .put("lastBenchmark",JSONObject().put("deadlineMs",30000))
    private fun rejects(block:()->Unit) { try { block();fail("Exceeded a resource/time bound") }catch(_:IllegalStateException){} }
    @Test fun galaxyTraceLeavesLanguageItsOwnMeasuredDeadlineIncludingDistinctFileValidation() {
        val budget=CognitiveTurnBudget(profile,0,7)
        budget.startPass(0)
        // Reproduce the owner's successful One timings, then a second distinct model's full hash.
        val oneFinished=4421L+671+15178+5770
        budget.startPass(oneFinished)
        val afterLanguageIntegrity=oneFinished+4482
        val language=runtimeProfileForRole(profile,receipt,budget.remaining(afterLanguageIntegrity))
        assertEquals(30000L,language.deadlineMillis);assertEquals(120000L,budget.totalMillis)
        assertEquals(7L,budget.epoch)
    }
    @Test fun receiptPressurePassCountAndWholeTurnBoundsCannotBeBypassed() {
        val budget=CognitiveTurnBudget(profile,100,0)
        repeat(4) { budget.startPass(100+it.toLong()) }
        rejects { budget.startPass(105) };rejects { budget.remaining(120100) }
        val limited=profile.copy(maxPasses=1,deadlineMillis=12000,mode=OperatingMode.THERMAL_LIMITED)
        val onePass=CognitiveTurnBudget(limited,0,0);onePass.startPass(0)
        assertEquals(12000L,onePass.totalMillis);rejects { onePass.startPass(1) }
        rejects { budget.requireCurrentProfile(limited) }
        val admitted=JSONObject(receipt.toString()).put("lastBenchmark",JSONObject().put("deadlineMs",12000))
        assertEquals(12000L,runtimeProfileForRole(profile,admitted,90000).deadlineMillis)
        assertEquals(2500L,runtimeProfileForRole(limited,receipt,2500).deadlineMillis)
        assertEquals(120000L,CognitiveTurnBudget(profile.copy(deadlineMillis=60000),0,0).totalMillis)
    }
}
