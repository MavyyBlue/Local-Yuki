package com.mavyy.localyuki.continuity

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.mavyy.localyuki.foundation.cognition.CanonicalCognitiveIdentityReader
import com.mavyy.localyuki.foundation.contracts.FailureCategory
import com.mavyy.localyuki.foundation.contracts.FoundationResult
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ContinuityStoreTest {
    private lateinit var context: Context

    @Before fun clear() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(ContinuitySchema.NAME)
    }

    @After fun clean() { context.deleteDatabase(ContinuitySchema.NAME) }

    private fun database(): SQLiteDatabase = SQLiteDatabase.openDatabase(
        context.getDatabasePath(ContinuitySchema.NAME).path, null, SQLiteDatabase.OPEN_READWRITE)

    private fun assertConflict() {
        ContinuityStore(context).use { store ->
            val open = store.open()
            assertEquals(ContinuityStart.UNAVAILABLE, open.status)
            assertEquals(FoundationResult.Failure(FailureCategory.CONFLICT), open.reader.read())
        }
    }

    @Test fun freshBootstrapPersistsExactCertifiedContentAndRestoresAfterNewRuntime() {
        val expected = CanonicalCognitiveIdentityReader.read()
        ContinuityStore(context).use { first ->
            val initial = first.open()
            assertEquals(ContinuityStart.INITIALIZED, initial.status)
            assertEquals(expected, initial.reader.read())
        }
        database().use { db ->
            assertEquals(3, db.version)
            listOf("identity_anchor" to 1, "personality_capsule" to 1,
                "continuity_honesty_rule" to 4, "personality_facet" to 9,
                "continuity_migration_history" to 0).forEach { (table, count) ->
                db.rawQuery("SELECT count(*) FROM $table", null).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(count, cursor.getInt(0))
                }
            }
        }
        ContinuityStore(context).use { second ->
            val restored = second.open()
            assertEquals(ContinuityStart.RESTORED, restored.status)
            assertEquals(expected, restored.reader.read())
        }
    }

    @Test fun existingPartialStoreNeverBootstraps() {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(ContinuitySchema.NAME), null).use {
            ContinuitySchema.create(it)
            it.version = 1
        }
        assertConflict()
        database().use { db ->
            db.rawQuery("SELECT count(*) FROM identity_anchor", null).use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
        }
    }

    @Test fun existingVersionZeroFileNeverBootstraps() {
        context.getDatabasePath(ContinuitySchema.NAME).parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(ContinuitySchema.NAME), null).close()
        assertConflict()
    }

    @Test fun physicalSchemaRejectsDuplicateFacetIdsAndCategories() {
        ContinuityStore(context).use { it.open() }
        database().use { db ->
            assertThrows(android.database.sqlite.SQLiteException::class.java) {
                db.execSQL("INSERT INTO personality_facet VALUES ('temperament', 'EXTRA', 'duplicate id')")
            }
            assertThrows(android.database.sqlite.SQLiteException::class.java) {
                db.execSQL("INSERT INTO personality_facet VALUES ('other', 'TEMPERAMENT', 'duplicate category')")
            }
        }
    }

    @Test fun crossRecordAndIdentityConflictsFailClosed() {
        val statements = listOf(
            "UPDATE identity_anchor SET capsule_id = 'other'",
            "UPDATE identity_anchor SET capsule_version = 2",
            "UPDATE personality_capsule SET capsule_version = 2",
            "DELETE FROM continuity_honesty_rule WHERE rule = 'ACTION_GROUNDING'",
            "UPDATE continuity_honesty_rule SET rule = 'OTHER' WHERE rule = 'ACTION_GROUNDING'",
            "DELETE FROM personality_facet WHERE category = 'STABLE_DISLIKE'",
            "UPDATE personality_facet SET facet_id = 'other' WHERE category = 'STABLE_DISLIKE'",
            "UPDATE identity_anchor SET self_id = 'other'",
            "UPDATE identity_anchor SET relationship_id = 'other'",
            "UPDATE identity_anchor SET continuity_format = 2",
            "UPDATE personality_facet SET content = ' ' WHERE category = 'STABLE_DISLIKE'"
        )
        for (statement in statements) {
            context.deleteDatabase(ContinuitySchema.NAME)
            ContinuityStore(context).use { assertEquals(ContinuityStart.INITIALIZED, it.open().status) }
            database().use { it.execSQL(statement) }
            assertConflict()
        }
    }

    @Test fun schemaDowngradeIsRejectedWithoutDataLoss() {
        ContinuityStore(context).use { it.open() }
        database().use { it.version = 4 }
        assertConflict()
        database().use { db ->
            assertEquals(4, db.version)
            db.rawQuery("SELECT self_id FROM identity_anchor", null).use {
                assertTrue(it.moveToFirst())
                assertEquals("yuki-aster", it.getString(0))
            }
        }
    }

    @Test fun missingMigrationPathAndInvalidRegistryAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { ContinuityMigrations().path(1, 3) }
        assertThrows(IllegalArgumentException::class.java) { ContinuityMigrations().path(0, 1) }
        assertThrows(IllegalArgumentException::class.java) { ContinuityMigrations().path(2, 1) }
    }
}
