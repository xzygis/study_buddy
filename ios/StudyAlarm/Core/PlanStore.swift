import Foundation
import Combine

/// One coordinator for every scene. A durable write-ahead record precedes every system mutation.
@MainActor
final class PlanStore: ObservableObject {
    @Published private(set) var database = PlanDatabase()
    @Published private(set) var authorization: AlarmAuthorization = .notDetermined
    @Published private(set) var isBusy = false
    @Published private(set) var verifiedPlanIDs: Set<UUID> = []
    @Published private(set) var hasCheckedSystem = false
    @Published private(set) var storageIssue: String?
    @Published private(set) var systemIssue: String?
    @Published private(set) var lastCheckedAt: Date?
    @Published var userMessage: String?

    private let persistence: any PlanPersistence
    private let service: any AlarmService
    private var reconciliationRequested = false

    var records: [PlanRecord] { database.records }
    var canMutate: Bool { !isBusy && storageIssue == nil }

    init(persistence: any PlanPersistence, service: any AlarmService, seedStarterPlans: Bool = true) {
        self.persistence = persistence
        self.service = service
        authorization = service.authorization
        do {
            var loaded = try persistence.load()
            if seedStarterPlans && !loaded.didSeedStarterPlans {
                loaded.records.append(contentsOf: StarterPlans.all.map { PlanRecord(plan: $0) })
                loaded.didSeedStarterPlans = true
                try persistence.save(loaded)
            }
            try loaded.validate()
            database = loaded
        } catch {
            storageIssue = "无法读取本地计划，未覆盖原文件。\(error.localizedDescription)"
        }
    }

    func record(_ id: UUID) -> PlanRecord? { records.first { $0.id == id } }

    func isEnabled(_ record: PlanRecord) -> Bool {
        record.wantsEnabled && !record.pendingDeletion && record.phase == .on
            && verifiedPlanIDs.contains(record.id) && authorization == .authorized
            && storageIssue == nil && systemIssue == nil
    }

    func statusText(_ record: PlanRecord) -> String {
        if record.pendingDeletion { return "待清理后删除" }
        if isEnabled(record) { return "已启用" }
        if storageIssue != nil { return "本地数据异常" }
        if record.phase == .attention { return "需要处理" }
        if record.wantsEnabled {
            return isBusy ? "正在同步" : "待核对"
        }
        return record.bindings.isEmpty ? "未启用" : "待清理"
    }

    /// Saves the edited definition first. Enabled plans replace all old alarms before creating any new ones.
    @discardableResult
    func save(_ plan: StudyPlan) async -> Bool {
        guard canMutate else { return false }
        let plan = plan.normalized
        if let message = plan.validationMessage { userMessage = message; return false }
        isBusy = true
        defer { finishOperation() }
        var record = record(plan.id) ?? PlanRecord(plan: plan)
        guard !record.pendingDeletion else { return false }
        if record.plan == plan, database.records.contains(where: { $0.id == plan.id }) {
            return true
        }
        record.plan = plan
        if record.wantsEnabled || !record.bindings.isEmpty {
            record.phase = .installing
            verifiedPlanIDs.remove(record.id)
        }
        do { try persist(record) } catch { return false }
        if record.wantsEnabled || !record.bindings.isEmpty {
            await synchronize(record.id, mayRequestAuthorization: false)
        }
        return storageIssue == nil
    }

    func setEnabled(_ enabled: Bool, id: UUID) async {
        guard canMutate, var record = record(id), !record.pendingDeletion else { return }
        if enabled && isEnabled(record) { return }
        isBusy = true
        defer { finishOperation() }
        record.wantsEnabled = enabled
        record.phase = .installing
        record.issue = nil
        verifiedPlanIDs.remove(id)
        do { try persist(record) } catch { return }
        await synchronize(id, mayRequestAuthorization: enabled)
    }

    func delete(_ id: UUID) async {
        guard canMutate, var record = record(id) else { return }
        isBusy = true
        defer { finishOperation() }
        // Tombstone remains visible until the system confirms every alarm has disappeared.
        record.pendingDeletion = true
        record.wantsEnabled = false
        record.phase = .installing
        verifiedPlanIDs.remove(id)
        do { try persist(record) } catch { return }
        await synchronize(id, mayRequestAuthorization: false)
    }

    func retry(_ id: UUID) async {
        guard canMutate, let record = record(id) else { return }
        isBusy = true
        defer { finishOperation() }
        await synchronize(id, mayRequestAuthorization: record.wantsEnabled)
    }

    func reloadStorage() async {
        guard !isBusy else { return }
        do {
            let recovered = try persistence.load()
            try recovered.validate()
            database = recovered
            storageIssue = nil
        } catch {
            storageIssue = "仍无法读取本地计划，原文件已保留。\(error.localizedDescription)"
            return
        }
        await reconcile()
    }

    /// Called on launch, foreground entry and AlarmKit events. Never silently recreates a missing alarm.
    func reconcile() async {
        guard !isBusy else { reconciliationRequested = true; return }
        guard storageIssue == nil else { return }
        isBusy = true
        defer { finishOperation() }
        verifiedPlanIDs.removeAll()
        authorization = service.authorization
        systemIssue = nil
        do {
            let alarms = try service.alarms()
            let known = Set(records.flatMap(\.bindings).map(\.id))
            let orphans = Set(alarms.map(\.id)).subtracting(known)
            if !orphans.isEmpty {
                do { try cancelAndConfirm(orphans) }
                catch {
                    systemIssue = "发现未关联的本 App 闹钟，清理失败，旧提醒可能仍会响铃。\(error.localizedDescription)"
                }
            }

            for original in records {
                var record = original
                if record.wantsEnabled && !record.pendingDeletion && record.phase == .on
                    && authorization == .authorized && matches(record, alarms: alarms) {
                    verifiedPlanIDs.insert(record.id)
                    continue
                }
                if !record.wantsEnabled && !record.pendingDeletion && record.phase == .off && record.bindings.isEmpty {
                    continue
                }

                // Interrupted installs and mismatches are rolled back as a group, including survivors.
                do {
                    try cancelAndConfirm(Set(record.bindings.map(\.id)))
                    record.bindings = []
                    if record.pendingDeletion {
                        try removeRecord(record.id)
                        continue
                    }
                    if !record.wantsEnabled {
                        record.phase = .off
                        record.issue = nil
                    } else {
                        record.phase = .attention
                        if authorization != .authorized {
                            record.issue = permissionMessage
                        } else if original.phase != .attention || original.issue == nil {
                            record.issue = "计划未生效：系统闹钟缺失、规则不一致或上次同步中断。已清理整组，请重试启用。"
                        }
                    }
                } catch {
                    record.phase = .attention
                    record.issue = "清理未完成，部分旧提醒可能仍会响铃。请重试。\(error.localizedDescription)"
                }
                if record != original { try persist(record) }
            }
            hasCheckedSystem = true
            lastCheckedAt = Date()
        } catch {
            systemIssue = "无法核对系统闹钟，当前不确认任何计划已启用。请重试。\(error.localizedDescription)"
        }
    }

    private var permissionMessage: String {
        authorization == .denied
            ? "闹钟权限已关闭，计划未生效。请在系统设置中允许闹钟，再返回重试。"
            : "尚未获得闹钟权限，计划未生效。请点击重试授权并启用。"
    }

    private func synchronize(_ id: UUID, mayRequestAuthorization: Bool) async {
        guard var record = record(id) else { return }
        defer { reconciliationRequested = true }
        verifiedPlanIDs.remove(id)
        do {
            record.phase = .installing
            record.issue = nil
            try persist(record)
            try cancelAndConfirm(Set(record.bindings.map(\.id)))
            record.bindings = []
            try persist(record)

            if record.pendingDeletion {
                try removeRecord(id)
                return
            }
            if !record.wantsEnabled {
                record.phase = .off
                try persist(record)
                return
            }
            authorization = service.authorization
            if authorization == .notDetermined && mayRequestAuthorization {
                authorization = try await service.requestAuthorization()
            }
            guard authorization == .authorized else { throw StudyAlarmError.message(permissionMessage) }

            // All IDs are durable before the first call; a crash after a system success is recoverable.
            record.bindings = AlarmBinding.make(for: record.plan)
            try persist(record)
            for binding in record.bindings {
                try await service.schedule(binding, planID: id)
            }
            authorization = service.authorization
            guard authorization == .authorized, matches(record, alarms: try service.alarms()) else {
                throw StudyAlarmError.message("整组闹钟核对未通过，计划未生效。")
            }
            record.phase = .on
            try persist(record)
            verifiedPlanIDs.insert(id)
            lastCheckedAt = Date()
        } catch {
            let failure = error.localizedDescription
            var cleanupFailure: String?
            do {
                try cancelAndConfirm(Set(record.bindings.map(\.id)))
                record.bindings = []
            } catch {
                cleanupFailure = error.localizedDescription
            }
            record.phase = .attention
            if let cleanupFailure {
                record.issue = "操作未完成，部分闹钟可能仍会响铃，请重试清理。\(failure) 清理原因：\(cleanupFailure)"
            } else if record.pendingDeletion {
                record.issue = "闹钟已清理，但删除未完成，请重试。\(failure)"
            } else {
                record.issue = "计划未生效，本次创建已回滚。\(failure)"
            }
            do { try persist(record) } catch { /* storageIssue blocks further changes; IDs remain in the journal. */ }
        }
    }

    private func matches(_ record: PlanRecord, alarms: [SystemAlarm]) -> Bool {
        guard record.bindings.count == record.plan.reminders.count,
              !record.bindings.isEmpty,
              Set(record.bindings.map(\.reminderID)) == Set(record.plan.reminders.map(\.id)) else { return false }
        return record.bindings.allSatisfy { binding in
            guard let reminder = record.plan.reminders.first(where: { $0.id == binding.reminderID }),
                  binding.title == reminder.name,
                  binding.rule == AlarmRule(hour: reminder.hour, minute: reminder.minute, weekdays: record.plan.weekdays)
            else { return false }
            return alarms.contains {
                $0.id == binding.id && $0.rule == binding.rule && $0.isScheduledOrAlerting
            }
        }
    }

    private func cancelAndConfirm(_ ids: Set<UUID>) throws {
        guard !ids.isEmpty else { return }
        let existing = Set(try service.alarms().map(\.id))
        var failures: [String] = []
        for id in ids.intersection(existing) {
            do { try service.cancel(id: id) }
            catch { failures.append(error.localizedDescription) }
        }
        let remaining = Set(try service.alarms().map(\.id)).intersection(ids)
        guard remaining.isEmpty else {
            let detail = failures.first.map { " \($0)" } ?? ""
            throw StudyAlarmError.message("仍有 \(remaining.count) 个闹钟未清理。\(detail)")
        }
    }

    private func persist(_ record: PlanRecord) throws {
        var next = database
        if let index = next.records.firstIndex(where: { $0.id == record.id }) {
            next.records[index] = record
        } else {
            next.records.append(record)
        }
        try commit(next)
    }

    private func removeRecord(_ id: UUID) throws {
        var next = database
        next.records.removeAll { $0.id == id }
        try commit(next)
        verifiedPlanIDs.remove(id)
    }

    private func commit(_ next: PlanDatabase) throws {
        do {
            try persistence.save(next)
            database = next
            storageIssue = nil
        } catch {
            verifiedPlanIDs.removeAll()
            storageIssue = "无法保存本地记录，已暂停操作。请检查设备可用空间后重试读取；闹钟状态尚待核对。\(error.localizedDescription)"
            throw error
        }
    }

    private func finishOperation() {
        isBusy = false
        if reconciliationRequested {
            reconciliationRequested = false
            Task { await reconcile() }
        }
    }
}
