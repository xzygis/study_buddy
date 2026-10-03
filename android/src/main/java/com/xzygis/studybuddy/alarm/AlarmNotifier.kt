package com.xzygis.studybuddy.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.xzygis.studybuddy.R

object AlarmNotifier {
    const val CHANNEL_ID = "study_alarm_ringing"
    const val EXTRA_NOTIFICATION_ID = "notification_id"

    fun ensureChannel(context: Context) {
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

    fun hasHighImportanceChannel(context: Context): Boolean {
        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        val channel = manager.getNotificationChannel(CHANNEL_ID) ?: return false
        return manager.areNotificationsEnabled() &&
            channel.importance >= NotificationManager.IMPORTANCE_HIGH &&
            channel.lockscreenVisibility != Notification.VISIBILITY_SECRET
    }

    fun canShowFullScreen(context: Context): Boolean {
        if (!hasHighImportanceChannel(context)) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true
    }

    fun fullScreenSettingsIntent(context: Context): Intent {
        val manager = context.getSystemService(NotificationManager::class.java)
        return if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            manager?.canUseFullScreenIntent() != true
        ) {
            Intent(
                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                Uri.parse("package:${context.packageName}"),
            )
        } else {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
        }
    }

    fun buildNotification(
        context: Context,
        bindingId: String,
        title: String,
        occurrenceId: String,
    ): Notification {
        ensureChannel(context)
        val notificationId = notificationId(bindingId)
        val fullScreen = AlarmScreenLauncher.pendingIntent(
            context = context,
            bindingId = bindingId,
            title = title,
            notificationId = notificationId,
            occurrenceId = occurrenceId,
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
        val builder = Notification.Builder(context, CHANNEL_ID)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
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
