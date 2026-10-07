package com.xzygis.studybuddy.alarm

import com.xzygis.studybuddy.data.AlarmBinding
import com.xzygis.studybuddy.data.PlanDatabase
import com.xzygis.studybuddy.data.PlanRecord
import com.xzygis.studybuddy.data.StudyPlan
import com.xzygis.studybuddy.data.StudyReminder
import com.xzygis.studybuddy.data.SyncPhase
import com.xzygis.studybuddy.data.Weekday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmDeliveryPolicyTest {
    private val binding = AlarmBinding(
        id = "binding-1",
        reminderId = "reminder-1",
        title = "Reading",
        hour = 18,
        minute = 30,
        weekdays = setOf(Weekday.MONDAY),
    )
    private val plan = StudyPlan(
        id = "plan-1",
        name = "Weekday",
        weekdays = setOf(Weekday.MONDAY),
        reminders = listOf(
            StudyReminder(
                id = "reminder-1",
                name = "Reading",
                hour = 18,
                minute = 30,
            ),
        ),
    )
    private val activeRecord = PlanRecord(
        plan = plan,
        wantsEnabled = true,
        phase = SyncPhase.ON,
        bindings = listOf(binding),
    )

    @Test
    fun returnsCurrentBindingForActivePlan() {
        val database = PlanDatabase(records = listOf(activeRecord))

        assertEquals(binding, database.activeAlarmBinding("plan-1", "binding-1"))
    }

    @Test
    fun rejectsBindingAfterPlanIsDisabled() {
        val database = PlanDatabase(
            records = listOf(activeRecord.copy(wantsEnabled = false)),
        )

        assertNull(database.activeAlarmBinding("plan-1", "binding-1"))
    }

    @Test
    fun rejectsBindingForPlansThatAreNotFullyActive() {
        val inactiveRecords = listOf(
            activeRecord.copy(pendingDeletion = true),
            activeRecord.copy(phase = SyncPhase.OFF),
            activeRecord.copy(phase = SyncPhase.INSTALLING),
            activeRecord.copy(phase = SyncPhase.ATTENTION),
        )

        inactiveRecords.forEach { record ->
            val database = PlanDatabase(records = listOf(record))
            assertNull(database.activeAlarmBinding("plan-1", "binding-1"))
        }
    }

    @Test
    fun rejectsStalePlanAndBindingIdentifiers() {
        val database = PlanDatabase(records = listOf(activeRecord))

        assertNull(database.activeAlarmBinding("missing-plan", "binding-1"))
        assertNull(database.activeAlarmBinding("plan-1", "missing-binding"))
    }
}
