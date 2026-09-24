package com.mavyy.localyuki.continuity

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import com.mavyy.localyuki.foundation.cognition.CognitiveIdentityProjector
import com.mavyy.localyuki.foundation.cognition.CognitiveIdentityReader
import com.mavyy.localyuki.foundation.cognition.CognitiveIdentityView
import com.mavyy.localyuki.foundation.contracts.FailureCategory
import com.mavyy.localyuki.foundation.contracts.FoundationResult
import com.mavyy.localyuki.foundation.contracts.UnavailableReason
import com.mavyy.localyuki.foundation.identity.*
import com.mavyy.localyuki.foundation.personality.*

internal object ContinuitySchema {
    const val VERSION = 1 // Physical schema, independent of ContinuityFormatVersion.
    const val NAME = "continuity.db"

    fun create(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE identity_anchor (singleton INTEGER PRIMARY KEY CHECK(singleton = 1), self_id TEXT NOT NULL, self_name TEXT NOT NULL, relationship_id TEXT NOT NULL, relationship_name TEXT NOT NULL, relationship_category TEXT NOT NULL, continuity_format INTEGER NOT NULL, capsule_id TEXT NOT NULL, capsule_version INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE continuity_honesty_rule (rule TEXT PRIMARY KEY NOT NULL)")
        db.execSQL("CREATE TABLE personality_capsule (singleton INTEGER PRIMARY KEY CHECK(singleton = 1), capsule_id TEXT NOT NULL, capsule_version INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE personality_facet (facet_id TEXT PRIMARY KEY NOT NULL, category TEXT NOT NULL UNIQUE, content TEXT NOT NULL)")
        db.execSQL("CREATE TABLE continuity_migration_history (migration_id TEXT PRIMARY KEY NOT NULL, from_version INTEGER NOT NULL, to_version INTEGER NOT NULL)")
    }
}

/** Real future migrations must register an explicit adjacent transition here. Version 1 is initialization. */
internal interface ContinuityMigration {
    val id: String
    val fromVersion: Int
    val toVersion: Int
    fun apply(db: SQLiteDatabase)
}

internal class ContinuityMigrations(private val steps: List<ContinuityMigration> = emptyList()) {
    fun path(from: Int, to: Int): List<ContinuityMigration> {
        require(from >= 1 && to >= from) { "Unsupported schema transition" }
        val ids = steps.map { it.id }
        require(ids.size == ids.toSet().size && ids.none { it.isBlank() }) { "Invalid migration registry" }
        val path = mutableListOf<ContinuityMigration>()
        for (version in from until to) {
            val candidates = steps.filter { it.fromVersion == version && it.toVersion == version + 1 }
            require(candidates.size == 1) { "Missing or ambiguous migration path" }
            path += candidates.single()
        }
        return path
    }

    /** Called inside SQLiteOpenHelper's upgrade transaction. */
    fun apply(db: SQLiteDatabase, from: Int, to: Int) {
        path(from, to).forEach { step ->
            step.apply(db)
            db.execSQL("INSERT INTO continuity_migration_history(migration_id, from_version, to_version) VALUES (?, ?, ?)",
                arrayOf(step.id, step.fromVersion, step.toVersion))
        }
        check(ContinuityMapper.read(db) is FoundationResult.Success) { "Migration result invalid" }
    }
}

private class ContinuityHelper(context: Context, private val newInstall: Boolean) :
    SQLiteOpenHelper(context, ContinuitySchema.NAME, null, ContinuitySchema.VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        // A preexisting version-zero or damaged file is not an install.
        check(newInstall) { "Existing continuity has no valid schema" }
        ContinuitySchema.create(db)
        val identity = CanonicalIdentitySeed.snapshot()
        val capsule = CanonicalPersonalityCapsule.snapshot()
        db.execSQL("INSERT INTO identity_anchor VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?)", arrayOf(
            identity.self.stableId, identity.self.canonicalName, identity.primaryRelationship.stableId,
            identity.primaryRelationship.displayName, identity.primaryRelationship.category.name,
            identity.continuityFormatVersion.value, identity.personalityCapsuleRef.stableId,
            identity.personalityCapsuleRef.version.value))
        identity.honesty.rules.forEach { db.execSQL("INSERT INTO continuity_honesty_rule VALUES (?)", arrayOf(it.name)) }
        db.execSQL("INSERT INTO personality_capsule VALUES (1, ?, ?)", arrayOf(capsule.id, capsule.version.value))
        capsule.facets.forEach { facet ->
            db.execSQL("INSERT INTO personality_facet VALUES (?, ?, ?)",
                arrayOf(facet.stableId, facet.category.name, facet.canonicalContent))
        }
        check(ContinuityMapper.read(db) is FoundationResult.Success) { "Bootstrap result invalid" }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        ContinuityMigrations().apply(db, oldVersion, newVersion)
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw IllegalStateException("Unsupported continuity schema version")
    }
}

/** SQL records are discarded before any cognition-facing view is returned. */
internal object ContinuityMapper {
    private fun rows(db: SQLiteDatabase, sql: String): List<List<String?>> =
        db.rawQuery(sql, null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add((0 until cursor.columnCount).map { cursor.getString(it) })
            }
        }

    fun read(db: SQLiteDatabase): FoundationResult<CognitiveIdentityView> {
        return try {
            val anchors = rows(db, "SELECT self_id, self_name, relationship_id, relationship_name, relationship_category, continuity_format, capsule_id, capsule_version FROM identity_anchor")
            val capsules = rows(db, "SELECT capsule_id, capsule_version FROM personality_capsule")
            val rules = rows(db, "SELECT rule FROM continuity_honesty_rule")
            val facets = rows(db, "SELECT facet_id, category, content FROM personality_facet")
            require(anchors.size == 1 && capsules.size == 1)
            require(rules.size == HonestyRule.entries.size && facets.size == FacetCategory.entries.size)
            val a = anchors.single()
            val c = capsules.single()
            val honesty = HonestyPolicy(rules.map { HonestyRule.valueOf(requireNotNull(it.single())) }.toSet())
            val identity = IdentityContinuitySnapshot(
                SelfIdentityAnchor(requireNotNull(a[0]), requireNotNull(a[1])),
                RelationshipAnchor(requireNotNull(a[2]), requireNotNull(a[3]),
                    RelationshipCategory.valueOf(requireNotNull(a[4]))), honesty,
                ContinuityFormatVersion(requireNotNull(a[5]).toInt()),
                PersonalityCapsuleRef(requireNotNull(a[6]), PersonalityCapsuleVersion(requireNotNull(a[7]).toInt())))
            val capsule = PersonalityCapsule(requireNotNull(c[0]), PersonalityCapsuleVersion(requireNotNull(c[1]).toInt()),
                honesty, facets.map { PersonalityFacet(requireNotNull(it[0]),
                    FacetCategory.valueOf(requireNotNull(it[1])), requireNotNull(it[2])) })
            CognitiveIdentityProjector.project(identity, capsule)
        } catch (_: IllegalArgumentException) {
            FoundationResult.Failure(FailureCategory.CONFLICT)
        } catch (_: SQLiteException) {
            FoundationResult.Failure(FailureCategory.CONFLICT)
        }
    }
}

enum class ContinuityStart { INITIALIZED, RESTORED, UNAVAILABLE }

data class ContinuityOpen(val status: ContinuityStart, val reader: CognitiveIdentityReader)

/** A new store object always opens and validates the durable file; no process-static identity cache. */
class ContinuityStore(context: Context) : AutoCloseable {
    private val applicationContext = context.applicationContext
    private val wasPresent = applicationContext.getDatabasePath(ContinuitySchema.NAME).exists()
    private val helper = ContinuityHelper(applicationContext, !wasPresent)
    private var opened = false

    fun open(): ContinuityOpen {
        if (opened) return ContinuityOpen(ContinuityStart.RESTORED, reader())
        val result = reader().read()
        if (result is FoundationResult.Success) {
            opened = true
            return ContinuityOpen(if (wasPresent) ContinuityStart.RESTORED else ContinuityStart.INITIALIZED, reader())
        }
        return ContinuityOpen(ContinuityStart.UNAVAILABLE, CognitiveIdentityReader { result })
    }

    private fun reader(): CognitiveIdentityReader = CognitiveIdentityReader {
        try {
            ContinuityMapper.read(helper.readableDatabase)
        } catch (_: IllegalStateException) {
            FoundationResult.Failure(FailureCategory.CONFLICT)
        } catch (_: SQLiteException) {
            FoundationResult.Unavailable(UnavailableReason.DEPENDENCY_UNAVAILABLE)
        } catch (_: Exception) {
            FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE)
        }
    }

    override fun close() = helper.close()
}
