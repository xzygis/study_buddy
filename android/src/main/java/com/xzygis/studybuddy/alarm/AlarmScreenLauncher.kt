package com.xzygis.studybuddy.alarm

import android.annotation.TargetApi
import android.app.ActivityOptions
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.os.PowerManager

object AlarmScreenLauncher {
    fun pendingIntent(
        context: Context,
        bindingId: String,
        title: String,
        notificationId: Int,
        occurrenceId: String,
    ): PendingIntent = PendingIntent.getActivity(
        context,
        notificationId,
        AlarmActivity.intent(context, bindingId, title, notificationId, occurrenceId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        creatorOptions(),
    )

    fun launchIfLocked(
        context: Context,
        bindingId: String,
        title: String,
        occurrenceId: String,
    ): Boolean {
        val isLocked = context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        val isInteractive = context.getSystemService(PowerManager::class.java)?.isInteractive != false
        if (!shouldLaunchImmediately(isLocked, isInteractive)) return false
        if (!AlarmNotifier.canShowFullScreen(context)) return false

        val notificationId = AlarmNotifier.notificationId(bindingId)
        val pendingIntent = pendingIntent(
            context = context,
            bindingId = bindingId,
            title = title,
            notificationId = notificationId,
            occurrenceId = occurrenceId,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            pendingIntent.send(senderOptions())
        } else {
            pendingIntent.send()
        }
        return true
    }

    internal fun shouldLaunchImmediately(isLocked: Boolean, isInteractive: Boolean): Boolean =
        isLocked || !isInteractive

    private fun creatorOptions() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic()
                .setPendingIntentCreatorBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                )
                .toBundle()
        } else {
            null
        }

    @TargetApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun senderOptions() =
        ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
            )
            .toBundle()
}
