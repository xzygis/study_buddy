import SwiftUI

struct PlanEditorView: View {
    @ObservedObject var store: PlanStore
    let session: EditorSession
    @State private var draft: StudyPlan
    @State private var saving = false
    @State private var confirmDiscard = false
    @Environment(\.dismiss) private var dismiss

    init(store: PlanStore, session: EditorSession) {
        self.store = store
        self.session = session
        _draft = State(initialValue: session.plan)
    }

    var body: some View {
        NavigationStack {
            Form {
                PlanFormSections(store: store, draft: $draft)
            }
            .disabled(saving)
            .navigationTitle(session.isNew ? "新建计划" : "编辑计划")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") {
                        if draft != session.plan { confirmDiscard = true }
                        else { dismiss() }
                    }
                    .disabled(saving)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        saving = true
                        Task {
                            let saved = await store.save(draft)
                            saving = false
                            if saved { dismiss() }
                        }
                    } label: {
                        if saving { ProgressView() }
                        else { Text("保存").bold() }
                    }
                    .accessibilityIdentifier("savePlan")
                    .disabled(saving || !store.canMutate || draft.validationMessage != nil)
                }
            }
            .confirmationDialog("放弃未保存的修改？", isPresented: $confirmDiscard, titleVisibility: .visible) {
                Button("放弃修改", role: .destructive) { dismiss() }
                Button("继续编辑", role: .cancel) {}
            }
            .interactiveDismissDisabled(saving || draft != session.plan)
        }
    }

}

private struct PlanFormSections: View {
    @ObservedObject var store: PlanStore
    @Binding var draft: StudyPlan

    var body: some View {
        Section("计划名称") {
            TextField("例如：上学日、周末", text: $draft.name)
                .accessibilityIdentifier("planName")
        }
        Section {
            HStack {
                Menu("快捷选择") {
                    Button("每天") { draft.weekdays = Set(Weekday.allCases) }
                    Button("周一至周五") { draft.weekdays = Weekday.schoolDays }
                    Button("周末") { draft.weekdays = [.saturday, .sunday] }
                }
                .accessibilityIdentifier("repeatPresets")
                Spacer()
                Text(draft.repeatText)
                    .foregroundStyle(.secondary)
            }
            HStack(spacing: 6) {
                ForEach(Weekday.displayOrder) { day in
                    Toggle(day.shortName, isOn: weekdayBinding(day))
                        .toggleStyle(.button)
                        .buttonStyle(.bordered)
                        .frame(maxWidth: .infinity)
                        .accessibilityLabel(day.name)
                        .accessibilityIdentifier("weekday-\(day.rawValue)")
                }
            }
        } header: {
            Text("整组重复")
        } footer: {
            Text(draft.weekdays.isEmpty ? "请至少选择一天。" : "\(draft.repeatText)重复，应用于下面所有提醒。")
        }
        Section {
            ForEach($draft.reminders) { $reminder in
                ReminderEditorRow(reminder: $reminder)
                    .contextMenu {
                        Button("删除提醒", systemImage: "trash", role: .destructive) {
                            draft.reminders.removeAll { $0.id == reminder.id }
                        }
                    }
            }
            .onDelete { offsets in draft.reminders.remove(atOffsets: offsets) }
            Button("添加提醒", systemImage: "plus.circle.fill") {
                draft.reminders.append(StudyReminder(name: "", hour: 9, minute: 0))
            }
            .accessibilityIdentifier("addReminder")
        } header: {
            Text("提醒事项")
        } footer: {
            Text("时间表示事项开始。长按或向左轻扫可删除提醒。")
        }
        if let message = draft.validationMessage {
            Section {
                Label(message, systemImage: "info.circle")
                    .font(.footnote).foregroundStyle(.secondary)
            }
        }
        if store.record(draft.id)?.wantsEnabled == true {
            Section {
                Text("保存后会先清理旧闹钟，再同步整组新规则。若同步失败，计划将显示未生效，可在计划页重试。")
                    .font(.footnote).foregroundStyle(.secondary)
            }
        }
        if let issue = store.storageIssue {
            Section { Text(issue).foregroundStyle(.red) }
        }
    }

    private func weekdayBinding(_ day: Weekday) -> Binding<Bool> {
        Binding(
            get: { draft.weekdays.contains(day) },
            set: { selected in
                if selected { draft.weekdays.insert(day) }
                else { draft.weekdays.remove(day) }
            }
        )
    }
}

private struct ReminderEditorRow: View {
    @Binding var reminder: StudyReminder

    private var time: Binding<Date> {
        Binding {
            Calendar.current.date(from: DateComponents(year: 2026, month: 1, day: 15,
                                                      hour: reminder.hour, minute: reminder.minute)) ?? Date()
        } set: { value in
            reminder.hour = Calendar.current.component(.hour, from: value)
            reminder.minute = Calendar.current.component(.minute, from: value)
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            TextField("科目或提醒名称", text: $reminder.name)
                .font(.headline)
                .accessibilityIdentifier("reminderName")
            DatePicker("开始时间", selection: time, displayedComponents: .hourAndMinute)
                .datePickerStyle(.compact)
                .accessibilityIdentifier("reminderTime")
        }
        .padding(.vertical, 5)
    }
}

struct PlanDetailView: View {
    @ObservedObject var store: PlanStore
    let planID: UUID
    @Binding var editor: EditorSession?
    @State private var draft: StudyPlan
    @State private var isEditing: Bool
    @State private var saving = false
    @State private var confirmDelete = false
    @State private var confirmDiscard = false
    @Environment(\.dismiss) private var dismiss

    init(
        store: PlanStore,
        record: PlanRecord,
        editor: Binding<EditorSession?>,
        startsEditing: Bool = false
    ) {
        self.store = store
        planID = record.id
        _editor = editor
        _draft = State(initialValue: record.plan)
        _isEditing = State(initialValue: startsEditing)
    }

    private var record: PlanRecord? {
        store.record(planID)
    }

    var body: some View {
        Group {
            if let record {
                if isEditing {
                    Form {
                        PlanFormSections(store: store, draft: $draft)
                    }
                    .disabled(saving)
                } else {
                    PlanReadOnlyDetails(store: store, record: record, confirmDelete: $confirmDelete)
                }
            } else {
                ContentUnavailableView("计划已删除", systemImage: "trash")
            }
        }
        .navigationTitle(isEditing ? "编辑计划" : (record?.plan.name ?? "计划"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if let record {
                if isEditing {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("取消") {
                            if draft != record.plan {
                                confirmDiscard = true
                            } else {
                                isEditing = false
                            }
                        }
                        .disabled(saving)
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button {
                            save()
                        } label: {
                            if saving { ProgressView() }
                            else { Text("保存").bold() }
                        }
                        .accessibilityIdentifier("savePlan")
                        .disabled(saving || !store.canMutate || draft.validationMessage != nil)
                    }
                } else {
                    ToolbarItemGroup(placement: .primaryAction) {
                        Button("复制", systemImage: "doc.on.doc") {
                            editor = EditorSession(
                                plan: record.plan.editableCopy(
                                    named: String(record.plan.name.prefix(37)) + " 副本"),
                                isNew: true)
                        }
                        .labelStyle(.iconOnly)
                        .accessibilityIdentifier("copyPlan")
                        .disabled(!store.canMutate || record.pendingDeletion)
                        Button("编辑") {
                            draft = record.plan
                            isEditing = true
                        }
                        .accessibilityIdentifier("editPlan")
                        .disabled(!store.canMutate || record.pendingDeletion)
                    }
                }
            }
        }
        .confirmationDialog("删除“\(record?.plan.name ?? "此计划")”及其所有闹钟？",
                            isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("删除计划", role: .destructive) {
                Task {
                    await store.delete(planID)
                    if store.record(planID) == nil { dismiss() }
                }
            }
            Button("取消", role: .cancel) {}
        }
        .confirmationDialog("放弃未保存的修改？",
                            isPresented: $confirmDiscard, titleVisibility: .visible) {
            Button("放弃修改", role: .destructive) {
                if let record { draft = record.plan }
                isEditing = false
            }
            Button("继续编辑", role: .cancel) {}
        }
        .onChange(of: record?.plan) { _, plan in
            if !isEditing, let plan { draft = plan }
        }
    }

    private func save() {
        saving = true
        Task {
            let saved = await store.save(draft)
            saving = false
            if saved { isEditing = false }
        }
    }
}

private struct PlanReadOnlyDetails: View {
    @ObservedObject var store: PlanStore
    let record: PlanRecord
    @Binding var confirmDelete: Bool

    var body: some View {
        List {
            Section {
                PlanControls(store: store, record: record)
            } footer: {
                Text("全部 \(record.plan.reminders.count) 个提醒由系统核对成功后，才会显示已启用。")
            }
            Section("重复规则") {
                Label(record.plan.repeatText, systemImage: "repeat")
            }
            Section("提醒事项") {
                ForEach(record.plan.sortedReminders) { reminder in
                    HStack(alignment: .firstTextBaseline, spacing: 20) {
                        Text(reminder.timeText)
                            .font(.system(.title, design: .rounded, weight: .semibold))
                            .monospacedDigit()
                        Text(reminder.name).font(.headline)
                    }
                    .padding(.vertical, 6)
                    .accessibilityElement(children: .combine)
                }
            }
            Section {
                Button("删除计划", role: .destructive) { confirmDelete = true }
                    .accessibilityIdentifier("deletePlan")
                    .disabled(!store.canMutate || record.pendingDeletion)
            } footer: {
                Text("删除前会取消整组系统闹钟；清理失败时保留计划并提供重试。")
            }
        }
    }
}

struct HelpView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section("如何开始") {
                    Text("1. 新建计划，或复制内置/自定义计划继续修改。\n\n2. 填写名称、重复星期和提醒事项。\n\n3. 保存后打开计划开关，首次启用时允许闹钟权限。\n\n4. 确认计划显示“已启用”。如果提示未生效，请按提示重试。")
                }
                Section("响铃与权限") {
                    Text("闹钟提前交给 iOS / iPadOS 管理，无需 App 常驻或联网。它们属于学习闹钟 App，不需要出现在苹果“时钟”的列表中。")
                    Text("AlarmKit 的系统闹钟可突破静音和专注模式。请用当前设备先试响，确认音量及实际表现；设备关机期间无法响铃。")
                    Text("若在设置中关闭闹钟权限，计划将无法生效。再次允许后，返回 App 核对并重试启用。")
                }
                Section("时间与重复") {
                    Text("时间仅表示每项安排开始，不包含时长。每天等于一周七天。提醒按设备本地时间每周重复，不跳过节假日。跨时区或夏令时切换由系统处理，建议变更后检查安排。")
                    Text("多个计划在相同时间的提醒会各自提交给系统。可关闭不需要的计划，避免重复响铃。")
                }
                Section("数据仅在本机") {
                    Text("无需账号，没有服务器或云同步。计划与闹钟对应记录仅保存在本机，不纳入云备份；卸载 App 会丢失计划。")
                }
            }
            .navigationTitle("使用说明")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") { dismiss() }
                }
            }
        }
    }
}
