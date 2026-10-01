import Foundation

enum Weekday: Int, Codable, CaseIterable, Identifiable, Sendable {
    case sunday = 1, monday, tuesday, wednesday, thursday, friday, saturday

    static let displayOrder: [Weekday] = [.monday, .tuesday, .wednesday, .thursday, .friday, .saturday, .sunday]
    static let schoolDays: Set<Weekday> = [.monday, .tuesday, .wednesday, .thursday, .friday]
    var id: Int { rawValue }
    var shortName: String { ["日", "一", "二", "三", "四", "五", "六"][rawValue - 1] }
    var name: String { "周" + shortName }
}

struct StudyReminder: Codable, Equatable, Identifiable, Sendable {
    var id = UUID()
    var name: String
    var hour: Int
    var minute: Int

    var timeText: String { String(format: "%02d:%02d", hour, minute) }
    var minutesSinceMidnight: Int { hour * 60 + minute }
}

struct StudyPlan: Codable, Equatable, Identifiable, Sendable {
    var id = UUID()
    var name: String
    var weekdays: Set<Weekday>
    var reminders: [StudyReminder]

    static func draft() -> StudyPlan {
        StudyPlan(name: "", weekdays: Weekday.schoolDays, reminders: [
            StudyReminder(name: "", hour: 9, minute: 0)
        ])
    }

    static func schoolDayExample() -> StudyPlan {
        StudyPlan(name: "上学日", weekdays: Weekday.schoolDays, reminders: [
            StudyReminder(name: "英语学习", hour: 9, minute: 0),
            StudyReminder(name: "数学学习", hour: 10, minute: 0),
            StudyReminder(name: "语文学习", hour: 14, minute: 0)
        ])
    }

    func editableCopy(named copiedName: String? = nil) -> StudyPlan {
        StudyPlan(
            name: copiedName ?? name,
            weekdays: weekdays,
            reminders: reminders.map {
                StudyReminder(name: $0.name, hour: $0.hour, minute: $0.minute)
            }
        )
    }

    var repeatText: String {
        if weekdays.count == 7 { return "每天" }
        if weekdays == Weekday.schoolDays { return "周一至周五" }
        if weekdays == [.saturday, .sunday] { return "周六、周日" }
        return Weekday.displayOrder.filter { weekdays.contains($0) }.map(\.name).joined(separator: "、")
    }

    var sortedReminders: [StudyReminder] {
        reminders.sorted {
            if $0.minutesSinceMidnight != $1.minutesSinceMidnight {
                return $0.minutesSinceMidnight < $1.minutesSinceMidnight
            }
            return $0.name < $1.name
        }
    }

    var normalized: StudyPlan {
        var result = self
        result.name = name.trimmingCharacters(in: .whitespacesAndNewlines)
        result.reminders = reminders.map {
            var item = $0
            item.name = item.name.trimmingCharacters(in: .whitespacesAndNewlines)
            return item
        }
        return result
    }

    var validationMessage: String? {
        let value = normalized
        if value.name.isEmpty { return "请填写计划名称。" }
        if value.name.count > 40 { return "计划名称最多 40 个字。" }
        if weekdays.isEmpty { return "请至少选择一个重复星期。" }
        if reminders.isEmpty { return "请至少添加一个提醒事项。" }
        if Set(reminders.map(\.id)).count != reminders.count { return "提醒标识重复，请重新添加提醒。" }
        for item in value.reminders {
            if item.name.isEmpty { return "请填写每个提醒的科目或名称。" }
            if item.name.count > 40 { return "提醒名称最多 40 个字。" }
            if !(0...23).contains(item.hour) || !(0...59).contains(item.minute) {
                return "提醒时间无效，请重新选择。"
            }
        }
        return nil
    }
}

enum StarterPlans {
    static var all: [StudyPlan] {
        [
            StudyPlan(
                name: "工作日（不含周二）",
                weekdays: [.monday, .wednesday, .thursday, .friday],
                reminders: [
                    StudyReminder(name: "休息", hour: 16, minute: 20),
                    StudyReminder(name: "语文作业", hour: 16, minute: 50),
                    StudyReminder(name: "晚餐+休息", hour: 17, minute: 30),
                    StudyReminder(name: "数学作业", hour: 18, minute: 20),
                    StudyReminder(name: "休息", hour: 19, minute: 0),
                    StudyReminder(name: "英语作业", hour: 19, minute: 10),
                    StudyReminder(name: "户外活动", hour: 19, minute: 50),
                    StudyReminder(name: "洗澡", hour: 20, minute: 30),
                    StudyReminder(name: "检查作业", hour: 21, minute: 0),
                    StudyReminder(name: "睡觉", hour: 22, minute: 0)
                ]),
            StudyPlan(
                name: "周二",
                weekdays: [.tuesday],
                reminders: [
                    StudyReminder(name: "篮球课", hour: 16, minute: 20),
                    StudyReminder(name: "晚餐+休息", hour: 17, minute: 50),
                    StudyReminder(name: "数学作业", hour: 18, minute: 30),
                    StudyReminder(name: "英语作业", hour: 19, minute: 10),
                    StudyReminder(name: "语文作业", hour: 19, minute: 50)
                ]),
            StudyPlan(
                name: "周末",
                weekdays: [.saturday, .sunday],
                reminders: [
                    StudyReminder(name: "英语学习", hour: 9, minute: 0),
                    StudyReminder(name: "休息", hour: 9, minute: 45),
                    StudyReminder(name: "数学学习", hour: 10, minute: 0),
                    StudyReminder(name: "休息", hour: 10, minute: 45),
                    StudyReminder(name: "语文学习", hour: 11, minute: 0),
                    StudyReminder(name: "午餐+休息", hour: 11, minute: 45),
                    StudyReminder(name: "检查作业", hour: 12, minute: 45)
                ])
        ]
    }
}

struct AlarmRule: Codable, Equatable, Sendable {
    var hour: Int
    var minute: Int
    var weekdays: Set<Weekday>
}

/// Saved before calling AlarmKit, including IDs whose schedule call may not return.
struct AlarmBinding: Codable, Equatable, Identifiable, Sendable {
    var id: UUID
    var reminderID: UUID
    var title: String
    var rule: AlarmRule

    static func make(for plan: StudyPlan) -> [AlarmBinding] {
        plan.sortedReminders.map {
            AlarmBinding(id: UUID(), reminderID: $0.id, title: $0.name,
                         rule: AlarmRule(hour: $0.hour, minute: $0.minute, weekdays: plan.weekdays))
        }
    }
}

enum SyncPhase: String, Codable, Sendable {
    case off, installing, on, attention
}

struct PlanRecord: Codable, Equatable, Identifiable, Sendable {
    var plan: StudyPlan
    var wantsEnabled = false
    var pendingDeletion = false
    var phase: SyncPhase = .off
    var bindings: [AlarmBinding] = []
    var issue: String?
    var id: UUID { plan.id }
}

struct PlanDatabase: Codable, Equatable, Sendable {
    var version = 1
    var records: [PlanRecord] = []
    var didSeedStarterPlans = false

    init(version: Int = 1, records: [PlanRecord] = [], didSeedStarterPlans: Bool = false) {
        self.version = version
        self.records = records
        self.didSeedStarterPlans = didSeedStarterPlans
    }

    private enum CodingKeys: String, CodingKey {
        case version, records, didSeedStarterPlans
    }

    init(from decoder: any Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        version = try values.decode(Int.self, forKey: .version)
        records = try values.decode([PlanRecord].self, forKey: .records)
        didSeedStarterPlans = try values.decodeIfPresent(Bool.self, forKey: .didSeedStarterPlans) ?? false
    }

    func validate() throws {
        guard version == 1 else { throw StudyAlarmError.message("本地数据版本不兼容，请使用更新版本的 App。") }
        guard Set(records.map(\.id)).count == records.count else {
            throw StudyAlarmError.message("本地计划标识重复，已停止修改数据。")
        }
        let ids = records.flatMap(\.bindings).map(\.id)
        guard Set(ids).count == ids.count else {
            throw StudyAlarmError.message("本地闹钟标识重复，已停止修改数据。")
        }
        for record in records {
            if let message = record.plan.validationMessage { throw StudyAlarmError.message(message) }
        }
    }
}

enum StudyAlarmError: LocalizedError {
    case message(String)
    var errorDescription: String? {
        switch self { case .message(let text): text }
    }
}

enum AlarmAuthorization: Equatable, Sendable {
    case notDetermined, denied, authorized
}

struct SystemAlarm: Equatable, Sendable {
    var id: UUID
    var rule: AlarmRule?
    var isScheduledOrAlerting = true
}

@MainActor
protocol AlarmService {
    var authorization: AlarmAuthorization { get }
    func requestAuthorization() async throws -> AlarmAuthorization
    func alarms() throws -> [SystemAlarm]
    func schedule(_ binding: AlarmBinding, planID: UUID) async throws
    func cancel(id: UUID) throws
}

@MainActor
protocol PlanPersistence {
    func load() throws -> PlanDatabase
    func save(_ database: PlanDatabase) throws
}
