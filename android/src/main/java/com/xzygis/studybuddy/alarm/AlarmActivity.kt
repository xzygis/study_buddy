package com.xzygis.studybuddy.alarm

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xzygis.studybuddy.StudyBuddyApplication
import com.xzygis.studybuddy.data.AlarmBinding
import com.xzygis.studybuddy.data.Weekday
import com.xzygis.studybuddy.ui.StudyBuddyTheme
import com.xzygis.studybuddy.ui.StudyGreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class AlarmActivity : ComponentActivity() {
    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var vibrator: Vibrator? = null
    private var currentBindingId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = WeakReference(this)
        setupLockScreenWindow()
        ensureAlarmChannel(this)
        startRinging()
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        startRinging()
        render()
    }

    override fun onDestroy() {
        if (current?.get() === this) current = null
        stopRinging()
        super.onDestroy()
    }

    private fun setupLockScreenWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun render() {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "学习提醒"
        setContent {
            StudyBuddyTheme {
                AlarmScreen(title) { stopAndReschedule() }
            }
        }
    }

    private fun startRinging() {
        val bindingId = intent.getStringExtra(EXTRA_BINDING_ID) ?: return
        if (bindingId == currentBindingId) return
        currentBindingId = bindingId
        stopPlayer()
        acquireWakeLock()
        startAlarmSound()
        startVibration()
    }

    private fun stopRinging() {
        stopPlayer()
        stopVibration()
        releaseWakeLock()
    }

    private fun stopAndReschedule() {
        val planId = intent.getStringExtra(EXTRA_PLAN_ID)
        val bindingId = intent.getStringExtra(EXTRA_BINDING_ID)
        val binding = bindingFromIntent()
        stopRinging()
        if (planId != null && binding != null) {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    (applicationContext as StudyBuddyApplication)
                        .alarmScheduler
                        .schedule(planId, binding)
                }
            }
        }
        bindingId?.let { getSystemService(NotificationManager::class.java)?.cancel(it.hashCode()) }
        finishAndRemoveTask()
    }

    private fun bindingFromIntent(): AlarmBinding? {
        val bindingId = intent.getStringExtra(EXTRA_BINDING_ID) ?: return null
        val weekdays = intent.getStringArrayExtra(EXTRA_WEEKDAYS).orEmpty()
            .mapNotNull { runCatching { Weekday.valueOf(it) }.getOrNull() }
            .toSet()
        if (weekdays.isEmpty()) return null
        return AlarmBinding(
            id = bindingId,
            reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: "",
            title = intent.getStringExtra(EXTRA_TITLE) ?: "学习提醒",
            hour = intent.getIntExtra(EXTRA_HOUR, 9),
            minute = intent.getIntExtra(EXTRA_MINUTE, 0),
            weekdays = weekdays,
        )
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "$packageName:study-alarm",
            )
            ?.apply { acquire(WAKE_LOCK_TIMEOUT_MILLIS) }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun startAlarmSound() {
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
                setDataSource(this@AlarmActivity, sound)
                isLooping = true
                prepare()
                start()
            }
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
        const val EXTRA_PLAN_ID = "plan_id"
        const val EXTRA_BINDING_ID = "binding_id"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_HOUR = "hour"
        const val EXTRA_MINUTE = "minute"
        const val EXTRA_WEEKDAYS = "weekdays"
        const val EXTRA_TRIGGER_AT = "trigger_at"
        const val ALARM_CHANNEL_ID = "study_alarm_ringing"

        private const val WAKE_LOCK_TIMEOUT_MILLIS = 10 * 60 * 1_000L
        private var current: WeakReference<AlarmActivity>? = null

        fun alarmIntent(
            context: Context,
            planId: String,
            binding: AlarmBinding,
            triggerAt: Long,
        ): Intent = Intent(context, AlarmActivity::class.java).apply {
            action = "com.xzygis.studybuddy.ALARM_RING"
            data = alarmUri(context, binding.id)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
            putExtra(EXTRA_PLAN_ID, planId)
            putExtra(EXTRA_BINDING_ID, binding.id)
            putExtra(EXTRA_REMINDER_ID, binding.reminderId)
            putExtra(EXTRA_TITLE, binding.title)
            putExtra(EXTRA_HOUR, binding.hour)
            putExtra(EXTRA_MINUTE, binding.minute)
            putExtra(
                EXTRA_WEEKDAYS,
                binding.weekdays.map { it.name }.toTypedArray(),
            )
            putExtra(EXTRA_TRIGGER_AT, triggerAt)
        }

        fun cancelProbeIntent(context: Context, bindingId: String): Intent =
            Intent(context, AlarmActivity::class.java).apply {
                action = "com.xzygis.studybuddy.ALARM_RING"
                data = alarmUri(context, bindingId)
            }

        private fun alarmUri(context: Context, bindingId: String): Uri =
            Uri.parse("studybuddy://${context.packageName}/alarm/$bindingId")

        private fun ensureAlarmChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(ALARM_CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                ALARM_CHANNEL_ID,
                "学习闹钟",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "按计划响铃的学习提醒"
                setSound(null, null)
                enableVibration(false)
                setBypassDnd(true)
            }
            manager.createNotificationChannel(channel)
        }

        fun finishCurrent(context: Context, bindingId: String? = null) {
            val activity = current?.get() ?: return
            if (bindingId == null || activity.currentBindingId == bindingId) {
                activity.finishAndRemoveTask()
            }
        }
    }
}

@Composable
private fun AlarmScreen(title: String, onStop: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudyGreen)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .background(Color.White.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Alarm,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(58.dp),
            )
        }
        Text(
            text = title,
            modifier = Modifier.padding(top = 32.dp),
            color = Color.White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "学习计划时间到了",
            modifier = Modifier.padding(top = 10.dp, bottom = 48.dp),
            color = Color.White.copy(alpha = 0.82f),
            style = MaterialTheme.typography.titleMedium,
        )
        Button(onClick = onStop) {
            Text("停止闹钟", modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp))
        }
    }
}
