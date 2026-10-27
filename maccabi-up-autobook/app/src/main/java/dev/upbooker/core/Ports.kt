package dev.upbooker.core

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The upapp backend. The real implementation is filled in after mapping the
 * app's traffic (see docs/CAPTURE.md).
 */
interface MaccabiClient {
    /** Ask Maccabi to send the login SMS to the phone registered for [idNumber]. */
    suspend fun requestOtp(idNumber: String)
    suspend fun verifyOtp(idNumber: String, code: String): Session
    suspend fun findClasses(session: Session, date: LocalDate): List<GymClass>
    suspend fun myBookings(session: Session): List<GymClass>
    suspend fun book(session: Session, gymClass: GymClass): BookResult
}

/** Delivers the login code from the SMS. [start] must be called before the SMS is requested. */
interface OtpSource {
    fun start()
    suspend fun await(timeout: Duration): String?
    fun stop()
}

interface JobState {
    fun idNumber(): String?
    fun lastBookedClassDate(): LocalDate?
    fun markBooked(classDate: LocalDate)
    /** Returns true (and forgets the skip) if the user asked to skip [classDate]. */
    fun consumeSkip(classDate: LocalDate): Boolean
}

interface JobClock {
    fun now(): LocalDateTime
    suspend fun sleep(duration: Duration)
}
