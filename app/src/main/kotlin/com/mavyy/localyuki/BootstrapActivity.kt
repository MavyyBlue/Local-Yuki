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

/** Static bootstrap surface. No cognitive or device-capability behavior. */
class BootstrapActivity : Activity() {
    private lateinit var continuityStore: ContinuityStore
    private lateinit var continuityReader: CognitiveIdentityReader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(BootstrapAvailability.current() == BootstrapAvailability.UNAVAILABLE)
        continuityStore = ContinuityStore(applicationContext)
        val continuity = continuityStore.open()
        continuityReader = continuity.reader
        setContentView(TextView(this).apply {
            val status = when (continuity.status) {
                ContinuityStart.INITIALIZED -> "initialized"
                ContinuityStart.RESTORED -> "restored"
                ContinuityStart.UNAVAILABLE -> "unavailable"
            }
            text = "Local Yuki\nFoundation setup in progress" +
                if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) "\nContinuity: $status" else ""
            textSize = 20f
            gravity = Gravity.CENTER
        })
    }

    override fun onDestroy() {
        continuityStore.close()
        super.onDestroy()
    }
}
