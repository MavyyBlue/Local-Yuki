package com.mavyy.localyuki
import android.app.Application
import android.content.ComponentCallbacks2
/** Android pressure signals immediately revoke optional native work, without touching continuity. */
class YukiApplication:Application() {
 @Suppress("DEPRECATION") override fun onTrimMemory(level:Int){super.onTrimMemory(level)
  if(level>=ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE)com.mavyy.localyuki.inference.NativeSupervisor.cancel()
 }
 @Suppress("DEPRECATION") override fun onLowMemory(){super.onLowMemory();com.mavyy.localyuki.inference.NativeSupervisor.cancel()}
}
