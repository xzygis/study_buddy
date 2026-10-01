package com.xzygis.studybuddy.alarm

import com.xzygis.studybuddy.data.AlarmBinding
import com.xzygis.studybuddy.data.Weekday
import org.junit.Assert.assertEquals
import org.junit.Test

class SystemClockAlarmSpecTest {
    @Test
    fun buildsStableLabelForManualManagement() {
        assertEquals(
            "StudyBuddy · 工作日 · 数学作业",
            SystemClockAlarmSpec.label("工作日", "数学作业"),
        )
    }

    @Test
    fun mapsWeekdaysToCalendarValues() {
        val binding = AlarmBinding(
            reminderId = "reminder",
            title = "学习",
            hour = 20,
            minute = 8,
            weekdays = setOf(Weekday.MONDAY, Weekday.FRIDAY, Weekday.SUNDAY),
        )

        assertEquals(listOf(1, 2, 6), SystemClockAlarmSpec.days(binding))
    }
}
