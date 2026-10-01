package com.xzygis.studybuddy

import android.app.Application
import com.xzygis.studybuddy.alarm.AndroidAlarmScheduler
import com.xzygis.studybuddy.data.PlanRepository

class StudyBuddyApplication : Application() {
    val repository by lazy { PlanRepository(this) }
    val alarmScheduler by lazy { AndroidAlarmScheduler(this) }
}
