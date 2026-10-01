package com.xzygis.studybuddy.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
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
import com.xzygis.studybuddy.ui.StudyBuddyTheme
import com.xzygis.studybuddy.ui.StudyGreen
import java.lang.ref.WeakReference

class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = WeakReference(this)
        setupLockScreenWindow()
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        render()
    }

    override fun onDestroy() {
        if (current?.get() === this) current = null
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
        val title = intent.getStringExtra(AndroidAlarmScheduler.EXTRA_TITLE) ?: "学习提醒"
        val notificationId = intent.getIntExtra(AlarmNotifier.EXTRA_NOTIFICATION_ID, -1)
        setContent {
            StudyBuddyTheme {
                AlarmScreen(title) {
                    AlarmRingingService.stop(this)
                    if (notificationId >= 0) AlarmNotifier.cancel(this, notificationId)
                    finishAndRemoveTask()
                }
            }
        }
    }

    companion object {
        private var current: WeakReference<AlarmActivity>? = null

        fun intent(context: Context, bindingId: String, title: String, notificationId: Int) =
            Intent(context, AlarmActivity::class.java).apply {
                putExtra(AndroidAlarmScheduler.EXTRA_BINDING_ID, bindingId)
                putExtra(AndroidAlarmScheduler.EXTRA_TITLE, title)
                putExtra(AlarmNotifier.EXTRA_NOTIFICATION_ID, notificationId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

        fun finishCurrent() {
            current?.get()?.finishAndRemoveTask()
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
