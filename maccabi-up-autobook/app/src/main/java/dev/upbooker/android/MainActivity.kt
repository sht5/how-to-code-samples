package dev.upbooker.android

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import dev.upbooker.core.BookingConfig
import dev.upbooker.core.Schedule
import dev.upbooker.databinding.ActivityMainBinding
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var ui: ActivityMainBinding
    private lateinit var store: AppStore

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ActivityMainBinding.inflate(layoutInflater)
        setContentView(ui.root)
        store = AppStore(this)
        Notifier.createChannels(this)

        ui.classDay.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            DayOfWeek.entries.map { it.getDisplayName(TextStyle.FULL, Locale.getDefault()) },
        )
        load(store.config())

        ui.save.setOnClickListener { save() }
        ui.dryRun.setOnClickListener {
            BookingService.start(this, dryRun = true)
            Toast.makeText(this, "Dry run started. Text this phone a test code.", Toast.LENGTH_LONG).show()
        }
        ui.skipNext.setOnClickListener {
            store.nextRun()?.let {
                store.skip(it.classDate)
                Toast.makeText(this, "Will skip ${it.classDate}", Toast.LENGTH_SHORT).show()
            }
            refresh()
        }
        ui.grantPermissions.setOnClickListener { requestEverything() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun load(c: BookingConfig) {
        ui.enabled.isChecked = c.enabled
        ui.classDay.setSelection(c.classDay.value - 1)
        ui.classTime.setText(c.classTime.toString())
        ui.backupTime.setText(c.backupTime?.toString().orEmpty())
        ui.activityKeyword.setText(c.activityKeyword)
        ui.studioKeyword.setText(c.studioKeyword)
        ui.opensDaysBefore.setText(c.opensDaysBefore.toString())
        ui.opensAt.setText(c.opensAt.toString())
        ui.smsKeyword.setText(c.smsKeyword)
    }

    private fun save() {
        val config = try {
            BookingConfig(
                enabled = ui.enabled.isChecked,
                classDay = DayOfWeek.of(ui.classDay.selectedItemPosition + 1),
                classTime = LocalTime.parse(ui.classTime.text.toString().trim()),
                backupTime = ui.backupTime.text.toString().trim().takeIf { it.isNotEmpty() }?.let(LocalTime::parse),
                activityKeyword = ui.activityKeyword.text.toString().trim(),
                studioKeyword = ui.studioKeyword.text.toString().trim(),
                opensDaysBefore = ui.opensDaysBefore.text.toString().trim().toInt(),
                opensAt = LocalTime.parse(ui.opensAt.text.toString().trim()),
                smsKeyword = ui.smsKeyword.text.toString().trim(),
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Check the fields (times are HH:mm): ${e.message}", Toast.LENGTH_LONG).show()
            return
        }
        val id = ui.idNumber.text.toString().trim()
        if (id.isNotEmpty()) {
            store.saveIdNumber(id)
            ui.idNumber.text?.clear()
        }
        store.saveConfig(config)
        AlarmScheduler.scheduleNext(this)
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        refresh()
    }

    @SuppressLint("BatteryLife")
    private fun requestEverything() {
        val wanted = mutableListOf(Manifest.permission.RECEIVE_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) wanted += Manifest.permission.POST_NOTIFICATIONS
        permissions.launch(wanted.toTypedArray())

        if (!AlarmScheduler.canScheduleExact(getSystemService(AlarmManager::class.java)) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        } else if (!getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)) {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        }
    }

    private fun refresh() {
        val next = store.nextRun()
        val skipped = store.skippedDate()
        val missing = buildList {
            if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PERMISSION_GRANTED) add("SMS")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PERMISSION_GRANTED
            ) add("notifications")
            if (!AlarmScheduler.canScheduleExact(getSystemService(AlarmManager::class.java))) add("exact alarms")
            if (!getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)) {
                add("battery optimization exemption")
            }
            if (store.idNumber() == null) add("ID number")
        }
        ui.status.text = buildString {
            appendLine(
                if (next == null) "Auto-booking is off"
                else "Next: class on ${next.classDate}, booking opens ${next.opensAt} (login starts ${next.startAt.toLocalTime()})"
            )
            if (skipped != null) appendLine("Skipping class on $skipped")
            if (missing.isNotEmpty()) appendLine("Missing: ${missing.joinToString()}")
            append("Now: ${LocalDateTime.now(Schedule.ZONE).withNano(0)} (Israel)")
        }
        ui.log.text = store.readLog()
    }

    private companion object {
        const val PERMISSION_GRANTED = android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
