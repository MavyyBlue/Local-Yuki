package com.mavyy.localyuki.presence

import android.content.Context
import com.mavyy.localyuki.brain.BrainDatabase
import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.provenance.*
import com.mavyy.localyuki.foundation.memory.*
import org.json.JSONObject
import org.json.JSONArray
import java.time.*
import java.util.UUID

enum class LifeKind { CONCERN, INTENTION, TOPIC, CURIOSITY, PREFERENCE, OPINION, REFLECTION, SELF_STATE, RELATIONSHIP }
data class LifeRecord(val id:String,val kind:LifeKind,val topic:String,val content:String,val evidence:List<EvidenceRef>,val created:Instant,val previous:String?,val resolved:Boolean)
data class AutonomyPolicy(val enabled:Boolean=false,val notifications:Boolean=false,val overlay:Boolean=false,
    val quietStart:Int=22,val quietEnd:Int=8,val cooldownMinutes:Int=60,val dailyLimit:Int=4,val threshold:Int=70) {
    init { require(quietStart in 0..23 && quietEnd in 0..23 && cooldownMinutes in 15..1440 && dailyLimit in 1..12 && threshold in 1..100) }
    fun quiet(now:ZonedDateTime)=if(quietStart==quietEnd) false else if(quietStart<quietEnd) now.hour in quietStart until quietEnd else now.hour>=quietStart || now.hour<quietEnd
    fun json()=JSONObject().put("version",1).put("enabled",enabled).put("notifications",notifications).put("overlay",overlay)
        .put("quietStart",quietStart).put("quietEnd",quietEnd).put("cooldownMinutes",cooldownMinutes).put("dailyLimit",dailyLimit).put("threshold",threshold)
    companion object { fun parse(s:String):AutonomyPolicy { val j=JSONObject(s);require(j.getInt("version")==1)
        return AutonomyPolicy(j.getBoolean("enabled"),j.getBoolean("notifications"),j.getBoolean("overlay"),j.getInt("quietStart"),j.getInt("quietEnd"),j.getInt("cooldownMinutes"),j.getInt("dailyLimit"),j.getInt("threshold")) } }
}
/** Only structured conclusions/questions are durable; raw hidden model reasoning is never persisted. */
class LifeStore(context:Context,private val evidenceReader:MemoryReader):AutoCloseable {
    private val db=BrainDatabase(context)
    private fun refs(s:String):List<EvidenceRef> { val a=JSONArray(s);require(a.length()<=32);return (0 until a.length()).map { val j=a.getJSONObject(it);EvidenceRef(j.getString("id"),EvidenceSourceKind.valueOf(j.getString("kind"))) } }
    private fun record(r:List<String?>)=LifeRecord(r[0]!!,LifeKind.valueOf(r[1]!!),r[2]!!,r[3]!!,refs(r[4]!!),Instant.parse(r[5]),r[6],r[7]=="1")
    fun records(kind:LifeKind?=null,limit:Int=30):FoundationResult<List<LifeRecord>> = db.read { sql ->
        require(limit in 1..100)
        val rows=if(kind==null) db.rows(sql,"SELECT * FROM life_record ORDER BY created DESC,id DESC LIMIT ?",limit.toString())
          else db.rows(sql,"SELECT * FROM life_record WHERE kind=? ORDER BY created DESC,id DESC LIMIT ?",kind.name,limit.toString())
        rows.map(::record)
    }
    fun active():FoundationResult<List<LifeRecord>> = db.read { sql->db.rows(sql,"SELECT * FROM life_record WHERE resolved=0 AND kind IN ('CONCERN','INTENTION','TOPIC','CURIOSITY') ORDER BY COALESCE((SELECT MAX(r.created) FROM life_record r WHERE r.kind='REFLECTION' AND r.topic=life_record.topic),'') ASC,created ASC LIMIT 128").map(::record) }
    fun append(kind:LifeKind,topic:String,content:String,evidence:List<EvidenceRef>,now:Instant,previous:String?=null):FoundationResult<LifeRecord> {
        if(!MemoryBounds.text(topic,128)||!MemoryBounds.text(content,2048)||evidence.size !in 1..32 || evidence.any { evidenceReader.evidence(it) !is FoundationResult.Success })
            return FoundationResult.Failure(FailureCategory.REJECTED)
        return db.write { sql ->
            if(previous!=null) { val r=db.rows(sql,"SELECT kind,topic FROM life_record WHERE id=?",previous).singleOrNull()
                require(r==listOf(kind.name,topic));require(db.rows(sql,"SELECT id FROM life_record WHERE previous_id=?",previous).isEmpty()) }
            require(db.rows(sql,"SELECT id FROM life_record WHERE resolved=0 AND kind IN ('CONCERN','INTENTION','TOPIC','CURIOSITY') LIMIT 129").size<128 || kind !in setOf(LifeKind.CONCERN,LifeKind.INTENTION,LifeKind.TOPIC,LifeKind.CURIOSITY))
            if(previous!=null)sql.execSQL("UPDATE life_record SET resolved=1 WHERE id=?",arrayOf(previous))
            val id="life-${UUID.randomUUID()}";val refs=JSONArray();evidence.forEach { refs.put(JSONObject().put("id",it.opaqueId).put("kind",it.sourceKind.name)) }
            sql.execSQL("INSERT INTO life_record VALUES(?,?,?,?,?,?,?,0)",arrayOf(id,kind.name,topic,content,refs.toString(),now.toString(),previous))
            LifeRecord(id,kind,topic,content,evidence.toList(),now,previous,false)
        }
    }
    fun resolve(id:String):FoundationResult<Boolean> = db.write { sql-> require(MemoryBounds.id(id));sql.execSQL("UPDATE life_record SET resolved=1 WHERE id=?",arrayOf(id));true }
    fun policy():FoundationResult<AutonomyPolicy> = db.read { AutonomyPolicy.parse(db.rows(it,"SELECT json FROM autonomy_policy WHERE singleton=1").single()[0]!!) }
    internal fun configure(policy:AutonomyPolicy):FoundationResult<Boolean> = db.write { it.execSQL("UPDATE autonomy_policy SET json=? WHERE singleton=1",arrayOf(policy.json().toString()));true }
    /** Reserve delivery in a transaction: restarts/concurrent events cannot bypass saturation. */
    fun reserveDelivery(record:LifeRecord,score:Int,channel:String,now:ZonedDateTime):FoundationResult<Boolean> = db.write { sql->
        require(score in 0..100 && channel in setOf("IN_APP","NOTIFICATION","OVERLAY"))
        val p=AutonomyPolicy.parse(db.rows(sql,"SELECT json FROM autonomy_policy").single()[0]!!)
        if(!p.enabled||p.quiet(now)||score<p.threshold||channel=="NOTIFICATION"&&!p.notifications||channel=="OVERLAY"&&!p.overlay) return@write false
        val last=db.rows(sql,"SELECT created FROM presence_delivery ORDER BY created DESC LIMIT 1").firstOrNull()?.first()?.let(Instant::parse)
        if(last!=null && Duration.between(last,now.toInstant()).seconds<p.cooldownMinutes*60L) return@write false
        val since=now.toLocalDate().atStartOfDay(now.zone).toInstant().toString()
        if(db.rows(sql,"SELECT id FROM presence_delivery WHERE created>=?",since).size>=p.dailyLimit) return@write false
        if(db.rows(sql,"SELECT id FROM presence_delivery WHERE topic=? AND created>=?",record.topic,now.minusHours(24).toInstant().toString()).isNotEmpty()) return@write false
        sql.execSQL("INSERT INTO presence_delivery VALUES(?,?,?,?,?)",arrayOf("delivery-${UUID.randomUUID()}",record.topic,now.toInstant().toString(),channel,record.id));true
    }
    override fun close()=db.close()
}
