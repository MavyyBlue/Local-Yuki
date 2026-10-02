package com.mavyy.localyuki.continuity

import android.database.sqlite.SQLiteDatabase
import com.mavyy.localyuki.foundation.contracts.FoundationResult

internal object AffectSchema {
    fun create(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE affect_state (singleton INTEGER PRIMARY KEY CHECK(singleton=1), format INTEGER NOT NULL CHECK(format=1), revision INTEGER NOT NULL CHECK(revision>=0), valence INTEGER NOT NULL CHECK(valence BETWEEN -100 AND 100), arousal INTEGER NOT NULL CHECK(arousal BETWEEN 0 AND 100), affiliation INTEGER NOT NULL CHECK(affiliation BETWEEN -100 AND 100), updated_at TEXT NOT NULL, opened INTEGER NOT NULL CHECK(opened IN (0,1)))")
        db.execSQL("INSERT INTO affect_state VALUES (1,1,0,20,15,40,'1970-01-01T00:00:00Z',0)")
        db.execSQL("CREATE TABLE affect_association (memory_id TEXT PRIMARY KEY REFERENCES durable_memory(memory_id), strength INTEGER NOT NULL CHECK(strength BETWEEN -100 AND 100), reinforcement_count INTEGER NOT NULL CHECK(reinforcement_count>0))")
        db.execSQL("CREATE TABLE affect_event (event_id TEXT PRIMARY KEY, fingerprint TEXT NOT NULL, resulting_revision INTEGER NOT NULL, kind TEXT NOT NULL, evidence_id TEXT NOT NULL REFERENCES memory_evidence(evidence_id), memory_id TEXT REFERENCES durable_memory(memory_id), source_revision_id TEXT REFERENCES memory_revision(revision_id), occurred_at TEXT NOT NULL)")
        for (op in listOf("UPDATE","DELETE")) db.execSQL("CREATE TRIGGER immutable_affect_event_${op.lowercase()} BEFORE $op ON affect_event BEGIN SELECT RAISE(ABORT,'immutable'); END")
    }
    object Migration : ContinuityMigration {
        override val id = "2026-10-02-affect-v1"
        override val fromVersion = 4
        override val toVersion = 5
        override fun apply(db: SQLiteDatabase) {
            check(ContinuityMapper.read(db) is FoundationResult.Success)
            create(db)
        }
    }
}
