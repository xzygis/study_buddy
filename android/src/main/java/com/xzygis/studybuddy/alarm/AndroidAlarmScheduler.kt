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
        val pending = alarmPendingIntent(
            planId = planId,
            binding = binding,
            triggerAt = triggerAt,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAt, pending),
            pending,
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
        triggerAt: Long,
        flags: Int,
    ): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode(binding.id),
        AlarmActivity.alarmIntent(context, planId, binding, triggerAt),
        flags,
    )

    private fun cancelPendingIntent(bindingId: String): PendingIntent? = PendingIntent.getActivity(
        context,
        requestCode(bindingId),
        AlarmActivity.cancelProbeIntent(context, bindingId),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun requestCode(bindingId: String) = bindingId.hashCode()
}
