package com.xzygis.studybuddy.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class Weekday(val calendarValue: Int, val shortName: String) {
    MONDAY(2, "一"),
    TUESDAY(3, "二"),
    WEDNESDAY(4, "三"),
    THURSDAY(5, "四"),
    FRIDAY(6, "五"),
    SATURDAY(7, "六"),
    SUNDAY(1, "日");

    val displayName: String get() = "周$shortName"

    companion object {
        val schoolDays = setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY)
        fun fromCalendar(value: Int) = entries.firstOrNull { it.calendarValue == value }
    }
}

@Serializable
data class StudyReminder(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val hour: Int,
    val minute: Int,
) {
    val timeText: String get() = "%02d:%02d".format(hour, minute)
    val minutesSinceMidnight: Int get() = hour * 60 + minute
}

@Serializable
data class StudyPlan(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val weekdays: Set<Weekday>,
    val reminders: List<StudyReminder>,
) {
    val repeatText: String
        get() = when (weekdays) {
            Weekday.entries.toSet() -> "每天"
            Weekday.schoolDays -> "周一至周五"
            setOf(Weekday.SATURDAY, Weekday.SUNDAY) -> "周六、周日"
            else -> Weekday.entries.filter(weekdays::contains).joinToString("、") { it.displayName }
        }

    val sortedReminders: List<StudyReminder>
        get() = reminders.sortedWith(compareBy(StudyReminder::minutesSinceMidnight, StudyReminder::name))

    fun normalized() = copy(
        name = name.trim(),
        reminders = reminders.map { it.copy(name = it.name.trim()) },
    )

    fun validationMessage(): String? {
        val value = normalized()
        if (value.name.isEmpty()) return "请填写计划名称。"
        if (value.name.length > 40) return "计划名称最多 40 个字。"
        if (value.weekdays.isEmpty()) return "请至少选择一个重复星期。"
        if (value.reminders.isEmpty()) return "请至少添加一个提醒事项。"
        if (value.reminders.map { it.id }.toSet().size != value.reminders.size) return "提醒标识重复，请重新添加提醒。"
        value.reminders.forEach {
            if (it.name.isEmpty()) return "请填写每个提醒的科目或名称。"
            if (it.name.length > 40) return "提醒名称最多 40 个字。"
            if (it.hour !in 0..23 || it.minute !in 0..59) return "提醒时间无效，请重新选择。"
        }
        return null
    }

    fun editableCopy(copiedName: String = name) = StudyPlan(
        name = copiedName,
        weekdays = weekdays,
        reminders = reminders.map { StudyReminder(name = it.name, hour = it.hour, minute = it.minute) },
    )

    companion object {
        fun draft() = StudyPlan(
            name = "",
            weekdays = Weekday.schoolDays,
            reminders = listOf(StudyReminder(name = "", hour = 9, minute = 0)),
        )
    }
}

@Serializable
data class AlarmBinding(
    val id: String = UUID.randomUUID().toString(),
    val reminderId: String,
    val title: String,
    val hour: Int,
    val minute: Int,
    val weekdays: Set<Weekday>,
) {
    companion object {
        fun forPlan(plan: StudyPlan) = plan.sortedReminders.map {
            AlarmBinding(
                reminderId = it.id,
                title = it.name,
                hour = it.hour,
                minute = it.minute,
                weekdays = plan.weekdays,
            )
        }
    }
}

@Serializable
enum class SyncPhase { OFF, INSTALLING, ON, ATTENTION }

@Serializable
data class PlanRecord(
    val plan: StudyPlan,
    val wantsEnabled: Boolean = false,
    val pendingDeletion: Boolean = false,
    val phase: SyncPhase = SyncPhase.OFF,
    val bindings: List<AlarmBinding> = emptyList(),
    val issue: String? = null,
)

@Serializable
data class PlanDatabase(
    val version: Int = 1,
    val records: List<PlanRecord> = emptyList(),
    val didSeedStarterPlans: Boolean = false,
) {
    fun validate() {
        require(version == 1) { "本地数据版本不兼容，请使用更新版本的 App。" }
        require(records.map { it.plan.id }.toSet().size == records.size) { "本地计划标识重复，已停止修改数据。" }
        val bindingIds = records.flatMap { it.bindings }.map { it.id }
        require(bindingIds.toSet().size == bindingIds.size) { "本地闹钟标识重复，已停止修改数据。" }
        records.forEach { record ->
            record.plan.validationMessage()?.let { throw IllegalArgumentException(it) }
        }
    }
}

object StarterPlans {
    val all = listOf(
        StudyPlan(
            name = "工作日（不含周二）",
            weekdays = setOf(Weekday.MONDAY, Weekday.WEDNESDAY, Weekday.THURSDAY, Weekday.FRIDAY),
            reminders = listOf(
                reminder("休息", 16, 20), reminder("语文作业", 16, 50),
                reminder("晚餐+休息", 17, 30), reminder("数学作业", 18, 20),
                reminder("休息", 19, 0), reminder("英语作业", 19, 10),
                reminder("户外活动", 19, 50), reminder("洗澡", 20, 30),
                reminder("检查作业", 21, 0), reminder("睡觉", 22, 0),
            ),
        ),
        StudyPlan(
            name = "周二",
            weekdays = setOf(Weekday.TUESDAY),
            reminders = listOf(
                reminder("篮球课", 16, 20), reminder("晚餐+休息", 17, 50),
                reminder("数学作业", 18, 30), reminder("英语作业", 19, 10),
                reminder("语文作业", 19, 50),
            ),
        ),
        StudyPlan(
            name = "周末",
            weekdays = setOf(Weekday.SATURDAY, Weekday.SUNDAY),
            reminders = listOf(
                reminder("英语学习", 9, 0), reminder("休息", 9, 45),
                reminder("数学学习", 10, 0), reminder("休息", 10, 45),
                reminder("语文学习", 11, 0), reminder("午餐+休息", 11, 45),
                reminder("检查作业", 12, 45),
            ),
        ),
    )

    private fun reminder(name: String, hour: Int, minute: Int) =
        StudyReminder(name = name, hour = hour, minute = minute)
}
