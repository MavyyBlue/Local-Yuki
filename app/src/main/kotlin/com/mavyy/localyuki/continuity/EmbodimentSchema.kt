package com.mavyy.localyuki.continuity

import android.database.sqlite.SQLiteDatabase
import com.mavyy.localyuki.foundation.contracts.FoundationResult
import com.mavyy.localyuki.foundation.embodiment.CapabilityId

internal object EmbodimentSchema {
    fun create(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE capability_policy (capability_id TEXT PRIMARY KEY, enabled INTEGER NOT NULL CHECK(enabled IN (0,1)), revision INTEGER NOT NULL CHECK(revision>=0))")
        CapabilityId.entries.forEach { db.execSQL("INSERT INTO capability_policy VALUES (?,0,0)",arrayOf(it.name)) }
        db.execSQL("CREATE TABLE capability_package (capability_id TEXT NOT NULL REFERENCES capability_policy(capability_id), package_name TEXT NOT NULL, decision TEXT NOT NULL CHECK(decision IN ('ALLOW','DENY')), PRIMARY KEY(capability_id,package_name,decision))")
    }
    object Migration : ContinuityMigration {
        override val id="2026-10-02-embodiment-v1"
        override val fromVersion=5
        override val toVersion=6
        override fun apply(db: SQLiteDatabase) { check(ContinuityMapper.read(db) is FoundationResult.Success);create(db) }
    }
}
