package com.mavyy.localyuki.state

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.mavyy.localyuki.continuity.*
import com.mavyy.localyuki.foundation.cognition.CanonicalCognitiveIdentityReader
import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.state.*
import com.mavyy.localyuki.foundation.temporal.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class YukiStateStoreTest {
    private fun removeMemorySchema(db: SQLiteDatabase) {
        listOf("memory_checkpoint", "memory_audit", "memory_provenance", "memory_revision",
            "durable_memory", "memory_evidence", "memory_thread", "memory_metadata").forEach { db.execSQL("DROP TABLE $it") }
    }
    private lateinit var context: Context
    private var instant = Instant.parse("2026-09-24T12:00:00Z")
    private var zone = ZoneId.of("America/Chicago")
    private val temporal get() = DeterministicTemporalGrounding(ClockSource { instant }, ZoneSource { zone })
    @Before fun setup() { context = RuntimeEnvironment.getApplication(); context.deleteDatabase(ContinuitySchema.NAME) }
    @After fun cleanup() { context.deleteDatabase(ContinuitySchema.NAME) }
    private fun db() = SQLiteDatabase.openDatabase(context.getDatabasePath(ContinuitySchema.NAME).path, null, SQLiteDatabase.OPEN_READWRITE)
    private fun state(store: YukiStateStore) = (store.reader().read() as FoundationResult.Success).value
    private fun change(store: YukiStateStore, action: StateChange): YukiStateSnapshot =
        (store.mutate(StateCommand(state(store).revision, action)) as FoundationResult.Success).value

    @Test fun roundTripConflictBoundsExpiryAndActualEventTime() {
        YukiStateStore(context, temporal).use { store ->
            assertEquals(YukiStateStore.Start.INITIALIZED, store.open().status)
            assertEquals(0, state(store).revision)
            change(store, StateChange.Project(CurrentProject("everthread", "Everthread")))
            change(store, StateChange.Focus(CurrentFocus("Implement Phase 3", instant.plusSeconds(60))))
            change(store, StateChange.PutIntention("next", "Review current code"))
            change(store, StateChange.PutTopic("open", "Resolve time semantics"))
            val prior = state(store)
            assertEquals(FoundationResult.Failure(FailureCategory.CONFLICT), store.mutate(StateCommand(0, StateChange.Project(null))))
            assertEquals(prior, state(store))
            assertEquals(FoundationResult.Failure(FailureCategory.INVALID_INPUT), store.mutate(StateCommand(prior.revision, StateChange.PutTopic(" ", "invalid"))))
            assertEquals(prior, state(store))
            assertEquals(FoundationResult.Failure(FailureCategory.INVALID_INPUT), store.mutate(StateCommand(prior.revision, StateChange.Focus(CurrentFocus("past", instant)))))
            val event = (store.recordInteraction(InteractionKind.USER_INPUT, prior.revision) as FoundationResult.Success).value
            assertEquals(instant, event.interactions.single().instant)
            assertEquals(zone, event.interactions.single().zoneId)
            assertEquals(prior.revision + 1, event.revision)
            instant = instant.plusSeconds(60)
            assertNull(state(store).focus)
            assertEquals(event.revision + 1, state(store).revision)
            assertEquals(1, state(store).intentions.size) // no invented TTL
        }
        zone = ZoneId.of("Asia/Tokyo")
        YukiStateStore(context, temporal).use { store ->
            assertEquals(YukiStateStore.Start.RESTORED, store.open().status)
            assertEquals("Everthread", state(store).project?.label)
            assertEquals("Review current code", state(store).intentions.single().text)
            assertEquals("Resolve time semantics", state(store).topics.single().text)
            assertEquals(ZoneId.of("America/Chicago"), state(store).interactions.single().zoneId)
            assertEquals(zone, (temporal.ground() as FoundationResult.Success).value.zoneId)
        }
    }

    @Test fun boundedCollectionsAndSemanticFailureIsolation() {
        ContinuityStore(context).use { assertEquals(ContinuityStart.INITIALIZED, it.open().status) }
        YukiStateStore(context, temporal).use { store ->
            store.open()
            repeat(YukiStateLimits.MAX_INTENTIONS) { change(store, StateChange.PutIntention("id$it", "entry")) }
            val old = state(store)
            assertEquals(FoundationResult.Failure(FailureCategory.INVALID_INPUT), store.mutate(StateCommand(old.revision, StateChange.PutIntention("overflow", "entry"))))
            assertEquals(old, state(store))
            assertEquals(FoundationResult.Failure(FailureCategory.INVALID_INPUT), store.mutate(StateCommand(old.revision, StateChange.Project(CurrentProject(null, "x".repeat(YukiStateLimits.PROJECT_LABEL + 1))))))
        }
        db().use { it.execSQL("UPDATE yuki_state SET state_version=77") }
        YukiStateStore(context, temporal).use { store ->
            assertEquals(YukiStateStore.Start.UNAVAILABLE, store.open().status)
            assertEquals(FoundationResult.Failure(FailureCategory.CONFLICT), store.reader().read())
        }
        ContinuityStore(context).use { assertEquals(CanonicalCognitiveIdentityReader.read(), it.open().reader.read()) }
        db().use { it.execSQL("UPDATE identity_anchor SET self_id='other'") }
        ContinuityStore(context).use { assertEquals(ContinuityStart.UNAVAILABLE, it.open().status) }
    }

    @Test fun corruptIdentityDoesNotReseedAndValidStateStillReads() {
        ContinuityStore(context).use { it.open() }
        YukiStateStore(context, temporal).use { it.open() }
        db().use { it.execSQL("UPDATE identity_anchor SET self_id='other'") }
        ContinuityStore(context).use { assertEquals(ContinuityStart.UNAVAILABLE, it.open().status) }
        YukiStateStore(context, temporal).use { store ->
            assertEquals(YukiStateStore.Start.RESTORED, store.open().status)
            assertEquals(0, state(store).revision)
        }
        db().use { it.rawQuery("SELECT self_id FROM identity_anchor", null).use { c -> c.moveToFirst(); assertEquals("other", c.getString(0)) } }
    }

    @Test fun migrationFailureRollsBackWithoutReseedingIdentity() {
        ContinuityStore(context).use { it.open() }
        db().use { db ->
            removeMemorySchema(db)
            listOf("state_interaction", "state_topic", "state_intention", "yuki_state").forEach { db.execSQL("DROP TABLE $it") }
            db.execSQL("UPDATE identity_anchor SET self_id='malformed'")
            db.version = 1
        }
        ContinuityStore(context).use { assertEquals(ContinuityStart.UNAVAILABLE, it.open().status) }
        db().use { db ->
            assertEquals(1, db.version)
            db.rawQuery("SELECT self_id FROM identity_anchor", null).use { c ->
                c.moveToFirst(); assertEquals("malformed", c.getString(0))
            }
            db.rawQuery("SELECT name FROM sqlite_master WHERE name='yuki_state'", null).use { c -> assertEquals(0, c.count) }
        }
    }

    @Test fun expiredItemsHaveExactBoundaryAndInvalidStateDoesNotSelfRepair() {
        YukiStateStore(context, temporal).use { store ->
            store.open()
            change(store, StateChange.PutIntention("due", "Do this", instant.plusSeconds(5)))
            change(store, StateChange.PutTopic("topic", "Check this", instant.plusSeconds(5)))
            instant = instant.plusSeconds(4)
            assertEquals(1, state(store).intentions.size)
            instant = instant.plusSeconds(1)
            val previousRevision = state(store).revision
            assertTrue(state(store).intentions.isEmpty())
            assertTrue(state(store).topics.isEmpty())
            assertEquals(previousRevision, state(store).revision)
        }
        db().use { it.execSQL("UPDATE yuki_state SET focus_text='corrupt', focus_expiry='not-an-instant'") }
        YukiStateStore(context, temporal).use { store ->
            assertEquals(YukiStateStore.Start.UNAVAILABLE, store.open().status)
            assertEquals(FoundationResult.Failure(FailureCategory.CONFLICT), store.reader().read())
        }
        db().use { it.rawQuery("SELECT focus_expiry FROM yuki_state", null).use { c -> c.moveToFirst(); assertEquals("not-an-instant", c.getString(0)) } }
    }

    @Test fun migrationPreservesIdentityRowsAndHistory() {
        ContinuityStore(context).use { it.open() }
        db().use { db ->
            removeMemorySchema(db)
            listOf("state_interaction", "state_topic", "state_intention", "yuki_state").forEach { db.execSQL("DROP TABLE $it") }
            db.version = 1
        }
        val expected = CanonicalCognitiveIdentityReader.read()
        ContinuityStore(context).use { assertEquals(expected, it.open().reader.read()) }
        YukiStateStore(context, temporal).use { assertEquals(YukiStateStore.Start.INITIALIZED, it.open().status) }
        db().use { db ->
            assertEquals(3, db.version)
            db.rawQuery("SELECT migration_id FROM continuity_migration_history ORDER BY migration_id", null).use { c ->
                assertEquals(2, c.count)
                c.moveToFirst(); assertEquals("2026-09-24-memory-authority-v1", c.getString(0))
                c.moveToNext(); assertEquals("2026-09-24-yuki-state-v1", c.getString(0))
            }
        }
    }
}
