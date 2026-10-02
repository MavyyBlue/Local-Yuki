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
        private const val MAX_ROWS=20_000
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
            input.use { source ->
                val header=ByteArray(HEADER_BYTES)
                DataInputStream(source).readFully(header)
                require(header.copyOfRange(0,10).contentEquals(MAGIC) && ByteBuffer.wrap(header,38,4).int==ContinuitySchema.VERSION)
                val crypto=cipher(Cipher.DECRYPT_MODE,password,header.copyOfRange(10,26),header.copyOfRange(26,38),header)
                candidate.outputStream().use { output ->
                    val buffer=ByteArray(64*1024);var read=0L;var written=0L
                    while(true) { val count=source.read(buffer);if(count<0) break;read+=count;require(read<=MAX_BYTES+16)
                        crypto.update(buffer,0,count)?.let { written+=it.size;require(written<=MAX_BYTES);output.write(it) } }
                    val last=crypto.doFinal();written+=last.size;require(written in 1..MAX_BYTES);output.write(last);output.fd.sync()
                }
            }
            validate(candidate)
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
