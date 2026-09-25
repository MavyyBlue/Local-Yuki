package com.mavyy.localyuki.foundation.temporal

import com.mavyy.localyuki.foundation.contracts.*
import java.time.*
import java.time.temporal.WeekFields

/** Platform observation is injected; the zone is sampled for every request. */
fun interface ClockSource { fun now(): Instant }
fun interface ZoneSource { fun zone(): ZoneId }

data class TemporalGroundingSnapshot(val instant: Instant, val zoneId: ZoneId, val localDateTime: LocalDateTime) {
    val localDate: LocalDate get() = localDateTime.toLocalDate()
}

data class ResolvedTimeWindow(
    val startInclusive: Instant, val endExclusive: Instant, val zoneId: ZoneId,
    val startLocalDate: LocalDate, val endLocalDateExclusive: LocalDate
)

sealed interface CalendarQuery {
    data object Today : CalendarQuery
    data object Yesterday : CalendarQuery
    data object Tomorrow : CalendarQuery
    data class Dates(val startInclusive: LocalDate, val endExclusive: LocalDate) : CalendarQuery
    data class Week(val weeksFromCurrent: Int, val firstDay: DayOfWeek) : CalendarQuery
}

interface TemporalGroundingReader {
    fun ground(): FoundationResult<TemporalGroundingSnapshot>
    fun resolve(query: CalendarQuery): FoundationResult<ResolvedTimeWindow>
}

class DeterministicTemporalGrounding(private val clock: ClockSource, private val zones: ZoneSource) : TemporalGroundingReader {
    override fun ground(): FoundationResult<TemporalGroundingSnapshot> = try {
        val instant = clock.now()
        val zone = zones.zone()
        FoundationResult.Success(TemporalGroundingSnapshot(instant, zone, instant.atZone(zone).toLocalDateTime()))
    } catch (_: Exception) { FoundationResult.Unavailable(UnavailableReason.DEPENDENCY_UNAVAILABLE) }

    override fun resolve(query: CalendarQuery): FoundationResult<ResolvedTimeWindow> {
        val grounded = ground()
        if (grounded !is FoundationResult.Success) return when (grounded) {
            is FoundationResult.Failure -> grounded
            is FoundationResult.Unavailable -> grounded
            else -> FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE)
        }
        return try {
            val today = grounded.value.localDate
            val (start, end) = when (query) {
                CalendarQuery.Today -> today to today.plusDays(1)
                CalendarQuery.Yesterday -> today.minusDays(1) to today
                CalendarQuery.Tomorrow -> today.plusDays(1) to today.plusDays(2)
                is CalendarQuery.Dates -> query.startInclusive to query.endExclusive
                is CalendarQuery.Week -> {
                    require(query.weeksFromCurrent in -520..520)
                    val first = today.with(WeekFields.of(query.firstDay, 1).dayOfWeek(), 1).plusWeeks(query.weeksFromCurrent.toLong())
                    first to first.plusWeeks(1)
                }
            }
            require(start.isBefore(end))
            val a = start.atStartOfDay(grounded.value.zoneId).toInstant()
            val b = end.atStartOfDay(grounded.value.zoneId).toInstant()
            if (!a.isBefore(b)) FoundationResult.Failure(FailureCategory.CONFLICT)
            else FoundationResult.Success(ResolvedTimeWindow(a, b, grounded.value.zoneId, start, end))
        } catch (_: Exception) { FoundationResult.Failure(FailureCategory.INVALID_INPUT) }
    }
}
