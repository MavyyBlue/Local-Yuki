package com.mavyy.localyuki.embodiment
import android.content.Context
import com.mavyy.localyuki.brain.*
import com.mavyy.localyuki.foundation.contracts.FoundationResult
import com.mavyy.localyuki.foundation.embodiment.CapabilityId
import java.util.concurrent.*
object BodyEvents {
 private val worker=ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,ArrayBlockingQueue(4),ThreadPoolExecutor.DiscardPolicy())
 fun submit(context:Context,kind:String,pkg:String,text:String) {
  val app=context.applicationContext
  worker.execute { ContinuityAccess.exclusive {
   BrainRuntime(app).use { brain->
    if(!brain.open())return@use
    val capability=if(kind=="NOTIFICATION")CapabilityId.NOTIFICATIONS else CapabilityId.SCREEN
    val permitted=brain.capabilities.permits(capability,pkg)
    if(permitted is FoundationResult.Success && permitted.value)brain.observeEvent(kind,pkg,text)
   }
  } }
 }
}
