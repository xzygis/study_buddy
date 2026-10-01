package com.xzygis.studybuddy.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.xzygis.studybuddy.R

object AlarmNotifier {
    const val CHANNEL_ID = "study_alarm_ringing"
    const val EXTRA_NOTIFICATION_ID = "notification_id"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "学习闹钟",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "按计划响铃的学习提醒"
            setSound(null, null)
            enableVibration(false)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    fun buildNotification(
        context: Context,
        bindingId: String,
        title: String,
        occurrenceId: String,
    ): Notification {
        ensureChannel(context)
        val notificationId = notificationId(bindingId)
        val fullScreen = PendingIntent.getActivity(
            context,
            notificationId,
            AlarmActivity.intent(context, bindingId, title, notificationId, occurrenceId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getBroadcast(
            context,
            notificationId,
            Intent(context, AlarmStopReceiver::class.java).apply {
                data = Uri.parse("studybuddy://${context.packageName}/stop/$bindingId")
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        return builder
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(title)
            .setContentText("学习计划时间到了")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .addAction(Notification.Action.Builder(null, "停止", stop).build())
            .build()
            .apply { flags = flags or Notification.FLAG_INSISTENT or Notification.FLAG_NO_CLEAR }
    }

    fun cancel(context: Context, notificationId: Int) {
        context.getSystemService(NotificationManager::class.java)?.cancel(notificationId)
    }

    fun notificationId(bindingId: String): Int = bindingId.hashCode() and Int.MAX_VALUE
}
