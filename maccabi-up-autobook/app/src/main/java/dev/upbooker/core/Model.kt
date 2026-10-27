package dev.upbooker.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** What to book and when booking opens. */
data class BookingConfig(
    val enabled: Boolean = false,
    val classDay: DayOfWeek = DayOfWeek.FRIDAY,
    val classTime: LocalTime = LocalTime.of(9, 0),
    val backupTime: LocalTime? = null,
    val activityKeyword: String = "פילאטיס",
    val studioKeyword: String = "",
    /** Booking for a class opens this many days before it... */
    val opensDaysBefore: Int = 7,
    /** ...at this time of day (Israel time). */
    val opensAt: LocalTime = LocalTime.of(8, 0),
    /** Text that appears in the Maccabi login SMS (sender name or body). */
    val smsKeyword: String = "מכבי",
)

data class GymClass(
    val id: String,
    val name: String,
    val studio: String,
    val start: LocalDateTime,
)

class Session(val token: String)

sealed interface BookResult {
    data object Booked : BookResult
    data object Full : BookResult
    data object NotOpenYet : BookResult
    data class Failed(val message: String) : BookResult
}

/** One scheduled attempt: log in at [startAt], book as soon as [opensAt] passes. */
data class PlannedRun(
    val classDate: LocalDate,
    val opensAt: LocalDateTime,
    val startAt: LocalDateTime,
)

sealed interface Outcome {
    data class Booked(val gymClass: GymClass) : Outcome
    data class AlreadyBooked(val classDate: LocalDate) : Outcome
    data class Skipped(val reason: String) : Outcome
    data class Failed(val reason: String) : Outcome
}
