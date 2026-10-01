package com.xzygis.studybuddy.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
            AlarmManager.AlarmClockInfo(triggerAt, showAppPendingIntent()),
            alarmPendingIntent(planId, binding, PendingIntent.FLAG_UPDATE_CURRENT),
        )
    }

    fun cancel(bindingId: String) {
        val pending = cancelPendingIntent(bindingId) ?: return
        alarmManager.cancel(pending)
        pending.cancel()
    }

    fun exists(bindingId: String): Boolean = cancelPendingIntent(bindingId) != null

    fun schedule(record: PlanRecord) {
        record.bindings.forEach { schedule(record.plan.id, it) }
    }

    internal fun nextTrigger(binding: AlarmBinding): ZonedDateTime =
        AlarmTimeCalculator.nextTrigger(binding, ZonedDateTime.now(clock))

    private fun alarmPendingIntent(
        planId: String,
        binding: AlarmBinding,
        flags: Int,
    ): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE
            data = alarmUri(binding.id)
            putExtra(EXTRA_PLAN_ID, planId)
            putExtra(EXTRA_BINDING_ID, binding.id)
            putExtra(EXTRA_REMINDER_ID, binding.reminderId)
            putExtra(EXTRA_TITLE, binding.title)
            putExtra(EXTRA_HOUR, binding.hour)
            putExtra(EXTRA_MINUTE, binding.minute)
            putExtra(EXTRA_RINGTONE_URI, binding.ringtoneUri)
            putExtra(
                EXTRA_WEEKDAYS,
                binding.weekdays.map { it.name }.toTypedArray(),
            )
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(binding.id),
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancelPendingIntent(bindingId: String): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE
            data = alarmUri(bindingId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(bindingId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun showAppPendingIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, com.xzygis.studybuddy.MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun alarmUri(bindingId: String): Uri =
        Uri.parse("studybuddy://${context.packageName}/alarm/$bindingId")

    private fun requestCode(bindingId: String) = bindingId.hashCode()

    companion object {
        const val EXTRA_PLAN_ID = "plan_id"
        const val EXTRA_BINDING_ID = "binding_id"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_HOUR = "hour"
        const val EXTRA_MINUTE = "minute"
        const val EXTRA_WEEKDAYS = "weekdays"
        const val EXTRA_RINGTONE_URI = "ringtone_uri"
    }
}
