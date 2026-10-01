package com.xzygis.studybuddy.alarm

import com.xzygis.studybuddy.data.AlarmBinding
import com.xzygis.studybuddy.data.Weekday
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class AlarmTimeCalculatorTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun schedulesLaterTodayWhenTimeHasNotPassed() {
        val now = ZonedDateTime.of(2026, 10, 1, 8, 0, 0, 0, zone)
        val binding = binding(hour = 9, minute = 30, weekdays = setOf(Weekday.THURSDAY))

        assertEquals(
            ZonedDateTime.of(2026, 10, 1, 9, 30, 0, 0, zone),
            AlarmTimeCalculator.nextTrigger(binding, now),
        )
    }

    @Test
    fun schedulesNextWeekWhenTodaysTimeHasPassed() {
        val now = ZonedDateTime.of(2026, 10, 1, 10, 0, 0, 0, zone)
        val binding = binding(hour = 9, minute = 30, weekdays = setOf(Weekday.THURSDAY))

        assertEquals(
            ZonedDateTime.of(2026, 10, 8, 9, 30, 0, 0, zone),
            AlarmTimeCalculator.nextTrigger(binding, now),
        )
    }

    @Test
    fun picksNearestSelectedWeekday() {
        val now = ZonedDateTime.of(2026, 10, 1, 10, 0, 0, 0, zone)
        val binding = binding(
            hour = 9,
            minute = 30,
            weekdays = setOf(Weekday.FRIDAY, Weekday.MONDAY),
        )

        assertEquals(
            ZonedDateTime.of(2026, 10, 2, 9, 30, 0, 0, zone),
            AlarmTimeCalculator.nextTrigger(binding, now),
        )
    }

    private fun binding(hour: Int, minute: Int, weekdays: Set<Weekday>) = AlarmBinding(
        reminderId = "reminder",
        title = "学习",
        hour = hour,
        minute = minute,
        weekdays = weekdays,
    )
}
