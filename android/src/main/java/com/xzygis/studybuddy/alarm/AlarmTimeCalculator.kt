package com.xzygis.studybuddy.alarm

import com.xzygis.studybuddy.data.AlarmBinding
import com.xzygis.studybuddy.data.Weekday
import java.time.LocalTime
import java.time.ZonedDateTime

object AlarmTimeCalculator {
    fun nextTrigger(binding: AlarmBinding, now: ZonedDateTime): ZonedDateTime {
        val time = LocalTime.of(binding.hour, binding.minute)
        for (daysAhead in 0..7) {
            val date = now.toLocalDate().plusDays(daysAhead.toLong())
            val weekday = Weekday.fromCalendar((date.dayOfWeek.value % 7) + 1)
            if (weekday in binding.weekdays) {
                val candidate = ZonedDateTime.of(date, time, now.zone)
                if (candidate.isAfter(now)) return candidate
            }
        }
        error("无法计算下一次提醒时间。")
    }
}
