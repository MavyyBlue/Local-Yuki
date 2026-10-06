package com.mavyy.localyuki.embodiment
import android.service.notification.*
import android.os.SystemClock
class YukiNotifications:NotificationListenerService() {
 companion object { @Volatile internal var live:YukiNotifications?=null }
 private var last=0L
 override fun onListenerConnected(){live=this}
 override fun onListenerDisconnected(){live=null}
 override fun onDestroy(){live=null;super.onDestroy()}
 override fun onNotificationPosted(sbn:StatusBarNotification) {
  if(sbn.packageName==packageName||SystemClock.elapsedRealtime()-last<30000)return
  last=SystemClock.elapsedRealtime()
  // Cheap package/category signal; private notification text is not implicitly acquired.
  BodyEvents.submit(applicationContext,"NOTIFICATION",sbn.packageName,"Notification posted; category ${sbn.notification.category?:"unspecified"}")
 }
}
