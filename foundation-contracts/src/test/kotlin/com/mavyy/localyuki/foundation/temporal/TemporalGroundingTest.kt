package com.mavyy.localyuki.foundation.temporal

import com.mavyy.localyuki.foundation.contracts.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class TemporalGroundingTest {
    @Test fun localBoundariesDstAndZoneResampling() {
        var zone = ZoneId.of("America/New_York")
        var instant = Instant.parse("2024-03-10T12:00:00Z")
        val grounding = DeterministicTemporalGrounding(ClockSource { instant }, ZoneSource { zone })
        assertEquals(LocalDate.of(2024, 3, 10), (grounding.ground() as FoundationResult.Success).value.localDate)
        val short = (grounding.resolve(CalendarQuery.Today) as FoundationResult.Success).value
        assertEquals(Duration.ofHours(23), Duration.between(short.startInclusive, short.endExclusive))
        assertEquals(Instant.parse("2024-03-10T05:00:00Z"), short.startInclusive)
        assertEquals(short.startInclusive, (grounding.resolve(CalendarQuery.Yesterday) as FoundationResult.Success).value.endExclusive)
        assertEquals(short.endExclusive, (grounding.resolve(CalendarQuery.Tomorrow) as FoundationResult.Success).value.startInclusive)
        instant = Instant.parse("2024-11-03T12:00:00Z")
        val long = (grounding.resolve(CalendarQuery.Today) as FoundationResult.Success).value
        assertEquals(Duration.ofHours(25), Duration.between(long.startInclusive, long.endExclusive))
        zone = ZoneId.of("Asia/Tokyo")
        assertEquals(zone, (grounding.ground() as FoundationResult.Success).value.zoneId)
        assertEquals(LocalDate.of(2024, 11, 3), (grounding.resolve(CalendarQuery.Today) as FoundationResult.Success).value.startLocalDate)
    }

    @Test fun calendarWindowsAndInvalidArithmetic() {
        val g = DeterministicTemporalGrounding(ClockSource { Instant.parse("2024-01-01T00:00:00Z") }, ZoneSource { ZoneId.of("UTC") })
        assertEquals(LocalDate.of(2024, 1, 1), (g.resolve(CalendarQuery.Today) as FoundationResult.Success).value.startLocalDate)
        val week = (g.resolve(CalendarQuery.Week(0, DayOfWeek.SUNDAY)) as FoundationResult.Success).value
        assertEquals(LocalDate.of(2023, 12, 31), week.startLocalDate)
        assertEquals(FoundationResult.Failure(FailureCategory.INVALID_INPUT), g.resolve(CalendarQuery.Dates(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 1, 1))))
        assertEquals(FoundationResult.Failure(FailureCategory.INVALID_INPUT), g.resolve(CalendarQuery.Week(521, DayOfWeek.MONDAY)))
    }
}
