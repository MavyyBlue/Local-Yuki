package com.mavyy.localyuki.foundation.recovery

import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.state.CurrentItem
import java.time.Instant

enum class RecoveryMode { AWAKE, FATIGUED, PREPARING_SLEEP, SLEEPING, RECOVERING }
data class RecoverySnapshot(val revision: Long,val mode: RecoveryMode,val pendingIntentions: List<CurrentItem>,
    val updatedAt: Instant,val maintenanceInterrupted: Boolean=false)
fun interface RecoveryReader { fun read(): FoundationResult<RecoverySnapshot> }
object RecoveryPolicy {
    fun permits(from: RecoveryMode,to: RecoveryMode): Boolean = to in when(from) {
        RecoveryMode.AWAKE -> setOf(RecoveryMode.FATIGUED,RecoveryMode.PREPARING_SLEEP)
        RecoveryMode.FATIGUED -> setOf(RecoveryMode.AWAKE,RecoveryMode.PREPARING_SLEEP)
        RecoveryMode.PREPARING_SLEEP -> setOf(RecoveryMode.SLEEPING,RecoveryMode.RECOVERING)
        RecoveryMode.SLEEPING -> setOf(RecoveryMode.RECOVERING)
        RecoveryMode.RECOVERING -> setOf(RecoveryMode.AWAKE,RecoveryMode.SLEEPING)
    }
    fun mayAct(mode: RecoveryMode)=mode==RecoveryMode.AWAKE || mode==RecoveryMode.FATIGUED
}
