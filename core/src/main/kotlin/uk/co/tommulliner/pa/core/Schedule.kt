package uk.co.tommulliner.pa.core

import java.time.*
import java.time.temporal.ChronoUnit

object LondonTime {
    val zone: ZoneId = ZoneId.of("Europe/London")
    // Gap: move forward by the gap. Overlap: first occurrence (BST), exactly once.
    fun instant(date: LocalDate, time: LocalTime): Instant = date.atTime(time).atZone(zone).toInstant()
    fun local(instant: Instant): LocalDateTime = LocalDateTime.ofInstant(instant, zone)
}

enum class ShiftOverride { EXTRA, SWAP_IN, CANCELLED, SWAP_OUT, HOLIDAY, SICK }

class RotaCalculator(val workingSaturday: LocalDate = LocalDate.of(2026, 10, 31)) {
    init { require(workingSaturday.dayOfWeek == DayOfWeek.SATURDAY) }
    fun works(date: LocalDate, override: ShiftOverride? = null): Boolean {
        if (override != null) return override in setOf(ShiftOverride.EXTRA, ShiftOverride.SWAP_IN)
        return when (date.dayOfWeek) {
            DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY -> true
            DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> {
                val saturday = if (date.dayOfWeek == DayOfWeek.SUNDAY) date.minusDays(1) else date
                Math.floorDiv(ChronoUnit.DAYS.between(workingSaturday, saturday), 7L) % 2L == 0L
            }
            else -> false
        }
    }
}

data class CheckOccurrence(val id: String, val date: LocalDate, val time: LocalTime, val instant: Instant)

object CheckPlanner {
    val defaults = listOf(LocalTime.of(9, 45)) + (10..16).map { LocalTime.of(it, 0) }
    fun plan(
        now: Instant,
        times: List<LocalTime> = defaults,
        rota: RotaCalculator = RotaCalculator(),
        overrides: Map<LocalDate, ShiftOverride> = emptyMap(),
        days: Int = 28,
    ): List<CheckOccurrence> {
        require(days in 1..366)
        val today = LondonTime.local(now).toLocalDate()
        return (0 until days).flatMap { offset ->
            val date = today.plusDays(offset.toLong())
            if (!rota.works(date, overrides[date])) emptyList() else times.distinct().sorted().mapNotNull { time ->
                val instant = LondonTime.instant(date, time)
                if (instant <= now) null else CheckOccurrence("check:$date:$time", date, time, instant)
            }
        }
    }
}

enum class ReminderState { SCHEDULED, ALERTING, SNOOZED, DELIVERED, DONE, DISMISSED, CANCELLED }
enum class ReminderAction { FIRE, DONE, SNOOZE, DISMISS }
data class ReminderTransition(val state: ReminderState, val next: Instant?)

object ReminderPolicy {
    fun transition(
        state: ReminderState, action: ReminderAction, now: Instant,
        repeatUntilDone: Boolean, graceMinutes: Long = 10, repeatMinutes: Long = 5, snoozeMinutes: Long = 5,
    ): ReminderTransition {
        require(graceMinutes > 0 && repeatMinutes > 0 && snoozeMinutes > 0)
        if (state in setOf(ReminderState.DONE, ReminderState.DISMISSED, ReminderState.CANCELLED)) return ReminderTransition(state, null)
        return when (action) {
            ReminderAction.DONE -> ReminderTransition(ReminderState.DONE, null)
            ReminderAction.DISMISS -> ReminderTransition(ReminderState.DISMISSED, null)
            ReminderAction.SNOOZE -> ReminderTransition(ReminderState.SNOOZED, now.plusSeconds(snoozeMinutes * 60))
            ReminderAction.FIRE -> if (!repeatUntilDone) ReminderTransition(ReminderState.DELIVERED, null)
                else ReminderTransition(ReminderState.ALERTING, now.plusSeconds(
                    (if (state == ReminderState.ALERTING) repeatMinutes else graceMinutes) * 60))
        }
    }
}
