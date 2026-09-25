package com.mavyy.localyuki.continuity

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.mavyy.localyuki.foundation.contracts.FoundationResult
import com.mavyy.localyuki.foundation.state.*
import com.mavyy.localyuki.foundation.temporal.*
import com.mavyy.localyuki.state.YukiStateStore
import com.mavyy.localyuki.memory.MemoryStore
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
        listOf("memory_checkpoint","memory_audit","memory_provenance","memory_revision","durable_memory",
            "memory_evidence","memory_thread","memory_metadata").forEach { sql.execSQL("DROP TABLE $it") }
    }
    private fun scalar(sql: SQLiteDatabase, query: String): String? = sql.rawQuery(query,null).use { it.moveToFirst(); it.getString(0) }
    @Test fun versionTwoToThreePreservesStateIdentityAndPersonality() {
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
            assertEquals(3,sql.version)
            assertEquals("yuki-aster",scalar(sql,"SELECT self_id FROM identity_anchor"))
            assertEquals("phase3",scalar(sql,"SELECT project_id FROM yuki_state"))
            assertEquals("2",scalar(sql,"SELECT revision FROM yuki_state"))
            assertEquals("1",scalar(sql,"SELECT count(*) FROM state_interaction"))
            assertEquals("9",scalar(sql,"SELECT count(*) FROM personality_facet"))
            assertEquals("1",scalar(sql,"SELECT count(*) FROM continuity_migration_history"))
            assertEquals(MemorySchema.MIGRATION_ID,scalar(sql,"SELECT migration_id FROM continuity_migration_history"))
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
