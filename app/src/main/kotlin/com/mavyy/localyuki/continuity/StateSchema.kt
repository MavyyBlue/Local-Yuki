package com.mavyy.localyuki.continuity

import android.database.sqlite.SQLiteDatabase
import com.mavyy.localyuki.foundation.contracts.FoundationResult

/** Additive physical schema; identity tables and their rows are untouched. */
internal object StateSchema {
    const val MIGRATION_ID = "2026-09-24-yuki-state-v1"
    fun create(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE yuki_state (singleton INTEGER PRIMARY KEY CHECK(singleton = 1), state_version INTEGER NOT NULL, revision INTEGER NOT NULL, project_id TEXT, project_label TEXT, focus_text TEXT, focus_expiry TEXT, updated_at TEXT NOT NULL, opened INTEGER NOT NULL CHECK(opened IN (0,1)))")
        db.execSQL("CREATE TABLE state_intention (item_id TEXT PRIMARY KEY NOT NULL, content TEXT NOT NULL, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, expires_at TEXT)")
        db.execSQL("CREATE TABLE state_topic (item_id TEXT PRIMARY KEY NOT NULL, content TEXT NOT NULL, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, expires_at TEXT)")
        db.execSQL("CREATE TABLE state_interaction (kind TEXT PRIMARY KEY NOT NULL, instant TEXT NOT NULL, zone_id TEXT NOT NULL, revision INTEGER NOT NULL)")
        db.execSQL("INSERT INTO yuki_state VALUES (1, 1, 0, NULL, NULL, NULL, NULL, '1970-01-01T00:00:00Z', 0)")
    }
    object Migration : ContinuityMigration {
        override val id = MIGRATION_ID
        override val fromVersion = 1
        override val toVersion = 2
        override fun apply(db: SQLiteDatabase) {
            check(ContinuityMapper.read(db) is FoundationResult.Success) { "Invalid v1 identity" }
            create(db)
        }
    }
}
