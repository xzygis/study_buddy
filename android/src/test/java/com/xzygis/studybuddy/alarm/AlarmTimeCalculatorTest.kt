package com.xzygis.studybuddy.alarm

import com.xzygis.studybuddy.data.AlarmBinding
import com.xzygis.studybuddy.data.Weekday
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class AlarmTimeCalculatorTest {
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun schedulesLaterTodayWhenTimeHasNotPassed() {
        val now = ZonedDateTime.of(2026, 10, 1, 8, 0, 0, 0, zone) // Thursday
        val binding = binding(hour = 9, minute = 0, days = setOf(Weekday.THURSDAY))

        val trigger = AlarmTimeCalculator.nextTrigger(binding, now)

        assertEquals(ZonedDateTime.of(2026, 10, 1, 9, 0, 0, 0, zone), trigger)
    }

    @Test
    fun schedulesNextWeekWhenTodaysTimeHasPassed() {
        val now = ZonedDateTime.of(2026, 10, 1, 10, 0, 0, 0, zone) // Thursday
        val binding = binding(hour = 9, minute = 0, days = setOf(Weekday.THURSDAY))

        val trigger = AlarmTimeCalculator.nextTrigger(binding, now)

        assertEquals(ZonedDateTime.of(2026, 10, 8, 9, 0, 0, 0, zone), trigger)
    }

    @Test
    fun chainsAcrossMultipleWeekdays() {
        val now = ZonedDateTime.of(2026, 10, 1, 23, 0, 0, 0, zone) // Thursday night
        val binding = binding(
            hour = 7,
            minute = 30,
            days = setOf(Weekday.MONDAY, Weekday.FRIDAY, Weekday.SATURDAY),
        )

        val trigger = AlarmTimeCalculator.nextTrigger(binding, now)

        assertEquals(ZonedDateTime.of(2026, 10, 2, 7, 30, 0, 0, zone), trigger)
    }

    private fun binding(hour: Int, minute: Int, days: Set<Weekday>): AlarmBinding =
        AlarmBinding(
            reminderId = "r",
            title = "测试提醒",
            hour = hour,
            minute = minute,
            weekdays = days,
        )
}
