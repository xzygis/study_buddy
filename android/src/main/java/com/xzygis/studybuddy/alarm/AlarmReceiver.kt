package com.xzygis.studybuddy.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xzygis.studybuddy.StudyBuddyApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val planId = intent.getStringExtra(AndroidAlarmScheduler.EXTRA_PLAN_ID) ?: return
        val bindingId = intent.getStringExtra(AndroidAlarmScheduler.EXTRA_BINDING_ID) ?: return
        val triggerAt = intent.getLongExtra(
            AndroidAlarmScheduler.EXTRA_TRIGGER_AT,
            System.currentTimeMillis(),
        )
        val occurrenceId = intent.getStringExtra(AndroidAlarmScheduler.EXTRA_OCCURRENCE_ID)
            ?: AlarmDiagnosticStore.occurrenceId(bindingId, triggerAt)
        val application = context.applicationContext as StudyBuddyApplication
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val binding = application.repository.current()
                    .activeAlarmBinding(planId = planId, bindingId = bindingId)
                if (binding == null) {
                    application.alarmDiagnostics.recordCancelled(
                        bindingId = bindingId,
                        timestamp = System.currentTimeMillis(),
                    )
                    AlarmNotifier.cancel(context, AlarmNotifier.notificationId(bindingId))
                    return@launch
                }
                application.alarmDiagnostics.recordReceiver(
                    occurrenceId = occurrenceId,
                    planId = planId,
                    bindingId = bindingId,
                    title = binding.title,
                    triggerAt = triggerAt,
                    timestamp = System.currentTimeMillis(),
                )
                runCatching {
                    AlarmScreenLauncher.launchIfLocked(
                        context = context,
                        bindingId = bindingId,
                        title = binding.title,
                        occurrenceId = occurrenceId,
                    )
                }.onFailure {
                    application.alarmDiagnostics.recordError(occurrenceId, "launch_screen", it)
                }
                runCatching {
                    AlarmRingingService.start(
                        context = context,
                        planId = planId,
                        bindingId = bindingId,
                        title = binding.title,
                        ringtoneUri = binding.ringtoneUri,
                        occurrenceId = occurrenceId,
                    )
                }.onFailure {
                    application.alarmDiagnostics.recordError(occurrenceId, "start_service", it)
                }
                try {
                    application.alarmScheduler.schedule(planId, binding)
                } catch (error: Throwable) {
                    application.alarmDiagnostics.recordError(occurrenceId, "reschedule", error)
                }
            } catch (error: Throwable) {
                application.alarmDiagnostics.recordError(occurrenceId, "validate", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.xzygis.studybuddy.ALARM_FIRE"
    }
}
