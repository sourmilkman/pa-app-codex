package uk.co.tommulliner.pa.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.*

data class PaidShift(val date: LocalDate, val start: LocalTime, val end: LocalTime, val unpaidBreakMinutes: Int)
data class PayTotal(val paidMinutes: Long, val gross: BigDecimal?)

object PayCalculator {
    fun total(shifts: List<PaidShift>, hourlyRate: BigDecimal?): PayTotal {
        require(hourlyRate == null || hourlyRate.signum() >= 0)
        val minutes = shifts.sumOf { shift ->
            require(shift.unpaidBreakMinutes >= 0)
            val endDate = if (shift.end < shift.start) shift.date.plusDays(1) else shift.date
            val elapsed = Duration.between(LondonTime.instant(shift.date, shift.start), LondonTime.instant(endDate, shift.end)).toMinutes()
            require(shift.unpaidBreakMinutes <= elapsed) { "Break exceeds shift duration" }
            elapsed - shift.unpaidBreakMinutes
        }
        return PayTotal(minutes, hourlyRate?.multiply(BigDecimal.valueOf(minutes))?.divide(BigDecimal(60), 2, RoundingMode.HALF_UP))
    }
    // Half-open date ranges avoid double-counting a boundary between pay periods.
    fun period(shifts: List<PaidShift>, from: LocalDate, until: LocalDate, rate: BigDecimal?): PayTotal {
        require(from < until)
        return total(shifts.filter { it.date >= from && it.date < until }, rate)
    }
}
