package dev.upbooker.android

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.upbooker.core.PlannedRun
import dev.upbooker.core.Schedule
import java.time.LocalDateTime

object AlarmScheduler {
    /** Schedules the next weekly run (or cancels it if disabled). */
    fun scheduleNext(context: Context): PlannedRun? {
        val store = AppStore(context)
        val config = store.config()
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context, 0, Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        alarms.cancel(pending)

        if (!config.enabled) {
            store.saveNextRun(null)
            return null
        }

        val run = Schedule.next(config, LocalDateTime.now(Schedule.ZONE))
        val triggerAt = run.startAt.atZone(Schedule.ZONE).toInstant().toEpochMilli()
        if (canScheduleExact(alarms)) {
            // An alarm clock alarm fires on time even in Doze and may start a foreground service.
            alarms.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, pending), pending)
        } else {
            store.log("WARNING: exact alarms not allowed, run may be late. Allow them in the app.")
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
        store.saveNextRun(run)
        return run
    }

    fun canScheduleExact(alarms: AlarmManager) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        BookingService.start(context, dryRun = false)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) AlarmScheduler.scheduleNext(context)
    }
}
