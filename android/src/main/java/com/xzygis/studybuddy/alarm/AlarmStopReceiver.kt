package com.xzygis.studybuddy.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(AlarmNotifier.EXTRA_NOTIFICATION_ID, -1)
        if (notificationId >= 0) AlarmNotifier.stop(context, notificationId)
        AlarmActivity.finishCurrent()
    }
}
