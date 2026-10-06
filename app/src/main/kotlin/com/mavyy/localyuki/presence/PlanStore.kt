package com.mavyy.localyuki.presence
import android.content.Context
import com.mavyy.localyuki.brain.BrainDatabase
import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.embodiment.*
import com.mavyy.localyuki.foundation.memory.MemoryReader
import com.mavyy.localyuki.foundation.provenance.*
import java.time.Instant
import java.util.UUID

data class DeferredPlan(val intent:ActionIntent,val due:Instant,val evidence:EvidenceRef,val status:String)
/** Accepted advisory plans, independent of model residency. Interrupted dispatches require reconciliation. */
class PlanStore(context:Context,private val memory:MemoryReader):AutoCloseable {
 private val db=BrainDatabase(context)
 internal fun enqueue(intent:ActionIntent,due:Instant,evidence:EvidenceRef,now:Instant):FoundationResult<String> {
  if(evidence.sourceKind!=EvidenceSourceKind.USER_INPUT || memory.evidence(evidence) !is FoundationResult.Success || due<=now || due>now.plusSeconds(30*86400L))return FoundationResult.Failure(FailureCategory.REJECTED)
  return db.write { sql->require(db.rows(sql,"SELECT id FROM cognitive_plan WHERE status='PENDING' LIMIT 65").size<64)
   sql.execSQL("INSERT INTO cognitive_plan VALUES(?,?,?,?,?,?,?,?)",arrayOf(intent.id,intent.capability.name,intent.payload,intent.targetPackage,due.toString(),evidence.opaqueId,"PENDING",null));intent.id }
 }
 fun list():FoundationResult<List<DeferredPlan>> = db.read { sql->db.rows(sql,"SELECT * FROM cognitive_plan ORDER BY CASE status WHEN 'PENDING' THEN 0 ELSE 1 END,due,id LIMIT 100").map { r->
  DeferredPlan(ActionIntent(r[0]!!,CapabilityId.valueOf(r[1]!!),r[2]!!,r[3]),Instant.parse(r[4]),EvidenceRef(r[5]!!,EvidenceSourceKind.USER_INPUT),r[6]!!) } }
 internal fun claim(id:String):FoundationResult<Boolean> = db.write { sql->
  val state=db.rows(sql,"SELECT status FROM cognitive_plan WHERE id=?",id).singleOrNull()?.first()
  if(state!="PENDING")return@write false
  sql.execSQL("UPDATE cognitive_plan SET status='RUNNING' WHERE id=?",arrayOf(id));true }
 internal fun finish(id:String,status:String,evidence:EvidenceRef?=null):FoundationResult<Boolean> = db.write { sql->
  require(status in setOf("SUCCEEDED","FAILED","BLOCKED","UNKNOWN","CANCELLED"));sql.execSQL("UPDATE cognitive_plan SET status=?,result_evidence=? WHERE id=?",arrayOf(status,evidence?.opaqueId,id));true }
 internal fun recover():FoundationResult<Boolean> = db.write { it.execSQL("UPDATE cognitive_plan SET status='UNKNOWN' WHERE status='RUNNING'");true }
 fun cancel(id:String):FoundationResult<Boolean> = finish(id,"CANCELLED")
 override fun close()=db.close()
}
