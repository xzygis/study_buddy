package com.xzygis.studybuddy.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import com.xzygis.studybuddy.data.AlarmBinding

class AndroidAlarmScheduler(private val context: Context) {
    fun schedule(planName: String, binding: AlarmBinding) {
        val intent = systemClockIntent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, binding.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, binding.minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, SystemClockAlarmSpec.label(planName, binding.title))
            putExtra(AlarmClock.EXTRA_VIBRATE, true)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            putIntegerArrayListExtra(
                AlarmClock.EXTRA_DAYS,
                ArrayList(SystemClockAlarmSpec.days(binding)),
            )
        }
        context.startActivity(intent)
    }

    fun showSystemAlarms() {
        val intent = systemClockIntent(AlarmClock.ACTION_SHOW_ALARMS)
        context.startActivity(intent)
    }

    fun cancelLegacyAlarm(bindingId: String) {
        val intent = Intent().apply {
            component = ComponentName(context, LEGACY_RECEIVER_CLASS)
            data = Uri.parse("studybuddy://${context.packageName}/alarm/$bindingId")
        }
        val pending = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(pending)
        pending.cancel()
    }

    private fun systemClockIntent(action: String) =
        Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    companion object {
        private const val LEGACY_RECEIVER_CLASS =
            "com.xzygis.studybuddy.alarm.AlarmReceiver"
    }
}

internal object SystemClockAlarmSpec {
    fun label(planName: String, reminderTitle: String): String =
        "StudyBuddy · $planName · $reminderTitle"

    fun days(binding: AlarmBinding): List<Int> =
        binding.weekdays.map { it.calendarValue }.sorted()
}
