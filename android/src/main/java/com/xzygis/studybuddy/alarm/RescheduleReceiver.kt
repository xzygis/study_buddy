package com.xzygis.studybuddy.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xzygis.studybuddy.StudyBuddyApplication
import com.xzygis.studybuddy.data.SyncPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in supportedActions) return
        val pendingResult = goAsync()
        val application = context.applicationContext as StudyBuddyApplication
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = application.repository.current()
                if (application.alarmScheduler.canScheduleExactAlarms()) {
                    database.records
                        .filter { it.wantsEnabled && it.phase == SyncPhase.ON && !it.pendingDeletion }
                        .forEach { record -> runCatching { application.alarmScheduler.schedule(record) } }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private val supportedActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
