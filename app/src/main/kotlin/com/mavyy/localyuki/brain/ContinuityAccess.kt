package com.mavyy.localyuki.brain

/** Serialize app turns, job maintenance and owner-requested Vault operations. */
object ContinuityAccess {
    private val lock=java.util.concurrent.locks.ReentrantLock()
    fun <T> exclusive(block: () -> T): T {
        lock.lock();try { return block() } finally { lock.unlock() }
    }
}
