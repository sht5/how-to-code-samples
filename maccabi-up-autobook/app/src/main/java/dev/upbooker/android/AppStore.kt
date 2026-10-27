package dev.upbooker.android

import android.content.Context
import dev.upbooker.core.BookingConfig
import dev.upbooker.core.JobState
import dev.upbooker.core.PlannedRun
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class AppStore(context: Context) : JobState {
    private val prefs = context.getSharedPreferences("upbooker", Context.MODE_PRIVATE)
    private val secure = SecureStore(context.getSharedPreferences("upbooker_secure", Context.MODE_PRIVATE))
    private val logFile = File(context.filesDir, "run.log")

    fun config() = BookingConfig(
        enabled = prefs.getBoolean("enabled", false),
        classDay = DayOfWeek.of(prefs.getInt("classDay", DayOfWeek.FRIDAY.value)),
        classTime = LocalTime.parse(prefs.getString("classTime", "09:00")),
        backupTime = prefs.getString("backupTime", null)?.let(LocalTime::parse),
        activityKeyword = prefs.getString("activityKeyword", null) ?: BookingConfig().activityKeyword,
        studioKeyword = prefs.getString("studioKeyword", "")!!,
        opensDaysBefore = prefs.getInt("opensDaysBefore", 7),
        opensAt = LocalTime.parse(prefs.getString("opensAt", "08:00")),
        smsKeyword = prefs.getString("smsKeyword", null) ?: BookingConfig().smsKeyword,
    )

    fun saveConfig(c: BookingConfig) {
        prefs.edit()
            .putBoolean("enabled", c.enabled)
            .putInt("classDay", c.classDay.value)
            .putString("classTime", c.classTime.toString())
            .putString("backupTime", c.backupTime?.toString())
            .putString("activityKeyword", c.activityKeyword)
            .putString("studioKeyword", c.studioKeyword)
            .putInt("opensDaysBefore", c.opensDaysBefore)
            .putString("opensAt", c.opensAt.toString())
            .putString("smsKeyword", c.smsKeyword)
            .apply()
    }

    fun saveIdNumber(id: String) = secure.put("idNumber", id)
    override fun idNumber(): String? = secure.get("idNumber")

    override fun lastBookedClassDate(): LocalDate? = prefs.getString("lastBooked", null)?.let(LocalDate::parse)
    override fun markBooked(classDate: LocalDate) = prefs.edit().putString("lastBooked", classDate.toString()).apply()

    fun skip(classDate: LocalDate) = prefs.edit().putString("skip", classDate.toString()).apply()
    fun skippedDate(): LocalDate? = prefs.getString("skip", null)?.let(LocalDate::parse)
    override fun consumeSkip(classDate: LocalDate): Boolean {
        if (skippedDate() != classDate) return false
        prefs.edit().remove("skip").apply()
        return true
    }

    fun saveNextRun(run: PlannedRun?) {
        val edit = prefs.edit()
        if (run == null) edit.remove("nextClass").remove("nextOpens").remove("nextStart")
        else edit.putString("nextClass", run.classDate.toString())
            .putString("nextOpens", run.opensAt.toString())
            .putString("nextStart", run.startAt.toString())
        edit.apply()
    }

    fun nextRun(): PlannedRun? {
        val classDate = prefs.getString("nextClass", null) ?: return null
        return PlannedRun(
            LocalDate.parse(classDate),
            LocalDateTime.parse(prefs.getString("nextOpens", null)),
            LocalDateTime.parse(prefs.getString("nextStart", null)),
        )
    }

    @Synchronized
    fun log(line: String) {
        val entry = "${LocalDateTime.now().withNano(0)}  $line\n"
        logFile.appendText(entry)
        if (logFile.length() > 64_000) logFile.writeText(logFile.readText().takeLast(32_000))
    }

    fun readLog(): String = if (logFile.exists()) logFile.readLines().takeLast(200).reversed().joinToString("\n") else ""
}
