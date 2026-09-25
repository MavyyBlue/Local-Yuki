package com.mavyy.localyuki.state

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.mavyy.localyuki.continuity.ContinuityHelper
import com.mavyy.localyuki.continuity.ContinuitySchema
import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.state.*
import com.mavyy.localyuki.foundation.temporal.*
import java.time.Instant
import java.time.ZoneId

/** The sole app-owned Yuki State mutator. No advisory or cognition-facing reader can reach these methods. */
class YukiStateStore(context: Context, private val temporal: TemporalGroundingReader) : AutoCloseable {
    private val app = context.applicationContext
    private val helper = ContinuityHelper(app, !app.getDatabasePath(ContinuitySchema.NAME).exists())
    private var opened = false

    enum class Start { INITIALIZED, RESTORED, UNAVAILABLE }
    data class Open(val status: Start, val reader: YukiStateReader)

    fun open(): Open {
        val first = !opened
        val result = transact { db, now ->
            val (state, initial) = readAndReconcile(db, now)
            if (first && initial) db.execSQL("UPDATE yuki_state SET opened = 1 WHERE singleton = 1")
            state to initial
        }
        if (result is FoundationResult.Success) {
            opened = true
            return Open(if (first && result.value.second) Start.INITIALIZED else Start.RESTORED, reader())
        }
        return Open(Start.UNAVAILABLE, YukiStateReader { when (result) {
            is FoundationResult.Failure -> result
            is FoundationResult.Unavailable -> result
            else -> FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE)
        } })
    }

    fun reader(): YukiStateReader = YukiStateReader { transact { db, now -> readAndReconcile(db, now).first } }

    fun mutate(command: StateCommand): FoundationResult<YukiStateSnapshot> = transact { db, now ->
        val current = readAndReconcile(db, now).first
        if (current.revision != command.expectedRevision) throw StateConflict()
        val nextRevision = Math.addExact(current.revision, 1)
        val next = when (val change = command.change) {
            is StateChange.Project -> current.copy(project = change.value)
            is StateChange.Focus -> {
                require(change.value?.expiresAt == null || change.value.expiresAt.isAfter(now))
                current.copy(focus = change.value)
            }
            is StateChange.PutIntention -> current.copy(intentions = put(current.intentions, change.id, change.text, change.expiresAt, now))
            is StateChange.RemoveIntention -> current.copy(intentions = remove(current.intentions, change.id))
            is StateChange.PutTopic -> current.copy(topics = put(current.topics, change.id, change.text, change.expiresAt, now))
            is StateChange.RemoveTopic -> current.copy(topics = remove(current.topics, change.id))
        }.copy(revision = nextRevision, updatedAt = now)
        require(YukiStateValidation.valid(next))
        persist(db, next)
        next
    }

    /** Called only by trusted app interaction plumbing at the actual event; no timestamp parameter. */
    fun recordInteraction(kind: InteractionKind, expectedRevision: Long): FoundationResult<YukiStateSnapshot> {
        val grounded = temporal.ground()
        if (grounded !is FoundationResult.Success) return when (grounded) {
            is FoundationResult.Unavailable -> grounded
            is FoundationResult.Failure -> grounded
            else -> FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE)
        }
        return transact(grounded.value.instant) { db, now ->
            val current = readAndReconcile(db, now).first
            if (current.revision != expectedRevision) throw StateConflict()
            val revision = Math.addExact(current.revision, 1)
            val marker = InteractionMarker(kind, now, grounded.value.zoneId, revision)
            val next = current.copy(revision = revision, updatedAt = now,
                interactions = current.interactions.filterNot { it.kind == kind } + marker)
            require(YukiStateValidation.valid(next))
            persist(db, next)
            next
        }
    }

    private fun put(items: List<CurrentItem>, id: String, text: String, expiry: Instant?, now: Instant): List<CurrentItem> {
        require(expiry == null || expiry.isAfter(now))
        val old = items.find { it.id == id }
        val item = CurrentItem(id, text, old?.createdAt ?: now, now, expiry)
        return items.filterNot { it.id == id } + item
    }
    private fun remove(items: List<CurrentItem>, id: String): List<CurrentItem> {
        require(items.any { it.id == id })
        return items.filterNot { it.id == id }
    }

    private class StateConflict : RuntimeException()
    private fun <T : Any> transact(block: (SQLiteDatabase, Instant) -> T): FoundationResult<T> {
        val time = temporal.ground()
        if (time !is FoundationResult.Success) return when (time) {
            is FoundationResult.Unavailable -> time
            is FoundationResult.Failure -> time
            else -> FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE)
        }
        return transact(time.value.instant, block)
    }
    private fun <T : Any> transact(now: Instant, block: (SQLiteDatabase, Instant) -> T): FoundationResult<T> = try {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val value = block(db, now)
            db.setTransactionSuccessful()
            FoundationResult.Success(value)
        } finally { db.endTransaction() }
    } catch (_: StateConflict) { FoundationResult.Failure(FailureCategory.CONFLICT) }
      catch (_: IllegalArgumentException) { FoundationResult.Failure(FailureCategory.INVALID_INPUT) }
      catch (_: Exception) { FoundationResult.Failure(FailureCategory.CONFLICT) }

    private fun readAndReconcile(db: SQLiteDatabase, now: Instant): Pair<YukiStateSnapshot, Boolean> {
        val raw = try { read(db).also { require(YukiStateValidation.valid(it.first)) } }
            catch (_: Exception) { throw StateConflict() }
        val state = raw.first
        val next = state.copy(
            focus = state.focus?.takeIf { it.expiresAt == null || now.isBefore(it.expiresAt) },
            intentions = state.intentions.filter { it.expiresAt == null || now.isBefore(it.expiresAt) },
            topics = state.topics.filter { it.expiresAt == null || now.isBefore(it.expiresAt) }
        )
        if (next != state) {
            val revised = next.copy(revision = Math.addExact(state.revision, 1), updatedAt = now)
            require(YukiStateValidation.valid(revised))
            persist(db, revised)
            return revised to raw.second
        }
        return state to raw.second
    }

    private fun read(db: SQLiteDatabase): Pair<YukiStateSnapshot, Boolean> {
        fun rows(sql: String): List<List<String?>> = db.rawQuery(sql, null).use { c ->
            buildList { while (c.moveToNext()) add((0 until c.columnCount).map { c.getString(it) }) }
        }
        val metadata = rows("SELECT state_version, revision, project_id, project_label, focus_text, focus_expiry, updated_at, opened FROM yuki_state")
        require(metadata.size == 1)
        val m = metadata.single()
        require(m[3] != null || m[2] == null)
        require(m[4] != null || m[5] == null)
        val intentions = rows("SELECT item_id, content, created_at, updated_at, expires_at FROM state_intention")
        val topics = rows("SELECT item_id, content, created_at, updated_at, expires_at FROM state_topic")
        fun item(row: List<String?>) = CurrentItem(requireNotNull(row[0]), requireNotNull(row[1]),
            Instant.parse(requireNotNull(row[2])), Instant.parse(requireNotNull(row[3])), row[4]?.let(Instant::parse))
        val markers = rows("SELECT kind, instant, zone_id, revision FROM state_interaction").map {
            InteractionMarker(InteractionKind.valueOf(requireNotNull(it[0])), Instant.parse(requireNotNull(it[1])),
                ZoneId.of(requireNotNull(it[2])), requireNotNull(it[3]).toLong())
        }
        val flag = requireNotNull(m[7]).toInt()
        require(flag == 0 || flag == 1)
        return YukiStateSnapshot(YukiStateVersion(requireNotNull(m[0]).toInt()), requireNotNull(m[1]).toLong(),
            m[3]?.let { CurrentProject(m[2], it) }, m[4]?.let { CurrentFocus(it, m[5]?.let(Instant::parse)) },
            intentions.map(::item), topics.map(::item), markers, Instant.parse(requireNotNull(m[6]))) to (flag == 0)
    }

    private fun persist(db: SQLiteDatabase, state: YukiStateSnapshot) {
        db.execSQL("UPDATE yuki_state SET state_version=?, revision=?, project_id=?, project_label=?, focus_text=?, focus_expiry=?, updated_at=? WHERE singleton=1",
            arrayOf(state.version.value, state.revision, state.project?.id, state.project?.label,
                state.focus?.text, state.focus?.expiresAt?.toString(), state.updatedAt.toString()))
        fun write(table: String, items: List<CurrentItem>) {
            db.execSQL("DELETE FROM $table")
            items.forEach { db.execSQL("INSERT INTO $table VALUES (?, ?, ?, ?, ?)",
                arrayOf(it.id, it.text, it.createdAt.toString(), it.updatedAt.toString(), it.expiresAt?.toString())) }
        }
        write("state_intention", state.intentions)
        write("state_topic", state.topics)
        db.execSQL("DELETE FROM state_interaction")
        state.interactions.forEach { db.execSQL("INSERT INTO state_interaction VALUES (?, ?, ?, ?)",
            arrayOf(it.kind.name, it.instant.toString(), it.zoneId.id, it.revision)) }
    }

    override fun close() = helper.close()
}

/** Clock and zone are sampled on demand, including after a device timezone change. */
fun deviceTemporalGrounding(): TemporalGroundingReader = DeterministicTemporalGrounding(
    ClockSource { Instant.now() }, ZoneSource { ZoneId.systemDefault() })
