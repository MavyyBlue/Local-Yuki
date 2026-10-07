package com.mavyy.localyuki.admission

import com.mavyy.localyuki.foundation.resource.SafeRuntimeProfile

/** Admission bounds one organ, while a foreground turn can contain several serialized organs. */
internal class CognitiveTurnBudget(profile:SafeRuntimeProfile,started:Long,val epoch:Long) {
    val totalMillis=minOf(120_000L,profile.deadlineMillis*profile.maxPasses)
    val until=started+totalMillis
    private val maxPasses=profile.maxPasses
    private var used=0
    val inspections=TurnModelInspection()
    fun startPass(now:Long) {
        check(used<maxPasses && now<until) { "Cognitive pass/time budget exhausted" }
        used++
    }
    fun remaining(now:Long):Long=(until-now).also { check(it>=1000) { "Cognitive turn deadline exhausted" } }
    fun requireCurrentProfile(profile:SafeRuntimeProfile) {
        check(used<=minOf(maxPasses,profile.maxPasses)) { "Resource profile no longer allows this cognitive pass" }
    }
}
