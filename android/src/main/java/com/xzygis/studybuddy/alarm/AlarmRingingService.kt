package com.xzygis.studybuddy.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.xzygis.studybuddy.StudyBuddyApplication

class AlarmRingingService : Service() {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentBindingId: String? = null
    private val diagnostics: AlarmDiagnosticStore
        get() = (application as StudyBuddyApplication).alarmDiagnostics

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val bindingId = intent?.getStringExtra(AndroidAlarmScheduler.EXTRA_BINDING_ID)
        val title = intent?.getStringExtra(AndroidAlarmScheduler.EXTRA_TITLE) ?: "学习提醒"
        val ringtoneUri = intent?.getStringExtra(AndroidAlarmScheduler.EXTRA_RINGTONE_URI)
        val occurrenceId = intent?.getStringExtra(AndroidAlarmScheduler.EXTRA_OCCURRENCE_ID)
            ?: bindingId?.let { AlarmDiagnosticStore.occurrenceId(it, System.currentTimeMillis()) }
        if (bindingId == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val resolvedOccurrenceId = occurrenceId
            ?: AlarmDiagnosticStore.occurrenceId(bindingId, System.currentTimeMillis())
        if (action == ACTION_STOP || bindingId == currentBindingId && player != null) {
            if (action == ACTION_STOP) {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        try {
            enterForeground(bindingId, title, resolvedOccurrenceId)
            diagnostics.recordService(resolvedOccurrenceId, System.currentTimeMillis())
        } catch (error: Exception) {
            diagnostics.recordError(resolvedOccurrenceId, "foreground", error)
            stopSelf()
            return START_NOT_STICKY
        }
        if (bindingId != currentBindingId) {
            currentBindingId = bindingId
            acquireWakeLock()
            launchAlarmScreen(bindingId, title, resolvedOccurrenceId)
            if (startAlarmSound(ringtoneUri, resolvedOccurrenceId)) {
                diagnostics.recordAudio(resolvedOccurrenceId, System.currentTimeMillis())
            } else {
                diagnostics.recordError(resolvedOccurrenceId, "audio: no playable ringtone")
            }
            startVibration()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopPlayer()
        stopVibration()
        releaseWakeLock()
        currentBindingId?.let { AlarmNotifier.cancel(this, AlarmNotifier.notificationId(it)) }
        currentBindingId = null
        AlarmActivity.finishCurrent()
        super.onDestroy()
    }

    private fun enterForeground(bindingId: String, title: String, occurrenceId: String) {
        val notificationId = AlarmNotifier.notificationId(bindingId)
        val notification = AlarmNotifier.buildNotification(this, bindingId, title, occurrenceId)
        ServiceCompat.startForeground(
            this,
            notificationId,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            },
        )
    }

    private fun launchAlarmScreen(bindingId: String, title: String, occurrenceId: String) {
        runCatching {
            startActivity(
                AlarmActivity.intent(
                    context = this,
                    bindingId = bindingId,
                    title = title,
                    notificationId = AlarmNotifier.notificationId(bindingId),
                    occurrenceId = occurrenceId,
                ),
            )
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:study-alarm")
            ?.apply { acquire(WAKE_LOCK_TIMEOUT_MILLIS) }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun startAlarmSound(ringtoneUri: String?, occurrenceId: String?): Boolean {
        stopPlayer()
        val sounds = listOfNotNull(
            ringtoneUri?.let { runCatching { Uri.parse(it) }.getOrNull() },
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        ).distinct()
        player = sounds.firstNotNullOfOrNull { createPlayer(it, occurrenceId) }
        return player != null
    }

    private fun createPlayer(sound: Uri, occurrenceId: String?): MediaPlayer? {
        val candidate = MediaPlayer()
        return runCatching {
            candidate.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(this@AlarmRingingService, sound)
                isLooping = true
                setOnErrorListener { _, what, extra ->
                    occurrenceId?.let {
                        diagnostics.recordError(
                            it,
                            "audio_runtime: MediaPlayer what=$what extra=$extra",
                        )
                    }
                    false
                }
                prepare()
                start()
            }
        }.onFailure {
            candidate.runCatching { release() }
        }.getOrNull()
    }

    private fun stopPlayer() {
        player?.runCatching {
            if (isPlaying) stop()
            release()
        }
        player = null
    }

    private fun startVibration() {
        val vib = resolveVibrator() ?: return
        vibrator = vib
        val pattern = longArrayOf(0, 600, 400)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(pattern, 0)
        }
    }

    private fun stopVibration() {
        vibrator?.cancel()
        vibrator = null
    }

    private fun resolveVibrator(): Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Vibrator::class.java)
    }

    companion object {
        private const val ACTION_START = "com.xzygis.studybuddy.action.START_ALARM"
        private const val ACTION_STOP = "com.xzygis.studybuddy.action.STOP_ALARM"
        private const val WAKE_LOCK_TIMEOUT_MILLIS = 10 * 60 * 1_000L

        fun start(
            context: Context,
            planId: String,
            bindingId: String,
            title: String,
            ringtoneUri: String?,
            occurrenceId: String,
        ) {
            val intent = Intent(context, AlarmRingingService::class.java).apply {
                action = ACTION_START
                putExtra(AndroidAlarmScheduler.EXTRA_PLAN_ID, planId)
                putExtra(AndroidAlarmScheduler.EXTRA_BINDING_ID, bindingId)
                putExtra(AndroidAlarmScheduler.EXTRA_TITLE, title)
                putExtra(AndroidAlarmScheduler.EXTRA_RINGTONE_URI, ringtoneUri)
                putExtra(AndroidAlarmScheduler.EXTRA_OCCURRENCE_ID, occurrenceId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AlarmRingingService::class.java))
        }
    }
}
