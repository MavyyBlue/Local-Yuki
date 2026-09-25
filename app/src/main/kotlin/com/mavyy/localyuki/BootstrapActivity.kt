package com.mavyy.localyuki

import android.app.Activity
import android.os.Bundle
import android.content.pm.ApplicationInfo
import android.view.Gravity
import android.widget.TextView
import com.mavyy.localyuki.foundation.BootstrapAvailability
import com.mavyy.localyuki.continuity.ContinuityStore
import com.mavyy.localyuki.continuity.ContinuityStart
import com.mavyy.localyuki.foundation.cognition.CognitiveIdentityReader
import com.mavyy.localyuki.state.YukiStateStore
import com.mavyy.localyuki.state.deviceTemporalGrounding
import com.mavyy.localyuki.foundation.contracts.FoundationResult
import com.mavyy.localyuki.memory.MemoryStore
import com.mavyy.localyuki.memory.LivingMemoryStore
import com.mavyy.localyuki.memory.DeterministicLexicalRecallEngine

/** Static bootstrap surface. No cognitive or device-capability behavior. */
class BootstrapActivity : Activity() {
    private lateinit var continuityStore: ContinuityStore
    private lateinit var continuityReader: CognitiveIdentityReader
    private lateinit var stateStore: YukiStateStore
    private lateinit var memoryStore: MemoryStore
    private lateinit var livingStore: LivingMemoryStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(BootstrapAvailability.current() == BootstrapAvailability.UNAVAILABLE)
        continuityStore = ContinuityStore(applicationContext)
        val continuity = continuityStore.open()
        continuityReader = continuity.reader
        val temporal = deviceTemporalGrounding()
        stateStore = YukiStateStore(applicationContext, temporal)
        val state = stateStore.open()
        memoryStore = MemoryStore(applicationContext, temporal)
        val memory = memoryStore.open()
        livingStore = LivingMemoryStore(applicationContext, temporal, memoryStore.reader())
        val living = livingStore.open()
        val lexicalReady = if (living != LivingMemoryStore.Start.UNAVAILABLE) {
            DeterministicLexicalRecallEngine(applicationContext, memoryStore.reader(), livingStore.reader()).use {
                it.propose(com.mavyy.localyuki.foundation.memory.RecallQuery("bootstrap",1)) is FoundationResult.Success
            }
        } else false
        val time = temporal.ground()
        setContentView(TextView(this).apply {
            val status = when (continuity.status) {
                ContinuityStart.INITIALIZED -> "initialized"
                ContinuityStart.RESTORED -> "restored"
                ContinuityStart.UNAVAILABLE -> "unavailable"
            }
            text = "Local Yuki\nFoundation setup in progress" +
                if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                    val stateStatus = state.status.name.lowercase()
                    val timeText = if (time is FoundationResult.Success)
                        "\nTemporal: grounded\nLocal date: ${time.value.localDate}\nTimezone: ${time.value.zoneId.id}"
                        else "\nTemporal: unavailable"
                    "\nContinuity: $status\nYuki State: $stateStatus\nMemory: ${memory.name.lowercase()}" +
                        "\nLiving Memory: ${living.name.lowercase()}\nSemantic Recall: ${if (lexicalReady) "lexical-ready" else "unavailable"}$timeText"
                } else ""
            textSize = 20f
            gravity = Gravity.CENTER
        })
    }

    override fun onDestroy() {
        livingStore.close()
        memoryStore.close()
        stateStore.close()
        continuityStore.close()
        super.onDestroy()
    }
}
