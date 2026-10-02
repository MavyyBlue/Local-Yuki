package com.mavyy.localyuki.admission

import android.content.Context
import com.mavyy.localyuki.brain.BrainDatabase
import com.mavyy.localyuki.foundation.admission.*
import com.mavyy.localyuki.foundation.contracts.*
import java.time.Instant

/** Continuity stores role/hash receipts, never model weights as identity. */
class ModelCandidateStore(context: Context): AutoCloseable {
    private val store=BrainDatabase(context)
    fun candidates(): FoundationResult<List<ModelDescriptor>> = store.read { db ->
        store.rows(db,"SELECT model_id,role,sha256,file_bytes,format,container_version FROM model_candidate ORDER BY model_id LIMIT 33").map {
            if(it.take(5).any { value -> value==null }) throw BrainDatabase.Conflict()
            ModelDescriptor(it[0]!!,ModelRole.valueOf(it[1]!!),it[2]!!,it[3]!!.toLong(),ModelFormat.valueOf(it[4]!!),it[5]?.toInt())
        }.also { if(it.size>32) throw BrainDatabase.Conflict() }
    }
    internal fun register(model: ModelDescriptor,now: Instant): FoundationResult<Boolean> = store.write { db ->
        if(store.rows(db,"SELECT COUNT(*) FROM model_candidate").single()[0]!!.toInt()>=32) throw BrainDatabase.Conflict()
        db.execSQL("INSERT INTO model_candidate VALUES (?,?,?,?,?,?,?)",arrayOf<Any?>(model.id,model.role.name,model.sha256,model.fileBytes,model.format.name,model.containerVersion,now.toString()));true
    }
    internal fun receipt(id: String,receipt: AdmissionReceipt,now: Instant): FoundationResult<Boolean> = store.write { db ->
        val row=store.rows(db,"SELECT sha256 FROM model_candidate WHERE model_id=?",receipt.model.id).singleOrNull()
        if(row?.get(0)!=receipt.model.sha256 || !com.mavyy.localyuki.foundation.memory.MemoryBounds.id(id) || receipt.reason.length>512) throw BrainDatabase.Conflict()
        db.execSQL("INSERT INTO model_admission VALUES (?,?,?,?,?,?)",arrayOf<Any?>(id,receipt.model.id,receipt.adapterId,if(receipt.accepted) 1 else 0,receipt.reason,now.toString()));true
    }
    override fun close()=store.close()
}
