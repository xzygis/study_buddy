import SwiftUI

struct TodayDashboardView: View {
    @ObservedObject var plans: PlanStore
    @State private var showingConflicts = false

    var body: some View {
        NavigationStack {
            TimelineView(.periodic(from: .now, by: 60)) { context in
                ScrollView {
                    TodayDashboardContent(
                        plans: plans,
                        date: context.date,
                        showingConflicts: $showingConflicts)
                        .padding()
                        .frame(maxWidth: 920)
                        .frame(maxWidth: .infinity)
                }
                .background(Color(uiColor: .systemGroupedBackground))
            }
            .navigationTitle("今日计划")
            .navigationDestination(isPresented: $showingConflicts) {
                ConflictManagerView(plans: plans)
            }
            .refreshable { await plans.reconcile() }
        }
    }
}

private struct TodayDashboardContent: View {
    @ObservedObject var plans: PlanStore
    let date: Date
    @Binding var showingConflicts: Bool

    private var activePlanIDs: Set<UUID> {
        Set(plans.records.filter(plans.isEnabled).map(\.id))
    }

    private var conflicts: [ScheduleConflict] {
        ScheduleConflictDetector.detect(records: plans.records, activePlanIDs: activePlanIDs)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 22) {
            DateSummaryCard(date: date, itemCount: agendaEntries.count)
            DashboardSystemStatus(plans: plans)
            if !conflicts.isEmpty {
                Button {
                    showingConflicts = true
                } label: {
                    HStack(spacing: 12) {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .foregroundStyle(.orange)
                        VStack(alignment: .leading, spacing: 3) {
                            Text("发现 \(conflicts.count) 处时间冲突")
                                .font(.headline)
                            Text("多个已启用计划会在同一时间分别响铃")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        Image(systemName: "chevron.right")
                            .foregroundStyle(.secondary)
                    }
                    .padding(16)
                    .background(Color.orange.opacity(0.10),
                                in: RoundedRectangle(cornerRadius: 8))
                }
                .buttonStyle(.plain)
            }

            Text("今日时间轴")
                .font(.headline)
            VStack(spacing: 0) {
                TimelineStartRow()
                if agendaEntries.isEmpty {
                    ContentUnavailableView(
                        "今天没有已启用的安排",
                        systemImage: "calendar.badge.clock",
                        description: Text("从计划页启用计划，提醒会按时间显示在这里。"))
                    .padding(.vertical, 32)
                } else {
                    ForEach(agendaEntries) { entry in
                        DashboardTimelineRow(entry: entry, date: date)
                    }
                }
            }
        }
    }

    private var agendaEntries: [DashboardAgendaEntry] {
        guard let day = Weekday(rawValue: Calendar.current.component(.weekday, from: date)) else {
            return []
        }
        return plans.records
            .filter { plans.isEnabled($0) && $0.plan.weekdays.contains(day) }
            .flatMap { record in
                record.plan.reminders.map {
                    DashboardAgendaEntry(planName: record.plan.name, reminder: $0)
                }
            }
            .sorted {
                if $0.reminder.minutesSinceMidnight != $1.reminder.minutesSinceMidnight {
                    return $0.reminder.minutesSinceMidnight < $1.reminder.minutesSinceMidnight
                }
                return $0.id < $1.id
            }
    }

}

private struct DateSummaryCard: View {
    let date: Date
    let itemCount: Int

    var body: some View {
        HStack(spacing: 16) {
            Image(systemName: "sun.max.fill")
                .font(.largeTitle)
                .foregroundStyle(.orange)
            VStack(alignment: .leading, spacing: 5) {
                Text(date.formatted(.dateTime.month(.wide).day().weekday(.wide)))
                    .font(.title2.bold())
                Text(itemCount == 0 ? "今天暂时没有已启用的提醒" : "今天有 \(itemCount) 项安排")
                    .foregroundStyle(.secondary)
            }
            Spacer()
        }
        .padding(18)
        .background(Color(uiColor: .secondarySystemGroupedBackground),
                    in: RoundedRectangle(cornerRadius: 8))
    }
}

private struct DashboardSystemStatus: View {
    @ObservedObject var plans: PlanStore
    @Environment(\.openURL) private var openURL

    var body: some View {
        if plans.authorization == .denied {
            HStack {
                Label("闹钟权限未开启，计划不会响铃", systemImage: "bell.slash")
                    .foregroundStyle(.orange)
                Spacer()
                Button("设置") {
                    if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                }
            }
            .padding(14)
            .background(Color.orange.opacity(0.10), in: RoundedRectangle(cornerRadius: 8))
        } else if plans.isBusy {
            HStack(spacing: 10) {
                ProgressView()
                Text("正在核对系统闹钟")
                    .foregroundStyle(.secondary)
            }
        } else if let issue = plans.systemIssue ?? plans.storageIssue {
            Label(issue, systemImage: "exclamationmark.triangle")
                .font(.footnote)
                .foregroundStyle(.orange)
        }
    }
}

private struct DashboardAgendaEntry: Identifiable {
    let planName: String
    let reminder: StudyReminder
    var id: UUID { reminder.id }
}

private struct TimelineStartRow: View {
    var body: some View {
        HStack(spacing: 12) {
            Text("06:30")
                .font(.subheadline.monospacedDigit())
                .foregroundStyle(.secondary)
                .frame(width: 54, alignment: .trailing)
            Image(systemName: "sunrise.fill")
                .foregroundStyle(.orange)
                .frame(width: 26)
            Text("一天开始")
                .font(.subheadline.weight(.semibold))
            Spacer()
        }
        .padding(.bottom, 12)
    }
}

private struct DashboardTimelineRow: View {
    let entry: DashboardAgendaEntry
    let date: Date

    private var state: AgendaVisualState {
        let now = Calendar.current.component(.hour, from: date) * 60
            + Calendar.current.component(.minute, from: date)
        let delta = now - entry.reminder.minutesSinceMidnight
        if delta < 0 { return .upcoming }
        if delta <= 40 { return .active }
        return .reminded
    }

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Text(entry.reminder.timeText)
                .font(.headline.monospacedDigit())
                .frame(width: 54, alignment: .trailing)
                .padding(.top, 14)
            VStack(spacing: 0) {
                Circle()
                    .fill(state.color)
                    .frame(width: 11, height: 11)
                    .padding(.top, 19)
                Rectangle()
                    .fill(Color.secondary.opacity(0.20))
                    .frame(width: 2)
            }
            .frame(width: 26)
            HStack(spacing: 12) {
                Image(systemName: icon)
                    .font(.title3)
                    .foregroundStyle(Color.studyGreen)
                    .frame(width: 28)
                VStack(alignment: .leading, spacing: 4) {
                    Text(entry.reminder.name)
                        .font(.headline)
                    Text(entry.planName)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Text(state.label)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(state.color)
                    .padding(.horizontal, 9)
                    .padding(.vertical, 5)
                    .background(state.color.opacity(0.10),
                                in: Capsule())
            }
            .padding(14)
            .background(Color(uiColor: .secondarySystemGroupedBackground),
                        in: RoundedRectangle(cornerRadius: 8))
            .padding(.bottom, 10)
        }
    }

    private var icon: String {
        let name = entry.reminder.name
        if name.contains("休息") { return "cup.and.saucer.fill" }
        if name.contains("餐") { return "fork.knife" }
        if name.contains("篮球") || name.contains("户外") { return "figure.run" }
        if name.contains("洗澡") { return "shower.fill" }
        if name.contains("睡觉") { return "moon.zzz.fill" }
        if name.contains("检查") || name.contains("整理") { return "checklist" }
        return "book.closed.fill"
    }
}

private enum AgendaVisualState {
    case upcoming
    case active
    case reminded

    var label: String {
        switch self {
        case .upcoming: "未开始"
        case .active: "进行中"
        case .reminded: "已提醒"
        }
    }

    var color: Color {
        switch self {
        case .upcoming: .secondary
        case .active: .orange
        case .reminded: .studyGreen
        }
    }
}

struct ConflictManagerView: View {
    @ObservedObject var plans: PlanStore

    private var conflicts: [ScheduleConflict] {
        let activeIDs = Set(plans.records.filter(plans.isEnabled).map(\.id))
        return ScheduleConflictDetector.detect(records: plans.records, activePlanIDs: activeIDs)
    }

    var body: some View {
        List {
            if conflicts.isEmpty {
                ContentUnavailableView(
                    "没有时间冲突",
                    systemImage: "checkmark.circle",
                    description: Text("已启用计划的提醒时间互不重复。"))
            } else {
                ForEach(conflicts) { conflict in
                    Section("\(conflict.weekday.name) \(conflict.timeText)") {
                        ForEach(conflict.items) { item in
                            VStack(alignment: .leading, spacing: 4) {
                                Text(item.reminderName).font(.headline)
                                Text(item.planName).font(.subheadline).foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle("冲突管理")
    }
}
