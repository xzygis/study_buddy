package com.xzygis.studybuddy.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelsTest {
    @Test
    fun starterPlansAreValidAndMatchExpectedCounts() {
        StarterPlans.all.forEach { assertNull(it.validationMessage()) }
        assertEquals(listOf(10, 5, 7), StarterPlans.all.map { it.reminders.size })
    }

    @Test
    fun editableCopyGetsIndependentIdentifiers() {
        val source = StarterPlans.all.first()
        val copy = source.editableCopy("副本")

        assertNotEquals(source.id, copy.id)
        assertEquals(source.reminders.map { it.name }, copy.reminders.map { it.name })
        source.reminders.zip(copy.reminders).forEach { (left, right) ->
            assertNotEquals(left.id, right.id)
        }
    }

    @Test
    fun validationRejectsMissingWeekdaysAndBlankReminder() {
        val plan = StudyPlan(
            name = "测试",
            weekdays = emptySet(),
            reminders = listOf(StudyReminder(name = "", hour = 9, minute = 0)),
        )

        assertEquals("请至少选择一个重复星期。", plan.validationMessage())
        assertEquals(
            "请填写每个提醒的科目或名称。",
            plan.copy(weekdays = setOf(Weekday.MONDAY)).validationMessage(),
        )
    }

    @Test
    fun repeatTextUsesCommonLabels() {
        val base = StudyPlan.draft()
        assertEquals("周一至周五", base.repeatText)
        assertEquals("每天", base.copy(weekdays = Weekday.entries.toSet()).repeatText)
        assertEquals(
            "周六、周日",
            base.copy(weekdays = setOf(Weekday.SATURDAY, Weekday.SUNDAY)).repeatText,
        )
    }
}
