package com.xzygis.studybuddy.alarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmScreenLauncherTest {
    @Test
    fun launchesWhenKeyguardIsLocked() {
        assertTrue(
            AlarmScreenLauncher.shouldLaunchImmediately(
                isLocked = true,
                isInteractive = true,
            ),
        )
    }

    @Test
    fun launchesWhenScreenIsOff() {
        assertTrue(
            AlarmScreenLauncher.shouldLaunchImmediately(
                isLocked = false,
                isInteractive = false,
            ),
        )
    }

    @Test
    fun leavesUnlockedInteractiveScreenToFullScreenNotificationPolicy() {
        assertFalse(
            AlarmScreenLauncher.shouldLaunchImmediately(
                isLocked = false,
                isInteractive = true,
            ),
        )
    }

    @Test
    fun launchesDirectlyOnlyForRelevantOemWithOverlayPermission() {
        assertTrue(
            AlarmScreenLauncher.shouldLaunchDirectly(
                isOemRelevant = true,
                canDrawOverlays = true,
            ),
        )
        assertFalse(
            AlarmScreenLauncher.shouldLaunchDirectly(
                isOemRelevant = true,
                canDrawOverlays = false,
            ),
        )
        assertFalse(
            AlarmScreenLauncher.shouldLaunchDirectly(
                isOemRelevant = false,
                canDrawOverlays = true,
            ),
        )
    }
}
