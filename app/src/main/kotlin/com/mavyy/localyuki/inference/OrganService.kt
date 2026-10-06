package com.mavyy.localyuki.inference

import android.app.Service
import android.content.Intent
import android.os.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Isolated UID: no continuity files, permissions, network or device authority. One request, then unload. */
class OrganService:Service() {
    private val worker=Executors.newSingleThreadExecutor()
    private val deadline=Executors.newSingleThreadScheduledExecutor()
    private val busy=java.util.concurrent.atomic.AtomicBoolean(false)
    private val inbox=Messenger(object:Handler(Looper.getMainLooper()) {
        override fun handleMessage(message:Message) {
            if(message.what==2) { Process.killProcess(Process.myPid());return }
            if(message.what!=1 || !busy.compareAndSet(false,true)) return
            val reply=message.replyTo ?: run { Process.killProcess(Process.myPid());return }
            val b=message.data
            val limit=b.getLong("deadline",30000).coerceIn(1000,60000)
            val killer=deadline.schedule({Process.killProcess(Process.myPid())},limit+5000,TimeUnit.MILLISECONDS)
            worker.execute {
                val out=Bundle();var handle=0L;var native:NativeOrgan?=null
                try {
                    val context=b.getInt("context");val threads=b.getInt("threads");val batch=b.getInt("batch")
                    require(context in 256..4096 && threads in 1..4 && batch in 8..128 && b.getInt("output") in 16..512)
                    require((b.getString("grammar")?:"").toByteArray().size<=8192)
                    require((b.getString("system")?:"").toByteArray().size<=8192 && (b.getString("user")?:"").toByteArray().size<=16384)
                    @Suppress("DEPRECATION") val fd=b.getParcelable<ParcelFileDescriptor>("fd") ?: error("missing descriptor")
                    fd.use {
                        val started=SystemClock.elapsedRealtime();native=NativeOrgan()
                        handle=native!!.load(fd.fd,context,threads,batch,b.getBoolean("embedding"),limit)
                        out.putLong("startupMs",SystemClock.elapsedRealtime()-started)
                        val inference=SystemClock.elapsedRealtime()
                        if(b.getBoolean("embedding")) out.putFloatArray("vector",native!!.embed(handle,(b.getString("user")?:"").toByteArray(),limit-out.getLong("startupMs")))
                        else out.putString("text",native!!.generate(handle,(b.getString("system")?:"").toByteArray(),(b.getString("user")?:"").toByteArray(),(b.getString("grammar")?:"").toByteArray(),b.getInt("output"),limit-out.getLong("startupMs")).toString(Charsets.UTF_8))
                        out.putLong("inferenceMs",SystemClock.elapsedRealtime()-inference)
                        out.putLongArray("metrics",native!!.metrics(handle));out.putBoolean("success",true)
                    }
                } catch(e:Throwable) { out.putBoolean("success",false);out.putString("error",e.message?.take(256)?:e.javaClass.simpleName) }
                finally {
                    if(handle!=0L) try { native?.unload(handle);out.putBoolean("unloaded",true) } catch(_:Throwable) { out.putBoolean("success",false) }
                    killer.cancel(false)
                    try { reply.send(Message.obtain(null,1).apply { data=out }) } catch(_:Exception) { }
                    busy.set(false)
                }
            }
        }
    })
    override fun onBind(intent:Intent)=inbox.binder
    override fun onDestroy(){worker.shutdownNow();deadline.shutdownNow();super.onDestroy()}
}
