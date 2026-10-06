package com.mavyy.localyuki.resource

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.resource.*
import java.time.Instant

/** Permission-free, measured physical state. Unsupported thermal sensing stays UNKNOWN. */
class AndroidResourceProbe(context: Context) {
    private val app=context.applicationContext
    fun read(foreground: Boolean,now: Instant): FoundationResult<DeviceResources> = try {
        val memory=ActivityManager.MemoryInfo()
        (app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        val battery=app.registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level=battery?.getIntExtra(BatteryManager.EXTRA_LEVEL,-1) ?: -1
        val scale=battery?.getIntExtra(BatteryManager.EXTRA_SCALE,-1) ?: -1
        val percent=if(level>=0 && scale>0) ((level.toLong()*100)/scale).toInt().coerceIn(0,100) else null
        val status=battery?.getIntExtra(BatteryManager.EXTRA_STATUS,-1)
        val charging=status==BatteryManager.BATTERY_STATUS_CHARGING || status==BatteryManager.BATTERY_STATUS_FULL
        val thermal=if(Build.VERSION.SDK_INT>=29) {
            val power=app.getSystemService(Context.POWER_SERVICE) as PowerManager
            when(power.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> ThermalPressure.NORMAL
                PowerManager.THERMAL_STATUS_LIGHT -> ThermalPressure.LIGHT
                PowerManager.THERMAL_STATUS_MODERATE -> ThermalPressure.MODERATE
                PowerManager.THERMAL_STATUS_SEVERE -> ThermalPressure.SEVERE
                PowerManager.THERMAL_STATUS_CRITICAL,PowerManager.THERMAL_STATUS_EMERGENCY,PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalPressure.CRITICAL
                else -> ThermalPressure.UNKNOWN
            }
        } else ThermalPressure.UNKNOWN
        FoundationResult.Success(DeviceResources(memory.totalMem,memory.availMem,percent,charging,thermal,foreground,now,
            ComputeCapabilities(Build.SUPPORTED_ABIS.toList().take(8),Runtime.getRuntime().availableProcessors().coerceIn(1,256),null,null),memory.lowMemory,(app.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode,
            foreground && ((com.mavyy.localyuki.embodiment.YukiAccessibility.foregroundPackage?.takeIf { android.os.SystemClock.elapsedRealtime()-com.mavyy.localyuki.embodiment.YukiAccessibility.observedAt<30000 } ?: com.mavyy.localyuki.embodiment.AndroidBody(app).usageForeground())?.let { it!=app.packageName }==true)))
    } catch (_: Exception) { FoundationResult.Unavailable(UnavailableReason.DEPENDENCY_UNAVAILABLE) }
}
