package com.mavyy.localyuki.memory

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.mavyy.localyuki.continuity.ContinuitySchema
import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.memory.*
import com.mavyy.localyuki.foundation.provenance.*
import com.mavyy.localyuki.foundation.subsystem.CoreSubsystemId
import com.mavyy.localyuki.foundation.temporal.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class MemoryStoreTest {
    private lateinit var context: Context
    private val instant = Instant.parse("2026-09-24T12:00:00Z")
    private var available = true
    private val temporal = object : TemporalGroundingReader {
        override fun ground(): FoundationResult<TemporalGroundingSnapshot> = if (!available)
            FoundationResult.Unavailable(UnavailableReason.DEPENDENCY_UNAVAILABLE) else
            FoundationResult.Success(TemporalGroundingSnapshot(instant,ZoneId.of("America/Chicago"),
                instant.atZone(ZoneId.of("America/Chicago")).toLocalDateTime()))
        override fun resolve(query: CalendarQuery): FoundationResult<ResolvedTimeWindow> =
            FoundationResult.Unavailable(UnavailableReason.DEPENDENCY_UNAVAILABLE)
    }
    @Before fun setup() { context=RuntimeEnvironment.getApplication(); context.deleteDatabase(ContinuitySchema.NAME); available=true }
    @After fun teardown() { context.deleteDatabase(ContinuitySchema.NAME) }
    private fun db() = SQLiteDatabase.openDatabase(context.getDatabasePath(ContinuitySchema.NAME).path,null,SQLiteDatabase.OPEN_READWRITE)
    private fun count(table: String): Int = db().use { it.rawQuery("SELECT count(*) FROM $table",null).use { c -> c.moveToFirst(); c.getInt(0) } }
    private fun <T : Any> ok(result: FoundationResult<T>): T = (result as FoundationResult.Success<T>).value
    private fun user(id: String) = EvidenceRef(id,EvidenceSourceKind.USER_INPUT)
    private fun seed(store: MemoryStore) {
        assertEquals(MemoryStore.Start.INITIALIZED,store.open())
        assertEquals("yuki-aster",ok(store.createThread("thread-1")).selfId)
        ok(store.appendEvidence(NewEvidence("e1",EvidenceSourceKind.USER_INPUT,"thread-1","first")))
        ok(store.appendEvidence(NewEvidence("e2",EvidenceSourceKind.USER_INPUT,"thread-1","correction")))
    }
    @Test fun immutableEvidenceOrderingIntegrityRestartAndNoClockReads() {
        MemoryStore(context,temporal).use { store ->
            seed(store)
            assertEquals(listOf(1L,2L),ok(store.threadEvidence("thread-1",0,10)).map { it.sequence })
            assertEquals(instant,ok(store.getEvidence(user("e1"))).capturedAt)
            assertEquals(FoundationResult.Failure(FailureCategory.CONFLICT),store.appendEvidence(NewEvidence("e1",EvidenceSourceKind.USER_INPUT,"thread-1","different")))
            assertEquals(FoundationResult.Failure(FailureCategory.INVALID_INPUT),store.appendEvidence(NewEvidence("e3",EvidenceSourceKind.USER_INPUT,null,"unthreaded")))
        }
        db().use { sql ->
            assertThrows(Exception::class.java) { sql.execSQL("UPDATE memory_evidence SET payload='evil' WHERE evidence_id='e1'") }
            assertThrows(Exception::class.java) { sql.execSQL("DELETE FROM memory_evidence WHERE evidence_id='e1'") }
        }
        available=false
        MemoryStore(context,temporal).use { store ->
            assertEquals(MemoryStore.Start.RESTORED,store.open())
            assertEquals("first",ok(store.getEvidence(user("e1"))).payload)
            assertEquals(FoundationResult.Unavailable(UnavailableReason.DEPENDENCY_UNAVAILABLE),
                store.appendEvidence(NewEvidence("e4",EvidenceSourceKind.SYSTEM_EVENT,null,"later")))
        }
        db().use { it.execSQL("DROP TRIGGER immutable_memory_evidence_update"); it.execSQL("UPDATE memory_evidence SET payload='changed' WHERE evidence_id='e1'") }
        MemoryStore(context,temporal).use { store ->
            assertEquals(FoundationResult.Failure(FailureCategory.CONFLICT),store.getEvidence(user("e1")))
        }
    }
    @Test fun revisionsRestoreAuditIdempotenceAndCheckpoints() {
        MemoryStore(context,temporal).use { store ->
            seed(store)
            val a=ok(store.create(CreateMemory("cmd1","m1","a",MemoryKind.AUTOBIOGRAPHICAL,"A",listOf(user("e1")))))
            assertEquals(MemoryStatus.CURRENT,a.status)
            assertEquals(FoundationResult.Failure(FailureCategory.REJECTED),store.create(CreateMemory("bad","m2","x",MemoryKind.FACTUAL,"X",listOf(user("missing")))))
            val b=ok(store.ownerUpdate(OwnerUpdate("cmd2","m1","b","a","B",user("e2"))))
            assertEquals("a",b.supersedes)
            assertEquals(MemoryStatus.HISTORICAL,ok(store.history("m1",0,10)).first().status)
            assertEquals(FoundationResult.Failure(FailureCategory.CONFLICT),store.update(UpdateMemory("stale","m1","c","a","C",listOf(user("e1")))))
            assertEquals(2,count("memory_audit"))
            val d=ok(store.ownerRestore(OwnerRestore("cmd3","m1","d","b","a",user("e2"))))
            assertEquals("A",d.content); assertEquals("b",d.supersedes); assertEquals("a",d.restoredFrom)
            assertEquals(listOf("a","b","d"),ok(store.history("m1",0,10)).map { it.id })
            assertEquals(d,ok(store.ownerRestore(OwnerRestore("cmd3","m1","d","b","a",user("e2")))))
            assertEquals(3,count("memory_audit"))
            assertEquals(FoundationResult.Failure(FailureCategory.CONFLICT),store.ownerRestore(OwnerRestore("cmd3","m1","z","d","a",user("e2"))))
            val c=ok(store.appendCheckpoint(NewCheckpoint("cp1","thread-1",1,2,"Derived",CoreSubsystemId.MEMORY_ENGINE,null)))
            assertEquals("cp1",ok(store.latestCheckpoint("thread-1")).checkpoint?.id)
            assertEquals(FoundationResult.Failure(FailureCategory.CONFLICT),store.getEvidence(EvidenceRef(c.id,EvidenceSourceKind.AUTHORITY_RECORD)))
            assertEquals(FoundationResult.Failure(FailureCategory.REJECTED),store.appendCheckpoint(NewCheckpoint("badcp","thread-1",1,3,"Future",CoreSubsystemId.MEMORY_ENGINE,null)))
            assertEquals(FoundationResult.Failure(FailureCategory.INVALID_INPUT),store.currentMemories(null,101))
        }
        MemoryStore(context,temporal).use { store ->
            assertEquals(MemoryStore.Start.RESTORED,store.open())
            assertEquals("d",ok(store.getCurrent("m1")).current.id)
        }
    }
    @Test fun invalidMetadataIsolatedFromIdentityAndState() {
        MemoryStore(context,temporal).use { seed(it) }
        db().use { it.execSQL("DELETE FROM memory_metadata") }
        MemoryStore(context,temporal).use { store ->
            assertEquals(MemoryStore.Start.UNAVAILABLE,store.open())
            assertEquals(FoundationResult.Failure(FailureCategory.CONFLICT),store.getEvidence(user("e1")))
        }
        db().use { sql ->
            sql.rawQuery("SELECT self_id FROM identity_anchor",null).use { c -> c.moveToFirst(); assertEquals("yuki-aster",c.getString(0)) }
            sql.rawQuery("SELECT revision FROM yuki_state",null).use { c -> c.moveToFirst(); assertEquals(0,c.getInt(0)) }
        }
        assertEquals(2,count("memory_evidence"))
    }
    @Test fun coreRangeQueriesUseIndexes() {
        MemoryStore(context,temporal).use { seed(it) }
        db().use { sql ->
            val paths = listOf(
                "SELECT evidence_id FROM memory_evidence WHERE thread_id='thread-1' AND sequence>0 ORDER BY sequence LIMIT 10" to "idx_evidence_thread_sequence",
                "SELECT revision_id FROM memory_revision WHERE memory_id='m1' AND revision_number>0 ORDER BY revision_number LIMIT 10" to "idx_revision_memory_number",
                "SELECT command_id FROM memory_audit WHERE memory_id='m1' ORDER BY occurred_at LIMIT 10" to "idx_audit_memory_time",
                "SELECT checkpoint_id FROM memory_checkpoint WHERE thread_id='thread-1' ORDER BY created_at LIMIT 10" to "idx_checkpoint_thread_time")
            for ((query,index) in paths) sql.rawQuery("EXPLAIN QUERY PLAN $query",null).use { cursor ->
                val descriptions=buildList { while (cursor.moveToNext()) add(cursor.getString(3) ?: "") }
                assertTrue("$index missing from $descriptions",descriptions.any { index in it })
            }
        }
        db().use { sql ->
            sql.rawQuery("EXPLAIN QUERY PLAN SELECT checkpoint_id FROM memory_checkpoint WHERE thread_id=? AND checkpoint_id>? ORDER BY checkpoint_id LIMIT ?",
                arrayOf("thread-1","cp-10","10")).use { cursor ->
                val descriptions=buildList { while (cursor.moveToNext()) add(cursor.getString(3) ?: "") }
                assertTrue("Page query did not use checkpoint ID index: $descriptions",
                    descriptions.any { "idx_checkpoint_thread_id" in it })
                assertFalse("Page query requires temporary sorting: $descriptions",
                    descriptions.any { "TEMP B-TREE" in it.uppercase() })
            }
        }
    }
    @Test fun checkpointKeysetPagesDoNotSkipOrDuplicateRows() {
        MemoryStore(context,temporal).use { store ->
            seed(store)
            for (id in listOf("cp-30","cp-10","cp-20")) {
                ok(store.appendCheckpoint(NewCheckpoint(id,"thread-1",1,2,
                    "Derived $id",CoreSubsystemId.MEMORY_ENGINE,null)))
            }
            ok(store.createThread("thread-2"))
            ok(store.appendEvidence(NewEvidence("e-other",EvidenceSourceKind.USER_INPUT,"thread-2","Other thread")))
            ok(store.appendCheckpoint(NewCheckpoint("cp-15","thread-2",1,1,
                "Other projection",CoreSubsystemId.MEMORY_ENGINE,null)))
            val collected=mutableListOf<String>()
            var after: String? = null
            while (true) {
                val page=ok(store.checkpoints("thread-1",after,1))
                if (page.isEmpty()) break
                assertEquals(1,page.size)
                collected += page.single().id
                after = page.single().id
            }
            assertEquals(listOf("cp-10","cp-20","cp-30"),collected)
            assertEquals(collected.size,collected.distinct().size)
            assertEquals(listOf("cp-20","cp-30"),ok(store.checkpoints("thread-1","cp-10",2)).map { it.id })
            assertEquals("cp-30",ok(store.latestCheckpoint("thread-1")).checkpoint?.id)
        }
    }
}
