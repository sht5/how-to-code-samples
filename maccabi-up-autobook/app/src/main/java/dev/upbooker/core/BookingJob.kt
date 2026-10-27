package dev.upbooker.core

import java.time.Duration
import java.time.LocalDateTime

/**
 * One weekly booking attempt: log in with the SMS code, wait for booking to open,
 * then book the configured class (falling back to the backup time if it's full).
 *
 * Retries are deliberately slow and capped so the bot never hammers the server.
 */
class BookingJob(
    private val client: MaccabiClient,
    private val otp: OtpSource,
    private val state: JobState,
    private val clock: JobClock,
    private val log: (String) -> Unit,
    private val otpTimeout: Duration = Duration.ofMinutes(2),
    private val retryEvery: Duration = Duration.ofSeconds(10),
    private val maxAttempts: Int = 12,
) {
    suspend fun run(config: BookingConfig, run: PlannedRun): Outcome {
        val date = run.classDate
        if (!config.enabled) return Outcome.Skipped("Auto-booking is turned off")
        if (state.consumeSkip(date)) return Outcome.Skipped("Skipped $date as requested")
        if (state.lastBookedClassDate() == date) return Outcome.AlreadyBooked(date)
        val id = state.idNumber() ?: return Outcome.Failed("No ID number saved")

        val session = try {
            login(id)
        } catch (e: Exception) {
            return Outcome.Failed("Login failed: ${e.message}")
        } ?: return Outcome.Failed("No login SMS arrived within ${otpTimeout.toMinutes()} min")

        val existing = try {
            client.myBookings(session)
        } catch (e: Exception) {
            log("Could not check existing bookings: ${e.message}")
            emptyList()
        }
        if (existing.any { it.start.toLocalDate() == date && ClassMatcher.matches(it, config) }) {
            state.markBooked(date)
            return Outcome.AlreadyBooked(date)
        }

        waitUntil(run.opensAt)

        val times = listOfNotNull(config.classTime, config.backupTime).distinct()
        var timeIndex = 0
        var attempt = 0
        while (attempt < maxAttempts) {
            attempt++
            val time = times[timeIndex]
            var target: GymClass? = null
            val result = try {
                target = ClassMatcher.find(client.findClasses(session, date), config, date, time)
                if (target == null) log("Attempt $attempt: $time class not listed yet")
                target?.let { client.book(session, it) }
            } catch (e: Exception) {
                log("Attempt $attempt: ${e.message}")
                null
            }
            when (result) {
                BookResult.Booked -> {
                    state.markBooked(date)
                    return Outcome.Booked(checkNotNull(target))
                }
                BookResult.Full -> {
                    if (timeIndex + 1 < times.size) {
                        timeIndex++
                        log("$time is full, trying backup ${times[timeIndex]}")
                        continue
                    }
                    return Outcome.Failed("$time class on $date is full")
                }
                BookResult.NotOpenYet -> log("Attempt $attempt: booking not open yet")
                is BookResult.Failed -> log("Attempt $attempt: ${result.message}")
                null -> Unit
            }
            clock.sleep(retryEvery)
        }
        return Outcome.Failed("Gave up after $maxAttempts attempts")
    }

    private suspend fun login(id: String): Session? {
        otp.start()
        try {
            client.requestOtp(id)
            log("Login SMS requested, waiting for it...")
            val code = otp.await(otpTimeout) ?: return null
            log("SMS code received")
            return client.verifyOtp(id, code)
        } finally {
            otp.stop()
        }
    }

    private suspend fun waitUntil(time: LocalDateTime) {
        val wait = Duration.between(clock.now(), time)
        if (!wait.isNegative && !wait.isZero) {
            log("Logged in, waiting ${wait.seconds}s for booking to open")
            clock.sleep(wait)
        }
    }
}
