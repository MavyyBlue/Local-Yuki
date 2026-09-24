package com.mavyy.localyuki

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import com.mavyy.localyuki.foundation.BootstrapAvailability

/** Static bootstrap surface. No cognitive or device-capability behavior. */
class BootstrapActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(BootstrapAvailability.current() == BootstrapAvailability.UNAVAILABLE)
        setContentView(TextView(this).apply {
            text = "Local Yuki\nFoundation setup in progress"
            textSize = 20f
            gravity = Gravity.CENTER
        })
    }
}
