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

/** Static bootstrap surface. No cognitive or device-capability behavior. */
class BootstrapActivity : Activity() {
    private lateinit var continuityStore: ContinuityStore
    private lateinit var continuityReader: CognitiveIdentityReader
    private lateinit var stateStore: YukiStateStore
    private lateinit var memoryStore: MemoryStore

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
                    "\nContinuity: $status\nYuki State: $stateStatus\nMemory: ${memory.name.lowercase()}$timeText"
                } else ""
            textSize = 20f
            gravity = Gravity.CENTER
        })
    }

    override fun onDestroy() {
        memoryStore.close()
        stateStore.close()
        continuityStore.close()
        super.onDestroy()
    }
}
