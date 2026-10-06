package com.mavyy.localyuki.admission

import android.content.Context
import com.mavyy.localyuki.brain.BrainDatabase
import com.mavyy.localyuki.foundation.admission.*
import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.resource.*
import com.mavyy.localyuki.inference.*
import com.mavyy.localyuki.resource.AndroidResourceProbe
import org.json.*
import java.io.*
import java.time.Instant
import java.util.UUID

internal interface OrganAdapter {
    val id:String;val version:Int
    fun supports(model:ModelDescriptor,metadata:GgufMetadata):Boolean
    fun execute(file:File,profile:SafeRuntimeProfile,system:String,user:String,embedding:Boolean,grammar:String,safe:()->Boolean,epoch:Long):OrganResult
}
internal class CpuGgufAdapter(context:Context):OrganAdapter {
    override val id="llama-cpp-cpu-b5046";override val version=3
    private val native=NativeSupervisor(context)
    override fun supports(model:ModelDescriptor,metadata:GgufMetadata)=model.format==ModelFormat.GGUF && metadata.parameters>0
    override fun execute(file:File,profile:SafeRuntimeProfile,system:String,user:String,embedding:Boolean,grammar:String,safe:()->Boolean,epoch:Long)=native.run(file,profile,system,user,embedding,grammar,safe,epoch)
}
data class OwnerModel(val id:String,val manifest:JSONObject,val activeRoles:Set<ModelRole>)
/** A shared file can have different measured limits for each independently admitted role. */
internal fun runtimeProfileForRole(current:SafeRuntimeProfile,receipt:JSONObject,remaining:Long,maxOutput:Int?=null):SafeRuntimeProfile =
    current.copy(deadlineMillis=minOf(current.deadlineMillis,remaining),context=minOf(current.context,receipt.getInt("context")),
        output=minOf(current.output,receipt.getInt("output"),maxOutput?:current.output),threads=minOf(current.threads,receipt.getInt("threads")),
        batch=minOf(current.batch,receipt.getInt("batch")))
/** Trusted model lifecycle. Every inference rechecks file integrity, receipt, actual body and a lease. */
class ModelSubsystem(context:Context,private val governor:ResourceGovernor):AutoCloseable {
    private val app=context.applicationContext
    private val root=File(app.filesDir,"model-organs").apply { mkdirs() }
    private val db=BrainDatabase(app);private val probe=AndroidResourceProbe(app)
    private val adapters:List<OrganAdapter> = listOf(CpuGgufAdapter(app))
    private data class TurnBudget(var remaining:Int,val until:Long,val epoch:Long)
    private val turnBudget=ThreadLocal<TurnBudget>()
    internal fun beginTurn() { observe();val p=governor.profile(Instant.now())?:error("Cognition deferred by body")
        turnBudget.set(TurnBudget(p.maxPasses,android.os.SystemClock.elapsedRealtime()+p.deadlineMillis,NativeSupervisor.cancellationEpoch())) }
    internal fun endTurn()=turnBudget.remove()
    internal fun checkTurnActive() { turnBudget.get()?.let { NativeSupervisor.requireActive(it.epoch) } }
    @Volatile var lastFailure:String="";private set
    @Volatile internal var lastInference:JSONObject?=null;private set
    internal fun clearInferenceDiagnostic() { lastInference=null }
    fun models():FoundationResult<List<OwnerModel>> = db.read { sql->
        val roles=db.rows(sql,"SELECT role,model_id FROM organ_role");db.rows(sql,"SELECT id,json FROM organ_manifest ORDER BY id").map { row ->
            OwnerModel(row[0]!!,JSONObject(row[1]!!),roles.filter { it[1]==row[0] }.map { ModelRole.valueOf(it[0]!!) }.toSet()) }
    }
    fun importFile(input:InputStream,role:ModelRole,now:Instant):FoundationResult<OwnerModel> {
        val count=(models() as? FoundationResult.Success)?.value?.count { !it.manifest.optBoolean("removed") } ?: run { input.close();return FoundationResult.Failure(FailureCategory.CONFLICT) }
        if(count>=32) {input.close();return FoundationResult.Failure(FailureCategory.REJECTED)}
        val id="model-${UUID.randomUUID()}";val file=File(root,id)
        val budget=minOf(ModelFiles.MAX_BYTES,root.usableSpace-128*ResourceGovernor.MIB)
        if(budget<8){input.close();return FoundationResult.Failure(FailureCategory.REJECTED)}
        val copied=ModelFiles.import(input,file,budget);if(copied !is FoundationResult.Success)return copied.asFailure()
        val inspected=ModelFiles.inspect(file,id,role);if(inspected !is FoundationResult.Success){file.delete();return inspected.asFailure()}
        val d=inspected.value
        val j=JSONObject().put("version",1).put("id",id).put("role",role.name).put("sha256",d.sha256).put("fileBytes",d.fileBytes)
            .put("format",d.format.name).put("containerVersion",d.containerVersion).put("importedAt",now.toString()).put("status","INSPECTED").put("acceptedRoles",JSONArray())
        try {
            val m=GgufInspector.inspect(file)
            j.put("name",m.name).put("architecture",m.architecture).put("parameters",m.parameters).put("quantization",m.quantization)
                .put("trainedContext",m.trainedContext).put("kvBytesPerToken",m.kvBytes(1)).put("estimatedMemory512",m.estimatedMemory(d.fileBytes,512))
                .put("backend","CPU").put("acceleration","No installed accelerated adapter; CPU execution must be measured")
        } catch(e:Exception) { j.put("status","INCOMPATIBLE").put("reason",e.message?.take(256)?:"GGUF metadata missing or invalid; no executable adapter for this format") }
        return db.write { sql-> sql.execSQL("INSERT INTO organ_manifest VALUES(?,?)",arrayOf(id,j.toString()));OwnerModel(id,j,emptySet()) }
    }
    private fun descriptor(model:OwnerModel,role:ModelRole)=ModelDescriptor(model.id,role,model.manifest.getString("sha256"),model.manifest.getLong("fileBytes"),ModelFormat.valueOf(model.manifest.getString("format")),model.manifest.optInt("containerVersion").takeIf { it>0 })
    private fun model(id:String):OwnerModel=(models() as? FoundationResult.Success)?.value?.singleOrNull { it.id==id } ?: error("model absent")
    private fun inspect(model:OwnerModel,role:ModelRole):Pair<ModelDescriptor,GgufMetadata> {
        require(!model.manifest.optBoolean("removed"))
        val expected=descriptor(model,role);val fresh=ModelFiles.inspect(File(root,model.id),model.id,role)
        check(fresh is FoundationResult.Success && fresh.value==expected) { "Model hash/size changed or weights are absent; reimport and readmit" }
        return expected to GgufInspector.inspect(File(root,model.id))
    }
    private fun observe():DeviceResources {
        val result=probe.read(com.mavyy.localyuki.resource.OwnerVisibility.active,Instant.now());check(result is FoundationResult.Success) { "Resource measurements unavailable" }
        governor.observe(result.value);return result.value
    }
    fun admit(id:String,role:ModelRole,now:Instant):FoundationResult<OwnerModel> {
        val admissionEpoch=NativeSupervisor.cancellationEpoch()
        var candidate:OwnerModel?=null;var accepted=false;var reason="admission failed";var measurement:OrganResult?=null;var benchmark:JSONObject?=null
        return try {
            val model=model(id);candidate=model
            val (d,m)=inspect(model,role);val adapter=adapters.firstOrNull { it.supports(d,m) }?:error("No executable adapter")
            require(role in setOf(ModelRole.SYSTEM_ONE,ModelRole.SYSTEM_TWO,ModelRole.LANGUAGE_EXPRESSION,ModelRole.EMBEDDING)) { "No executable adapter for this role" }
            require(com.mavyy.localyuki.resource.OwnerVisibility.active) { "Benchmark requires the visible owner surface" }
            val body=observe();val base=governor.profile(Instant.now())?:error("Unsafe resource state")
            require(m.trainedContext>=256) { "Trained context below runtime minimum" };val context=minOf(base.context,m.trainedContext,if(role==ModelRole.EMBEDDING)512 else 4096)
            val estimate=m.estimatedMemory(d.fileBytes,context);require(estimate<=base.memoryBudget) { "Model estimate exceeds measured phone budget" }
            val profile=base.copy(context=context,output=if(role==ModelRole.EMBEDDING)16 else minOf(base.output,256))
            benchmark=JSONObject().put("role",role.name).put("measuredAt",now.toString()).put("adapterVersion",adapter.version)
                .put("mode",profile.mode.name).put("deadlineMs",profile.deadlineMillis).put("context",profile.context)
                .put("requestedOutput",profile.output).put("threads",profile.threads).put("batch",profile.batch)
                .put("thermalBefore",body.thermal.name).put("accepted",false)
            model.manifest.put("lastBenchmark",benchmark)
            val benchmarks=model.manifest.optJSONObject("roleBenchmarks")?:JSONObject().also { model.manifest.put("roleBenchmarks",it) }
            benchmarks.put(role.name,benchmark)
            val sample=when(role) {
                ModelRole.SYSTEM_ONE->"Return JSON only: {\"route\":\"RESPOND\",\"confidence\":90,\"salience\":30,\"intent\":\"conversation\",\"affect\":\"neutral\",\"meaning\":[\"Hello, Mavyy. I am glad you are here.\"],\"uncertainty\":[],\"sources\":[\"input\"],\"updates\":[]}. The owner says hello."
                ModelRole.SYSTEM_TWO->"Return JSON only: {\"points\":[\"The evidence is insufficient to confirm this.\"],\"uncertainty\":[\"Unverified\"],\"sources\":[\"input\"],\"actions\":[]}. Do not invent an event."
                ModelRole.AFFECT->"Return JSON only: {\"affect\":\"warmth\",\"confidence\":80}. Interpret: Thank you, Yuki."
                ModelRole.EMBEDDING->"A quiet forest with birds."
                else->"Return JSON only: {\"text\":\"Hello, Mavyy.\",\"pointIds\":[0]}. Prepared meaning point 0: Mavyy greeted Yuki. Do not add facts."
            }
            measurement=execute(model,role,profile,"You are an advisory cognitive organ. Follow the bounded output contract; no tools or authority.",sample,estimate,adapter,admissionEpoch)
            benchmark.put("startupMs",measurement.startupMs).put("inferenceMs",measurement.inferenceMs)
                .put("preparationMs",measurement.preparationMs).put("generationMs",measurement.generationMs)
                .put("measuredTokens",measurement.tokens).put("peakBytes",measurement.peakBytes).put("unloadVerified",measurement.unloaded)
            val outputBudget=if(role==ModelRole.EMBEDDING)profile.output else AdaptiveProfile.measuredOutput(profile,measurement.startupMs,measurement.generationMs,measurement.tokens,measurement.preparationMs)
            benchmark.put("outputBudget",outputBudget?:0)
            if(role==ModelRole.EMBEDDING) {
                val first=measurement.vector?:error("No embedding");require(first.isNotEmpty()&&first.all(Float::isFinite))
                val near=execute(model,role,profile,"","Birds are singing in a peaceful woodland.",estimate,adapter,admissionEpoch)
                val far=execute(model,role,profile,"","Cryptocurrency markets experienced a financial collapse.",estimate,adapter,admissionEpoch)
                fun cosine(v:FloatArray?):Double { require(v!=null&&v.size==first.size&&v.all(Float::isFinite));return first.indices.sumOf { first[it].toDouble()*v[it] } }
                val related=cosine(near.vector);val unrelated=cosine(far.vector);require(related>unrelated+0.01) { "Embedding separation benchmark failed" }
                model.manifest.put("embeddingRelatedCosine",related).put("embeddingUnrelatedCosine",unrelated)
                measurement=measurement.copy(peakBytes=maxOf(measurement.peakBytes,near.peakBytes,far.peakBytes))
            }
            else {
                val text=measurement.text ?: error("No generated text");require(text.isNotBlank() && text.toByteArray().size<=4096)
                if(role==ModelRole.SYSTEM_ONE){val j=JSONObject(text);require(j.getString("route") in setOf("RESPOND","REASON","REMAIN_QUIET")&&j.getInt("confidence") in 0..100 && j.getJSONArray("meaning").length() in 1..8)}
                if(role==ModelRole.SYSTEM_TWO){val j=JSONObject(text);require(j.getJSONArray("points").length() in 1..8 && j.getJSONArray("sources").length()>0 && j.getJSONArray("actions").length()==0)}
                if(role==ModelRole.LANGUAGE_EXPRESSION){val j=JSONObject(text);require(j.getString("text").isNotBlank() && j.getJSONArray("pointIds").getInt(0)==0)}
                if(role==ModelRole.AFFECT){val j=JSONObject(text);require(j.getInt("confidence") in 0..100&&j.getString("affect") in setOf("warmth","concern","neutral","curiosity","frustration"))}
            }
            require(measurement.unloaded);require(measurement.peakBytes<=profile.memoryBudget)
            require(outputBudget!=null) { "Measured generation is too slow for a safe structured response: role=$role, load=${measurement.startupMs}ms, prepare=${measurement.preparationMs}ms, generate=${measurement.generationMs}ms/${measurement.tokens} tokens, deadline=${profile.deadlineMillis}ms, budget<64" }
            val after=observe();require(after.thermal<ThermalPressure.SEVERE && !after.lowMemory)
            benchmark.put("thermalAfter",after.thermal.name)
            model.manifest.put("adapter",adapter.id).put("adapterVersion",adapter.version).put("status","ADMITTED").put("context",profile.context)
                .put("output",outputBudget).put("threads",profile.threads).put("requestedBatch",profile.batch).put("batch",if(role==ModelRole.EMBEDDING)profile.context else profile.batch).put("peakBytes",measurement.peakBytes)
                .put("startupMs",measurement.startupMs).put("inferenceMs",measurement.inferenceMs).put("measuredTokens",measurement.tokens)
                .put("preparationMs",measurement.preparationMs).put("generationMs",measurement.generationMs)
                .put("tokensPerSecond",measurement.tokens*1000.0/maxOf(1,if(role==ModelRole.EMBEDDING)measurement.inferenceMs else measurement.generationMs)).put("unloadVerified",true)
                .put("thermalBefore",body.thermal.name).put("thermalAfter",after.thermal.name).put("measuredAt",now.toString())
            val roles=model.manifest.getJSONArray("acceptedRoles");if((0 until roles.length()).none { roles.getString(it)==role.name }) roles.put(role.name)
            reason="Runtime, role output, resource and unload checks passed; owner behavioral acceptance remains separate"
            model.manifest.put("reason",reason)
            benchmark.put("accepted",true).put("reason",reason)
            NativeSupervisor.requireActive(admissionEpoch)
            val saved=publishAdmission(model,role,now,reason)
            check(saved is FoundationResult.Success);accepted=true;FoundationResult.Success(model(id))
        } catch(e:Exception) {
            reason=e.message?.take(256)?:e.javaClass.simpleName;lastFailure=reason
            benchmark?.put("accepted",false)?.put("reason",reason)
            candidate?.let { model ->model.manifest.put("lastRejection",reason);db.write { it.execSQL("UPDATE organ_manifest SET json=? WHERE id=?",arrayOf(model.manifest.toString(),id));it.execSQL("DELETE FROM organ_role WHERE model_id=? AND role=?",arrayOf(id,role.name));true } }
            FoundationResult.Failure(FailureCategory.REJECTED)
        } finally {
            if(candidate!=null && !accepted)db.write { it.execSQL("INSERT INTO organ_receipt VALUES(?,?,?,?,?,?,?)",arrayOf<Any>("receipt-${UUID.randomUUID()}",id,role.name,if(accepted)1 else 0,reason,now.toString(),candidate.manifest.toString()));true }
        }
    }
    /** Receipt and role activation commit together: disk/constraint failure cannot admit without proof. */
    internal fun publishAdmission(model:OwnerModel,role:ModelRole,now:Instant,reason:String):FoundationResult<Boolean> = db.write { sql->
        require(model.manifest.getString("status")=="ADMITTED")
        sql.execSQL("INSERT INTO organ_receipt VALUES(?,?,?,?,?,?,?)",arrayOf<Any>("receipt-${UUID.randomUUID()}",model.id,role.name,1,reason,now.toString(),model.manifest.toString()))
        sql.execSQL("UPDATE organ_manifest SET json=? WHERE id=?",arrayOf(model.manifest.toString(),model.id))
        sql.execSQL("INSERT OR REPLACE INTO organ_role VALUES(?,?)",arrayOf(role.name,model.id));true
    }
    private fun execute(model:OwnerModel,role:ModelRole,profile:SafeRuntimeProfile,system:String,user:String,estimate:Long,adapter:OrganAdapter,epoch:Long=turnBudget.get()?.epoch ?: NativeSupervisor.cancellationEpoch()):OrganResult {
        NativeSupervisor.requireActive(epoch)
        val id="infer-${UUID.randomUUID()}";val lease=governor.reserve(Workload(id,estimate,profile.deadlineMillis,true),Instant.now())
        check(lease is FoundationResult.Success) { "Resource governor deferred inference" }
        try { return adapter.execute(File(root,model.id),profile,system,user,role==ModelRole.EMBEDDING,OrganGrammar.forRole(role,run { val refs=mutableSetOf("input");try { for(key in listOf("memories","observations")){val a=JSONObject(user).optJSONArray(key);if(a!=null)for(i in 0 until minOf(a.length(),20)){val source=a.getJSONObject(i).getString("source");require(source.matches(Regex("(memory|observation)[0-9]{1,2}")));refs+=source}} }catch(_:Exception){};refs },run { val caps=mutableSetOf<com.mavyy.localyuki.foundation.embodiment.CapabilityId>();try { val a=JSONObject(user).optJSONArray("availableCapabilities");if(a!=null)for(i in 0 until a.length())caps+=com.mavyy.localyuki.foundation.embodiment.CapabilityId.valueOf(a.getString(i)) }catch(_:Exception){};caps }), {
            val result=probe.read(com.mavyy.localyuki.resource.OwnerVisibility.active,Instant.now())
            result is FoundationResult.Success && result.value.foreground && result.value.thermal!=ThermalPressure.UNKNOWN && result.value.thermal<ThermalPressure.MODERATE && !result.value.lowMemory &&
                result.value.availableBytes>512*ResourceGovernor.MIB && (result.value.charging||(result.value.batteryPercent?:0)>5) && !result.value.powerSave && epoch==NativeSupervisor.cancellationEpoch()
        },epoch) } finally { governor.release(id) }
    }
    internal fun infer(role:ModelRole,system:String,user:String,maxOutput:Int?=null):OrganResult {
        lastInference=JSONObject().put("role",role.name).put("previousPass",if(turnBudget.get()!=null)lastInference else null)
        checkTurnActive()
        turnBudget.get()?.let { b->check(b.remaining>0 && android.os.SystemClock.elapsedRealtime()<b.until) { "Cognitive pass/time budget exhausted" };b.remaining-- }
        val active=(models() as? FoundationResult.Success)?.value?.singleOrNull { role in it.activeRoles }?:error("No admitted $role model enabled")
        val (d,m)=inspect(active,role);require(active.manifest.getJSONArray("acceptedRoles").let { a->(0 until a.length()).any { a.getString(it)==role.name } })
        val adapter=adapters.singleOrNull { it.id==active.manifest.getString("adapter")&&it.version==active.manifest.getInt("adapterVersion")&&it.supports(d,m) }?:error("Adapter version unavailable; readmit")
        val proof=db.read { sql->db.rows(sql,"SELECT manifest FROM organ_receipt WHERE model_id=? AND role=? AND accepted=1 ORDER BY created DESC,id DESC LIMIT 1",active.id,role.name).firstOrNull()?.first()?.let(::JSONObject) ?: error("Admission receipt missing") }
        require(proof is FoundationResult.Success && proof.value.getString("sha256")==d.sha256 && proof.value.getString("adapter")==adapter.id && proof.value.getInt("adapterVersion")==adapter.version) { "Admission receipt does not authorize this engine" }
        val admitted=proof.value
        observe();val current=governor.profile(Instant.now(),admitted.getLong("peakBytes"))?:error("Resource pressure deferred cognition")
        val remaining=turnBudget.get()?.let { it.until-android.os.SystemClock.elapsedRealtime() } ?: current.deadlineMillis
        check(remaining>=1000) { "Cognitive turn deadline exhausted" }
        val p=runtimeProfileForRole(current,admitted,remaining,maxOutput)
        lastInference?.put("mode",p.mode.name)?.put("outputLimit",p.output)?.put("context",p.context)?.put("deadlineMs",p.deadlineMillis)
        val boundedSystem=system+"\nOutput allowance: ${p.output} generated tokens total, including every JSON field. Keep content brief and complete."
        val result=execute(active,role,p,boundedSystem,user,m.estimatedMemory(d.fileBytes,p.context),adapter)
        lastInference?.put("startupMs",result.startupMs)?.put("preparationMs",result.preparationMs)?.put("generationMs",result.generationMs)?.put("generatedTokens",result.tokens)?.put("outputLimitReached",result.tokens>=p.output)?.put("contextShortened",result.contextTruncated)?.put("unloadVerified",result.unloaded)
        return result
    }
    fun activeHash(role:ModelRole):String?=(models() as? FoundationResult.Success)?.value?.firstOrNull { role in it.activeRoles }?.manifest?.optString("sha256")
    fun disable(role:ModelRole):FoundationResult<Boolean> = db.write { it.execSQL("DELETE FROM organ_role WHERE role=?",arrayOf(role.name));true }
    fun remove(id:String):FoundationResult<Boolean> = db.write { sql->
        require(id.matches(Regex("model-[0-9a-f-]{36}")));val model=model(id);require(model.activeRoles.isEmpty()) { "Disable roles before removing" }
        check(!File(root,id).exists()||File(root,id).delete());model.manifest.put("removed",true).put("status","REMOVED")
        sql.execSQL("UPDATE organ_manifest SET json=? WHERE id=?",arrayOf(model.manifest.toString(),id));true
    }
    fun unload():Boolean=NativeSupervisor.cancel()
    private fun <T:Any> FoundationResult<T>.asFailure():FoundationResult<Nothing> = when(this) {
        is FoundationResult.Failure->this;is FoundationResult.Unavailable->this;else->FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE)
    }
    override fun close()=db.close()
}
