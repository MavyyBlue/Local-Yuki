package com.mavyy.localyuki.foundation.state

import com.mavyy.localyuki.foundation.contracts.FoundationResult
import java.time.Instant
import java.time.ZoneId

/** Explicit finite storage and projection limits. Invalid input is rejected, never truncated. */
object YukiStateLimits {
    const val MAX_INTENTIONS = 32
    const val MAX_TOPICS = 32
    const val ID = 80
    const val TEXT = 512
    const val FOCUS = 240
    const val PROJECT_LABEL = 160
}

@JvmInline value class YukiStateVersion(val value: Int)
data class CurrentProject(val id: String?, val label: String)
data class CurrentFocus(val text: String, val expiresAt: Instant? = null)
data class CurrentItem(val id: String, val text: String, val createdAt: Instant, val updatedAt: Instant, val expiresAt: Instant? = null)
enum class InteractionKind { USER_INPUT, YUKI_OUTPUT }
data class InteractionMarker(val kind: InteractionKind, val instant: Instant, val zoneId: ZoneId, val revision: Long)
data class YukiStateSnapshot(
    val version: YukiStateVersion, val revision: Long, val project: CurrentProject?, val focus: CurrentFocus?,
    val intentions: List<CurrentItem>, val topics: List<CurrentItem>, val interactions: List<InteractionMarker>,
    val updatedAt: Instant
)

fun interface YukiStateReader { fun read(): FoundationResult<YukiStateSnapshot> }

/** Commands carry no clock value; only the owning app implementation timestamps changes. */
sealed interface StateChange {
    data class Project(val value: CurrentProject?) : StateChange
    data class Focus(val value: CurrentFocus?) : StateChange
    data class PutIntention(val id: String, val text: String, val expiresAt: Instant? = null) : StateChange
    data class RemoveIntention(val id: String) : StateChange
    data class PutTopic(val id: String, val text: String, val expiresAt: Instant? = null) : StateChange
    data class RemoveTopic(val id: String) : StateChange
}

data class StateCommand(val expectedRevision: Long, val change: StateChange)

object YukiStateValidation {
    private fun bounded(s: String, max: Int) = s.isNotBlank() && s.length <= max && s == s.trim()
    fun valid(s: YukiStateSnapshot): Boolean =
        s.version.value == 1 && s.revision >= 0 &&
        (s.project == null || (s.project.id == null || bounded(s.project.id, YukiStateLimits.ID)) && bounded(s.project.label, YukiStateLimits.PROJECT_LABEL)) &&
        (s.focus == null || bounded(s.focus.text, YukiStateLimits.FOCUS)) &&
        s.intentions.size <= YukiStateLimits.MAX_INTENTIONS && s.topics.size <= YukiStateLimits.MAX_TOPICS &&
        listOf(s.intentions, s.topics).all { items ->
            items.map { it.id }.distinct().size == items.size && items.all {
                bounded(it.id, YukiStateLimits.ID) && bounded(it.text, YukiStateLimits.TEXT)
            }
        } && s.interactions.map { it.kind }.distinct().size == s.interactions.size &&
        s.interactions.all { it.revision in 1..s.revision }
}
