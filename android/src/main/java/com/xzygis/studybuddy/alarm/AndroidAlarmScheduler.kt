package com.xzygis.studybuddy.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.xzygis.studybuddy.MainActivity
import com.xzygis.studybuddy.data.AlarmBinding
import com.xzygis.studybuddy.data.PlanRecord
import java.time.Clock
import java.time.ZonedDateTime

class AndroidAlarmScheduler(
    private val context: Context,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun exactAlarmSettingsIntent(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExactAlarms()) {
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${context.packageName}"),
            )
        } else {
            null
        }

    fun schedule(planId: String, binding: AlarmBinding) {
        check(canScheduleExactAlarms()) { "系统尚未允许精确闹钟，请先授权。" }
        val triggerAt = nextTrigger(binding).toInstant().toEpochMilli()
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAt, showAppIntent()),
            alarmIntent(planId, binding, PendingIntent.FLAG_UPDATE_CURRENT),
        )
    }

    fun cancel(bindingId: String) {
        val pending = alarmPendingIntent(bindingId, PendingIntent.FLAG_NO_CREATE) ?: return
        alarmManager.cancel(pending)
        pending.cancel()
    }

    fun exists(bindingId: String): Boolean =
        alarmPendingIntent(bindingId, PendingIntent.FLAG_NO_CREATE) != null

    fun schedule(record: PlanRecord) {
        record.bindings.forEach { schedule(record.plan.id, it) }
    }

    internal fun nextTrigger(binding: AlarmBinding): ZonedDateTime =
        AlarmTimeCalculator.nextTrigger(binding, ZonedDateTime.now(clock))

    private fun alarmIntent(
        planId: String,
        binding: AlarmBinding,
        flags: Int,
    ): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            data = alarmUri(binding.id)
            putExtra(EXTRA_PLAN_ID, planId)
            putExtra(EXTRA_BINDING_ID, binding.id)
            putExtra(EXTRA_TITLE, binding.title)
            putExtra(EXTRA_HOUR, binding.hour)
            putExtra(EXTRA_MINUTE, binding.minute)
            putExtra(EXTRA_WEEKDAYS, binding.weekdays.map { it.name }.toTypedArray())
        }
        return PendingIntent.getBroadcast(context, 0, intent, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun alarmPendingIntent(bindingId: String, flags: Int): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            data = alarmUri(bindingId)
        }
        return PendingIntent.getBroadcast(context, 0, intent, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun showAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun alarmUri(bindingId: String) =
        Uri.parse("studybuddy://${context.packageName}/alarm/$bindingId")

    companion object {
        const val EXTRA_PLAN_ID = "plan_id"
        const val EXTRA_BINDING_ID = "binding_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_HOUR = "hour"
        const val EXTRA_MINUTE = "minute"
        const val EXTRA_WEEKDAYS = "weekdays"
    }
}
