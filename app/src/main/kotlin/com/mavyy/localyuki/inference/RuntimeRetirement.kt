package com.mavyy.localyuki.inference

import android.os.IBinder
import android.os.RemoteException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** A death callback belongs to this Binder only; an old callback cannot release a newer runtime. */
internal class RuntimeDeath(private val binder:IBinder) {
    private val dead=CompletableFuture<Unit>()
    private val recipient=IBinder.DeathRecipient { dead.complete(Unit) }
    init {
        try { binder.linkToDeath(recipient,0) }catch(_:RemoteException) { dead.complete(Unit) }
        if(!binder.isBinderAlive)dead.complete(Unit)
    }
    val confirmed:Boolean get()=dead.isDone
    fun await(timeoutMillis:Long):Boolean = try {
        dead.get(timeoutMillis,TimeUnit.MILLISECONDS);true
    }catch(_:TimeoutException) { false }
    catch(_:InterruptedException) { Thread.currentThread().interrupt();false }
    fun detach() { try { binder.unlinkToDeath(recipient,0) }catch(_:Exception){} }
}

/** Access is serialized by NativeSupervisor's lock. Timeout retains the preceding death fence. */
internal class RuntimeRetirementGate {
    @Volatile private var retiring:RuntimeDeath?=null
    fun requireReady() {
        val previous=retiring?:return
        check(previous.confirmed) { "Previous cognitive runtime is still retiring" }
        previous.detach();retiring=null
    }
    fun retire(death:RuntimeDeath,timeoutMillis:Long,kill:()->Unit) {
        retiring=death
        try { kill() }catch(_:Exception){}
        check(death.await(timeoutMillis)) { "Cognitive runtime teardown deadline exhausted" }
        death.detach();retiring=null
    }
}
