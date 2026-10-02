package com.mavyy.localyuki.memory

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.mavyy.localyuki.continuity.ContinuityHelper
import com.mavyy.localyuki.continuity.ContinuitySchema
import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.memory.*
import com.mavyy.localyuki.foundation.temporal.TemporalGroundingReader
import java.security.MessageDigest
import java.time.Instant

/** Trusted derived-state adapter. All factual content is resolved through the Phase 4 reader. */
class LivingMemoryStore(context: Context, private val temporal: TemporalGroundingReader,
    private val deep: MemoryReader) : AutoCloseable {
    private val app = context.applicationContext
    private val helper = ContinuityHelper(app, !app.getDatabasePath(ContinuitySchema.NAME).exists())
    enum class Start { INITIALIZED, RESTORED, UNAVAILABLE }
    private class Conflict : RuntimeException()
    private var opened = false
    private fun rows(db: SQLiteDatabase, sql: String, vararg args: String): List<List<String?>> =
        db.rawQuery(sql, args).use { c -> buildList { while (c.moveToNext()) add((0 until c.columnCount).map(c::getString)) } }
    private fun metadata(db: SQLiteDatabase): Boolean {
        val result = rows(db,"SELECT format_version,opened,coverage_complete,coverage_cursor FROM living_memory_metadata")
        if (result.size != 1 || result[0][0] != "1" || result[0][1] !in listOf("0","1") ||
            result[0][2] !in listOf("0","1") || result[0][3] == null ||
            result[0][3]!!.isNotEmpty() && !MemoryBounds.id(result[0][3]!!)) throw Conflict()
        return result[0][1] == "1"
    }
    private fun <T : Any> read(block: (SQLiteDatabase) -> T): FoundationResult<T> = try {
        val db = helper.readableDatabase; metadata(db); FoundationResult.Success(block(db))
    } catch (_: Conflict) { FoundationResult.Failure(FailureCategory.CONFLICT) }
      catch (_: IllegalArgumentException) { FoundationResult.Failure(FailureCategory.INVALID_INPUT) }
      catch (_: java.time.DateTimeException) { FoundationResult.Failure(FailureCategory.CONFLICT) }
      catch (_: SQLiteException) { FoundationResult.Failure(FailureCategory.CONFLICT) }
      catch (_: Exception) { FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE) }
    private fun <T : Any> write(block: (SQLiteDatabase) -> T): FoundationResult<T> = try {
        val db = helper.writableDatabase; db.beginTransaction()
        try { metadata(db); val value = block(db); db.setTransactionSuccessful(); FoundationResult.Success(value) }
        finally { db.endTransaction() }
    } catch (_: Conflict) { FoundationResult.Failure(FailureCategory.CONFLICT) }
      catch (_: IllegalArgumentException) { FoundationResult.Failure(FailureCategory.INVALID_INPUT) }
      catch (_: java.time.DateTimeException) { FoundationResult.Failure(FailureCategory.CONFLICT) }
      catch (_: SQLiteException) { FoundationResult.Failure(FailureCategory.CONFLICT) }
      catch (_: Exception) { FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE) }
    private fun now(): FoundationResult<Instant> = try {
        when (val stamp = temporal.ground()) {
            is FoundationResult.Success -> FoundationResult.Success(stamp.value.instant)
            is FoundationResult.Unavailable -> stamp
            is FoundationResult.Failure -> stamp
        }
    } catch (_: Exception) { FoundationResult.Unavailable(UnavailableReason.DEPENDENCY_UNAVAILABLE) }
    private fun <T : Any> timed(block: (SQLiteDatabase, Instant) -> T): FoundationResult<T> =
        when (val time = now()) {
            is FoundationResult.Success -> write { block(it, time.value) }
            is FoundationResult.Unavailable -> time
            is FoundationResult.Failure -> time
        }
    fun open(): Start {
        val result = write { db ->
            val prior = metadata(db)
            if (!prior) db.execSQL("UPDATE living_memory_metadata SET opened=1 WHERE singleton=1")
            if (opened || prior) Start.RESTORED else Start.INITIALIZED
        }
        if (result is FoundationResult.Success) { opened = true; return result.value }
        return Start.UNAVAILABLE
    }
    private fun state(db: SQLiteDatabase, id: String): LivingMemoryView? {
        val r = rows(db,"SELECT memory_id,source_revision_id,state_revision,abstraction_level,importance,reinforcement,recall_count,last_recalled_at,reconciled_at FROM living_memory_state WHERE memory_id=?",id).singleOrNull() ?: return null
        val level = AbstractionLevel.entries.find { it.name == r[3] } ?: throw Conflict()
        val importance = r[4]?.toIntOrNull() ?: throw Conflict()
        val reinforcement = r[5]?.toIntOrNull() ?: throw Conflict()
        val revision = r[2]?.toLongOrNull() ?: throw Conflict()
        val count = r[6]?.toLongOrNull() ?: throw Conflict()
        if (importance !in 0..100 || reinforcement !in 0..100 || revision < 1 || count !in 0..2147483647 ||
            !MemoryBounds.id(r[0] ?: "") || !MemoryBounds.id(r[1] ?: "") ||
            (count == 0L) != (r[7] == null)) throw Conflict()
        val terms = rows(db,"SELECT term,source_revision_id,rank FROM living_memory_term WHERE memory_id=? ORDER BY rank",id)
        if (terms.size > LivingMemoryPolicyV1.budget(level) || terms.withIndex().any { (index, term) ->
            term[1] != r[1] || term[2]?.toIntOrNull() != index || !LivingMemoryPolicyV1.validTerm(term[0] ?: "") }) throw Conflict()
        val source = rows(db,"SELECT content FROM memory_revision WHERE revision_id=? AND memory_id=?",
            r[1] ?: throw Conflict(),id).singleOrNull()?.get(0) ?: throw Conflict()
        if (!MemoryBounds.text(source,MemoryBounds.MAX_CONTENT_BYTES) ||
            terms.map { it[0] } != LivingMemoryPolicyV1.terms(source,LivingMemoryPolicyV1.budget(level))) throw Conflict()
        return LivingMemoryView(r[0] ?: throw Conflict(),r[1] ?: throw Conflict(),revision,level,
            terms.map { it[0] ?: throw Conflict() },importance,reinforcement,count,
            r[7]?.let { Instant.parse(it) },Instant.parse(r[8] ?: throw Conflict()))
    }
    fun reader(): LivingMemoryReader = object : LivingMemoryReader {
        override fun current(memoryId: String) = this@LivingMemoryStore.current(memoryId)
        override fun episode(memoryId: String, revisionId: String) = this@LivingMemoryStore.episode(memoryId,revisionId)
    }
    fun coverage(): FoundationResult<IndexCoverage> = read { db ->
        if (rows(db,"SELECT coverage_complete FROM living_memory_metadata").single()[0] == "1") IndexCoverage.COMPLETE else IndexCoverage.PARTIAL
    }
    fun current(memoryId: String): FoundationResult<LivingMemoryView> {
        if (!MemoryBounds.id(memoryId)) return FoundationResult.Failure(FailureCategory.INVALID_INPUT)
        val memory = deep.current(memoryId)
        if (memory !is FoundationResult.Success) return when (memory) {
            is FoundationResult.Failure -> memory
            is FoundationResult.Unavailable -> memory
            else -> FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE)
        }
        return read { db ->
            val s = state(db,memoryId) ?: throw Conflict()
            val head = rows(db,"SELECT current_revision FROM durable_memory WHERE memory_id=?",memoryId).singleOrNull()?.get(0)
            if (head != s.sourceRevisionId || head != memory.value.current.id) throw Conflict()
            s
        }
    }
    fun episode(memoryId: String, revisionId: String): FoundationResult<EpisodicMemoryView> {
        if (!MemoryBounds.id(memoryId) || !MemoryBounds.id(revisionId)) return FoundationResult.Failure(FailureCategory.INVALID_INPUT)
        // Read a bounded page targeted by the revision number, including histories longer than one page.
        val number = read { db -> rows(db,"SELECT revision_number FROM memory_revision WHERE revision_id=? AND memory_id=?",revisionId,memoryId).singleOrNull()?.get(0)?.toLongOrNull() ?: throw Conflict() }
        if (number !is FoundationResult.Success) return when (number) {
            is FoundationResult.Failure -> number
            is FoundationResult.Unavailable -> number
            else -> FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE)
        }
        val one = deep.history(memoryId,number.value-1,1)
        if (one !is FoundationResult.Success) return when (one) {
            is FoundationResult.Failure -> one
            is FoundationResult.Unavailable -> one
            else -> FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE)
        }
        val revision = one.value.singleOrNull()?.takeIf { it.id == revisionId } ?: return FoundationResult.Failure(FailureCategory.CONFLICT)
        val current = deep.current(memoryId)
        if (current !is FoundationResult.Success) return when (current) {
            is FoundationResult.Failure -> current
            is FoundationResult.Unavailable -> current
            else -> FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE)
        }
        return FoundationResult.Success(EpisodicMemoryView(memoryId,revisionId,current.value.kind,
            if (current.value.current.id == revision.id) MemoryStatus.CURRENT else MemoryStatus.HISTORICAL,
            revision.createdAt,LivingMemoryPolicyV1.terms(revision.content,48),revision.evidence))
    }
    /** A page advances by memory ID. The caller supplies its own cursor; nothing scans at startup. */
    fun reconcilePage(afterMemoryId: String?, limit: Int): FoundationResult<List<LivingMemoryView>> {
        if (limit !in 1..LivingMemoryPolicyV1.MAX_RECONCILE_PAGE || afterMemoryId != null && !MemoryBounds.id(afterMemoryId))
            return FoundationResult.Failure(FailureCategory.INVALID_INPUT)
        val time = now()
        if (time !is FoundationResult.Success) return when (time) {
            is FoundationResult.Failure -> time
            is FoundationResult.Unavailable -> time
            else -> FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE)
        }
        val page = deep.currentPage(afterMemoryId,limit)
        if (page !is FoundationResult.Success) return when (page) {
            is FoundationResult.Failure -> page
            is FoundationResult.Unavailable -> page
            else -> FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE)
        }
        return write { db ->
            val ids = rows(db,"SELECT memory_id FROM durable_memory WHERE memory_id>? ORDER BY memory_id LIMIT ?",
                afterMemoryId ?: "",limit.toString()).map { it[0] }
            if (ids != page.value.map { it.id }) throw Conflict()
            val views = page.value.map { m -> reconcile(db,m,time.value) }
            val cursor = rows(db,"SELECT coverage_cursor FROM living_memory_metadata").single()[0]
            if (cursor == (afterMemoryId ?: "")) {
                db.execSQL("UPDATE living_memory_metadata SET coverage_cursor=?,coverage_complete=? WHERE singleton=1",
                    arrayOf<Any?>(page.value.lastOrNull()?.id ?: cursor,if (page.value.size < limit) 1 else 0))
            }
            views
        }
    }
    private fun reconcile(db: SQLiteDatabase, memory: DurableMemory, time: Instant): LivingMemoryView {
        val id = memory.id
        val head = rows(db,"SELECT current_revision FROM durable_memory WHERE memory_id=?",id).singleOrNull()?.get(0)
        if (head != memory.current.id) throw Conflict()
        val prior = state(db,id)
        val importance = prior?.importance ?: 50
        val sameSource = prior?.sourceRevisionId == head
        val reinforcement = if (sameSource) prior?.reinforcement ?: 0 else 0
        val level = LivingMemoryPolicyV1.level(memory.current.createdAt,time,importance,reinforcement)
        val terms = LivingMemoryPolicyV1.terms(memory.current.content,LivingMemoryPolicyV1.budget(level))
        if (prior != null && prior.sourceRevisionId == head && prior.abstraction == level && prior.terms == terms) return prior
        val revision = nextRevision(prior?.stateRevision ?: 0)
        if (prior == null) db.execSQL("INSERT INTO living_memory_state VALUES (?,?,?,?,?,?,?,?,?)",arrayOf<Any?>(id,head,revision,level.name,importance,reinforcement,0,null,time.toString()))
        else db.execSQL("UPDATE living_memory_state SET source_revision_id=?,state_revision=?,abstraction_level=?,reinforcement=?,recall_count=?,last_recalled_at=?,reconciled_at=? WHERE memory_id=? AND state_revision=?",
            arrayOf<Any?>(head,revision,level.name,reinforcement,if (sameSource) prior.meaningfulRecallCount else 0,
                if (sameSource) prior.lastMeaningfulRecallAt?.toString() else null,time.toString(),id,prior.stateRevision))
        db.execSQL("DELETE FROM living_memory_term WHERE memory_id=?",arrayOf<Any?>(id))
        terms.forEachIndexed { rank, term -> db.execSQL("INSERT INTO living_memory_term VALUES (?,?,?,?)",arrayOf<Any?>(id,head,term,rank)) }
        return state(db,id) ?: throw Conflict()
    }
    private fun nextRevision(prior: Long): Long {
        if (prior == Long.MAX_VALUE) throw Conflict()
        return prior + 1
    }
    /** Refresh metadata and its lexical projection together, in the caller's transaction. */
    private fun refreshProjection(db: SQLiteDatabase, prior: LivingMemoryView, memory: DurableMemory, time: Instant,
        importance: Int, reinforcement: Int, count: Long, lastRecall: Instant?): LivingMemoryView {
        if (memory.id != prior.memoryId || memory.current.id != prior.sourceRevisionId ||
            rows(db,"SELECT current_revision FROM durable_memory WHERE memory_id=?",prior.memoryId).singleOrNull()?.get(0) != memory.current.id) throw Conflict()
        val level = LivingMemoryPolicyV1.level(memory.current.createdAt,time,importance,reinforcement)
        val terms = LivingMemoryPolicyV1.terms(memory.current.content,LivingMemoryPolicyV1.budget(level))
        db.execSQL("UPDATE living_memory_state SET state_revision=?,abstraction_level=?,importance=?,reinforcement=?,recall_count=?,last_recalled_at=?,reconciled_at=? WHERE memory_id=? AND state_revision=?",
            arrayOf<Any?>(nextRevision(prior.stateRevision),level.name,importance,reinforcement,count,lastRecall?.toString(),time.toString(),prior.memoryId,prior.stateRevision))
        db.execSQL("DELETE FROM living_memory_term WHERE memory_id=?",arrayOf<Any?>(prior.memoryId))
        terms.forEachIndexed { rank, term -> db.execSQL("INSERT INTO living_memory_term VALUES (?,?,?,?)",
            arrayOf<Any?>(prior.memoryId,prior.sourceRevisionId,term,rank)) }
        return state(db,prior.memoryId) ?: throw Conflict()
    }
    /** Importance is trusted app-owned salience, not a semantic engine's confidence. */
    internal fun setImportance(memoryId: String, expectedStateRevision: Long, importance: Int): FoundationResult<LivingMemoryView> {
        if (!MemoryBounds.id(memoryId) || importance !in 0..100 || expectedStateRevision < 1)
            return FoundationResult.Failure(FailureCategory.INVALID_INPUT)
        // Resolve before taking this helper's exclusive write lock. Recheck the head inside it.
        val memory = deep.current(memoryId)
        if (memory !is FoundationResult.Success) return when (memory) {
            is FoundationResult.Failure -> memory
            is FoundationResult.Unavailable -> memory
            else -> FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE)
        }
        return timed { db,time ->
            val prior = state(db,memoryId) ?: throw Conflict()
            if (prior.stateRevision != expectedStateRevision) throw Conflict()
            refreshProjection(db,prior,memory.value,time,importance,prior.reinforcement,prior.meaningfulRecallCount,prior.lastMeaningfulRecallAt)
        }
    }
    /** Caller decides that grounded content was consumed; search itself never calls this. */
    internal fun recordMeaningfulRecall(eventId: String, memoryId: String, sourceRevisionId: String,
        expectedStateRevision: Long): FoundationResult<Long> {
        if (!MemoryBounds.id(eventId) || !MemoryBounds.id(memoryId) || !MemoryBounds.id(sourceRevisionId) || expectedStateRevision < 1)
            return FoundationResult.Failure(FailureCategory.INVALID_INPUT)
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(listOf(memoryId,sourceRevisionId,expectedStateRevision.toString()).joinToString("\u0000").toByteArray()).joinToString("") { "%02x".format(it) }
        // Replay does not need a new timestamp.
        val replay = read { db -> rows(db,"SELECT memory_id,source_revision_id,fingerprint,resulting_state_revision FROM living_recall_event WHERE event_id=?",eventId).singleOrNull() ?: emptyList() }
        if (replay !is FoundationResult.Success) return when (replay) {
            is FoundationResult.Failure -> replay
            is FoundationResult.Unavailable -> replay
            else -> FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE)
        }
        if (replay.value.isNotEmpty()) {
            val r = replay.value
            if (r[0] != memoryId || r[1] != sourceRevisionId || r[2] != fingerprint) return FoundationResult.Failure(FailureCategory.CONFLICT)
            val next = r[3]?.toLongOrNull() ?: return FoundationResult.Failure(FailureCategory.CONFLICT)
            if (expectedStateRevision == Long.MAX_VALUE || next != expectedStateRevision + 1)
                return FoundationResult.Failure(FailureCategory.CONFLICT)
            return FoundationResult.Success(next)
        }
        val memory = deep.current(memoryId)
        if (memory !is FoundationResult.Success) return when (memory) {
            is FoundationResult.Failure -> memory
            is FoundationResult.Unavailable -> memory
            else -> FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE)
        }
        return timed { db,time ->
            // Another caller may have committed between the optimistic replay read and this transaction.
            val committed = rows(db,"SELECT fingerprint,resulting_state_revision FROM living_recall_event WHERE event_id=?",eventId).singleOrNull()
            if (committed != null) {
                if (committed[0] != fingerprint || expectedStateRevision == Long.MAX_VALUE ||
                    committed[1]?.toLongOrNull() != expectedStateRevision + 1) throw Conflict()
                return@timed expectedStateRevision + 1
            }
            val s = state(db,memoryId) ?: throw Conflict()
            if (s.sourceRevisionId != sourceRevisionId || s.stateRevision != expectedStateRevision ||
                rows(db,"SELECT current_revision FROM durable_memory WHERE memory_id=?",memoryId).singleOrNull()?.get(0) != sourceRevisionId) throw Conflict()
            val next = nextRevision(expectedStateRevision)
            val lastRecall = s.lastMeaningfulRecallAt?.let { if (it > time) it else time } ?: time
            refreshProjection(db,s,memory.value,time,s.importance,(s.reinforcement+10).coerceAtMost(100),
                (s.meaningfulRecallCount+1).coerceAtMost(2147483647),lastRecall)
            db.execSQL("INSERT INTO living_recall_event VALUES (?,?,?,?,?,?,?)",arrayOf<Any?>(eventId,memoryId,sourceRevisionId,time.toString(),expectedStateRevision,next,fingerprint))
            next
        }
    }
    override fun close() = helper.close()
}
