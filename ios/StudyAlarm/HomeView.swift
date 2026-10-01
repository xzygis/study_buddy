import SwiftUI

struct HomeView: View {
    @ObservedObject var store: PlanStore
    @Binding var selection: UUID?
    @Binding var editor: EditorSession?

    private var isPad: Bool { UIDevice.current.userInterfaceIdiom == .pad }

    var body: some View {
        List(selection: $selection) {
            SystemStatusSection(store: store)
            if isPad {
                Section {
                    Button {
                        selection = nil
                    } label: {
                        Label("今天", systemImage: "calendar")
                    }
                    .accessibilityIdentifier("showToday")
                }
            }
            Section {
                if store.records.isEmpty {
                    VStack(alignment: .leading, spacing: 14) {
                        Label("把学习安排好", systemImage: "books.vertical")
                            .font(.headline)
                        Text("新建计划，设置重复星期和各项开始时间。")
                            .font(.subheadline).foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 10)
                }
                ForEach(store.records) { record in
                    VStack(alignment: .leading, spacing: 12) {
                        NavigationLink(value: record.id) {
                            VStack(alignment: .leading, spacing: 5) {
                                Text(record.plan.name).font(.headline)
                                Text("\(record.plan.repeatText) · \(record.plan.reminders.count) 个提醒")
                                    .font(.subheadline).foregroundStyle(.secondary)
                            }
                        }
                        PlanControls(store: store, record: record)
                        HStack(spacing: 24) {
                            Button("编辑", systemImage: "pencil") {
                                editor = EditorSession(plan: record.plan, isNew: false)
                            }
                            .accessibilityIdentifier("editPlan-\(record.id.uuidString)")
                            Button("复制", systemImage: "doc.on.doc") {
                                editor = EditorSession(
                                    plan: record.plan.editableCopy(
                                        named: String(record.plan.name.prefix(37)) + " 副本"),
                                    isNew: true)
                            }
                            .accessibilityIdentifier("copyPlan-\(record.id.uuidString)")
                        }
                        .font(.subheadline)
                        .disabled(!store.canMutate || record.pendingDeletion)
                    }
                    .padding(.vertical, 6)
                }
            } header: {
                HStack {
                    Text("我的计划")
                    Spacer()
                    Text("\(store.records.count) 组")
                }
            } footer: {
                Text("启用成功后由系统管理闹钟，无需保持 App 打开。")
            }
            if !isPad {
                Section {
                    TimelineView(.periodic(from: .now, by: 60)) { context in
                        TodayAgenda(store: store, date: context.date, compact: true)
                    }
                } header: {
                    Text("今天的安排")
                } footer: {
                    Text("仅提醒每项安排开始；时间按设备所在时区。")
                }
            }
        }
        .listStyle(.insetGrouped)
        .refreshable { await store.reconcile() }
    }
}

struct SystemStatusSection: View {
    @ObservedObject var store: PlanStore
    @Environment(\.openURL) private var openURL

    var body: some View {
        if let issue = store.storageIssue {
            Section {
                Label(issue, systemImage: "externaldrive.badge.exclamationmark")
                    .foregroundStyle(.red)
                Button("重新读取本地数据") { Task { await store.reloadStorage() } }
                    .disabled(store.isBusy)
            }
        }
        if let issue = store.systemIssue {
            Section {
                Label(issue, systemImage: "exclamationmark.triangle")
                    .foregroundStyle(.orange)
                Button("重新核对与清理") { Task { await store.reconcile() } }
                    .disabled(store.isBusy)
            }
        }
        if store.authorization == .denied {
            Section {
                Label("闹钟权限未开启，学习提醒未生效。", systemImage: "bell.slash")
                    .foregroundStyle(.orange)
                Button("打开系统设置") {
                    if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                }
            }
        }
        if store.isBusy {
            Section {
                HStack(spacing: 12) {
                    ProgressView()
                    Text("正在核对与同步闹钟…").foregroundStyle(.secondary)
                }
                .accessibilityIdentifier("syncProgress")
            }
        }
    }
}

struct PlanControls: View {
    @ObservedObject var store: PlanStore
    let record: PlanRecord

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Toggle(isOn: Binding(
                get: { store.isEnabled(record) },
                set: { value in Task { await store.setEnabled(value, id: record.id) } }
            )) {
                Label(store.statusText(record),
                      systemImage: store.isEnabled(record) ? "alarm.fill" : "alarm")
                    .font(.subheadline)
                    .foregroundStyle(store.isEnabled(record) ? Color.studyGreen : .secondary)
            }
            .accessibilityLabel("\(record.plan.name)，\(store.statusText(record))")
            .accessibilityIdentifier("planToggle-\(record.plan.name)")
            .disabled(!store.canMutate || record.pendingDeletion)
            if let issue = record.issue {
                Text(issue).font(.footnote).foregroundStyle(.orange)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if record.phase == .attention || record.pendingDeletion {
                HStack {
                    Button(record.pendingDeletion ? "重试清理并删除" :
                            (record.wantsEnabled ? "重试启用" : "重试清理")) {
                        Task { await store.retry(record.id) }
                    }
                    .buttonStyle(.bordered)
                    if record.wantsEnabled && !record.pendingDeletion {
                        Button("关闭计划") {
                            Task { await store.setEnabled(false, id: record.id) }
                        }
                        .buttonStyle(.borderless)
                    }
                }
                .font(.subheadline)
                .disabled(!store.canMutate)
            }
        }
    }
}

private struct AgendaEntry: Identifiable {
    let record: PlanRecord
    let reminder: StudyReminder
    var id: String { record.id.uuidString + reminder.id.uuidString }
}

struct TodayAgenda: View {
    @ObservedObject var store: PlanStore
    let date: Date
    var compact = false

    private var entries: [AgendaEntry] {
        guard let day = Weekday(rawValue: Calendar.current.component(.weekday, from: date)) else { return [] }
        return store.records.filter { store.isEnabled($0) && $0.plan.weekdays.contains(day) }
            .flatMap { record in record.plan.reminders.map { AgendaEntry(record: record, reminder: $0) } }
            .sorted {
                if $0.reminder.minutesSinceMidnight != $1.reminder.minutesSinceMidnight {
                    return $0.reminder.minutesSinceMidnight < $1.reminder.minutesSinceMidnight
                }
                return $0.id < $1.id
            }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: compact ? 20 : 28) {
            Text(date.formatted(.dateTime.month(.wide).day().weekday(.wide)))
                .font(.subheadline).foregroundStyle(.secondary)
            if entries.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Text("今天没有已启用的安排").font(.title3.weight(.semibold))
                    Text("打开适用于今天的计划，提醒会显示在这里。")
                        .font(.subheadline).foregroundStyle(.secondary)
                }
                .padding(.vertical, 8)
            } else {
                ForEach(entries) { entry in
                    HStack(alignment: .firstTextBaseline, spacing: 18) {
                        Text(entry.reminder.timeText)
                            .font(.system(compact ? .title2 : .largeTitle, design: .rounded, weight: .semibold))
                            .monospacedDigit()
                        VStack(alignment: .leading, spacing: 5) {
                            Text(entry.reminder.name).font(.headline)
                            Text(entry.record.plan.name)
                                .font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer(minLength: 0)
                    }
                    .accessibilityElement(children: .combine)
                }
            }
        }
        .padding(.vertical, compact ? 8 : 16)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct TodayDetailView: View {
    @ObservedObject var store: PlanStore

    var body: some View {
        List {
            Section {
                HStack(spacing: 14) {
                    Image(systemName: "sun.max").font(.largeTitle).foregroundStyle(Color.studyGreen)
                    VStack(alignment: .leading, spacing: 6) {
                        Text("每天，从容开始").font(.title2.bold())
                        Text("时间交给闹钟，专注留给学习。")
                            .font(.subheadline).foregroundStyle(.secondary)
                    }
                }
                .padding(.vertical, 12)
            }
            Section("今天的安排") {
                TimelineView(.periodic(from: .now, by: 60)) { context in
                    TodayAgenda(store: store, date: context.date)
                }
            }
            Section {
                Text("从左侧选择计划，查看、复制或编辑整组提醒。")
                    .foregroundStyle(.secondary)
            }
        }
        .navigationTitle("今天")
        .listStyle(.insetGrouped)
    }
}
