package com.mavyy.localyuki.vault

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import com.mavyy.localyuki.continuity.*
import com.mavyy.localyuki.brain.BrainRuntime
import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.memory.*
import com.mavyy.localyuki.foundation.provenance.*
import com.mavyy.localyuki.memory.MemoryStore
import com.mavyy.localyuki.state.deviceTemporalGrounding
import java.io.*
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Owner-only encrypted continuity. Caller must serialize and close app stores before restore. */
class ContinuityVault(context: Context) {
    private val app=context.applicationContext
    companion object {
        const val MAX_BYTES=64L*1024*1024
        private const val MAX_ROWS=200_000
        private val MAGIC="YUKIVAULT1".toByteArray(Charsets.US_ASCII)
        private const val HEADER_BYTES=10+16+12+4
    }
    private fun cipher(mode: Int,password: CharArray,salt: ByteArray,nonce: ByteArray,header: ByteArray): Cipher {
        require(password.size in 12..256)
        val spec=PBEKeySpec(password,salt,120_000,256)
        val material=try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
        return try { Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode,SecretKeySpec(material,"AES"),GCMParameterSpec(128,nonce));updateAAD(header)
        } } finally { material.fill(0) }
    }
    fun export(output: OutputStream,password: CharArray): FoundationResult<Long> = try {
        val source=app.getDatabasePath(ContinuitySchema.NAME)
        require(source.isFile && source.length() in 1..MAX_BYTES)
        val salt=ByteArray(16);val nonce=ByteArray(12);SecureRandom().apply { nextBytes(salt);nextBytes(nonce) }
        val header=MAGIC+salt+nonce+ByteBuffer.allocate(4).putInt(ContinuitySchema.VERSION).array()
        val crypto=cipher(Cipher.ENCRYPT_MODE,password,salt,nonce,header)
        SQLiteDatabase.openDatabase(source.path,null,SQLiteDatabase.OPEN_READWRITE).use { db ->
            check(!db.isWriteAheadLoggingEnabled) { "Quiesce WAL before export" }
            db.beginTransaction()
            try {
                check(db.version==ContinuitySchema.VERSION && ContinuityMapper.read(db) is FoundationResult.Success)
                var count=0L
                for(table in listOf("memory_evidence","memory_revision","durable_memory","memory_audit","memory_checkpoint"))
                    db.rawQuery("SELECT COUNT(*) FROM $table",null).use { it.moveToFirst();count+=it.getLong(0) }
                check(count<=MAX_ROWS)
                output.use { target ->
                    target.write(header)
                    var total=0L
                    source.inputStream().use { input ->
                        val buffer=ByteArray(64*1024)
                        while(true) { val count=input.read(buffer);if(count<0) break;total+=count;require(total<=MAX_BYTES)
                            crypto.update(buffer,0,count)?.let(target::write) }
                    }
                    target.write(crypto.doFinal());target.flush()
                    db.setTransactionSuccessful()
                    FoundationResult.Success(total)
                }
            } finally { db.endTransaction() }
        }
    } catch (_: IllegalArgumentException) { FoundationResult.Failure(FailureCategory.INVALID_INPUT) }
      catch (_: Exception) { FoundationResult.Failure(FailureCategory.CONFLICT) }

    private class CandidateContext(base: Context,private val file: File): ContextWrapper(base) {
        override fun getApplicationContext(): Context=this
        override fun getDatabasePath(name: String): File=file
    }
    private fun schema(db: SQLiteDatabase): Map<String,Pair<String,String?>> = db.rawQuery(
        "SELECT name,type,sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' ORDER BY name",null).use { c ->
        buildMap { while(c.moveToNext()) put(c.getString(0),c.getString(1) to c.getString(2)) }
    }
    private fun migrate(file:File,declaredVersion:Int) {
        SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READONLY).use { require(it.version==declaredVersion && declaredVersion in 1..ContinuitySchema.VERSION) }
        val context=CandidateContext(app,file)
        ContinuityHelper(context,false).use { helper ->require(helper.writableDatabase.version==ContinuitySchema.VERSION) }
    }
    private fun validate(file: File) {
        val reference=File(app.cacheDir,"vault-schema-${java.util.UUID.randomUUID()}.db")
        try {
            SQLiteDatabase.openOrCreateDatabase(reference,null).use { expected ->
                ContinuitySchema.create(expected)
                SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READONLY).use { candidate ->
                    check(candidate.version==ContinuitySchema.VERSION && schema(candidate)==schema(expected))
                    candidate.rawQuery("PRAGMA quick_check",null).use { check(it.moveToFirst() && it.getString(0)=="ok" && !it.moveToNext()) }
                    candidate.rawQuery("PRAGMA foreign_key_check",null).use { check(it.count==0) }
                    check(ContinuityMapper.read(candidate) is FoundationResult.Success)
                    var count=0L
                    for(table in listOf("memory_evidence","memory_revision","durable_memory","memory_audit","memory_checkpoint"))
                        candidate.rawQuery("SELECT COUNT(*) FROM $table",null).use { it.moveToFirst();count+=it.getLong(0) }
                    check(count<=MAX_ROWS)
                }
            }
            val context=CandidateContext(app,file)
            BrainRuntime(context).use { check(it.open()) }
            MemoryStore(context,deviceTemporalGrounding()).use { memory ->
                var cursor: String?=null
                do {
                    val result=memory.currentMemories(cursor,100);check(result is FoundationResult.Success)
                    result.value.forEach { item ->
                        var after=0L
                        do {
                            val history=memory.history(item.id,after,100);check(history is FoundationResult.Success)
                            after=history.value.lastOrNull()?.number ?: after
                        } while(history.value.size==100)
                    }
                    cursor=result.value.lastOrNull()?.id ?: cursor
                } while(result.value.size==100)
                SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READONLY).use { db ->
                    db.rawQuery("SELECT id,kind,topic,content,evidence,created,previous_id FROM life_record",null).use { rows ->
                        var total=0
                        while(rows.moveToNext()) {
                            require(++total<=MAX_ROWS);val id=rows.getString(0);require(MemoryBounds.id(id))
                            com.mavyy.localyuki.presence.LifeKind.valueOf(rows.getString(1));require(MemoryBounds.text(rows.getString(2),128) && MemoryBounds.text(rows.getString(3),2048));java.time.Instant.parse(rows.getString(5))
                            val evidence=org.json.JSONArray(rows.getString(4));require(evidence.length() in 1..32)
                            for(i in 0 until evidence.length()){val e=evidence.getJSONObject(i);check(memory.getEvidence(EvidenceRef(e.getString("id"),EvidenceSourceKind.valueOf(e.getString("kind")))) is FoundationResult.Success)}
                            if(!rows.isNull(6))db.rawQuery("SELECT kind,topic,created FROM life_record WHERE id=?",arrayOf(rows.getString(6))).use { prior ->
                                require(prior.moveToFirst() && prior.getString(0)==rows.getString(1) && prior.getString(1)==rows.getString(2) && java.time.Instant.parse(prior.getString(2))<=java.time.Instant.parse(rows.getString(5))) }
                        }
                    }
                    db.rawQuery("SELECT previous_id,COUNT(*) FROM life_record WHERE previous_id IS NOT NULL GROUP BY previous_id HAVING COUNT(*)>1",null).use { require(it.count==0) }
                    db.rawQuery("SELECT json FROM autonomy_policy",null).use { require(it.moveToFirst());com.mavyy.localyuki.presence.AutonomyPolicy.parse(it.getString(0));require(!it.moveToNext()) }
                    db.rawQuery("SELECT id,json FROM organ_manifest",null).use { rows ->while(rows.moveToNext()) {
                        val j=org.json.JSONObject(rows.getString(1));require(j.getInt("version")==1 && j.getString("id")==rows.getString(0) && j.getString("sha256").matches(Regex("[0-9a-f]{64}")))
                        require(j.getLong("fileBytes") in 1..com.mavyy.localyuki.admission.ModelFiles.MAX_BYTES);com.mavyy.localyuki.foundation.admission.ModelRole.valueOf(j.getString("role"))
                    } }
                    db.rawQuery("SELECT evidence_id,source_kind FROM memory_evidence",null).use { c ->
                        while(c.moveToNext()) check(memory.getEvidence(EvidenceRef(c.getString(0),EvidenceSourceKind.valueOf(c.getString(1)))) is FoundationResult.Success)
                    }
                }
            }
        } finally { SQLiteDatabase.deleteDatabase(reference) }
    }
    fun restore(input: InputStream,password: CharArray): FoundationResult<Boolean> {
        val target=app.getDatabasePath(ContinuitySchema.NAME)
        target.parentFile?.mkdirs()
        val candidate=File(target.parentFile,"vault-candidate-${java.util.UUID.randomUUID()}.db")
        return try {
            var declaredVersion=0
            input.use { source ->
                val header=ByteArray(HEADER_BYTES)
                DataInputStream(source).readFully(header)
                require(header.copyOfRange(0,10).contentEquals(MAGIC) && ByteBuffer.wrap(header,38,4).int in 1..ContinuitySchema.VERSION)
                declaredVersion=ByteBuffer.wrap(header,38,4).int
                val crypto=cipher(Cipher.DECRYPT_MODE,password,header.copyOfRange(10,26),header.copyOfRange(26,38),header)
                candidate.outputStream().use { output ->
                    val buffer=ByteArray(64*1024);var read=0L;var written=0L
                    while(true) { val count=source.read(buffer);if(count<0) break;read+=count;require(read<=MAX_BYTES+16)
                        crypto.update(buffer,0,count)?.let { written+=it.size;require(written<=MAX_BYTES);output.write(it) } }
                    val last=crypto.doFinal();written+=last.size;require(written in 1..MAX_BYTES);output.write(last);output.fd.sync()
                }
            }
            migrate(candidate,declaredVersion)
            validate(candidate)
            SQLiteDatabase.openDatabase(candidate.path,null,SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.beginTransaction()
                try {
                    // Admission is body-specific. Preserve receipts/role metadata, never activate old weights or grants.
                    db.execSQL("DELETE FROM organ_role")
                    db.execSQL("DELETE FROM semantic_vector")
                    db.execSQL("DELETE FROM body_signal")
                    db.execSQL("UPDATE capability_policy SET enabled=0,revision=revision+1 WHERE capability_id!='LOCAL_NOTE'")
                    db.rawQuery("SELECT id,json FROM organ_manifest",null).use { rows->
                        val manifests=mutableListOf<Pair<String,String>>()
                        while(rows.moveToNext()) { val j=org.json.JSONObject(rows.getString(1));j.put("status","REQUIRES_READMISSION");manifests+=rows.getString(0) to j.toString() }
                        manifests.forEach { db.execSQL("UPDATE organ_manifest SET json=? WHERE id=?",arrayOf(it.second,it.first)) }
                    }
                    val policy=db.rawQuery("SELECT json FROM autonomy_policy",null).use { it.moveToFirst();org.json.JSONObject(it.getString(0)) }
                    policy.put("enabled",false);db.execSQL("UPDATE autonomy_policy SET json=?",arrayOf(policy.toString()))
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
            }
            // Preserve original bytes on all failed restores; retain one owner recovery backup on success.
            check(listOf("-wal","-journal").all { !File(target.path+it).exists() || File(target.path+it).length()==0L })
            if(target.exists()) {
                val backup=File(target.parentFile,"continuity.before-restore.db")
                target.inputStream().use { source ->backup.outputStream().use { out ->source.copyTo(out);out.fd.sync() } }
            }
            Files.move(candidate.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
            FoundationResult.Success(true)
        } catch (_: Exception) { FoundationResult.Failure(FailureCategory.REJECTED) }
          finally { SQLiteDatabase.deleteDatabase(candidate) }
    }
}
