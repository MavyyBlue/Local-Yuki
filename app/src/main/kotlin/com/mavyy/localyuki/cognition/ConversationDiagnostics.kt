package com.mavyy.localyuki.cognition

/** App-owned status only; never a model instruction or a saved Yuki utterance. */
internal class ConversationDiagnostics {
    var stage="Preparation";private set
    var reason="";private set
    fun reset() { stage="Preparation";reason="" }
    fun enter(value:String) { stage=value;reason="" }
    fun fail(error:Exception) { reason=(error.message?.takeIf { it.isNotBlank() }?:error.javaClass.simpleName).take(256) }
    fun rejected(detail:String) { if(reason.isEmpty())reason=detail.take(256) }
    fun report(runtime:String?):String="$stage could not complete: $reason"+if(runtime.isNullOrBlank())"" else "\n$runtime"
}
