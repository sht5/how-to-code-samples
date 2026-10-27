package dev.upbooker.android

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.upbooker.core.BookingJob
import dev.upbooker.core.FakeMaccabiClient
import dev.upbooker.core.JobClock
import dev.upbooker.core.JobState
import dev.upbooker.core.MaccabiApiClient
import dev.upbooker.core.Outcome
import dev.upbooker.core.PlannedRun
import dev.upbooker.core.Schedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** Runs one booking attempt in the foreground so Android doesn't kill it mid-login. */
class BookingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val dryRun = intent?.getBooleanExtra(EXTRA_DRY_RUN, false) ?: false
        ServiceCompat.startForeground(
            this,
            Notifier.RUNNING_ID,
            Notifier.running(this, if (dryRun) "Dry run in progress" else "Booking your class..."),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        if (running) return START_NOT_STICKY
        running = true

        val wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "upbooker:booking")
            .apply { acquire(Duration.ofMinutes(10).toMillis()) }

        scope.launch {
            val store = AppStore(this@BookingService)
            val log: (String) -> Unit = { store.log(if (dryRun) "[dry] $it" else it) }
            val outcome = try {
                run(store, dryRun, log)
            } catch (t: Throwable) {
                Outcome.Failed(t.message ?: t.toString())
            }
            log("Result: ${describe(outcome)}")
            Notifier.result(this@BookingService, title(outcome, dryRun), describe(outcome))
            if (!dryRun) AlarmScheduler.scheduleNext(this@BookingService)
            if (wakeLock.isHeld) wakeLock.release()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private suspend fun run(store: AppStore, dryRun: Boolean, log: (String) -> Unit): Outcome {
        val config = store.config()
        val otp = SmsOtpSource(this, config.smsKeyword)
        return if (dryRun) {
            val now = LocalDateTime.now(Schedule.ZONE)
            val plan = Schedule.next(config, now)
            val dryConfig = config.copy(enabled = true)
            BookingJob(FakeMaccabiClient(dryConfig, log), otp, DryRunState(store), SystemClock, log)
                .run(dryConfig, PlannedRun(plan.classDate, now.plusSeconds(5), now))
        } else {
            val plan = store.nextRun() ?: return Outcome.Failed("No run was scheduled")
            log("Starting run for class on ${plan.classDate}")
            BookingJob(MaccabiApiClient(), otp, store, SystemClock, log).run(config, plan)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** Uses the real ID but never records a booking or consumes a skip. */
    private class DryRunState(private val store: AppStore) : JobState {
        override fun idNumber() = store.idNumber() ?: "000000000"
        override fun lastBookedClassDate(): LocalDate? = null
        override fun markBooked(classDate: LocalDate) = Unit
        override fun consumeSkip(classDate: LocalDate) = false
    }

    private object SystemClock : JobClock {
        override fun now(): LocalDateTime = LocalDateTime.now(Schedule.ZONE)
        override suspend fun sleep(duration: Duration) = delay(duration.toMillis())
    }

    companion object {
        private const val EXTRA_DRY_RUN = "dryRun"

        fun start(context: Context, dryRun: Boolean) {
            val intent = Intent(context, BookingService::class.java).putExtra(EXTRA_DRY_RUN, dryRun)
            ContextCompat.startForegroundService(context, intent)
        }

        fun title(outcome: Outcome, dryRun: Boolean) = (if (dryRun) "Dry run: " else "") + when (outcome) {
            is Outcome.Booked -> "Pilates booked ✅"
            is Outcome.AlreadyBooked -> "Already booked"
            is Outcome.Skipped -> "Skipped this week"
            is Outcome.Failed -> "Booking FAILED ❌"
        }

        fun describe(outcome: Outcome) = when (outcome) {
            is Outcome.Booked -> "${outcome.gymClass.name} at ${outcome.gymClass.studio}, ${outcome.gymClass.start}"
            is Outcome.AlreadyBooked -> "You're already booked for ${outcome.classDate}"
            is Outcome.Skipped -> outcome.reason
            is Outcome.Failed -> "${outcome.reason}. Book manually in upapp."
        }
    }
}
