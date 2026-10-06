package com.mavyy.localyuki.scheduler

import android.app.*
import android.app.job.*
import android.content.*
import android.os.*
import com.mavyy.localyuki.brain.*
import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.embodiment.*
import java.util.concurrent.*

/** Coarse, reboot-persistent Android scheduler. Jobs resume app-owned state; no fragile polling lifetime. */
class PresenceJob:JobService() {
    private val worker=Executors.newSingleThreadExecutor();@Volatile private var stopped=false
    override fun onStartJob(params:JobParameters):Boolean {
        stopped=false;worker.execute {
            if(!stopped)ContinuityAccess.exclusive { BrainRuntime(applicationContext).use { brain->
                if(brain.open()&&!stopped) {
                    val policy=brain.life.policy()
                    if(policy is FoundationResult.Success&&policy.value.enabled){brain.executeDuePlans();brain.reflectAndInitiate()}
                }
            } };if(!stopped)jobFinished(params,false)
        };return true
    }
    override fun onStopJob(params:JobParameters):Boolean { stopped=true;return true }
    override fun onDestroy(){stopped=true;worker.shutdownNow();super.onDestroy()}
}
object PresenceScheduling {
    const val JOB=60019
    fun configure(context:Context,enabled:Boolean):Boolean {
        val scheduler=context.getSystemService(JobScheduler::class.java)
        if(!enabled){scheduler.cancel(JOB);return true}
        val info=JobInfo.Builder(JOB,ComponentName(context,PresenceJob::class.java)).setPeriodic(30*60000L).setRequiresBatteryNotLow(true).setPersisted(true).build()
        return scheduler.schedule(info)==JobScheduler.RESULT_SUCCESS
    }
    fun restore(context:Context) { ContinuityAccess.exclusive { BrainRuntime(context).use { b->if(b.open()){val p=b.life.policy();configure(context,p is FoundationResult.Success&&p.value.enabled)} } } }
}
class ResumeReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val pending=goAsync();Executors.newSingleThreadExecutor().apply { execute { try { PresenceScheduling.restore(context);BackgroundMaintenance.restore(context) }finally { pending.finish();shutdown() } } }
    }
}
object PresenceChannels {
    const val CHANNEL="yuki_presence"
    fun notify(context:Context,text:String):Boolean {
        val manager=context.getSystemService(NotificationManager::class.java)
        if(!manager.areNotificationsEnabled() || Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return false
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"Yuki check-ins",NotificationManager.IMPORTANCE_DEFAULT))
        val open=PendingIntent.getActivity(context,1,Intent(context,com.mavyy.localyuki.BootstrapActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(60019,Notification.Builder(context,CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Yuki").setContentText(text.take(240)).setStyle(Notification.BigTextStyle().bigText(text)).setContentIntent(open).setAutoCancel(true).build())
        return true
    }
}
