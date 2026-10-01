// AlarmKit's SDK singleton is not yet Sendable-annotated. App operations are serialized by PlanStore.
@preconcurrency import AlarmKit
import Foundation
import SwiftUI

private struct StudyAlarmMetadata: AlarmMetadata {
    var planID: UUID
    var reminderID: UUID
}

@MainActor
final class AlarmKitService: AlarmService {
    private let manager = AlarmManager.shared

    var authorization: AlarmAuthorization {
        switch manager.authorizationState {
        case .authorized: .authorized
        case .denied: .denied
        case .notDetermined: .notDetermined
        @unknown default: .denied
        }
    }

    func requestAuthorization() async throws -> AlarmAuthorization {
        _ = try await manager.requestAuthorization()
        return authorization
    }

    func alarms() throws -> [SystemAlarm] {
        try manager.alarms.map { alarm in
            let rule: AlarmRule?
            if case .relative(let relative) = alarm.schedule,
               case .weekly(let days) = relative.repeats {
                rule = AlarmRule(hour: relative.time.hour, minute: relative.time.minute,
                                 weekdays: Set(days.compactMap(Weekday.init(localeWeekday:))))
            } else {
                rule = nil
            }
            return SystemAlarm(id: alarm.id, rule: rule,
                               isScheduledOrAlerting: alarm.state == .scheduled || alarm.state == .alerting)
        }
    }

    func schedule(_ binding: AlarmBinding, planID: UUID) async throws {
        let title = LocalizedStringResource(stringLiteral: binding.title)
        let alert: AlarmPresentation.Alert
        if #available(iOS 26.1, *) {
            alert = AlarmPresentation.Alert(title: title)
        } else {
            alert = AlarmPresentation.Alert(title: title, stopButton:
                AlarmButton(text: "停止", textColor: .white, systemImageName: "stop.fill"))
        }
        let attributes = AlarmAttributes(
            presentation: AlarmPresentation(alert: alert),
            metadata: StudyAlarmMetadata(planID: planID, reminderID: binding.reminderID),
            tintColor: Color(red: 0.12, green: 0.42, blue: 0.36))
        let days = Weekday.displayOrder.filter { binding.rule.weekdays.contains($0) }.map(\.localeWeekday)
        let schedule = Alarm.Schedule.relative(.init(
            time: .init(hour: binding.rule.hour, minute: binding.rule.minute),
            repeats: .weekly(days)))
        // Alert-only: no countdown, snooze, widget extension, background task or network.
        let configuration = AlarmManager.AlarmConfiguration.alarm(
            schedule: schedule, attributes: attributes, sound: .default)
        do {
            _ = try await manager.schedule(id: binding.id, configuration: configuration)
        } catch AlarmManager.AlarmError.maximumLimitReached {
            throw StudyAlarmError.message("已达到系统闹钟数量上限。请关闭不需要的计划后重试。")
        }
    }

    func cancel(id: UUID) throws {
        try manager.cancel(id: id)
    }

    func observeAlarms(store: PlanStore) async {
        for await _ in manager.alarmUpdates {
            if Task.isCancelled { return }
            await store.reconcile()
        }
    }

    func observeAuthorization(store: PlanStore) async {
        for await _ in manager.authorizationUpdates {
            if Task.isCancelled { return }
            await store.reconcile()
        }
    }
}

extension Weekday {
    var localeWeekday: Locale.Weekday {
        switch self {
        case .monday: .monday
        case .tuesday: .tuesday
        case .wednesday: .wednesday
        case .thursday: .thursday
        case .friday: .friday
        case .saturday: .saturday
        case .sunday: .sunday
        }
    }

    init?(localeWeekday: Locale.Weekday) {
        switch localeWeekday {
        case .monday: self = .monday
        case .tuesday: self = .tuesday
        case .wednesday: self = .wednesday
        case .thursday: self = .thursday
        case .friday: self = .friday
        case .saturday: self = .saturday
        case .sunday: self = .sunday
        @unknown default: return nil
        }
    }
}
