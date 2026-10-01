package com.xzygis.studybuddy.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineStatusTest {
    @Test
    fun onlyLatestRecentReminderIsActive() {
        assertEquals(
            listOf(
                TimelineStatus.REMINDED,
                TimelineStatus.ACTIVE,
                TimelineStatus.UPCOMING,
            ),
            timelineStatuses(
                reminderMinutes = listOf(minutes(19, 50), minutes(20, 8), minutes(21, 0)),
                currentMinute = minutes(20, 9),
            ),
        )
    }

    @Test
    fun noReminderIsActiveAfterWindowExpires() {
        assertEquals(
            listOf(TimelineStatus.REMINDED, TimelineStatus.REMINDED),
            timelineStatuses(
                reminderMinutes = listOf(minutes(19, 10), minutes(20, 8)),
                currentMinute = minutes(20, 50),
            ),
        )
    }

    @Test
    fun exactReminderTimeSelectsOnlyOneActiveEntry() {
        val statuses = timelineStatuses(
            reminderMinutes = listOf(minutes(20, 8), minutes(20, 8)),
            currentMinute = minutes(20, 8),
        )

        assertEquals(1, statuses.count { it == TimelineStatus.ACTIVE })
        assertEquals(TimelineStatus.ACTIVE, statuses.last())
    }

    private fun minutes(hour: Int, minute: Int) = hour * 60 + minute
}
