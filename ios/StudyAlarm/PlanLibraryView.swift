import SwiftUI

private struct PlanRoute: Hashable {
    let id: UUID
    let startsEditing: Bool
}

struct PlanLibraryView: View {
    @ObservedObject var plans: PlanStore
    @Binding var editor: EditorSession?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 28) {
                    HStack {
                        Text("我的计划").font(.title2.bold())
                        Spacer()
                        Text("\(plans.records.count) 组")
                            .foregroundStyle(.secondary)
                    }
                    if plans.records.isEmpty {
                        ContentUnavailableView(
                            "还没有计划",
                            systemImage: "calendar.badge.plus",
                            description: Text("点击右上角加号创建第一组计划。"))
                    } else {
                        LazyVStack(spacing: 12) {
                            ForEach(plans.records) { record in
                                PlanSummaryCard(
                                    plans: plans,
                                    record: record,
                                    editor: $editor)
                            }
                        }
                    }
                }
                .padding()
                .frame(maxWidth: 1120)
                .frame(maxWidth: .infinity)
            }
            .background(Color(uiColor: .systemGroupedBackground))
            .navigationTitle("计划")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button("新建计划", systemImage: "plus") {
                        editor = EditorSession(plan: .draft(), isNew: true)
                    }
                    .accessibilityIdentifier("newPlan")
                    .disabled(!plans.canMutate)
                }
            }
            .navigationDestination(for: PlanRoute.self) { route in
                if let record = plans.record(route.id) {
                    PlanDetailView(
                        store: plans,
                        record: record,
                        editor: $editor,
                        startsEditing: route.startsEditing)
                } else {
                    ContentUnavailableView("计划已删除", systemImage: "trash")
                }
            }
        }
    }
}

private struct PlanSummaryCard: View {
    @ObservedObject var plans: PlanStore
    let record: PlanRecord
    @Binding var editor: EditorSession?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            NavigationLink(value: PlanRoute(id: record.id, startsEditing: false)) {
                HStack(alignment: .firstTextBaseline) {
                    VStack(alignment: .leading, spacing: 5) {
                        Text(record.plan.name).font(.headline)
                        Text("\(record.plan.repeatText) · \(record.plan.reminders.count) 项")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.caption)
                        .foregroundStyle(.tertiary)
                }
            }
            .buttonStyle(.plain)
            PlanControls(store: plans, record: record)
            HStack(spacing: 22) {
                NavigationLink(value: PlanRoute(id: record.id, startsEditing: true)) {
                    Label("编辑", systemImage: "pencil")
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
            .disabled(!plans.canMutate || record.pendingDeletion)
        }
        .padding(16)
        .background(Color(uiColor: .secondarySystemGroupedBackground),
                    in: RoundedRectangle(cornerRadius: 8))
    }
}
