package com.mavyy.localyuki.semantic

import android.content.Context
import com.mavyy.localyuki.brain.BrainDatabase
import com.mavyy.localyuki.admission.ModelSubsystem
import com.mavyy.localyuki.foundation.admission.ModelRole
import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.memory.*
import java.nio.*

/** Rebuildable, engine-hash-specific vectors. Original memory/evidence are resolved after ranking. */
class SemanticMemory(context:Context,private val models:ModelSubsystem,private val memory:MemoryReader,private val living:LivingMemoryReader):AutoCloseable {
    private val db=BrainDatabase(context)
    private fun embed(text:String):FloatArray {
        val v=models.infer(ModelRole.EMBEDDING,"",text.take(512)).vector?:error("No embedding")
        require(v.size in 1..8192 && v.all { it.isFinite() });val norm=v.sumOf { it.toDouble()*it };require(norm in 0.99..1.01);return v
    }
    /** Each call indexes at most one changed memory; never scans raw evidence into an LLM. */
    fun maintain():FoundationResult<Boolean> { return try {
        val hash=models.activeHash(ModelRole.EMBEDDING)?:return FoundationResult.Unavailable(UnavailableReason.DEPENDENCY_UNAVAILABLE)
        val missing=db.read { sql-> db.rows(sql,"SELECT d.memory_id,r.revision_id,r.content FROM durable_memory d JOIN memory_revision r ON r.revision_id=d.current_revision LEFT JOIN semantic_vector v ON v.memory_id=d.memory_id WHERE v.memory_id IS NULL OR v.revision_id!=r.revision_id OR v.model_hash!=? ORDER BY d.memory_id LIMIT 1",hash) }
        if(missing !is FoundationResult.Success)return FoundationResult.Failure(FailureCategory.CONFLICT)
        val row=missing.value.firstOrNull()?:return FoundationResult.Success(false)
        val current=memory.current(row[0]!!);require(current is FoundationResult.Success && current.value.current.id==row[1])
        val v=embed(current.value.current.content);val bytes=ByteBuffer.allocate(v.size*4).order(ByteOrder.LITTLE_ENDIAN);v.forEach(bytes::putFloat)
        val fresh=memory.current(row[0]!!);require(fresh is FoundationResult.Success && fresh.value.current.id==row[1] && models.activeHash(ModelRole.EMBEDDING)==hash)
        db.write { it.execSQL("INSERT OR REPLACE INTO semantic_vector VALUES(?,?,?,?)",arrayOf(row[0],row[1],hash,bytes.array()));true }
    } catch(_:Exception) { FoundationResult.Failure(FailureCategory.REJECTED) }
    }
    fun recall(query:RecallQuery):FoundationResult<RecallCandidateSet> = try {
        val hash=models.activeHash(ModelRole.EMBEDDING)?:error("No embedding model")
        val q=embed(query.text)
        require(models.activeHash(ModelRole.EMBEDDING)==hash) { "Embedding model changed" }
        resolveVectorRecall(query,hash,q)
    } catch(_:Exception){FoundationResult.Failure(FailureCategory.REJECTED)}
    /** Shared provenance resolver, independently exercised with normalized vector fixtures. */
    internal fun resolveVectorRecall(query:RecallQuery,hash:String,q:FloatArray):FoundationResult<RecallCandidateSet> { return try {
        require(hash.matches(Regex("[0-9a-f]{64}")) && q.size in 1..8192 && q.all(Float::isFinite))
        require(q.sumOf { it.toDouble()*it } in 0.99..1.01)
        val ranks=db.read { sql->sql.rawQuery("SELECT memory_id,revision_id,vector FROM semantic_vector WHERE model_hash=? ORDER BY memory_id LIMIT 2001",arrayOf(hash)).use { c->
            val ranked=mutableListOf<Triple<String,String,Int>>()
            while(c.moveToNext()) {
                val b=c.getBlob(2);require(b.size in 4..32768 && b.size%4==0);if(b.size!=q.size*4)continue
                val v=ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);var score=0.0;var norm=0.0
                q.forEach { x->val y=v.float;require(y.isFinite());score+=x*y;norm+=y*y };require(norm in 0.99..1.01)
                if(score>=0.35)ranked+=Triple(c.getString(0),c.getString(1),(score*100).toInt().coerceIn(0,100))
            }
            ranked.sortedWith(compareByDescending<Triple<String,String,Int>> { it.third }.thenBy { it.first }).take(query.limit)
        } }
        if(ranks !is FoundationResult.Success)return FoundationResult.Failure(FailureCategory.CONFLICT)
        val candidates=ranks.value.mapNotNull { (id,revision,score)->
            val original=memory.current(id);val surface=living.current(id)
            if(original is FoundationResult.Success && surface is FoundationResult.Success && original.value.current.id==revision &&
                surface.value.sourceRevisionId==revision && (query.kind==null || original.value.kind==query.kind) &&
                original.value.current.evidence.all { memory.evidence(it) is FoundationResult.Success })
                GroundedRecallCandidate(original.value,surface.value,score,emptyList(),RecallBasis.SEMANTIC) else null
        }
        FoundationResult.Success(RecallCandidateSet(candidates,IndexCoverage.PARTIAL))
    } catch(_:Exception){FoundationResult.Failure(FailureCategory.REJECTED)}
    }
    override fun close()=db.close()
}
