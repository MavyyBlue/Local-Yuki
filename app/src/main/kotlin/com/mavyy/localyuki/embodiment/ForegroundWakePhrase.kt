package com.mavyy.localyuki.embodiment
import android.app.Activity
import android.os.*
import com.mavyy.localyuki.resource.*
import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.resource.*
import java.time.Instant
/** Optional visible-app wake phrase through REAL offline ASR. No claim of a background hotword DSP. */
object ForegroundWakePhrase {
 @Volatile var enabled=false;private set
 private var generation=0;private val main=Handler(Looper.getMainLooper())
 fun stop(){enabled=false;generation++;LocalVoice.stop()}
 fun start(activity:Activity,onWake:(String)->Unit,onStatus:(String)->Unit) {
  stop();enabled=true;val epoch=generation;val probe=AndroidResourceProbe(activity);var failures=0
  fun listen() {
   if(!enabled||generation!=epoch||activity.isDestroyed||!OwnerVisibility.active)return
   val body=probe.read(true,Instant.now())
   if(body !is FoundationResult.Success || body.value.thermal!=ThermalPressure.NORMAL || body.value.lowMemory || body.value.powerSave || (body.value.batteryPercent?:0)<30) {stop();onStatus("Wake phrase paused to protect the phone.");return}
   LocalVoice.listen(activity,{ transcript ->
    failures=0
    if(Regex("^\\s*yuki(?:\\b|[,.!?])",RegexOption.IGNORE_CASE).containsMatchIn(transcript)){stop();onWake(transcript)}
    else main.postDelayed({listen()},2000)
   },{ message->failures++;if(failures>=3){stop();onStatus(message)}else main.postDelayed({listen()},2000) })
  }
  listen()
 }
}
