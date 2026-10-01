package com.xzygis.studybuddy.ui

internal enum class TimelineStatus {
    REMINDED,
    ACTIVE,
    UPCOMING,
}

internal fun timelineStatuses(
    reminderMinutes: List<Int>,
    currentMinute: Int,
    activeWindowMinutes: Int = 40,
): List<TimelineStatus> {
    val activeIndex = reminderMinutes.indexOfLast { minute ->
        minute <= currentMinute && currentMinute <= minute + activeWindowMinutes
    }
    return reminderMinutes.mapIndexed { index, minute ->
        when {
            index == activeIndex -> TimelineStatus.ACTIVE
            minute <= currentMinute -> TimelineStatus.REMINDED
            else -> TimelineStatus.UPCOMING
        }
    }
}
