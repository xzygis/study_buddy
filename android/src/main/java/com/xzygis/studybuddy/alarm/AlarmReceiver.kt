package com.xzygis.studybuddy.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xzygis.studybuddy.StudyBuddyApplication
import com.xzygis.studybuddy.data.AlarmBinding
import com.xzygis.studybuddy.data.Weekday
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val planId = intent.getStringExtra(AndroidAlarmScheduler.EXTRA_PLAN_ID) ?: return
        val bindingId = intent.getStringExtra(AndroidAlarmScheduler.EXTRA_BINDING_ID) ?: return
        val title = intent.getStringExtra(AndroidAlarmScheduler.EXTRA_TITLE) ?: "学习提醒"
        val triggerAt = intent.getLongExtra(
            AndroidAlarmScheduler.EXTRA_TRIGGER_AT,
            System.currentTimeMillis(),
        )
        val occurrenceId = intent.getStringExtra(AndroidAlarmScheduler.EXTRA_OCCURRENCE_ID)
            ?: AlarmDiagnosticStore.occurrenceId(bindingId, triggerAt)
        val application = context.applicationContext as StudyBuddyApplication
        application.alarmDiagnostics.recordReceiver(
            occurrenceId = occurrenceId,
            planId = planId,
            bindingId = bindingId,
            title = title,
            triggerAt = triggerAt,
            timestamp = System.currentTimeMillis(),
        )
        val binding = decodeBinding(intent, bindingId, title)

        runCatching {
            AlarmRingingService.start(
                context = context,
                planId = planId,
                bindingId = bindingId,
                title = title,
                ringtoneUri = binding?.ringtoneUri,
                occurrenceId = occurrenceId,
            )
        }.onFailure {
            application.alarmDiagnostics.recordError(occurrenceId, "start_service", it)
        }
        if (binding != null) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    application.alarmScheduler.schedule(planId, binding)
                } catch (error: Throwable) {
                    application.alarmDiagnostics.recordError(occurrenceId, "reschedule", error)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun decodeBinding(
        intent: Intent,
        bindingId: String,
        title: String,
    ): AlarmBinding? {
        val weekdays = intent.getStringArrayExtra(AndroidAlarmScheduler.EXTRA_WEEKDAYS).orEmpty()
            .mapNotNull { runCatching { Weekday.valueOf(it) }.getOrNull() }
            .toSet()
        if (weekdays.isEmpty()) return null
        return AlarmBinding(
            id = bindingId,
            reminderId = intent.getStringExtra(AndroidAlarmScheduler.EXTRA_REMINDER_ID) ?: "",
            title = title,
            hour = intent.getIntExtra(AndroidAlarmScheduler.EXTRA_HOUR, 9),
            minute = intent.getIntExtra(AndroidAlarmScheduler.EXTRA_MINUTE, 0),
            weekdays = weekdays,
            ringtoneUri = intent.getStringExtra(AndroidAlarmScheduler.EXTRA_RINGTONE_URI),
        )
    }

    companion object {
        const val ACTION_FIRE = "com.xzygis.studybuddy.ALARM_FIRE"
    }
}
