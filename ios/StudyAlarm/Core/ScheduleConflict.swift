import Foundation

struct ScheduleConflictItem: Equatable, Identifiable, Sendable {
    var planID: UUID
    var planName: String
    var reminderID: UUID
    var reminderName: String
    var id: String { planID.uuidString + reminderID.uuidString }
}

struct ScheduleConflict: Equatable, Identifiable, Sendable {
    var weekday: Weekday
    var minute: Int
    var items: [ScheduleConflictItem]
    var id: String { "\(weekday.rawValue)-\(minute)" }
    var timeText: String { String(format: "%02d:%02d", minute / 60, minute % 60) }
}

enum ScheduleConflictDetector {
    private struct Slot: Hashable {
        var weekday: Weekday
        var minute: Int
    }

    static func detect(records: [PlanRecord], activePlanIDs: Set<UUID>) -> [ScheduleConflict] {
        var grouped: [Slot: [ScheduleConflictItem]] = [:]
        for record in records where activePlanIDs.contains(record.id) && !record.pendingDeletion {
            for weekday in record.plan.weekdays {
                for reminder in record.plan.reminders {
                    let slot = Slot(weekday: weekday, minute: reminder.minutesSinceMidnight)
                    grouped[slot, default: []].append(ScheduleConflictItem(
                        planID: record.id,
                        planName: record.plan.name,
                        reminderID: reminder.id,
                        reminderName: reminder.name))
                }
            }
        }
        return grouped.compactMap { slot, items in
            guard Set(items.map(\.planID)).count > 1 else { return nil }
            return ScheduleConflict(weekday: slot.weekday, minute: slot.minute, items: items)
        }
        .sorted {
            let lhsDay = Weekday.displayOrder.firstIndex(of: $0.weekday) ?? 0
            let rhsDay = Weekday.displayOrder.firstIndex(of: $1.weekday) ?? 0
            return lhsDay == rhsDay ? $0.minute < $1.minute : lhsDay < rhsDay
        }
    }
}
