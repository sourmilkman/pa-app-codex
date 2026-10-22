package uk.co.tommulliner.pa.core

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.*

class EngineTest {
    private val rota = RotaCalculator()
    @Test fun `anchor and weekend before it match Tom's rota`() {
        listOf("2026-10-24", "2026-10-25", "2026-11-07", "2026-11-08").forEach { assertFalse(it, rota.works(LocalDate.parse(it))) }
        listOf("2026-10-31", "2026-11-01", "2026-10-17", "2026-10-18").forEach { assertTrue(it, rota.works(LocalDate.parse(it))) }
    }
    @Test fun `regular weekdays`() {
        val expected = listOf(false, false, true, true, true, false, false)
        val monday = LocalDate.parse("2026-10-19")
        expected.forEachIndexed { i, works -> assertEquals(works, rota.works(monday.plusDays(i.toLong()))) }
    }
    @Test fun `overrides take precedence`() {
        val work = LocalDate.parse("2026-10-31")
        listOf(ShiftOverride.HOLIDAY, ShiftOverride.CANCELLED, ShiftOverride.SICK, ShiftOverride.SWAP_OUT).forEach { assertFalse(rota.works(work, it)) }
        assertTrue(rota.works(LocalDate.parse("2026-10-26"), ShiftOverride.EXTRA))
        assertTrue(rota.works(LocalDate.parse("2026-10-26"), ShiftOverride.SWAP_IN))
    }
    @Test(expected = IllegalArgumentException::class) fun `anchor must be Saturday`() { RotaCalculator(LocalDate.parse("2026-10-30")) }
    @Test fun `eight checks only on working days across October clock change`() {
        val checks = CheckPlanner.plan(Instant.parse("2026-10-23T23:00:00Z"), days = 10)
        listOf("2026-10-24", "2026-10-25").forEach { date -> assertTrue(checks.none { it.date.toString() == date }) }
        listOf("2026-10-28", "2026-10-29", "2026-10-30", "2026-10-31", "2026-11-01").forEach { date ->
            assertEquals(8, checks.count { it.date.toString() == date })
            assertEquals(Instant.parse("${date}T09:45:00Z"), checks.first { it.date.toString() == date }.instant)
        }
    }
    @Test fun `spring shift stays at 0945 London after clocks advance`() {
        assertEquals(Instant.parse("2027-03-27T09:45:00Z"), LondonTime.instant(LocalDate.parse("2027-03-27"), LocalTime.of(9,45)))
        assertEquals(Instant.parse("2027-03-28T08:45:00Z"), LondonTime.instant(LocalDate.parse("2027-03-28"), LocalTime.of(9,45)))
        val checks = CheckPlanner.plan(Instant.parse("2027-03-26T00:00:00Z"), rota=RotaCalculator(LocalDate.parse("2027-03-27")), days = 4)
        assertEquals(8, checks.count { it.date == LocalDate.parse("2027-03-28") })
    }
    @Test fun `autumn overlap happens once with first offset`() {
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), LondonTime.instant(LocalDate.parse("2026-10-25"), LocalTime.of(1,30)))
    }
    @Test fun `spring nonexistent time shifts forward`() {
        assertEquals(Instant.parse("2027-03-28T01:30:00Z"), LondonTime.instant(LocalDate.parse("2027-03-28"), LocalTime.of(1,30)))
    }
    @Test fun `planner deduplicates and never backfills elapsed checks`() {
        val now = Instant.parse("2026-10-28T10:00:00Z")
        val checks = CheckPlanner.plan(now, CheckPlanner.defaults + LocalTime.of(10,0), days = 1)
        assertEquals(6, checks.size)
        assertTrue(checks.all { it.instant > now })
        assertEquals(checks.size, checks.map { it.id }.distinct().size)
    }
    @Test fun `holiday prevents routine generation`() {
        val date = LocalDate.parse("2026-10-28")
        assertTrue(CheckPlanner.plan(LondonTime.instant(date,LocalTime.MIDNIGHT), overrides=mapOf(date to ShiftOverride.HOLIDAY), days=1).isEmpty())
    }
    @Test fun `retry starts at ten minutes then every five`() {
        val now = Instant.parse("2026-10-28T09:45:00Z")
        val first = ReminderPolicy.transition(ReminderState.SCHEDULED,ReminderAction.FIRE,now,true)
        assertEquals(now.plusSeconds(600), first.next)
        val retry = ReminderPolicy.transition(first.state,ReminderAction.FIRE,first.next!!,true)
        assertEquals(now.plusSeconds(900), retry.next)
    }
    @Test fun `snooze measures five elapsed minutes across autumn rollback`() {
        val now = Instant.parse("2026-10-25T00:58:00Z")
        val snoozed = ReminderPolicy.transition(ReminderState.ALERTING,ReminderAction.SNOOZE,now,true)
        assertEquals(Instant.parse("2026-10-25T01:03:00Z"), snoozed.next)
        assertEquals(LocalTime.of(1,3), LondonTime.local(snoozed.next!!).toLocalTime())
    }
    @Test fun `done and dismissed are terminal even after stale broadcasts`() {
        for (state in listOf(ReminderState.DONE,ReminderState.DISMISSED,ReminderState.CANCELLED)) for (action in ReminderAction.entries) {
            assertEquals(ReminderTransition(state,null), ReminderPolicy.transition(state,action,Instant.EPOCH,true))
        }
    }
    @Test fun `notification is delivered once`() {
        assertEquals(ReminderTransition(ReminderState.DELIVERED,null),ReminderPolicy.transition(ReminderState.SCHEDULED,ReminderAction.FIRE,Instant.EPOCH,false))
    }
    @Test fun `weekly pay subtracts each unpaid break and rounds once`() {
        val shifts = (0..2).map { PaidShift(LocalDate.parse("2026-10-28").plusDays(it.toLong()),LocalTime.of(9,0),LocalTime.of(17,0),30) }
        assertEquals(PayTotal(1350,BigDecimal("281.25")),PayCalculator.total(shifts,BigDecimal("12.50")))
    }
    @Test fun `unset rate produces hours without invented pay`() {
        assertNull(PayCalculator.total(emptyList(),null).gross)
    }
    @Test fun `pay period is inclusive start exclusive end`() {
        val shifts = listOf("2026-10-25","2026-10-26","2026-11-01","2026-11-02").map { PaidShift(LocalDate.parse(it),LocalTime.of(9,0),LocalTime.of(10,0),0) }
        assertEquals(BigDecimal("20.00"),PayCalculator.period(shifts,LocalDate.parse("2026-10-26"),LocalDate.parse("2026-11-02"),BigDecimal.TEN).gross)
    }
    @Test fun `overnight pay uses actual elapsed time during GMT change`() {
        val shift = PaidShift(LocalDate.parse("2026-10-24"),LocalTime.of(22,0),LocalTime.of(6,0),30)
        assertEquals(510,PayCalculator.total(listOf(shift),BigDecimal.TEN).paidMinutes.toInt())
    }
    @Test(expected=IllegalArgumentException::class) fun `break cannot exceed shift`() {
        PayCalculator.total(listOf(PaidShift(LocalDate.parse("2026-10-28"),LocalTime.of(9,0),LocalTime.of(10,0),61)),BigDecimal.TEN)
    }
}
