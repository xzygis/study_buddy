import Foundation
import XCTest
@testable import StudyAlarmCore

@MainActor
private final class MemoryPersistence: PlanPersistence {
    var database = PlanDatabase()
    var saves = 0
    var failSaveCalls: Set<Int> = []
    var failLoad = false
    func load() throws -> PlanDatabase {
        if failLoad { throw StudyAlarmError.message("读取失败") }
        return database
    }
    func save(_ database: PlanDatabase) throws {
        saves += 1
        if failSaveCalls.contains(saves) { throw StudyAlarmError.message("磁盘写入失败") }
        self.database = database
    }
}

@MainActor
private final class FakeAlarmService: AlarmService {
    var authorization: AlarmAuthorization = .authorized
    var authorizationResult: AlarmAuthorization = .authorized
    var authorizationRequests = 0
    var installed: [UUID: SystemAlarm] = [:]
    var schedules = 0
    var cancellations: [UUID] = []
    var failScheduleCall: Int?
    var acceptsBeforeThrow = false
    var cancelFailures: Set<UUID> = []
    var failFetch = false
    var willSchedule: ((AlarmBinding) -> Void)?
    func requestAuthorization() async throws -> AlarmAuthorization {
        authorizationRequests += 1
        authorization = authorizationResult
        return authorization
    }
    func alarms() throws -> [SystemAlarm] {
        if failFetch { throw StudyAlarmError.message("系统读取失败") }
        return Array(installed.values)
    }
    func schedule(_ binding: AlarmBinding, planID: UUID) async throws {
        willSchedule?(binding)
        schedules += 1
        if failScheduleCall == schedules {
            if acceptsBeforeThrow { installed[binding.id] = SystemAlarm(id: binding.id, rule: binding.rule) }
            throw StudyAlarmError.message("系统创建失败")
        }
        installed[binding.id] = SystemAlarm(id: binding.id, rule: binding.rule)
    }
    func cancel(id: UUID) throws {
        cancellations.append(id)
        if cancelFailures.contains(id) { throw StudyAlarmError.message("系统取消失败") }
        installed.removeValue(forKey: id)
    }
}

@MainActor
private struct Fixture {
    let disk = MemoryPersistence()
    let alarms = FakeAlarmService()
    let store: PlanStore
    init(seedStarterPlans: Bool = false) {
        store = PlanStore(persistence: disk, service: alarms, seedStarterPlans: seedStarterPlans)
    }
    func enabledExample() async -> StudyPlan {
        let plan = StudyPlan.schoolDayExample()
        await store.save(plan)
        await store.setEnabled(true, id: plan.id)
        return plan
    }
}

/// Eager evaluation permits async expressions while retaining XCTest file/line diagnostics.
private func expect(_ condition: Bool, file: StaticString = #filePath, line: UInt = #line) {
    XCTAssertTrue(condition, file: file, line: line)
}
private func require<T>(_ value: T?, file: StaticString = #filePath, line: UInt = #line) throws -> T {
    try XCTUnwrap(value, file: file, line: line)
}

@MainActor
final class PlanStoreTests: XCTestCase {
    func testBuiltInPlansMatchScheduleAndCopiesUseFreshIDs() throws {
        let plans = StarterPlans.all
        XCTAssertEqual(plans.map(\.name), ["工作日（不含周二）", "周二", "周末"])

        let weekday = plans[0]
        XCTAssertEqual(weekday.weekdays, [.monday, .wednesday, .thursday, .friday])
        XCTAssertEqual(weekday.sortedReminders.map(\.timeText),
                       ["16:20", "16:50", "17:30", "18:20", "19:00",
                        "19:10", "19:50", "20:30", "21:00", "22:00"])
        XCTAssertEqual(weekday.sortedReminders.map(\.name),
                       ["休息", "语文作业", "晚餐+休息", "数学作业", "休息",
                        "英语作业", "户外活动", "洗澡", "检查作业", "睡觉"])

        let tuesday = plans[1]
        XCTAssertEqual(tuesday.weekdays, [.tuesday])
        XCTAssertEqual(tuesday.sortedReminders.map(\.timeText),
                       ["16:20", "17:50", "18:30", "19:10", "19:50"])
        XCTAssertEqual(tuesday.sortedReminders.map(\.name),
                       ["篮球课", "晚餐+休息", "数学作业", "英语作业", "语文作业"])

        let weekend = plans[2]
        XCTAssertEqual(weekend.weekdays, [.saturday, .sunday])
        XCTAssertEqual(weekend.sortedReminders.map(\.timeText),
                       ["09:00", "09:45", "10:00", "10:45", "11:00", "11:45", "12:45"])
        XCTAssertEqual(weekend.sortedReminders.map(\.name),
                       ["英语学习", "休息", "数学学习", "休息", "语文学习", "午餐+休息", "检查作业"])

        let copy = weekday.editableCopy()
        XCTAssertNotEqual(copy.id, weekday.id)
        XCTAssertEqual(copy.name, weekday.name)
        XCTAssertEqual(copy.weekdays, weekday.weekdays)
        XCTAssertEqual(copy.reminders.map(\.name), weekday.reminders.map(\.name))
        XCTAssertTrue(Set(copy.reminders.map(\.id)).isDisjoint(with: weekday.reminders.map(\.id)))

        let customCopy = weekday.editableCopy(named: "工作日副本")
        XCTAssertEqual(customCopy.name, "工作日副本")
        XCTAssertNotEqual(customCopy.id, weekday.id)
    }

    func testStarterPlansSeedOnceAsEditableDisabledPlans() async throws {
        let f = Fixture(seedStarterPlans: true)
        XCTAssertEqual(f.store.records.map(\.plan.name),
                       ["工作日（不含周二）", "周二", "周末"])
        XCTAssertTrue(f.store.records.allSatisfy {
            !$0.wantsEnabled && $0.phase == .off && $0.bindings.isEmpty
        })
        XCTAssertTrue(f.disk.database.didSeedStarterPlans)

        for record in f.store.records {
            await f.store.delete(record.id)
        }
        XCTAssertTrue(f.store.records.isEmpty)

        let restarted = PlanStore(persistence: f.disk, service: f.alarms)
        XCTAssertTrue(restarted.records.isEmpty)
    }

    func testCreateDisabledWithoutRequestingAuthorization() async throws {
        let f = Fixture()
        f.alarms.authorization = .notDetermined
        let plan = StudyPlan.schoolDayExample()
        expect(await f.store.save(plan))
        expect(f.store.records.count == 1 && f.alarms.authorizationRequests == 0)
        expect(f.alarms.installed.isEmpty)
        expect(try require(f.store.record(plan.id)).phase == .off)
    }

    func testEnableIsIdempotent() async throws {
        let f = Fixture()
        let plan = await f.enabledExample()
        expect(f.store.isEnabled(try require(f.store.record(plan.id))))
        expect(f.alarms.installed.count == 3)
        let ids = Set(f.alarms.installed.keys)
        await f.store.setEnabled(true, id: plan.id)
        await f.store.reconcile()
        expect(Set(f.alarms.installed.keys) == ids && f.alarms.schedules == 3)
    }

    func testAllIDsPersistBeforeSchedulingAndNoPrematureEnabledStatus() async {
        let f = Fixture()
        let plan = StudyPlan.schoolDayExample()
        await f.store.save(plan)
        f.alarms.willSchedule = { binding in
            let record = f.disk.database.records[0]
            expect(record.bindings.count == 3)
            expect(record.bindings.contains { $0.id == binding.id })
            expect(record.phase == .installing && !f.store.isEnabled(record))
        }
        await f.store.setEnabled(true, id: plan.id)
    }

    func testPermissionDeniedThenRetry() async throws {
        let f = Fixture()
        f.alarms.authorization = .notDetermined
        f.alarms.authorizationResult = .denied
        let plan = StudyPlan.schoolDayExample()
        await f.store.save(plan)
        await f.store.setEnabled(true, id: plan.id)
        expect(f.alarms.authorizationRequests == 1 && f.alarms.installed.isEmpty)
        let denied = try require(f.store.record(plan.id))
        expect(denied.phase == .attention && denied.issue?.contains("权限") == true)
        f.alarms.authorization = .authorized
        await f.store.retry(plan.id)
        expect(f.store.isEnabled(try require(f.store.record(plan.id))))
    }

    func testRollbackPartialAndAmbiguousSchedule() async throws {
        for ambiguous in [false, true] {
            let f = Fixture()
            f.alarms.failScheduleCall = 2
            f.alarms.acceptsBeforeThrow = ambiguous
            let plan = await f.enabledExample()
            expect(f.alarms.installed.isEmpty)
            let record = try require(f.store.record(plan.id))
            expect(record.phase == .attention && record.bindings.isEmpty && !f.store.isEnabled(record))
            f.alarms.failScheduleCall = nil
            await f.store.retry(plan.id)
            expect(f.alarms.installed.count == 3)
        }
    }

    func testFailedRollbackSurvivesRestart() async throws {
        let f = Fixture()
        let plan = StudyPlan.schoolDayExample()
        await f.store.save(plan)
        f.alarms.willSchedule = { binding in
            if f.alarms.schedules == 0 { f.alarms.cancelFailures.insert(binding.id) }
        }
        f.alarms.failScheduleCall = 2
        await f.store.setEnabled(true, id: plan.id)
        let failed = try require(f.store.record(plan.id))
        expect(!failed.bindings.isEmpty && failed.issue?.contains("仍会响铃") == true)
        expect(f.alarms.installed.count == 1)
        f.alarms.cancelFailures = []
        let restarted = PlanStore(persistence: f.disk, service: f.alarms)
        await restarted.reconcile()
        expect(f.alarms.installed.isEmpty)
        expect(try require(restarted.record(plan.id)).phase == .attention)
    }

    func testEditEnabledReplacesNamesTimesWeekdaysAndReminderList() async throws {
        let f = Fixture()
        var plan = await f.enabledExample()
        let oldIDs = Set(f.alarms.installed.keys)
        plan.name = "周末"
        plan.weekdays = [.saturday, .sunday]
        plan.reminders.removeLast()
        plan.reminders[0].name = "阅读"
        plan.reminders[0].hour = 8
        plan.reminders[0].minute = 30
        plan.reminders.append(StudyReminder(name: "绘画", hour: 15, minute: 45))
        await f.store.save(plan)
        expect(oldIDs.isDisjoint(with: f.alarms.installed.keys))
        expect(oldIDs.isSubset(of: Set(f.alarms.cancellations)))
        let record = try require(f.store.record(plan.id))
        expect(f.store.isEnabled(record))
        expect(record.bindings.contains { $0.title == "阅读" && $0.rule.hour == 8 && $0.rule.minute == 30 })
        expect(record.bindings.allSatisfy { $0.rule.weekdays == [.saturday, .sunday] })
    }

    func testFailedEditCleansBothOldAndNewAlarms() async throws {
        let f = Fixture()
        var plan = await f.enabledExample()
        f.alarms.failScheduleCall = 5
        plan.reminders[0].hour = 7
        await f.store.save(plan)
        expect(f.alarms.installed.isEmpty)
        let record = try require(f.store.record(plan.id))
        expect(record.plan.reminders[0].hour == 7 && record.phase == .attention)
    }

    func testDisableDoesNotAffectAnotherGroup() async throws {
        let f = Fixture()
        let first = await f.enabledExample()
        let second = await f.enabledExample()
        let secondIDs = Set(try require(f.store.record(second.id)).bindings.map(\.id))
        await f.store.setEnabled(false, id: first.id)
        expect(Set(f.alarms.installed.keys) == secondIDs)
        expect(try require(f.store.record(first.id)).phase == .off)
        expect(f.store.isEnabled(try require(f.store.record(second.id))))
    }

    func testDeleteFailureKeepsTombstoneAndRetryRemovesIt() async throws {
        let f = Fixture()
        let plan = await f.enabledExample()
        f.alarms.cancelFailures = Set(f.alarms.installed.keys)
        await f.store.delete(plan.id)
        expect(try require(f.store.record(plan.id)).pendingDeletion)
        expect(f.alarms.installed.count == 3)
        f.alarms.cancelFailures = []
        await f.store.retry(plan.id)
        expect(f.store.records.isEmpty && f.disk.database.records.isEmpty && f.alarms.installed.isEmpty)
    }

    func testDisableFailureThenRetry() async throws {
        let f = Fixture()
        let plan = await f.enabledExample()
        f.alarms.cancelFailures = Set(f.alarms.installed.keys)
        await f.store.setEnabled(false, id: plan.id)
        expect(try require(f.store.record(plan.id)).phase == .attention)
        expect(try require(f.store.record(plan.id)).bindings.count == 3)
        f.alarms.cancelFailures = []
        await f.store.retry(plan.id)
        expect(try require(f.store.record(plan.id)).phase == .off)
        expect(f.alarms.installed.isEmpty)
    }

    func testRestartVerifiesActualAlarms() async throws {
        let f = Fixture()
        let plan = await f.enabledExample()
        let restarted = PlanStore(persistence: f.disk, service: f.alarms)
        expect(!restarted.isEnabled(try require(restarted.record(plan.id))))
        await restarted.reconcile()
        expect(restarted.isEnabled(try require(restarted.record(plan.id))))
        expect(f.alarms.schedules == 3)
    }

    func testRecoverInterruptedInstallWithoutAutomaticallyEnabling() async throws {
        let f = Fixture()
        let plan = await f.enabledExample()
        f.disk.database.records[0].phase = .installing
        let restarted = PlanStore(persistence: f.disk, service: f.alarms)
        await restarted.reconcile()
        expect(f.alarms.installed.isEmpty)
        expect(try require(restarted.record(plan.id)).phase == .attention)
        expect(f.alarms.schedules == 3)
    }

    func testMissingAlarmInvalidatesAndCleansGroup() async throws {
        let f = Fixture()
        let plan = await f.enabledExample()
        f.alarms.installed.removeValue(forKey: try require(f.alarms.installed.keys.first))
        await f.store.reconcile()
        expect(f.alarms.installed.isEmpty)
        expect(try require(f.store.record(plan.id)).phase == .attention)
    }

    func testMismatchedRuleInvalidatesGroup() async throws {
        let f = Fixture()
        let plan = await f.enabledExample()
        let id = try require(f.alarms.installed.keys.first)
        f.alarms.installed[id]?.rule?.hour = 23
        await f.store.reconcile()
        expect(f.alarms.installed.isEmpty)
        expect(try require(f.store.record(plan.id)).phase == .attention)
    }

    func testRevokedPermission() async throws {
        let f = Fixture()
        let plan = await f.enabledExample()
        f.alarms.authorization = .denied
        await f.store.reconcile()
        expect(!f.store.isEnabled(try require(f.store.record(plan.id))))
        expect(try require(f.store.record(plan.id)).issue?.contains("权限") == true)
        expect(f.alarms.installed.isEmpty)
    }

    func testSystemQueryFailurePreservesIDs() async throws {
        let f = Fixture()
        let plan = await f.enabledExample()
        f.alarms.failFetch = true
        await f.store.reconcile()
        let record = try require(f.store.record(plan.id))
        expect(!f.store.isEnabled(record) && record.bindings.count == 3 && f.store.systemIssue != nil)
        f.alarms.failFetch = false
        await f.store.reconcile()
        expect(f.store.isEnabled(record))
    }

    func testOrphanCleanupAndFailureVisibility() async throws {
        let f = Fixture()
        let plan = await f.enabledExample()
        let orphan = UUID()
        f.alarms.installed[orphan] = SystemAlarm(id: orphan, rule: nil)
        f.alarms.cancelFailures = [orphan]
        await f.store.reconcile()
        expect(f.store.systemIssue?.contains("旧提醒可能") == true)
        f.alarms.cancelFailures = []
        await f.store.reconcile()
        expect(f.store.systemIssue == nil && f.alarms.installed.count == 3)
        expect(f.store.isEnabled(try require(f.store.record(plan.id))))
    }

    func testFinalCommitFailureRollsBackSystemAlarms() async throws {
        let f = Fixture()
        f.disk.failSaveCalls = [6]
        let plan = await f.enabledExample()
        expect(f.alarms.installed.isEmpty)
        expect(try require(f.store.record(plan.id)).phase == .attention)
    }

    func testIntentWriteFailureDoesNotTouchSystem() async {
        let f = Fixture()
        let plan = StudyPlan.schoolDayExample()
        await f.store.save(plan)
        f.disk.failSaveCalls = [2]
        await f.store.setEnabled(true, id: plan.id)
        expect(f.alarms.schedules == 0 && f.store.storageIssue != nil && !f.store.canMutate)
        await f.store.reloadStorage()
        expect(f.store.canMutate)
    }

    func testLoadFailureDoesNotOverwriteOrTreatDatabaseAsEmpty() async {
        let f = Fixture()
        f.disk.failLoad = true
        let orphan = UUID()
        f.alarms.installed[orphan] = SystemAlarm(id: orphan, rule: nil)
        let store = PlanStore(persistence: f.disk, service: f.alarms)
        await store.reconcile()
        expect(store.storageIssue != nil && !store.canMutate)
        expect(f.alarms.installed.count == 1 && f.disk.saves == 0)
    }

    func testDailyAndMidnightRules() async {
        let f = Fixture()
        var plan = StudyPlan.schoolDayExample()
        plan.weekdays = Set(Weekday.allCases)
        plan.reminders = [StudyReminder(name: "阅读", hour: 0, minute: 0),
                          StudyReminder(name: "复习", hour: 23, minute: 59)]
        await f.store.save(plan)
        await f.store.setEnabled(true, id: plan.id)
        expect(plan.repeatText == "每天")
        expect(f.alarms.installed.values.allSatisfy { $0.rule?.weekdays.count == 7 })
        expect(Set(f.alarms.installed.values.compactMap(\.rule?.hour)) == [0, 23])
        expect(plan.sortedReminders.map(\.timeText) == ["00:00", "23:59"])
    }

    func testInvalidInputNeverPersists() async {
        let f = Fixture()
        var plan = StudyPlan.schoolDayExample()
        plan.weekdays = []
        expect(!(await f.store.save(plan)))
        plan = .schoolDayExample()
        plan.name = "   "
        expect(!(await f.store.save(plan)))
        plan = .schoolDayExample()
        plan.reminders = []
        expect(!(await f.store.save(plan)))
        plan = .schoolDayExample()
        plan.reminders[0].hour = 24
        expect(!(await f.store.save(plan)))
        expect(f.disk.saves == 0)
    }

    func testFileRoundTripAndCorruption() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let file = directory.appendingPathComponent("plans.json")
        let disk = FilePlanPersistence(fileURL: file)
        let plan = StudyPlan.schoolDayExample()
        var record = PlanRecord(plan: plan)
        record.wantsEnabled = true
        record.phase = .installing
        record.bindings = AlarmBinding.make(for: plan)
        let database = PlanDatabase(records: [record])
        try disk.save(database)
        expect(try disk.load() == database)
        try Data("broken json".utf8).write(to: file)
        XCTAssertThrowsError(try disk.load())
        expect(try String(contentsOf: file, encoding: .utf8) == "broken json")
    }
}
