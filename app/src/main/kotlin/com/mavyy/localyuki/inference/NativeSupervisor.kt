package com.mavyy.localyuki.inference

import android.content.*
import android.os.*
import com.mavyy.localyuki.foundation.resource.SafeRuntimeProfile
import java.io.File
import java.util.concurrent.*
import java.util.concurrent.locks.ReentrantLock

internal data class OrganResult(val text:String?,val vector:FloatArray?,val startupMs:Long,val inferenceMs:Long,
    val parameters:Long,val weightBytes:Long,val peakBytes:Long,val context:Int,val unloaded:Boolean,val tokens:Int,val contextTruncated:Boolean=false,
    val preparationMs:Long=0,val generationMs:Long=inferenceMs,val promptTokens:Int=0,val decodedPrompt:Int=0,val selectedCandidate:Int=0)
internal class OrganExecutionFailure(message:String,val measurement:OrganResult):IllegalStateException(message)
/** Native failures/timeouts affect a disposable process. A second cognition cannot bypass the global lock. */
internal class NativeSupervisor(context:Context) {
    private val app=context.applicationContext
    companion object {
        private val lock=ReentrantLock()
        private val cancellation=java.util.concurrent.atomic.AtomicLong()
        fun cancellationEpoch():Long=cancellation.get()
        fun requireActive(epoch:Long) { check(epoch==cancellation.get()) { "Cognitive operation cancelled" } }
        @Volatile private var active:Messenger?=null
        @Volatile private var pending:CompletableFuture<Bundle>?=null
        fun cancel():Boolean { cancellation.incrementAndGet();val remote=active ?: return true
            pending?.completeExceptionally(IllegalStateException("Owner/resource unload requested"))
            return try { remote.send(Message.obtain(null,2));true }catch(_:Exception){false}
        }
    }
    fun run(file:File,profile:SafeRuntimeProfile,system:String,user:String,embedding:Boolean=false,grammar:String="",
        safe:()->Boolean,epoch:Long=cancellationEpoch(),prompts:List<OrganPrompt>?=null):OrganResult {
        requireActive(epoch)
        check(Looper.myLooper()!=Looper.getMainLooper()) { "Inference must run off the UI thread" }
        check(lock.tryLock()) { "cognition already active" }
        val connected=CompletableFuture<Messenger>();val response=CompletableFuture<Bundle>()
        val incoming=Messenger(object:Handler(Looper.getMainLooper()) { override fun handleMessage(m:Message) { response.complete(m.data) } })
        val connection=object:ServiceConnection {
            override fun onServiceConnected(name:ComponentName,service:IBinder) { connected.complete(Messenger(service)) }
            override fun onServiceDisconnected(name:ComponentName) { response.completeExceptionally(IllegalStateException("runtime process died")) }
            override fun onBindingDied(name:ComponentName) { response.completeExceptionally(IllegalStateException("runtime binding died")) }
            override fun onNullBinding(name:ComponentName) { connected.completeExceptionally(IllegalStateException("runtime unavailable")) }
        }
        var bound=false;var remote:Messenger?=null
        val monitor=Executors.newSingleThreadScheduledExecutor()
        try {
            requireActive(epoch);check(safe());bound=app.bindService(Intent(app,OrganService::class.java),connection,Context.BIND_AUTO_CREATE or Context.BIND_WAIVE_PRIORITY);check(bound)
            remote=connected.get(10,TimeUnit.SECONDS);requireActive(epoch);active=remote;pending=response
            val request=Bundle().apply {
                putParcelable("fd",ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY))
                putInt("context",profile.context);putInt("threads",profile.threads);putInt("batch",profile.batch);putInt("output",profile.output)
                putLong("deadline",profile.deadlineMillis);putString("system",system);putString("user",if(embedding)user else "");putBoolean("embedding",embedding);putString("grammar",grammar)
                if(!embedding) {
                    val plan=prompts?:listOf(OrganPrompt(user,grammar,false))
                    putStringArray("users",plan.map { it.user }.toTypedArray());putStringArray("grammars",plan.map { it.grammar }.toTypedArray());putBooleanArray("shortened",plan.map { it.shortened }.toBooleanArray())
                    putInt("promptLimit",minOf(if(profile.deadlineMillis>=15000)256 else 192,profile.context-profile.output-1))
                }
            }
            @Suppress("DEPRECATION") val fd=request.getParcelable<ParcelFileDescriptor>("fd")!!
            requireActive(epoch)
            try { remote.send(Message.obtain(null,1).apply { data=request;replyTo=incoming }) } finally { fd.close() }
            monitor.scheduleAtFixedRate({ if(!safe()) { response.completeExceptionally(IllegalStateException("resource pressure interrupted runtime"));try { remote.send(Message.obtain(null,2)) } catch(_:Exception){} } },1,1,TimeUnit.SECONDS)
            val b=response.get(profile.deadlineMillis+6000,TimeUnit.MILLISECONDS)
            if(!b.getBoolean("success")) {
                val m=b.getLongArray("metrics")?:LongArray(11)
                fun at(i:Int)=m.getOrElse(i){0}
                throw OrganExecutionFailure(b.getString("error")?:"runtime failed",OrganResult(null,null,b.getLong("startupMs"),b.getLong("inferenceMs"),at(0),at(1),at(2),at(3).toInt(),b.getBoolean("unloaded"),0,at(5)==1L,at(6),at(7),at(8).toInt(),at(9).toInt(),at(10).toInt()))
            };check(b.getBoolean("unloaded")) { "runtime unload failed" }
            val m=b.getLongArray("metrics")?:error("missing measurements");require(m.size>=8 && m[2]>0 && m[2]<=profile.memoryBudget)
            val inferenceMs=b.getLong("inferenceMs")
            require(m[6]>=0 && m[7]>=0 && (embedding || (m[4]>0 && m[7]>0))) { "Missing generation timing" }
            // Account for JNI/tokenization overhead as preparation too; never give that time back to generation.
            val preparationMs=if(embedding)0 else maxOf(m[6],inferenceMs-m[7])
            return OrganResult(b.getString("text"),b.getFloatArray("vector"),b.getLong("startupMs"),inferenceMs,m[0],m[1],m[2],m[3].toInt(),true,m[4].toInt(),m[5]==1L,preparationMs,m[7],m.getOrElse(8){0}.toInt(),m.getOrElse(9){0}.toInt(),m.getOrElse(10){0}.toInt())
        } finally {
            try { remote?.send(Message.obtain(null,2)) } catch(_:Exception){}
            active=null;pending=null;monitor.shutdownNow();if(bound)app.unbindService(connection);lock.unlock()
        }
    }
}
