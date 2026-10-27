package dev.upbooker.core

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

object Schedule {
    val ZONE: ZoneId = ZoneId.of("Asia/Jerusalem")

    /** Start early enough to finish the SMS login before booking opens. */
    val LEAD: Duration = Duration.ofSeconds(90)

    /** The next class whose login window hasn't started yet. */
    fun next(config: BookingConfig, now: LocalDateTime): PlannedRun {
        var classDate = now.toLocalDate().with(TemporalAdjusters.nextOrSame(config.classDay))
        while (true) {
            val opensAt = classDate.minusDays(config.opensDaysBefore.toLong()).atTime(config.opensAt)
            val startAt = opensAt.minus(LEAD)
            if (startAt.isAfter(now)) return PlannedRun(classDate, opensAt, startAt)
            classDate = classDate.plusWeeks(1)
        }
    }
}
