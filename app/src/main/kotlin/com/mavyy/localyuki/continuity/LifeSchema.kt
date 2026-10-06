package com.mavyy.localyuki.continuity

import android.database.sqlite.SQLiteDatabase
import com.mavyy.localyuki.foundation.embodiment.CapabilityId

/** App-owned continuity; vectors are derived and rebuildable; receipts never carry Android authority. */
internal object LifeSchema {
    fun create(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE organ_manifest(id TEXT PRIMARY KEY NOT NULL, json TEXT NOT NULL)")
        db.execSQL("CREATE TABLE organ_receipt(id TEXT PRIMARY KEY NOT NULL,model_id TEXT NOT NULL REFERENCES organ_manifest(id),role TEXT NOT NULL,accepted INTEGER NOT NULL CHECK(accepted IN (0,1)),reason TEXT NOT NULL,created TEXT NOT NULL,manifest TEXT NOT NULL)")
        for(op in listOf("UPDATE","DELETE")) db.execSQL("CREATE TRIGGER immutable_organ_receipt_${op.lowercase()} BEFORE $op ON organ_receipt BEGIN SELECT RAISE(ABORT,'immutable'); END")
        db.execSQL("CREATE TABLE organ_role(role TEXT PRIMARY KEY NOT NULL, model_id TEXT NOT NULL REFERENCES organ_manifest(id))")
        db.execSQL("CREATE TABLE life_record(id TEXT PRIMARY KEY NOT NULL, kind TEXT NOT NULL, topic TEXT NOT NULL, content TEXT NOT NULL, evidence TEXT NOT NULL, created TEXT NOT NULL, previous_id TEXT REFERENCES life_record(id), resolved INTEGER NOT NULL DEFAULT 0 CHECK(resolved IN (0,1)))")
        db.execSQL("CREATE INDEX idx_life_kind_time ON life_record(kind,created,id)")
        db.execSQL("CREATE TABLE autonomy_policy(singleton INTEGER PRIMARY KEY CHECK(singleton=1),json TEXT NOT NULL)")
        db.execSQL("INSERT INTO autonomy_policy VALUES(1,'{\"version\":1,\"enabled\":false,\"notifications\":false,\"overlay\":false,\"quietStart\":22,\"quietEnd\":8,\"cooldownMinutes\":60,\"dailyLimit\":4,\"threshold\":70}')")
        db.execSQL("CREATE TABLE presence_delivery(id TEXT PRIMARY KEY NOT NULL,topic TEXT NOT NULL,created TEXT NOT NULL,channel TEXT NOT NULL,record_id TEXT NOT NULL REFERENCES life_record(id))")
        db.execSQL("CREATE TABLE semantic_vector(memory_id TEXT PRIMARY KEY NOT NULL,revision_id TEXT NOT NULL,model_hash TEXT NOT NULL,vector BLOB NOT NULL)")
        db.execSQL("CREATE TABLE body_signal(id TEXT PRIMARY KEY NOT NULL,kind TEXT NOT NULL,package_name TEXT,content TEXT NOT NULL,created TEXT NOT NULL)")
        db.execSQL("CREATE TABLE cognitive_plan(id TEXT PRIMARY KEY NOT NULL,capability TEXT NOT NULL,payload TEXT NOT NULL,target_package TEXT,due TEXT NOT NULL,evidence_id TEXT NOT NULL REFERENCES memory_evidence(evidence_id),status TEXT NOT NULL,result_evidence TEXT REFERENCES memory_evidence(evidence_id))")
        db.execSQL("CREATE TABLE executive_audit(id TEXT PRIMARY KEY NOT NULL,intent TEXT NOT NULL,capability TEXT NOT NULL,executor TEXT NOT NULL,grant_snapshot TEXT NOT NULL,result TEXT NOT NULL,evidence_id TEXT NOT NULL)")
    }
    object Migration: ContinuityMigration {
        override val id="2026-10-06-local-cognitive-life-v1"
        override val fromVersion=8;override val toVersion=9
        override fun apply(db: SQLiteDatabase) {
            create(db)
            CapabilityId.entries.forEach { db.execSQL("INSERT OR IGNORE INTO capability_policy VALUES(?,0,0)",arrayOf(it.name)) }
        }
    }
}
