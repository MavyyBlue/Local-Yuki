package com.mavyy.localyuki.scheduler

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.mavyy.localyuki.brain.*
import com.mavyy.localyuki.foundation.contracts.FoundationResult
import com.mavyy.localyuki.foundation.recovery.RecoveryMode
import java.util.concurrent.Executors

/** Coarse owner-enabled maintenance only. No wake word, polling or resident neural cognition. */
class MaintenanceJob : JobService() {
    private val executor=Executors.newSingleThreadExecutor()
    @Volatile private var stopped=false
    override fun onStartJob(params: JobParameters): Boolean {
        stopped=false
        executor.execute {
            if(!stopped) ContinuityAccess.exclusive {
                BrainRuntime(applicationContext).use { brain ->
                    if(!brain.open() || !BackgroundMaintenance.enabled(applicationContext)) return@use
                    val body=brain.refreshBody(false)
                    if(body !is FoundationResult.Success || body.value.engagement==com.mavyy.localyuki.foundation.resource.Engagement.RECOVERY) return@use
                    val state=brain.state.reader().read()
                    if(state is FoundationResult.Success) {
                        val now=java.time.Instant.now()
                        val last=state.value.interactions.find { it.kind==com.mavyy.localyuki.foundation.state.InteractionKind.USER_INPUT }?.instant
                        val idle=last?.let { java.time.Duration.between(it,now).seconds.coerceIn(0,86400) } ?: 0
                        brain.backgroundSignal(com.mavyy.localyuki.foundation.scheduler.BackgroundSignal(
                            "job-${java.util.UUID.randomUUID()}",com.mavyy.localyuki.foundation.scheduler.SignalKind.SCHEDULED,now,idle,state.value.intentions.size))
                    }
                    val initial=brain.recovery.reader().read()
                    if(initial !is FoundationResult.Success) return@use
                    val wasAwake=initial.value.mode in setOf(RecoveryMode.AWAKE,RecoveryMode.FATIGUED)
                    if(wasAwake && brain.sleep() !is FoundationResult.Success) return@use
                    if(!stopped) brain.maintenance()
                    if(wasAwake) brain.wake()
                }
            }
            if(!stopped) jobFinished(params,false)
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { stopped=true;return false }
    override fun onDestroy() { stopped=true;executor.shutdown();super.onDestroy() }
}
object BackgroundMaintenance {
    const val JOB_ID=60010
    private const val KEY="background_maintenance"
    // Owner setting is durable and separate from platform permission facts.
    fun enabled(context: Context)=context.getSharedPreferences("owner-controls",Context.MODE_PRIVATE).getBoolean(KEY,false)
    fun configure(context: Context,enabled: Boolean): Boolean {
        val scheduler=context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        if(!enabled) {
            scheduler.cancel(JOB_ID)
            return context.getSharedPreferences("owner-controls",Context.MODE_PRIVATE).edit().putBoolean(KEY,false).commit()
        }
        val job=JobInfo.Builder(JOB_ID,ComponentName(context,MaintenanceJob::class.java))
            .setPeriodic(15*60*1000L).setRequiresBatteryNotLow(true).setRequiresCharging(true).build()
        if(scheduler.schedule(job)!=JobScheduler.RESULT_SUCCESS) return false
        return context.getSharedPreferences("owner-controls",Context.MODE_PRIVATE).edit().putBoolean(KEY,true).commit()
    }
    fun restore(context: Context) { if(enabled(context)) configure(context,true) }
}
