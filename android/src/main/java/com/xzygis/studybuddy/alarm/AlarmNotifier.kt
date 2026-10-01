package com.xzygis.studybuddy.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import com.xzygis.studybuddy.R

object AlarmNotifier {
    const val CHANNEL_ID = "study_alarm"
    const val EXTRA_NOTIFICATION_ID = "notification_id"

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.alarm_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.alarm_channel_description)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 300, 500)
            setSound(sound, attributes)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun show(context: Context, bindingId: String, title: String) {
        createChannel(context)
        val notificationId = notificationId(bindingId)
        val fullScreenIntent = PendingIntent.getActivity(
            context,
            notificationId,
            AlarmActivity.intent(context, bindingId, title, notificationId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            Intent(context, AlarmStopReceiver::class.java).apply {
                data = Uri.parse("studybuddy://${context.packageName}/stop/$bindingId")
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setColor(Color.rgb(23, 107, 90))
            .setContentTitle(title)
            .setContentText("学习计划时间到了")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenIntent, true)
            .setContentIntent(fullScreenIntent)
            .addAction(Notification.Action.Builder(null, "停止", stopIntent).build())
            .build()
            .apply { flags = flags or Notification.FLAG_INSISTENT or Notification.FLAG_NO_CLEAR }
        context.getSystemService(NotificationManager::class.java).notify(notificationId, notification)
    }

    fun stop(context: Context, notificationId: Int) {
        context.getSystemService(NotificationManager::class.java).cancel(notificationId)
    }

    private fun notificationId(bindingId: String) = bindingId.hashCode() and Int.MAX_VALUE
}
