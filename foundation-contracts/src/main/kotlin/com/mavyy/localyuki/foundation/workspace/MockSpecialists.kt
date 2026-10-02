package com.mavyy.localyuki.foundation.workspace

import com.mavyy.localyuki.foundation.affect.*
import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.memory.MemoryBounds

/** QA-only deterministic adapters. Production wiring does not advertise these as neural organs. */
class MockAffectInterpreter(private val result: AffectEventKind) : AffectInterpreter {
    override fun interpret(text: String): FoundationResult<AffectEventKind> = if(MemoryBounds.text(text,512))
        FoundationResult.Success(result) else FoundationResult.Failure(FailureCategory.INVALID_INPUT)
}
class MockEmbeddingEngine : EmbeddingEngine {
    override fun embed(text: String): FoundationResult<List<Float>> {
        if(!MemoryBounds.text(text,512)) return FoundationResult.Failure(FailureCategory.INVALID_INPUT)
        val histogram=IntArray(16)
        text.toByteArray(Charsets.UTF_8).forEach { histogram[(it.toInt() and 255)%16]++ }
        val norm=kotlin.math.sqrt(histogram.sumOf { it.toDouble()*it }.coerceAtLeast(1.0))
        return FoundationResult.Success(histogram.map { (it/norm).toFloat() })
    }
}
class MockVisionEngine(private val descriptions: Map<String,String>) : VisionEngine {
    override fun describe(inputId: String): FoundationResult<String> {
        if(!MemoryBounds.id(inputId)) return FoundationResult.Failure(FailureCategory.INVALID_INPUT)
        val text=descriptions[inputId] ?: return FoundationResult.Unavailable(UnavailableReason.CAPABILITY_UNAVAILABLE)
        return if(MemoryBounds.text(text,2048)) FoundationResult.Success(text) else FoundationResult.Failure(FailureCategory.INVALID_INPUT)
    }
}
class MockSpeechEngine(private val transcriptions: Map<String,String>) : SpeechEngine {
    override fun transcribe(inputId: String): FoundationResult<String> {
        if(!MemoryBounds.id(inputId)) return FoundationResult.Failure(FailureCategory.INVALID_INPUT)
        val text=transcriptions[inputId] ?: return FoundationResult.Unavailable(UnavailableReason.CAPABILITY_UNAVAILABLE)
        return if(MemoryBounds.text(text,2048)) FoundationResult.Success(text) else FoundationResult.Failure(FailureCategory.INVALID_INPUT)
    }
}
