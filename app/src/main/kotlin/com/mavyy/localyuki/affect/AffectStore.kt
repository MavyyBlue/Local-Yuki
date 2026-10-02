package com.mavyy.localyuki.affect

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.mavyy.localyuki.brain.BrainDatabase
import com.mavyy.localyuki.brain.BrainDatabase.Conflict
import com.mavyy.localyuki.foundation.affect.*
import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.memory.*
import com.mavyy.localyuki.foundation.temporal.TemporalGroundingReader
import java.time.Instant
import java.security.MessageDigest

class AffectStore(context: Context, private val temporal: TemporalGroundingReader, private val memory: MemoryReader) : AutoCloseable {
    private val store=BrainDatabase(context)
    private fun snapshot(db: SQLiteDatabase): AffectSnapshot {
        val row=store.rows(db,"SELECT format,revision,valence,arousal,affiliation,updated_at,opened FROM affect_state").singleOrNull() ?: throw Conflict()
        if (row[0]!="1" || row[6] !in listOf("0","1")) throw Conflict()
        val revision=row[1]?.toLongOrNull()?.takeIf { it>=0 } ?: throw Conflict()
        val vector=try { AffectVector(row[2]!!.toInt(),row[3]!!.toInt(),row[4]!!.toInt()) } catch (_: Exception) { throw Conflict() }
        return AffectSnapshot(revision,vector,Instant.parse(row[5]))
    }
    fun open(): FoundationResult<Boolean> = store.write { db ->
        snapshot(db);val restored=store.rows(db,"SELECT opened FROM affect_state").single()[0]=="1"
        db.execSQL("UPDATE affect_state SET opened=1 WHERE singleton=1");restored
    }
    fun reader(): AffectReader = AffectReader { read() }
    fun read(): FoundationResult<AffectSnapshot> = store.read(::snapshot)
    fun associations(limit: Int=50): FoundationResult<List<AffectiveAssociation>> = store.read { db ->
        require(limit in 1..50);snapshot(db)
        store.rows(db,"SELECT memory_id,strength,reinforcement_count FROM affect_association ORDER BY memory_id LIMIT ?",limit.toString()).map {
            val id=it[0] ?: throw Conflict();val strength=it[1]?.toIntOrNull() ?: throw Conflict();val count=it[2]?.toLongOrNull() ?: throw Conflict()
            if (!MemoryBounds.id(id) || strength !in -100..100 || count<1) throw Conflict()
            AffectiveAssociation(id,strength,count)
        }
    }
    internal fun apply(event: AffectEvent): FoundationResult<Long> {
        if (!MemoryBounds.id(event.id) || event.expectedRevision<0 || event.memoryId?.let { !MemoryBounds.id(it) }==true)
            return FoundationResult.Failure(FailureCategory.INVALID_INPUT)
        val text=listOf(event.expectedRevision.toString(),event.kind.name,event.evidence.opaqueId,event.evidence.sourceKind.name,event.memoryId ?: "").joinToString("\u0000")
        val fingerprint=MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
        val replay=store.read { db -> snapshot(db);store.rows(db,"SELECT fingerprint,resulting_revision FROM affect_event WHERE event_id=?",event.id).singleOrNull() ?: emptyList() }
        if (replay !is FoundationResult.Success) return when (replay) { is FoundationResult.Failure -> replay;is FoundationResult.Unavailable -> replay;else -> FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE) }
        if (replay.value.isNotEmpty()) return if (replay.value[0]==fingerprint)
            FoundationResult.Success(replay.value[1]!!.toLong()) else FoundationResult.Failure(FailureCategory.CONFLICT)
        when (val source=memory.evidence(event.evidence)) { is FoundationResult.Success -> Unit;is FoundationResult.Failure -> return source;is FoundationResult.Unavailable -> return source }
        var sourceRevision: String?=null
        event.memoryId?.let { id -> when (val m=memory.current(id)) {
            is FoundationResult.Success -> {
                if (event.evidence !in m.value.current.evidence) return FoundationResult.Failure(FailureCategory.REJECTED)
                sourceRevision=m.value.current.id
            }
            is FoundationResult.Failure -> return m
            is FoundationResult.Unavailable -> return m
        } }
        val time=temporal.ground();if (time !is FoundationResult.Success) return when (time) { is FoundationResult.Failure -> time;is FoundationResult.Unavailable -> time;else -> FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE) }
        return store.write { db ->
            val s=snapshot(db)
            val committed=store.rows(db,"SELECT fingerprint,resulting_revision FROM affect_event WHERE event_id=?",event.id).singleOrNull()
            if (committed!=null) { if (committed[0]!=fingerprint) throw Conflict();return@write committed[1]!!.toLong() }
            if (s.revision!=event.expectedRevision || s.revision==Long.MAX_VALUE) throw Conflict()
            val now=maxOf(time.value.instant,s.updatedAt)
            val vector=AffectPolicy.apply(AffectPolicy.normalize(s.vector,s.updatedAt,now),event.kind)
            event.memoryId?.let { id ->
                if(store.rows(db,"SELECT current_revision FROM durable_memory WHERE memory_id=?",id).singleOrNull()?.get(0)!=sourceRevision) throw Conflict()
                val prior=store.rows(db,"SELECT strength,reinforcement_count FROM affect_association WHERE memory_id=?",id).singleOrNull()
                if (prior==null && store.rows(db,"SELECT COUNT(*) FROM affect_association").single()[0]!!.toInt()>=AffectPolicy.MAX_ASSOCIATIONS) throw Conflict()
                val delta=when(event.kind) { AffectEventKind.POSITIVE -> 10;AffectEventKind.NEGATIVE -> -10;else -> 0 }
                val strength=((prior?.get(0)?.toInt() ?: 0)+delta).coerceIn(-100,100)
                val count=(prior?.get(1)?.toLong() ?: 0).let { if (it==Long.MAX_VALUE) throw Conflict();it+1 }
                db.execSQL("INSERT OR REPLACE INTO affect_association VALUES (?,?,?)",arrayOf<Any?>(id,strength,count))
            }
            db.execSQL("UPDATE affect_state SET revision=?,valence=?,arousal=?,affiliation=?,updated_at=? WHERE singleton=1",arrayOf<Any?>(s.revision+1,vector.valence,vector.arousal,vector.affiliation,now.toString()))
            db.execSQL("INSERT INTO affect_event VALUES (?,?,?,?,?,?,?,?)",arrayOf<Any?>(event.id,fingerprint,s.revision+1,event.kind.name,event.evidence.opaqueId,event.memoryId,sourceRevision,now.toString()));s.revision+1
        }
    }
    internal fun normalize(): FoundationResult<Long> {
        val time=temporal.ground();if (time !is FoundationResult.Success) return when (time) { is FoundationResult.Failure -> time;is FoundationResult.Unavailable -> time;else -> FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE) }
        return store.write { db ->
            val s=snapshot(db);val now=maxOf(time.value.instant,s.updatedAt)
            val vector=AffectPolicy.normalize(s.vector,s.updatedAt,now)
            // Preserve fractional decay time; frequent calls must not prevent normalization.
            if (vector==s.vector) return@write s.revision
            if (s.revision==Long.MAX_VALUE) throw Conflict()
            db.execSQL("UPDATE affect_state SET revision=?,valence=?,arousal=?,affiliation=?,updated_at=? WHERE singleton=1",arrayOf<Any?>(s.revision+1,vector.valence,vector.arousal,vector.affiliation,now.toString()));s.revision+1
        }
    }
    override fun close()=store.close()
}
