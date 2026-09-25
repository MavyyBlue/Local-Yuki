package com.mavyy.localyuki.continuity

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.mavyy.localyuki.foundation.contracts.FoundationResult
import com.mavyy.localyuki.foundation.state.*
import com.mavyy.localyuki.foundation.temporal.*
import com.mavyy.localyuki.state.YukiStateStore
import com.mavyy.localyuki.memory.MemoryStore
import com.mavyy.localyuki.foundation.memory.*
import com.mavyy.localyuki.foundation.provenance.*
import com.mavyy.localyuki.foundation.subsystem.CoreSubsystemId
import com.mavyy.localyuki.memory.LivingMemoryStore
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class MemoryMigrationTest {
    private lateinit var context: Context
    private val temporal = DeterministicTemporalGrounding(ClockSource { Instant.parse("2026-09-24T12:00:00Z") },
        ZoneSource { ZoneId.of("America/Chicago") })
    @Before fun setup() { context=RuntimeEnvironment.getApplication(); context.deleteDatabase(ContinuitySchema.NAME) }
    @After fun clean() { context.deleteDatabase(ContinuitySchema.NAME) }
    private fun db() = SQLiteDatabase.openDatabase(context.getDatabasePath(ContinuitySchema.NAME).path,null,SQLiteDatabase.OPEN_READWRITE)
    private fun dropMemory(sql: SQLiteDatabase) {
        listOf("living_recall_event","living_memory_term","living_memory_state","living_memory_metadata","memory_checkpoint","memory_audit","memory_provenance","memory_revision","durable_memory",
            "memory_evidence","memory_thread","memory_metadata").forEach { sql.execSQL("DROP TABLE $it") }
    }
    private fun scalar(sql: SQLiteDatabase, query: String): String? = sql.rawQuery(query,null).use { it.moveToFirst(); it.getString(0) }
    private fun dropLiving(sql: SQLiteDatabase) {
        listOf("living_coverage_insert","living_coverage_head").forEach { sql.execSQL("DROP TRIGGER $it") }
        listOf("living_recall_event","living_memory_term","living_memory_state","living_memory_metadata")
            .forEach { sql.execSQL("DROP TABLE $it") }
    }
    @Test fun versionThreeToFourPreservesActualPhaseFourHistory() {
        MemoryStore(context,temporal).use { store ->
            store.open()
            assertTrue(store.createThread("thread") is FoundationResult.Success)
            val ref=EvidenceRef("evidence",EvidenceSourceKind.USER_INPUT)
            assertTrue(store.appendEvidence(NewEvidence("evidence",EvidenceSourceKind.USER_INPUT,"thread","Original")) is FoundationResult.Success)
            assertTrue(store.create(CreateMemory("cmd1","memory","rev1",MemoryKind.FACTUAL,"Original",listOf(ref))) is FoundationResult.Success)
            assertTrue(store.ownerUpdate(OwnerUpdate("cmd2","memory","rev2","rev1","Corrected",ref)) is FoundationResult.Success)
            assertTrue(store.appendCheckpoint(NewCheckpoint("checkpoint","thread",1,1,"Derived",CoreSubsystemId.MEMORY_ENGINE,null)) is FoundationResult.Success)
        }
        db().use { sql -> dropLiving(sql); sql.version=3 }
        MemoryStore(context,temporal).use { store ->
            assertEquals(MemoryStore.Start.RESTORED,store.open())
            assertEquals("rev2",(store.getCurrent("memory") as FoundationResult.Success).value.current.id)
            assertEquals(2,(store.history("memory",0,10) as FoundationResult.Success).value.size)
            assertEquals(1,(store.getCurrent("memory") as FoundationResult.Success).value.current.evidence.size)
            assertEquals(2,(store.audit("memory",null,10) as FoundationResult.Success).value.size)
            assertEquals("checkpoint",(store.latestCheckpoint("thread") as FoundationResult.Success).value.checkpoint?.id)
            assertEquals("Original",(store.getEvidence(EvidenceRef("evidence",EvidenceSourceKind.USER_INPUT)) as FoundationResult.Success).value.payload)
            LivingMemoryStore(context,temporal,store.reader()).use { assertEquals(LivingMemoryStore.Start.INITIALIZED,it.open()) }
        }
        db().use { sql ->
            assertEquals(4,sql.version)
            assertEquals("yuki-aster",scalar(sql,"SELECT self_id FROM identity_anchor"))
            assertEquals("9",scalar(sql,"SELECT count(*) FROM personality_facet"))
            assertEquals("1",scalar(sql,"SELECT count(*) FROM memory_thread"))
            assertEquals("1",scalar(sql,"SELECT count(*) FROM memory_evidence"))
            assertEquals("2",scalar(sql,"SELECT count(*) FROM memory_revision"))
            assertEquals("2",scalar(sql,"SELECT count(*) FROM memory_provenance"))
            assertEquals("2",scalar(sql,"SELECT count(*) FROM memory_audit"))
            assertEquals("1",scalar(sql,"SELECT count(*) FROM memory_checkpoint"))
            assertEquals(LivingMemorySchema.MIGRATION_ID,scalar(sql,"SELECT migration_id FROM continuity_migration_history WHERE to_version=4"))
        }
    }
    @Test fun failedLivingMigrationRollsBackWithoutReinitialization() {
        ContinuityStore(context).use { it.open() }
        db().use { sql ->
            dropLiving(sql)
            sql.execSQL("CREATE TABLE living_memory_state (blocker TEXT)")
            sql.version=3
        }
        MemoryStore(context,temporal).use { assertEquals(MemoryStore.Start.UNAVAILABLE,it.open()) }
        db().use { sql ->
            assertEquals(3,sql.version)
            assertEquals("yuki-aster",scalar(sql,"SELECT self_id FROM identity_anchor"))
            assertEquals("0",scalar(sql,"SELECT count(*) FROM continuity_migration_history"))
            sql.rawQuery("SELECT name FROM sqlite_master WHERE name='living_memory_metadata'",null).use { assertEquals(0,it.count) }
        }
    }
    @Test fun versionTwoToFourPreservesStateIdentityAndPersonality() {
        ContinuityStore(context).use { it.open() }
        YukiStateStore(context,temporal).use { state ->
            state.open()
            val revision=(state.reader().read() as FoundationResult.Success).value.revision
            assertTrue(state.mutate(StateCommand(revision,StateChange.Project(CurrentProject("phase3","Phase 3")))) is FoundationResult.Success)
            assertTrue(state.recordInteraction(InteractionKind.USER_INPUT,revision+1) is FoundationResult.Success)
        }
        db().use { sql ->
            dropMemory(sql); sql.version=2
        }
        MemoryStore(context,temporal).use { assertEquals(MemoryStore.Start.INITIALIZED,it.open()) }
        db().use { sql ->
            assertEquals(4,sql.version)
            assertEquals("yuki-aster",scalar(sql,"SELECT self_id FROM identity_anchor"))
            assertEquals("phase3",scalar(sql,"SELECT project_id FROM yuki_state"))
            assertEquals("2",scalar(sql,"SELECT revision FROM yuki_state"))
            assertEquals("1",scalar(sql,"SELECT count(*) FROM state_interaction"))
            assertEquals("9",scalar(sql,"SELECT count(*) FROM personality_facet"))
            assertEquals("2",scalar(sql,"SELECT count(*) FROM continuity_migration_history"))
            assertEquals(MemorySchema.MIGRATION_ID,scalar(sql,"SELECT migration_id FROM continuity_migration_history WHERE to_version=3"))
        }
    }
    @Test fun migrationFailureRollsBackAndDoesNotReinitialize() {
        ContinuityStore(context).use { it.open() }
        db().use { sql ->
            dropMemory(sql); sql.execSQL("UPDATE identity_anchor SET self_id='corrupt'"); sql.version=2
        }
        MemoryStore(context,temporal).use { assertEquals(MemoryStore.Start.UNAVAILABLE,it.open()) }
        db().use { sql ->
            assertEquals(2,sql.version)
            assertEquals("corrupt",scalar(sql,"SELECT self_id FROM identity_anchor"))
            assertEquals("0",scalar(sql,"SELECT count(*) FROM continuity_migration_history"))
            sql.rawQuery("SELECT name FROM sqlite_master WHERE name='memory_metadata'",null).use { assertEquals(0,it.count) }
        }
    }
}
