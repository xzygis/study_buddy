package com.xzygis.studybuddy

import android.app.Application
import com.xzygis.studybuddy.alarm.AlarmDiagnosticStore
import com.xzygis.studybuddy.alarm.AlarmNotifier
import com.xzygis.studybuddy.alarm.AndroidAlarmScheduler
import com.xzygis.studybuddy.data.PlanRepository

class StudyBuddyApplication : Application() {
    val repository by lazy { PlanRepository(this) }
    val alarmDiagnostics by lazy { AlarmDiagnosticStore(this) }
    val alarmScheduler by lazy { AndroidAlarmScheduler(this, diagnostics = alarmDiagnostics) }

    override fun onCreate() {
        super.onCreate()
        AlarmNotifier.ensureChannel(this)
    }
}
