package com.mavyy.localyuki.memory

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.mavyy.localyuki.continuity.ContinuityHelper
import com.mavyy.localyuki.continuity.ContinuityMapper
import com.mavyy.localyuki.continuity.ContinuitySchema
import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.memory.*
import com.mavyy.localyuki.foundation.provenance.*
import com.mavyy.localyuki.foundation.temporal.*
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId

/** App authority. Construct and wire only in trusted application plumbing, never an advisory port. */
class MemoryStore(context: Context, private val temporal: TemporalGroundingReader) : AutoCloseable {
    private val app = context.applicationContext
    private val helper = ContinuityHelper(app, !app.getDatabasePath(ContinuitySchema.NAME).exists())
    enum class Start { INITIALIZED, RESTORED, UNAVAILABLE }
    private var opened = false
    private class Conflict : RuntimeException()
    private class Rejected : RuntimeException()

    fun reader(): MemoryReader = object : MemoryReader {
        override fun thread(id: String) = getThread(id)
        override fun evidence(ref: EvidenceRef) = getEvidence(ref)
        override fun threadEvidence(threadId: String, afterSequence: Long, limit: Int) = this@MemoryStore.threadEvidence(threadId,afterSequence,limit)
        override fun current(id: String) = getCurrent(id)
        override fun currentPage(afterId: String?, limit: Int) = currentMemories(afterId,limit)
        override fun history(id: String, afterRevision: Long, limit: Int) = this@MemoryStore.history(id,afterRevision,limit)
        override fun provenance(revisionId: String) = revisionEvidence(revisionId)
        override fun audit(memoryId: String, afterCommandId: String?, limit: Int) = this@MemoryStore.audit(memoryId,afterCommandId,limit)
        override fun latestCheckpoint(threadId: String) = this@MemoryStore.latestCheckpoint(threadId)
        override fun checkpoints(threadId: String, afterId: String?, limit: Int) = this@MemoryStore.checkpoints(threadId,afterId,limit)
    }

    fun open(): Start {
        val result = write { db ->
            val wasOpened = metadata(db)
            if (!wasOpened) db.execSQL("UPDATE memory_metadata SET opened=1 WHERE singleton=1")
            if (opened || wasOpened) Start.RESTORED else Start.INITIALIZED
        }
        if (result is FoundationResult.Success) { opened = true; return result.value }
        return Start.UNAVAILABLE
    }

    private fun metadata(db: SQLiteDatabase): Boolean {
        val rows = query(db, "SELECT format_version,opened FROM memory_metadata")
        if (rows.size != 1 || rows[0][0] != "1" || rows[0][1] !in listOf("0", "1")) throw Conflict()
        return rows[0][1] == "1"
    }
    private fun query(db: SQLiteDatabase, sql: String, args: Array<String> = emptyArray()): List<List<String?>> =
        db.rawQuery(sql, args).use { cursor -> buildList {
            while (cursor.moveToNext()) add((0 until cursor.columnCount).map { cursor.getString(it) })
        } }
    private fun row(db: SQLiteDatabase, sql: String, vararg args: String): List<String?>? =
        query(db, sql, arrayOf(*args)).singleOrNull()
    private fun String?.required(): String = this ?: throw Conflict()
    private fun String?.time(): Instant = try { Instant.parse(required()) } catch (_: Exception) { throw Conflict() }
    private fun String?.number(): Long = toString().toLongOrNull() ?: throw Conflict()
    private inline fun <reified E : Enum<E>> named(value: String?): E =
        enumValues<E>().find { it.name == value } ?: throw Conflict()
    private fun validId(value: String) { require(MemoryBounds.id(value)) }
    private fun stamp(): FoundationResult<TemporalGroundingSnapshot> = try { temporal.ground() }
        catch (_: Exception) { FoundationResult.Unavailable(UnavailableReason.DEPENDENCY_UNAVAILABLE) }
    private fun <T : Any> write(block: (SQLiteDatabase) -> T): FoundationResult<T> = try {
        val db = helper.writableDatabase
        db.beginTransaction()
        try { metadata(db); val value = block(db); db.setTransactionSuccessful(); FoundationResult.Success(value) }
        finally { db.endTransaction() }
    } catch (_: Conflict) { FoundationResult.Failure(FailureCategory.CONFLICT) }
      catch (_: Rejected) { FoundationResult.Failure(FailureCategory.REJECTED) }
      catch (_: IllegalArgumentException) { FoundationResult.Failure(FailureCategory.INVALID_INPUT) }
      catch (_: SQLiteException) { FoundationResult.Failure(FailureCategory.CONFLICT) }
      catch (_: Exception) { FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE) }
    private fun <T : Any> read(block: (SQLiteDatabase) -> T): FoundationResult<T> = try {
        val db = helper.readableDatabase
        metadata(db)
        FoundationResult.Success(block(db))
    } catch (_: Conflict) { FoundationResult.Failure(FailureCategory.CONFLICT) }
      catch (_: IllegalArgumentException) { FoundationResult.Failure(FailureCategory.INVALID_INPUT) }
      catch (_: SQLiteException) { FoundationResult.Failure(FailureCategory.CONFLICT) }
      catch (_: Exception) { FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE) }
    private fun <T : Any> timed(block: (SQLiteDatabase, TemporalGroundingSnapshot) -> T): FoundationResult<T> {
        val time = stamp()
        if (time !is FoundationResult.Success) return when (time) {
            is FoundationResult.Unavailable -> time
            is FoundationResult.Failure -> time
            else -> FoundationResult.Unavailable(UnavailableReason.DEPENDENCY_UNAVAILABLE)
        }
        return write { db -> block(db, time.value) }
    }

    /** Canonical versioned digest; SHA-256 detects accidental corruption, not an adversarial actor. */
    private fun digest(id: String, kind: String, thread: String?, seq: Long?, instant: String,
                       zone: String, version: Int, payload: String): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            listOf("memory-evidence-v1", id, kind, thread ?: "", seq?.toString() ?: "",
                instant, zone, version.toString(), payload).forEach {
                val b = it.toByteArray(Charsets.UTF_8); out.writeInt(b.size); out.write(b)
            }
        }
        return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
    private fun thread(db: SQLiteDatabase, id: String): MemoryThread? =
        row(db, "SELECT thread_id,self_id,relationship_id,created_at FROM memory_thread WHERE thread_id=?", id)?.let {
            MemoryThread(it[0].required(), it[1].required(), it[2].required(), it[3].time())
        }
    fun getThread(id: String): FoundationResult<MemoryThread> = read { db ->
        validId(id); thread(db, id) ?: throw Conflict()
    }

    /** Identity anchors are obtained from the existing durable authority inside the transaction. */
    internal fun createThread(id: String): FoundationResult<MemoryThread> = timed { db, now ->
        validId(id)
        if (thread(db, id) != null) throw Conflict()
        if (ContinuityMapper.read(db) !is FoundationResult.Success) throw Conflict()
        val anchors = query(db, "SELECT self_id,relationship_id FROM identity_anchor")
        if (anchors.size != 1) throw Conflict()
        val self = anchors[0][0].required(); val relationship = anchors[0][1].required()
        validId(self); validId(relationship)
        db.execSQL("INSERT INTO memory_thread VALUES (?,?,?,?,?)",
            arrayOf(id,self,relationship,now.instant.toString(),now.zoneId.id))
        MemoryThread(id,self,relationship,now.instant)
    }

    private fun evidence(db: SQLiteDatabase, ref: EvidenceRef): RawEvidence? {
        val r = row(db, "SELECT evidence_id,source_kind,thread_id,sequence,captured_at,zone_id,payload_version,payload,digest FROM memory_evidence WHERE evidence_id=?", ref.opaqueId) ?: return null
        if (r[1] != ref.sourceKind.name) throw Conflict()
        val seq = r[3]?.number()
        val version = r[6].number().toInt()
        if (version != 1 || (r[2] == null) != (seq == null) || seq != null && seq <= 0) throw Conflict()
        val raw = RawEvidence(ref,r[2],seq,r[4].time(),try { ZoneId.of(r[5].required()) } catch (_: Exception) { throw Conflict() }, version,r[7].required())
        if (!MemoryBounds.text(raw.payload,MemoryBounds.MAX_EVIDENCE_BYTES) ||
            r[8] != digest(ref.opaqueId,r[1].required(),r[2],seq,r[4].required(),r[5].required(),version,raw.payload)) throw Conflict()
        raw.threadId?.let { if (thread(db,it) == null) throw Conflict() }
        return raw
    }
    fun getEvidence(ref: EvidenceRef): FoundationResult<RawEvidence> = read { db ->
        validId(ref.opaqueId); evidence(db,ref) ?: throw Conflict()
    }
    internal fun appendEvidence(input: NewEvidence): FoundationResult<RawEvidence> = timed { db, now ->
        validId(input.id)
        require(MemoryBounds.text(input.payload,MemoryBounds.MAX_EVIDENCE_BYTES))
        if (input.kind in listOf(EvidenceSourceKind.USER_INPUT,EvidenceSourceKind.YUKI_OUTPUT))
            require(input.threadId != null)
        input.threadId?.let { validId(it); if (thread(db,it) == null) throw Conflict() }
        // No implicit idempotence: duplicate IDs are always conflicts, even when contents match.
        if (row(db,"SELECT evidence_id FROM memory_evidence WHERE evidence_id=?",input.id) != null) throw Conflict()
        val seq = input.threadId?.let { id ->
            (row(db,"SELECT MAX(sequence) FROM memory_evidence WHERE thread_id=?",id)?.get(0)?.number() ?: 0L) + 1L
        }
        val instant = now.instant.toString(); val zone = now.zoneId.id
        db.execSQL("INSERT INTO memory_evidence VALUES (?,?,?,?,?,?,?,?,?)",arrayOf(input.id,input.kind.name,
            input.threadId,seq,instant,zone,1,input.payload,digest(input.id,input.kind.name,input.threadId,seq,instant,zone,1,input.payload)))
        evidence(db,EvidenceRef(input.id,input.kind)) ?: throw Conflict()
    }
    fun threadEvidence(threadId: String, afterSequence: Long, limit: Int): FoundationResult<List<RawEvidence>> = read { db ->
        validId(threadId); require(afterSequence >= 0 && limit in 1..MemoryBounds.MAX_PAGE)
        if (thread(db,threadId) == null) throw Conflict()
        query(db,"SELECT evidence_id,source_kind FROM memory_evidence WHERE thread_id=? AND sequence>? ORDER BY sequence LIMIT ?",
            arrayOf(threadId,afterSequence.toString(),limit.toString())).map {
            evidence(db,EvidenceRef(it[0].required(),named<EvidenceSourceKind>(it[1]))) ?: throw Conflict()
        }.also { rows -> rows.forEachIndexed { index, evidence ->
            if (evidence.sequence != afterSequence+index+1L) throw Conflict()
        } }
    }

    private fun provenance(db: SQLiteDatabase, revisionId: String): List<EvidenceRef> =
        query(db,"SELECT e.evidence_id,e.source_kind FROM memory_provenance p JOIN memory_evidence e ON e.evidence_id=p.evidence_id WHERE p.revision_id=? ORDER BY e.evidence_id",arrayOf(revisionId)).map {
            val ref = EvidenceRef(it[0].required(),named<EvidenceSourceKind>(it[1]))
            evidence(db,ref) ?: throw Conflict(); ref
        }
    private fun revision(db: SQLiteDatabase, id: String): MemoryRevision? {
        val r = row(db,"SELECT revision_id,memory_id,revision_number,content,created_at,origin,supersedes,restored_from FROM memory_revision WHERE revision_id=?",id) ?: return null
        val head = row(db,"SELECT current_revision FROM durable_memory WHERE memory_id=?",r[1].required())?.get(0) ?: throw Conflict()
        val refs = provenance(db,id)
        if (!MemoryBounds.evidence(refs) || !MemoryBounds.text(r[3].required(),MemoryBounds.MAX_CONTENT_BYTES)) throw Conflict()
        return MemoryRevision(r[0].required(),r[1].required(),r[2].number(),r[3].required(),r[4].time(),
            named<MemoryOrigin>(r[5]),r[6],r[7],if (head == id) MemoryStatus.CURRENT else MemoryStatus.HISTORICAL,refs)
    }
    private fun memory(db: SQLiteDatabase,id: String): DurableMemory? {
        val r = row(db,"SELECT memory_id,kind,created_at,current_revision FROM durable_memory WHERE memory_id=?",id) ?: return null
        val current = revision(db,r[3].required()) ?: throw Conflict()
        if (current.memoryId != id || current.status != MemoryStatus.CURRENT || current.number <= 0) throw Conflict()
        val max = row(db,"SELECT MAX(revision_number) FROM memory_revision WHERE memory_id=?",id)?.get(0)?.number() ?: throw Conflict()
        val total = row(db,"SELECT COUNT(*) FROM memory_revision WHERE memory_id=?",id)?.get(0)?.number() ?: throw Conflict()
        if (max != current.number || total != max) throw Conflict()
        if (current.number == 1L && current.supersedes != null || current.number > 1L && current.supersedes == null) throw Conflict()
        current.supersedes?.let { priorId ->
            val prev = revision(db,priorId) ?: throw Conflict()
            if (prev.memoryId != id || prev.number != current.number-1) throw Conflict()
        }
        return DurableMemory(id,named<MemoryKind>(r[1]),r[2].time(),current)
    }
    fun getCurrent(id: String): FoundationResult<DurableMemory> = read { db ->
        validId(id); memory(db,id) ?: throw Conflict()
    }
    fun currentMemories(afterId: String?, limit: Int): FoundationResult<List<DurableMemory>> = read { db ->
        require(limit in 1..MemoryBounds.MAX_PAGE); afterId?.let(::validId)
        query(db,"SELECT memory_id FROM durable_memory WHERE memory_id>? ORDER BY memory_id LIMIT ?",
            arrayOf(afterId ?: "",limit.toString())).map { memory(db,it[0].required()) ?: throw Conflict() }
    }
    fun history(id: String, afterRevision: Long, limit: Int): FoundationResult<List<MemoryRevision>> = read { db ->
        validId(id); require(afterRevision >= 0 && limit in 1..MemoryBounds.MAX_PAGE)
        val m = memory(db,id) ?: throw Conflict()
        query(db,"SELECT revision_id FROM memory_revision WHERE memory_id=? AND revision_number>? ORDER BY revision_number LIMIT ?",
            arrayOf(id,afterRevision.toString(),limit.toString())).map { revision(db,it[0].required()) ?: throw Conflict() }
            .also { rows -> rows.forEachIndexed { index, item ->
                if (item.number != afterRevision+index+1L) throw Conflict()
                if (item.number > 1L) {
                    val priorId = row(db,"SELECT revision_id FROM memory_revision WHERE memory_id=? AND revision_number=?",
                        id,(item.number-1).toString())?.get(0) ?: throw Conflict()
                    if (item.supersedes != priorId) throw Conflict()
                } else if (item.supersedes != null) throw Conflict()
            } }
    }
    fun revisionEvidence(revisionId: String): FoundationResult<List<EvidenceRef>> = read { db ->
        validId(revisionId); revision(db,revisionId)?.evidence ?: throw Conflict()
    }

    private fun fingerprint(parts: List<String>): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out -> parts.forEach { val b=it.toByteArray(Charsets.UTF_8); out.writeInt(b.size); out.write(b) } }
        return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()).joinToString("") { "%02x".format(it) }
    }
    private fun command(db: SQLiteDatabase, id: String, fp: String): MemoryRevision? {
        val r = row(db,"SELECT fingerprint,resulting_revision FROM memory_audit WHERE command_id=?",id) ?: return null
        if (r[0] != fp) throw Conflict()
        return revision(db,r[1].required()) ?: throw Conflict()
    }
    private fun commit(db: SQLiteDatabase, now: Instant, commandId: String, memoryId: String, revisionId: String,
                       kind: MemoryKind?, content: String, refs: List<EvidenceRef>, expected: String?,
                       origin: MemoryOrigin, action: MemoryAction, restoreFrom: String?, owner: EvidenceRef?, fp: String): MemoryRevision {
        validId(commandId); validId(memoryId); validId(revisionId)
        expected?.let(::validId); restoreFrom?.let(::validId)
        require(MemoryBounds.text(content,MemoryBounds.MAX_CONTENT_BYTES) && MemoryBounds.evidence(refs))
        command(db,commandId,fp)?.let { return it }
        val old = memory(db,memoryId)
        if (kind != null) { if (old != null || expected != null) throw Conflict() }
        else { if (old == null || old.current.id != expected) throw Conflict() }
        if (row(db,"SELECT revision_id FROM memory_revision WHERE revision_id=?",revisionId) != null) throw Conflict()
        refs.forEach { if (evidence(db,it) == null) throw Rejected() }
        if (owner != null) {
            if (owner.sourceKind != EvidenceSourceKind.USER_INPUT || owner !in refs || evidence(db,owner)?.threadId == null) throw Rejected()
        }
        if (restoreFrom != null) {
            val historical = revision(db,restoreFrom) ?: throw Conflict()
            if (historical.memoryId != memoryId || historical.status != MemoryStatus.HISTORICAL || historical.content != content) throw Conflict()
        }
        if (kind != null) db.execSQL("INSERT INTO durable_memory VALUES (?,?,?,?)",
            arrayOf(memoryId,kind.name,now.toString(),revisionId))
        val number = if (old == null) 1L else old.current.number+1L
        db.execSQL("INSERT INTO memory_revision VALUES (?,?,?,?,?,?,?,?)",
            arrayOf(revisionId,memoryId,number,content,now.toString(),origin.name,expected,restoreFrom))
        refs.forEach { db.execSQL("INSERT INTO memory_provenance VALUES (?,?,?)",arrayOf(revisionId,it.opaqueId,"SUPPORTS")) }
        if (old != null) db.execSQL("UPDATE durable_memory SET current_revision=? WHERE memory_id=? AND current_revision=?",
            arrayOf(revisionId,memoryId,expected))
        db.execSQL("INSERT INTO memory_audit VALUES (?,?,?,?,?,?,?,?,?)",arrayOf(commandId,memoryId,action.name,
            expected,revisionId,restoreFrom,owner?.opaqueId,now.toString(),fp))
        return revision(db,revisionId) ?: throw Conflict()
    }
    internal fun create(input: CreateMemory): FoundationResult<MemoryRevision> = timed { db,now ->
        val fp=fingerprint(listOf("CREATE",input.memoryId,input.revisionId,input.kind.name,input.content)+input.evidence.map { it.opaqueId+it.sourceKind.name })
        commit(db,now.instant,input.commandId,input.memoryId,input.revisionId,input.kind,input.content,input.evidence,
            null,MemoryOrigin.APP_VALIDATED,MemoryAction.CREATE,null,null,fp)
    }
    internal fun update(input: UpdateMemory): FoundationResult<MemoryRevision> = timed { db,now ->
        val fp=fingerprint(listOf("UPDATE",input.memoryId,input.revisionId,input.expectedRevision,input.content)+input.evidence.map { it.opaqueId+it.sourceKind.name })
        commit(db,now.instant,input.commandId,input.memoryId,input.revisionId,null,input.content,input.evidence,
            input.expectedRevision,MemoryOrigin.APP_VALIDATED,MemoryAction.UPDATE,null,null,fp)
    }
    /** Only the trusted owner-facing adapter may call this; advisory proposals have no route here. */
    internal fun ownerUpdate(input: OwnerUpdate): FoundationResult<MemoryRevision> = timed { db,now ->
        val fp=fingerprint(listOf("OWNER_UPDATE",input.memoryId,input.revisionId,input.expectedRevision,input.content,input.ownerEvidence.opaqueId,input.ownerEvidence.sourceKind.name))
        commit(db,now.instant,input.commandId,input.memoryId,input.revisionId,null,input.content,listOf(input.ownerEvidence),
            input.expectedRevision,MemoryOrigin.OWNER_UPDATE,MemoryAction.OWNER_UPDATE,null,input.ownerEvidence,fp)
    }
    internal fun ownerRestore(input: OwnerRestore): FoundationResult<MemoryRevision> = timed { db,now ->
        val fp=fingerprint(listOf("OWNER_RESTORE",input.memoryId,input.revisionId,input.expectedRevision,input.historicalRevision,input.ownerEvidence.opaqueId,input.ownerEvidence.sourceKind.name))
        command(db,input.commandId,fp)?.let { return@timed it }
        val history=revision(db,input.historicalRevision) ?: throw Conflict()
        commit(db,now.instant,input.commandId,input.memoryId,input.revisionId,null,history.content,
            (history.evidence+input.ownerEvidence).distinct(),input.expectedRevision,MemoryOrigin.OWNER_RESTORE,
            MemoryAction.OWNER_RESTORE,input.historicalRevision,input.ownerEvidence,fp)
    }
    fun audit(memoryId: String, afterCommandId: String?, limit: Int): FoundationResult<List<MemoryAudit>> = read { db ->
        validId(memoryId); afterCommandId?.let(::validId); require(limit in 1..MemoryBounds.MAX_PAGE)
        if (memory(db,memoryId) == null) throw Conflict()
        val cursorTime = afterCommandId?.let { id ->
            val anchor = row(db,"SELECT memory_id,occurred_at FROM memory_audit WHERE command_id=?",id) ?: throw Conflict()
            if (anchor[0] != memoryId) throw Conflict()
            anchor[1].time().toString()
        }
        val sql = "SELECT command_id,action,previous_revision,resulting_revision,restored_from,command_evidence,occurred_at FROM memory_audit WHERE memory_id=?" +
            if (cursorTime == null) " ORDER BY occurred_at,command_id LIMIT ?" else
                " AND (occurred_at>? OR (occurred_at=? AND command_id>?)) ORDER BY occurred_at,command_id LIMIT ?"
        val args = if (cursorTime == null) arrayOf(memoryId,limit.toString()) else
            arrayOf(memoryId,cursorTime,cursorTime,afterCommandId!!,limit.toString())
        query(db,sql,args).map { r ->
            val ref=r[5]?.let { id -> val kind=row(db,"SELECT source_kind FROM memory_evidence WHERE evidence_id=?",id)?.get(0) ?: throw Conflict();
                EvidenceRef(id,named<EvidenceSourceKind>(kind)).also { evidence(db,it) ?: throw Conflict() } }
            MemoryAudit(r[0].required(),memoryId,named<MemoryAction>(r[1]),r[2],r[3].required(),r[4],ref,r[6].time())
        }
    }
    private fun checkpoint(db: SQLiteDatabase,id: String): ConversationCheckpoint? =
        row(db,"SELECT checkpoint_id,thread_id,start_sequence,end_sequence,content,created_at,producer,supersedes FROM memory_checkpoint WHERE checkpoint_id=?",id)?.let { r ->
            val start=r[2].number(); val end=r[3].number()
            if (start <= 0 || end < start || thread(db,r[1].required()) == null || !MemoryBounds.text(r[4].required(),MemoryBounds.MAX_CONTENT_BYTES)) throw Conflict()
            ConversationCheckpoint(r[0].required(),r[1].required(),start,end,r[4].required(),r[5].time(),
                named<com.mavyy.localyuki.foundation.subsystem.CoreSubsystemId>(r[6]),r[7])
        }
    internal fun appendCheckpoint(input: NewCheckpoint): FoundationResult<ConversationCheckpoint> = timed { db,now ->
        validId(input.id); validId(input.threadId)
        require(input.startSequence > 0 && input.endSequence >= input.startSequence &&
            MemoryBounds.text(input.content,MemoryBounds.MAX_CONTENT_BYTES))
        if (thread(db,input.threadId) == null || checkpoint(db,input.id) != null) throw Conflict()
        input.supersedes?.let { validId(it); if (checkpoint(db,it)?.threadId != input.threadId) throw Conflict() }
        val count=row(db,"SELECT COUNT(*) FROM memory_evidence WHERE thread_id=? AND sequence BETWEEN ? AND ?",
            input.threadId,input.startSequence.toString(),input.endSequence.toString())?.get(0)?.number() ?: throw Conflict()
        if (count != input.endSequence-input.startSequence+1) throw Rejected()
        db.execSQL("INSERT INTO memory_checkpoint VALUES (?,?,?,?,?,?,?,?)",arrayOf(input.id,input.threadId,
            input.startSequence,input.endSequence,input.content,now.instant.toString(),input.producer.name,input.supersedes))
        checkpoint(db,input.id) ?: throw Conflict()
    }
    fun latestCheckpoint(threadId: String): FoundationResult<LatestCheckpoint> = read { db ->
        validId(threadId); if (thread(db,threadId) == null) throw Conflict()
        LatestCheckpoint(row(db,"SELECT checkpoint_id FROM memory_checkpoint WHERE thread_id=? AND checkpoint_id NOT IN (SELECT supersedes FROM memory_checkpoint WHERE supersedes IS NOT NULL) ORDER BY created_at DESC,checkpoint_id DESC LIMIT 1",threadId)
            ?.let { checkpoint(db,it[0].required()) ?: throw Conflict() })
    }
    fun checkpoints(threadId: String, afterId: String?, limit: Int): FoundationResult<List<ConversationCheckpoint>> = read { db ->
        validId(threadId); afterId?.let(::validId); require(limit in 1..MemoryBounds.MAX_PAGE)
        if (thread(db,threadId) == null) throw Conflict()
        query(db,"SELECT checkpoint_id FROM memory_checkpoint WHERE thread_id=? AND checkpoint_id>? ORDER BY checkpoint_id LIMIT ?",
            arrayOf(threadId,afterId ?: "",limit.toString())).map { checkpoint(db,it[0].required()) ?: throw Conflict() }
    }
    override fun close() = helper.close()
}
