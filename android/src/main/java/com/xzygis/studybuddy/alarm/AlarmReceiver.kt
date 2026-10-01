package com.xzygis.studybuddy.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xzygis.studybuddy.data.AlarmBinding
import com.xzygis.studybuddy.data.Weekday
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.xzygis.studybuddy.StudyBuddyApplication

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val planId = intent.getStringExtra(AndroidAlarmScheduler.EXTRA_PLAN_ID) ?: return
        val bindingId = intent.getStringExtra(AndroidAlarmScheduler.EXTRA_BINDING_ID) ?: return
        val title = intent.getStringExtra(AndroidAlarmScheduler.EXTRA_TITLE) ?: "学习提醒"
        val binding = decodeBinding(intent, bindingId, title)

        runCatching {
            AlarmRingingService.start(context, planId, bindingId, title)
        }
        if (binding != null) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    (context.applicationContext as StudyBuddyApplication)
                        .alarmScheduler
                        .schedule(planId, binding)
                } catch (_: Throwable) {
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
        )
    }

    companion object {
        const val ACTION_FIRE = "com.xzygis.studybuddy.ALARM_FIRE"
    }
}
