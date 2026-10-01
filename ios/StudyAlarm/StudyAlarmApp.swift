import SwiftUI

@main
struct StudyAlarmApp: App {
    @State private var store: PlanStore?
    private let service = AlarmKitService()
    private let startupError: String?

    init() {
        do {
            _store = State(initialValue: PlanStore(persistence: try FilePlanPersistence.local(), service: service))
            startupError = nil
        } catch {
            startupError = error.localizedDescription
        }
    }

    var body: some Scene {
        WindowGroup {
            if let store {
                StudyAlarmRoot(store: store, service: service)
                .tint(.studyGreen)
                .environment(\.locale, Locale(identifier: "zh_CN"))
            } else {
                ContentUnavailableView("无法打开本地存储", systemImage: "externaldrive.badge.exclamationmark",
                                       description: Text(startupError ?? "请重新打开 App。"))
            }
        }
    }
}

extension Color {
    static let studyGreen = Color(uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? UIColor(red: 0.42, green: 0.80, blue: 0.68, alpha: 1)
            : UIColor(red: 0.12, green: 0.42, blue: 0.36, alpha: 1)
    })
}

enum MainTab: Hashable {
    case today
    case plans
}

struct StudyAlarmRoot: View {
    @ObservedObject var store: PlanStore
    let service: AlarmKitService
    @Environment(\.scenePhase) private var scenePhase
    @State private var selectedTab: MainTab = .today
    @State private var editor: EditorSession?

    var body: some View {
        TabView(selection: $selectedTab) {
            TodayDashboardView(plans: store)
                .tabItem { Label("今日", systemImage: "calendar") }
                .tag(MainTab.today)
            PlanLibraryView(plans: store, editor: $editor)
                .tabItem { Label("计划", systemImage: "square.grid.2x2") }
                .tag(MainTab.plans)
        }
        .sheet(item: $editor) { session in
            PlanEditorView(store: store, session: session)
                .presentationDetents([.large])
        }
        .alert("请检查", isPresented: Binding(
            get: { store.userMessage != nil },
            set: { if !$0 { store.userMessage = nil } }
        )) {
            Button("知道了", role: .cancel) { store.userMessage = nil }
        } message: {
            Text(store.userMessage ?? "")
        }
        .onChange(of: scenePhase, initial: true) { _, phase in
            if phase == .active { Task { await store.reconcile() } }
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.significantTimeChangeNotification)) { _ in
            Task { await store.reconcile() }
        }
        .task { await service.observeAlarms(store: store) }
        .task { await service.observeAuthorization(store: store) }
    }
}

struct EditorSession: Identifiable {
    let id = UUID()
    var plan: StudyPlan
    var isNew: Bool
}
