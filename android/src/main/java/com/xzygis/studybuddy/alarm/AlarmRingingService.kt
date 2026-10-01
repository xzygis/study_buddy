package com.xzygis.studybuddy.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat

class AlarmRingingService : Service() {
    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val bindingId = intent?.getStringExtra(AndroidAlarmScheduler.EXTRA_BINDING_ID)
            ?: return START_NOT_STICKY
        val title = intent.getStringExtra(AndroidAlarmScheduler.EXTRA_TITLE) ?: "学习提醒"
        val notificationId = AlarmNotifier.notificationId(bindingId)
        startForeground(
            notificationId,
            AlarmNotifier.buildNotification(this, bindingId, title, notificationId),
        )
        acquireWakeLock()
        startAlarmSound()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        player?.runCatching {
            stop()
            release()
        }
        player = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:study-alarm")
            .apply { acquire(WAKE_LOCK_TIMEOUT_MILLIS) }
    }

    private fun startAlarmSound() {
        player?.runCatching {
            stop()
            release()
        }
        player = null
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(this@AlarmRingingService, sound)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
    }

    companion object {
        private const val ACTION_START = "com.xzygis.studybuddy.action.START_ALARM"
        private const val WAKE_LOCK_TIMEOUT_MILLIS = 2 * 60 * 60 * 1_000L

        fun start(context: Context, bindingId: String, title: String) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AlarmRingingService::class.java).apply {
                    action = ACTION_START
                    putExtra(AndroidAlarmScheduler.EXTRA_BINDING_ID, bindingId)
                    putExtra(AndroidAlarmScheduler.EXTRA_TITLE, title)
                },
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AlarmRingingService::class.java))
        }
    }
}
