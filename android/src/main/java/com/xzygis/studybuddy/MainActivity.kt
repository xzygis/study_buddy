package com.xzygis.studybuddy

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.xzygis.studybuddy.ui.StudyBuddyApp
import com.xzygis.studybuddy.ui.StudyBuddyTheme

class MainActivity : ComponentActivity() {
    private val applicationContainer get() = application as StudyBuddyApplication
    private val plans: PlanViewModel by viewModels {
        PlanViewModel.Factory(
            applicationContainer.repository,
            applicationContainer.alarmScheduler,
        )
    }
    private var pendingEnablePlanId: String? = null
    private var pendingSettings: PendingSettings? = null
    private var permissionEpoch by mutableIntStateOf(0)

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) continueEnableFlow() else pendingEnablePlanId = null
    }

    private val settingsPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val granted = when (pendingSettings) {
            PendingSettings.EXACT_ALARM -> applicationContainer.alarmScheduler.canScheduleExactAlarms()
            PendingSettings.FULL_SCREEN -> canUseFullScreenIntent()
            null -> false
        }
        pendingSettings = null
        if (granted) continueEnableFlow() else pendingEnablePlanId = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            permissionEpoch
            StudyBuddyTheme {
                StudyBuddyApp(
                    viewModel = plans,
                    hasNotificationPermission = hasNotificationPermission(),
                    canScheduleExactAlarms = applicationContainer.alarmScheduler.canScheduleExactAlarms(),
                    canUseFullScreenIntent = canUseFullScreenIntent(),
                    onEnablePlan = ::beginEnableFlow,
                    onOpenNotificationSettings = {
                        startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
                        )
                    },
                    onOpenExactAlarmSettings = {
                        applicationContainer.alarmScheduler.exactAlarmSettingsIntent()
                            ?.let {
                                pendingSettings = PendingSettings.EXACT_ALARM
                                settingsPermission.launch(it)
                            }
                    },
                    onOpenFullScreenSettings = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            pendingSettings = PendingSettings.FULL_SCREEN
                            settingsPermission.launch(
                                Intent(
                                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                    android.net.Uri.parse("package:$packageName"),
                                ),
                            )
                        }
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionEpoch += 1
        plans.reconcile()
    }

    private fun beginEnableFlow(planId: String) {
        pendingEnablePlanId = planId
        continueEnableFlow()
    }

    private fun continueEnableFlow() {
        val planId = pendingEnablePlanId ?: return
        if (!hasNotificationPermission()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            return
        }
        applicationContainer.alarmScheduler.exactAlarmSettingsIntent()?.let {
            pendingSettings = PendingSettings.EXACT_ALARM
            settingsPermission.launch(it)
            return
        }
        if (!canUseFullScreenIntent() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            pendingSettings = PendingSettings.FULL_SCREEN
            settingsPermission.launch(
                Intent(
                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    android.net.Uri.parse("package:$packageName"),
                ),
            )
            return
        }
        pendingEnablePlanId = null
        plans.setEnabled(planId, true)
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    private fun canUseFullScreenIntent(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    private enum class PendingSettings { EXACT_ALARM, FULL_SCREEN }
}
