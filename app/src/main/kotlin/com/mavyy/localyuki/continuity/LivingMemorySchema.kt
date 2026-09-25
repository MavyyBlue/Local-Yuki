package com.mavyy.localyuki.continuity

import android.database.sqlite.SQLiteDatabase
import com.mavyy.localyuki.foundation.contracts.FoundationResult

internal object LivingMemorySchema {
    const val MIGRATION_ID = "2026-09-25-living-memory-v1"
    fun create(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE living_memory_metadata (singleton INTEGER PRIMARY KEY CHECK(singleton=1), format_version INTEGER NOT NULL CHECK(format_version=1), opened INTEGER NOT NULL CHECK(opened IN (0,1)), coverage_complete INTEGER NOT NULL CHECK(coverage_complete IN (0,1)), coverage_cursor TEXT NOT NULL)")
        db.execSQL("INSERT INTO living_memory_metadata VALUES (1,1,0,0,'')")
        db.execSQL("CREATE TABLE living_memory_state (memory_id TEXT PRIMARY KEY NOT NULL REFERENCES durable_memory(memory_id), source_revision_id TEXT NOT NULL REFERENCES memory_revision(revision_id), state_revision INTEGER NOT NULL CHECK(state_revision>0), abstraction_level TEXT NOT NULL CHECK(abstraction_level IN ('DETAILED','COMPACT','SPARSE','TRACE')), importance INTEGER NOT NULL CHECK(importance BETWEEN 0 AND 100), reinforcement INTEGER NOT NULL CHECK(reinforcement BETWEEN 0 AND 100), recall_count INTEGER NOT NULL CHECK(recall_count BETWEEN 0 AND 2147483647), last_recalled_at TEXT, reconciled_at TEXT NOT NULL)")
        db.execSQL("CREATE TABLE living_memory_term (memory_id TEXT NOT NULL REFERENCES living_memory_state(memory_id), source_revision_id TEXT NOT NULL REFERENCES memory_revision(revision_id), term TEXT NOT NULL, rank INTEGER NOT NULL CHECK(rank BETWEEN 0 AND 47), PRIMARY KEY(memory_id,term), UNIQUE(memory_id,rank))")
        db.execSQL("CREATE INDEX idx_living_term_lookup ON living_memory_term(term,memory_id,source_revision_id)")
        db.execSQL("CREATE TABLE living_recall_event (event_id TEXT PRIMARY KEY NOT NULL, memory_id TEXT NOT NULL REFERENCES durable_memory(memory_id), source_revision_id TEXT NOT NULL REFERENCES memory_revision(revision_id), occurred_at TEXT NOT NULL, previous_state_revision INTEGER NOT NULL, resulting_state_revision INTEGER NOT NULL, fingerprint TEXT NOT NULL)")
        db.execSQL("CREATE INDEX idx_living_recall_history ON living_recall_event(memory_id,occurred_at,event_id)")
        db.execSQL("CREATE TRIGGER living_coverage_insert AFTER INSERT ON durable_memory BEGIN UPDATE living_memory_metadata SET coverage_complete=0,coverage_cursor='' WHERE singleton=1; END")
        db.execSQL("CREATE TRIGGER living_coverage_head AFTER UPDATE OF current_revision ON durable_memory BEGIN UPDATE living_memory_metadata SET coverage_complete=0,coverage_cursor='' WHERE singleton=1; END")
        for (op in listOf("UPDATE", "DELETE")) db.execSQL("CREATE TRIGGER immutable_living_event_${op.lowercase()} BEFORE $op ON living_recall_event BEGIN SELECT RAISE(ABORT,'immutable'); END")
    }
    object Migration : ContinuityMigration {
        override val id = MIGRATION_ID
        override val fromVersion = 3
        override val toVersion = 4
        override fun apply(db: SQLiteDatabase) {
            check(ContinuityMapper.read(db) is FoundationResult.Success) { "Invalid identity before living migration" }
            db.rawQuery("SELECT format_version FROM memory_metadata", null).use {
                check(it.moveToFirst() && it.getInt(0) == 1 && !it.moveToNext()) { "Invalid memory before living migration" }
            }
            create(db)
        }
    }
}
