package com.mavyy.localyuki.scheduler

import android.content.Context
import com.mavyy.localyuki.brain.BrainDatabase
import com.mavyy.localyuki.brain.BrainDatabase.Conflict
import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.scheduler.*
import com.mavyy.localyuki.foundation.resource.Engagement
import java.time.Instant

class SchedulerStore(context: Context) : AutoCloseable {
    private val store=BrainDatabase(context)
    internal fun observe(signal: BackgroundSignal,decision: SalienceDecision,now: Instant,engagement: Engagement): FoundationResult<Boolean> = store.write { db ->
        val r=store.rows(db,"SELECT format,last_escalation,last_signal_id,events_seen FROM scheduler_state").singleOrNull() ?: throw Conflict()
        if(r[0]!="1" || decision.score !in 0..100 || decision.reason.length>160) throw Conflict()
        val count=r[3]?.toLongOrNull()?.takeIf { it in 0..2147483647 } ?: throw Conflict()
        if(r[2]==signal.id) return@write false
        val last=r[1]?.let(Instant::parse)
        val escalate=SchedulerPolicy.mayEscalate(signal,decision,last,now,engagement)
        db.execSQL("UPDATE scheduler_state SET last_escalation=?,last_signal_id=?,events_seen=? WHERE singleton=1",
            arrayOf<Any?>(if(escalate) now.toString() else r[1],signal.id,(count+1).coerceAtMost(2147483647)))
        escalate
    }
    override fun close()=store.close()
}
