package com.mavyy.localyuki.embodiment

import android.app.*
import android.app.usage.*
import android.content.*
import android.content.pm.PackageManager
import android.media.session.*
import android.os.*
import android.provider.*
import android.net.Uri
import android.speech.SpeechRecognizer
import android.view.*
import android.widget.TextView
import android.graphics.PixelFormat
import com.mavyy.localyuki.foundation.embodiment.*
import org.json.JSONObject

class AndroidBody(context:Context) {
    private val app=context.applicationContext
    val executors=setOf(CapabilityId.LOCAL_NOTE,CapabilityId.SCREEN,CapabilityId.NOTIFICATIONS,CapabilityId.SPEECH_OUTPUT,
        CapabilityId.APP_LAUNCH,CapabilityId.DEVICE_NAVIGATION,CapabilityId.UI_INTERACTION,CapabilityId.MEDIA_CONTROL,CapabilityId.DOCUMENTS,CapabilityId.COMPANION,CapabilityId.LOCKDOWN_CONTROL,CapabilityId.CONVERSATION_NOTIFY,CapabilityId.IMAGE_TEXT)
    fun grant(id:CapabilityId):Pair<Boolean,Boolean> = when(id) {
        CapabilityId.LOCAL_NOTE,CapabilityId.APP_LAUNCH -> true to true
        CapabilityId.SPEECH_OUTPUT -> true to LocalVoice.ready
        CapabilityId.SCREEN,CapabilityId.DEVICE_NAVIGATION,CapabilityId.UI_INTERACTION -> true to (YukiAccessibility.live!=null)
        CapabilityId.NOTIFICATIONS,CapabilityId.MEDIA_CONTROL -> true to (YukiNotifications.live!=null)
        CapabilityId.CONVERSATION_NOTIFY -> true to (app.getSystemService(NotificationManager::class.java).areNotificationsEnabled() && (Build.VERSION.SDK_INT<33 || app.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED))
        CapabilityId.IMAGE_TEXT -> true to true
        CapabilityId.COMPANION -> true to Settings.canDrawOverlays(app)
        CapabilityId.SPEECH_INPUT -> (Build.VERSION.SDK_INT>=31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(app)) to (app.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)
        CapabilityId.DOCUMENTS -> true to app.contentResolver.persistedUriPermissions.any { it.isReadPermission }
        CapabilityId.LOCKDOWN_CONTROL -> com.mavyy.localyuki.bridge.LockdownClient(app).available().let { true to it }
    }
    fun execute(intent:ActionIntent):String {
        if(intent.capability in setOf(CapabilityId.UI_INTERACTION,CapabilityId.DEVICE_NAVIGATION)) {
            val operation=JSONObject(intent.payload).getString("op")
            val operations=if(intent.capability==CapabilityId.DEVICE_NAVIGATION)setOf("home","back","recents","notifications") else setOf("click","setText","scrollForward","scrollBackward")
            require(operation in operations) { "Operation does not belong to granted capability" }
        }
        if(intent.capability==CapabilityId.UI_INTERACTION) {
            val blocked=setOf(app.packageName,"com.mavyy.yukilockdown","com.android.settings","com.android.packageinstaller","com.google.android.packageinstaller","com.android.permissioncontroller","com.google.android.permissioncontroller","com.samsung.android.permissioncontroller")
            val controller=app.packageManager.resolveActivity(Intent("android.intent.action.MANAGE_PERMISSIONS"),0)?.activityInfo?.packageName
            require(intent.targetPackage !in blocked && intent.targetPackage!=controller) { "Owner authority/setup surfaces require direct owner interaction or the authenticated app API" }
        }
        return when(intent.capability) {
        CapabilityId.IMAGE_TEXT -> { val j=JSONObject(intent.payload);require(j.keys().asSequence().all { it in setOf("op","uri") })
            when(j.getString("op")) { "file"->LocalImageText.file(app,Uri.parse(j.getString("uri")));"screenshot"->LocalImageText.screenshot(YukiAccessibility.live?:error("Accessibility unavailable"),requireNotNull(intent.targetPackage));else->error("Unsupported image text operation") } }
        CapabilityId.SPEECH_OUTPUT -> LocalVoice.speak(intent.payload)
        CapabilityId.SCREEN -> YukiAccessibility.live?.screen(requireNotNull(intent.targetPackage))?:error("Accessibility unavailable")
        CapabilityId.UI_INTERACTION,CapabilityId.DEVICE_NAVIGATION -> YukiAccessibility.live?.act(intent.targetPackage,intent.payload)?:error("Accessibility unavailable")
        CapabilityId.APP_LAUNCH -> {
            val pkg=requireNotNull(intent.targetPackage);require(JSONObject(intent.payload).getString("op")=="launch")
            val launch=app.packageManager.getLaunchIntentForPackage(pkg)?:error("No launchable app")
            app.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));"Launch intent dispatched for $pkg; foreground observation must confirm visibility"
        }
        CapabilityId.MEDIA_CONTROL -> {
            val j=JSONObject(intent.payload);val session=app.getSystemService(MediaSessionManager::class.java).getActiveSessions(ComponentName(app,YukiNotifications::class.java))
                .singleOrNull { it.packageName==intent.targetPackage }?:error("Unique active media session unavailable")
            when(j.getString("op")){"play"->session.transportControls.play();"pause"->session.transportControls.pause();"next"->session.transportControls.skipToNext();"previous"->session.transportControls.skipToPrevious();"stop"->session.transportControls.stop();else->error("Unsupported media command")}
            "Media command dispatched to ${session.packageName}; actual playback state is ${session.playbackState?.state}"
        }
        CapabilityId.NOTIFICATIONS -> { require(JSONObject(intent.payload).getString("op")=="inspect");val live=YukiNotifications.live?:error("Notification access unavailable")
            live.activeNotifications.filter { intent.targetPackage==null||it.packageName==intent.targetPackage }.take(8).joinToString("; ") { "${it.packageName}: ${it.notification.category?:"notification"}" }.ifEmpty { "No currently active matching notifications" }.take(480) }
        CapabilityId.DOCUMENTS -> {
            val j=JSONObject(intent.payload);require(j.getString("op")=="search");val query=j.getString("query").also { require(it.length in 1..80) }
            val trees=app.contentResolver.persistedUriPermissions.filter { it.isReadPermission };require(trees.isNotEmpty())
            val names=mutableListOf<String>();for(tree in trees.take(4)) {
                val uri=DocumentsContract.buildChildDocumentsUriUsingTree(tree.uri,DocumentsContract.getTreeDocumentId(tree.uri))
                app.contentResolver.query(uri,arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),null,null,null)?.use { c->var count=0;while(c.moveToNext()&&count++<100)if(c.getString(0).contains(query,true))names+=c.getString(0).take(80) }
            };names.take(5).joinToString("; ").ifEmpty { "No matching file in granted folder roots" }
        }
        CapabilityId.CONVERSATION_NOTIFY -> { check(com.mavyy.localyuki.scheduler.PresenceChannels.notify(app,intent.payload));"Conversation notification delivered to Android" }
        CapabilityId.COMPANION -> { check(CompanionSurface.show(app,intent.payload));"Companion message added to Android overlay" }
        CapabilityId.LOCKDOWN_CONTROL -> com.mavyy.localyuki.bridge.LockdownClient(app).execute(intent.payload)
        else -> error("Capability requires its dedicated voice or local-note path")
    }
    }
    fun usageForeground():String? {
        val ops=app.getSystemService(AppOpsManager::class.java)
        @Suppress("DEPRECATION") if(ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,Process.myUid(),app.packageName)!=AppOpsManager.MODE_ALLOWED)return null
        val now=System.currentTimeMillis();val events=app.getSystemService(UsageStatsManager::class.java).queryEvents(now-600000,now)
        val e=UsageEvents.Event();var pkg:String?=null
        while(events.hasNextEvent()){events.getNextEvent(e);if(e.eventType==UsageEvents.Event.ACTIVITY_RESUMED)pkg=e.packageName}
        return pkg
    }
}
object CompanionSurface {
 private var view:TextView?=null
 fun show(context:Context,text:String):Boolean {
  require(text.toByteArray().size<=512 && Settings.canDrawOverlays(context))
  check(Looper.myLooper()!=Looper.getMainLooper())
  val outcome=java.util.concurrent.CompletableFuture<Boolean>()
  Handler(Looper.getMainLooper()).post {
   val wm=context.getSystemService(WindowManager::class.java);view?.let { try { wm.removeView(it) }catch(_:Exception){} };view=null
   if(!Settings.canDrawOverlays(context)){outcome.complete(false);return@post}
   val label=TextView(context).apply { this.text="Yuki · $text";setPadding(24,20,24,20);setTextColor(android.graphics.Color.WHITE);setBackgroundColor(0xee152d35.toInt());setOnClickListener { wm.removeView(this);view=null } }
   try { wm.addView(label,WindowManager.LayoutParams(-2,-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT).apply { gravity=Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL;y=120 });view=label;outcome.complete(true)
      Handler(Looper.getMainLooper()).postDelayed({ if(view===label) { try{wm.removeView(label)}catch(_:Exception){};view=null } },15000)
   }catch(_:Exception){outcome.complete(false)}
  }
  return outcome.get(5,java.util.concurrent.TimeUnit.SECONDS)
 }
}
