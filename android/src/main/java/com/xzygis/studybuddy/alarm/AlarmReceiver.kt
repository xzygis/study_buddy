package com.xzygis.studybuddy.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xzygis.studybuddy.data.AlarmBinding
import com.xzygis.studybuddy.data.Weekday

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val bindingId = intent.getStringExtra(AndroidAlarmScheduler.EXTRA_BINDING_ID) ?: return
        val planId = intent.getStringExtra(AndroidAlarmScheduler.EXTRA_PLAN_ID) ?: return
        val title = intent.getStringExtra(AndroidAlarmScheduler.EXTRA_TITLE) ?: "学习提醒"
        val weekdayNames = intent.getStringArrayExtra(AndroidAlarmScheduler.EXTRA_WEEKDAYS).orEmpty()
        val binding = AlarmBinding(
            id = bindingId,
            reminderId = "",
            title = title,
            hour = intent.getIntExtra(AndroidAlarmScheduler.EXTRA_HOUR, 9),
            minute = intent.getIntExtra(AndroidAlarmScheduler.EXTRA_MINUTE, 0),
            weekdays = weekdayNames.mapNotNull { name ->
                runCatching { Weekday.valueOf(name) }.getOrNull()
            }.toSet(),
        )

        runCatching { AlarmRingingService.start(context, bindingId, title) }
        runCatching { AndroidAlarmScheduler(context).schedule(planId, binding) }
    }
}
