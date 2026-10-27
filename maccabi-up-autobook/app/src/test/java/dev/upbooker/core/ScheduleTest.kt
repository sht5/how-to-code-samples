package dev.upbooker.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ScheduleTest {
    private val config = BookingConfig(
        classDay = DayOfWeek.FRIDAY,
        opensDaysBefore = 7,
        opensAt = LocalTime.of(8, 0),
    )

    @Test
    fun `books the next Friday whose booking hasn't opened yet`() {
        // Tue 2026-10-27: next Friday is 10-30, but its booking opened on 10-23.
        val run = Schedule.next(config, LocalDateTime.of(2026, 10, 27, 12, 0))
        assertEquals(LocalDate.of(2026, 11, 6), run.classDate)
        assertEquals(LocalDateTime.of(2026, 10, 30, 8, 0), run.opensAt)
        assertEquals(LocalDateTime.of(2026, 10, 30, 7, 58, 30), run.startAt)
    }

    @Test
    fun `just before the login window still targets this week`() {
        val run = Schedule.next(config, LocalDateTime.of(2026, 10, 30, 7, 58, 0))
        assertEquals(LocalDate.of(2026, 11, 6), run.classDate)
    }

    @Test
    fun `once the login window started it moves to the following week`() {
        val run = Schedule.next(config, LocalDateTime.of(2026, 10, 30, 7, 59, 0))
        assertEquals(LocalDate.of(2026, 11, 13), run.classDate)
    }

    @Test
    fun `same-day opening`() {
        val sameDay = config.copy(opensDaysBefore = 0, opensAt = LocalTime.of(6, 0))
        val run = Schedule.next(sameDay, LocalDateTime.of(2026, 10, 27, 12, 0))
        assertEquals(LocalDate.of(2026, 10, 30), run.classDate)
        assertEquals(LocalDateTime.of(2026, 10, 30, 6, 0), run.opensAt)
    }
}
