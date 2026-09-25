package com.mavyy.localyuki.foundation.state

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class YukiStateValidationTest {
    private val now = Instant.parse("2026-09-24T12:00:00Z")
    private val empty = YukiStateSnapshot(YukiStateVersion(1), 0, null, null, emptyList(), emptyList(), emptyList(), now)

    @Test fun boundsAndDuplicateIdentifiers() {
        assertTrue(YukiStateValidation.valid(empty))
        val item = CurrentItem("same", "text", now, now)
        assertFalse(YukiStateValidation.valid(empty.copy(intentions = listOf(item, item))))
        assertFalse(YukiStateValidation.valid(empty.copy(topics = List(YukiStateLimits.MAX_TOPICS + 1) { item.copy(id = "id$it") })))
        assertFalse(YukiStateValidation.valid(empty.copy(intentions = listOf(item.copy(id = " ")))))
        assertFalse(YukiStateValidation.valid(empty.copy(intentions = listOf(item.copy(text = "x".repeat(YukiStateLimits.TEXT + 1))))))
        assertFalse(YukiStateValidation.valid(empty.copy(focus = CurrentFocus("x".repeat(YukiStateLimits.FOCUS + 1)))))
        assertFalse(YukiStateValidation.valid(empty.copy(project = CurrentProject("x".repeat(YukiStateLimits.ID + 1), "valid"))))
        assertFalse(YukiStateValidation.valid(empty.copy(version = YukiStateVersion(2))))
    }
}
