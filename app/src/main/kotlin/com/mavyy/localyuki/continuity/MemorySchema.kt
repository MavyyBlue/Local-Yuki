package com.mavyy.localyuki.continuity

import android.database.sqlite.SQLiteDatabase
import com.mavyy.localyuki.foundation.contracts.FoundationResult

/** Additive, adjacent physical migration. Version-one creation is initialization. */
internal object MemorySchema {
    const val MIGRATION_ID = "2026-09-24-memory-authority-v1"
    fun create(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE memory_metadata (singleton INTEGER PRIMARY KEY CHECK(singleton=1), format_version INTEGER NOT NULL CHECK(format_version=1), opened INTEGER NOT NULL CHECK(opened IN (0,1)))")
        db.execSQL("INSERT INTO memory_metadata VALUES (1,1,0)")
        db.execSQL("CREATE TABLE memory_thread (thread_id TEXT PRIMARY KEY NOT NULL, self_id TEXT NOT NULL, relationship_id TEXT NOT NULL, created_at TEXT NOT NULL, zone_id TEXT NOT NULL)")
        db.execSQL("CREATE TABLE memory_evidence (evidence_id TEXT PRIMARY KEY NOT NULL, source_kind TEXT NOT NULL, thread_id TEXT REFERENCES memory_thread(thread_id), sequence INTEGER, captured_at TEXT NOT NULL, zone_id TEXT NOT NULL, payload_version INTEGER NOT NULL CHECK(payload_version=1), payload TEXT NOT NULL, digest TEXT NOT NULL, CHECK ((thread_id IS NULL AND sequence IS NULL) OR (thread_id IS NOT NULL AND sequence > 0)), UNIQUE(thread_id,sequence))")
        db.execSQL("CREATE INDEX idx_evidence_thread_sequence ON memory_evidence(thread_id,sequence)")
        db.execSQL("CREATE TABLE durable_memory (memory_id TEXT PRIMARY KEY NOT NULL, kind TEXT NOT NULL, created_at TEXT NOT NULL, current_revision TEXT NOT NULL)")
        db.execSQL("CREATE TABLE memory_revision (revision_id TEXT PRIMARY KEY NOT NULL, memory_id TEXT NOT NULL REFERENCES durable_memory(memory_id), revision_number INTEGER NOT NULL CHECK(revision_number>0), content TEXT NOT NULL, created_at TEXT NOT NULL, origin TEXT NOT NULL, supersedes TEXT REFERENCES memory_revision(revision_id), restored_from TEXT REFERENCES memory_revision(revision_id), UNIQUE(memory_id,revision_number), UNIQUE(supersedes))")
        db.execSQL("CREATE INDEX idx_revision_memory_number ON memory_revision(memory_id,revision_number)")
        db.execSQL("CREATE TABLE memory_provenance (revision_id TEXT NOT NULL REFERENCES memory_revision(revision_id), evidence_id TEXT NOT NULL REFERENCES memory_evidence(evidence_id), meaning TEXT NOT NULL, PRIMARY KEY(revision_id,evidence_id))")
        db.execSQL("CREATE INDEX idx_provenance_evidence ON memory_provenance(evidence_id)")
        db.execSQL("CREATE TABLE memory_audit (command_id TEXT PRIMARY KEY NOT NULL, memory_id TEXT NOT NULL REFERENCES durable_memory(memory_id), action TEXT NOT NULL, previous_revision TEXT, resulting_revision TEXT NOT NULL REFERENCES memory_revision(revision_id), restored_from TEXT, command_evidence TEXT REFERENCES memory_evidence(evidence_id), occurred_at TEXT NOT NULL, fingerprint TEXT NOT NULL)")
        db.execSQL("CREATE INDEX idx_audit_memory_time ON memory_audit(memory_id,occurred_at,command_id)")
        db.execSQL("CREATE INDEX idx_memory_current ON durable_memory(memory_id,current_revision)")
        db.execSQL("CREATE TABLE memory_checkpoint (checkpoint_id TEXT PRIMARY KEY NOT NULL, thread_id TEXT NOT NULL REFERENCES memory_thread(thread_id), start_sequence INTEGER NOT NULL CHECK(start_sequence>0), end_sequence INTEGER NOT NULL CHECK(end_sequence>=start_sequence), content TEXT NOT NULL, created_at TEXT NOT NULL, producer TEXT NOT NULL, supersedes TEXT REFERENCES memory_checkpoint(checkpoint_id) UNIQUE)")
        db.execSQL("CREATE INDEX idx_checkpoint_thread_time ON memory_checkpoint(thread_id,created_at,checkpoint_id)")
        for (table in listOf("memory_evidence", "memory_revision", "memory_provenance", "memory_audit")) {
            db.execSQL("CREATE TRIGGER immutable_${table}_update BEFORE UPDATE ON $table BEGIN SELECT RAISE(ABORT,'immutable'); END")
            db.execSQL("CREATE TRIGGER immutable_${table}_delete BEFORE DELETE ON $table BEGIN SELECT RAISE(ABORT,'immutable'); END")
        }
        db.execSQL("CREATE TRIGGER immutable_thread_update BEFORE UPDATE ON memory_thread BEGIN SELECT RAISE(ABORT,'immutable'); END")
        db.execSQL("CREATE TRIGGER immutable_thread_delete BEFORE DELETE ON memory_thread BEGIN SELECT RAISE(ABORT,'immutable'); END")
        db.execSQL("CREATE TRIGGER immutable_checkpoint_update BEFORE UPDATE ON memory_checkpoint BEGIN SELECT RAISE(ABORT,'immutable'); END")
        db.execSQL("CREATE TRIGGER immutable_checkpoint_delete BEFORE DELETE ON memory_checkpoint BEGIN SELECT RAISE(ABORT,'immutable'); END")
        db.execSQL("CREATE TRIGGER immutable_memory_identity BEFORE UPDATE OF memory_id,kind,created_at ON durable_memory BEGIN SELECT RAISE(ABORT,'immutable'); END")
        db.execSQL("CREATE TRIGGER memory_head_advance BEFORE UPDATE OF current_revision ON durable_memory BEGIN SELECT CASE WHEN NOT EXISTS (SELECT 1 FROM memory_revision next JOIN memory_revision prior ON prior.revision_id=OLD.current_revision WHERE next.revision_id=NEW.current_revision AND next.memory_id=OLD.memory_id AND next.supersedes=OLD.current_revision AND next.revision_number=prior.revision_number+1) THEN RAISE(ABORT,'invalid head') END; END")
        db.execSQL("CREATE TRIGGER immutable_memory_delete BEFORE DELETE ON durable_memory BEGIN SELECT RAISE(ABORT,'immutable'); END")
    }
    object Migration : ContinuityMigration {
        override val id = MIGRATION_ID
        override val fromVersion = 2
        override val toVersion = 3
        override fun apply(db: SQLiteDatabase) {
            check(ContinuityMapper.read(db) is FoundationResult.Success) { "Invalid identity before memory migration" }
            create(db)
        }
    }
}
