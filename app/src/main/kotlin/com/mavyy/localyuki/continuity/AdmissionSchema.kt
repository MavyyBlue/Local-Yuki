package com.mavyy.localyuki.continuity

import android.database.sqlite.SQLiteDatabase
import com.mavyy.localyuki.foundation.contracts.FoundationResult
internal object AdmissionSchema {
    fun create(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE model_candidate (model_id TEXT PRIMARY KEY, role TEXT NOT NULL, sha256 TEXT NOT NULL, file_bytes INTEGER NOT NULL CHECK(file_bytes>0), format TEXT NOT NULL, container_version INTEGER, imported_at TEXT NOT NULL)")
        db.execSQL("CREATE TABLE model_admission (receipt_id TEXT PRIMARY KEY, model_id TEXT NOT NULL REFERENCES model_candidate(model_id), adapter_id TEXT NOT NULL, accepted INTEGER NOT NULL CHECK(accepted IN (0,1)), reason TEXT NOT NULL, created_at TEXT NOT NULL)")
        for(op in listOf("UPDATE","DELETE")) db.execSQL("CREATE TRIGGER immutable_model_admission_${op.lowercase()} BEFORE $op ON model_admission BEGIN SELECT RAISE(ABORT,'immutable'); END")
    }
    object Migration : ContinuityMigration {
        override val id="2026-10-02-model-admission-v1"
        override val fromVersion=7
        override val toVersion=8
        override fun apply(db: SQLiteDatabase) { check(ContinuityMapper.read(db) is FoundationResult.Success);create(db) }
    }
}
