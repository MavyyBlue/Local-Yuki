package com.mavyy.localyuki.recovery

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.mavyy.localyuki.brain.BrainDatabase
import com.mavyy.localyuki.brain.BrainDatabase.Conflict
import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.recovery.*
import com.mavyy.localyuki.foundation.state.*
import com.mavyy.localyuki.foundation.temporal.TemporalGroundingReader
import java.time.Instant

class RecoveryStore(context: Context,private val temporal: TemporalGroundingReader,private val state: YukiStateReader) : AutoCloseable {
    private val store=BrainDatabase(context)
    private fun snapshot(db: SQLiteDatabase): RecoverySnapshot {
        val r=store.rows(db,"SELECT format,revision,mode,updated_at,maintenance_active,opened FROM recovery_state").singleOrNull() ?: throw Conflict()
        if(r[0]!="1" || r[4] !in listOf("0","1") || r[5] !in listOf("0","1")) throw Conflict()
        val revision=r[1]?.toLongOrNull()?.takeIf { it>=0 } ?: throw Conflict()
        val mode=RecoveryMode.entries.find { it.name==r[2] } ?: throw Conflict()
        val intentions=store.rows(db,"SELECT item_id,content,created_at,updated_at,expires_at FROM recovery_intention ORDER BY item_id LIMIT 33").map {
            CurrentItem(it[0] ?: throw Conflict(),it[1] ?: throw Conflict(),Instant.parse(it[2]),Instant.parse(it[3]),it[4]?.let(Instant::parse))
        }
        if(intentions.size>32 || intentions.any { it.id.isBlank() || it.id.length>80 || it.text.isBlank() || it.text.length>512 }) throw Conflict()
        return RecoverySnapshot(revision,mode,intentions,Instant.parse(r[3]),r[4]=="1")
    }
    fun reader(): RecoveryReader=RecoveryReader { store.read(::snapshot) }
    fun open(): FoundationResult<RecoverySnapshot> = store.write { db ->
        val s=snapshot(db)
        if(s.maintenanceInterrupted || s.mode==RecoveryMode.PREPARING_SLEEP) {
            if(s.revision==Long.MAX_VALUE) throw Conflict()
            db.execSQL("UPDATE recovery_state SET mode='RECOVERING',maintenance_active=0,opened=1,revision=? WHERE singleton=1",arrayOf<Any?>(s.revision+1))
        } else db.execSQL("UPDATE recovery_state SET opened=1 WHERE singleton=1")
        snapshot(db)
    }
    internal fun transition(expectedRevision: Long,target: RecoveryMode): FoundationResult<RecoverySnapshot> {
        val time=temporal.ground();if(time !is FoundationResult.Success) return when (time) { is FoundationResult.Failure -> time;is FoundationResult.Unavailable -> time;else -> FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE) }
        val current=state.read();if(current !is FoundationResult.Success) return when (current) { is FoundationResult.Failure -> current;is FoundationResult.Unavailable -> current;else -> FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE) }
        return store.write { db ->
            val s=snapshot(db)
            if(s.revision!=expectedRevision || s.revision==Long.MAX_VALUE || !RecoveryPolicy.permits(s.mode,target) || s.maintenanceInterrupted) throw Conflict()
            if(target==RecoveryMode.PREPARING_SLEEP) {
                // State authority is read before our lock and checked again through its durable revision.
                val head=store.rows(db,"SELECT revision FROM yuki_state").singleOrNull()?.get(0)?.toLongOrNull()
                if(head!=current.value.revision) throw Conflict()
                db.execSQL("DELETE FROM recovery_intention")
                current.value.intentions.forEach { db.execSQL("INSERT INTO recovery_intention VALUES (?,?,?,?,?)",arrayOf<Any?>(it.id,it.text,it.createdAt.toString(),it.updatedAt.toString(),it.expiresAt?.toString())) }
            }
            db.execSQL("UPDATE recovery_state SET mode=?,revision=?,updated_at=? WHERE singleton=1",arrayOf<Any?>(target.name,s.revision+1,maxOf(time.value.instant,s.updatedAt).toString()))
            snapshot(db)
        }
    }
    /** Hooks run outside the checkpoint transaction. Their individual writes remain atomic. */
    internal fun maintain(hooks: List<() -> FoundationResult<*>>): FoundationResult<Boolean> {
        if(hooks.size>4) return FoundationResult.Failure(FailureCategory.INVALID_INPUT)
        val claimed=store.write { db -> val s=snapshot(db)
            if(s.mode!=RecoveryMode.SLEEPING || s.maintenanceInterrupted) throw Conflict()
            db.execSQL("UPDATE recovery_state SET maintenance_active=1 WHERE singleton=1");true }
        if(claimed !is FoundationResult.Success) return claimed
        var success=true
        try { for(hook in hooks) { if(hook() !is FoundationResult.Success) { success=false;break } } } catch (_: Exception) { success=false }
        val completed=store.write { db ->snapshot(db);db.execSQL("UPDATE recovery_state SET maintenance_active=0 WHERE singleton=1");true }
        return if(completed !is FoundationResult.Success) completed else FoundationResult.Success(success)
    }
    internal fun maintenanceCursor(): FoundationResult<String> = store.read { db ->
        snapshot(db)
        val value=store.rows(db,"SELECT maintenance_cursor FROM recovery_state").singleOrNull()?.get(0) ?: throw Conflict()
        if(value.isNotEmpty() && !com.mavyy.localyuki.foundation.memory.MemoryBounds.id(value)) throw Conflict()
        value
    }
    internal fun advanceMaintenanceCursor(expected: String,next: String): FoundationResult<Boolean> = store.write { db ->
        val s=snapshot(db)
        if(s.mode!=RecoveryMode.SLEEPING || !s.maintenanceInterrupted ||
            next.isNotEmpty() && !com.mavyy.localyuki.foundation.memory.MemoryBounds.id(next)) throw Conflict()
        val prior=store.rows(db,"SELECT maintenance_cursor FROM recovery_state").singleOrNull()?.get(0)
        if(prior!=expected) throw Conflict()
        db.execSQL("UPDATE recovery_state SET maintenance_cursor=? WHERE singleton=1",arrayOf(next));true
    }
    override fun close()=store.close()
}
