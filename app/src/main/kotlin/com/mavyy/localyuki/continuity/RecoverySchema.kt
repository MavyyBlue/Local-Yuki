package com.mavyy.localyuki.continuity

import android.database.sqlite.SQLiteDatabase
import com.mavyy.localyuki.foundation.contracts.FoundationResult

internal object RecoverySchema {
    fun create(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE recovery_state (singleton INTEGER PRIMARY KEY CHECK(singleton=1), format INTEGER NOT NULL CHECK(format=1), revision INTEGER NOT NULL CHECK(revision>=0), mode TEXT NOT NULL CHECK(mode IN ('AWAKE','FATIGUED','PREPARING_SLEEP','SLEEPING','RECOVERING')), updated_at TEXT NOT NULL, maintenance_active INTEGER NOT NULL CHECK(maintenance_active IN (0,1)), opened INTEGER NOT NULL CHECK(opened IN (0,1)), maintenance_cursor TEXT NOT NULL)")
        db.execSQL("INSERT INTO recovery_state VALUES (1,1,0,'AWAKE','1970-01-01T00:00:00Z',0,0,'')")
        db.execSQL("CREATE TABLE recovery_intention (item_id TEXT PRIMARY KEY, content TEXT NOT NULL, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, expires_at TEXT)")
        db.execSQL("CREATE TABLE scheduler_state (singleton INTEGER PRIMARY KEY CHECK(singleton=1), format INTEGER NOT NULL CHECK(format=1), last_escalation TEXT, last_signal_id TEXT, events_seen INTEGER NOT NULL CHECK(events_seen BETWEEN 0 AND 2147483647))")
        db.execSQL("INSERT INTO scheduler_state VALUES (1,1,NULL,NULL,0)")
    }
    object Migration : ContinuityMigration {
        override val id="2026-10-02-recovery-scheduler-v1"
        override val fromVersion=6
        override val toVersion=7
        override fun apply(db: SQLiteDatabase) { check(ContinuityMapper.read(db) is FoundationResult.Success);create(db) }
    }
}
