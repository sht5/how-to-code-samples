package dev.upbooker.core

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class BookingJobTest {
    private val classDate = LocalDate.of(2026, 11, 6)
    private val opensAt = LocalDateTime.of(2026, 10, 30, 8, 0)
    private val plan = PlannedRun(classDate, opensAt, opensAt.minusSeconds(90))
    private val config = BookingConfig(
        enabled = true,
        classTime = LocalTime.of(9, 0),
        backupTime = LocalTime.of(10, 0),
        studioKeyword = "Ramat Aviv",
    )

    private fun pilates(time: LocalTime, id: String = time.toString()) =
        GymClass(id, "פילאטיס מכשירים", "Ramat Aviv Studio", classDate.atTime(time))

    private class FakeClient(
        var classes: List<GymClass> = emptyList(),
        var bookings: List<GymClass> = emptyList(),
        val results: MutableMap<String, ArrayDeque<BookResult>> = mutableMapOf(),
    ) : MaccabiClient {
        val booked = mutableListOf<String>()
        var otpRequests = 0
        override suspend fun requestOtp(idNumber: String) { otpRequests++ }
        override suspend fun verifyOtp(idNumber: String, code: String): Session {
            require(code == "123456")
            return Session("t")
        }
        override suspend fun findClasses(session: Session, date: LocalDate) = classes
        override suspend fun myBookings(session: Session) = bookings
        override suspend fun book(session: Session, gymClass: GymClass): BookResult {
            booked += gymClass.id
            return results[gymClass.id]?.removeFirstOrNull() ?: BookResult.Booked
        }
    }

    private class FakeOtp(val code: String?) : OtpSource {
        var started = false
        var stopped = false
        override fun start() { started = true }
        override suspend fun await(timeout: Duration) = code.also { check(started) }
        override fun stop() { stopped = true }
    }

    private class FakeState(var id: String? = "123456789") : JobState {
        var booked: LocalDate? = null
        var skip: LocalDate? = null
        override fun idNumber() = id
        override fun lastBookedClassDate() = booked
        override fun markBooked(classDate: LocalDate) { booked = classDate }
        override fun consumeSkip(classDate: LocalDate) = (skip == classDate).also { if (it) skip = null }
    }

    private class FakeClock(var now: LocalDateTime) : JobClock {
        val sleeps = mutableListOf<Duration>()
        override fun now() = now
        override suspend fun sleep(duration: Duration) {
            sleeps += duration
            now = now.plus(duration)
        }
    }

    private fun job(client: MaccabiClient, otp: OtpSource = FakeOtp("123456"), state: JobState = FakeState()) =
        BookingJob(client, otp, state, FakeClock(plan.startAt), log = {}, maxAttempts = 5)

    @Test
    fun `logs in, waits for opening, and books the primary class`() = runTest {
        val client = FakeClient(classes = listOf(pilates(LocalTime.of(9, 0)), pilates(LocalTime.of(10, 0))))
        val otp = FakeOtp("123456")
        val state = FakeState()
        val clock = FakeClock(plan.startAt)
        val outcome = BookingJob(client, otp, state, clock, log = {}).run(config, plan)

        assertEquals(Outcome.Booked(pilates(LocalTime.of(9, 0))), outcome)
        assertEquals(listOf("09:00"), client.booked)
        assertEquals(classDate, state.booked)
        assertTrue(otp.stopped)
        assertEquals(listOf(Duration.ofSeconds(90)), clock.sleeps)
    }

    @Test
    fun `falls back to the backup time when the primary is full`() = runTest {
        val client = FakeClient(
            classes = listOf(pilates(LocalTime.of(9, 0)), pilates(LocalTime.of(10, 0))),
            results = mutableMapOf("09:00" to ArrayDeque(listOf(BookResult.Full))),
        )
        val outcome = job(client).run(config, plan)
        assertEquals(Outcome.Booked(pilates(LocalTime.of(10, 0))), outcome)
        assertEquals(listOf("09:00", "10:00"), client.booked)
    }

    @Test
    fun `retries while booking is not open yet`() = runTest {
        val client = FakeClient(
            classes = listOf(pilates(LocalTime.of(9, 0))),
            results = mutableMapOf(
                "09:00" to ArrayDeque(listOf(BookResult.NotOpenYet, BookResult.NotOpenYet, BookResult.Booked))
            ),
        )
        assertTrue(job(client).run(config, plan) is Outcome.Booked)
        assertEquals(3, client.booked.size)
    }

    @Test
    fun `gives up after max attempts when the class never appears`() = runTest {
        val outcome = job(FakeClient()).run(config, plan)
        assertTrue(outcome is Outcome.Failed)
    }

    @Test
    fun `fails cleanly when no SMS arrives`() = runTest {
        val client = FakeClient(classes = listOf(pilates(LocalTime.of(9, 0))))
        val otp = FakeOtp(null)
        val outcome = job(client, otp).run(config, plan)
        assertTrue(outcome is Outcome.Failed)
        assertTrue(client.booked.isEmpty())
        assertTrue(otp.stopped)
    }

    @Test
    fun `does nothing when already booked this week`() = runTest {
        val client = FakeClient(classes = listOf(pilates(LocalTime.of(9, 0))))
        val state = FakeState().apply { booked = classDate }
        assertEquals(Outcome.AlreadyBooked(classDate), job(client, state = state).run(config, plan))
        assertEquals(0, client.otpRequests)
    }

    @Test
    fun `detects a booking made manually in the app`() = runTest {
        val client = FakeClient(
            classes = listOf(pilates(LocalTime.of(9, 0))),
            bookings = listOf(pilates(LocalTime.of(10, 0))),
        )
        assertEquals(Outcome.AlreadyBooked(classDate), job(client).run(config, plan))
        assertTrue(client.booked.isEmpty())
    }

    @Test
    fun `honors skip and disabled`() = runTest {
        val client = FakeClient(classes = listOf(pilates(LocalTime.of(9, 0))))
        val state = FakeState().apply { skip = classDate }
        assertTrue(job(client, state = state).run(config, plan) is Outcome.Skipped)
        assertTrue(job(client).run(config.copy(enabled = false), plan) is Outcome.Skipped)
        assertEquals(0, client.otpRequests)
    }

    @Test
    fun `ignores classes at other studios`() = runTest {
        val elsewhere = GymClass("x", "פילאטיס", "Other Gym", classDate.atTime(9, 0))
        val client = FakeClient(classes = listOf(elsewhere))
        assertTrue(job(client).run(config.copy(backupTime = null), plan) is Outcome.Failed)
        assertTrue(client.booked.isEmpty())
    }
}
