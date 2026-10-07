package com.mavyy.localyuki.brain

import android.os.Binder
import android.os.IBinder
import android.os.RemoteException
import com.mavyy.localyuki.inference.RuntimeDeath
import com.mavyy.localyuki.inference.RuntimeRetirementGate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Controlled death notifications exercise the fence; this is not a physical Android Binder run. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class RuntimeRetirementTest {
    private class ControlledBinder:Binder() {
        @Volatile private var alive=true
        private val recipients=CopyOnWriteArrayList<IBinder.DeathRecipient>()
        private val historical=CopyOnWriteArrayList<IBinder.DeathRecipient>()
        override fun isBinderAlive()=alive
        override fun linkToDeath(recipient:IBinder.DeathRecipient,flags:Int) {
            if(!alive)throw RemoteException("already dead")
            recipients+=recipient;historical+=recipient
        }
        override fun unlinkToDeath(recipient:IBinder.DeathRecipient,flags:Int)=recipients.remove(recipient)
        fun die() { alive=false;recipients.forEach { it.binderDied() } }
        fun replayOldCallbacks() { historical.forEach { it.binderDied() } }
    }
    private fun rejects(block:()->Unit) {
        try { block();fail("A live preceding runtime was accepted") }catch(_:IllegalStateException){}
    }
    @Test fun nextPassWaitsForDeathRatherThanSuccessfulKillSend() {
        val gate=RuntimeRetirementGate();val binder=ControlledBinder();val death=RuntimeDeath(binder)
        val killSent=CountDownLatch(1);val complete=CompletableFuture<Unit>();val worker=Executors.newSingleThreadExecutor()
        try {
            worker.execute { try { gate.retire(death,3000) { killSent.countDown() };complete.complete(Unit) }catch(e:Throwable) { complete.completeExceptionally(e) } }
            assertTrue(killSent.await(2,TimeUnit.SECONDS));assertFalse(complete.isDone)
            rejects { gate.requireReady() }
            binder.die();complete.get(2,TimeUnit.SECONDS);gate.requireReady()
        }finally { binder.die();worker.shutdownNow() }
    }
    @Test fun teardownTimeoutRefusesAnotherBindUntilDelayedDeath() {
        val gate=RuntimeRetirementGate();val binder=ControlledBinder();var kills=0
        rejects { gate.retire(RuntimeDeath(binder),0) { kills++ } }
        assertEquals(1,kills);repeat(2) { rejects { gate.requireReady() } }
        binder.die();gate.requireReady();gate.requireReady()
    }
    @Test fun staleDeathCallbackCannotReleaseNewerRetiringBinder() {
        val gate=RuntimeRetirementGate();val old=ControlledBinder()
        gate.retire(RuntimeDeath(old),0) { old.die() }
        val current=ControlledBinder()
        rejects { gate.retire(RuntimeDeath(current),0){} }
        old.replayOldCallbacks();rejects { gate.requireReady() }
        current.die();gate.requireReady()
    }
    @Test fun failedKillSendRetainsFenceAndAlreadyDeadConnectionCanRetire() {
        val gate=RuntimeRetirementGate();val binder=ControlledBinder()
        rejects { gate.retire(RuntimeDeath(binder),0) { throw RemoteException("send failed") } }
        rejects { gate.requireReady() };binder.die();gate.requireReady()
        val alreadyDead=ControlledBinder().apply { die() }
        gate.retire(RuntimeDeath(alreadyDead),0) { throw RemoteException("already dead") };gate.requireReady()
    }
}
